package com.spacehorizons.client.planetmesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ForkJoinPool;

import org.junit.jupiter.api.Test;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrain;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.SurfaceType;
import com.spacehorizons.planet.terrain.SphericalTerrain;

class PlanetMeshGeometryTest {
    private static final PlanetDefinition EARTH = Planets.EARTH;
    private static final double R = EARTH.radius();
    private static final PlanetMeshGeometry.Palette PALETTE = palette();

    private static PlanetMeshGeometry.Palette palette() {
        int[] land = new int[SurfaceType.values().length];
        for (int i = 0; i < land.length; i++) {
            land[i] = 0x102030 + i;
        }
        return new PlanetMeshGeometry.Palette(land, 0x3F76E4, 0x1A3A8C);
    }

    /** Planeta plano: tudo oceano no nível do mar. Deixa a geometria da esfera pura para conferir. */
    private static PlanetTerrain flatOcean() {
        return new PlanetTerrain() {
            @Override
            public PlanetDefinition planet() {
                return EARTH;
            }

            @Override
            public int surfaceHeight(int blockX, int blockZ) {
                return EARTH.seaLevel() - 20;
            }

            @Override
            public SurfaceType surfaceType(int blockX, int blockZ) {
                return SurfaceType.OCEAN;
            }
        };
    }

    private static PlanetMeshGeometry.Layout layout(double rMin) {
        return PlanetMeshGeometry.Layout.create(rMin, PlanetMeshGeometry.outerRadius(EARTH), 96, 400);
    }

    @Test
    void layoutHasExpectedCounts() {
        PlanetMeshGeometry.Layout layout = layout(4000.0);
        assertEquals(layout.rings() * layout.segments(), layout.vertexCount());
        assertEquals((layout.rings() - 1) * layout.segments() * 6, layout.indexCount());
        assertEquals(4000.0, layout.ringRadius(0), 1e-9);
        assertEquals(layout.rMax(), layout.ringRadius(layout.rings() - 1), 1e-6);
        for (int i = 1; i < layout.rings(); i++) {
            double ratio = layout.ringRadius(i) / layout.ringRadius(i - 1);
            assertTrue(ratio > 1.0 && ratio < 1.1, "razão entre anéis " + ratio);
        }
    }

    @Test
    void fillProducesFiniteVerticesAndExpectedCount() {
        PlanetMeshGeometry.Layout layout = layout(4000.0);
        float[] pos = new float[layout.vertexCount() * 3];
        int[] col = new int[layout.vertexCount()];
        PlanetMeshGeometry.fill(new SphericalTerrain(EARTH), PALETTE, layout, 1234.0, 200.0, -3210.0, pos, col, null);
        for (float v : pos) {
            assertTrue(Float.isFinite(v), "coordenada não finita");
        }
        // vértice de terra tem alfa 0; água tem alfa 255
        boolean sawWater = false;
        boolean sawLand = false;
        for (int c : col) {
            int alpha = c >>> 24;
            assertTrue(alpha == 0 || alpha == PlanetMeshGeometry.WATER_ALPHA, "alfa inesperado " + alpha);
            sawWater |= alpha == PlanetMeshGeometry.WATER_ALPHA;
            sawLand |= alpha == 0;
        }
        assertTrue(sawWater && sawLand, "o planeta deveria ter oceano e terra");
    }

    @Test
    void outerRingLiesOnTheSphere() {
        double camY = 300.0;
        double camAlt = camY - EARTH.seaLevel();
        PlanetMeshGeometry.Layout layout = layout(4000.0);
        float[] pos = new float[layout.vertexCount() * 3];
        int[] col = new int[layout.vertexCount()];
        PlanetMeshGeometry.fill(flatOcean(), PALETTE, layout, -500.0, camY, 700.0, pos, col, null);

        // centro do planeta relativo à câmera: R + altitude abaixo dela
        double cy = -(R + camAlt);
        // oceano plano: toda a malha está a R - SINK do centro (h = nível do mar, rebaixado)
        for (int ring = 0; ring < layout.rings(); ring++) {
            int v = ring * layout.segments();
            double dist = Math.sqrt(sq(pos[v * 3]) + sq(pos[v * 3 + 1] - cy) + sq(pos[v * 3 + 2]));
            assertEquals(R - PlanetMeshGeometry.SINK, dist, 0.25, "anel " + ring);
        }

        // o anel externo está em ~0,98·π da esfera: o ponto mais baixo fica perto do antípoda (y ~ -2R)
        int outer = (layout.rings() - 1) * layout.segments();
        double minY = Double.MAX_VALUE;
        for (int j = 0; j < layout.segments(); j++) {
            minY = Math.min(minY, pos[(outer + j) * 3 + 1]);
        }
        double expectedY = (R - PlanetMeshGeometry.SINK) * Math.cos(PlanetMeshGeometry.OUTER_FRACTION * Math.PI) - R - camAlt;
        assertEquals(expectedY, minY, 0.5);
        assertTrue(minY < -1.9 * R - camAlt, "anel externo deveria estar quase no antípoda");
    }

    @Test
    void ringsAreNotMeshedAcrossThePole() {
        // calota polar: além do limite de latitude a malha repete a coluna e fica de gelo
        double camZ = EARTH.maxLatitudeBlocks() + 5000.0;
        PlanetMeshGeometry.Layout layout = layout(4000.0);
        float[] pos = new float[layout.vertexCount() * 3];
        int[] col = new int[layout.vertexCount()];
        PlanetMeshGeometry.fill(new SphericalTerrain(EARTH), PALETTE, layout, 0.0, 400.0, camZ, pos, col, null);
        int ice = 0x102030 + SurfaceType.POLAR_ICE.ordinal();
        // o primeiro anel (r = 4000) está todo além do limite quando camZ - r > maxLat
        for (int j = 0; j < layout.segments(); j++) {
            double z = camZ + 4000.0 * Math.sin(2.0 * Math.PI * j / layout.segments());
            if (z > EARTH.maxLatitudeBlocks() + 1) {
                assertEquals(ice, col[j], "segmento " + j);
            }
        }
    }

    @Test
    void parallelFillMatchesSequential() {
        PlanetMeshGeometry.Layout layout = layout(3000.0);
        int n = layout.vertexCount();
        float[] seqPos = new float[n * 3];
        int[] seqCol = new int[n];
        float[] parPos = new float[n * 3];
        int[] parCol = new int[n];
        PlanetTerrain terrain = new SphericalTerrain(EARTH);
        PlanetMeshGeometry.fill(terrain, PALETTE, layout, 10.0, 250.0, 20.0, seqPos, seqCol, null);
        ForkJoinPool pool = new ForkJoinPool(2);
        try {
            PlanetMeshGeometry.fill(terrain, PALETTE, layout, 10.0, 250.0, 20.0, parPos, parCol, pool);
        } finally {
            pool.shutdown();
        }
        for (int i = 0; i < seqPos.length; i++) {
            assertEquals(seqPos[i], parPos[i]);
        }
        for (int i = 0; i < n; i++) {
            assertEquals(seqCol[i], parCol[i]);
        }
    }

    @Test
    void indicesStayInRangeAndCloseTheRing() {
        PlanetMeshGeometry.Layout layout = layout(4000.0);
        int[] idx = new int[layout.indexCount()];
        PlanetMeshGeometry.fillIndices(layout, idx);
        for (int i : idx) {
            assertTrue(i >= 0 && i < layout.vertexCount(), "índice fora da grade: " + i);
        }
        // última célula do primeiro anel usa o segmento 0 como vizinho
        int last = (layout.segments() - 1) * 6;
        assertEquals(layout.segments() - 1, idx[last]);
        assertEquals(0, idx[last + 2]);
    }

    private static double sq(double v) {
        return v * v;
    }
}
