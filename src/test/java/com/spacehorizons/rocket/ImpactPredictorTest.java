package com.spacehorizons.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.rocket.ImpactPredictor.Prediction;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.flight.BodyPhysics;
import com.spacehorizons.space.flight.FlightState;
import com.spacehorizons.space.flight.RocketStats;
import com.spacehorizons.space.flight.V3;

/** A previsão do impacto contra o voo balístico (queda radial de Kepler) e o comportamento nos casos de borda. */
class ImpactPredictorTest {
    private static final RocketStats STATS = RocketStats.of(4, 12, 1, 2, 1, 4, 4.0);
    private static final double CLOCK = 5_000.0;

    /** Tempo de queda radial de Kepler, de {@code r0} em repouso até {@code r1}. */
    private static double radialFallTicks(double mu, double r0, double r1) {
        double x = r1 / r0;
        return Math.sqrt(r0 * r0 * r0 / (2.0 * mu)) * (Math.sqrt(x * (1.0 - x)) + Math.acos(Math.sqrt(x)));
    }

    @Test
    void aDropOverTheAirlessMoonMatchesTheAnalyticRadialFall() {
        BodyPhysics moon = BodyPhysics.of(SolarSystem.MOON);
        double sea = SolarSystem.MOON.planet().seaLevel();
        double drop = 1_500.0;
        // a Lua não tem atmosfera: o integrador reproduz a queda de Kepler; o "terreno" é o nível do mar
        FlightState s = new FlightState(SolarSystem.MOON, new V3(moon.radius() + drop, 0, 0), V3.ZERO, 0.0, 0.0);
        Prediction p = ImpactPredictor.predict(s, STATS, CLOCK, (x, z) -> sea);
        assertNotNull(p);
        double expected = radialFallTicks(moon.mu(), moon.radius() + drop, moon.radius());
        assertEquals(expected, p.ticks(), 2.0, "ticks até o impacto");
        assertEquals(sea, p.y(), 1e-9);
    }

    @Test
    void theImpactPointFollowsTheHorizontalVelocityOnTheMoon() {
        BodyPhysics moon = BodyPhysics.of(SolarSystem.MOON);
        double sea = SolarSystem.MOON.planet().seaLevel();
        double drop = 1_000.0;
        double vTangent = 1.0;
        FlightState still = new FlightState(SolarSystem.MOON, new V3(moon.radius() + drop, 0, 0), V3.ZERO, 0.0, 0.0);
        FlightState moving = new FlightState(SolarSystem.MOON, new V3(moon.radius() + drop, 0, 0), new V3(0, 0, vTangent), 0.0, 0.0);
        Prediction a = ImpactPredictor.predict(still, STATS, CLOCK, (x, z) -> sea);
        Prediction b = ImpactPredictor.predict(moving, STATS, CLOCK, (x, z) -> sea);
        assertNotNull(a);
        assertNotNull(b);
        double shift = Math.hypot(b.x() - a.x(), b.z() - a.z());
        // a deriva é a velocidade lateral vezes o tempo de queda (a trajetória curva um pouco com a gravidade radial)
        assertEquals(vTangent * a.ticks(), shift, 0.15 * vTangent * a.ticks());
    }

    @Test
    void theAtmosphereSlowsTheFallOverTheEarth() {
        BodyPhysics earth = BodyPhysics.of(SolarSystem.EARTH);
        double sea = SolarSystem.EARTH.planet().seaLevel();
        double drop = 3_000.0;
        FlightState s = new FlightState(SolarSystem.EARTH, new V3(earth.radius() + drop, 0, 0), V3.ZERO, 0.0, 0.0);
        Prediction p = ImpactPredictor.predict(s, STATS, CLOCK, (x, z) -> sea);
        assertNotNull(p);
        double vacuum = radialFallTicks(earth.mu(), earth.radius() + drop, earth.radius());
        assertTrue(p.ticks() > vacuum, "com arrasto cai mais devagar: " + p.ticks() + " contra " + vacuum + " no vácuo");
        assertTrue(p.ticks() < 3.0 * vacuum, "e não infinitamente: " + p.ticks());
    }

    @Test
    void theFinalPathEndsAtTheImpactAndStaysWithin1500Blocks() {
        BodyPhysics moon = BodyPhysics.of(SolarSystem.MOON);
        double sea = SolarSystem.MOON.planet().seaLevel();
        FlightState s = new FlightState(SolarSystem.MOON, new V3(moon.radius() + 2_200.0, 0, 0), V3.ZERO, 0.0, 0.0);
        Prediction p = ImpactPredictor.predict(s, STATS, CLOCK, (x, z) -> sea);
        assertNotNull(p);
        assertTrue(p.path().size() > 10);
        assertTrue(p.path().get(0).y() - sea <= LandingPreloader.PATH_HEIGHT + 1e-6);
        assertTrue(p.path().get(0).y() - sea > LandingPreloader.PATH_HEIGHT - 100.0, "o trecho começa perto dos 1.500");
        ImpactPredictor.Point last = p.path().get(p.path().size() - 1);
        assertEquals(p.x(), last.x(), 1e-9);
        assertEquals(p.z(), last.z(), 1e-9);
        for (int i = 1; i < p.path().size(); i++) {
            assertTrue(p.path().get(i).y() <= p.path().get(i - 1).y() + 1e-9, "descendo, sempre");
        }
    }

    @Test
    void aCircularOrbitNeverHitsTheGround() {
        BodyPhysics moon = BodyPhysics.of(SolarSystem.MOON);
        double r = moon.radius() + 3_000.0;
        FlightState s = new FlightState(SolarSystem.MOON, new V3(r, 0, 0), new V3(0, 0, moon.circularSpeed(r)), 0.0, 0.0);
        assertNull(ImpactPredictor.predict(s, STATS, CLOCK, (x, z) -> 0.0));
    }

    @Test
    void anEscapingRocketHasNoImpact() {
        BodyPhysics moon = BodyPhysics.of(SolarSystem.MOON);
        double r = moon.radius() + 3_000.0;
        FlightState s = new FlightState(SolarSystem.MOON, new V3(r, 0, 0), new V3(2.0 * moon.escapeSpeed(r), 0, 0), 0.0, 0.0);
        assertNull(ImpactPredictor.predict(s, STATS, CLOCK, (x, z) -> 0.0));
    }
}
