package com.spacehorizons.space.vessel;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

/**
 * A malha de triângulos de um casco ({@link VesselHull}): as faces das células que dão para fora (vizinho fora do casco). Lógica pura, com as posições
 * RELATIVAS AO PIVÔ do casco (em blocos), prontas para o desenho girar em torno dele. As cores são ARGB opacas; a luz é do shader (a normal sai
 * da própria face, então não há normal por vértice).
 *
 * <p>Sem casco (ainda não chegou, ou longe demais para pedir) o veículo é desenhado como uma caixa ({@link #box}).
 */
public final class VesselMesh {
    /** Os vértices (3 floats cada) e as cores ARGB (1 por vértice) de triângulos soltos (3 vértices por triângulo). */
    public record Mesh(float[] positions, int[] colors) {
        public int vertexCount() {
            return this.colors.length;
        }

        public int triangleCount() {
            return this.colors.length / 3;
        }
    }

    private VesselMesh() {
    }

    private static final int[][] NORMALS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    public static Mesh build(VesselHull hull) {
        IntOpenHashSet present = new IntOpenHashSet(hull.cellCount() * 2);
        for (int i = 0; i < hull.cellCount(); i++) {
            present.add(hull.cellIndex(i));
        }
        int sx = hull.sizeX;
        int sz = hull.sizeZ;
        float[] positions = new float[Math.max(1, hull.cellCount()) * 6 * 6 * 3];
        int[] colors = new int[Math.max(1, hull.cellCount()) * 6 * 6];
        int n = 0;
        for (int i = 0; i < hull.cellCount(); i++) {
            int at = hull.cellIndex(i);
            int x = at % sx;
            int z = (at / sx) % sz;
            int y = at / (sx * sz);
            int argb = 0xFF000000 | hull.cellRgb(i);
            for (int[] d : NORMALS) {
                int ax = x + d[0];
                int ay = y + d[1];
                int az = z + d[2];
                boolean covered = ax >= 0 && ay >= 0 && az >= 0 && ax < sx && ay < hull.sizeY && az < sz && present.contains(hull.index(ax, ay, az));
                if (covered) {
                    continue;
                }
                n = face(positions, colors, n, x, y, z, d, hull.scale, argb);
            }
        }
        float scaleOffsetX = hull.pivotX;
        float scaleOffsetY = hull.pivotY;
        float scaleOffsetZ = hull.pivotZ;
        float[] outPositions = new float[n * 3];
        for (int v = 0; v < n; v++) {
            outPositions[v * 3] = positions[v * 3] - scaleOffsetX;
            outPositions[v * 3 + 1] = positions[v * 3 + 1] - scaleOffsetY;
            outPositions[v * 3 + 2] = positions[v * 3 + 2] - scaleOffsetZ;
        }
        int[] outColors = new int[n];
        System.arraycopy(colors, 0, outColors, 0, n);
        return new Mesh(outPositions, outColors);
    }

    /** Uma caixa centrada na origem com meias extensões dadas (a forma de quem ainda não tem casco): 12 triângulos. */
    public static Mesh box(float hx, float hy, float hz, int rgb) {
        float[] positions = new float[36 * 3];
        int[] colors = new int[36];
        int argb = 0xFF000000 | rgb;
        int n = 0;
        for (int[] d : NORMALS) {
            // o mesmo desenho das faces do casco, sobre uma célula unitária esticada: centrada na origem
            n = face(positions, colors, n, 0, 0, 0, d, 1, argb);
        }
        for (int v = 0; v < n; v++) {
            positions[v * 3] = (positions[v * 3] - 0.5F) * 2.0F * hx;
            positions[v * 3 + 1] = (positions[v * 3 + 1] - 0.5F) * 2.0F * hy;
            positions[v * 3 + 2] = (positions[v * 3 + 2] - 0.5F) * 2.0F * hz;
        }
        return new Mesh(positions, colors);
    }

    /** Escreve os dois triângulos da face {@code d} da célula (x, y, z); devolve o novo número de vértices. */
    private static int face(float[] positions, int[] colors, int n, int x, int y, int z, int[] d, int scale, int argb) {
        float x0 = x * scale;
        float y0 = y * scale;
        float z0 = z * scale;
        float x1 = x0 + scale;
        float y1 = y0 + scale;
        float z1 = z0 + scale;
        // os 4 cantos da face, no sentido anti-horário visto de fora (o shader vira a normal para o lado do observador de qualquer jeito)
        float[][] c;
        if (d[0] == 1) {
            c = new float[][] {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}};
        } else if (d[0] == -1) {
            c = new float[][] {{x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}, {x0, y0, z0}};
        } else if (d[1] == 1) {
            c = new float[][] {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
        } else if (d[1] == -1) {
            c = new float[][] {{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
        } else if (d[2] == 1) {
            c = new float[][] {{x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}, {x0, y0, z1}};
        } else {
            c = new float[][] {{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}};
        }
        int[] order = {0, 1, 2, 0, 2, 3};
        for (int k : order) {
            positions[n * 3] = c[k][0];
            positions[n * 3 + 1] = c[k][1];
            positions[n * 3 + 2] = c[k][2];
            colors[n] = argb;
            n++;
        }
        return n;
    }
}
