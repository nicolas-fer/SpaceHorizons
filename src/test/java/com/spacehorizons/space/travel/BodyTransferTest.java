package com.spacehorizons.space.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceFrame;

class BodyTransferTest {
    private static final double EPS = 1e-6;

    /** {x, y, z, vx, vy, vz, yaw, pitch} do jogador na Terra, perto do eixo do spawn (a Lua fica no zênite). */
    private static final double[][] CASES = {
            {0.5, 30_000, 0.5, 0, 2, 0, 0, -90},
            {0.5, 30_000, 0.5, 0, 2, 0, 40, -60},
            {350, 38_500, -120, 0.3, 1.5, -0.2, -135, -30},
            {-2_000, 36_000, 900, 0, -1, 0.5, 90, 10},
            {5_000, 45_000, 4_000, 0.1, 0.1, 0.1, 12, 45},
    };

    /** Vetor "para cima" da câmera (sem rolagem) para yaw/pitch em graus, nos eixos do Minecraft. */
    private static double[] cameraUp(double yawDeg, double pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        return new double[] {-Math.sin(yaw) * Math.sin(pitch), Math.cos(pitch), Math.cos(yaw) * Math.sin(pitch)};
    }

    @Test
    void verticalLookKeepsTheImageOrientation() {
        // olhando para a Lua do zênite (pitch -90) e para a Terra do chão da Lua: o "para cima" da tela é o mesmo no espaço
        double[][] cases = {{0.5, 30_000, 0.5, 20, -90}, {0.5, 30_000, 0.5, -135, -90}, {0.5, 30_000, 0.5, 70, 90}};
        for (double[] c : cases) {
            BodyTransfer t = BodyTransfer.compute(SolarSystem.EARTH, SolarSystem.MOON, c[0], c[1], c[2], 0, 0, 0,
                    (float) c[3], (float) c[4]);
            double[] oldUp = cameraUp(c[3], c[4]);
            double[] newUp = cameraUp(t.yRot(), t.xRot());
            double[] before = new double[3];
            double[] after = new double[3];
            SpaceFrame.localToSpace(SolarSystem.EARTH, c[0], c[2], oldUp[0], oldUp[1], oldUp[2], before);
            SpaceFrame.localToSpace(t.target(), t.x(), t.z(), newUp[0], newUp[1], newUp[2], after);
            // o olhar quase vertical pode errar até VERTICAL_EPSILON para a imagem não girar
            assertVec(before, after, 2e-3);
        }
    }

    @Test
    void positionVelocityAndLookAreTheSameVectorsInSpace() {
        for (double[] c : CASES) {
            BodyTransfer t = BodyTransfer.compute(SolarSystem.EARTH, SolarSystem.MOON, c[0], c[1], c[2], c[3], c[4], c[5],
                    (float) c[6], (float) c[7]);

            double[] before = new double[3];
            double[] after = new double[3];
            SpaceFrame.toSpace(SolarSystem.EARTH, c[0], c[1], c[2], before);
            SpaceFrame.toSpace(t.target(), t.x(), t.y(), t.z(), after);
            assertVec(before, after, 1e-5);

            SpaceFrame.localToSpace(SolarSystem.EARTH, c[0], c[2], c[3], c[4], c[5], before);
            SpaceFrame.localToSpace(t.target(), t.x(), t.z(), t.vx(), t.vy(), t.vz(), after);
            assertVec(before, after, EPS);

            double[] look = new double[3];
            TravelMath.lookVector((float) c[6], (float) c[7], look);
            SpaceFrame.localToSpace(SolarSystem.EARTH, c[0], c[2], look[0], look[1], look[2], before);
            TravelMath.lookVector(t.yRot(), t.xRot(), look);
            SpaceFrame.localToSpace(t.target(), t.x(), t.z(), look[0], look[1], look[2], after);
            // yaw/pitch são float: a direção bate com ~1e-5; perto da vertical, até ~1e-3 (ver BodyTransfer.VERTICAL_EPSILON)
            assertVec(before, after, 1e-3);
        }
    }

    @Test
    void speedIsPreservedByTheChangeOfBasis() {
        BodyTransfer t = BodyTransfer.compute(SolarSystem.EARTH, SolarSystem.MOON, 0.5, 38_000, 0.5, 3, 4, 12, 10, -20);
        assertEquals(13.0, Math.sqrt(t.vx() * t.vx() + t.vy() * t.vy() + t.vz() * t.vz()), EPS);
    }

    @Test
    void roundTripEarthMoonEarthRestoresEverything() {
        for (double[] c : CASES) {
            BodyTransfer there = BodyTransfer.compute(SolarSystem.EARTH, SolarSystem.MOON, c[0], c[1], c[2], c[3], c[4], c[5],
                    (float) c[6], (float) c[7]);
            BodyTransfer back = BodyTransfer.compute(SolarSystem.MOON, SolarSystem.EARTH, there.x(), there.y(), there.z(),
                    there.vx(), there.vy(), there.vz(), there.yRot(), there.xRot());
            assertEquals(c[0], TravelMath.normalizeX(SolarSystem.EARTH, back.x()), 1e-4);
            assertEquals(c[1], back.y(), 1e-4);
            assertEquals(c[2], back.z(), 1e-4);
            assertEquals(c[3], back.vx(), 1e-5);
            assertEquals(c[4], back.vy(), 1e-5);
            assertEquals(c[5], back.vz(), 1e-5);
            assertEquals(c[7], back.xRot(), 0.1);
            // yaw só é definido fora da vertical; aqui o olhar de 90 graus de pitch tem yaw preservado pelo vetor "para frente"
            if (Math.abs(c[7]) < 89) {
                assertEquals(0, Math.IEEEremainder(c[6] - back.yRot(), 360), 1e-3);
            }
        }
    }

    @Test
    void lookingStraightUpAtTheMoonLooksStraightDownOnArrival() {
        // no zênite do spawn: olhar para a Lua (pitch -90) vira olhar para o chão lunar (pitch +90) na face voltada à Terra
        BodyTransfer t = BodyTransfer.compute(SolarSystem.EARTH, SolarSystem.MOON, 0.5, 40_000, 0.5, 0, 3, 0, 0, -90);
        assertEquals(90.0, t.xRot(), 0.1);
        // posição na Lua: sobre a face voltada para a Terra (|x| ≈ C/2, z ≈ 0), a ~7.600 blocos de altitude
        assertEquals(SolarSystem.MOON.planet().halfCircumference(), Math.abs(t.x()), 1.0);
        assertEquals(0, t.z(), 1.0);
        double altitude = t.y() - SolarSystem.MOON.planet().seaLevel();
        assertEquals(SolarSystem.EARTH_MOON_DISTANCE - (40_000 - 63 + SolarSystem.EARTH.planet().radius()) - SolarSystem.MOON.planet().radius(),
                altitude, 0.05);
        // subindo (+y) na Terra é descer (-y) na Lua
        assertEquals(-3.0, t.vy(), EPS);
    }

    @Test
    void crossingTheSeamKeepsXInsideThePlanetRange() {
        BodyTransfer t = BodyTransfer.compute(SolarSystem.EARTH, SolarSystem.MOON, 0.5, 40_000, 0.5, 0, 0, 0, 0, 0);
        double half = SolarSystem.MOON.planet().halfCircumference();
        assertEquals(true, t.x() >= -half && t.x() < half, "x = " + t.x());
    }

    private static void assertVec(double[] expected, double[] actual, double eps) {
        for (int i = 0; i < 3; i++) {
            assertEquals(expected[i], actual[i], eps, "componente " + i);
        }
    }
}
