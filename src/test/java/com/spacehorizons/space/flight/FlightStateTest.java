package com.spacehorizons.space.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;

class FlightStateTest {
    private static final BodyPhysics EARTH = BodyPhysics.of(SolarSystem.EARTH);

    @Test
    void fiveArgumentConstructorStartsWithTheParachuteClosed() {
        FlightState s = new FlightState(SolarSystem.EARTH, new V3(1, 2, 3), new V3(4, 5, 6), 100, 0.2);
        assertEquals(0.0, s.chute(), 0.0);
        assertEquals(100, s.fuelMb(), 0.0);
        assertEquals(0.2, s.heat(), 0.0);
    }

    @Test
    void withersChangeOnlyTheirField() {
        FlightState s = new FlightState(SolarSystem.EARTH, new V3(1, 2, 3), new V3(4, 5, 6), 100, 0.2, 0.5);
        assertEquals(new FlightState(SolarSystem.EARTH, new V3(1, 2, 3), new V3(4, 5, 6), 50, 0.2, 0.5), s.withFuel(50));
        assertEquals(new FlightState(SolarSystem.EARTH, new V3(1, 2, 3), new V3(4, 5, 6), 100, 0.9, 0.5), s.withHeat(0.9));
        assertEquals(new FlightState(SolarSystem.EARTH, new V3(1, 2, 3), new V3(4, 5, 6), 100, 0.2, 1.0), s.withChute(1.0));
        assertEquals(new FlightState(SolarSystem.EARTH, V3.ZERO, V3.ZERO, 100, 0.2, 0.5), s.withRV(V3.ZERO, V3.ZERO));
        FlightState moon = s.withBody(SolarSystem.MOON, V3.ZERO, V3.ZERO);
        assertSame(SolarSystem.MOON, moon.body());
        assertEquals(100, moon.fuelMb(), 0.0);
        assertEquals(0.5, moon.chute(), 0.0);
    }

    @Test
    void altitudeSpeedAndVerticalSpeed() {
        V3 r = new V3(EARTH.radius() + 1_500, 0, 0);
        FlightState up = new FlightState(SolarSystem.EARTH, r, new V3(10, 0, 20), 0, 0);
        assertEquals(1_500, up.altitude(), 1e-9);
        assertEquals(EARTH.radius() + 1_500, up.distance(), 1e-9);
        assertEquals(Math.sqrt(500), up.speed(), 1e-12);
        // a velocidade vertical é a componente radial: subindo é positiva, descendo negativa
        assertEquals(10, up.verticalSpeed(), 1e-12);
        assertEquals(-10, up.withRV(r, new V3(-10, 0, 20)).verticalSpeed(), 1e-12);
        assertEquals(0.0, up.withRV(r, new V3(0, 0, 25)).verticalSpeed(), 1e-12);
    }
}
