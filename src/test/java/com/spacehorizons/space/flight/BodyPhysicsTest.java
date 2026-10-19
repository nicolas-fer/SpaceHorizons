package com.spacehorizons.space.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;

class BodyPhysicsTest {
    private static final BodyPhysics EARTH = BodyPhysics.of(SolarSystem.EARTH);
    private static final BodyPhysics MOON = BodyPhysics.of(SolarSystem.MOON);

    @Test
    void gravitationalParametersMatchTheFeltGravity() {
        assertEquals(870_342_340.3, EARTH.mu(), 100.0);
        assertEquals(8_975_405.4, MOON.mu(), 10.0);
        // a Lua tem 1/4 do raio e 0,165 da gravidade: mu_Lua/mu_Terra = 0,165/16 exato
        assertEquals(0.0103125, MOON.mu() / EARTH.mu(), 1e-12);
        // a gravidade de superfície é a que o jogador sente: G0 · fator do planeta
        assertEquals(0.08, EARTH.surfaceGravity(), 1e-12);
        assertEquals(0.08 * 0.165, MOON.surfaceGravity(), 1e-12);
    }

    @Test
    void laplaceSphereOfInfluenceOfTheMoon() {
        assertEquals(96_271.3, MOON.soiRadius(), 1.0);
        assertEquals(Double.POSITIVE_INFINITY, EARTH.soiRadius());
        assertEquals(96_271.3 * 0.95, MOON.soiEnterRadius(), 1.0);
        assertEquals(96_271.3 * 1.05, MOON.soiExitRadius(), 1.0);
        assertTrue(MOON.soiEnterRadius() < MOON.soiRadius() && MOON.soiRadius() < MOON.soiExitRadius());
    }

    @Test
    void referenceSpeedsAndPeriods() {
        assertEquals(91.3472, EARTH.circularSpeed(EARTH.radius()), 1e-3);
        assertEquals(129.184, EARTH.escapeSpeed(EARTH.radius()), 1e-3);
        assertEquals(Math.sqrt(2) * EARTH.circularSpeed(200_000), EARTH.escapeSpeed(200_000), 1e-12);
        assertEquals(18.5527, MOON.circularSpeed(MOON.radius()), 1e-3);
        double leo = EARTH.radius() + 6_000;
        assertEquals(88.828, EARTH.circularSpeed(leo), 1e-3);
        assertEquals(7_802.26, EARTH.period(leo), 0.1);
        // período rasante: 7.174 ticks na Terra e 8.831 na Lua
        assertEquals(7_174, EARTH.period(EARTH.radius()), 1);
        assertEquals(8_831, MOON.period(MOON.radius()), 1);
    }

    @Test
    void exitAltitudeScalesWithTheRadius() {
        assertEquals(5_000.0, EARTH.exitAltitude(), 1e-9);
        assertEquals(1_250.0, MOON.exitAltitude(), 1e-9);
    }

    @Test
    void altitudeIsDistanceMinusRadius() {
        assertEquals(1_800.0, EARTH.altitude(EARTH.radius() + 1_800), 1e-9);
        assertEquals(EARTH.radius() + 500, EARTH.radiusAt(500), 1e-9);
        assertEquals(0.0, MOON.altitude(MOON.radius()), 0.0);
    }

    @Test
    void gravityFollowsTheInverseSquare() {
        assertEquals(EARTH.gravityAt(10_000) / 4.0, EARTH.gravityAt(20_000), 1e-15);
    }

    @Test
    void poleIsMinusTheBodyNorth() {
        V3 earth = EARTH.pole(12_345);
        assertEquals(0.0, earth.x(), 0.0);
        assertEquals(-1.0, earth.y(), 0.0);
        assertEquals(0.0, earth.z(), 0.0);
        // a Lua: norte = (−sin 5°, cos 5°, 0) fixo no espaço, o polo é o oposto
        for (double clock : new double[] {0, 5_000, 90_000.5}) {
            V3 moon = MOON.pole(clock);
            assertEquals(Math.sin(Math.toRadians(5)), moon.x(), 1e-12);
            assertEquals(-Math.cos(Math.toRadians(5)), moon.y(), 1e-12);
            assertEquals(0.0, moon.z(), 1e-12);
        }
    }

    @Test
    void catalogBodiesAreCachedAndAtmosphereComesFromTheModel() {
        assertSame(EARTH, BodyPhysics.of(SolarSystem.EARTH));
        assertSame(MOON, BodyPhysics.of(SolarSystem.MOON));
        assertTrue(EARTH.atmosphere().present());
        assertTrue(!MOON.atmosphere().present());
    }
}
