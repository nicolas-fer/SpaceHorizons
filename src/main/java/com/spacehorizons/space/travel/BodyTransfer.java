package com.spacehorizons.space.travel;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceDimension;
import com.spacehorizons.space.SpaceFrame;

import org.jspecify.annotations.Nullable;

/**
 * Resultado de levar o jogador de uma dimensão para outra mantendo o mesmo quadro no espaço: mesma posição no espaço,
 * mesma velocidade no espaço e mesma direção de olhar, só que expressos na base local (east/up/north) da dimensão nova.
 * As dimensões são as dos corpos ({@link #compute}) ou a do espaço aberto ({@link #toSpace}, {@link #fromSpace}).
 *
 * As velocidades do Minecraft num corpo são sempre relativas ao chão dele (o jogador parado na superfície tem velocidade
 * zero), mas os corpos se movem (a Lua anda ~140 blocos/tick e gira com a órbita). Então a velocidade vira velocidade no
 * espaço somando a do chão de origem (SpaceFrame#groundVelocity), e no destino subtrai-se a do chão de lá: o jogador
 * chega com a velocidade relativa ao corpo novo, não com centenas de blocos/s herdados do movimento do outro. No espaço
 * aberto não há chão: a velocidade é a do espaço (o referencial inercial da Terra), sem somar nem subtrair nada.
 * Posição e olhar são do instante {@code clock}.
 *
 * O Minecraft não tem rolagem de câmera, então só a direção do olhar é preservada; perto da vertical o yaw sai
 * do vetor "para cima" da câmera, para a imagem não girar na troca.
 *
 * @param target corpo de destino, ou null se o destino é a dimensão de espaço aberto
 */
public record BodyTransfer(
        SolarSystem.@Nullable Body target,
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
     * Um lugar onde o jogador pode estar: o conjunto de funções que levam pontos e vetores entre as coordenadas dele e o
     * espaço do SolarSystem.
     */
    private interface Frame {
        /** Ponto (x, y, z) das coordenadas do frame → espaço. */
        void pointToSpace(double x, double y, double z, double[] out);

        /** Ponto do espaço → coordenadas do frame, já normalizadas (o x de um planeta fica em [-C/2, C/2)). */
        void pointFromSpace(double sx, double sy, double sz, double[] out);

        /** Vetor nos eixos locais do ponto (x, z) → eixos do espaço. */
        void vectorToSpace(double x, double z, double mx, double my, double mz, double[] out);

        /** Vetor do espaço → eixos locais do ponto (x, z). */
        void vectorFromSpace(double x, double z, double vx, double vy, double vz, double[] out);

        /** Velocidade (espaço, blocos/tick) do "chão" do frame no ponto do espaço; zero no espaço aberto. */
        void groundVelocity(double sx, double sy, double sz, double[] out);
    }

    /** A dimensão de um corpo no instante {@code clock}. */
    private record BodyFrame(SolarSystem.Body body, double clock) implements Frame {
        @Override
        public void pointToSpace(double x, double y, double z, double[] out) {
            SpaceFrame.toSpace(body, clock, x, y, z, out);
        }

        @Override
        public void pointFromSpace(double sx, double sy, double sz, double[] out) {
            SpaceFrame.fromSpace(body, clock, sx, sy, sz, out);
            out[0] = TravelMath.normalizeX(body, out[0]);
        }

        @Override
        public void vectorToSpace(double x, double z, double mx, double my, double mz, double[] out) {
            SpaceFrame.localToSpace(body, clock, x, z, mx, my, mz, out);
        }

        @Override
        public void vectorFromSpace(double x, double z, double vx, double vy, double vz, double[] out) {
            SpaceFrame.spaceToLocal(body, clock, x, z, vx, vy, vz, out);
        }

        @Override
        public void groundVelocity(double sx, double sy, double sz, double[] out) {
            SpaceFrame.groundVelocity(body, clock, sx, sy, sz, out);
        }
    }

    /** A dimensão de espaço aberto: eixos fixos (SpaceDimension.LOCAL_TO_SPACE), sem chão e sem rotação. */
    private enum OpenSpaceFrame implements Frame {
        INSTANCE;

        @Override
        public void pointToSpace(double x, double y, double z, double[] out) {
            SpaceDimension.toSpace(x, y, z, out);
        }

        @Override
        public void pointFromSpace(double sx, double sy, double sz, double[] out) {
            SpaceDimension.fromSpace(sx, sy, sz, out);
        }

        @Override
        public void vectorToSpace(double x, double z, double mx, double my, double mz, double[] out) {
            double[] m = SpaceDimension.LOCAL_TO_SPACE;
            out[0] = m[0] * mx + m[1] * my + m[2] * mz;
            out[1] = m[3] * mx + m[4] * my + m[5] * mz;
            out[2] = m[6] * mx + m[7] * my + m[8] * mz;
        }

        @Override
        public void vectorFromSpace(double x, double z, double vx, double vy, double vz, double[] out) {
            double[] m = SpaceDimension.LOCAL_TO_SPACE;
            out[0] = m[0] * vx + m[3] * vy + m[6] * vz;
            out[1] = m[1] * vx + m[4] * vy + m[7] * vz;
            out[2] = m[2] * vx + m[5] * vy + m[8] * vz;
        }

        @Override
        public void groundVelocity(double sx, double sy, double sz, double[] out) {
            out[0] = 0.0;
            out[1] = 0.0;
            out[2] = 0.0;
        }
    }

    /** O destino é a dimensão de espaço aberto (e não a de um corpo). */
    public boolean isSpace() {
        return target == null;
    }

    /**
     * @param from corpo atual
     * @param to destino
     * @param clock relógio do Overworld (ticks) do instante da troca
     * @param x posição x no corpo atual
     * @param y posição y no corpo atual
     * @param z posição z no corpo atual
     * @param vx velocidade (blocos/tick, relativa ao chão do corpo atual) nos eixos do Minecraft do corpo atual
     * @param vy idem
     * @param vz idem
     * @param yRot yaw atual em graus
     * @param xRot pitch atual em graus
     */
    public static BodyTransfer compute(SolarSystem.Body from, SolarSystem.Body to, double clock, double x, double y, double z,
            double vx, double vy, double vz, float yRot, float xRot) {
        return convert(new BodyFrame(from, clock), new BodyFrame(to, clock), to, x, y, z, vx, vy, vz, yRot, xRot);
    }

    /**
     * Da dimensão de um corpo para a de espaço aberto, no mesmo ponto do espaço. A velocidade que chega é a do espaço (a
     * relativa ao chão de origem mais a do chão), nos eixos da dimensão de espaço.
     *
     * @param from corpo atual
     * @param clock relógio do Overworld (ticks) do instante da troca
     * @param x posição x no corpo atual
     * @param y posição y no corpo atual
     * @param z posição z no corpo atual
     * @param vx velocidade (blocos/tick, relativa ao chão do corpo atual) nos eixos do Minecraft do corpo atual
     * @param vy idem
     * @param vz idem
     * @param yRot yaw atual em graus
     * @param xRot pitch atual em graus
     */
    public static BodyTransfer toSpace(SolarSystem.Body from, double clock, double x, double y, double z, double vx, double vy,
            double vz, float yRot, float xRot) {
        return convert(new BodyFrame(from, clock), OpenSpaceFrame.INSTANCE, null, x, y, z, vx, vy, vz, yRot, xRot);
    }

    /**
     * Da dimensão de espaço aberto para a de um corpo, no mesmo ponto do espaço. A velocidade que chega é a relativa ao
     * chão do corpo (a do espaço menos a do chão), nos eixos do Minecraft dele.
     *
     * @param to corpo de destino
     * @param clock relógio do Overworld (ticks) do instante da troca
     * @param x posição x na dimensão de espaço
     * @param y posição y na dimensão de espaço
     * @param z posição z na dimensão de espaço
     * @param vx velocidade (blocos/tick, do espaço) nos eixos da dimensão de espaço
     * @param vy idem
     * @param vz idem
     * @param yRot yaw atual em graus
     * @param xRot pitch atual em graus
     */
    public static BodyTransfer fromSpace(SolarSystem.Body to, double clock, double x, double y, double z, double vx, double vy,
            double vz, float yRot, float xRot) {
        return convert(OpenSpaceFrame.INSTANCE, new BodyFrame(to, clock), to, x, y, z, vx, vy, vz, yRot, xRot);
    }

    private static BodyTransfer convert(Frame from, Frame to, SolarSystem.@Nullable Body target, double x, double y, double z,
            double vx, double vy, double vz, float yRot, float xRot) {
        double[] space = new double[3];
        from.pointToSpace(x, y, z, space);
        double[] local = new double[3];
        to.pointFromSpace(space[0], space[1], space[2], local);
        double nx = local[0];
        double ny = local[1];
        double nz = local[2];

        // velocidade no espaço = a relativa ao chão de origem + a do chão; no destino, tira-se a do chão de lá
        double[] vSpace = new double[3];
        from.vectorToSpace(x, z, vx, vy, vz, vSpace);
        double[] ground = new double[3];
        from.groundVelocity(space[0], space[1], space[2], ground);
        for (int i = 0; i < 3; i++) {
            vSpace[i] += ground[i];
        }
        to.groundVelocity(space[0], space[1], space[2], ground);
        for (int i = 0; i < 3; i++) {
            vSpace[i] -= ground[i];
        }
        double[] v = new double[3];
        to.vectorFromSpace(nx, nz, vSpace[0], vSpace[1], vSpace[2], v);

        double[] look = new double[3];
        TravelMath.lookVector(yRot, xRot, look);
        double[] newLook = convertVector(from, to, x, z, nx, nz, look[0], look[1], look[2]);

        // vetor "para cima" da câmera (sem rolagem): perto da vertical é ele, e não o "para frente", que fixa a rotação
        // da imagem (olhando para baixo a tela tem o rumo do yaw em cima; olhando para cima, o oposto)
        double yaw = Math.toRadians(yRot);
        double pitch = Math.toRadians(xRot);
        double[] up = convertVector(from, to, x, z, nx, nz,
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
        return new BodyTransfer(target, nx, ny, nz, v[0], v[1], v[2], newYaw, newPitch);
    }

    /** Direção nos eixos locais de (from, x, z) → mesma direção no espaço → eixos locais de (to, nx, nz). */
    private static double[] convertVector(Frame from, Frame to, double x, double z, double nx, double nz, double mx, double my,
            double mz) {
        double[] s = new double[3];
        from.vectorToSpace(x, z, mx, my, mz, s);
        double[] out = new double[3];
        to.vectorFromSpace(nx, nz, s[0], s[1], s[2], out);
        return out;
    }
}
