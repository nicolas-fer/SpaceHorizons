package com.spacehorizons.space.travel;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceFrame;

/**
 * Resultado de levar o jogador de uma dimensão-planeta para a de outro corpo mantendo o mesmo quadro no espaço:
 * mesma posição no espaço, mesmo vetor de velocidade e mesma direção de olhar, só que expressos na base local
 * (east/up/north) do corpo novo.
 *
 * O Minecraft não tem rolagem de câmera, então só a direção do olhar é preservada; perto da vertical o yaw sai
 * do vetor "para cima" da câmera, para a imagem não girar na troca.
 */
public record BodyTransfer(
        SolarSystem.Body target,
        double x,
        double y,
        double z,
        double vx,
        double vy,
        double vz,
        float yRot,
        float xRot) {

    /** Abaixo disto (~1,1° da vertical) a parte horizontal do olhar é considerada nula e o yaw vem do vetor "para cima" da câmera (a direção do olhar erra no máximo ~2°). */
    private static final double VERTICAL_EPSILON = 0.02;

    /**
     * @param from corpo atual
     * @param to destino
     * @param x posição x no corpo atual
     * @param y posição y no corpo atual
     * @param z posição z no corpo atual
     * @param vx velocidade (blocos/tick) nos eixos do Minecraft do corpo atual
     * @param vy idem
     * @param vz idem
     * @param yRot yaw atual em graus
     * @param xRot pitch atual em graus
     */
    public static BodyTransfer compute(SolarSystem.Body from, SolarSystem.Body to, double x, double y, double z,
            double vx, double vy, double vz, float yRot, float xRot) {
        double[] space = new double[3];
        SpaceFrame.toSpace(from, x, y, z, space);
        double[] local = new double[3];
        SpaceFrame.fromSpace(to, space[0], space[1], space[2], local);
        double nx = TravelMath.normalizeX(to, local[0]);
        double ny = local[1];
        double nz = local[2];

        double[] v = convert(from, to, x, z, nx, nz, vx, vy, vz);

        double[] look = new double[3];
        TravelMath.lookVector(yRot, xRot, look);
        double[] newLook = convert(from, to, x, z, nx, nz, look[0], look[1], look[2]);

        // vetor "para cima" da câmera (sem rolagem): perto da vertical é ele, e não o "para frente", que fixa a rotação
        // da imagem (olhando para baixo a tela tem o rumo do yaw em cima; olhando para cima, o oposto)
        double yaw = Math.toRadians(yRot);
        double pitch = Math.toRadians(xRot);
        double[] up = convert(from, to, x, z, nx, nz,
                -Math.sin(yaw) * Math.sin(pitch), Math.cos(pitch), Math.cos(yaw) * Math.sin(pitch));

        float newYaw;
        if (Math.hypot(newLook[0], newLook[2]) > VERTICAL_EPSILON) {
            newYaw = TravelMath.yawOf(newLook[0], newLook[2]);
        } else if (Math.hypot(up[0], up[2]) > VERTICAL_EPSILON) {
            boolean looksDown = newLook[1] < 0.0;
            newYaw = looksDown ? TravelMath.yawOf(up[0], up[2]) : TravelMath.yawOf(-up[0], -up[2]);
        } else {
            newYaw = yRot;
        }
        float newPitch = TravelMath.pitchOf(newLook[1]);
        return new BodyTransfer(to, nx, ny, nz, v[0], v[1], v[2], newYaw, newPitch);
    }

    /** Vetor nos eixos do Minecraft de (from, x, z) → mesmo vetor no espaço → eixos do Minecraft de (to, nx, nz). */
    private static double[] convert(SolarSystem.Body from, SolarSystem.Body to, double x, double z, double nx, double nz,
            double mx, double my, double mz) {
        double[] s = new double[3];
        SpaceFrame.localToSpace(from, x, z, mx, my, mz, s);
        double[] out = new double[3];
        SpaceFrame.spaceToLocal(to, nx, nz, s[0], s[1], s[2], out);
        return out;
    }
}
