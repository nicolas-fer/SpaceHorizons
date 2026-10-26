package com.spacehorizons.ship.deck;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.phys.AABB;

/**
 * O raio da mira contra a grade do retrato, em coordenadas locais (o bloco {@code (x, y, z)} ocupa {@code [x, x+1]} etc.). Lógica pura: anda
 * pelas células que o raio atravessa (DDA de Amanatides e Woo, em ordem de distância) e, em cada célula ocupada, testa as caixas de seleção
 * dela; a primeira célula com acerto é o bloco mirado. O ponto de partida dentro de uma caixa não a seleciona (como o vanilla: o olho dentro
 * de um bloco não mira nele).
 */
public final class PortraitRay {
    private PortraitRay() {
    }

    /** As caixas de seleção de uma célula, relativas a ela (0 a 1); nada se a célula está vazia. */
    @FunctionalInterface
    public interface Cells {
        void boxes(int x, int y, int z, Consumer<AABB> out);
    }

    /**
     * O acerto: a célula, a face atingida (ordem de {@code Direction.get3DDataValue}: 0 baixo, 1 cima, 2 norte, 3 sul, 4 oeste, 5 leste), a
     * distância {@code t} em unidades da direção (blocos, se ela for unitária) e o ponto local.
     */
    public record Hit(int x, int y, int z, int face, double t, double hx, double hy, double hz) {
    }

    /**
     * Lança o raio de {@code origin} na direção {@code dir} até {@code reach}; null se nada na caixa {@code sizeX x sizeY x sizeZ} for atingido.
     * A direção não precisa ser unitária (o {@code t} acompanha a norma dela).
     */
    public static @Nullable Hit cast(double ox, double oy, double oz, double dx, double dy, double dz, double reach, int sizeX, int sizeY, int sizeZ,
            Cells cells) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-9 || reach <= 0.0 || sizeX <= 0 || sizeY <= 0 || sizeZ <= 0 || !Double.isFinite(len + ox + oy + oz)) {
            return null;
        }
        dx /= len;
        dy /= len;
        dz /= len;
        int x = (int) Math.floor(ox);
        int y = (int) Math.floor(oy);
        int z = (int) Math.floor(oz);
        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;
        double tMaxX = dx == 0 ? Double.POSITIVE_INFINITY : ((dx > 0 ? x + 1 : x) - ox) / dx;
        double tMaxY = dy == 0 ? Double.POSITIVE_INFINITY : ((dy > 0 ? y + 1 : y) - oy) / dy;
        double tMaxZ = dz == 0 ? Double.POSITIVE_INFINITY : ((dz > 0 ? z + 1 : z) - oz) / dz;
        double tDeltaX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tDeltaY = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tDeltaZ = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        int maxSteps = (int) Math.ceil(reach * 3.0) + 4;
        for (int i = 0; i < maxSteps; i++) {
            if (x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ) {
                Best best = new Best();
                final int cx = x;
                final int cy = y;
                final int cz = z;
                final double fdx = dx;
                final double fdy = dy;
                final double fdz = dz;
                cells.boxes(cx, cy, cz, box -> best.offer(box.move(cx, cy, cz), ox, oy, oz, fdx, fdy, fdz, reach));
                if (best.face >= 0) {
                    return new Hit(x, y, z, best.face, best.t, ox + dx * best.t, oy + dy * best.t, oz + dz * best.t);
                }
            }
            double tNext = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
            if (tNext > reach) {
                return null;
            }
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
        }
        return null;
    }

    /** O melhor acerto entre as caixas de uma célula (a de menor t). */
    private static final class Best {
        double t = Double.POSITIVE_INFINITY;
        int face = -1;

        void offer(AABB box, double ox, double oy, double oz, double dx, double dy, double dz, double reach) {
            double near = Double.NEGATIVE_INFINITY;
            double far = Double.POSITIVE_INFINITY;
            int nearFace = -1;
            double[] o = {ox, oy, oz};
            double[] d = {dx, dy, dz};
            double[] min = {box.minX, box.minY, box.minZ};
            double[] max = {box.maxX, box.maxY, box.maxZ};
            for (int axis = 0; axis < 3; axis++) {
                if (Math.abs(d[axis]) < 1.0E-12) {
                    if (o[axis] < min[axis] || o[axis] > max[axis]) {
                        return;
                    }
                    continue;
                }
                double t1 = (min[axis] - o[axis]) / d[axis];
                double t2 = (max[axis] - o[axis]) / d[axis];
                boolean enteringMin = t1 < t2;
                double tIn = Math.min(t1, t2);
                double tOut = Math.max(t1, t2);
                if (tIn > near) {
                    near = tIn;
                    // entrando pela face mínima do eixo (raio no sentido +): o acerto é a face "baixo/norte/oeste" desse eixo
                    nearFace = faceOf(axis, enteringMin);
                }
                far = Math.min(far, tOut);
                if (near > far) {
                    return;
                }
            }
            // origem dentro da caixa (near < 0): não seleciona; atrás da origem ou além do alcance: nada
            if (near < 0.0 || near > reach || nearFace < 0) {
                return;
            }
            if (near < this.t) {
                this.t = near;
                this.face = nearFace;
            }
        }

        /** Eixo 0 = x (oeste 4 / leste 5), 1 = y (baixo 0 / cima 1), 2 = z (norte 2 / sul 3); {@code minSide} = o raio entra pelo lado mínimo. */
        private static int faceOf(int axis, boolean minSide) {
            return switch (axis) {
                case 0 -> minSide ? 4 : 5;
                case 1 -> minSide ? 0 : 1;
                default -> minSide ? 2 : 3;
            };
        }
    }
}
