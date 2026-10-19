package com.spacehorizons.space.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceFrame;

/** Voos completos do piloto automático (FlightGuide + FlightPlan), com a Lua em órbita. */
class FlightPlanTest {
    private static final int MAX_TICKS = 20_000;
    private static final double TARGET_ALTITUDE = 300;
    private static final double[] START_CLOCKS = {0, 4_000, 18_000, 30_000, 66_666, 100_000, 150_000};

    private record Flight(boolean arrived, int ticks, List<SolarSystem.Body> visited, double peakSpeed, double finalSpeed,
            double[] finalPosition, double[] target, double[] relativeSpeedAtTransfer, double minClearance) {
    }

    private static double[] facingTarget(SolarSystem.Body body, double altitude) {
        double[] facing = new double[2];
        TravelMath.facingPoint(body, facing);
        return new double[] {facing[0], body.planet().seaLevel() + altitude, facing[1]};
    }

    private static Flight fly(SolarSystem.Body from, double[] start, double clock0, SolarSystem.Body to, double[] target) {
        FlightGuide guide = new FlightGuide(from, start[0], start[1], start[2], clock0, to, target[0], target[1], target[2], b -> true);
        List<SolarSystem.Body> visited = new ArrayList<>();
        visited.add(from);
        double peak = 0;
        double lastSpeed = 0;
        double[] last = start;
        double transferSpeed = Double.NaN;
        double minClearance = Double.MAX_VALUE;
        double clock = clock0;
        boolean arrived = false;
        int ticks = 0;
        double[] space = new double[3];
        double[] center = new double[3];
        while (!arrived && ticks < MAX_TICKS) {
            FlightGuide.Step step = guide.tick(clock, 0, 0);
            ticks++;
            clock += 1;
            BodyTransfer s = step.state();
            double speed = Math.sqrt(s.vx() * s.vx() + s.vy() * s.vy() + s.vz() * s.vz());
            if (step.transferred()) {
                visited.add(s.target());
                transferSpeed = speed;
            } else {
                peak = Math.max(peak, speed);
                lastSpeed = speed;
            }
            // nunca atravessa a superfície de um corpo (distância medida no espaço, no mesmo instante)
            SpaceFrame.toSpace(s.target(), clock - 1, s.x(), s.y(), s.z(), space);
            for (SolarSystem.Body body : SolarSystem.BODIES) {
                body.positionAt(clock - 1, center);
                double d = Math.sqrt(Math.pow(space[0] - center[0], 2) + Math.pow(space[1] - center[1], 2) + Math.pow(space[2] - center[2], 2));
                minClearance = Math.min(minClearance, d - body.planet().radius());
            }
            last = new double[] {s.x(), s.y(), s.z()};
            arrived = step.arrived();
        }
        return new Flight(arrived, ticks, visited, peak, lastSpeed, last, target, new double[] {transferSpeed}, minClearance);
    }

    private static double[] earthStart(double altitude) {
        return new double[] {0.5, SolarSystem.EARTH.planet().seaLevel() + altitude, 0.5};
    }

    @Test
    void spawnToMoonFollowsTheMovingMoonAndArrivesAtTheTarget() {
        double[] target = facingTarget(SolarSystem.MOON, TARGET_ALTITUDE);
        for (double clock : START_CLOCKS) {
            Flight f = fly(SolarSystem.EARTH, earthStart(2), clock, SolarSystem.MOON, target);
            assertTrue(f.arrived(), "não chegou no relógio " + clock);
            assertTrue(f.ticks() < 11_000, "ticks " + f.ticks() + " no relógio " + clock);
            // uma única troca Terra -> Lua
            assertEquals(List.of(SolarSystem.EARTH, SolarSystem.MOON), f.visited(), "relógio " + clock);
            for (int i = 0; i < 3; i++) {
                assertEquals(target[i], f.finalPosition()[i], 1e-6, "eixo " + i + " no relógio " + clock);
            }
            assertTrue(f.minClearance() > -1, "atravessou um corpo no relógio " + clock + ": " + f.minClearance());
            // velocidade relativa ao chão nunca passa do teto fora das trocas
            assertTrue(f.peakSpeed() <= FlightPlan.MAX_SPEED + 1e-9, "pico " + f.peakSpeed());
            // frenagem: a última velocidade é baixa
            assertTrue(f.finalSpeed() < 5.0, "velocidade na chegada " + f.finalSpeed());
        }
    }

    @Test
    void theTransferToTheMoonDoesNotCarryTheOrbitalVelocity() {
        // a velocidade com que o jogador chega à Lua é relativa ao chão dela: a de cruzeiro (no referencial preso à Terra) combinada
        // com a da Lua nesse referencial (66 a 149 blocos/tick, porque a Terra gira); nunca mais que isso
        double[] target = facingTarget(SolarSystem.MOON, TARGET_ALTITUDE);
        for (double clock : START_CLOCKS) {
            Flight f = fly(SolarSystem.EARTH, earthStart(2), clock, SolarSystem.MOON, target);
            double speed = f.relativeSpeedAtTransfer()[0];
            assertTrue(speed <= Math.hypot(FlightPlan.MAX_SPEED, 150.0) + 1.0, "velocidade relativa na troca " + speed + " no relógio " + clock);
        }
    }

    @Test
    void moonToEarthComesBackToTheSpawnZenith() {
        double[] target = facingTarget(SolarSystem.EARTH, TARGET_ALTITUDE);
        double[] start = facingTarget(SolarSystem.MOON, TARGET_ALTITUDE);
        for (double clock : START_CLOCKS) {
            Flight f = fly(SolarSystem.MOON, start, clock, SolarSystem.EARTH, target);
            assertTrue(f.arrived(), "não chegou no relógio " + clock);
            assertTrue(f.ticks() < 11_000, "ticks " + f.ticks() + " no relógio " + clock);
            assertEquals(List.of(SolarSystem.MOON, SolarSystem.EARTH), f.visited(), "relógio " + clock);
            assertTrue(f.minClearance() > -1, "atravessou um corpo no relógio " + clock + ": " + f.minClearance());
            for (int i = 0; i < 3; i++) {
                assertEquals(target[i], f.finalPosition()[i], 1e-6, "eixo " + i);
            }
        }
    }

    @Test
    void farSideStartDetoursAroundTheEarth() {
        // saindo do lado da Terra oposto à Lua: a reta até ela atravessaria o planeta
        double[] target = facingTarget(SolarSystem.MOON, TARGET_ALTITUDE);
        for (double clock : new double[] {0, 18_000, 70_000}) {
            double[] m = new double[3];
            SolarSystem.MOON.positionAt(clock, m);
            double r = SolarSystem.EARTH.planet().radius() + 17;
            double[] start = new double[3];
            SpaceFrame.fromSpace(SolarSystem.EARTH, clock, -m[0] / SolarSystem.EARTH_MOON_DISTANCE * r,
                    -m[1] / SolarSystem.EARTH_MOON_DISTANCE * r, -m[2] / SolarSystem.EARTH_MOON_DISTANCE * r, start);
            FlightGuide guide = new FlightGuide(SolarSystem.EARTH, start[0], start[1], start[2], clock, SolarSystem.MOON, target[0],
                    target[1], target[2], b -> true);
            assertTrue(guide.plan().waypoints().size() > 1,
                    "esperava subida e desvio, vieram " + guide.plan().waypoints().size() + " ponto(s)");
            Flight f = fly(SolarSystem.EARTH, start, clock, SolarSystem.MOON, target);
            assertTrue(f.arrived(), "não chegou no relógio " + clock);
            assertTrue(f.minClearance() > -1, "atravessou um corpo: " + f.minClearance());
        }
    }

    @Test
    void sameBodyTargetIsStaticAndArrivesExactly() {
        double[] target = {0.5, 63 + TARGET_ALTITUDE, 0.5};
        FlightGuide guide = new FlightGuide(SolarSystem.EARTH, 2000, 70, 900, 5_000, SolarSystem.EARTH, target[0], target[1], target[2],
                b -> true);
        assertTrue(guide.plan().targetIsStatic());
        int ticks = 0;
        FlightGuide.Step step;
        do {
            step = guide.tick(5_000 + ticks, 0, 0);
            ticks++;
        } while (!step.arrived() && ticks < MAX_TICKS);
        assertTrue(step.arrived());
        assertEquals(0.5, step.state().x(), 1e-6);
        assertEquals(63 + TARGET_ALTITUDE, step.state().y(), 1e-6);
        assertEquals(0.5, step.state().z(), 1e-6);
        assertEquals(0.0, step.state().vx() + step.state().vy() + step.state().vz(), 1e-12);
    }

    @Test
    void speedGrowsWithAltitudeAndBrakesNearTheTarget() {
        double[] target = facingTarget(SolarSystem.MOON, TARGET_ALTITUDE);
        Flight f = fly(SolarSystem.EARTH, earthStart(2), 18_000, SolarSystem.MOON, target);
        assertEquals(FlightPlan.MAX_SPEED, f.peakSpeed(), 1e-9);
        assertTrue(f.finalSpeed() < 5.0, "velocidade na chegada " + f.finalSpeed());
    }

    @Test
    void accelerationIsLimitedSoThereAreNoJerks() {
        double[] target = facingTarget(SolarSystem.MOON, TARGET_ALTITUDE);
        FlightGuide guide = new FlightGuide(SolarSystem.EARTH, 0.5, 65, 0.5, 18_000, SolarSystem.MOON, target[0], target[1], target[2],
                b -> true);
        double previous = 0;
        double clock = 18_000;
        for (int i = 0; i < 6_000; i++) {
            FlightGuide.Step step = guide.tick(clock++, 0, 0);
            if (step.transferred()) {
                previous = guide.plan().speed();
                continue;
            }
            double speed = guide.plan().speed();
            assertTrue(speed - previous <= previous * (FlightPlan.ACCELERATION_FACTOR - 1) + FlightPlan.ACCELERATION_ADD + 1e-9,
                    "salto de velocidade " + previous + " -> " + speed);
            previous = speed;
            if (step.arrived()) {
                break;
            }
        }
    }

    @Test
    void notTransferringToAMissingDimensionKeepsFlyingInTheCurrentBody() {
        double[] target = facingTarget(SolarSystem.MOON, TARGET_ALTITUDE);
        FlightGuide guide = new FlightGuide(SolarSystem.EARTH, 0.5, 65, 0.5, 0, SolarSystem.MOON, target[0], target[1], target[2],
                b -> b != SolarSystem.MOON);
        double clock = 0;
        for (int i = 0; i < 3_000; i++) {
            FlightGuide.Step step = guide.tick(clock++, 0, 0);
            assertTrue(!step.transferred());
            assertEquals(SolarSystem.EARTH, step.state().target());
        }
    }
}
