package com.spacehorizons.planet.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.SurfaceType;
import com.spacehorizons.planet.TerrainStyle;

class MoonTerrainTest {
    private static final PlanetDefinition MOON = Planets.MOON;
    private static final int C = MOON.circumferenceBlocks();
    private static final int MIN_Y = -64;
    private static final int MAX_Y = MIN_Y + 384 - 1;

    private final MoonTerrain terrain = new MoonTerrain(MOON);

    @Test
    void usesMoonStyleWithoutOcean() {
        assertEquals(TerrainStyle.MOON, MOON.style());
        assertTrue(!MOON.hasOcean());
    }

    @Test
    void heightAndTypeAreExactlyPeriodicInX() {
        Random rnd = new Random(11);
        for (int i = 0; i < 10_000; i++) {
            int x = rnd.nextInt(60_000) - 30_000;
            int z = rnd.nextInt(2 * MOON.maxLatitudeBlocks()) - MOON.maxLatitudeBlocks();
            assertEquals(terrain.surfaceHeight(x, z), terrain.surfaceHeight(x + C, z), "x=" + x + " z=" + z);
            assertEquals(terrain.surfaceHeight(x, z), terrain.surfaceHeight(x - 2 * C, z), "x=" + x + " z=" + z);
            assertEquals(terrain.surfaceType(x, z), terrain.surfaceType(x + 3 * C, z));
        }
    }

    @Test
    void isDeterministicAndSeedDependent() {
        MoonTerrain again = new MoonTerrain(MOON);
        PlanetDefinition other = new PlanetDefinition(MOON.id(), MOON.seed() + 1, C, MOON.seaLevel(),
                MOON.maxLatitudeDegrees(), MOON.terrainAmplitude(), TerrainStyle.MOON, MOON.surfaceGravity());
        MoonTerrain otherTerrain = new MoonTerrain(other);
        int diff = 0;
        for (int x = -4_000; x < 4_000; x += 41) {
            for (int z = -2_000; z < 2_000; z += 59) {
                assertEquals(terrain.surfaceHeight(x, z), again.surfaceHeight(x, z));
                assertEquals(terrain.surfaceType(x, z), again.surfaceType(x, z));
                if (terrain.surfaceHeight(x, z) != otherTerrain.surfaceHeight(x, z)) {
                    diff++;
                }
            }
        }
        assertTrue(diff > 0, "seeds diferentes deveriam gerar relevos diferentes");
    }

    @Test
    void heightsStayInsideTheWorldAndAboveSeaReference() {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int x = -C / 2; x < C / 2; x += 53) {
            for (int z = -MOON.maxLatitudeBlocks(); z <= MOON.maxLatitudeBlocks(); z += 61) {
                int h = terrain.surfaceHeight(x, z);
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        }
        assertTrue(min > MIN_Y && max < MAX_Y, "fora do mundo: " + min + ".." + max);
        // a malha orbital lê altura < seaLevel como água: a Lua nunca pode descer a esse ponto
        assertTrue(min >= MOON.seaLevel() + 4, "altura abaixo do piso lunar: " + min);
        assertTrue(max - min > 40, "relevo plano demais: " + min + ".." + max);
    }

    @Test
    void allFourSurfaceTypesAppearAndNoEarthTypes() {
        Map<SurfaceType, Integer> counts = new EnumMap<>(SurfaceType.class);
        int total = 0;
        for (int x = -C / 2; x < C / 2; x += 29) {
            for (int z = -MOON.maxLatitudeBlocks(); z <= MOON.maxLatitudeBlocks(); z += 31) {
                counts.merge(terrain.surfaceType(x, z), 1, Integer::sum);
                total++;
            }
        }
        for (SurfaceType type : SurfaceType.usedBy(TerrainStyle.MOON)) {
            int n = counts.getOrDefault(type, 0);
            assertTrue(n > total / 200, type + " raro demais: " + n + " de " + total);
        }
        for (SurfaceType type : counts.keySet()) {
            assertTrue(SurfaceType.usedBy(TerrainStyle.MOON).contains(type), "tipo da Terra na Lua: " + type);
        }
    }

    @Test
    void cratersExistWithRaisedRimsAndLowFloors() {
        double minDelta = 0;
        double maxDelta = 0;
        int rims = 0;
        int bowls = 0;
        for (int x = -C / 2; x < C / 2; x += 17) {
            for (int z = -3_000; z <= 3_000; z += 19) {
                double d = terrain.craterDelta(x, z);
                minDelta = Math.min(minDelta, d);
                maxDelta = Math.max(maxDelta, d);
                if (d > 1.5) {
                    rims++;
                }
                if (d < -5) {
                    bowls++;
                }
            }
        }
        assertTrue(rims > 200, "poucas bordas elevadas: " + rims);
        assertTrue(bowls > 200, "poucas bacias fundas: " + bowls);
        assertTrue(maxDelta > 5 && minDelta < -15, "crateras fracas: " + minDelta + ".." + maxDelta);
    }

    @Test
    void aCraterRimIsHigherThanItsFloor() {
        // acha o ponto mais fundo e anda em +X: a altura sobe até a borda
        int bestX = 0;
        int bestZ = 0;
        double best = 0;
        for (int x = -C / 2; x < C / 2; x += 23) {
            for (int z = -1_500; z <= 1_500; z += 23) {
                double d = terrain.craterDelta(x, z);
                if (d < best) {
                    best = d;
                    bestX = x;
                    bestZ = z;
                }
            }
        }
        assertTrue(best < -20, "nenhuma cratera funda encontrada: " + best);
        double rim = Double.NEGATIVE_INFINITY;
        for (int dx = 0; dx <= 900; dx += 3) {
            rim = Math.max(rim, terrain.craterDelta(bestX + dx, bestZ));
        }
        assertTrue(rim > best + 15, "a borda deveria ficar bem acima do fundo: fundo=" + best + " borda=" + rim);
    }

    @Test
    void craterFloorsAreLowerThanRegolithAndHighlandsOnAverage() {
        long floorSum = 0;
        long floorN = 0;
        long otherSum = 0;
        long otherN = 0;
        for (int x = -C / 2; x < C / 2; x += 37) {
            for (int z = -3_000; z <= 3_000; z += 41) {
                int h = terrain.surfaceHeight(x, z);
                SurfaceType t = terrain.surfaceType(x, z);
                if (t == SurfaceType.CRATER_FLOOR) {
                    floorSum += h;
                    floorN++;
                } else if (t == SurfaceType.REGOLITH || t == SurfaceType.HIGHLAND) {
                    otherSum += h;
                    otherN++;
                }
            }
        }
        assertTrue(floorN > 0 && otherN > 0);
        assertTrue((double) floorSum / floorN < (double) otherSum / otherN,
                "fundos de cratera deveriam ficar abaixo do regolito e das terras altas");
    }

    @Test
    void seamAcrossWrapIsContinuous() {
        int half = C / 2;
        int worst = 0;
        for (int z = -3_000; z <= 3_000; z += 13) {
            worst = Math.max(worst, Math.abs(terrain.surfaceHeight(half - 1, z) - terrain.surfaceHeight(-half, z)));
        }
        assertTrue(worst <= 4, "salto na costura: " + worst);
    }

    @Test
    void adjacentBlocksDoNotJumpTooMuch() {
        Random rnd = new Random(5);
        int worst = 0;
        for (int i = 0; i < 30_000; i++) {
            int x = rnd.nextInt(C) - C / 2;
            int z = rnd.nextInt(2 * 3_000) - 3_000;
            worst = Math.max(worst, Math.abs(terrain.surfaceHeight(x + 1, z) - terrain.surfaceHeight(x, z)));
            worst = Math.max(worst, Math.abs(terrain.surfaceHeight(x, z + 1) - terrain.surfaceHeight(x, z)));
        }
        assertTrue(worst <= 4, "degrau de 1 bloco grande demais: " + worst);
    }

    @Test
    void polesAreAHighWallOfHighland() {
        int z = MOON.maxLatitudeBlocks() - 2;
        for (int x = 0; x < C; x += 499) {
            assertEquals(SurfaceType.HIGHLAND, terrain.surfaceType(x, z));
            assertEquals(SurfaceType.HIGHLAND, terrain.surfaceType(x, -z));
            assertTrue(terrain.surfaceHeight(x, z) > MOON.seaLevel() + 80, "parede polar baixa");
        }
    }

    @Test
    void everySurfaceTypeBelongsToExactlyOneStyle() {
        for (SurfaceType type : SurfaceType.values()) {
            int styles = 0;
            for (TerrainStyle style : TerrainStyle.values()) {
                if (SurfaceType.usedBy(style).contains(type)) {
                    styles++;
                }
            }
            assertEquals(1, styles, type + " deve pertencer a exatamente um estilo");
        }
    }
}
