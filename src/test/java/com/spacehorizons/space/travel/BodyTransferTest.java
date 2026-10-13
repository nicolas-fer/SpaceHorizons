package com.spacehorizons.space.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceFrame;

class BodyTransferTest {
    private static final double EPS = 1e-6;
    private static final double[] CLOCKS = {0, 6_000, 18_000, 70_000.5, 123_456};

    /** {x, y, z, vx, vy, vz, yaw, pitch} do jogador na Terra: pontos quaisquer no espaço próximo (a conversão é geral). */
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

    /** Ponto da Terra (x, y, z) com a Lua no zênite exato no instante: o sublunar, a {@code altitude} blocos do nível do mar. */
    private static double[] subLunar(double clock, double altitude) {
        double[] m = new double[3];
        SolarSystem.MOON.positionAt(clock, m);
        double r = SolarSystem.EARTH.planet().radius() + altitude;
        double[] p = new double[3];
        SpaceFrame.fromSpace(SolarSystem.EARTH, clock, m[0] / SolarSystem.EARTH_MOON_DISTANCE * r,
                m[1] / SolarSystem.EARTH_MOON_DISTANCE * r, m[2] / SolarSystem.EARTH_MOON_DISTANCE * r, p);
        return p;
    }

    /** Velocidade no espaço do jogador no corpo: a relativa ao chão (eixos locais) mais a do chão. */
    private static double[] spaceVelocity(SolarSystem.Body body, double clock, double x, double y, double z, double vx, double vy,
            double vz) {
        double[] v = new double[3];
        SpaceFrame.localToSpace(body, clock, x, z, vx, vy, vz, v);
        double[] p = new double[3];
        SpaceFrame.toSpace(body, clock, x, y, z, p);
        double[] ground = new double[3];
        SpaceFrame.groundVelocity(body, clock, p[0], p[1], p[2], ground);
        return new double[] {v[0] + ground[0], v[1] + ground[1], v[2] + ground[2]};
    }

    @Test
    void verticalLookKeepsTheImageOrientation() {
        // olhando para a Lua a partir do sublunar (pitch -90) e para a Terra da face lunar: o "para cima" da tela é o mesmo no espaço
        for (double clock : CLOCKS) {
            double[] p = subLunar(clock, 30_000);
            double[][] cases = {{20, -90}, {-135, -90}, {70, 90}};
            for (double[] c : cases) {
                BodyTransfer t = BodyTransfer.compute(SolarSystem.EARTH, SolarSystem.MOON, clock, p[0], p[1], p[2], 0, 0, 0,
                        (float) c[0], (float) c[1]);
                double[] oldUp = cameraUp(c[0], c[1]);
                double[] newUp = cameraUp(t.yRot(), t.xRot());
                double[] before = new double[3];
                double[] after = new double[3];
                SpaceFrame.localToSpace(SolarSystem.EARTH, clock, p[0], p[2], oldUp[0], oldUp[1], oldUp[2], before);
                SpaceFrame.localToSpace(t.target(), clock, t.x(), t.z(), newUp[0], newUp[1], newUp[2], after);
                // o olhar quase vertical pode errar até VERTICAL_EPSILON para a imagem não girar
                assertVec(before, after, 2e-3);
            }
        }
    }

    @Test
    void positionAndLookAreTheSameVectorsInSpaceAndTheVelocityIsTheSameRelativeToTheNewGround() {
        for (double clock : CLOCKS) {
            for (double[] c : CASES) {
                BodyTransfer t = BodyTransfer.compute(SolarSystem.EARTH, SolarSystem.MOON, clock, c[0], c[1], c[2], c[3], c[4], c[5],
                        (float) c[6], (float) c[7]);

                double[] before = new double[3];
                double[] after = new double[3];
                SpaceFrame.toSpace(SolarSystem.EARTH, clock, c[0], c[1], c[2], before);
                SpaceFrame.toSpace(t.target(), clock, t.x(), t.y(), t.z(), after);
                assertVec(before, after, 1e-5);

                // a velocidade NO ESPAÇO se conserva: relativa ao chão + velocidade do chão, dos dois lados
                before = spaceVelocity(SolarSystem.EARTH, clock, c[0], c[1], c[2], c[3], c[4], c[5]);
                after = spaceVelocity(t.target(), clock, t.x(), t.y(), t.z(), t.vx(), t.vy(), t.vz());
                assertVec(before, after, EPS);

                double[] look = new double[3];
                TravelMath.lookVector((float) c[6], (float) c[7], look);
                SpaceFrame.localToSpace(SolarSystem.EARTH, clock, c[0], c[2], look[0], look[1], look[2], before);
                TravelMath.lookVector(t.yRot(), t.xRot(), look);
                SpaceFrame.localToSpace(t.target(), clock, t.x(), t.z(), look[0], look[1], look[2], after);
                // yaw/pitch são float: a direção bate com ~1e-5; perto da vertical, até ~1e-3 (ver BodyTransfer.VERTICAL_EPSILON)
                assertVec(before, after, 1e-3);
            }
        }
    }

    @Test
    void aShipAtRestOnTheEarthArrivesOnTheMoonWithTheMoonsGroundVelocityReversed() {
        // parado no referencial da Terra, na Lua o jogador "voa" a ~14 blocos/tick em relação a ela (a Lua anda por baixo dele)
        double clock = 40_000;
        double[] p = subLunar(clock, 40_000);
        BodyTransfer t = BodyTransfer.compute(SolarSystem.EARTH, SolarSystem.MOON, clock, p[0], p[1], p[2], 0, 0, 0, 0, 0);
        double speed = Math.sqrt(t.vx() * t.vx() + t.vy() * t.vy() + t.vz() * t.vz());
        assertTrue(speed > 5 && speed < 30, "velocidade relativa " + speed);
        double[] space = new double[3];
        SpaceFrame.toSpace(SolarSystem.MOON, clock, t.x(), t.y(), t.z(), space);
        double[] ground = new double[3];
        SpaceFrame.groundVelocity(SolarSystem.MOON, clock, space[0], space[1], space[2], ground);
        double[] relative = new double[3];
        SpaceFrame.localToSpace(SolarSystem.MOON, clock, t.x(), t.z(), t.vx(), t.vy(), t.vz(), relative);
        for (int i = 0; i < 3; i++) {
            assertEquals(-ground[i], relative[i], EPS);
        }
    }

    @Test
    void aShipMovingWithTheMoonArrivesAtRestRelativeToIt() {
        double clock = 40_000;
        double[] p = subLunar(clock, 40_000);
        double[] space = new double[3];
        SpaceFrame.toSpace(SolarSystem.EARTH, clock, p[0], p[1], p[2], space);
        double[] ground = new double[3];
        SpaceFrame.groundVelocity(SolarSystem.MOON, clock, space[0], space[1], space[2], ground);
        double[] v = new double[3];
        SpaceFrame.spaceToLocal(SolarSystem.EARTH, clock, p[0], p[2], ground[0], ground[1], ground[2], v);
        BodyTransfer t = BodyTransfer.compute(SolarSystem.EARTH, SolarSystem.MOON, clock, p[0], p[1], p[2], v[0], v[1], v[2], 0, 0);
        assertEquals(0, t.vx(), EPS);
        assertEquals(0, t.vy(), EPS);
        assertEquals(0, t.vz(), EPS);
    }

    @Test
    void roundTripEarthMoonEarthRestoresEverything() {
        for (double clock : CLOCKS) {
            for (double[] c : CASES) {
                BodyTransfer there = BodyTransfer.compute(SolarSystem.EARTH, SolarSystem.MOON, clock, c[0], c[1], c[2], c[3], c[4],
                        c[5], (float) c[6], (float) c[7]);
                BodyTransfer back = BodyTransfer.compute(SolarSystem.MOON, SolarSystem.EARTH, clock, there.x(), there.y(), there.z(),
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
    }

    @Test
    void lookingStraightUpAtTheMoonLooksStraightDownOnArrival() {
        for (double clock : CLOCKS) {
            // no sublunar: olhar para a Lua (pitch -90) vira olhar para o chão lunar (pitch +90) na face voltada à Terra
            double altitude = 40_000;
            double[] p = subLunar(clock, altitude);
            BodyTransfer t = BodyTransfer.compute(SolarSystem.EARTH, SolarSystem.MOON, clock, p[0], p[1], p[2], 0, 0, 0, 0, -90);
            assertEquals(90.0, t.xRot(), 0.1);
            // posição na Lua: sobre a face voltada para a Terra (|x| ≈ C/2, z ≈ 0)
            assertEquals(SolarSystem.MOON.planet().halfCircumference(), Math.abs(t.x()), 1.0);
            assertEquals(0, t.z(), 1.0);
            double shipRadius = SolarSystem.EARTH.planet().radius() + altitude;
            double moonAltitude = t.y() - SolarSystem.MOON.planet().seaLevel();
            assertEquals(SolarSystem.EARTH_MOON_DISTANCE - shipRadius - SolarSystem.MOON.planet().radius(), moonAltitude, 0.05);
        }
    }

    @Test
    void climbingTowardsTheMoonIsDescendingOnTheMoonAtTheSameRate() {
        // a velocidade orbital e a da rotação da Lua são perpendiculares à linha Terra-Lua: só somam componentes laterais,
        // então subir (+y) na Terra rumo à Lua é descer (-y) na Lua com a mesma velocidade vertical
        for (double clock : CLOCKS) {
            double[] p = subLunar(clock, 40_000);
            BodyTransfer t = BodyTransfer.compute(SolarSystem.EARTH, SolarSystem.MOON, clock, p[0], p[1], p[2], 0, 3, 0, 0, 0);
            assertEquals(-3.0, t.vy(), 1e-5);
        }
    }

    @Test
    void crossingTheSeamKeepsXInsideThePlanetRange() {
        for (double clock : CLOCKS) {
            double[] p = subLunar(clock, 40_000);
            BodyTransfer t = BodyTransfer.compute(SolarSystem.EARTH, SolarSystem.MOON, clock, p[0], p[1], p[2], 0, 0, 0, 0, 0);
            double half = SolarSystem.MOON.planet().halfCircumference();
            assertEquals(true, t.x() >= -half && t.x() < half, "x = " + t.x());
        }
    }

    private static void assertVec(double[] expected, double[] actual, double eps) {
        for (int i = 0; i < 3; i++) {
            assertEquals(expected[i], actual[i], eps, "componente " + i);
        }
    }
}
