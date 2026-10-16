package com.spacehorizons.rocket;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/**
 * Contas da atitude do foguete (quaternion dos eixos locais do retrato para os do mundo; identidade = em pé, como foi
 * montado). Lógica pura sobre JOML. O yaw segue o Minecraft: cresce no sentido horário visto de cima, o mesmo de
 * {@code Rotation.CLOCKWISE_90} (o norte vira leste), que equivale a girar -yaw em torno de +Y.
 */
public final class RocketAttitude {
    private RocketAttitude() {
    }

    /** A atitude de um foguete só girado em yaw graus (horário visto de cima). */
    public static Quaternionf fromYaw(double yawDegrees) {
        return new Quaternionf().rotationY((float) Math.toRadians(-yawDegrees));
    }

    /** O giro em torno da vertical, em graus horários vistos de cima, de -180 a 180 (para onde o "norte" do retrato aponta). */
    public static double yawDegrees(Quaternionfc attitude) {
        Vector3f north = attitude.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        return Math.toDegrees(Math.atan2(north.x(), -north.z()));
    }

    /** O ângulo entre o "para cima" do foguete e a vertical, em graus (0 em pé, 180 de cabeça para baixo). */
    public static double tiltDegrees(Quaternionfc attitude) {
        Vector3f up = attitude.transform(new Vector3f(0.0F, 1.0F, 0.0F));
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, up.y()))));
    }
}
