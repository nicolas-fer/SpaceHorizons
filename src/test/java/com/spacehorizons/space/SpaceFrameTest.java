package com.spacehorizons.space;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
