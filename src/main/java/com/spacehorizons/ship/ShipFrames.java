package com.spacehorizons.ship;

import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Referencial local da nave na conversão (plano 3.2): a entidade do foguete tem o pivô no centro da caixa do retrato e uma atitude
 * (quaternion que leva o retrato ao mundo); o estaleiro não gira, então a posição de um passageiro vira
 * {@code local = R⁻¹ · (pos − centro) + pivô} e a vista dele é girada por R⁻¹ também (a orientação RELATIVA à nave se mantém). Matemática
 * pura, testada sem o jogo.
 */
public final class ShipFrames {
    private ShipFrames() {
    }

    /** Posição do mundo (junto da entidade) → coordenadas locais do retrato. */
    public static Vector3d worldToLocal(Quaternionfc attitude, double cx, double cy, double cz, Vector3f pivot, double x, double y, double z) {
        Vector3f d = new Vector3f((float) (x - cx), (float) (y - cy), (float) (z - cz));
        attitude.invert(new org.joml.Quaternionf()).transform(d);
        return new Vector3d(d.x + pivot.x, d.y + pivot.y, d.z + pivot.z);
    }

    /** Coordenadas locais do retrato → mundo (o inverso de {@link #worldToLocal}). */
    public static Vector3d localToWorld(Quaternionfc attitude, double cx, double cy, double cz, Vector3f pivot, double x, double y, double z) {
        Vector3f d = new Vector3f((float) (x - pivot.x), (float) (y - pivot.y), (float) (z - pivot.z));
        attitude.transform(d);
        return new Vector3d(cx + d.x, cy + d.y, cz + d.z);
    }

    /** Direção de visão (yaw/pitch do Minecraft, graus) → a mesma direção vista no referencial local da nave; devolve {yaw, pitch}. */
    public static float[] viewToLocal(Quaternionfc attitude, float yaw, float pitch) {
        return rotateView(attitude.invert(new org.joml.Quaternionf()), yaw, pitch);
    }

    /** O inverso de {@link #viewToLocal}: da nave para o mundo. */
    public static float[] viewToWorld(Quaternionfc attitude, float yaw, float pitch) {
        return rotateView(attitude, yaw, pitch);
    }

    private static float[] rotateView(Quaternionfc q, float yaw, float pitch) {
        double yr = Math.toRadians(yaw);
        double pr = Math.toRadians(pitch);
        // vetor de visão do Minecraft: x = -sin(yaw)cos(pitch), y = -sin(pitch), z = cos(yaw)cos(pitch)
        Vector3f v = new Vector3f((float) (-Math.sin(yr) * Math.cos(pr)), (float) -Math.sin(pr), (float) (Math.cos(yr) * Math.cos(pr)));
        q.transform(v);
        float newPitch = (float) -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, v.y))));
        float newYaw = Math.abs(v.x) < 1.0E-6F && Math.abs(v.z) < 1.0E-6F ? yaw : (float) Math.toDegrees(Math.atan2(-v.x, v.z));
        return new float[] {newYaw, newPitch};
    }
}
