package com.spacehorizons.planet.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.SurfaceType;
import com.spacehorizons.planet.spec.OreVeinSpec;

/** O veio de gelo lunar (etapa 4 da sobrevivência): só em latitude alta e só sob o fundo de cratera; o relevo da Lua não muda. */
class MoonIceVeinsTest {
    private static final OreVeinSpec ICE = Planets.spec(Planets.MOON).ores().get(0);
    private final OreVeins veins = new OreVeins(Planets.MOON, Planets.spec(Planets.MOON).ores());

    /** Conta blocos de veio numa janela de 64x64 colunas em torno de z, em todo o intervalo de Y do veio. */
    private int count(int z, SurfaceType surface) {
        int n = 0;
        for (int x = 0; x < 64; x++) {
            for (int dz = 0; dz < 64; dz++) {
                for (int y = ICE.minY(); y <= ICE.maxY(); y++) {
                    if (veins.veinAt(x * 3, y, z + dz, surface) >= 0) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private static int zAtLatitude(double degrees) {
        return (int) Math.round(Math.toRadians(degrees) * Planets.MOON.radius());
    }

    @Test
    void theMoonVeinIsLunarIceWithAVanillaFallback() {
        assertEquals("spacehorizons:lunar_ice_ore", ICE.block().get(0).toString());
        assertEquals("minecraft:packed_ice", ICE.block().get(1).toString());
        assertEquals(55.0, ICE.minLatitude());
        assertEquals(java.util.List.of(SurfaceType.CRATER_FLOOR), ICE.surfaceTypes());
    }

    @Test
    void noIceNearTheEquatorEvenUnderCraterFloors() {
        assertEquals(0, count(zAtLatitude(10), SurfaceType.CRATER_FLOOR));
        assertEquals(0, count(zAtLatitude(54), SurfaceType.CRATER_FLOOR));
    }

    @Test
    void noIceUnderOtherSurfacesAtHighLatitude() {
        assertEquals(0, count(zAtLatitude(60), SurfaceType.REGOLITH));
        assertEquals(0, count(zAtLatitude(60), SurfaceType.HIGHLAND));
    }

    @Test
    void iceExistsUnderCraterFloorsAtHighLatitude() {
        int found = count(zAtLatitude(60), SurfaceType.CRATER_FLOOR) + count(zAtLatitude(-62), SurfaceType.CRATER_FLOOR);
        assertTrue(found > 500, "esperado gelo suficiente sob uma janela polar, achou " + found);
    }

    @Test
    void withoutASurfaceTheLatitudeFilterStillApplies() {
        assertEquals(0, count(zAtLatitude(20), null));
        assertTrue(count(zAtLatitude(60), null) > 0);
    }
}
