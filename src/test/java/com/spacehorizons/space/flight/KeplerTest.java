package com.spacehorizons.space.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;

class KeplerTest {
    private static final BodyPhysics EARTH = BodyPhysics.of(SolarSystem.EARTH);
    private static final BodyPhysics MOON = BodyPhysics.of(SolarSystem.MOON);
    private static final double LEO = EARTH.radius() + 6_000;

    private static double energy(double mu, V3 r, V3 v) {
        return 0.5 * v.lengthSquared() - mu / r.length();
    }

    @Test
    void zeroTimeReturnsTheSameState() {
        V3 r = new V3(LEO, 0, 0);
        V3 v = new V3(0, 0, 25);
        Kepler.State s = Kepler.propagate(EARTH.mu(), r, v, 0);
        assertEquals(r, s.r());
        assertEquals(v, s.v());
    }

    @Test
    void circularOrbitReturnsAfterOnePeriodAndStaysOnTheCircle() {
        V3 r = new V3(LEO, 0, 0);
        V3 v = new V3(0, 0, EARTH.circularSpeed(LEO));
        double period = EARTH.period(LEO);
        Kepler.State full = Kepler.propagate(EARTH.mu(), r, v, period);
        assertEquals(0.0, full.r().distanceTo(r), 1e-5);
        assertEquals(0.0, full.v().distanceTo(v), 1e-8);
        Kepler.State quarter = Kepler.propagate(EARTH.mu(), r, v, period / 4);
        assertEquals(LEO, quarter.r().length(), 1e-6);
        assertEquals(0.0, quarter.r().x(), 1e-5);
        assertTrue(quarter.r().z() > 0);
    }

    @Test
    void forwardThenBackwardRestoresTheState() {
        double speed = Math.sqrt(EARTH.mu() * (2.0 / LEO - 2.0 / (LEO + 60_000)));
        V3 r = new V3(LEO, 1_000, 0);
        V3 v = new V3(0.5, 0, speed);
        for (double dt : new double[] {1, 37.5, 2_500, 9_999}) {
            Kepler.State ahead = Kepler.propagate(EARTH.mu(), r, v, dt);
            Kepler.State back = Kepler.propagate(EARTH.mu(), ahead.r(), ahead.v(), -dt);
            assertEquals(0.0, back.r().distanceTo(r), 1e-5, "dt = " + dt);
            assertEquals(0.0, back.v().distanceTo(v), 1e-8, "dt = " + dt);
            assertEquals(energy(EARTH.mu(), r, v), energy(EARTH.mu(), ahead.r(), ahead.v()), 1e-9);
        }
    }

    @Test
    void ellipseApoapsisAfterHalfAPeriod() {
        double speed = Math.sqrt(EARTH.mu() * (2.0 / LEO - 2.0 / (LEO + 60_000)));
        Kepler.State apo = Kepler.propagate(EARTH.mu(), new V3(LEO, 0, 0), new V3(0, 0, speed), 7_955.34);
        assertEquals(60_000.0, apo.r().length(), 0.5);
        assertEquals(-1.0, apo.r().unit().x(), 1e-5);
    }

    @Test
    void hyperbolaKeepsItsEnergyAndMovesAway() {
        V3 r = new V3(MOON.radius() + 500, 0, 0);
        V3 v = new V3(0, 0, 10.05);
        double e0 = energy(MOON.mu(), r, v);
        assertTrue(e0 > 0);
        Kepler.State s = Kepler.propagate(MOON.mu(), r, v, 3_000);
        assertEquals(e0, energy(MOON.mu(), s.r(), s.v()), 1e-9);
        assertTrue(s.r().length() > 20_000);
        // o momento angular se conserva
        assertEquals(r.cross(v).length(), s.r().cross(s.v()).length(), 1e-6);
    }

    @Test
    void radialFallFromRestReachesTheCenterInFreeFallTime() {
        // queda radial a partir do repouso em r0: tempo de queda = (π/2)·√(r0³/(2·mu)) para r → 0
        double r0 = 20_000;
        double fall = Math.PI / 2 * Math.sqrt(r0 * r0 * r0 / (2 * EARTH.mu()));
        Kepler.State s = Kepler.propagate(EARTH.mu(), new V3(r0, 0, 0), V3.ZERO, fall * 0.999);
        assertTrue(s.r().length() < 0.02 * r0, "r = " + s.r().length());
        assertTrue(s.v().x() < 0);
    }

    @Test
    void stumpffFunctionsAreContinuousAroundTheSeriesThreshold() {
        for (double z : new double[] {-1e-2, 1e-2, 1e-3, -1e-3, 0}) {
            double c = Kepler.stumpffC(z);
            double s = Kepler.stumpffS(z);
            assertEquals(0.5, c, 0.02);
            assertEquals(1.0 / 6, s, 0.01);
        }
        // continuidade na emenda entre a série e a fórmula fechada
        assertEquals(Kepler.stumpffC(0.0100001), Kepler.stumpffC(0.0099999), 1e-8);
        assertEquals(Kepler.stumpffS(-0.0100001), Kepler.stumpffS(-0.0099999), 1e-8);
    }
}
