package com.spacehorizons.client.space;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

import com.spacehorizons.client.planetmesh.PlanetMeshGeometry;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrain;

/**
 * Esfera completa de um planeta, para vê-lo de outro corpo (Java puro, sem Minecraft). Grade lat/lon própria do
 * planeta: {@code segments} longitudes e segments/2 + 1 anéis de latitude, do polo norte (+Y do espaço) ao sul.
 *
 * As posições são FIXAS no corpo e relativas ao centro dele, nos eixos do espaço: (R + h)·n, com
 * n = (cos lat·cos lon, sin lat, cos lat·sin lon), o mesmo mapeamento de {@code SpaceFrame.toSpace}. Assim cada
 * nível de resolução é construído uma vez e reaproveitado para qualquer posição da câmera: o renderer só aplica
 * uma matriz (rotação para os eixos locais da câmera, escala do scaled space e translação até o centro).
 *
 * Altura e cor de cada vértice vêm de {@link PlanetMeshGeometry#shadeVertex}, a mesma função da malha orbital do
 * planeta atual (mesmo terreno, mesma paleta, mesmo afundamento), o que garante a coincidência (ver BodySky).
 */
public final class BodyMeshGeometry {
    /** Células menores que isto (blocos) não precisam de supersampling (mesmo critério da malha orbital). */
    private static final double SUPERSAMPLE_MIN_CELL = PlanetMeshGeometry.SUPERSAMPLE_MIN_CELL_BLOCKS;
    private static final double SUPERSAMPLE_RADIUS = 0.35;
    private static final double[] SUPERSAMPLE_COS = {Math.cos(Math.PI / 2), Math.cos(Math.PI * 7 / 6), Math.cos(Math.PI * 11 / 6)};
    private static final double[] SUPERSAMPLE_SIN = {Math.sin(Math.PI / 2), Math.sin(Math.PI * 7 / 6), Math.sin(Math.PI * 11 / 6)};

    private BodyMeshGeometry() {
    }

    private static void checkSegments(int segments) {
        if (segments < 4 || segments % 2 != 0) {
            throw new IllegalArgumentException("segments deve ser par e >= 4: " + segments);
        }
    }

    public static int rings(int segments) {
        return segments / 2 + 1;
    }

    public static int vertexCount(int segments) {
        checkSegments(segments);
        return rings(segments) * segments;
    }

    public static int indexCount(int segments) {
        checkSegments(segments);
        return (rings(segments) - 1) * segments * 6;
    }

    /** Latitude (rad) do anel: +π/2 no anel 0 até -π/2 no último. */
    public static double ringLatitude(int segments, int ring) {
        return Math.PI * (0.5 - ring / (double) (segments / 2));
    }

    /** Longitude (rad) da coluna j, em [-π, π). */
    public static double columnLongitude(int segments, int column) {
        return 2.0 * Math.PI * column / segments - Math.PI;
    }

    /**
     * Preenche posições (3 floats por vértice, relativas ao centro do corpo, eixos do espaço) e cores
     * (0xAARRGGBB, alfa = máscara de água). Se pool não for nulo, os anéis são calculados em paralelo nele.
     */
    public static void fill(PlanetTerrain terrain, PlanetMeshGeometry.Palette palette, int segments,
            float[] outPos, int[] outColor, ForkJoinPool pool) {
        int vertices = vertexCount(segments);
        if (outPos.length < vertices * 3 || outColor.length < vertices) {
            throw new IllegalArgumentException("buffers pequenos para " + vertices + " vértices");
        }
        int rings = rings(segments);
        if (pool == null) {
            for (int i = 0; i < rings; i++) {
                fillRing(terrain, palette, segments, i, outPos, outColor);
            }
            return;
        }
        Runnable all = () -> IntStream.range(0, rings).parallel().forEach(i -> fillRing(terrain, palette, segments, i, outPos, outColor));
        try {
            pool.submit(all).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("esfera do corpo interrompida", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("falha ao gerar a esfera do corpo", e.getCause());
        }
    }

    private static void fillRing(PlanetTerrain terrain, PlanetMeshGeometry.Palette palette, int segments, int ring,
            float[] outPos, int[] outColor) {
        PlanetDefinition planet = terrain.planet();
        double radius = planet.radius();
        int sea = planet.seaLevel();
        double lat = ringLatitude(segments, ring);
        double cosLat = Math.cos(lat);
        double sinLat = Math.sin(lat);
        double z = lat * radius;
        double cell = 2.0 * Math.PI * radius / segments;
        boolean supersample = cell >= SUPERSAMPLE_MIN_CELL;
        // perto dos polos o arco de longitude encolhe; o piso evita explodir (além da calota o terreno é constante)
        double lonScale = 1.0 / Math.max(cosLat, 0.05);
        double[] extra = new double[SUPERSAMPLE_COS.length * 2];
        double[] height = new double[1];

        for (int j = 0; j < segments; j++) {
            double lon = columnLongitude(segments, j);
            double x = lon * radius;
            int extras = 0;
            if (supersample) {
                extras = SUPERSAMPLE_COS.length;
                for (int s = 0; s < extras; s++) {
                    double east = SUPERSAMPLE_RADIUS * cell * SUPERSAMPLE_SIN[s];
                    double north = SUPERSAMPLE_RADIUS * cell * SUPERSAMPLE_COS[s];
                    extra[s * 2] = x + east * lonScale;
                    extra[s * 2 + 1] = z + north;
                }
            }
            int argb = PlanetMeshGeometry.shadeVertex(terrain, palette, x, z, extra, extras, height);
            double shell = radius + (height[0] - PlanetMeshGeometry.SINK - sea);
            int v = ring * segments + j;
            outPos[v * 3] = (float) (shell * cosLat * Math.cos(lon));
            outPos[v * 3 + 1] = (float) (shell * sinLat);
            outPos[v * 3 + 2] = (float) (shell * cosLat * Math.sin(lon));
            outColor[v] = argb;
        }
    }

    /** Índices (triângulos) da grade; o segmento seguinte do último é o 0. Orientação irrelevante (sem culling). */
    public static void fillIndices(int segments, int[] out) {
        if (out.length < indexCount(segments)) {
            throw new IllegalArgumentException("array de índices pequeno");
        }
        int n = 0;
        for (int i = 0; i < rings(segments) - 1; i++) {
            int top = i * segments;
            int bottom = (i + 1) * segments;
            for (int j = 0; j < segments; j++) {
                int j1 = (j + 1) % segments;
                out[n++] = top + j;
                out[n++] = bottom + j;
                out[n++] = top + j1;
                out[n++] = top + j1;
                out[n++] = bottom + j;
                out[n++] = bottom + j1;
            }
        }
    }
}
