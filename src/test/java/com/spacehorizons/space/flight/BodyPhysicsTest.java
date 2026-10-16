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
        assertEquals(8_703_423.4, EARTH.mu(), 1.0);
        assertEquals(89_754.05, MOON.mu(), 0.1);
        // a Lua tem 1/4 do raio e 0,165 da gravidade: mu_Lua/mu_Terra = 0,165/16 exato
        assertEquals(0.0103125, MOON.mu() / EARTH.mu(), 1e-12);
        // a gravidade de superfície é a que o jogador sente: G0 · fator do planeta
        assertEquals(0.08, EARTH.surfaceGravity(), 1e-12);
        assertEquals(0.08 * 0.165, MOON.surfaceGravity(), 1e-12);
    }

    @Test
    void laplaceSphereOfInfluenceOfTheMoon() {
        assertEquals(9_627.1, MOON.soiRadius(), 0.1);
        assertEquals(Double.POSITIVE_INFINITY, EARTH.soiRadius());
        assertEquals(9_627.1 * 0.95, MOON.soiEnterRadius(), 0.1);
        assertEquals(9_627.1 * 1.05, MOON.soiExitRadius(), 0.1);
        assertTrue(MOON.soiEnterRadius() < MOON.soiRadius() && MOON.soiRadius() < MOON.soiExitRadius());
    }

    @Test
    void referenceSpeedsAndPeriods() {
        assertEquals(28.8865, EARTH.circularSpeed(EARTH.radius()), 1e-3);
        assertEquals(40.852, EARTH.escapeSpeed(EARTH.radius()), 1e-3);
        assertEquals(Math.sqrt(2) * EARTH.circularSpeed(20_000), EARTH.escapeSpeed(20_000), 1e-12);
        assertEquals(5.867, MOON.circularSpeed(MOON.radius()), 1e-3);
        double leo = EARTH.radius() + 6_000;
        assertEquals(23.016, EARTH.circularSpeed(leo), 1e-3);
        assertEquals(4_485.45, EARTH.period(leo), 0.1);
        // período rasante: 2.269 ticks na Terra e 2.793 na Lua
        assertEquals(2_269, EARTH.period(EARTH.radius()), 1);
        assertEquals(2_793, MOON.period(MOON.radius()), 1);
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
