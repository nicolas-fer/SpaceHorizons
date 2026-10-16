package com.spacehorizons.space.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceFrame;

/**
 * Regressão do movimento da Lua no {@link SolarSystem}, que decide toda a calibração das manobras: a Lua é cinemática
 * (não kepleriana), com velocidade de 8,5 a 16,7 blocos/tick, e gira de forma sincronizada. Se alguém mexer na curva do
 * sol ou na órbita, estes números mudam e a física de voo (janelas de transferência, custo de captura) precisa ser
 * recalibrada.
 */
class MoonKinematicsTest {
    @Test
    void moonSpeedRangesFromEightAndAHalfToSixteenAndThreeQuarters() {
        double min = Double.MAX_VALUE;
        double max = 0;
        double sum = 0;
        int count = 0;
        double[] v = new double[3];
        for (double c = 0; c < 24_000; c += 10) {
            SolarSystem.MOON.velocityAt(c + 0.5, v);
            double speed = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            min = Math.min(min, speed);
            max = Math.max(max, speed);
            sum += speed;
            count++;
        }
        assertEquals(8.495, min, 0.01);
        assertEquals(16.724, max, 0.01);
        assertEquals(13.744, sum / count, 0.01);
    }

    @Test
    void equatorialSurfaceSpeedOfTheMoonRotationRangesFrom037To073() {
        double min = Double.MAX_VALUE;
        double max = 0;
        double[] abs = new double[3];
        double[] ground = new double[3];
        double[] center = new double[3];
        for (double clock = 0; clock < 24_000; clock += 25) {
            SpaceFrame.toSpace(SolarSystem.MOON, clock, 0, 63, 0, abs);
            SpaceFrame.groundVelocity(SolarSystem.MOON, clock, abs[0], abs[1], abs[2], ground);
            SolarSystem.MOON.velocityAt(clock, center);
            double speed = new V3(ground[0] - center[0], ground[1] - center[1], ground[2] - center[2]).length();
            min = Math.min(min, speed);
            max = Math.max(max, speed);
        }
        assertEquals(0.369, min, 0.005);
        assertEquals(0.727, max, 0.005);
    }

    @Test
    void moonOrbitsWestwardSeenFromTheEarthPole() {
        // o polo P = −norte da Terra dá à Lua uma inclinação de ~175° em todo instante: órbita para o OESTE
        BodyPhysics earth = BodyPhysics.of(SolarSystem.EARTH);
        for (double clock : new double[] {0, 4_321, 12_000.5, 77_777, 191_999}) {
            double[] m = new double[3];
            SolarSystem.MOON.positionAt(clock, m);
            double[] mv = new double[3];
            SolarSystem.MOON.velocityAt(clock, mv);
            Orbit moon = Orbit.fromState(earth.mu(), V3.of(m), V3.of(mv), earth.pole(clock));
            assertEquals(175.0, Math.toDegrees(moon.inclination()), 0.5, "clock = " + clock);
            assertTrue(!moon.eastward());
        }
    }

    @Test
    void theMoonIsFasterThanKeplerOnAverage() {
        // uma Lua kepleriana com este mu teria ω = 2,007e-4 rad/tick (período 31.301); a real tem ω médio de 2,291e-4
        BodyPhysics earth = BodyPhysics.of(SolarSystem.EARTH);
        double keplerian = Math.sqrt(earth.mu() / Math.pow(SolarSystem.EARTH_MOON_DISTANCE, 3));
        assertEquals(2.007e-4, keplerian, 1e-6);
        double real = 2 * Math.PI / 27_428.6;
        assertEquals(2.291e-4, real, 1e-6);
        assertTrue(real > keplerian * 1.1);
    }
}
