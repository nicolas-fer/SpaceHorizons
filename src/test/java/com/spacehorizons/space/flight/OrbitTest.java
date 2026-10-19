package com.spacehorizons.space.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;

class OrbitTest {
    private static final BodyPhysics EARTH = BodyPhysics.of(SolarSystem.EARTH);
    private static final BodyPhysics MOON = BodyPhysics.of(SolarSystem.MOON);
    /** Polo de referência da Terra: −norte. */
    private static final V3 EARTH_POLE = new V3(0, -1, 0);
    private static final double LEO = EARTH.radius() + 6_000;
    private static final double MOON_DISTANCE = SolarSystem.EARTH_MOON_DISTANCE;

    /** Órbita de transferência (TLI) a partir do periastro em +X, para leste (+Z). */
    private static Orbit transfer() {
        double speed = Math.sqrt(EARTH.mu() * (2.0 / LEO - 2.0 / (LEO + MOON_DISTANCE)));
        return Orbit.fromState(EARTH.mu(), new V3(LEO, 0, 0), new V3(0, 0, speed), EARTH_POLE);
    }

    @Test
    void circularOrbitHasZeroEccentricity() {
        double vc = EARTH.circularSpeed(LEO);
        Orbit o = Orbit.fromState(EARTH.mu(), new V3(LEO, 0, 0), new V3(0, 0, vc), EARTH_POLE);
        assertTrue(o.e() < 1e-12, "e = " + o.e());
        assertEquals(LEO, o.a(), 1e-6);
        assertEquals(LEO, o.periapsis(), 1e-6);
        assertEquals(LEO, o.apoapsis(), 1e-6);
        assertEquals(7_802.26, o.period(), 0.1);
        assertTrue(o.bound());
        assertEquals(0.0, o.trueAnomaly(), 0.0);
    }

    @Test
    void equatorialEastwardOrbitHasZeroInclinationWithTheMinusNorthPole() {
        double vc = EARTH.circularSpeed(LEO);
        Orbit east = Orbit.fromState(EARTH.mu(), new V3(LEO, 0, 0), new V3(0, 0, vc), EARTH_POLE);
        assertEquals(0.0, east.inclination(), 1e-9);
        assertTrue(east.eastward());
        // contra o norte +Y a mesma órbita sairia com 180° (a mão invertida dos eixos fixos no corpo)
        Orbit againstNorth = Orbit.fromState(EARTH.mu(), new V3(LEO, 0, 0), new V3(0, 0, vc), new V3(0, 1, 0));
        assertEquals(Math.PI, againstNorth.inclination(), 1e-9);
        // para oeste: 180° com o polo certo
        Orbit west = Orbit.fromState(EARTH.mu(), new V3(LEO, 0, 0), new V3(0, 0, -vc), EARTH_POLE);
        assertEquals(Math.PI, west.inclination(), 1e-9);
        assertFalse(west.eastward());
    }

    @Test
    void polarOrbitIsNinetyDegrees() {
        double vc = EARTH.circularSpeed(LEO);
        Orbit polar = Orbit.fromState(EARTH.mu(), new V3(LEO, 0, 0), new V3(0, vc, 0), EARTH_POLE);
        assertEquals(Math.PI / 2, polar.inclination(), 1e-9);
        assertTrue(polar.e() < 1e-12);
    }

    @Test
    void inclinedOrbitMatchesTheLaunchAngle() {
        double vc = EARTH.circularSpeed(LEO);
        double inc = Math.toRadians(28.5);
        // velocidade para leste inclinada para o norte (+Y) em 28,5°
        V3 v = new V3(0, Math.sin(inc) * vc, Math.cos(inc) * vc);
        Orbit o = Orbit.fromState(EARTH.mu(), new V3(LEO, 0, 0), v, EARTH_POLE);
        assertEquals(inc, o.inclination(), 1e-9);
    }

    @Test
    void hohmannTransferToTheMoonDistance() {
        Orbit t = transfer();
        assertEquals(0.68942, t.e(), 1e-5);
        assertEquals(110_303.78, t.periapsis(), 0.01);
        assertEquals(600_000.0, t.apoapsis(), 0.01);
        assertEquals(355_151.89, t.a(), 0.01);
        assertEquals(22_538.55, t.period() / 2, 0.05);
        assertEquals(22_538.55, t.timeTo(Math.PI), 0.05);
        assertEquals(0.0, t.timeTo(0.0), 1e-6);
        assertEquals(0.0, t.trueAnomaly(), 1e-9);
        // o Δv de Hohmann do planejador de referência
        assertEquals(26.6285, Hohmann.dv1(EARTH.mu(), LEO, MOON_DISTANCE), 1e-3);
        assertEquals(22_538.55, Hohmann.flightTime(EARTH.mu(), LEO, MOON_DISTANCE), 0.05);
        double vc = EARTH.circularSpeed(LEO);
        double vTransfer = Math.sqrt(EARTH.mu() * (2.0 / LEO - 1.0 / t.a()));
        assertEquals(26.6285, vTransfer - vc, 1e-3);
        assertEquals(vTransfer, t.speedAt(LEO), 1e-9);
    }

    @Test
    void hohmannHelpersMatchTheResearchTables() {
        double mu = EARTH.mu();
        // ângulo de avanço com a Lua média (ω = 2,2907e-4 rad/tick) para a LEO de 6.000: a Lua anda 296° durante a transferência de 22.539 ticks, logo π − 296° = −115,8° (a nave precisa estar 244° à frente)
        double lead = Hohmann.phaseAngle(Hohmann.flightTime(mu, LEO, MOON_DISTANCE), 2.2907e-4);
        assertEquals(-115.81, Math.toDegrees(lead), 0.1);
        // LEO de 2.500: Δv1 27,351 e 22.372 ticks de voo
        double leo2500 = EARTH.radius() + 2_500;
        assertEquals(27.3515, Hohmann.dv1(mu, leo2500, MOON_DISTANCE), 1e-3);
        assertEquals(22_372, Hohmann.flightTime(mu, leo2500, MOON_DISTANCE), 1.0);
        // velocidade no apoastro de 6.000: 21,225 (a circular lá é 38,086), então o segundo impulso é a diferença
        assertEquals(38.0864 - 21.2255, Hohmann.dv2(mu, LEO, MOON_DISTANCE), 0.01);
        assertEquals(355_151.89, Hohmann.transferSemiMajor(LEO, MOON_DISTANCE), 0.01);
    }

    @Test
    void trueAnomalyAtRadiusGivesTheSoiEntryTime() {
        Orbit t = transfer();
        double rSoi = MOON_DISTANCE - MOON.soiRadius();
        double nu = t.trueAnomalyAtRadius(rSoi);
        assertEquals(12_016, t.timeTo(nu), 2.0);
        // raio além do apoastro: não alcança
        assertTrue(Double.isNaN(t.trueAnomalyAtRadius(MOON_DISTANCE + 1_000)));
        // raio abaixo do periastro também não
        assertTrue(Double.isNaN(t.trueAnomalyAtRadius(LEO - 1_000)));
        // periastro e apoastro
        assertEquals(0.0, t.trueAnomalyAtRadius(LEO), 1e-6);
        assertEquals(Math.PI, t.trueAnomalyAtRadius(MOON_DISTANCE), 1e-6);
    }

    @Test
    void timeToAgreesWithKeplerPropagationOnAnEllipse() {
        Orbit t = transfer();
        // estado qualquer na elipse: 3.000 ticks depois do periastro
        Kepler.State s = Kepler.propagate(EARTH.mu(), new V3(LEO, 0, 0), new V3(0, 0, t.speedAt(LEO)), 3_000);
        Orbit now = Orbit.fromState(EARTH.mu(), s.r(), s.v(), EARTH_POLE);
        double toApoapsis = now.timeTo(Math.PI);
        assertEquals(22_538.55 - 3_000, toApoapsis, 0.05);
        Kepler.State apo = Kepler.propagate(EARTH.mu(), s.r(), s.v(), toApoapsis);
        assertEquals(MOON_DISTANCE, apo.r().length(), 1e-3);
        assertEquals(0.0, apo.r().dot(apo.v()) / apo.r().length(), 1e-6);
        // próximo periastro
        double toPeriapsis = now.timeTo(0.0);
        assertEquals(t.period() - 3_000, toPeriapsis, 0.05);
        // descendo (r·v < 0) a anomalia verdadeira fica em (π, 2π)
        Kepler.State down = Kepler.propagate(EARTH.mu(), new V3(LEO, 0, 0), new V3(0, 0, t.speedAt(LEO)), 30_000);
        Orbit descending = Orbit.fromState(EARTH.mu(), down.r(), down.v(), EARTH_POLE);
        assertTrue(descending.trueAnomaly() > Math.PI && descending.trueAnomaly() < 2 * Math.PI);
        assertEquals(t.period() - 30_000, descending.timeTo(0.0), 0.05);
    }

    @Test
    void hyperbolaIsUnboundWithInfinitePeriodAndSignedTimes() {
        double rp = MOON.radius() + 500;
        V3 r0 = new V3(rp, 0, 0);
        V3 v0 = new V3(0, 0, 31.8);
        Orbit o = Orbit.fromState(MOON.mu(), r0, v0, new V3(0, -1, 0));
        assertFalse(o.bound());
        assertTrue(o.e() > 1);
        assertTrue(o.a() < 0);
        assertEquals(rp, o.periapsis(), 1e-6);
        assertEquals(Double.POSITIVE_INFINITY, o.apoapsis());
        assertEquals(Double.POSITIVE_INFINITY, o.period());
        assertEquals(0.0, o.timeTo(0.0), 1e-9);
        // 2.000 ticks depois já passou do periastro: tempo negativo
        Kepler.State later = Kepler.propagate(MOON.mu(), r0, v0, 2_000);
        Orbit after = Orbit.fromState(MOON.mu(), later.r(), later.v(), new V3(0, -1, 0));
        assertEquals(-2_000, after.timeTo(0.0), 1e-3);
        // e a anomalia verdadeira de saída em outro raio prevê o tempo certo
        double rTarget = 200_000;
        double nu = after.trueAnomalyAtRadius(rTarget);
        double dt = after.timeTo(nu);
        Kepler.State there = Kepler.propagate(MOON.mu(), later.r(), later.v(), dt);
        assertEquals(rTarget, there.r().length(), 1e-3);
        // além das assíntotas: NaN
        assertTrue(Double.isNaN(after.timeTo(Math.PI)));
    }

    @Test
    void unusualCasesDoNotProduceNaN() {
        // parte do repouso: órbita radial degenerada (cai de volta)
        Orbit rest = Orbit.fromState(EARTH.mu(), new V3(EARTH.radius(), 0, 0), V3.ZERO, EARTH_POLE);
        assertTrue(rest.bound());
        assertEquals(EARTH.radius(), rest.apoapsis(), 1e-6);
        assertEquals(0.0, rest.periapsis(), 1e-6);
        assertFalse(Double.isNaN(rest.inclination()));
        assertFalse(Double.isNaN(rest.trueAnomaly()));
        // velocidade de escape exata: parábola
        double escape = EARTH.escapeSpeed(LEO);
        Orbit parabola = Orbit.fromState(EARTH.mu(), new V3(LEO, 0, 0), new V3(0, 0, escape), EARTH_POLE);
        assertFalse(parabola.bound());
        assertEquals(Double.POSITIVE_INFINITY, parabola.apoapsis());
        assertTrue(Double.isNaN(parabola.timeTo(1.0)));
        assertEquals(LEO, parabola.periapsis(), 1e-3);
    }

    @Test
    void orbitOfAFlightStateUsesThePoleOfTheBody() {
        double vc = EARTH.circularSpeed(LEO);
        FlightState s = new FlightState(SolarSystem.EARTH, new V3(LEO, 0, 0), new V3(0, 0, vc), 0, 0);
        assertEquals(0.0, Orbit.of(s, 0).inclination(), 1e-9);
        // a Lua em I orbita para leste (prógrada), no plano inclinado 5°
        double clock = 4_000;
        double[] m = new double[3];
        SolarSystem.MOON.inertialPositionAt(clock, m);
        double[] mv = new double[3];
        SolarSystem.MOON.inertialVelocityAt(clock, mv);
        Orbit moon = Orbit.fromState(EARTH.mu(), V3.of(m), V3.of(mv), EARTH.pole(clock));
        assertEquals(5.0, Math.toDegrees(moon.inclination()), 0.1);
        assertTrue(moon.eastward());
    }
}
