package com.spacehorizons.space.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import com.spacehorizons.planet.Planets;
import com.spacehorizons.rocket.FlightMachine;
import com.spacehorizons.space.Heliocentric;
import com.spacehorizons.space.InertialFrame;
import com.spacehorizons.space.SolarSystem;

/** As pontes do estado de voo (I e H) com as dimensões: Marte como corpo de zona, o espaço aberto longe da Terra e as atitudes. */
class HeliocentricFramesTest {
    @Test
    void marsDimensionRoundTripAndGroundSpeed() {
        Random rng = new Random(9);
        double worst = 0.0;
        for (int i = 0; i < 300; i++) {
            double clock = rng.nextDouble() * 900_000;
            double rate = i % 3 == 0 ? 0.0 : 1.0;
            V3 dir = new V3(rng.nextGaussian(), rng.nextGaussian() * 0.3, rng.nextGaussian()).unit();
            V3 r = dir.times(BodyPhysics.of(SolarSystem.MARS).radius() + 100 + rng.nextDouble() * 3_000);
            V3 v = new V3(rng.nextGaussian() * 5, rng.nextGaussian() * 5, rng.nextGaussian() * 5);
            FrameBridge.Local l = FrameBridge.toDimension(SolarSystem.MARS, clock, rate, r, v);
            FlightState back = FrameBridge.fromDimension(SolarSystem.MARS, clock, rate, l, 0.0);
            worst = Math.max(worst, back.r().distanceTo(r) + back.v().distanceTo(v));
        }
        assertTrue(worst < 5e-6, "pior erro = " + worst);
    }

    @Test
    void aRocketAtRestOnMarsMovesWithTheSurface() {
        // o chão de Marte em H gira com o dia dele: ~14 b/t no equador (2π·R/24.660), além da velocidade orbital de Marte (que não entra: é relativa)
        double clock = 123_000.0;
        FrameBridge.Local rest = new FrameBridge.Local(0.0, Planets.MARS.seaLevel() + 50.0, 0.0, 0.0, 0.0, 0.0);
        FlightState s = FrameBridge.fromDimension(SolarSystem.MARS, clock, 1.0, rest, 0.0);
        double speed = s.v().length();
        double expected = 2.0 * Math.PI * (BodyPhysics.of(SolarSystem.MARS).radius() + 50.0) / Planets.spec(Planets.MARS).dayLengthTicks();
        // o dia de Marte segue a curva do sol do vanilla (de 0,67 a 1,19 vez a média), então a velocidade varia em volta da média
        assertTrue(speed > 0.6 * expected && speed < 1.4 * expected, "velocidade do chão = " + speed + " (média " + expected + ")");
        FrameBridge.Local back = FrameBridge.toDimension(SolarSystem.MARS, clock, 1.0, s.r(), s.v());
        assertEquals(0.0, Math.hypot(back.vx(), Math.hypot(back.vy(), back.vz())), 1e-9);
    }

    @Test
    void spaceDimensionRoundTripForTheSunAndMarsStates() {
        Random rng = new Random(11);
        double worst = 0.0;
        for (SolarSystem.Body body : new SolarSystem.Body[] {SolarSystem.SUN, SolarSystem.MARS}) {
            for (int i = 0; i < 200; i++) {
                double clock = rng.nextDouble() * 900_000;
                double rate = i % 4 == 0 ? 0.0 : 1.0;
                double radius = body == SolarSystem.SUN ? 1_000_000 + rng.nextDouble() * 5_000_000 : 60_000 + rng.nextDouble() * 400_000;
                V3 r = new V3(rng.nextGaussian(), 0.2 * rng.nextGaussian(), rng.nextGaussian()).unit().times(radius);
                V3 v = new V3(rng.nextGaussian() * 50, rng.nextGaussian() * 5, rng.nextGaussian() * 50);
                FrameBridge.Local l = FrameBridge.toSpaceDimension(body, clock, rate, r, v);
                FlightState back = FrameBridge.fromSpaceDimension(body, clock, rate, l, 0.0);
                worst = Math.max(worst, (back.r().distanceTo(r) + back.v().distanceTo(v)) / 1.0e3);
            }
        }
        assertTrue(worst < 1e-6, "pior erro (em milésimos de bloco) = " + worst);
    }

    @Test
    void theAbsolutePositionOfMarsCenterIsItsPoseInEarthFixed() {
        for (double clock = 0; clock < 900_000; clock += 55_555) {
            V3 e = FrameBridge.absoluteInEarthFixed(SolarSystem.MARS, clock, V3.ZERO);
            double[] pose = new double[3];
            SolarSystem.MARS.positionAt(clock, pose);
            assertEquals(0.0, e.distanceTo(V3.of(pose)), 1e-6, "clock " + clock);
            // o sol: a origem de H vista de E está na distância da órbita da Terra, na direção do sol do vanilla
            V3 sun = FrameBridge.absoluteInEarthFixed(SolarSystem.SUN, clock, V3.ZERO);
            assertEquals(Heliocentric.EARTH_ORBIT_RADIUS, sun.length(), 1e-6);
        }
    }

    @Test
    void anEarthStateAndTheSameStateRebasedToTheSunAgreeOnTheAbsolutePosition() {
        double clock = 321_000.0;
        FlightState earth = new FlightState(SolarSystem.EARTH, new V3(700_000, 0, 200_000), new V3(10, 0, 30), 0, 0);
        FlightState sun = FlightMachine.rebase(earth, SolarSystem.SUN, clock, 1.0);
        FlightState again = FlightMachine.rebase(sun, SolarSystem.EARTH, clock, 1.0);
        assertEquals(0.0, again.r().distanceTo(earth.r()), 1e-6);
        assertEquals(0.0, again.v().distanceTo(earth.v()), 1e-9);
        assertEquals(0.0, FrameBridge.absoluteInEarthFixed(SolarSystem.EARTH, clock, earth.r()).distanceTo(FrameBridge.absoluteInEarthFixed(SolarSystem.SUN, clock, sun.r())),
                1e-6);
        FlightState mars = FlightMachine.rebase(sun, SolarSystem.MARS, clock, 1.0);
        FlightState viaMars = FlightMachine.rebase(mars, SolarSystem.EARTH, clock, 1.0);
        assertEquals(0.0, viaMars.r().distanceTo(earth.r()), 1e-5);
        // Terra → Lua continua sendo só uma troca de origem
        FlightState moon = FlightMachine.rebase(earth, SolarSystem.MOON, clock, 1.0);
        assertEquals(0.0, FlightMachine.rebase(moon, SolarSystem.EARTH, clock, 1.0).r().distanceTo(earth.r()), 1e-6);
    }

    @Test
    void attitudeFollowsTheFrameChangeAndComesBack() {
        double clock = 150_000.0;
        Quaterniond q = new Quaterniond().rotateX(0.3).rotateZ(-0.8).rotateY(1.1).normalize();
        Quaterniond inH = AttitudeMath.changeFrame(SolarSystem.Frame.EARTH_SYSTEM, SolarSystem.Frame.HELIOCENTRIC, clock, q);
        Quaterniond back = AttitudeMath.changeFrame(SolarSystem.Frame.HELIOCENTRIC, SolarSystem.Frame.EARTH_SYSTEM, clock, inH);
        assertTrue(Math.abs(Math.abs(q.dot(back)) - 1.0) < 1e-12);
        // o nariz acompanha o vetor: a direção do nariz em H é a de I girada por Ry(−θ_T)
        V3 noseI = AttitudeMath.noseDirection(q);
        V3 noseH = AttitudeMath.noseDirection(inH);
        double[] expected = new double[3];
        Heliocentric.fromEarthSystem(clock, noseI.x(), noseI.y(), noseI.z(), expected);
        assertEquals(0.0, noseH.distanceTo(V3.of(expected)), 1e-12);
        // mesmo referencial: a mesma atitude; Terra e Lua dão o resultado de sempre no espaço
        assertEquals(0.0, AttitudeMath.stateToSpaceLocal(SolarSystem.EARTH, q, clock).difference(AttitudeMath.inertialToSpaceLocal(q, clock), new Quaterniond()).angle(), 1e-7);
        Quaterniond local = AttitudeMath.stateToSpaceLocal(SolarSystem.MARS, inH, clock);
        Quaterniond fromLocal = AttitudeMath.spaceLocalToState(SolarSystem.MARS, local, clock);
        assertTrue(Math.abs(Math.abs(fromLocal.dot(inH)) - 1.0) < 1e-12);
        // o estado de Marte em H vira, na dimensão de espaço, o mesmo que a atitude em I dá
        assertEquals(0.0, local.difference(AttitudeMath.inertialToSpaceLocal(q, clock), new Quaterniond()).angle(), 1e-7);
        assertTrue(InertialFrame.angle(clock) != 0.0);
    }
}
