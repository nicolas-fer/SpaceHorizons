package com.spacehorizons.space.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;

class TravelMathTest {
    @Test
    void flightSpeedIsNormalNearTheGroundAndGrowsWithAltitudeUpToTheCap() {
        assertEquals(1.0, TravelMath.flightSpeedMultiplier(-50), 0);
        assertEquals(1.0, TravelMath.flightSpeedMultiplier(0), 0);
        assertEquals(1.0, TravelMath.flightSpeedMultiplier(TravelMath.BOOST_START_ALTITUDE), 0);
        double previous = 1.0;
        for (double altitude = 0; altitude <= 200_000; altitude += 137) {
            double m = TravelMath.flightSpeedMultiplier(altitude);
            assertTrue(m >= previous - 1e-12, "não monotônico em " + altitude);
            assertTrue(m <= TravelMath.BOOST_MAX);
            previous = m;
        }
        assertEquals(TravelMath.BOOST_MAX, TravelMath.flightSpeedMultiplier(1_000_000), 0);
        // proporcional: dobrar a altitude dobra o multiplicador (dentro da quantização)
        assertEquals(2 * TravelMath.flightSpeedMultiplier(10_000), TravelMath.flightSpeedMultiplier(20_000), TravelMath.BOOST_STEP);
        // a 40.000 blocos já é dezenas de vezes mais rápido
        assertTrue(TravelMath.flightSpeedMultiplier(40_000) >= 40);
    }

    @Test
    void lookVectorRoundTripsThroughYawAndPitch() {
        double[] v = new double[3];
        float[][] cases = {{0, 0}, {90, 0}, {-135, 30}, {45, -60}, {179, 10}};
        for (float[] c : cases) {
            TravelMath.lookVector(c[0], c[1], v);
            assertEquals(1.0, Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]), 1e-12);
            assertEquals(c[0], TravelMath.yawOf(v[0], v[2]), 1e-4);
            assertEquals(c[1], TravelMath.pitchOf(v[1]), 1e-4);
        }
    }

    @Test
    void normalizeXWrapsIntoTheValidRange() {
        double c = SolarSystem.MOON.planet().circumferenceBlocks();
        double half = c / 2;
        assertEquals(0, TravelMath.normalizeX(SolarSystem.MOON, 0), 0);
        assertEquals(-half, TravelMath.normalizeX(SolarSystem.MOON, half), 1e-9);
        assertEquals(-half, TravelMath.normalizeX(SolarSystem.MOON, -half), 1e-9);
        assertEquals(100, TravelMath.normalizeX(SolarSystem.MOON, 100 + 3 * c), 1e-6);
        assertEquals(-100, TravelMath.normalizeX(SolarSystem.MOON, -100 - 2 * c), 1e-6);
    }

    @Test
    void facingPointsFaceTheOtherBody() {
        double[] p = new double[2];
        TravelMath.facingPoint(SolarSystem.EARTH, p);
        assertEquals(0.5, p[0], 0);
        assertEquals(0.5, p[1], 0);

        TravelMath.facingPoint(SolarSystem.MOON, p);
        double half = SolarSystem.MOON.planet().halfCircumference();
        // face da Lua voltada para a Terra: lon = pi, no limite da costura (x = +-C/2), z = 0
        assertEquals(half, Math.abs(p[0]) + 0.5, 1.0);
        assertEquals(0.5, p[1], 0);
        assertTrue(p[0] >= -half && p[0] < half);
    }
}
