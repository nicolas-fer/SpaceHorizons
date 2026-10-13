package com.spacehorizons.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpaceFrameTest {
    @Test
    void roundTripOnEarthAndMoon() {
        double[] s = new double[3];
        double[] back = new double[3];
        double[][] points = {{0, 63, 0}, {1234.5, 300, -800}, {-7000, 5000, 1500}, {100, 20000, 2000}};
        for (SolarSystem.Body body : SolarSystem.BODIES) {
            for (double[] p : points) {
                SpaceFrame.toSpace(body, p[0], p[1], p[2], s);
                SpaceFrame.fromSpace(body, s[0], s[1], s[2], back);
                assertEquals(p[1], back[1], 1e-6);
                assertEquals(p[2], back[2], 1e-6);
                double c = body.planet().circumferenceBlocks();
                assertEquals(0, Math.IEEEremainder(p[0] - back[0], c), 1e-6);
            }
        }
    }

    @Test
    void spawnZenithPointsAtTheMoon() {
        double[] s = new double[3];
        // 1 bloco acima do nível do mar no spawn da Terra fica sobre o eixo +X, entre a Terra e a Lua
        SpaceFrame.toSpace(SolarSystem.EARTH, 0, 64, 0, s);
        assertEquals(SolarSystem.EARTH.planet().radius() + 1, s[0], 1e-9);
        double[] local = new double[3];
        SpaceFrame.spaceToLocal(SolarSystem.EARTH, 0, 0, SolarSystem.MOON.x() - s[0], -s[1], -s[2], local);
        assertEquals(0, local[0], 1e-6);
        assertEquals(0, local[2], 1e-6);
        assertEquals(SolarSystem.EARTH_MOON_DISTANCE - s[0], local[1], 1e-6);
    }

    @Test
    void localBasisIsOrthonormalAndInverts() {
        double[] b = new double[9];
        SpaceFrame.localBasis(SolarSystem.MOON, 900, -1500, b);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double dot = b[i * 3] * b[j * 3] + b[i * 3 + 1] * b[j * 3 + 1] + b[i * 3 + 2] * b[j * 3 + 2];
                assertEquals(i == j ? 1.0 : 0.0, dot, 1e-12);
            }
        }
        double[] local = new double[3];
        double[] space = new double[3];
        SpaceFrame.spaceToLocal(SolarSystem.MOON, 900, -1500, 3, -7, 11, local);
        SpaceFrame.localToSpace(SolarSystem.MOON, 900, -1500, local[0], local[1], local[2], space);
        assertEquals(3, space[0], 1e-9);
        assertEquals(-7, space[1], 1e-9);
        assertEquals(11, space[2], 1e-9);
    }

    @Test
    void minecraftAxesMatchLocalBasis() {
        double[] a = new double[3];
        double[] east = new double[3];
        double[] north = new double[3];
        double[] up = new double[3];
        SpaceFrame.toSpace(SolarSystem.EARTH, 500, 63, 200, a);
        SpaceFrame.toSpace(SolarSystem.EARTH, 501, 63, 200, east);
        SpaceFrame.toSpace(SolarSystem.EARTH, 500, 63, 201, north);
        SpaceFrame.toSpace(SolarSystem.EARTH, 500, 64, 200, up);
        double[] local = new double[3];
        SpaceFrame.spaceToLocal(SolarSystem.EARTH, 500, 200, east[0] - a[0], east[1] - a[1], east[2] - a[2], local);
        assertEquals(1.0, local[0], 1e-3);
        assertEquals(0.0, local[2], 1e-3);
        SpaceFrame.spaceToLocal(SolarSystem.EARTH, 500, 200, north[0] - a[0], north[1] - a[1], north[2] - a[2], local);
        assertEquals(1.0, local[2], 1e-3);
        SpaceFrame.spaceToLocal(SolarSystem.EARTH, 500, 200, up[0] - a[0], up[1] - a[1], up[2] - a[2], local);
        assertEquals(1.0, local[1], 1e-9);
    }

    private static final double[] CLOCKS = {0, 3_333.3, 18_000, 55_555.5, 114_000, 400_000.25};

    @Test
    void roundTripWithTheMoonAtSeveralInstants() {
        double[] s = new double[3];
        double[] back = new double[3];
        double[][] points = {{0, 63, 0}, {1234.5, 300, -800}, {-7000, 5000, 1500}, {8191, 20000, 2000}, {-8192, 63, 0}};
        for (double clock : CLOCKS) {
            for (SolarSystem.Body body : SolarSystem.BODIES) {
                for (double[] p : points) {
                    SpaceFrame.toSpace(body, clock, p[0], p[1], p[2], s);
                    SpaceFrame.fromSpace(body, clock, s[0], s[1], s[2], back);
                    assertEquals(p[1], back[1], 1e-6);
                    assertEquals(p[2], back[2], 1e-6);
                    double c = body.planet().circumferenceBlocks();
                    assertEquals(0, Math.IEEEremainder(p[0] - back[0], c), 1e-6);
                }
            }
        }
    }

    @Test
    void bodyFixedAndSpaceRoundTripAndAnEarthPointIsItsOwnSpacePoint() {
        double[] fixed = new double[3];
        double[] space = new double[3];
        double[] back = new double[3];
        for (double clock : CLOCKS) {
            SpaceFrame.toBodyFixed(SolarSystem.MOON, 900, 300, -1500, fixed);
            SpaceFrame.bodyFixedToSpace(SolarSystem.MOON, clock, fixed[0], fixed[1], fixed[2], space);
            SpaceFrame.spaceToBodyFixed(SolarSystem.MOON, clock, space[0], space[1], space[2], back);
            for (int i = 0; i < 3; i++) {
                assertEquals(fixed[i], back[i], 1e-8);
            }
            // a Terra não gira neste referencial: fixo no corpo == espaço
            SpaceFrame.toBodyFixed(SolarSystem.EARTH, 900, 300, -1500, fixed);
            SpaceFrame.toSpace(SolarSystem.EARTH, clock, 900, 300, -1500, space);
            for (int i = 0; i < 3; i++) {
                assertEquals(fixed[i], space[i], 1e-9);
            }
        }
    }

    @Test
    void localVectorsRoundTripThroughTheSpaceAtAnyInstant() {
        double[] local = new double[3];
        double[] space = new double[3];
        for (double clock : CLOCKS) {
            for (SolarSystem.Body body : SolarSystem.BODIES) {
                SpaceFrame.spaceToLocal(body, clock, 900, -1500, 3, -7, 11, local);
                SpaceFrame.localToSpace(body, clock, 900, -1500, local[0], local[1], local[2], space);
                assertEquals(3, space[0], 1e-9);
                assertEquals(-7, space[1], 1e-9);
                assertEquals(11, space[2], 1e-9);
                // a conversão preserva o comprimento
                assertEquals(Math.sqrt(9 + 49 + 121), Math.sqrt(local[0] * local[0] + local[1] * local[1] + local[2] * local[2]), 1e-9);
            }
        }
    }

    @Test
    void theEarthIsUnchangedByTheTimeButTheMoonIsNot() {
        double[] a = new double[3];
        double[] b = new double[3];
        SpaceFrame.toSpace(SolarSystem.EARTH, 0, 500, 63, 200, a);
        SpaceFrame.toSpace(SolarSystem.EARTH, 77_777, 500, 63, 200, b);
        for (int i = 0; i < 3; i++) {
            assertEquals(a[i], b[i], 0);
        }
        SpaceFrame.toSpace(SolarSystem.MOON, 0, 500, 63, 200, a);
        SpaceFrame.toSpace(SolarSystem.MOON, 5_000, 500, 63, 200, b);
        assertTrue(Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]) > 1000);
    }

    @Test
    void groundVelocityIsZeroOnTheEarthAndFollowsTheSurfaceOnTheMoon() {
        double[] v = new double[3];
        double[] s = new double[3];
        for (double clock : CLOCKS) {
            SpaceFrame.toSpace(SolarSystem.EARTH, clock, 100, 200, 300, s);
            SpaceFrame.groundVelocity(SolarSystem.EARTH, clock, s[0], s[1], s[2], v);
            assertEquals(0, v[0], 0);
            assertEquals(0, v[1], 0);
            assertEquals(0, v[2], 0);

            // o chão da Lua num ponto fixo da dimensão se move como a derivada da posição desse ponto no espaço
            SpaceFrame.toSpace(SolarSystem.MOON, clock, -3000, 120, 700, s);
            SpaceFrame.groundVelocity(SolarSystem.MOON, clock, s[0], s[1], s[2], v);
            double[] before = new double[3];
            double[] after = new double[3];
            SpaceFrame.toSpace(SolarSystem.MOON, clock - 0.01, -3000, 120, 700, before);
            SpaceFrame.toSpace(SolarSystem.MOON, clock + 0.01, -3000, 120, 700, after);
            for (int i = 0; i < 3; i++) {
                assertEquals((after[i] - before[i]) / 0.02, v[i], 1e-4);
            }
        }
    }

    @Test
    void groundVelocityAddsTheRotationToTheCenterVelocity() {
        // alto sobre a Lua o chão anda mais (ω × r) do que o centro; no centro é exatamente a velocidade orbital
        double[] center = new double[3];
        double[] v = new double[3];
        double clock = 40_000;
        SolarSystem.MOON.velocityAt(clock, center);
        double[] c = new double[3];
        SolarSystem.MOON.positionAt(clock, c);
        SpaceFrame.groundVelocity(SolarSystem.MOON, clock, c[0], c[1], c[2], v);
        for (int i = 0; i < 3; i++) {
            assertEquals(center[i], v[i], 1e-6);
        }
        double[] far = new double[3];
        SpaceFrame.toSpace(SolarSystem.MOON, clock, 0, SolarSystem.MOON.planet().seaLevel() + 10_000, 0, far);
        SpaceFrame.groundVelocity(SolarSystem.MOON, clock, far[0], far[1], far[2], v);
        double speedCenter = Math.sqrt(center[0] * center[0] + center[1] * center[1] + center[2] * center[2]);
        double speedFar = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        assertTrue(Math.abs(speedFar - speedCenter) > 1.0, "rotação deveria somar: " + speedCenter + " vs " + speedFar);
    }

    @Test
    void meshToLocalOfTheObserverItselfIsTheLocalBasis() {
        double[] m = new double[9];
        double[] b = new double[9];
        for (double clock : CLOCKS) {
            for (SolarSystem.Body body : SolarSystem.BODIES) {
                SpaceFrame.meshToLocal(body, clock, 900, -1500, body, m);
                SpaceFrame.localBasis(body, 900, -1500, b);
                for (int i = 0; i < 9; i++) {
                    assertEquals(b[i], m[i], 1e-12);
                }
            }
        }
    }

    @Test
    void meshToLocalCarriesTheBodyAxesToTheCameraAxes() {
        // o eixo +X do corpo da Lua (lat 0 lon 0) visto de um ponto da Terra é a direção Terra->Lua nos eixos dele
        double[] m = new double[9];
        double[] moon = new double[3];
        double[] expected = new double[3];
        for (double clock : CLOCKS) {
            SpaceFrame.meshToLocal(SolarSystem.EARTH, clock, 0, 0, SolarSystem.MOON, m);
            SolarSystem.MOON.positionAt(clock, moon);
            SpaceFrame.spaceToLocal(SolarSystem.EARTH, clock, 0, 0, moon[0] / SolarSystem.EARTH_MOON_DISTANCE,
                    moon[1] / SolarSystem.EARTH_MOON_DISTANCE, moon[2] / SolarSystem.EARTH_MOON_DISTANCE, expected);
            assertEquals(expected[0], m[0], 1e-12);
            assertEquals(expected[1], m[3], 1e-12);
            assertEquals(expected[2], m[6], 1e-12);
        }
    }
}
