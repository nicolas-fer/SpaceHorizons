package com.spacehorizons.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SolarSystemTest {
    private static final double D = SolarSystem.EARTH_MOON_DISTANCE;
    private static final double P = SolarSystem.SYNODIC_PERIOD_TICKS;
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
    void synodicPeriodIsEightGameDays() {
        assertEquals(8 * 24_000, P, 0);
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
            SolarSystem.Pose pose = SolarSystem.MOON.poseAt(clock);
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

    @Test
    void orbitAndOrientationRepeatEveryEightDays() {
        for (double clock : CLOCKS) {
            SolarSystem.Pose a = SolarSystem.MOON.poseAt(clock);
            SolarSystem.Pose b = SolarSystem.MOON.poseAt(clock + P);
            SolarSystem.Pose c = SolarSystem.MOON.poseAt(clock + 3 * P);
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
    void moonRisesOncePerLunarDayWhichIsLongerThanASolarDay() {
        // do spawn da Terra (zênite = +X) a Lua nasce 7 vezes a cada 8 dias do jogo (cada nascer ~1,14 dia depois do anterior)
        int rises = 0;
        boolean up = moon(0)[0] > 0;
        for (double clock = 0; clock < P; clock += 25) {
            boolean nowUp = moon(clock)[0] > 0;
            if (nowUp && !up) {
                rises++;
            }
            up = nowUp;
        }
        assertEquals(7, rises);
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
    void moonMovesAboutTenToFifteenBlocksPerTick() {
        double[] v = new double[3];
        double min = Double.MAX_VALUE;
        double max = 0;
        for (double clock = 0; clock < 24_000; clock += 100) {
            SolarSystem.MOON.velocityAt(clock, v);
            double speed = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            min = Math.min(min, speed);
            max = Math.max(max, speed);
        }
        // o ângulo da Lua acompanha o do sol (Bézier do vanilla), então a velocidade oscila em torno de ~14
        assertTrue(min > 8, "mín " + min);
        assertTrue(max < 22, "máx " + max);
    }

    @Test
    void velocityIsTheDerivativeOfPosition() {
        double[] v = new double[3];
        for (double clock : CLOCKS) {
            SolarSystem.MOON.velocityAt(clock, v);
            double[] a = moon(clock - 0.01);
            double[] b = moon(clock + 0.01);
            for (int i = 0; i < 3; i++) {
                assertEquals((b[i] - a[i]) / 0.02, v[i], 5e-3);
            }
        }
    }

    @Test
    void phaseCycleIsEightDaysAndFollowsTheVanillaPhases() {
        // vanilla: dia 0 = lua cheia, dia 4 = nova, dias 2 e 6 = quartos; vista a cada meia-noite
        double midnight0 = 18_000;
        for (int day = 0; day < 16; day++) {
            double f = SolarSystem.moonIlluminatedFraction(midnight0 + day * 24_000.0);
            switch (day % 8) {
                case 0 -> assertTrue(f > 0.99, "dia " + day + ": " + f);
                case 4 -> assertTrue(f < 0.01, "dia " + day + ": " + f);
                case 2, 6 -> assertEquals(0.5, f, 0.03, "dia " + day);
                case 1, 7 -> assertTrue(f > 0.7 && f < 0.95, "dia " + day + ": " + f);
                default -> assertTrue(f > 0.05 && f < 0.3, "dia " + day + ": " + f);
            }
        }
    }

    @Test
    void fullMoonsAreEightDaysApart() {
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
        // o mundo novo começa perto do tick 0 (e o primeiro dia inteiro): lua gibosa, nunca perto do sol
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
