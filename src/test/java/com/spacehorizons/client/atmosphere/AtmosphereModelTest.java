package com.spacehorizons.client.atmosphere;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.planet.Planets;

class AtmosphereModelTest {
    private final AtmosphereModel model = AtmosphereModel.of(Planets.EARTH);

    @Test
    void groundIsUntouched() {
        assertEquals(1.0, model.density(-20), 1e-12);
        assertEquals(1.0, model.density(model.baseAltitude()), 1e-12);
        assertEquals(1.0, model.skyBrightness(100), 1e-12);
        assertEquals(1.0, model.fogDistanceFactor(100), 1e-12);
        assertEquals(0.0, model.starVisibility(100), 1e-12);
        assertEquals(0.0, model.limbVisibility(100), 1e-12);
    }

    @Test
    void spaceIsEmpty() {
        double top = model.thickness();
        assertEquals(0.0, model.density(top), 1e-12);
        assertEquals(0.0, model.skyBrightness(top), 1e-12);
        assertEquals(1.0, model.starVisibility(top), 1e-12);
        assertEquals(1.0, model.limbVisibility(top), 1e-12);
        assertTrue(model.isVacuum(top));
        assertFalse(model.isVacuum(top - 1));
        assertEquals(AtmosphereModel.FOG_FAR_FACTOR, model.fogDistanceFactor(40_000), 1e-6);
    }

    @Test
    void densityFallsMonotonically() {
        double previous = 2.0;
        for (double alt = 0; alt <= 3_000; alt += 25) {
            double d = model.density(alt);
            assertTrue(d <= previous + 1e-12, "densidade subiu em " + alt);
            assertTrue(d >= 0.0 && d <= 1.0);
            previous = d;
        }
    }

    @Test
    void fogGrowsAndSkyDarkensMonotonically() {
        double fog = 0.0;
        double sky = 2.0;
        double stars = -1.0;
        for (double alt = 0; alt <= 3_000; alt += 25) {
            assertTrue(model.fogDistanceFactor(alt) >= fog - 1e-9);
            assertTrue(model.skyBrightness(alt) <= sky + 1e-12);
            assertTrue(model.starVisibility(alt) >= stars - 1e-12);
            fog = model.fogDistanceFactor(alt);
            sky = model.skyBrightness(alt);
            stars = model.starVisibility(alt);
        }
    }

    @Test
    void densityHasZeroSlopeAtTop() {
        double t = model.thickness();
        assertEquals(0.0, model.density(t - 1) - model.density(t), 1e-5);
    }

    @Test
    void rejectsInvalidThickness() {
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereModel(100, 100));
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereModel(100, -1));
    }
}
