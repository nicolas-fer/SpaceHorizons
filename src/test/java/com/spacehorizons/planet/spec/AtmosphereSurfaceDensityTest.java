package com.spacehorizons.planet.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.spacehorizons.planet.Planets;

/** A densidade do ar na superfície ({@code surface_density}): Marte tem ar fino para o paraquedas, a Terra a densidade de referência. */
class AtmosphereSurfaceDensityTest {
    @Test
    void earthIsTheReferenceAndMarsIsThin() {
        assertEquals(1.0, Planets.spec(Planets.EARTH).atmosphere().surfaceDensity(), 0.0);
        assertEquals(0.15, Planets.spec(Planets.MARS).atmosphere().surfaceDensity(), 0.0);
        // sem ar não há densidade a declarar: 1 (não é usada, a curva é 0)
        assertEquals(1.0, Planets.spec(Planets.MOON).atmosphere().surfaceDensity(), 0.0);
    }

    @Test
    void theFieldIsOptionalAndValidated() {
        AtmosphereSpec.Air air = new AtmosphereSpec.Air(1_000.0, 100.0, false, List.of(1.0, 0.5, 0.3), 0.5, 0xc89a6c, 0xd8ad82, false);
        assertEquals(1.0, air.surfaceDensity(), 0.0);
        assertThrows(IllegalArgumentException.class,
                () -> new AtmosphereSpec.Air(1_000.0, 100.0, false, List.of(1.0, 0.5, 0.3), 0.5, 0xc89a6c, 0xd8ad82, false, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new AtmosphereSpec.Air(1_000.0, 100.0, false, List.of(1.0, 0.5, 0.3), 0.5, 0xc89a6c, 0xd8ad82, false, 3.0));
    }
}
