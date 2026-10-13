package com.spacehorizons.client.planetmesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ForkJoinPool;

import org.junit.jupiter.api.Test;

import com.spacehorizons.planet.CurvatureMath;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrain;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.SurfaceType;
import com.spacehorizons.planet.terrain.SphericalTerrain;

class PlanetMeshGeometryTest {
    private static final PlanetDefinition EARTH = Planets.EARTH;
    private static final double R = EARTH.radius();
    private static final PlanetMeshGeometry.Palette PALETTE = palette();
    private static final double PIXEL = PlanetMeshGeometry.Layout.DEFAULT_PIXEL_ANGLE;

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

    private static PlanetMeshGeometry.Layout uniform(double rMin) {
        return PlanetMeshGeometry.Layout.uniform(rMin, PlanetMeshGeometry.outerRadius(EARTH), 96, 400);
    }

    private static PlanetMeshGeometry.Layout adaptive(double altitude, int budget) {
        double outer = PlanetMeshRenderer.outerRadius(EARTH, altitude);
        return PlanetMeshGeometry.Layout.adaptive(R, altitude, 128.0, outer, PIXEL, budget);
    }

    @Test
    void uniformLayoutHasExpectedCounts() {
        PlanetMeshGeometry.Layout layout = uniform(4000.0);
        assertEquals(layout.rings() * 96, layout.vertexCount());
        assertEquals((layout.rings() - 1) * 96 * 6, layout.indexCount());
        assertEquals(4000.0, layout.ringRadius(0), 1e-9);
        assertEquals(PlanetMeshGeometry.outerRadius(EARTH), layout.rMax(), 1e-6);
        for (int i = 1; i < layout.rings(); i++) {
            double ratio = layout.ringRadius(i) / layout.ringRadius(i - 1);
            assertTrue(ratio > 1.0 && ratio < 1.1, "razão entre anéis " + ratio);
        }
    }

    @Test
    void adaptiveLayoutRespectsBudgetAndStitchingRule() {
        for (double altitude : new double[] {600, 2_000, 5_000, 15_000, 40_000}) {
            PlanetMeshGeometry.Layout layout = adaptive(altitude, 110_000);
            assertTrue(layout.vertexCount() <= 110_000, "orçamento estourado a " + altitude + ": " + layout.vertexCount());
            assertTrue(layout.rings() >= 2);
            for (int i = 1; i < layout.rings(); i++) {
                assertTrue(layout.ringRadius(i) > layout.ringRadius(i - 1));
                int a = layout.segments(i - 1);
                int b = layout.segments(i);
                assertTrue(b == a || b == 2 * a, "anéis " + (i - 1) + "/" + i + ": " + a + " -> " + b);
            }
            assertEquals(128.0, layout.rMin(), 1e-9);
            assertTrue(layout.rMax() <= PlanetMeshGeometry.outerRadius(EARTH) + 1e-6);
        }
    }

    @Test
    void adaptiveCellsAreFinerThanTheOldUniformGridAtTheLimb() {
        // grade antiga: 384 segmentos => célula tangencial de 2πr/384 (164 blocos a r = 10.000)
        PlanetMeshGeometry.Layout layout = adaptive(15_000, 110_000);
        int ring = layout.rings() - 1;
        double cell = 2.0 * Math.PI * layout.ringRadius(ring) / layout.segments(ring);
        double old = 2.0 * Math.PI * layout.ringRadius(ring) / 384.0;
        assertTrue(cell < 0.8 * old, "célula do anel externo " + cell + " vs grade antiga " + old);
        // e o horizonte limita o raio externo: bem abaixo do antípoda
        assertTrue(layout.rMax() < 0.5 * PlanetMeshGeometry.outerRadius(EARTH));
    }

    @Test
    void smallerBudgetGrowsCellsInsteadOfOverflowing() {
        PlanetMeshGeometry.Layout rich = adaptive(15_000, 110_000);
        PlanetMeshGeometry.Layout poor = adaptive(15_000, 20_000);
        assertTrue(poor.vertexCount() <= 20_000);
        assertTrue(poor.vertexCount() < rich.vertexCount());
    }

    @Test
    void layoutRejectsInvalidStitching() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlanetMeshGeometry.Layout(new double[] {100, 200}, new int[] {48, 144}));
        assertThrows(IllegalArgumentException.class,
                () -> new PlanetMeshGeometry.Layout(new double[] {200, 100}, new int[] {48, 48}));
    }

    @Test
    void fillProducesFiniteVerticesAndPlausibleMask() {
        PlanetMeshGeometry.Layout layout = adaptive(15_000, 40_000);
        float[] pos = new float[layout.vertexCount() * 3];
        int[] col = new int[layout.vertexCount()];
        PlanetMeshGeometry.fill(new SphericalTerrain(EARTH), PALETTE, layout, 1234.0, 15_063.0, -3210.0, 2_000.0, pos, col, null);
        for (float v : pos) {
            assertTrue(Float.isFinite(v), "coordenada não finita");
        }
        boolean sawWater = false;
        boolean sawLand = false;
        boolean sawCoast = false;
        for (int c : col) {
            int alpha = c >>> 24;
            sawWater |= alpha == PlanetMeshGeometry.WATER_ALPHA;
            sawLand |= alpha == 0;
            sawCoast |= alpha > 0 && alpha < PlanetMeshGeometry.WATER_ALPHA;
        }
        assertTrue(sawWater && sawLand, "o planeta deveria ter oceano e terra");
        assertTrue(sawCoast, "o supersampling deveria dar máscara fracionária na costa");
    }

    @Test
    void uniformColorsAreUnchangedBySupersampling() {
        // oceano uniforme: toda amostra é água, então a média é a própria cor e a máscara é cheia
        // (a 5.000 de altitude a malha não chega à calota polar, que é gelo e não água)
        PlanetMeshGeometry.Layout layout = adaptive(5_000, 20_000);
        float[] pos = new float[layout.vertexCount() * 3];
        int[] col = new int[layout.vertexCount()];
        PlanetMeshGeometry.fill(flatOcean(), PALETTE, layout, 0.0, 5_063.0, 0.0, 2_000.0, pos, col, null);
        int expected = PlanetMeshGeometry.waterColor(PALETTE, 20);
        for (int c : col) {
            assertEquals(expected, c);
        }
    }

    @Test
    void nearTheGroundTheMeshMatchesCurvatureMath() {
        // sem mistura (altitude baixa) cada vértice cai exatamente onde chunks e LODs o colocariam
        double camY = 300.0;
        double camAlt = camY - EARTH.seaLevel();
        PlanetMeshGeometry.Layout layout = uniform(3000.0);
        float[] pos = new float[layout.vertexCount() * 3];
        int[] col = new int[layout.vertexCount()];
        PlanetMeshGeometry.fill(flatOcean(), PALETTE, layout, 700.0, camY, -900.0, 2_000.0, pos, col, null);
        double[] curved = new double[3];
        for (int ring = 0; ring < layout.rings(); ring += 7) {
            double r = layout.ringRadius(ring);
            for (int j = 0; j < layout.segments(ring); j += 11) {
                double phi = 2.0 * Math.PI * j / layout.segments(ring);
                double height = EARTH.seaLevel() - PlanetMeshGeometry.SINK;
                CurvatureMath.curve(R, camAlt, r * Math.cos(phi), height - camY, r * Math.sin(phi), curved);
                int v = layout.vertexOffset(ring) + j;
                assertEquals(curved[0], pos[v * 3], 0.05);
                assertEquals(curved[1], pos[v * 3 + 1], 0.05);
                assertEquals(curved[2], pos[v * 3 + 2], 0.05);
            }
        }
    }

    @Test
    void ringsLieOnTheSphereAndOuterRingReachesTheAntipode() {
        double camY = 300.0;
        double camAlt = camY - EARTH.seaLevel();
        PlanetMeshGeometry.Layout layout = uniform(4000.0);
        float[] pos = new float[layout.vertexCount() * 3];
        int[] col = new int[layout.vertexCount()];
        PlanetMeshGeometry.fill(flatOcean(), PALETTE, layout, -500.0, camY, 700.0, 2_000.0, pos, col, null);

        double cy = -(R + camAlt);
        for (int ring = 0; ring < layout.rings(); ring++) {
            int v = layout.vertexOffset(ring);
            double dist = Math.sqrt(sq(pos[v * 3]) + sq(pos[v * 3 + 1] - cy) + sq(pos[v * 3 + 2]));
            assertEquals(R - PlanetMeshGeometry.SINK, dist, 0.25, "anel " + ring);
        }

        int outer = layout.vertexOffset(layout.rings() - 1);
        double minY = Double.MAX_VALUE;
        for (int j = 0; j < layout.segments(layout.rings() - 1); j++) {
            minY = Math.min(minY, pos[(outer + j) * 3 + 1]);
        }
        double expectedY = (R - PlanetMeshGeometry.SINK) * Math.cos(PlanetMeshGeometry.OUTER_FRACTION * Math.PI) - R - camAlt;
        assertEquals(expectedY, minY, 0.5);
        assertTrue(minY < -1.9 * R - camAlt, "anel externo deveria estar quase no antípoda");
    }

    @Test
    void highAltitudeMeshStaysOnTheSphereToo() {
        // com a mistura para a carta lat/lon ativa, a posição ainda é a direção do nadir (só o terreno amostrado muda)
        double camY = 15_063.0;
        double camAlt = camY - EARTH.seaLevel();
        PlanetMeshGeometry.Layout layout = adaptive(camAlt, 30_000);
        float[] pos = new float[layout.vertexCount() * 3];
        int[] col = new int[layout.vertexCount()];
        PlanetMeshGeometry.fill(flatOcean(), PALETTE, layout, 8_000.0, camY, 4_000.0, 2_000.0, pos, col, null);
        double cy = -(R + camAlt);
        for (int v = 0; v < layout.vertexCount(); v += 13) {
            double dist = Math.sqrt(sq(pos[v * 3]) + sq(pos[v * 3 + 1] - cy) + sq(pos[v * 3 + 2]));
            assertEquals(R - PlanetMeshGeometry.SINK, dist, 0.5);
        }
    }

    @Test
    void polarCapIsIce() {
        // calota polar: além do limite de latitude a malha repete a coluna e fica de gelo
        double camZ = EARTH.maxLatitudeBlocks() + 5000.0;
        PlanetMeshGeometry.Layout layout = uniform(4000.0);
        float[] pos = new float[layout.vertexCount() * 3];
        int[] col = new int[layout.vertexCount()];
        PlanetMeshGeometry.fill(new SphericalTerrain(EARTH), PALETTE, layout, 0.0, 400.0, camZ, 2_000.0, pos, col, null);
        int ice = 0x102030 + SurfaceType.POLAR_ICE.ordinal();
        // o primeiro anel (r = 4000) está todo além do limite quando camZ - r > maxLat
        for (int j = 0; j < layout.segments(0); j++) {
            double z = camZ + 4000.0 * Math.sin(2.0 * Math.PI * j / layout.segments(0));
            if (z > EARTH.maxLatitudeBlocks() + 1) {
                assertEquals(ice, col[j], "segmento " + j);
            }
        }
    }

    @Test
    void parallelFillMatchesSequential() {
        PlanetMeshGeometry.Layout layout = adaptive(15_000, 15_000);
        int n = layout.vertexCount();
        float[] seqPos = new float[n * 3];
        int[] seqCol = new int[n];
        float[] parPos = new float[n * 3];
        int[] parCol = new int[n];
        PlanetTerrain terrain = new SphericalTerrain(EARTH);
        PlanetMeshGeometry.fill(terrain, PALETTE, layout, 10.0, 15_063.0, 20.0, 2_000.0, seqPos, seqCol, null);
        ForkJoinPool pool = new ForkJoinPool(2);
        try {
            PlanetMeshGeometry.fill(terrain, PALETTE, layout, 10.0, 15_063.0, 20.0, 2_000.0, parPos, parCol, pool);
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
        PlanetMeshGeometry.Layout layout = uniform(4000.0);
        int[] idx = new int[layout.indexCount()];
        PlanetMeshGeometry.fillIndices(layout, idx);
        for (int i : idx) {
            assertTrue(i >= 0 && i < layout.vertexCount(), "índice fora da grade: " + i);
        }
        // última célula do primeiro anel usa o segmento 0 como vizinho
        int last = (layout.segments(0) - 1) * 6;
        assertEquals(layout.segments(0) - 1, idx[last]);
        assertEquals(0, idx[last + 2]);
    }

    @Test
    void adaptiveIndicesUseEveryVertexAndNoDegenerateTriangle() {
        PlanetMeshGeometry.Layout layout = adaptive(15_000, 40_000);
        int[] idx = new int[layout.indexCount()];
        PlanetMeshGeometry.fillIndices(layout, idx);
        boolean[] used = new boolean[layout.vertexCount()];
        for (int t = 0; t < idx.length; t += 3) {
            assertTrue(idx[t] != idx[t + 1] && idx[t + 1] != idx[t + 2] && idx[t] != idx[t + 2], "triângulo degenerado " + t / 3);
            for (int k = 0; k < 3; k++) {
                assertTrue(idx[t + k] >= 0 && idx[t + k] < layout.vertexCount());
                used[idx[t + k]] = true;
            }
        }
        for (int v = 0; v < used.length; v++) {
            assertTrue(used[v], "vértice órfão " + v);
        }
    }

    private static double sq(double v) {
        return v * v;
    }
}
