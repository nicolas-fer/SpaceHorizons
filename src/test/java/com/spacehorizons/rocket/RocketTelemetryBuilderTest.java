package com.spacehorizons.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.flight.AttitudeMath.SasMode;
import com.spacehorizons.space.flight.BodyPhysics;
import com.spacehorizons.space.flight.FlightState;
import com.spacehorizons.space.flight.RocketStats;
import com.spacehorizons.space.flight.TransferPlanner;
import com.spacehorizons.space.flight.V3;

/** A telemetria montada a partir do estado de voo (a mesma matemática do painel). */
class RocketTelemetryBuilderTest {
    private static final BodyPhysics EARTH = BodyPhysics.of(SolarSystem.EARTH);
    private static final RocketStats STATS = RocketStats.of(4, 12, 1, 2, 1, 4, 4.0);
    private static final double CLOCK = 5_000.0;

    private static RocketTelemetryPayload build(FlightState s) {
        return RocketTelemetryBuilder.build(s, FlightMode.FLIGHT, SasMode.PROGRADE, 0.5, true, STATS, CLOCK, 1.0, false);
    }

    @Test
    void aCircularEastwardOrbitHasEqualApsesAndZeroInclination() {
        double r = EARTH.radius() + 1_000.0;
        FlightState s = new FlightState(SolarSystem.EARTH, new V3(r, 0, 0), new V3(0, 0, EARTH.circularSpeed(r)), 20_000.0, 0.1, 0.0);
        RocketTelemetryPayload t = build(s);
        assertEquals("earth", t.body());
        assertTrue(t.inSpace());
        assertEquals(1_000.0, t.altitude(), 1e-6);
        assertEquals(1_000.0, t.apoapsisAltitude(), 0.01);
        assertEquals(1_000.0, t.periapsisAltitude(), 0.01);
        assertEquals(0.0, t.inclination(), 0.01, "leste = 0 graus (polo P = -norte)");
        assertEquals(0.0, t.verticalSpeed(), 1e-9);
        assertEquals(EARTH.circularSpeed(r), t.horizontalSpeed(), 1e-9);
        assertEquals(EARTH.circularSpeed(r), t.orbitalSpeed(), 1e-9);
        assertTrue(t.timeToApoapsis() >= 0.0 && Double.isFinite(t.timeToApoapsis()));
        assertEquals(SasMode.PROGRADE.ordinal(), t.sasMode());
        assertEquals(FlightMode.FLIGHT.ordinal(), t.flightMode());
        assertEquals(0.5F, t.throttle());
        assertEquals(0.1F, t.heat(), 1e-6);
        // órbita para leste: o planejador da Lua diz que o sentido está errado
        assertEquals(TransferPlanner.Status.WRONG_DIRECTION.ordinal(), t.plannerStatus());
    }

    @Test
    void anEllipseReportsApoapsisPeriapsisAndTheTimeToTheApoapsis() {
        // no periastro (raio 11.430) com 1,2 vezes a velocidade circular: elipse alta
        double r = EARTH.radius() + 1_000.0;
        double v = EARTH.circularSpeed(r) * 1.2;
        FlightState s = new FlightState(SolarSystem.EARTH, new V3(r, 0, 0), new V3(0, 0, v), 20_000.0, 0.0, 0.0);
        RocketTelemetryPayload t = build(s);
        assertEquals(1_000.0, t.periapsisAltitude(), 0.01);
        assertTrue(t.apoapsisAltitude() > 3_000.0, "apoastro " + t.apoapsisAltitude());
        double semiMajor = (r + (t.apoapsisAltitude() + EARTH.radius())) / 2.0;
        double period = 2.0 * Math.PI * Math.sqrt(semiMajor * semiMajor * semiMajor / EARTH.mu());
        assertEquals(period / 2.0, t.timeToApoapsis(), 1.0, "do periastro ao apoastro é meio período");
    }

    @Test
    void anEscapeTrajectoryHasNoApoapsisAndNoTimeToIt() {
        double r = EARTH.radius() + 1_000.0;
        FlightState s = new FlightState(SolarSystem.EARTH, new V3(r, 0, 0), new V3(0, 0, EARTH.escapeSpeed(r) * 1.1), 0.0, 0.0, 0.0);
        RocketTelemetryPayload t = build(s);
        assertTrue(Double.isInfinite(t.apoapsisAltitude()));
        assertTrue(Double.isNaN(t.timeToApoapsis()));
    }

    @Test
    void aVerticalAscentSplitsTheSpeedIntoVerticalOnly() {
        double r = EARTH.radius() + 300.0;
        FlightState s = new FlightState(SolarSystem.EARTH, new V3(r, 0, 0), new V3(12.0, 0, 0), STATS.fuelCapacityMb(), 0.0, 0.0);
        RocketTelemetryPayload t = build(s);
        assertEquals(12.0, t.verticalSpeed(), 1e-9);
        assertEquals(0.0, t.horizontalSpeed(), 1e-9);
        assertTrue(t.apoapsisAltitude() > 300.0, "sobe mais");
        assertTrue(t.periapsisAltitude() < 0.0, "a órbita radial cruza o chão");
    }

    @Test
    void fuelDeltaVAndTwrComeFromTheRocketStats() {
        double r = EARTH.radius();
        FlightState s = new FlightState(SolarSystem.EARTH, new V3(r, 0, 0), V3.ZERO, STATS.fuelCapacityMb(), 0.0, 0.0);
        RocketTelemetryPayload t = build(s);
        assertEquals(STATS.fuelCapacityMb(), t.fuelMb(), 0.0);
        assertEquals(STATS.fuelCapacityMb(), t.fuelCapacityMb(), 0.0);
        assertEquals(STATS.deltaV(STATS.fuelCapacityMb()), t.deltaV(), 1e-9);
        assertEquals(STATS.twr(STATS.fuelCapacityMb(), EARTH.surfaceGravity()), t.twr(), 1e-9);
        assertTrue(t.twr() > 1.0, "o foguete de teste sai do chão");
    }

    @Test
    void aroundTheMoonThereIsNoPlannerAndTheBodyIsTheMoon() {
        BodyPhysics moon = BodyPhysics.of(SolarSystem.MOON);
        double r = moon.radius() + 400.0;
        FlightState s = new FlightState(SolarSystem.MOON, new V3(r, 0, 0), new V3(0, 0, moon.circularSpeed(r)), 5_000.0, 0.0, 0.0);
        RocketTelemetryPayload t = RocketTelemetryBuilder.build(s, FlightMode.PARACHUTE, SasMode.OFF, 0.0, false, STATS, CLOCK, 1.0, true);
        assertEquals("moon", t.body());
        assertFalse(t.inSpace());
        assertEquals(-1, t.plannerStatus());
        assertTrue(t.heldForChunks());
        assertEquals(FlightMode.PARACHUTE.ordinal(), t.flightMode());
        assertEquals(400.0, t.altitude(), 1e-6);
    }
}
