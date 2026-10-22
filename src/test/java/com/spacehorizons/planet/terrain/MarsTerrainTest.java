package com.spacehorizons.planet.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.SurfaceType;
import com.spacehorizons.planet.TerrainStyle;
import com.spacehorizons.planet.spec.PlanetSpec;

/** Marte, o primeiro planeta feito só de parâmetros (data/spacehorizons/planet/mars.json). */
class MarsTerrainTest {
    private static final PlanetDefinition MARS = Planets.MARS;
    private static final PlanetSpec SPEC = Planets.spec(MARS);
    private static final int C = MARS.circumferenceBlocks();
    private static final int LAT = MARS.maxLatitudeBlocks();
    private static final int MIN_Y = -64;
    private static final int MAX_Y = MIN_Y + 384 - 1;

    private final ParametricTerrain terrain = new ParametricTerrain(MARS);

    @Test
    void marsIsAParametricPlanetOnTheCompressedScale() {
        assertEquals(TerrainStyle.PARAMETRIC, MARS.style());
        assertFalse(MARS.hasOcean());
        assertEquals(0.38, MARS.surfaceGravity(), 1e-12);
        // mesma razão de raios da real (0,532): entre a Lua (0,25) e a Terra do mod
        double ratio = MARS.radius() / Planets.EARTH.radius();
        assertEquals(0.53, ratio, 0.01);
        assertTrue(MARS.radius() > Planets.MOON.radius());
        assertEquals(0, C % 16);
        assertEquals(24_660, SPEC.dayLengthTicks());
        // atmosfera fina: existe para o céu, mas o traje trata como vácuo
        assertTrue(SPEC.atmosphere().model().present());
        assertFalse(SPEC.atmosphere().model().breathable());
        assertTrue(SPEC.atmosphere().model().needsOxygen(0));
    }

    @Test
    void heightAndTypeAreDeterministicAndExactlyPeriodicInX() {
        ParametricTerrain again = new ParametricTerrain(MARS);
        Random rnd = new Random(23);
        for (int i = 0; i < 8_000; i++) {
            int x = rnd.nextInt(2 * C) - C;
            int z = rnd.nextInt(2 * LAT) - LAT;
            int h = terrain.surfaceHeight(x, z);
            assertEquals(h, again.surfaceHeight(x, z));
            assertEquals(terrain.surfaceType(x, z), again.surfaceType(x, z));
            assertEquals(h, terrain.surfaceHeight(x + C, z), "x=" + x + " z=" + z);
            assertEquals(h, terrain.surfaceHeight(x - 3 * C, z), "x=" + x + " z=" + z);
            assertEquals(terrain.surfaceType(x, z), terrain.surfaceType(x + 2 * C, z));
        }
    }

    @Test
    void heightsStayInsideTheWorldAndTheConfiguredBand() {
        var relief = SPEC.terrain().relief().orElseThrow();
        int floor = MARS.seaLevel() + relief.minAboveSea();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int x = -C / 2; x < C / 2; x += 61) {
            for (int z = -LAT; z <= LAT; z += 67) {
                int h = terrain.surfaceHeight(x, z);
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        }
        assertTrue(min >= floor, "abaixo do piso: " + min);
        assertTrue(max <= relief.maxHeight(), "acima do teto: " + max);
        assertTrue(min > MIN_Y && max < MAX_Y, "fora do mundo: " + min + ".." + max);
        assertTrue(max - min > 120, "relevo plano demais para Marte (vulcões e crateras): " + min + ".." + max);
    }

    @Test
    void producesOnlyPaletteTypesAndAllOfThemAppear() {
        Map<SurfaceType, Integer> counts = new EnumMap<>(SurfaceType.class);
        int total = 0;
        for (int x = -C / 2; x < C / 2; x += 37) {
            for (int z = -LAT; z <= LAT; z += 41) {
                counts.merge(terrain.surfaceType(x, z), 1, Integer::sum);
                total++;
            }
        }
        var produced = SPEC.terrain().relief().orElseThrow().producedTypes();
        for (SurfaceType type : counts.keySet()) {
            assertTrue(produced.contains(type), "tipo fora do relevo: " + type);
            assertTrue(SPEC.surface().types().containsKey(type), "tipo fora da paleta: " + type);
        }
        for (SurfaceType type : produced) {
            assertTrue(counts.getOrDefault(type, 0) > total / 500, type + " raro demais: " + counts.get(type) + " de " + total);
        }
    }

    @Test
    void polarCapsAreIce() {
        int z = LAT - 2;
        for (int x = 0; x < C; x += 997) {
            assertEquals(SurfaceType.POLAR_ICE, terrain.surfaceType(x, z));
            assertEquals(SurfaceType.POLAR_ICE, terrain.surfaceType(x, -z));
        }
        assertTrue(terrain.surfaceType(0, 0) != SurfaceType.POLAR_ICE || terrain.surfaceType(5_000, 0) != SurfaceType.POLAR_ICE);
    }

    @Test
    void cratersAndVolcanoesExist() {
        double minCrater = 0;
        double maxVolcano = 0;
        int volcanicColumns = 0;
        for (int x = -C / 2; x < C / 2; x += 53) {
            for (int z = -LAT / 2; z <= LAT / 2; z += 59) {
                minCrater = Math.min(minCrater, terrain.craterDelta(x, z));
                maxVolcano = Math.max(maxVolcano, terrain.volcanoDelta(x, z));
                if (terrain.surfaceType(x, z) == SurfaceType.VOLCANIC) {
                    volcanicColumns++;
                }
            }
        }
        assertTrue(minCrater < -15, "crateras rasas demais: " + minCrater);
        assertTrue(maxVolcano > 60, "nenhum vulcão alto: " + maxVolcano);
        assertTrue(volcanicColumns > 50, "encostas vulcânicas raras: " + volcanicColumns);
    }

    @Test
    void adjacentBlocksDoNotJumpTooMuchAndTheSeamIsContinuous() {
        Random rnd = new Random(7);
        int worst = 0;
        for (int i = 0; i < 30_000; i++) {
            int x = rnd.nextInt(C) - C / 2;
            int z = rnd.nextInt(2 * (LAT - 100)) - (LAT - 100);
            worst = Math.max(worst, Math.abs(terrain.surfaceHeight(x + 1, z) - terrain.surfaceHeight(x, z)));
            worst = Math.max(worst, Math.abs(terrain.surfaceHeight(x, z + 1) - terrain.surfaceHeight(x, z)));
        }
        assertTrue(worst <= 4, "degrau de 1 bloco grande demais: " + worst);
        int seam = 0;
        for (int z = -3_000; z <= 3_000; z += 13) {
            seam = Math.max(seam, Math.abs(terrain.surfaceHeight(C / 2 - 1, z) - terrain.surfaceHeight(-C / 2, z)));
        }
        assertTrue(seam <= 4, "salto na costura: " + seam);
    }

    @Test
    void surfaceColorComesFromThePalette() {
        // regolito declarado no JSON: a cor de longe é a declarada, não a cinza lunar padrão
        for (int x = 0; x < 40_000; x += 101) {
            if (terrain.surfaceType(x, 0) == SurfaceType.REGOLITH) {
                assertEquals(SPEC.surface().types().get(SurfaceType.REGOLITH).color().orElseThrow(), terrain.surfaceColor(x, 0));
                return;
            }
        }
        throw new AssertionError("nenhum regolito no equador em 40.000 blocos");
    }
}
