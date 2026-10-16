package com.spacehorizons.space.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;

/** Altitude de saída por corpo, altitude sobre um corpo e corpo mais próximo: a base da velocidade de voo no espaço e do comando de volta. */
class SpaceZonesMathTest {
    @Test
    void exitAltitudeScalesWithTheRadius() {
        assertEquals(5_000.0, TravelMath.exitAltitude(SolarSystem.EARTH), 1e-9);
        double moon = TravelMath.exitAltitude(SolarSystem.MOON);
        assertEquals(1_250.0, moon, 1.0);
        assertEquals(SolarSystem.MOON.planet().radius() / SolarSystem.EARTH.planet().radius(), moon / 5_000.0, 1e-12);
    }

    @Test
    void altitudeOverIsTheDistanceToTheCentreMinusTheRadius() {
        double radius = SolarSystem.EARTH.planet().radius();
        assertEquals(5_000, TravelMath.altitudeOver(SolarSystem.EARTH, 0, radius + 5_000, 0, 0), 1e-9);
        assertEquals(5_000, TravelMath.altitudeOver(SolarSystem.EARTH, 123_456, 0, -(radius + 5_000), 0), 1e-9);
        assertEquals(-radius, TravelMath.altitudeOver(SolarSystem.EARTH, 0, 0, 0, 0), 1e-9);
        for (double clock : new double[] {0, 6_000, 70_000.5}) {
            double[] m = new double[3];
            SolarSystem.MOON.positionAt(clock, m);
            assertEquals(0, TravelMath.altitudeOver(SolarSystem.MOON, clock, m[0], m[1], m[2]) + SolarSystem.MOON.planet().radius(), 1e-6);
        }
    }

    @Test
    void nearestBodyIsTheOneWithTheLowestAltitude() {
        for (double clock : new double[] {0, 6_000, 18_000, 70_000.5}) {
            double[] m = new double[3];
            SolarSystem.MOON.positionAt(clock, m);
            // perto da Terra
            assertSame(SolarSystem.EARTH, TravelMath.nearestBody(clock, 15_000, 0, 0));
            // perto da Lua (a 3.000 blocos do centro dela, do lado da Terra: 400 de altitude)
            assertSame(SolarSystem.MOON, TravelMath.nearestBody(clock, m[0] * 0.95, m[1] * 0.95, m[2] * 0.95));
            // a meio caminho dos centros: a Terra, por ser maior, tem a superfície mais perto (19.570 contra 27.390)
            assertSame(SolarSystem.EARTH, TravelMath.nearestBody(clock, m[0] / 2, m[1] / 2, m[2] / 2));
            assertTrue(TravelMath.altitudeOver(SolarSystem.EARTH, clock, m[0] / 2, m[1] / 2, m[2] / 2) > 19_000);
        }
    }

    /** A velocidade de voo em órbita da Terra é a mesma dos dois lados da saída (a regra é por altitude sobre o corpo). */
    @Test
    void theFlightSpeedMultiplierIsContinuousAtTheExitOfThePlanet() {
        double altitude = TravelMath.exitAltitude(SolarSystem.EARTH);
        double inPlanet = TravelMath.flightSpeedMultiplier(altitude);
        double[] space = {SolarSystem.EARTH.planet().radius() + altitude, 0, 0};
        SolarSystem.Body nearest = TravelMath.nearestBody(0, space[0], space[1], space[2]);
        double inSpace = TravelMath.flightSpeedMultiplier(TravelMath.altitudeOver(nearest, 0, space[0], space[1], space[2]));
        assertEquals(inPlanet, inSpace, 1e-12);
    }
}
