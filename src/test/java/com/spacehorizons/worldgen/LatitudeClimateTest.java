package com.spacehorizons.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.planet.Planets;
import com.spacehorizons.worldgen.density.LatitudeClimate;

class LatitudeClimateTest {
    private static final double MAX_LAT = Planets.EARTH.maxLatitudeBlocks();

    @Test
    void esfriaMonotonamenteDoEquadorAoPolo() {
        float previous = Float.POSITIVE_INFINITY;
        for (double z = 0; z <= MAX_LAT + 500; z += 25) {
            float offset = LatitudeClimate.offset(z, MAX_LAT);
            assertTrue(offset <= previous, "temperatura subiu com a latitude em z=" + z);
            previous = offset;
        }
    }

    @Test
    void simetricoEntreOsHemisferios() {
        for (double z = 0; z <= MAX_LAT; z += 137) {
            assertEquals(LatitudeClimate.offset(z, MAX_LAT), LatitudeClimate.offset(-z, MAX_LAT), 0.0F);
        }
    }

    @Test
    void equadorQuasePlanoEPolosCongelados() {
        // perto do equador a distribuição vanilla fica praticamente intacta
        assertEquals(LatitudeClimate.EQUATOR_WARMTH, LatitudeClimate.offset(0, MAX_LAT), 1e-6F);
        assertTrue(Math.abs(LatitudeClimate.offset(0.2 * MAX_LAT, MAX_LAT) - LatitudeClimate.EQUATOR_WARMTH) < 0.08F);
        // a 90% da barreira polar o deslocamento leva o clima à faixa de gelo (temperature < -0,45 com folga)
        assertTrue(LatitudeClimate.offset(0.9 * MAX_LAT, MAX_LAT) < -1.2F);
        // além do polo trava
        assertEquals(-LatitudeClimate.POLE_COOLING, LatitudeClimate.offset(MAX_LAT * 2, MAX_LAT), 1e-6F);
    }
}
