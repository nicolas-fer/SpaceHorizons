package com.spacehorizons.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SolarSystemTest {
    private static final double D = SolarSystem.EARTH_MOON_DISTANCE;
    private static final double P = SolarSystem.ORBITAL_PERIOD_TICKS;
    private static final double[] CLOCKS = {0, 1, 999.5, 6000, 12_345.25, 18_000, 23_999, 47_000.75, 114_000, 191_999, 500_000};

    private static double[] moon(double clock) {
        double[] p = new double[3];
        SolarSystem.MOON.positionAt(clock, p);
        return p;
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    @Test
    void orbitalPeriodIsKeplerianAroundTheEarth() {
        // T = 2π·sqrt(a³/μ), μ = G0·g·R² da Terra (8,7034e8): ~98.985 ticks (~4,12 dias do jogo)
        assertEquals(2.0 * Math.PI * Math.sqrt(D * D * D / SolarSystem.EARTH_MU), P, 1e-6);
        assertEquals(98_985.0, P, 5.0);
        assertEquals(8.7034e8, SolarSystem.EARTH_MU, 1.0e5);
        // a velocidade orbital em I é n·D ≈ 38 blocos/tick
        assertEquals(38.08, SolarSystem.MOON_MEAN_MOTION * D, 0.02);
    }

    @Test
    void moonOrbitsEastwardInTheInertialFrameAtAConstantRateAndRadius() {
        double[] v = new double[3];
        double[] p = new double[3];
        for (double clock : CLOCKS) {
            SolarSystem.MOON.inertialPositionAt(clock, p);
            assertEquals(D, Math.sqrt(dot(p, p)), 1e-6);
            SolarSystem.MOON.inertialVelocityAt(clock, v);
            assertEquals(SolarSystem.MOON_MEAN_MOTION * D, Math.sqrt(dot(v, v)), 1e-4, "clock " + clock);
            // prógrada (leste): o momento angular r × v aponta para o sul (−Y) como o da rotação da Terra, a menos dos 5°
            double[] h = {p[1] * v[2] - p[2] * v[1], p[2] * v[0] - p[0] * v[2], p[0] * v[1] - p[1] * v[0]};
            assertTrue(h[1] / Math.sqrt(dot(h, h)) < -Math.cos(SolarSystem.ORBIT_INCLINATION) + 1e-9, "h = " + h[1]);
        }
    }

    @Test
    void inertialMoonIsTheEarthFixedMoonRotatedByTheEarthRotation() {
        double[] i = new double[3];
        double[] e = new double[3];
        double[] back = new double[3];
        for (double clock : CLOCKS) {
            SolarSystem.MOON.inertialPositionAt(clock, i);
            SolarSystem.MOON.positionAt(clock, e);
            InertialFrame.toInertial(clock, e[0], e[1], e[2], back);
            for (int k = 0; k < 3; k++) {
                assertEquals(i[k], back[k], 1e-6);
            }
        }
    }

    @Test
    void fullMoonAtMidnightOfDayZeroLikeTheVanilla() {
        double[] sun = new double[3];
        SunModel.directionInSpace(18_000, sun);
        double[] m = moon(18_000);
        // lua cheia: a Lua do lado oposto ao sol (a menos dos 5° de inclinação) e (E = I neste instante) em +X
        assertEquals(-Math.cos(SolarSystem.ORBIT_INCLINATION), dot(sun, m) / D, 1e-9);
        assertTrue(SolarSystem.moonIlluminatedFraction(18_000) > 0.998);
        assertEquals(-Math.PI, SolarSystem.moonElongation(18_000), 1e-12);
    }

    @Test
    void earthStaysPutWithTheIdentity() {
        double[] identity = {1, 0, 0, 0, 1, 0, 0, 0, 1};
        for (double clock : CLOCKS) {
            SolarSystem.Pose pose = SolarSystem.EARTH.poseAt(clock);
            assertEquals(0, pose.x(), 0);
            assertEquals(0, pose.y(), 0);
            assertEquals(0, pose.z(), 0);
            for (int i = 0; i < 9; i++) {
                assertEquals(identity[i], pose.rotation()[i], 0);
            }
        }
    }

    @Test
    void moonStaysOnACircleOfTheOrbitRadiusWithAProperRotation() {
        for (double clock : CLOCKS) {
            for (SolarSystem.Pose pose : new SolarSystem.Pose[] {SolarSystem.MOON.poseAt(clock), SolarSystem.MOON.inertialPoseAt(clock)}) {
                assertEquals(D, Math.sqrt(pose.x() * pose.x() + pose.y() * pose.y() + pose.z() * pose.z()), 1e-6);
                double[] r = pose.rotation();
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        double dot = r[i] * r[j] + r[3 + i] * r[3 + j] + r[6 + i] * r[6 + j];
                        assertEquals(i == j ? 1.0 : 0.0, dot, 1e-12);
                    }
                }
                // determinante +1 (rotação, não reflexão)
                double det = r[0] * (r[4] * r[8] - r[5] * r[7]) - r[1] * (r[3] * r[8] - r[5] * r[6]) + r[2] * (r[3] * r[7] - r[4] * r[6]);
                assertEquals(1.0, det, 1e-12);
            }
        }
    }

    @Test
    void inertialOrbitAndOrientationRepeatEveryOrbitalPeriod() {
        for (double clock : CLOCKS) {
            SolarSystem.Pose a = SolarSystem.MOON.inertialPoseAt(clock);
            SolarSystem.Pose b = SolarSystem.MOON.inertialPoseAt(clock + P);
            SolarSystem.Pose c = SolarSystem.MOON.inertialPoseAt(clock + 3 * P);
            assertEquals(a.x(), b.x(), 1e-6);
            assertEquals(a.y(), b.y(), 1e-6);
            assertEquals(a.z(), b.z(), 1e-6);
            assertEquals(a.x(), c.x(), 1e-6);
            for (int i = 0; i < 9; i++) {
                assertEquals(a.rotation()[i], b.rotation()[i], 1e-9);
            }
        }
    }

    @Test
    void moonRisesEveryAboutOnePointThreeTwoDays() {
        // do spawn da Terra (zênite = +X) a Lua nasce a leste a cada 1/(1/24000 − 1/T) ≈ 31.677 ticks (~1,32 dia do jogo)
        int rises = 0;
        double first = Double.NaN;
        double last = Double.NaN;
        boolean up = moon(0)[0] > 0;
        for (double clock = 0; clock < 8 * P; clock += 25) {
            boolean nowUp = moon(clock)[0] > 0;
            if (nowUp && !up) {
                rises++;
                if (Double.isNaN(first)) {
                    first = clock;
                }
                last = clock;
            }
            up = nowUp;
        }
        double expected = 1.0 / (1.0 / SunModel.DAY_TICKS - 1.0 / P);
        assertEquals(31_677.0, expected, 5.0);
        assertTrue(rises > 20, "nasceres em 8 períodos: " + rises);
        assertEquals(expected, (last - first) / (rises - 1), 0.01 * expected);
    }

    @Test
    void moonOrbitIsInclinedAboutFiveDegreesToTheEquator() {
        double maxY = 0;
        for (double clock = 0; clock < P; clock += 50) {
            maxY = Math.max(maxY, Math.abs(moon(clock)[1]));
        }
        assertEquals(Math.sin(Math.toRadians(5.0)) * D, maxY, 5.0);
    }

    @Test
    void moonMovesAboutSixtyToOneFiftyBlocksPerTickInTheEarthFixedFrame() {
        double[] v = new double[3];
        double min = Double.MAX_VALUE;
        double max = 0;
        for (double clock = 0; clock < 24_000; clock += 100) {
            SolarSystem.MOON.velocityAt(clock, v);
            double speed = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            min = Math.min(min, speed);
            max = Math.max(max, speed);
        }
        // em E a Lua anda a (φ̇ − n)·D; φ̇ segue a curva do sol do vanilla (0,67 a 1,19 vezes a média), então oscila de ~66 a ~149
        assertTrue(min > 55 && min < 80, "mín " + min);
        assertTrue(max > 135 && max < 165, "máx " + max);
    }

    @Test
    void velocityIsTheDerivativeOfPosition() {
        double[] v = new double[3];
        double[] vi = new double[3];
        for (double clock : CLOCKS) {
            SolarSystem.MOON.velocityAt(clock, v);
            SolarSystem.MOON.inertialVelocityAt(clock, vi);
            double[] a = moon(clock - 0.01);
            double[] b = moon(clock + 0.01);
            double[] ia = new double[3];
            double[] ib = new double[3];
            SolarSystem.MOON.inertialPositionAt(clock - 0.01, ia);
            SolarSystem.MOON.inertialPositionAt(clock + 0.01, ib);
            for (int i = 0; i < 3; i++) {
                assertEquals((b[i] - a[i]) / 0.02, v[i], 5e-3);
                assertEquals((ib[i] - ia[i]) / 0.02, vi[i], 5e-3);
            }
        }
    }

    @Test
    void phaseCycleIsTheOrbitalPeriod() {
        // cheia no tick 18.000 do dia 0; nova a meio período; quartos a 1/4 e 3/4; cheia de novo depois de um período
        // (o sol é fixo em I: o ciclo de fases é o próprio período orbital, ~4,12 dias, e não os 8 dias das fases do vanilla)
        double full = 18_000;
        assertTrue(SolarSystem.moonIlluminatedFraction(full) > 0.99);
        assertTrue(SolarSystem.moonIlluminatedFraction(full + P / 2) < 0.01);
        assertEquals(0.5, SolarSystem.moonIlluminatedFraction(full + P / 4), 0.03);
        assertEquals(0.5, SolarSystem.moonIlluminatedFraction(full + 3 * P / 4), 0.03);
        assertTrue(SolarSystem.moonIlluminatedFraction(full + P) > 0.99);
        assertTrue(SolarSystem.moonIlluminatedFraction(full + 5 * P) > 0.99);
    }

    @Test
    void fullMoonsAreOneOrbitalPeriodApart() {
        double previous = Double.NaN;
        int count = 0;
        double prevF = 0;
        double prevPrevF = 0;
        for (double clock = 0; clock < 4 * P; clock += 200) {
            double f = SolarSystem.moonIlluminatedFraction(clock);
            if (prevF > prevPrevF && prevF >= f && prevF > 0.995) {
                double peak = clock - 200;
                if (!Double.isNaN(previous)) {
                    assertEquals(P, peak - previous, 400, "intervalo entre luas cheias");
                }
                previous = peak;
                count++;
            }
            prevPrevF = prevF;
            prevF = f;
        }
        assertTrue(count >= 3, "luas cheias " + count);
    }

    @Test
    void aNewWorldDoesNotStartInAnEclipse() {
        // o mundo novo começa perto do tick 0 (e o primeiro dia inteiro): a Lua a mais de 60° do sol
        double[] sun = new double[3];
        for (double clock = 0; clock <= 24_000; clock += 250) {
            SunModel.directionInSpace(clock, sun);
            double[] m = moon(clock);
            double angle = Math.toDegrees(Math.acos(dot(sun, m) / D));
            assertTrue(angle > 60, "a Lua está a " + angle + " graus do sol no tick " + clock);
        }
    }

    @Test
    void synchronousRotationKeepsTheEarthFacingPointTowardTheEarth() {
        double half = SolarSystem.MOON.planet().halfCircumference();
        double[] local = new double[3];
        for (double clock : CLOCKS) {
            // o centro da Terra visto da Lua (nas coordenadas da dimensão dela) é sempre o ponto lon=π, lat 0
            SpaceFrame.fromSpace(SolarSystem.MOON, clock, 0, 0, 0, local);
            assertEquals(half, Math.abs(local[0]), 1e-5, "x no tick " + clock);
            assertEquals(0, local[2], 1e-5);
            assertEquals(D - SolarSystem.MOON.planet().radius() + SolarSystem.MOON.planet().seaLevel(), local[1], 1e-5);
        }
    }

    @Test
    void theFacePointLooksStraightUpAtTheEarth() {
        double[] s = new double[3];
        double[] localDir = new double[3];
        double halfC = SolarSystem.MOON.planet().halfCircumference();
        for (double clock : CLOCKS) {
            SpaceFrame.toSpace(SolarSystem.MOON, clock, halfC, SolarSystem.MOON.planet().seaLevel(), 0, s);
            // da superfície da face até o centro da Terra: reto para cima (vertical local)
            SpaceFrame.spaceToLocal(SolarSystem.MOON, clock, halfC, 0, -s[0], -s[1], -s[2], localDir);
            double len = Math.sqrt(localDir[0] * localDir[0] + localDir[1] * localDir[1] + localDir[2] * localDir[2]);
            assertEquals(1.0, localDir[1] / len, 1e-9);
        }
    }

    @Test
    void theFarSideNeverSeesTheEarth() {
        double[] s = new double[3];
        double[] earthDir = new double[3];
        for (double clock : CLOCKS) {
            SpaceFrame.toSpace(SolarSystem.MOON, clock, 0, SolarSystem.MOON.planet().seaLevel(), 0, s);
            SpaceFrame.spaceToLocal(SolarSystem.MOON, clock, 0, 0, -s[0], -s[1], -s[2], earthDir);
            assertTrue(earthDir[1] < 0, "Terra acima do horizonte do lado oculto, up=" + earthDir[1]);
        }
    }

    @Test
    void bodyOfFindsThePlanets() {
        assertEquals(SolarSystem.EARTH, SolarSystem.bodyOf(SolarSystem.EARTH.planet()).orElseThrow());
        assertEquals(SolarSystem.MOON, SolarSystem.bodyOf(SolarSystem.MOON.planet()).orElseThrow());
    }
}
