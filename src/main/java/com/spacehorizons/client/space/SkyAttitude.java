package com.spacehorizons.client.space;

import com.spacehorizons.space.SpaceDimension;

import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.jspecify.annotations.Nullable;

/**
 * Atitude virtual do céu (spike S1 das naves, comando de desenvolvimento {@code /spacehorizons skytest}): com o modo ligado, o
 * céu do espaço aberto é desenhado como se o jogador estivesse numa nave com esta atitude, em vez da orientação fixa da
 * dimensão. A atitude é um quaternion nos eixos do espaço ({@code localToSpace = R · SpaceDimension.LOCAL_TO_SPACE}, ver
 * {@link SkyObserver#forShip}); os ângulos do comando e o giro contínuo são em torno dos eixos LOCAIS da nave (x leste, y cima,
 * z norte, os da câmera), o que é o natural para um piloto.
 *
 * <p>Um único valor por frame: {@link #update} roda uma vez na extração do estado de render (SpaceSkyClient) e o sol, as
 * estrelas e os corpos ({@link BodyRenderer}) leem o mesmo {@link #current}, para nunca divergirem dentro de um frame. O giro
 * usa o relógio real (nanoTime) em double: sem acúmulo de erro de float e suave entre ticks.
 *
 * <p>Só o thread de render lê e escreve (o handler do pacote roda no thread principal do cliente, que é o de render).
 */
public final class SkyAttitude {
    /** Eixos do espaço da dimensão como quaternion: {@code SpaceDimension.LOCAL_TO_SPACE} (det +1). */
    private static final Quaterniond DIMENSION = new Quaterniond().setFromNormalized(
            new Matrix3d().set(SpaceDimension.LOCAL_TO_SPACE).transpose());

    private static boolean active;
    /** Atitude de base, nos eixos locais da nave. */
    private static final Quaterniond baseLocal = new Quaterniond();
    private static double spinRate;
    private static int spinAxis = 1;
    private static long spinStartNanos;
    /** Atitude deste frame, nos eixos do espaço (R). Só vale com {@link #active}. */
    private static final Quaterniond current = new Quaterniond();

    private SkyAttitude() {
    }

    /**
     * Atitude em eixos locais a partir de yaw (em torno de y, cima), pitch (em torno de x, leste) e roll (em torno de z, norte),
     * em graus: {@code Ry(yaw) · Rx(pitch) · Rz(roll)}.
     */
    public static Quaterniond fromEuler(double yawDeg, double pitchDeg, double rollDeg) {
        return new Quaterniond().rotateY(Math.toRadians(yawDeg)).rotateX(Math.toRadians(pitchDeg)).rotateZ(Math.toRadians(rollDeg));
    }

    /** Atitude nos eixos locais da nave → atitude R nos eixos do espaço: {@code R = L · R_local · L⁻¹}. */
    public static Quaterniond localToSpaceFrame(Quaterniondc local) {
        return new Quaterniond(DIMENSION).mul(local).mul(new Quaterniond(DIMENSION).conjugate()).normalize();
    }

    /** Liga com uma atitude fixa (graus, ver {@link #fromEuler}). */
    public static void set(double yawDeg, double pitchDeg, double rollDeg) {
        baseLocal.set(fromEuler(yawDeg, pitchDeg, rollDeg));
        spinRate = 0.0;
        active = true;
        update(System.nanoTime());
    }

    /** Liga o giro contínuo de {@code degPerSecond} em torno do eixo local 0 = x, 1 = y, 2 = z, a partir da atitude de agora. */
    public static void spin(double degPerSecond, int axis, long nowNanos) {
        if (active) {
            update(nowNanos);
            baseLocal.set(localAt(nowNanos));       // continua de onde está (sem salto ao trocar o eixo ou a taxa)
        }
        active = true;
        spinRate = Math.toRadians(degPerSecond);
        spinAxis = Math.max(0, Math.min(2, axis));
        spinStartNanos = nowNanos;
        update(nowNanos);
    }

    /** Desliga: o céu volta à orientação fixa da dimensão. */
    public static void off() {
        active = false;
        spinRate = 0.0;
        baseLocal.identity();
        current.identity();
    }

    /** Esquece tudo (saída do mundo). */
    public static void reset() {
        off();
    }

    /** Atitude local (eixos da nave) no instante {@code nowNanos}: a base vezes o giro em torno do eixo escolhido. */
    private static Quaterniond localAt(long nowNanos) {
        Quaterniond q = new Quaterniond(baseLocal);
        if (spinRate != 0.0) {
            double angle = spinRate * (nowNanos - spinStartNanos) * 1.0e-9;
            q.rotateAxis(angle, spinAxis == 0 ? 1.0 : 0.0, spinAxis == 1 ? 1.0 : 0.0, spinAxis == 2 ? 1.0 : 0.0);
        }
        return q.normalize();
    }

    /** Fixa a atitude de um frame (uma vez por frame, na extração). Sem efeito se desligado. */
    public static void update(long nowNanos) {
        if (active) {
            current.set(localToSpaceFrame(localAt(nowNanos)));
        }
    }

    /** Atitude R deste frame (eixos do espaço), ou null com o modo desligado. */
    public static @Nullable Quaterniondc current() {
        return active ? current : null;
    }
}
