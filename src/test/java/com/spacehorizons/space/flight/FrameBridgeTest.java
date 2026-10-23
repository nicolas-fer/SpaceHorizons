package com.spacehorizons.space.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.InertialFrame;
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
            // o estado é INERCIAL: a posição é a do mapeamento esférico levada a I pela pose inercial do corpo
            double[] abs = new double[3];
            SpaceFrame.toSpace(body, body.inertialPoseAt(clock), l.x(), l.y(), l.z(), abs);
            double[] center = new double[3];
            body.inertialPositionAt(clock, center);
            assertEquals(0.0, s.r().distanceTo(V3.of(abs).minus(V3.of(center))), 1e-9);
            assertEquals(700.0, s.altitude(), 1e-9);
            // e em E (preso à Terra) o mesmo ponto absoluto é o do SpaceFrame com a pose em E
            double[] inE = new double[3];
            SpaceFrame.toSpace(body, clock, l.x(), l.y(), l.z(), inE);
            assertEquals(0.0, FrameBridge.absoluteInEarthFixed(body, clock, s.r()).distanceTo(V3.of(inE)), 1e-6);
        }
    }

    @Test
    void standingOnTheGroundOfTheMoonHasTheInertialSpeedOfTheRotation() {
        // parado no chão (velocidade local 0): o estado inercial é ω × r (a rotação da Terra, ou a sincronizada da Lua)
        double clock = 5_555;
        FrameBridge.Local still = new FrameBridge.Local(1_200, 63, 300, 0, 0, 0);
        FlightState moon = FrameBridge.fromDimension(SolarSystem.MOON, clock, still, 0);
        assertEquals(SolarSystem.MOON_MEAN_MOTION * SolarSystem.MOON.planet().radius(), moon.v().length(), 0.02);
        FrameBridge.Local back = FrameBridge.toDimension(SolarSystem.MOON, clock, moon.r(), moon.v());
        assertEquals(0.0, Math.abs(back.vx()) + Math.abs(back.vy()) + Math.abs(back.vz()), 1e-9);
        // na Terra é a rotação diurna: w(t) vezes a distância ao eixo polar (~18 a ~33 b/t no equador, 27,3 em média), perpendicular ao eixo
        FlightState earth = FrameBridge.fromDimension(SolarSystem.EARTH, clock, still, 0);
        double axisDistance = Math.hypot(earth.r().x(), earth.r().z());
        assertEquals(InertialFrame.rate(clock) * axisDistance, earth.v().length(), 2e-3);
        assertEquals(0.0, earth.v().y(), 1e-9);
        assertEquals(0.0, earth.v().dot(earth.r()) / (earth.v().length() * earth.r().length()), 1e-8);
        assertEquals(0.0, earth.v().distanceTo(FrameBridge.rotationVelocity(SolarSystem.EARTH, clock, 1.0, earth.r())), 1e-9);
        FrameBridge.Local earthBack = FrameBridge.toDimension(SolarSystem.EARTH, clock, earth.r(), earth.v());
        assertEquals(0.0, Math.abs(earthBack.vx()) + Math.abs(earthBack.vy()) + Math.abs(earthBack.vz()), 1e-9);
        // relógio pausado: o chão não gira
        assertEquals(V3.ZERO, FrameBridge.rotationVelocity(SolarSystem.MOON, clock, 0.0, moon.r()));
    }

    @Test
    void absoluteVelocityAgreesWithTheBodyTransferConvention() {
        // v absoluta (em I) = v local girada para I + velocidade inercial do chão (a conta do BodyTransfer, em I)
        double clock = 77_777;
        FrameBridge.Local l = new FrameBridge.Local(-2_000, 63 + 1_500, 900, 1.0, -2.0, 0.5);
        for (SolarSystem.Body body : SolarSystem.BODIES) {
            FlightState s = FrameBridge.fromDimension(body, clock, l, 0);
            double[] v = new double[3];
            SolarSystem.Pose pose = body.inertialPoseAt(clock);
            SpaceFrame.localToSpace(body, pose, l.x(), l.z(), l.vx(), l.vy(), l.vz(), v);
            double[] p = new double[3];
            SpaceFrame.toSpace(body, pose, l.x(), l.y(), l.z(), p);
            double[] ground = new double[3];
            SpaceFrame.inertialGroundVelocity(body, clock, p[0], p[1], p[2], ground);
            double[] center = new double[3];
            body.inertialVelocityAt(clock, center);
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
            // um tick h depois o chão também girou: a variação das coordenadas é a do movimento RELATIVO a ele
            FrameBridge.Local moved = FrameBridge.toDimension(body, clock + h, s.r().plus(s.v().times(h)), s.v());
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
                // Marte fica a milhões de blocos da Terra (a posição em E tem 7 algarismos antes da vírgula): o erro de arredondamento é maior
                double error = back.r().distanceTo(r) + back.v().distanceTo(v);
                worst = Math.max(worst, body == SolarSystem.MARS ? error / 100.0 : error);
            }
        }
        assertTrue(worst < 1e-9, "pior erro = " + worst);
    }

    @Test
    void aMoonStateInTheSpaceDimensionUsesAbsolutePositionAndVelocity() {
        // o estado é inercial (relativo à Lua, em I); a dimensão de espaço guarda posição e velocidade ABSOLUTAS em E
        double clock = 6_000;
        V3 r = new V3(0, 3_000, 0);
        V3 v = new V3(0, 0, 4);
        FrameBridge.Local l = FrameBridge.toSpaceDimension(SolarSystem.MOON, clock, 1.0, r, v);
        double[] center = new double[3];
        SolarSystem.MOON.inertialPositionAt(clock, center);
        double[] cv = new double[3];
        SolarSystem.MOON.inertialVelocityAt(clock, cv);
        V3 absI = V3.of(center).plus(r);
        double[] absE = new double[3];
        InertialFrame.toEarthFixed(clock, absI.x(), absI.y(), absI.z(), absE);
        V3 abs = FrameBridge.spaceDimToSpace(l.x(), l.y(), l.z());
        assertEquals(0.0, abs.distanceTo(V3.of(absE)), 1e-9);
        // a posição E da Lua é a de SolarSystem.positionAt
        double[] moonE = new double[3];
        SolarSystem.MOON.positionAt(clock, moonE);
        assertEquals(0.0, FrameBridge.absoluteInEarthFixed(SolarSystem.MOON, clock, V3.ZERO).distanceTo(V3.of(moonE)), 1e-6);
        // a velocidade em E é a derivada da posição em E de um ponto que anda a v + a da Lua em I
        V3 vAbsI = V3.of(cv).plus(v);
        double h = 0.01;
        double[] a = new double[3];
        double[] b = new double[3];
        InertialFrame.toEarthFixed(clock - h, absI.x() - vAbsI.x() * h, absI.y() - vAbsI.y() * h, absI.z() - vAbsI.z() * h, a);
        InertialFrame.toEarthFixed(clock + h, absI.x() + vAbsI.x() * h, absI.y() + vAbsI.y() * h, absI.z() + vAbsI.z() * h, b);
        V3 vAbs = FrameBridge.spaceDimVectorToSpace(l.vx(), l.vy(), l.vz());
        assertEquals(0.0, vAbs.distanceTo(new V3((b[0] - a[0]) / (2 * h), (b[1] - a[1]) / (2 * h), (b[2] - a[2]) / (2 * h))), 5e-3);
    }

    @Test
    void aRocketAtRestOnTheGroundHasZeroVelocityInTheEarthFixedSpaceDimension() {
        // o chão está parado em E: a dimensão de espaço (E) vê um foguete parado na pista com velocidade 0, mesmo que em I ele ande ~27 b/t
        for (double clock : new double[] {0, 6_000, 18_000, 33_333}) {
            FrameBridge.Local pad = new FrameBridge.Local(500, 63 + 100, 200, 0, 0, 0);
            FlightState s = FrameBridge.fromDimension(SolarSystem.EARTH, clock, pad, 0);
            assertTrue(s.v().length() > 15.0, "em I ele anda com a Terra: " + s.v().length());
            FrameBridge.Local space = FrameBridge.toSpaceDimension(SolarSystem.EARTH, clock, 1.0, s.r(), s.v());
            assertEquals(0.0, Math.abs(space.vx()) + Math.abs(space.vy()) + Math.abs(space.vz()), 5e-3, "clock " + clock);
        }
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
        double[] absI = new double[3];
        InertialFrame.toInertial(clock, abs[0], abs[1], abs[2], absI);
        FlightState reentry = FrameBridge.fromSpaceDimension(SoiTracker.dominantAt(V3.of(absI), clock), clock, 1.0, space, 500);
        assertEquals(0.0, reentry.r().distanceTo(s.r()), 1e-9);
        assertEquals(0.0, reentry.v().distanceTo(s.v()), 1e-12);
        assertEquals(500, reentry.fuelMb(), 0.0);
    }

    // ------------------------------------------------------------------ rotação da Terra em I

    @Test
    void aRocketOnThePadAtTheEquatorMovesTwentySevenPointThreeBlocksPerTickInTheInertialFrameOnAverage() {
        // lon 0, lat 0, ao nível do mar: a velocidade inercial é ω(t)·R; a média do dia é 2π·R/24.000 = 27,31 b/t, e ela varia de ~18 a ~33
        double radius = SolarSystem.EARTH.planet().radius();
        FrameBridge.Local pad = new FrameBridge.Local(0, SolarSystem.EARTH.planet().seaLevel(), 0, 0, 0, 0);
        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = 0;
        int n = 24_000;
        for (int t = 0; t < n; t += 10) {
            double clock = 18_000 + t;
            FlightState s = FrameBridge.fromDimension(SolarSystem.EARTH, clock, pad, 0);
            double speed = s.v().length();
            assertEquals(InertialFrame.rate(clock) * radius, speed, 2e-3, "clock " + clock);
            sum += speed;
            min = Math.min(min, speed);
            max = Math.max(max, speed);
        }
        assertEquals(2.0 * Math.PI * radius / 24_000.0, sum / (n / 10), 0.05);
        assertEquals(27.3, sum / (n / 10), 0.1);
        assertEquals(18.2, min, 0.3);
        assertEquals(32.5, max, 0.5);
    }

    @Test
    void theGroundSpeedInTheInertialFrameFallsWithTheCosineOfTheLatitude() {
        double radius = SolarSystem.EARTH.planet().radius();
        double clock = 12_000;
        double equator = FrameBridge.fromDimension(SolarSystem.EARTH, clock,
                new FrameBridge.Local(0, 63, 0, 0, 0, 0), 0).v().length();
        for (double latDeg : new double[] {30, 60, 80}) {
            FrameBridge.Local at = new FrameBridge.Local(0, 63, Math.toRadians(latDeg) * radius, 0, 0, 0);
            double speed = FrameBridge.fromDimension(SolarSystem.EARTH, clock, at, 0).v().length();
            assertEquals(equator * Math.cos(Math.toRadians(latDeg)), speed, 2e-3, "lat " + latDeg);
        }
    }

    @Test
    void launchingEastIsCheaperThanLaunchingWestByTwiceTheGroundSpeed() {
        // o custo da órbita circular baixa a partir da pista: vc − v_chão para o leste, vc + v_chão para o oeste
        double clock = 18_000;
        BodyPhysics earth = BodyPhysics.of(SolarSystem.EARTH);
        double leo = earth.radius() + 6_000;
        double vc = earth.circularSpeed(leo);
        FlightState pad = FrameBridge.fromDimension(SolarSystem.EARTH, clock, new FrameBridge.Local(0, 63, 0, 0, 0, 0), 0);
        double ground = pad.v().length();
        // para o leste (a direção da velocidade do chão) e para o oeste (a oposta)
        double east = vc - ground;
        double west = vc + ground;
        assertEquals(2.0 * ground, west - east, 1e-9);
        assertTrue(ground > 18 && ground < 33, "v do chão " + ground);
        // a velocidade relativa ao chão da órbita para leste é a orbital menos a do chão
        FlightState orbit = new FlightState(SolarSystem.EARTH, pad.r().unit().times(leo), pad.v().unit().times(vc), 0, 0);
        V3 relative = orbit.v().minus(FrameBridge.rotationVelocity(SolarSystem.EARTH, clock, 1.0, orbit.r()));
        assertTrue(relative.length() < vc, "relativa ao chão " + relative.length());
        FlightState westOrbit = new FlightState(SolarSystem.EARTH, pad.r().unit().times(leo), pad.v().unit().times(-vc), 0, 0);
        V3 westRelative = westOrbit.v().minus(FrameBridge.rotationVelocity(SolarSystem.EARTH, clock, 1.0, westOrbit.r()));
        assertTrue(westRelative.length() > vc, "relativa ao chão, para o oeste " + westRelative.length());
    }
}
