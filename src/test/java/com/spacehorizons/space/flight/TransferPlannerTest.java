package com.spacehorizons.space.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.flight.TransferPlanner.Plan;
import com.spacehorizons.space.flight.TransferPlanner.Status;

class TransferPlannerTest {
    private static final BodyPhysics EARTH = BodyPhysics.of(SolarSystem.EARTH);
    private static final BodyPhysics MOON = BodyPhysics.of(SolarSystem.MOON);
    private static final double LEO = EARTH.radius() + 6_000;
    private static final RocketStats STATS = RocketStats.of(1, 1, 0, 0, 1, 3, 1.0);

    /** Base do plano da órbita lunar em I: a Lua anda de E1 para E2, para o leste (+Z; ver SolarSystem.moonInertialPose). */
    private static final V3 E1 = new V3(Math.cos(SolarSystem.ORBIT_INCLINATION), Math.sin(SolarSystem.ORBIT_INCLINATION), 0);
    private static final V3 E2 = new V3(0, 0, 1);

    /** Órbita circular de raio {@code r1} no PLANO DA LUA, no ângulo {@code phi}, no mesmo sentido dela (leste, prógrada). */
    private static FlightState planarOrbit(double r1, double phi) {
        V3 radial = E1.times(Math.cos(phi)).plus(E2.times(Math.sin(phi)));
        V3 tangent = E1.times(-Math.sin(phi)).plus(E2.times(Math.cos(phi)));
        return new FlightState(SolarSystem.EARTH, radial.times(r1), tangent.times(EARTH.circularSpeed(r1)), 0, 0);
    }

    private static double wrapDegrees(double d) {
        double w = d % 360;
        if (w > 180) {
            w -= 360;
        } else if (w <= -180) {
            w += 360;
        }
        return w;
    }

    /** Voo de TLI: ticks até entrar na esfera da Lua (−1 se não entrou) e o periastro em torno da Lua nesse instante. */
    private record Flight(int ticks, double moonPeriapsis) {
    }

    private static int flyTli(double clock0, double phi0, double offset, double clockRate) {
        return fly(clock0, phi0, offset, clockRate).ticks();
    }

    /**
     * Queima a TLI {@code offset} ticks depois do centro da janela prevista e voa até entrar na esfera da Lua, ou até
     * 30.000 ticks.
     */
    private static Flight fly(double clock0, double phi0, double offset, double clockRate) {
        FlightState start = planarOrbit(LEO, phi0);
        Plan plan = TransferPlanner.moon(start, clock0, clockRate);
        assertSame(Status.OK, plan.status());
        double burnAt = plan.timeToWindowTicks() + offset;
        Kepler.State coasted = Kepler.propagate(EARTH.mu(), start.r(), start.v(), burnAt);
        V3 burnVelocity = coasted.v().plus(coasted.v().unit().times(plan.tliDeltaV()));
        FlightState s = new FlightState(SolarSystem.EARTH, coasted.r(), burnVelocity, 0, 0);
        double clock = clock0 + clockRate * burnAt;
        for (int tick = 1; tick <= 30_000; tick++) {
            s = FlightIntegrator.step(s, Control.IDLE, STATS, clock);
            clock += clockRate;
            s = SoiTracker.update(s, clock, clockRate);
            if (s.body() == SolarSystem.MOON) {
                return new Flight(tick, Orbit.of(s, clock).periapsis());
            }
        }
        return new Flight(-1, Double.NaN);
    }

    // ------------------------------------------------------------------ estados do plano

    @Test
    void notAvailableAroundTheMoon() {
        FlightState moon = new FlightState(SolarSystem.MOON, new V3(MOON.radius() + 500, 0, 0), new V3(0, 0, 5), 0, 0);
        Plan plan = TransferPlanner.moon(moon, 1_000);
        assertSame(Status.NOT_AROUND_EARTH, plan.status());
        assertFalse(plan.usable());
        assertFalse(plan.burnNow());
        assertTrue(Double.isNaN(plan.tliDeltaV()));
    }

    @Test
    void notInOrbitWhenSuborbitalEccentricOrTooHigh() {
        // suborbital: o periastro está dentro do planeta
        FlightState hop = new FlightState(SolarSystem.EARTH, new V3(EARTH.radius() + 100, 0, 0), new V3(0, 0, 10), 0, 0);
        assertSame(Status.NOT_IN_ORBIT, TransferPlanner.moon(hop, 1_000).status());
        // elíptica demais (e ≈ 0,44)
        FlightState eccentric = planarOrbit(LEO, 0.3);
        eccentric = eccentric.withRV(eccentric.r(), eccentric.v().times(1.2));
        assertSame(Status.NOT_IN_ORBIT, TransferPlanner.moon(eccentric, 1_000).status());
        // alta demais: mais da metade do caminho até a Lua
        assertSame(Status.NOT_IN_ORBIT, TransferPlanner.moon(planarOrbit(400_000, 0.3), 1_000).status());
        // não ligada
        FlightState escaping = planarOrbit(LEO, 0.3);
        escaping = escaping.withRV(escaping.r(), escaping.v().times(1.5));
        assertSame(Status.NOT_IN_ORBIT, TransferPlanner.moon(escaping, 1_000).status());
    }

    @Test
    void westwardOrbitIsTheWrongDirection() {
        // equatorial para o oeste: a Lua vai para o leste (em I, como a rotação da Terra)
        double vc = EARTH.circularSpeed(LEO);
        FlightState west = new FlightState(SolarSystem.EARTH, new V3(LEO, 0, 0), new V3(0, 0, -vc), 0, 0);
        Plan plan = TransferPlanner.moon(west, 1_000);
        assertSame(Status.WRONG_DIRECTION, plan.status());
        assertFalse(plan.prograde());
        assertFalse(plan.usable());
        // para o leste a mesma órbita vale (a 5° do plano lunar)
        FlightState east = new FlightState(SolarSystem.EARTH, new V3(LEO, 0, 0), new V3(0, 0, vc), 0, 0);
        Plan ok = TransferPlanner.moon(east, 1_000);
        assertSame(Status.OK, ok.status());
        assertTrue(ok.prograde());
        assertEquals(5.0, ok.planeAngleDeg(), 0.01);
    }

    // ------------------------------------------------------------------ números do plano

    @Test
    void planNumbersMatchTheHohmannReference() {
        Plan plan = TransferPlanner.moon(planarOrbit(LEO, 0.7), 12_000);
        assertSame(Status.OK, plan.status());
        assertTrue(plan.usable());
        assertEquals(26.6285, plan.tliDeltaV(), 1e-3);
        assertEquals(22_538.55, plan.transferTicks(), 0.05);
        assertEquals(0.0, plan.planeAngleDeg(), 0.01);
        // a diferença entre as fases atual e ideal é o erro de fase (módulo 360)
        assertEquals(0.0, wrapDegrees(plan.currentPhaseDeg() - plan.idealPhaseDeg() - plan.phaseErrorDeg()), 1e-9);
        assertTrue(plan.currentPhaseDeg() >= 0 && plan.currentPhaseDeg() < 360);
        assertTrue(plan.idealPhaseDeg() >= 0 && plan.idealPhaseDeg() < 360);
        assertTrue(plan.timeToWindowTicks() > -200 && plan.timeToWindowTicks() < 12_500, "janela em " + plan.timeToWindowTicks());
    }

    @Test
    void idealPhaseIsConstantBecauseTheMoonIsUniformInTheInertialFrame() {
        // em I a Lua anda a n constante (6,35e-5 rad/tick, ~38 b/t): a fase ideal é 180° − n·t_f, a mesma a qualquer hora do dia
        // (antes, no referencial preso à Terra, ela variava de ~237° a ~248° porque a Lua andava de 85 a 167 b/t)
        Plan reference = TransferPlanner.moon(planarOrbit(LEO, 0.7), 0.0);
        double expected = 180.0 - Math.toDegrees(SolarSystem.MOON_MEAN_MOTION * reference.transferTicks());
        assertEquals(98.0, expected, 0.1);
        for (int i = 0; i < 48; i++) {
            Plan plan = TransferPlanner.moon(planarOrbit(LEO, 0.7), i * 500.0);
            assertEquals(expected, plan.idealPhaseDeg(), 0.01, "clock " + i * 500.0);
        }
    }

    @Test
    void burnNowIsExactlyTheWindowAroundTheIdealPhase() {
        for (int i = 0; i < 300; i++) {
            double phi = i * 0.13;
            Plan plan = TransferPlanner.moon(planarOrbit(LEO, phi), 3_000 + i * 411.0);
            assertEquals(Math.abs(plan.phaseErrorDeg()) <= TransferPlanner.WINDOW_HALF_ANGLE_DEG, plan.burnNow(),
                    "erro de fase " + plan.phaseErrorDeg());
        }
    }

    @Test
    void waitingUntilTheWindowOpensTurnsBurnNowOn() {
        for (double clock0 : new double[] {0, 6_500, 14_000, 22_222, 51_000}) {
            FlightState start = planarOrbit(LEO, 1.1);
            Plan first = TransferPlanner.moon(start, clock0);
            Kepler.State later = Kepler.propagate(EARTH.mu(), start.r(), start.v(), first.timeToWindowTicks());
            FlightState waiting = new FlightState(SolarSystem.EARTH, later.r(), later.v(), 0, 0);
            Plan second = TransferPlanner.moon(waiting, clock0 + first.timeToWindowTicks());
            assertTrue(second.burnNow(), "clock0 = " + clock0 + ", erro = " + second.phaseErrorDeg());
            assertTrue(Math.abs(second.phaseErrorDeg()) < 1.0, "erro = " + second.phaseErrorDeg());
            assertEquals(0.0, second.timeToWindowTicks(), 5.0);
        }
    }

    @Test
    void theWindowRepeatsEveryOrbit() {
        // a próxima janela depois de queimar tarde está uma órbita (relativa) adiante
        FlightState start = planarOrbit(LEO, 0.4);
        Plan now = TransferPlanner.moon(start, 9_000);
        double after = now.timeToWindowTicks() + 200;
        Kepler.State later = Kepler.propagate(EARTH.mu(), start.r(), start.v(), after);
        Plan next = TransferPlanner.moon(new FlightState(SolarSystem.EARTH, later.r(), later.v(), 0, 0), 9_000 + after);
        assertFalse(next.burnNow());
        // período sinódico 2π/(n − n_Lua) ≈ 8.470 ticks (a órbita baixa dá a volta em 7.802 ticks; a Lua anda 6,35e-5 rad/tick)
        double synodic = next.timeToWindowTicks() + 200;
        double expected = 2.0 * Math.PI / (EARTH.circularSpeed(LEO) / LEO - SolarSystem.MOON_MEAN_MOTION);
        assertEquals(expected, synodic, 60.0, "período sinódico = " + synodic);
        assertTrue(next.phaseErrorDeg() < 0, "a janela passou: o erro é negativo");
    }

    // ------------------------------------------------------------------ voo completo até a Lua

    @Test
    void burningAtThePlannedWindowReachesTheMoonSphereOfInfluence() {
        for (double clock0 : new double[] {0, 5_000, 12_000, 18_000, 25_000, 40_000}) {
            for (double phi0 : new double[] {0.3, 2.0, 4.4}) {
                int ticks = flyTli(clock0, phi0, 0, 1.0);
                // a transferência dura 22.539 ticks; a Lua anda a 38 b/t na mesma direção da nave (que chega ao apoastro a ~21 b/t),
                // então ela alcança a nave por trás e a esfera (91 mil blocos) é tocada ~5.500 ticks antes do fim
                assertTrue(ticks > 15_500 && ticks < 19_500, "clock0 = " + clock0 + ", phi0 = " + phi0 + ": " + ticks);
                // a Lua é uniforme em I: a transferência é a mesma a qualquer hora e fase (medido: 17.008 ticks)
                assertEquals(17_008, ticks, 40, "clock0 = " + clock0 + ", phi0 = " + phi0);
            }
        }
    }

    @Test
    void thePlannedBurnAimsAtTheCenterOfTheMoon() {
        // o alvo é o centro da Lua: no centro da janela o periastro em torno dela é pequeno (a correção de meio de curso
        // desloca o alvo para uma passagem a ~500 blocos de altitude); a ±50 ticks (3,4° de fase) ainda é menor que a esfera; medido: ~8.400 blocos nos dois casos (o alvo é o centro da Lua, de raio 26.076)
        for (double clock0 : new double[] {0, 12_000, 25_000, 40_000}) {
            for (double phi0 : new double[] {0.3, 2.0, 4.4}) {
                assertTrue(fly(clock0, phi0, 0, 1.0).moonPeriapsis() < 15_000, "clock0 = " + clock0 + ", phi0 = " + phi0);
                assertTrue(fly(clock0, phi0, 50, 1.0).moonPeriapsis() < 40_000, "clock0 = " + clock0 + ", phi0 = " + phi0);
            }
        }
    }

    @Test
    void burningAtTheEdgesOfTheWindowStillReachesTheMoon() {
        for (double clock0 : new double[] {3_000, 17_000, 33_000}) {
            for (double offset : new double[] {-50, 50}) {
                int ticks = flyTli(clock0, 0.9, offset, 1.0);
                assertTrue(ticks > 0, "clock0 = " + clock0 + ", deslocamento = " + offset);
            }
        }
    }

    @Test
    void burningHalfAnOrbitLateMissesTheMoon() {
        for (double clock0 : new double[] {0, 12_000, 25_000}) {
            assertEquals(-1, flyTli(clock0, 0.9, EARTH.period(LEO) / 2, 1.0), "clock0 = " + clock0);
        }
    }

    @Test
    void worksWithAPausedClockToo() {
        // com o relógio parado (taxa 0) a Lua fica onde está: o plano espera a nave dar a volta
        int ticks = flyTli(7_000, 0.9, 0, 0.0);
        assertTrue(ticks > 0, "ticks = " + ticks);
    }

    @Test
    void enteringTheSphereGivesAHyperbolaAroundTheMoon() {
        FlightState start = planarOrbit(LEO, 0.9);
        Plan plan = TransferPlanner.moon(start, 8_000);
        Kepler.State coasted = Kepler.propagate(EARTH.mu(), start.r(), start.v(), plan.timeToWindowTicks());
        FlightState s = new FlightState(SolarSystem.EARTH, coasted.r(), coasted.v().plus(coasted.v().unit().times(plan.tliDeltaV())),
                0, 0);
        double clock = 8_000 + plan.timeToWindowTicks();
        for (int tick = 0; tick < 30_000 && s.body() == SolarSystem.EARTH; tick++) {
            s = FlightIntegrator.step(s, Control.IDLE, STATS, clock);
            clock += 1;
            s = SoiTracker.update(s, clock);
        }
        assertSame(SolarSystem.MOON, s.body());
        Orbit around = Orbit.of(s, clock);
        // chega com a Lua alcançando a nave por trás: a velocidade relativa é a diferença (~17 b/t) mais a que a gravidade da Lua dá na
        // queda até a borda da esfera, poucas dezenas de b/t (antes, com a Lua cinemática em E, eram 83 a 146)
        assertTrue(around.periapsis() < MOON.soiRadius(), "periastro = " + around.periapsis());
        assertTrue(s.speed() > 8 && s.speed() < 35, "velocidade relativa = " + s.speed());
        assertEquals(15.9, s.speed(), 0.3);
    }
}
