package com.spacehorizons.space.flight;

import org.jspecify.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;

import com.spacehorizons.space.InertialFrame;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceDimension;
import com.spacehorizons.space.SpaceFrame;

/**
 * Atitude do foguete: conversão entre o quaternion INERCIAL (o que o servidor guarda: eixos de I, o referencial inercial, o SAS e as
 * manobras ficam triviais) e o quaternion LOCAL da dimensão (o que o cliente usa para render e câmera), mais as contas
 * do SAS e dos comandos de arfagem, guinada e rolagem.
 *
 * <h2>Convenções</h2>
 * <ul>
 *   <li>Um quaternion de atitude {@code q} leva vetores do corpo do foguete (os eixos do retrato) para o referencial de
 *       destino: {@code mundo = q · corpo}.</li>
 *   <li>O NARIZ do foguete é o eixo {@link #NOSE} = +Y do retrato; o empuxo sai pelo nariz (os motores ficam no lado
 *       oposto, em −Y). Foguete de pé = nariz para cima = quaternion LOCAL identidade.</li>
 *   <li>Arfagem gira em torno de +X do corpo, guinada em torno de +Z e rolagem em torno do nariz (+Y); ângulo positivo é a
 *       rotação de mão direita nas coordenadas numéricas, aplicada nos eixos do corpo.</li>
 *   <li>Local da dimensão de um planeta: eixos (x leste, y cima, z norte) no ponto (x, z) do corpo; local da dimensão de
 *       espaço: os de {@link SpaceDimension#LOCAL_TO_SPACE}. As duas bases levam ao espaço por rotações próprias
 *       (determinante +1), por isso a conversão é uma simples composição de quaternions.</li>
 * </ul>
 *
 * Internamente tudo é em double; as versões {@link Quaternionf} só convertem nas pontas (o dado sincronizado do vanilla
 * é float).
 */
public final class AttitudeMath {
    /** Eixo do nariz (e do empuxo) no referencial do corpo do foguete. */
    public static final V3 NOSE = new V3(0.0, 1.0, 0.0);

    /**
     * Modos do SAS na ordem do ciclo da tecla T: desligado → estabilizar → prógrado → retrógrado → horizontal. Os números
     * (o byte sincronizado e salvo) seguem a ordem da declaração: {@link #HORIZONTAL} entrou no fim.
     */
    public enum SasMode {
        OFF, STABILIZE, PROGRADE, RETROGRADE, HORIZONTAL;

        /** Próximo modo do ciclo da tecla. */
        public SasMode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        /** Modo pelo número (o byte sincronizado); fora da faixa vira {@link #OFF}. */
        public static SasMode byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : OFF;
        }
    }

    private AttitudeMath() {
    }

    // ------------------------------------------------------------------ bases

    /**
     * Matriz (coluna j = eixo j) que leva vetores dos eixos locais (leste, cima, norte) do ponto {@code (x, z)} da
     * dimensão do corpo para os eixos inerciais (I), no instante {@code clock}.
     */
    public static Matrix3d localToSpaceMatrix(SolarSystem.Body body, double clock, double x, double z) {
        double[] ex = new double[3];
        double[] ey = new double[3];
        double[] ez = new double[3];
        SolarSystem.Pose pose = body.inertialPoseAt(clock);
        SpaceFrame.localToSpace(body, pose, x, z, 1.0, 0.0, 0.0, ex);
        SpaceFrame.localToSpace(body, pose, x, z, 0.0, 1.0, 0.0, ey);
        SpaceFrame.localToSpace(body, pose, x, z, 0.0, 0.0, 1.0, ez);
        return new Matrix3d(ex[0], ex[1], ex[2], ey[0], ey[1], ey[2], ez[0], ez[1], ez[2]);
    }

    /** Matriz que leva vetores dos eixos da dimensão de espaço para os de E (preso à Terra), sem depender do tempo. */
    public static Matrix3d spaceDimensionMatrix() {
        double[] m = SpaceDimension.LOCAL_TO_SPACE;
        return new Matrix3d(m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]);
    }

    /** Matriz que leva vetores dos eixos da dimensão de espaço para os inerciais (I) no instante {@code clock}: {@code Ry(−φ)·M}. */
    public static Matrix3d spaceDimensionMatrix(double clock) {
        double[] ry = InertialFrame.rotationY(-InertialFrame.angle(clock));
        Matrix3d toInertial = new Matrix3d(ry[0], ry[3], ry[6], ry[1], ry[4], ry[7], ry[2], ry[5], ry[8]);
        return toInertial.mul(spaceDimensionMatrix());
    }

    private static Quaterniond rotationOf(Matrix3d m) {
        return new Quaterniond().setFromNormalized(m).normalize();
    }

    // ------------------------------------------------------------------ inercial <-> local (dimensão do corpo)

    /** Quaternion inercial → local da dimensão do corpo no ponto {@code (x, z)}: {@code q_local = M⁻¹ · q_inercial}. */
    public static Quaterniond inertialToLocal(SolarSystem.Body body, double clock, double x, double z,
            Quaterniondc inertial) {
        return rotationOf(localToSpaceMatrix(body, clock, x, z)).conjugate().mul(inertial, new Quaterniond()).normalize();
    }

    /** Inverso de {@link #inertialToLocal(SolarSystem.Body, double, double, double, Quaterniondc)}. */
    public static Quaterniond localToInertial(SolarSystem.Body body, double clock, double x, double z,
            Quaterniondc local) {
        return rotationOf(localToSpaceMatrix(body, clock, x, z)).mul(local, new Quaterniond()).normalize();
    }

    /** Versão em float de {@link #inertialToLocal(SolarSystem.Body, double, double, double, Quaterniondc)}. */
    public static Quaternionf inertialToLocal(SolarSystem.Body body, double clock, double x, double z,
            Quaternionfc inertial) {
        return toFloat(inertialToLocal(body, clock, x, z, toDouble(inertial)));
    }

    /** Versão em float de {@link #localToInertial(SolarSystem.Body, double, double, double, Quaterniondc)}. */
    public static Quaternionf localToInertial(SolarSystem.Body body, double clock, double x, double z,
            Quaternionfc local) {
        return toFloat(localToInertial(body, clock, x, z, toDouble(local)));
    }

    // ------------------------------------------------------------------ inercial <-> local (dimensão de espaço)

    /** Quaternion inercial → local da dimensão de espaço no instante {@code clock}. */
    public static Quaterniond inertialToSpaceLocal(Quaterniondc inertial, double clock) {
        return rotationOf(spaceDimensionMatrix(clock)).conjugate().mul(inertial, new Quaterniond()).normalize();
    }

    /** Quaternion local da dimensão de espaço → inercial no instante {@code clock}. */
    public static Quaterniond spaceLocalToInertial(Quaterniondc local, double clock) {
        return rotationOf(spaceDimensionMatrix(clock)).mul(local, new Quaterniond()).normalize();
    }

    public static Quaternionf inertialToSpaceLocal(Quaternionfc inertial, double clock) {
        return toFloat(inertialToSpaceLocal(toDouble(inertial), clock));
    }

    public static Quaternionf spaceLocalToInertial(Quaternionfc local, double clock) {
        return toFloat(spaceLocalToInertial(toDouble(local), clock));
    }

    // ------------------------------------------------------------------ direções

    /** Direção do nariz (e do empuxo) no referencial do quaternion {@code q}. */
    public static V3 noseDirection(Quaterniondc q) {
        Vector3d out = q.transform(NOSE.x(), NOSE.y(), NOSE.z(), new Vector3d());
        return new V3(out.x, out.y, out.z);
    }

    /** Ângulo (graus) entre o nariz e a direção {@code up} (mesmo referencial): a inclinação em relação à vertical. */
    public static double tiltDegrees(Quaterniondc q, V3 up) {
        return Math.toDegrees(noseDirection(q).angleTo(up));
    }

    /**
     * Direção que o SAS quer para o nariz, nos eixos de {@code velocity}: prógrado = a velocidade, retrógrado = o
     * oposto. {@code null} para desligado e estabilizar (que só seguram a atitude atual) e para velocidade quase nula.
     * Use a velocidade relativa ao chão perto da superfície e a orbital no espaço.
     * <p>
     * Sem a vertical ({@code up} nulo), o modo horizontal não tem alvo e devolve {@code null}.
     */
    public static @Nullable V3 sasTarget(SasMode mode, V3 velocity) {
        return sasTarget(mode, velocity, null);
    }

    /**
     * O mesmo que {@link #sasTarget(SasMode, V3)}, com a direção radial para fora {@code up} (do centro do corpo dominante até
     * o veículo, no mesmo referencial de {@code velocity}). O modo {@link SasMode#HORIZONTAL} aponta o nariz para a horizontal
     * local no sentido do movimento: {@code h = v − (v·û)û}, com {@code û} = {@code up} unitário, e o alvo é {@code h.unit()}.
     * Em órbita circular o alvo é o prógrado; numa órbita excêntrica, fora do apoastro e do periastro, ele é perpendicular ao
     * radial, mas não é o prógrado. Se {@code |h|} é quase nulo (subida vertical, velocidade radial pura) ou falta {@code up},
     * devolve {@code null}: o SAS segura a atitude atual, como no estabilizar.
     */
    public static @Nullable V3 sasTarget(SasMode mode, V3 velocity, @Nullable V3 up) {
        if (mode == SasMode.HORIZONTAL) {
            if (up == null || up.length() < 1.0e-9) {
                return null;
            }
            V3 radial = up.unit();
            V3 horizontal = velocity.minus(radial.times(velocity.dot(radial)));
            return horizontal.length() < 1.0e-6 * Math.max(1.0, velocity.length()) ? null : horizontal.unit();
        }
        if ((mode != SasMode.PROGRADE && mode != SasMode.RETROGRADE) || velocity.length() < 1.0e-6) {
            return null;
        }
        V3 prograde = velocity.unit();
        return mode == SasMode.PROGRADE ? prograde : prograde.negate();
    }

    // ------------------------------------------------------------------ manobras

    /**
     * Gira o foguete pelo menor arco para apontar o nariz a {@code target} (mesmo referencial de {@code current}), no
     * máximo {@code maxAngle} radianos neste passo. A rolagem em torno do nariz é preservada (gira em torno do eixo
     * perpendicular a nariz e alvo). Alvo nulo devolve a atitude atual.
     */
    public static Quaterniond slewNoseToward(Quaterniondc current, V3 target, double maxAngle) {
        Quaterniond result = new Quaterniond(current);
        V3 goal = target.unit();
        V3 nose = noseDirection(current);
        double angle = nose.angleTo(goal);
        if (goal.lengthSquared() == 0.0 || angle < 1.0e-12 || maxAngle <= 0.0) {
            return result;
        }
        V3 axis = nose.cross(goal);
        if (axis.length() < 1.0e-12) {
            // nariz oposto ao alvo: qualquer eixo perpendicular serve
            axis = nose.cross(Math.abs(nose.x()) < 0.9 ? new V3(1, 0, 0) : new V3(0, 0, 1));
        }
        axis = axis.unit();
        double step = Math.min(angle, maxAngle);
        Quaterniond delta = new Quaterniond().fromAxisAngleRad(axis.x(), axis.y(), axis.z(), step);
        return delta.mul(current, result).normalize();
    }

    /**
     * Aplica comandos de arfagem (em torno de +X do corpo), guinada (+Z) e rolagem (+Y, o nariz), em radianos neste
     * passo, nos eixos do CORPO: a atitude nova é {@code q · dq}.
     */
    public static Quaterniond rotateBody(Quaterniondc current, double pitch, double yaw, double roll) {
        return new Quaterniond(current).rotateX(pitch).rotateZ(yaw).rotateY(roll).normalize();
    }

    // ------------------------------------------------------------------ float

    private static Quaterniond toDouble(Quaternionfc q) {
        return new Quaterniond(q.x(), q.y(), q.z(), q.w());
    }

    private static Quaternionf toFloat(Quaterniondc q) {
        return new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w()).normalize();
    }
}
