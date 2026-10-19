package com.spacehorizons.space.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.planet.Planets;
import com.spacehorizons.space.SolarSystem;

class ZonesTest {
    private static final double EARTH_R = Planets.EARTH.radius();
    private static final double MOON_R = Planets.MOON.radius();

    private static V3 moonCenter(double clock) {
        return SoiTracker.position(SolarSystem.MOON, clock);
    }

    @Test
    void exitAltitudeScalesWithTheRadius() {
        assertEquals(5_000.0, Zones.exitAltitude(Planets.EARTH), 1e-9);
        assertEquals(1_250.0, Zones.exitAltitude(Planets.MOON), 1e-9);
        assertEquals(4_000.0, Zones.enterAltitude(Planets.EARTH), 1e-9);
        assertEquals(1_000.0, Zones.enterAltitude(Planets.MOON), 1e-9);
    }

    @Test
    void leavesThePlanetDimensionAboveTheExitAltitude() {
        int sea = Planets.EARTH.seaLevel();
        assertFalse(Zones.shouldLeave(Planets.EARTH, sea + 4_999));
        assertFalse(Zones.shouldLeave(Planets.EARTH, sea + 5_000));
        assertTrue(Zones.shouldLeave(Planets.EARTH, sea + 5_001));
        assertFalse(Zones.shouldLeave(Planets.EARTH, sea + 100));
        int moonSea = Planets.MOON.seaLevel();
        assertFalse(Zones.shouldLeave(Planets.MOON, moonSea + 1_249));
        assertTrue(Zones.shouldLeave(Planets.MOON, moonSea + 1_251));
    }

    @Test
    void entersTheEarthDimensionBelowEightyPercentOfTheExitAltitude() {
        double clock = 5_000;
        assertSame(SolarSystem.EARTH, Zones.shouldEnter(clock, new V3(EARTH_R + 3_999, 0, 0)));
        assertSame(SolarSystem.EARTH, Zones.shouldEnter(clock, new V3(0, -(EARTH_R + 500), 0)));
        assertNull(Zones.shouldEnter(clock, new V3(EARTH_R + 4_001, 0, 0)));
        assertNull(Zones.shouldEnter(clock, new V3(EARTH_R + 4_500, 0, 0)));
    }

    @Test
    void entersTheMoonDimensionNearTheMoon() {
        for (double clock : new double[] {0, 8_000, 61_234.5}) {
            V3 center = moonCenter(clock);
            V3 dir = new V3(0.3, -0.5, 0.8).unit();
            assertSame(SolarSystem.MOON, Zones.shouldEnter(clock, center.plus(dir.times(MOON_R + 999))));
            assertNull(Zones.shouldEnter(clock, center.plus(dir.times(MOON_R + 1_001))));
            assertSame(SolarSystem.MOON, Zones.shouldEnter(clock, center.plus(dir.times(MOON_R + 10))));
        }
    }

    @Test
    void hysteresisBetweenLeavingAndEntering() {
        // sobe saindo a 5.000 e só volta a entrar abaixo de 4.000: entre os dois nenhum dos dois acontece
        int sea = Planets.EARTH.seaLevel();
        double clock = 5_000;
        for (double altitude : new double[] {4_100, 4_500, 4_900, 5_000}) {
            assertFalse(Zones.shouldLeave(Planets.EARTH, sea + altitude), "altitude " + altitude);
            assertNull(Zones.shouldEnter(clock, new V3(EARTH_R + altitude, 0, 0)), "altitude " + altitude);
        }
    }

    @Test
    void noZoneInOpenSpace() {
        double clock = 12_000;
        assertNull(Zones.shouldEnter(clock, new V3(300_000, 0, 0)));
        assertNull(Zones.shouldEnter(clock, new V3(-200_000, 50_000, 10_000)));
        // a meio caminho entre a Terra e a Lua
        V3 mid = moonCenter(clock).times(0.5);
        assertNull(Zones.shouldEnter(clock, mid));
    }

    @Test
    void exitAndEntryAreConsistentForAnyBody() {
        for (SolarSystem.Body body : SolarSystem.BODIES) {
            assertTrue(Zones.enterAltitude(body.planet()) < Zones.exitAltitude(body.planet()));
            assertEquals(BodyPhysics.of(body).exitAltitude(), Zones.exitAltitude(body.planet()), 1e-9);
        }
    }
}
