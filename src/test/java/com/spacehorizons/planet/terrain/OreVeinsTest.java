package com.spacehorizons.planet.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.spec.OreVeinSpec;

/** Veios de minério de Marte (titânio): determinísticos, periódicos, raros, só na faixa de Y e em forma de veio. */
class OreVeinsTest {
    private static final List<OreVeinSpec> SPECS = Planets.spec(Planets.MARS).ores();
    private final OreVeins veins = new OreVeins(Planets.MARS, SPECS);

    @Test
    void marsHasTheTitaniumVeinWithAProvisionalFallback() {
        assertEquals(1, SPECS.size());
        OreVeinSpec titanium = SPECS.get(0);
        assertEquals("spacehorizons:mars_titanium_ore", titanium.block().get(0).toString());
        assertTrue(titanium.block().size() >= 2, "precisa de um bloco vanilla de reserva enquanto o minério não existe");
        assertTrue(Planets.spec(Planets.MOON).ores().isEmpty(), "a Lua continua sem veios");
    }

    @Test
    void deterministicAndPeriodicInX() {
        OreVeins again = new OreVeins(Planets.MARS, SPECS);
        int c = Planets.MARS.circumferenceBlocks();
        for (int x = -400; x < 400; x += 3) {
            for (int y = -50; y <= 110; y += 5) {
                for (int z = -300; z < 300; z += 7) {
                    int v = veins.veinAt(x, y, z);
                    assertEquals(v, again.veinAt(x, y, z));
                    assertEquals(v, veins.veinAt(x + c, y, z));
                }
            }
        }
    }

    @Test
    void rareOnlyInsideTheYRangeAndClustered() {
        OreVeinSpec s = SPECS.get(0);
        long ore = 0;
        long total = 0;
        for (int x = 0; x < 512; x += 2) {
            for (int z = 0; z < 512; z += 2) {
                for (int y = s.minY(); y <= s.maxY(); y += 2) {
                    total++;
                    if (veins.veinAt(x, y, z) >= 0) {
                        ore++;
                    }
                }
                assertEquals(-1, veins.veinAt(x, s.minY() - 1, z));
                assertEquals(-1, veins.veinAt(x, s.maxY() + 1, z));
            }
        }
        double fraction = ore / (double) total;
        assertTrue(fraction > 0.0005 && fraction < 0.02, "fração de minério fora do esperado: " + fraction);
    }

    @Test
    void veinsAreThinTubesNotBlobs() {
        // num veio, os vizinhos de minério estão quase todos fora dele: num tubo de ~1-2 blocos de espessura, menos da metade
        // dos 26 vizinhos é minério
        int samples = 0;
        double neighbourShare = 0;
        for (int x = 0; x < 600 && samples < 300; x++) {
            for (int z = 0; z < 600 && samples < 300; z += 3) {
                for (int y = 0; y < 100 && samples < 300; y += 4) {
                    if (veins.veinAt(x, y, z) < 0) {
                        continue;
                    }
                    int n = 0;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                if ((dx | dy | dz) != 0 && veins.veinAt(x + dx, y + dy, z + dz) >= 0) {
                                    n++;
                                }
                            }
                        }
                    }
                    neighbourShare += n / 26.0;
                    samples++;
                }
            }
        }
        assertTrue(samples > 20, "poucos blocos de minério para medir: " + samples);
        double mean = neighbourShare / samples;
        assertTrue(mean > 0.02 && mean < 0.5, "forma do veio inesperada (vizinhos de minério em média): " + mean);
    }

    @Test
    void emptyListMeansNoOre() {
        OreVeins none = new OreVeins(Planets.MOON, List.of());
        assertTrue(none.isEmpty());
        assertFalse(none.veinAt(10, 10, 10) >= 0);
    }
}
