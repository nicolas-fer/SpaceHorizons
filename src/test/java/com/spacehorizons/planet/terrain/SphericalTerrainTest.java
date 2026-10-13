package com.spacehorizons.planet.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrain;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.SurfaceType;

class SphericalTerrainTest {
    private static final PlanetDefinition EARTH = Planets.EARTH;
    private static final int C = EARTH.circumferenceBlocks();

    private final PlanetTerrain terrain = new SphericalTerrain(EARTH);

    @Test
    void heightIsExactlyPeriodicInX() {
        Random rnd = new Random(1);
        for (int i = 0; i < 20_000; i++) {
            int x = rnd.nextInt(200_000) - 100_000;
            int z = rnd.nextInt(2 * EARTH.maxLatitudeBlocks()) - EARTH.maxLatitudeBlocks();
            int h = terrain.surfaceHeight(x, z);
            assertEquals(h, terrain.surfaceHeight(x + C, z), "x=" + x + " z=" + z);
            assertEquals(h, terrain.surfaceHeight(x - C, z), "x=" + x + " z=" + z);
            assertEquals(terrain.surfaceType(x, z), terrain.surfaceType(x + 3 * C, z));
        }
    }

    @Test
    void typeIsExactlyPeriodicInX() {
        Random rnd = new Random(7);
        for (int i = 0; i < 5_000; i++) {
            int x = rnd.nextInt(C) - C / 2;
            int z = rnd.nextInt(EARTH.maxLatitudeBlocks());
            assertEquals(terrain.surfaceType(x, z), terrain.surfaceType(x - 2 * C, z));
        }
    }

    @Test
    void isDeterministicAndSeedDependent() {
        PlanetTerrain again = new SphericalTerrain(EARTH);
        PlanetDefinition other = new PlanetDefinition(EARTH.id(), EARTH.seed() + 1, C, EARTH.seaLevel(),
                EARTH.maxLatitudeDegrees(), EARTH.terrainAmplitude());
        PlanetTerrain otherTerrain = new SphericalTerrain(other);
        int diff = 0;
        for (int x = -2_000; x < 2_000; x += 37) {
            for (int z = -1_000; z < 1_000; z += 53) {
                assertEquals(terrain.surfaceHeight(x, z), again.surfaceHeight(x, z));
                if (terrain.surfaceHeight(x, z) != otherTerrain.surfaceHeight(x, z)) {
                    diff++;
                }
            }
        }
        assertTrue(diff > 0, "seeds diferentes deveriam gerar relevos diferentes");
    }

    @Test
    void heightsAreSensibleAndWorldHasOceanAndLand() {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int ocean = 0;
        int land = 0;
        int total = 0;
        for (int x = 0; x < C; x += 211) {
            for (int z = -EARTH.maxLatitudeBlocks(); z <= EARTH.maxLatitudeBlocks(); z += 307) {
                int h = terrain.surfaceHeight(x, z);
                min = Math.min(min, h);
                max = Math.max(max, h);
                SurfaceType t = terrain.surfaceType(x, z);
                if (t == SurfaceType.OCEAN) {
                    ocean++;
                } else {
                    land++;
                }
                total++;
            }
        }
        assertTrue(min >= -60 && max <= 300, "fora da faixa: " + min + ".." + max);
        assertTrue(min < EARTH.seaLevel() - 10, "deveria ter fundo oceânico, min=" + min);
        assertTrue(max > EARTH.seaLevel() + 20, "deveria ter montanhas, max=" + max);
        assertTrue(ocean > total / 20 && land > total / 20, "oceano=" + ocean + " terra=" + land);
    }

    @Test
    void seamAcrossWrapIsContinuous() {
        // vizinhos na costura x = C/2 - 1 e x = -C/2 são o mesmo meridiano: não podem dar salto
        int half = C / 2;
        int worst = 0;
        for (int z = -3_000; z <= 3_000; z += 17) {
            int a = terrain.surfaceHeight(half - 1, z);
            int b = terrain.surfaceHeight(-half, z);
            worst = Math.max(worst, Math.abs(a - b));
        }
        assertTrue(worst <= 3, "salto na costura: " + worst);
    }

    @Test
    void adjacentBlocksDoNotJump() {
        Random rnd = new Random(3);
        int worst = 0;
        for (int i = 0; i < 20_000; i++) {
            int x = rnd.nextInt(C) - C / 2;
            int z = rnd.nextInt(2 * 4_000) - 4_000;
            worst = Math.max(worst, Math.abs(terrain.surfaceHeight(x + 1, z) - terrain.surfaceHeight(x, z)));
        }
        assertTrue(worst <= 3, "degrau de 1 bloco grande demais: " + worst);
    }

    @Test
    void polesAreIceWall() {
        int z = EARTH.maxLatitudeBlocks() - 2;
        for (int x = 0; x < C; x += 997) {
            assertEquals(SurfaceType.POLAR_ICE, terrain.surfaceType(x, z));
            assertEquals(SurfaceType.POLAR_ICE, terrain.surfaceType(x, -z));
            assertTrue(terrain.surfaceHeight(x, z) > EARTH.seaLevel() + 50, "parede polar baixa");
        }
    }

    @Test
    void equatorIsNotIce() {
        assertNotEquals(SurfaceType.POLAR_ICE, terrain.surfaceType(0, 0));
    }
}
