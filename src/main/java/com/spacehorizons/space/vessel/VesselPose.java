package com.spacehorizons.space.vessel;

import com.spacehorizons.space.SpaceDimension;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;

/**
 * A transformação RELATIVA de um veículo visto por um observador (docs/PLANO_NAVES.md, 4.3; fase 2d), lógica pura em {@code double}: a posição do
 * outro menos a do observador, girada para os eixos LOCAIS do observador (os da câmera: x leste, y cima, z norte; para quem está numa nave, com a
 * atitude dela), e a rotação do modelo do outro nesses mesmos eixos. Tudo em double, relativo ao observador, então a câmera nunca vê coordenadas
 * grandes (sem tremor).
 *
 * <p>Convenção: o observador tem {@code localToSpace} (3x3, linhas: eixos locais → espaço do SolarSystem, ver
 * {@code SkyObserver}); o veículo tem a atitude local {@code ql} ({@link VesselEntry#attitude}) e o modelo vai ao espaço por
 * {@code M_V = LOCAL_TO_SPACE · R(ql)}. O observador numa nave com atitude {@code R} (nos eixos do espaço) tem
 * {@code M_O = R · LOCAL_TO_SPACE}, que é a mesma forma ({@code L · R(ql)}), então dois veículos com a mesma atitude se veem sem rotação relativa.
 */
public final class VesselPose {
    private VesselPose() {
    }

    /** A matriz de rotação 3x3 (ordem de linhas) de um quaternion (x, y, z, w) unitário. */
    public static void rotationMatrix(double qx, double qy, double qz, double qw, double[] out) {
        double n = Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
        double x = n < 1.0e-12 ? 0.0 : qx / n;
        double y = n < 1.0e-12 ? 0.0 : qy / n;
        double z = n < 1.0e-12 ? 0.0 : qz / n;
        double w = n < 1.0e-12 ? 1.0 : qw / n;
        out[0] = 1.0 - 2.0 * (y * y + z * z);
        out[1] = 2.0 * (x * y - z * w);
        out[2] = 2.0 * (x * z + y * w);
        out[3] = 2.0 * (x * y + z * w);
        out[4] = 1.0 - 2.0 * (x * x + z * z);
        out[5] = 2.0 * (y * z - x * w);
        out[6] = 2.0 * (x * z - y * w);
        out[7] = 2.0 * (y * z + x * w);
        out[8] = 1.0 - 2.0 * (x * x + y * y);
    }

    /** {@code M_V = LOCAL_TO_SPACE · R(ql)} (3x3, ordem de linhas): dos blocos do veículo (relativos ao pivô) ao espaço do SolarSystem. */
    public static double[] modelToSpace(double[] attitude) {
        double[] r = new double[9];
        rotationMatrix(attitude[0], attitude[1], attitude[2], attitude[3], r);
        return multiply(SpaceDimension.LOCAL_TO_SPACE, r);
    }

    /** A atitude de um veículo parado em sua orientação de referência (identidade). */
    public static double[] identityAttitude() {
        return new double[] {0.0, 0.0, 0.0, 1.0};
    }

    /** {@code a · b}, 3x3 em ordem de linhas. */
    public static double[] multiply(double[] a, double[] b) {
        double[] m = new double[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double v = 0.0;
                for (int k = 0; k < 3; k++) {
                    v += a[i * 3 + k] * b[k * 3 + j];
                }
                m[i * 3 + j] = v;
            }
        }
        return m;
    }

    /**
     * O outro veículo nos eixos do observador. {@code center} (3) recebe o vetor do observador ao pivô do outro, nos eixos locais do observador
     * ({@code M_Oᵀ · (p_V − p_O)}); {@code rotation} (9, ordem de linhas) recebe a rotação do modelo do outro nesses eixos ({@code M_Oᵀ · M_V}).
     */
    public static void relative(double[] observerPos, double[] observerLocalToSpace, double[] vesselPos, double[] vesselModelToSpace, double[] center,
            double[] rotation) {
        double dx = vesselPos[0] - observerPos[0];
        double dy = vesselPos[1] - observerPos[1];
        double dz = vesselPos[2] - observerPos[2];
        double[] m = observerLocalToSpace;
        center[0] = m[0] * dx + m[3] * dy + m[6] * dz;
        center[1] = m[1] * dx + m[4] * dy + m[7] * dz;
        center[2] = m[2] * dx + m[5] * dy + m[8] * dz;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double v = 0.0;
                for (int k = 0; k < 3; k++) {
                    v += m[k * 3 + i] * vesselModelToSpace[k * 3 + j];
                }
                rotation[i * 3 + j] = v;
            }
        }
    }

    /** A posição em E {@code dt} ticks depois do pacote, pela velocidade (linear). */
    public static double[] extrapolatePosition(double[] pos, double[] velocity, double dt) {
        return new double[] {pos[0] + velocity[0] * dt, pos[1] + velocity[1] * dt, pos[2] + velocity[2] * dt};
    }

    /** A atitude local {@code dt} ticks depois do pacote: {@code rot(omega · dt) · q}. */
    public static double[] extrapolateAttitude(double[] attitude, double[] omega, double dt) {
        double wx = omega[0] * dt;
        double wy = omega[1] * dt;
        double wz = omega[2] * dt;
        double angle = Math.sqrt(wx * wx + wy * wy + wz * wz);
        Quaterniond q = new Quaterniond(attitude[0], attitude[1], attitude[2], attitude[3]).normalize();
        if (angle > 1.0e-12) {
            Quaterniondc delta = new Quaterniond().setAngleAxis(angle, wx / angle, wy / angle, wz / angle);
            q = new Quaterniond(delta).mul(q).normalize();
        }
        return new double[] {q.x, q.y, q.z, q.w};
    }

    /** O giro entre duas atitudes: {@code omega} tal que {@code rot(omega) · from = to} (rad), em eixos locais da dimensão. */
    public static double[] rotationBetween(double[] from, double[] to) {
        Quaterniond a = new Quaterniond(from[0], from[1], from[2], from[3]).normalize();
        Quaterniond b = new Quaterniond(to[0], to[1], to[2], to[3]).normalize();
        Quaterniond delta = new Quaterniond(b).mul(new Quaterniond(a).conjugate()).normalize();
        if (delta.w < 0.0) {
            delta.x = -delta.x;
            delta.y = -delta.y;
            delta.z = -delta.z;
            delta.w = -delta.w;
        }
        double s = Math.sqrt(delta.x * delta.x + delta.y * delta.y + delta.z * delta.z);
        if (s < 1.0e-12) {
            return new double[] {0.0, 0.0, 0.0};
        }
        double angle = 2.0 * Math.atan2(s, delta.w);
        return new double[] {delta.x / s * angle, delta.y / s * angle, delta.z / s * angle};
    }

    /** O raio angular (rad) de uma esfera de {@code radius} blocos vista a {@code distance} blocos do centro. */
    public static double angularRadius(double radius, double distance) {
        if (distance <= radius) {
            return Math.PI / 2.0;
        }
        return Math.asin(Math.min(1.0, radius / distance));
    }
}
