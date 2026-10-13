package com.spacehorizons.space.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceFrame;

class FlightPlanTest {
    private static final int MAX_TICKS = 20_000;

    private static double[] space(SolarSystem.Body body, double x, double y, double z) {
        double[] s = new double[3];
        SpaceFrame.toSpace(body, x, y, z, s);
        return s;
    }

    /** Alvo: 300 blocos sobre o ponto da Lua voltado para a Terra. */
    private static double[] moonTarget() {
        double[] facing = new double[2];
        TravelMath.facingPoint(SolarSystem.MOON, facing);
        return space(SolarSystem.MOON, facing[0], 63 + 300, facing[1]);
    }

    private static double distanceToCenter(SolarSystem.Body body, double[] p) {
        return Math.sqrt(Math.pow(p[0] - body.x(), 2) + Math.pow(p[1] - body.y(), 2) + Math.pow(p[2] - body.z(), 2));
    }

    @Test
    void spawnToMoonIsAStraightClimbThatArrives() {
        double[] start = space(SolarSystem.EARTH, 0.5, 65, 0.5);
        double[] target = moonTarget();
        FlightPlan plan = FlightPlan.create(SolarSystem.BODIES, start, target);
        assertEquals(1, plan.waypoints().size());

        List<SolarSystem.Body> visited = new ArrayList<>();
        SolarSystem.Body current = SolarSystem.EARTH;
        visited.add(current);
        double previousSpeed = 0;
        int ticks = 0;
        boolean done = false;
        while (!done && ticks < MAX_TICKS) {
            done = plan.advance();
            ticks++;
            double[] v = plan.velocity();
            double speed = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            assertTrue(speed <= FlightPlan.MAX_SPEED + 1e-9, "velocidade " + speed);
            // aceleração limitada: nenhum tranco
            assertTrue(speed - previousSpeed <= previousSpeed * (FlightPlan.ACCELERATION_FACTOR - 1) + FlightPlan.ACCELERATION_ADD + 1e-9,
                    "salto de velocidade " + previousSpeed + " -> " + speed);
            previousSpeed = speed;
            SolarSystem.Body next = SphereOfInfluence.dominant(current, plan.position()[0], plan.position()[1], plan.position()[2]);
            if (next != current) {
                visited.add(next);
                current = next;
            }
            // nunca atravessa a superfície de um corpo
            for (SolarSystem.Body body : SolarSystem.BODIES) {
                assertTrue(distanceToCenter(body, plan.position()) > body.planet().radius() - 1, "dentro de " + body.planet().id());
            }
        }
        assertTrue(done, "não chegou em " + MAX_TICKS + " ticks");
        // viagem em menos de ~2 minutos de jogo (2.400 ticks) e uma única troca Terra -> Lua
        assertTrue(ticks < 2_400, "ticks " + ticks);
        assertEquals(List.of(SolarSystem.EARTH, SolarSystem.MOON), visited);
        for (int i = 0; i < 3; i++) {
            assertEquals(target[i], plan.position()[i], 1e-9);
        }
        assertEquals(0, plan.velocity()[0] + plan.velocity()[1] + plan.velocity()[2], 1e-12);
    }

    @Test
    void moonToEarthComesBackToTheSpawnZenith() {
        double[] facing = new double[2];
        TravelMath.facingPoint(SolarSystem.MOON, facing);
        double[] start = space(SolarSystem.MOON, facing[0], 63 + 300, facing[1]);
        double[] earthFacing = new double[2];
        TravelMath.facingPoint(SolarSystem.EARTH, earthFacing);
        double[] target = space(SolarSystem.EARTH, earthFacing[0], 63 + 300, earthFacing[1]);
        FlightPlan plan = FlightPlan.create(SolarSystem.BODIES, start, target);
        int ticks = 0;
        while (!plan.advance() && ticks < MAX_TICKS) {
            ticks++;
        }
        assertTrue(plan.isFinished());
        assertTrue(ticks < 2_400, "ticks " + ticks);
    }

    @Test
    void farSideStartDetoursAroundTheBodyInstead() {
        // saindo do lado oposto da Terra (-X): a reta até a Lua atravessaria o planeta
        double[] start = space(SolarSystem.EARTH, SolarSystem.EARTH.planet().halfCircumference() - 0.5, 80, 0.5);
        double[] target = moonTarget();
        FlightPlan plan = FlightPlan.create(SolarSystem.BODIES, start, target);
        assertTrue(plan.waypoints().size() > 1, "esperava desvio, veio " + plan.waypoints().size() + " ponto(s)");
        int ticks = 0;
        boolean done = false;
        while (!done && ticks < MAX_TICKS) {
            done = plan.advance();
            ticks++;
            for (SolarSystem.Body body : SolarSystem.BODIES) {
                assertTrue(distanceToCenter(body, plan.position()) > body.planet().radius() - 1, "dentro de " + body.planet().id());
            }
        }
        assertTrue(done, "não chegou");
        for (int i = 0; i < 3; i++) {
            assertEquals(target[i], plan.position()[i], 1e-9);
        }
    }

    @Test
    void speedGrowsWithAltitudeAndBrakesNearTheTarget() {
        double[] start = space(SolarSystem.EARTH, 0.5, 65, 0.5);
        FlightPlan plan = FlightPlan.create(SolarSystem.BODIES, start, moonTarget());
        double peak = 0;
        double lastSpeed = 0;
        int ticks = 0;
        while (!plan.advance() && ticks++ < MAX_TICKS) {
            double[] v = plan.velocity();
            lastSpeed = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            peak = Math.max(peak, lastSpeed);
        }
        assertEquals(FlightPlan.MAX_SPEED, peak, 1e-9);
        // a última velocidade antes de parar é baixa (frenagem), bem abaixo do cruzeiro
        assertTrue(lastSpeed < 5.0, "velocidade na chegada " + lastSpeed);
    }
}
