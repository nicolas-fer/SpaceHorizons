package com.spacehorizons.space.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceDimension;
import com.spacehorizons.space.SpaceFrame;

/**
 * A travessia entre a dimensão de um corpo e a de espaço aberto: mesma posição, velocidade e olhar no espaço, ida e volta
 * sem perdas, e nenhuma diferença entre ir de um corpo a outro direto ou passando pelo espaço.
 */
class SpaceTransferTest {
    private static final double EPS = 1e-6;
    private static final double[] CLOCKS = {0, 6_000, 18_000, 70_000.5, 123_456};

    /** {x, y, z, vx, vy, vz, yaw, pitch} do jogador no corpo (pontos quaisquer, até acima da altitude de saída). */
    private static final double[][] CASES = {
            {0.5, 5_063, 0.5, 0, 2, 0, 0, -90},
            {0.5, 5_063, 0.5, 0, 2, 0, 40, -60},
            {350, 4_900, -120, 0.3, 1.5, -0.2, -135, -30},
            {-2_000, 1_500, 900, 0, -1, 0.5, 90, 10},
            {5_000, 6_000, 4_000, 0.1, 0.1, 0.1, 12, 45},
    };

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

    /** Vetor da dimensão de espaço (eixos dela) → eixos do espaço. */
    private static double[] spaceDimToSpace(double vx, double vy, double vz) {
        double[] m = SpaceDimension.LOCAL_TO_SPACE;
        return new double[] {m[0] * vx + m[1] * vy + m[2] * vz, m[3] * vx + m[4] * vy + m[5] * vz, m[6] * vx + m[7] * vy + m[8] * vz};
    }

    private static void assertVec(double[] expected, double[] actual, double eps) {
        for (int i = 0; i < 3; i++) {
            assertEquals(expected[i], actual[i], eps, "componente " + i);
        }
    }

    private static BodyTransfer toSpace(SolarSystem.Body from, double clock, double[] c) {
        return BodyTransfer.toSpace(from, clock, c[0], c[1], c[2], c[3], c[4], c[5], (float) c[6], (float) c[7]);
    }

    @Test
    void theTargetIsNullInSpaceAndTheBodyOtherwise() {
        BodyTransfer toSpace = toSpace(SolarSystem.EARTH, 0, CASES[0]);
        assertNull(toSpace.target());
        assertTrue(toSpace.isSpace());
        BodyTransfer back = BodyTransfer.fromSpace(SolarSystem.MOON, 0, toSpace.x(), toSpace.y(), toSpace.z(), 0, 0, 0, 0, 0);
        assertEquals(SolarSystem.MOON, back.target());
        assertFalse(back.isSpace());
    }

    @Test
    void thePositionInSpaceIsTheSameOnBothSides() {
        for (SolarSystem.Body from : SolarSystem.BODIES) {
            for (double clock : CLOCKS) {
                for (double[] c : CASES) {
                    BodyTransfer t = toSpace(from, clock, c);
                    double[] before = new double[3];
                    double[] after = new double[3];
                    SpaceFrame.toSpace(from, clock, c[0], c[1], c[2], before);
                    SpaceDimension.toSpace(t.x(), t.y(), t.z(), after);
                    assertVec(before, after, 1e-5);
                }
            }
        }
    }

    @Test
    void theVelocityInSpaceIsConservedIncludingTheMovingGround() {
        for (SolarSystem.Body from : SolarSystem.BODIES) {
            for (double clock : CLOCKS) {
                for (double[] c : CASES) {
                    BodyTransfer t = toSpace(from, clock, c);
                    double[] before = spaceVelocity(from, clock, c[0], c[1], c[2], c[3], c[4], c[5]);
                    double[] after = spaceDimToSpace(t.vx(), t.vy(), t.vz());
                    assertVec(before, after, EPS);
                }
            }
        }
    }

    @Test
    void theLookDirectionIsTheSameVectorInSpace() {
        for (SolarSystem.Body from : SolarSystem.BODIES) {
            for (double clock : CLOCKS) {
                for (double[] c : CASES) {
                    BodyTransfer t = toSpace(from, clock, c);
                    double[] look = new double[3];
                    TravelMath.lookVector((float) c[6], (float) c[7], look);
                    double[] before = new double[3];
                    SpaceFrame.localToSpace(from, clock, c[0], c[2], look[0], look[1], look[2], before);
                    TravelMath.lookVector(t.yRot(), t.xRot(), look);
                    double[] after = spaceDimToSpace(look[0], look[1], look[2]);
                    assertVec(before, after, 1e-3);
                }
            }
        }
    }

    @Test
    void aShipRisingOverTheEarthSpawnKeepsItsHeadingAndVelocityAxes() {
        // os eixos da dimensão de espaço são os do spawn da Terra: sobre lon 0, lat 0 só a altura e o viés mudam
        for (double clock : CLOCKS) {
            BodyTransfer t = BodyTransfer.toSpace(SolarSystem.EARTH, clock, 0, 5_063, 0, 1, 2, 3, 30f, 10f);
            assertEquals(0, t.x(), 1e-9);
            assertEquals(0, t.z(), 1e-9);
            assertEquals(SolarSystem.EARTH.planet().radius() + 5_000 + SpaceDimension.Y_BIAS, t.y(), 1e-6);
            assertEquals(1, t.vx(), EPS);
            assertEquals(2, t.vy(), EPS);
            assertEquals(3, t.vz(), EPS);
            assertEquals(30f, t.yRot(), 1e-3);
            assertEquals(10f, t.xRot(), 1e-3);
        }
    }

    @Test
    void roundTripBodySpaceBodyRestoresEverything() {
        for (SolarSystem.Body body : SolarSystem.BODIES) {
            for (double clock : CLOCKS) {
                for (double[] c : CASES) {
                    BodyTransfer there = toSpace(body, clock, c);
                    BodyTransfer back = BodyTransfer.fromSpace(body, clock, there.x(), there.y(), there.z(), there.vx(), there.vy(),
                            there.vz(), there.yRot(), there.xRot());
                    assertEquals(body, back.target());
                    assertEquals(c[0], TravelMath.normalizeX(body, back.x()), 1e-4);
                    assertEquals(c[1], back.y(), 1e-4);
                    assertEquals(c[2], back.z(), 1e-4);
                    assertEquals(c[3], back.vx(), 1e-5);
                    assertEquals(c[4], back.vy(), 1e-5);
                    assertEquals(c[5], back.vz(), 1e-5);
                    assertEquals(c[7], back.xRot(), 0.1);
                    if (Math.abs(c[7]) < 89) {
                        assertEquals(0, Math.IEEEremainder(c[6] - back.yRot(), 360), 1e-3);
                    }
                }
            }
        }
    }

    @Test
    void passingThroughSpaceIsTheSameAsGoingStraightToTheOtherBody() {
        for (double clock : CLOCKS) {
            for (double[] c : CASES) {
                BodyTransfer direct = BodyTransfer.compute(SolarSystem.EARTH, SolarSystem.MOON, clock, c[0], c[1], c[2], c[3], c[4],
                        c[5], (float) c[6], (float) c[7]);
                BodyTransfer inSpace = toSpace(SolarSystem.EARTH, clock, c);
                BodyTransfer viaSpace = BodyTransfer.fromSpace(SolarSystem.MOON, clock, inSpace.x(), inSpace.y(), inSpace.z(),
                        inSpace.vx(), inSpace.vy(), inSpace.vz(), inSpace.yRot(), inSpace.xRot());
                assertEquals(direct.x(), viaSpace.x(), 1e-4);
                assertEquals(direct.y(), viaSpace.y(), 1e-4);
                assertEquals(direct.z(), viaSpace.z(), 1e-4);
                assertEquals(direct.vx(), viaSpace.vx(), 1e-5);
                assertEquals(direct.vy(), viaSpace.vy(), 1e-5);
                assertEquals(direct.vz(), viaSpace.vz(), 1e-5);
                assertEquals(direct.xRot(), viaSpace.xRot(), 0.1);
            }
        }
    }

    @Test
    void leavingTheSpaceKeepsXInsideThePlanetRange() {
        // sobre a costura (x = -C/2): o ponto do espaço pode voltar como +C/2, que a normalização leva para dentro de [-C/2, C/2)
        for (SolarSystem.Body body : SolarSystem.BODIES) {
            double half = body.planet().halfCircumference();
            for (double clock : CLOCKS) {
                for (double seamX : new double[] {-half, half - 1e-9, -half + 1e-9}) {
                    double[] space = new double[3];
                    SpaceFrame.toSpace(body, clock, seamX, body.planet().seaLevel() + 800, 40, space);
                    double[] dim = new double[3];
                    SpaceDimension.fromSpace(space[0], space[1], space[2], dim);
                    BodyTransfer t = BodyTransfer.fromSpace(body, clock, dim[0], dim[1], dim[2], 0, 0, 0, 0, 0);
                    assertTrue(t.x() >= -half && t.x() < half, "x = " + t.x());
                }
            }
        }
    }
}
