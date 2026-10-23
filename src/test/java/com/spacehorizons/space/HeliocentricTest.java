package com.spacehorizons.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.planet.Planets;
import com.spacehorizons.space.flight.BodyPhysics;
import com.spacehorizons.space.flight.Hohmann;

/** O sistema solar centrado no sol: órbitas da Terra e de Marte, a ponte H ↔ I e os corpos do {@link SolarSystem}. */
class HeliocentricTest {
    private static double length(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    @Test
    void keplersThirdLawGivesTheTwoPeriods() {
        assertEquals(Heliocentric.EARTH_PERIOD_TICKS, 2.0 * Math.PI * Math.sqrt(Math.pow(Heliocentric.EARTH_ORBIT_RADIUS, 3) / Heliocentric.MU_SUN), 1e-6);
        assertEquals(Heliocentric.EARTH_PERIOD_TICKS * Math.pow(Heliocentric.MARS_TO_EARTH_ORBIT, 1.5), Heliocentric.MARS_PERIOD_TICKS, 1e-6);
        assertEquals(240_000.0, Heliocentric.EARTH_PERIOD_TICKS, 0.0);
        assertEquals(451_532.0, Heliocentric.MARS_PERIOD_TICKS, 1.0);
        // período sinódico ~512.000 ticks (21 dias do jogo): de uma janela de lançamento à seguinte
        assertEquals(512_299.0, Heliocentric.synodicPeriod(), 1.0);
    }

    @Test
    void theOrbitsAreCirclesAtTheRightSpeed() {
        double[] p = new double[3];
        double[] v = new double[3];
        for (double clock = 0; clock < 600_000; clock += 12_345) {
            Heliocentric.earthPosition(clock, p);
            Heliocentric.earthVelocity(clock, v);
            assertEquals(Heliocentric.EARTH_ORBIT_RADIUS, length(p), 1e-6);
            assertEquals(Heliocentric.earthSpeed(), length(v), 1e-9);
            assertEquals(0.0, p[0] * v[0] + p[1] * v[1] + p[2] * v[2], 1e-3, "a velocidade é perpendicular ao raio");
            Heliocentric.marsPosition(clock, p);
            Heliocentric.marsVelocity(clock, v);
            assertEquals(Heliocentric.MARS_ORBIT_RADIUS, length(p), 1e-6);
            assertEquals(Heliocentric.marsSpeed(), length(v), 1e-9);
        }
        // a velocidade circular bate com sqrt(mu/r)
        assertEquals(Math.sqrt(Heliocentric.MU_SUN / Heliocentric.EARTH_ORBIT_RADIUS), Heliocentric.earthSpeed(), 1e-9);
        assertEquals(104.72, Heliocentric.earthSpeed(), 0.01);
        assertEquals(84.83, Heliocentric.marsSpeed(), 0.01);
    }

    @Test
    void theEarthSystemFrameKeepsTheSunFixedAtMinusX() {
        double[] earth = new double[3];
        double[] i = new double[3];
        for (double clock = 0; clock < 500_000; clock += 7_777) {
            Heliocentric.earthPosition(clock, earth);
            // o sol visto da Terra: o vetor Terra → sol (−posição da Terra em H), em I, é sempre −X
            Heliocentric.toEarthSystem(clock, -earth[0], -earth[1], -earth[2], i);
            assertEquals(-Heliocentric.EARTH_ORBIT_RADIUS, i[0], 1e-6);
            assertEquals(0.0, i[1], 1e-9);
            assertEquals(0.0, i[2], 1e-6);
        }
    }

    @Test
    void theBridgeBetweenFramesIsAnExactRotation() {
        double[] a = new double[3];
        double[] b = new double[3];
        for (double clock = 0; clock < 500_000; clock += 31_111) {
            Heliocentric.toEarthSystem(clock, 1.5, -2.5, 7.0, a);
            Heliocentric.fromEarthSystem(clock, a[0], a[1], a[2], b);
            assertEquals(1.5, b[0], 1e-12);
            assertEquals(-2.5, b[1], 1e-12);
            assertEquals(7.0, b[2], 1e-12);
            assertEquals(Math.sqrt(1.5 * 1.5 + 2.5 * 2.5 + 49.0), length(a), 1e-12);
        }
    }

    @Test
    void marsRelativeToTheEarthIsTheDifferenceOfThePositions() {
        double[] mars = new double[3];
        double[] earth = new double[3];
        double[] inI = new double[3];
        double[] expected = new double[3];
        for (double clock = 0; clock < 800_000; clock += 40_001) {
            Heliocentric.marsPosition(clock, mars);
            Heliocentric.earthPosition(clock, earth);
            Heliocentric.marsInEarthSystem(clock, inI);
            Heliocentric.toEarthSystem(clock, mars[0] - earth[0], mars[1] - earth[1], mars[2] - earth[2], expected);
            for (int k = 0; k < 3; k++) {
                assertEquals(expected[k], inI[k], 1e-6);
            }
            double d = Heliocentric.earthMarsDistance(clock);
            assertTrue(d >= Heliocentric.MARS_ORBIT_RADIUS - Heliocentric.EARTH_ORBIT_RADIUS - 1.0);
            assertTrue(d <= Heliocentric.MARS_ORBIT_RADIUS + Heliocentric.EARTH_ORBIT_RADIUS + 1.0);
        }
    }

    @Test
    void marsPhaseAheadShrinksByTheSynodicRate() {
        double rate = Heliocentric.EARTH_MEAN_MOTION - Heliocentric.MARS_MEAN_MOTION;
        double a0 = Heliocentric.marsPhaseAhead(1_000);
        double a1 = Heliocentric.marsPhaseAhead(1_000 + 10_000);
        double diff = ((a0 - a1) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
        assertEquals(rate * 10_000, diff, 1e-9);
        // uma janela por período sinódico
        assertEquals(a0, Heliocentric.marsPhaseAhead(1_000 + Heliocentric.synodicPeriod()), 1e-6);
    }

    @Test
    void hohmannBetweenTheTwoOrbitsTakesAboutSeventyPercentOfAYear() {
        double mu = Heliocentric.MU_SUN;
        double r1 = Heliocentric.EARTH_ORBIT_RADIUS;
        double r2 = Heliocentric.MARS_ORBIT_RADIUS;
        double time = Hohmann.flightTime(mu, r1, r2);
        assertEquals(170_126.0, time, 1.0);
        assertEquals(0.709, time / Heliocentric.EARTH_PERIOD_TICKS, 0.001);
        assertEquals(10.36, Hohmann.dv1(mu, r1, r2), 0.01);
        assertEquals(9.32, Hohmann.dv2(mu, r1, r2), 0.05);
        // o ângulo de fase ideal de Hohmann é o real: ~44°
        assertEquals(44.3, Math.toDegrees(Hohmann.phaseAngle(time, Heliocentric.MARS_MEAN_MOTION)), 0.5);
    }

    @Test
    void theBodiesOfTheSystem() {
        assertTrue(SolarSystem.BODIES.contains(SolarSystem.MARS));
        assertEquals(SolarSystem.Frame.EARTH_SYSTEM, SolarSystem.EARTH.frame());
        assertEquals(SolarSystem.Frame.EARTH_SYSTEM, SolarSystem.MOON.frame());
        assertEquals(SolarSystem.Frame.HELIOCENTRIC, SolarSystem.MARS.frame());
        assertEquals(SolarSystem.Frame.HELIOCENTRIC, SolarSystem.SUN.frame());
        // mu do sol: o planeta do sol dá exatamente o parâmetro de Heliocentric
        assertEquals(Heliocentric.MU_SUN, BodyPhysics.of(SolarSystem.SUN).mu(), Heliocentric.MU_SUN * 1e-12);
        assertEquals(Heliocentric.SUN_RADIUS, BodyPhysics.of(SolarSystem.SUN).radius(), 1.0);
        assertEquals(Double.POSITIVE_INFINITY, BodyPhysics.of(SolarSystem.SUN).soiRadius());
    }

    @Test
    void marsPoseInEarthFixedIsItsRealPositionRelativeToTheEarth() {
        double[] expected = new double[3];
        double[] inE = new double[3];
        for (double clock = 0; clock < 800_000; clock += 33_333) {
            Heliocentric.marsInEarthSystem(clock, expected);
            InertialFrame.toEarthFixed(clock, expected[0], expected[1], expected[2], inE);
            SolarSystem.Pose pose = SolarSystem.MARS.poseAt(clock);
            assertEquals(inE[0], pose.x(), 1e-6);
            assertEquals(inE[1], pose.y(), 1e-6);
            assertEquals(inE[2], pose.z(), 1e-6);
            // a rotação é ortonormal
            double[] r = pose.rotation();
            assertEquals(1.0, r[0] * r[0] + r[1] * r[1] + r[2] * r[2], 1e-12);
        }
    }

    @Test
    void theSunSeenFromMarsPointsAtTheSunAndFromTheEarthIsTheOldDirection() {
        double[] old = new double[3];
        double[] fromEarth = new double[3];
        double[] fromMars = new double[3];
        for (double clock = 0; clock < 800_000; clock += 44_444) {
            SunModel.directionInSpace(clock, old);
            SolarSystem.sunDirectionFrom(SolarSystem.EARTH, clock, fromEarth);
            for (int k = 0; k < 3; k++) {
                assertEquals(old[k], fromEarth[k], 0.0);
            }
            SolarSystem.sunDirectionFrom(SolarSystem.MARS, clock, fromMars);
            assertEquals(1.0, length(fromMars), 1e-12);
            // o sol está à distância de Marte ao sol na direção devolvida
            SolarSystem.Pose mars = SolarSystem.MARS.poseAt(clock);
            SolarSystem.Pose sun = SolarSystem.SUN.poseAt(clock);
            double d = Math.sqrt(Math.pow(sun.x() - mars.x(), 2) + Math.pow(sun.y() - mars.y(), 2) + Math.pow(sun.z() - mars.z(), 2));
            assertEquals(Heliocentric.MARS_ORBIT_RADIUS, d, 1e-3);
            assertEquals(sun.x(), mars.x() + fromMars[0] * d, 1e-3);
            assertEquals(sun.z(), mars.z() + fromMars[2] * d, 1e-3);
        }
        assertTrue(Planets.MARS.radius() > 0);
    }
}
