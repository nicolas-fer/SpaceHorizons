package com.spacehorizons.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SunModelTest {
    private static final double C_EARTH = SolarSystem.EARTH.planet().circumferenceBlocks();
    private static final double C_MOON = SolarSystem.MOON.planet().circumferenceBlocks();

    @Test
    void atEarthSpawnTheEffectiveClockIsTheRealClock() {
        for (double clock : new double[] {0, 1000, 6000, 11999, 18000, 23999, 48_123.5}) {
            assertEquals(clock, SunModel.effectiveClock(SolarSystem.EARTH, 0, 0, clock), 1e-6);
        }
    }

    @Test
    void sunRisesInTheEastAtTickZeroOnSpawn() {
        double[] local = new double[3];
        SunModel.localDirection(SolarSystem.EARTH, 0, 0, 0, local);
        assertEquals(1.0, local[0], 1e-9);
        assertEquals(0.0, local[1], 1e-9);
        SunModel.localDirection(SolarSystem.EARTH, 0, 0, 6000, local);
        assertEquals(1.0, local[1], 1e-9);
    }

    @Test
    void antipodeOfSpawnHasMidnightAtNoon() {
        // x = C/2 na Terra é o outro lado do planeta: meio-dia no spawn = meia-noite lá
        double clock = 6000;
        double eff = SunModel.effectiveClock(SolarSystem.EARTH, C_EARTH / 2, 0, clock);
        assertEquals(18000, Math.floorMod((long) Math.round(eff), 24000L), 1.0);
    }

    @Test
    void quarterPlanetEastIsAfternoon() {
        // 90° a leste do spawn o sol do meio-dia do spawn está se pondo no oeste
        double eff = SunModel.effectiveClock(SolarSystem.EARTH, C_EARTH / 4, 0, 6000);
        assertEquals(12000, Math.floorMod((long) Math.round(eff), 24000L), 1.0);
    }

    @Test
    void moonFaceTowardEarthIsDarkAtEarthNoon() {
        // a face da Lua voltada para a Terra (lon = π) tem o sol do meio-dia do spawn atrás dela: noite (lua nova)
        double[] local = new double[3];
        SunModel.localDirection(SolarSystem.MOON, C_MOON / 2, 0, 6000, local);
        assertTrue(local[1] < -0.99, "sol deveria estar abaixo do horizonte, up=" + local[1]);
        // e no meio da noite do spawn (lua cheia vista da Terra) essa face está ao meio-dia
        SunModel.localDirection(SolarSystem.MOON, C_MOON / 2, 0, 18000, local);
        assertTrue(local[1] > 0.99, "sol deveria estar a pino, up=" + local[1]);
    }

    @Test
    void effectiveClockIsContinuousAcrossMidnight() {
        double prev = SunModel.effectiveClock(SolarSystem.EARTH, 5000, 300, 23_900);
        for (double clock = 23_910; clock < 24_400; clock += 10) {
            double eff = SunModel.effectiveClock(SolarSystem.EARTH, 5000, 300, clock);
            assertTrue(Math.abs(eff - prev - 10) < 30, "salto em " + clock + ": " + prev + " -> " + eff);
            prev = eff;
        }
    }
}
