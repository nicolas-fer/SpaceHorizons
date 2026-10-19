package com.spacehorizons.space.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceDimension;
import com.spacehorizons.space.SpaceFrame;

class FrameBridgeTest {
    private static final double[] RATES = {1.0, 0.0, 2.5};

    /** Coordenadas aleatórias válidas (latitude até 70°) na dimensão do corpo, com velocidade local qualquer. */
    private static FrameBridge.Local randomLocal(Random rng, SolarSystem.Body body) {
        double radius = body.planet().radius();
        double lon = (rng.nextDouble() * 2 - 1) * Math.PI;
        double lat = (rng.nextDouble() * 2 - 1) * Math.toRadians(70);
        double altitude = rng.nextDouble() * 4_000 + 100;
        return new FrameBridge.Local(lon * radius, body.planet().seaLevel() + altitude, lat * radius, rng.nextGaussian() * 5,
                rng.nextGaussian() * 5, rng.nextGaussian() * 5);
    }

    private static double worstError(FrameBridge.Local a, FrameBridge.Local b) {
        return Math.max(Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) + Math.abs(a.z() - b.z()),
                Math.abs(a.vx() - b.vx()) + Math.abs(a.vy() - b.vy()) + Math.abs(a.vz() - b.vz()));
    }

    // ------------------------------------------------------------------ dimensão do corpo

    @Test
    void dimensionRoundTripIsExactForEarthAndMoonWithVariedClocks() {
        Random rng = new Random(1);
        double worst = 0;
        for (SolarSystem.Body body : SolarSystem.BODIES) {
            for (int i = 0; i < 1_500; i++) {
                double clock = rng.nextDouble() * 200_000;
                double rate = RATES[i % RATES.length];
                FrameBridge.Local l0 = randomLocal(rng, body);
                FlightState state = FrameBridge.fromDimension(body, clock, rate, l0, 100);
                FrameBridge.Local l1 = FrameBridge.toDimension(body, clock, rate, state.r(), state.v());
                worst = Math.max(worst, worstError(l0, l1));
            }
        }
        assertTrue(worst < 1e-9, "pior erro = " + worst);
    }

    @Test
    void stateRoundTripThroughTheDimension() {
        Random rng = new Random(2);
        double worst = 0;
        for (SolarSystem.Body body : SolarSystem.BODIES) {
            for (int i = 0; i < 500; i++) {
                double clock = rng.nextDouble() * 200_000;
                FrameBridge.Local l = randomLocal(rng, body);
                FlightState s0 = FrameBridge.fromDimension(body, clock, l, 0);
                FrameBridge.Local mid = FrameBridge.toDimension(body, clock, s0.r(), s0.v());
                FlightState s1 = FrameBridge.fromDimension(body, clock, mid, 0);
                worst = Math.max(worst, s0.r().distanceTo(s1.r()) + s0.v().distanceTo(s1.v()));
            }
        }
        assertTrue(worst < 1e-9, "pior erro = " + worst);
    }

    @Test
    void positionMatchesTheSpaceFrameMapping() {
        double clock = 31_000;
        for (SolarSystem.Body body : SolarSystem.BODIES) {
            FrameBridge.Local l = new FrameBridge.Local(1_234, 63 + 700, -987, 0, 0, 0);
            FlightState s = FrameBridge.fromDimension(body, clock, l, 0);
            double[] abs = new double[3];
            SpaceFrame.toSpace(body, clock, l.x(), l.y(), l.z(), abs);
            double[] center = new double[3];
            body.positionAt(clock, center);
            assertEquals(0.0, s.r().distanceTo(V3.of(abs).minus(V3.of(center))), 1e-9);
            assertEquals(700.0, s.altitude(), 1e-9);
        }
    }

    @Test
    void standingOnTheGroundOfTheMoonHasTheInertialSpeedOfTheRotation() {
        // parado no chão (velocidade local 0): o estado inercial é ω × r; na Terra é zero
        double clock = 5_555;
        FrameBridge.Local still = new FrameBridge.Local(1_200, 63, 300, 0, 0, 0);
        FlightState moon = FrameBridge.fromDimension(SolarSystem.MOON, clock, still, 0);
        assertTrue(moon.v().length() > 1.0 && moon.v().length() < 7.5, "|v| = " + moon.v().length());
        FrameBridge.Local back = FrameBridge.toDimension(SolarSystem.MOON, clock, moon.r(), moon.v());
        assertEquals(0.0, Math.abs(back.vx()) + Math.abs(back.vy()) + Math.abs(back.vz()), 1e-9);
        FlightState earth = FrameBridge.fromDimension(SolarSystem.EARTH, clock, still, 0);
        assertEquals(V3.ZERO, earth.v());
        assertEquals(V3.ZERO, FrameBridge.rotationVelocity(SolarSystem.EARTH, clock, 1.0, earth.r()));
        // relógio pausado: o chão não gira
        assertEquals(V3.ZERO, FrameBridge.rotationVelocity(SolarSystem.MOON, clock, 0.0, moon.r()));
    }

    @Test
    void absoluteVelocityAgreesWithTheBodyTransferConvention() {
        // v absoluta = v local girada para o espaço + velocidade do chão (a conta do BodyTransfer)
        double clock = 77_777;
        FrameBridge.Local l = new FrameBridge.Local(-2_000, 63 + 1_500, 900, 1.0, -2.0, 0.5);
        for (SolarSystem.Body body : SolarSystem.BODIES) {
            FlightState s = FrameBridge.fromDimension(body, clock, l, 0);
            double[] v = new double[3];
            SpaceFrame.localToSpace(body, clock, l.x(), l.z(), l.vx(), l.vy(), l.vz(), v);
            double[] p = new double[3];
            SpaceFrame.toSpace(body, clock, l.x(), l.y(), l.z(), p);
            double[] ground = new double[3];
            SpaceFrame.groundVelocity(body, clock, p[0], p[1], p[2], ground);
            double[] center = new double[3];
            body.velocityAt(clock, center);
            V3 absolute = V3.of(v).plus(V3.of(ground));
            assertEquals(0.0, s.v().plus(V3.of(center)).distanceTo(absolute), 1e-9);
        }
    }

    @Test
    void coordinateVelocityIsTheRateOfChangeOfTheCoordinates() {
        double clock = 12_000;
        SolarSystem.Body body = SolarSystem.EARTH;
        for (double latDeg : new double[] {0, 40, 65}) {
            double radius = body.planet().radius();
            FrameBridge.Local l = new FrameBridge.Local(900, 63 + 3_000, Math.toRadians(latDeg) * radius, 4.0, 1.0, 2.5);
            V3 rate = l.coordinateVelocity(body);
            FlightState s = FrameBridge.fromDimension(body, clock, l, 0);
            double h = 1e-3;
            FrameBridge.Local moved = FrameBridge.toDimension(body, clock, s.r().plus(s.v().times(h)), s.v());
            assertEquals(rate.x(), (moved.x() - l.x()) / h, 5e-3 * Math.abs(rate.x()) + 1e-3);
            assertEquals(rate.y(), (moved.y() - l.y()) / h, 5e-3 * Math.abs(rate.y()) + 1e-3);
            assertEquals(rate.z(), (moved.z() - l.z()) / h, 5e-3 * Math.abs(rate.z()) + 1e-3);
        }
        // no equador e em altitude a coordenada x anda MENOS que a velocidade métrica (R/r < 1)
        FrameBridge.Local eq = new FrameBridge.Local(0, 63 + 5_000, 0, 10, 0, 0);
        assertTrue(eq.coordinateVelocity(body).x() < 10);
    }

    // ------------------------------------------------------------------ dimensão de espaço

    @Test
    void spaceDimensionMappingFollowsTheDesign() {
        // (x,y,z)_dim = (sz, sx + 20000, sy)
        double[] dim = FrameBridge.spaceToSpaceDim(new V3(1_000, 2_000, 3_000));
        assertEquals(3_000, dim[0], 0.0);
        assertEquals(21_000, dim[1], 0.0);
        assertEquals(2_000, dim[2], 0.0);
        assertEquals(new V3(1_000, 2_000, 3_000), FrameBridge.spaceDimToSpace(dim[0], dim[1], dim[2]));
        // subir sobre o spawn da Terra (+X do espaço) é subir (+Y) na dimensão
        double[] up = FrameBridge.spaceToSpaceDim(new V3(EARTH_R + 6_000, 0, 0));
        assertEquals(0.0, up[0], 0.0);
        assertEquals(EARTH_R + 26_000, up[1], 1e-9);
        assertEquals(0.0, up[2], 0.0);
    }

    private static final double EARTH_R = SolarSystem.EARTH.planet().radius();

    @Test
    void spaceDimensionMappingMatchesSpaceDimension() {
        Random rng = new Random(3);
        for (int i = 0; i < 100; i++) {
            V3 abs = new V3(rng.nextGaussian() * 40_000, rng.nextGaussian() * 40_000, rng.nextGaussian() * 40_000);
            double[] viaBridge = FrameBridge.spaceToSpaceDim(abs);
            double[] viaClass = new double[3];
            SpaceDimension.fromSpace(abs.x(), abs.y(), abs.z(), viaClass);
            assertEquals(viaClass[0], viaBridge[0], 0.0);
            assertEquals(viaClass[1], viaBridge[1], 0.0);
            assertEquals(viaClass[2], viaBridge[2], 0.0);
            V3 back = FrameBridge.spaceDimToSpace(viaBridge[0], viaBridge[1], viaBridge[2]);
            assertEquals(0.0, back.distanceTo(abs), 1e-9);
        }
    }

    @Test
    void spaceDimensionVectorsUseTheLocalToSpaceRotationWithoutBias() {
        double[] m = SpaceDimension.LOCAL_TO_SPACE;
        V3 local = new V3(2, -3, 5);
        V3 viaMatrix = new V3(m[0] * 2 + m[1] * -3 + m[2] * 5, m[3] * 2 + m[4] * -3 + m[5] * 5, m[6] * 2 + m[7] * -3 + m[8] * 5);
        assertEquals(viaMatrix, FrameBridge.spaceDimVectorToSpace(local.x(), local.y(), local.z()));
        assertEquals(local, FrameBridge.spaceVectorToSpaceDim(FrameBridge.spaceDimVectorToSpace(2, -3, 5)));
    }

    @Test
    void spaceDimensionRoundTripForEarthAndMoon() {
        Random rng = new Random(4);
        double worst = 0;
        for (SolarSystem.Body body : SolarSystem.BODIES) {
            for (int i = 0; i < 300; i++) {
                double clock = rng.nextDouble() * 200_000;
                double rate = RATES[i % RATES.length];
                V3 dir = new V3(rng.nextGaussian(), rng.nextGaussian(), rng.nextGaussian()).unit();
                V3 r = dir.times(body.planet().radius() + 1_000 + rng.nextDouble() * 5_000);
                V3 v = new V3(rng.nextGaussian() * 10, rng.nextGaussian() * 10, rng.nextGaussian() * 10);
                FrameBridge.Local l = FrameBridge.toSpaceDimension(body, clock, rate, r, v);
                FlightState back = FrameBridge.fromSpaceDimension(body, clock, rate, l, 0);
                worst = Math.max(worst, back.r().distanceTo(r) + back.v().distanceTo(v));
            }
        }
        assertTrue(worst < 1e-9, "pior erro = " + worst);
    }

    @Test
    void aMoonStateInTheSpaceDimensionUsesAbsolutePositionAndVelocity() {
        double clock = 6_000;
        V3 r = new V3(0, 3_000, 0);
        V3 v = new V3(0, 0, 4);
        FrameBridge.Local l = FrameBridge.toSpaceDimension(SolarSystem.MOON, clock, 1.0, r, v);
        double[] center = new double[3];
        SolarSystem.MOON.positionAt(clock, center);
        double[] cv = new double[3];
        SolarSystem.MOON.velocityAt(clock, cv);
        V3 abs = FrameBridge.spaceDimToSpace(l.x(), l.y(), l.z());
        assertEquals(0.0, abs.distanceTo(V3.of(center).plus(r)), 1e-9);
        V3 vAbs = FrameBridge.spaceDimVectorToSpace(l.vx(), l.vy(), l.vz());
        assertEquals(0.0, vAbs.distanceTo(V3.of(cv).plus(v)), 1e-12);
        // a Lua anda ~14 b/t: na Terra (parada) a velocidade absoluta é a relativa
        FrameBridge.Local earth = FrameBridge.toSpaceDimension(SolarSystem.EARTH, clock, 1.0, r, v);
        assertEquals(0.0, FrameBridge.spaceDimVectorToSpace(earth.vx(), earth.vy(), earth.vz()).distanceTo(v), 1e-15);
    }

    @Test
    void planetDimensionToSpaceDimensionKeepsTheAbsolutePosition() {
        // sobe da dimensão da Terra para a de espaço: a posição absoluta é a mesma nas duas leituras
        double clock = 20_000;
        FrameBridge.Local ground = new FrameBridge.Local(1_000, 63 + 5_100, 800, 3, 20, 1);
        FlightState s = FrameBridge.fromDimension(SolarSystem.EARTH, clock, ground, 500);
        FrameBridge.Local space = FrameBridge.toSpaceDimension(SolarSystem.EARTH, clock, 1.0, s.r(), s.v());
        double[] abs = new double[3];
        SpaceFrame.toSpace(SolarSystem.EARTH, clock, ground.x(), ground.y(), ground.z(), abs);
        double[] expected = new double[3];
        SpaceDimension.fromSpace(abs[0], abs[1], abs[2], expected);
        assertEquals(expected[0], space.x(), 1e-9);
        assertEquals(expected[1], space.y(), 1e-9);
        assertEquals(expected[2], space.z(), 1e-9);
        // e o corpo dominante certo para esse ponto é a Terra
        FlightState reentry = FrameBridge.fromSpaceDimension(SoiTracker.dominantAt(V3.of(abs), clock), clock, 1.0, space, 500);
        assertEquals(0.0, reentry.r().distanceTo(s.r()), 1e-9);
        assertEquals(0.0, reentry.v().distanceTo(s.v()), 1e-12);
        assertEquals(500, reentry.fuelMb(), 0.0);
    }
}
