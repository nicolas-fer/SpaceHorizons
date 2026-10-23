package com.spacehorizons.space.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.Heliocentric;
import com.spacehorizons.space.SolarSystem;

/** O planejador Terra → Marte: janela, Δv da injeção, previsão da chegada e correção de meio de curso. */
class MarsPlannerTest {
    private static final BodyPhysics EARTH = BodyPhysics.of(SolarSystem.EARTH);
    private static final BodyPhysics MARS = BodyPhysics.of(SolarSystem.MARS);
    /** Órbita baixa circular equatorial, para o leste, a 5.600 de altitude (a da missão). */
    private static final double R0 = EARTH.radius() + 5_600.0;
    private static final double N = Math.sqrt(EARTH.mu() / (R0 * R0 * R0));

    private static FlightState leo(double psi, double extraDeltaV) {
        V3 pos = new V3(R0 * Math.cos(psi), 0.0, R0 * Math.sin(psi));
        V3 tangent = new V3(-Math.sin(psi), 0.0, Math.cos(psi));
        return new FlightState(SolarSystem.EARTH, pos, tangent.times(EARTH.circularSpeed(R0) + extraDeltaV), 0.0, 0.0);
    }

    /** O primeiro relógio (a partir de {@code from}) em que o plano manda queimar, com a nave na órbita que parte de ψ0 em {@code from}. */
    private static double firstBurn(double from, double psi0) {
        for (double clock = from; clock < from + 700_000; clock += 20) {
            FlightState s = leo(psi0 + N * (clock - from), 0.0);
            if (MarsPlanner.plan(s, clock).burnNow()) {
                return clock;
            }
        }
        throw new AssertionError("nenhuma janela em 700.000 ticks");
    }

    @Test
    void theMinimumInjectionIsTheEllipseThatReachesTheEdgeOfTheEarthSphere() {
        FlightState s = leo(0.0, 0.0);
        double gap = MarsPlanner.escapeSpeedGap(s);
        // ~29,6 b/t a partir de 5.600 de altitude (o Δv da missão lunar até a TLI é 26,6)
        assertEquals(29.6, gap, 0.2);
        double apoapsis = Orbit.fromState(EARTH.mu(), s.r(), s.v().plus(s.v().unit().times(gap)), new V3(0, -1, 0)).apoapsis();
        assertEquals(EARTH.soiExitRadius(), apoapsis, 5.0);
    }

    @Test
    void theWindowOpensOnceEverySynodicPeriodAndSuggestsAboutThirtyOneBlocksPerTick() {
        double first = firstBurn(0.0, 0.0);
        MarsPlanner.Plan plan = MarsPlanner.plan(leo(N * first, 0.0), first);
        assertTrue(plan.usable());
        assertTrue(plan.prograde());
        assertEquals(30.6, plan.injectionDeltaV(), 0.3);
        assertTrue(Math.abs(plan.phaseErrorDeg()) <= MarsPlanner.WINDOW_HALF_ANGLE_DEG);
        // a fase de Marte à frente da Terra na janela está na faixa da tabela (34° a 66°)
        assertTrue(plan.currentPhaseDeg() > 28.0 && plan.currentPhaseDeg() < 72.0, "fase = " + plan.currentPhaseDeg());
        // a próxima janela vem um período sinódico depois (os pontos da órbita entram na faixa ao longo de ~60.000 ticks)
        double next = firstBurn(first + 100_000, N * (first + 100_000 - first));
        double gap = next - first;
        assertTrue(Math.abs(gap % Heliocentric.synodicPeriod()) < 70_000 || Math.abs(gap % Heliocentric.synodicPeriod() - Heliocentric.synodicPeriod()) < 70_000,
                "intervalo entre janelas = " + gap);
    }

    @Test
    void theTimeToWindowCountsDownToABurn() {
        double clock = 1_000.0;
        MarsPlanner.Plan plan = MarsPlanner.plan(leo(0.5, 0.0), clock);
        assertTrue(plan.usable());
        assertFalse(plan.burnNow());
        double wait = plan.timeToWindowTicks();
        assertTrue(wait > 0 && wait < Heliocentric.synodicPeriod() * 1.05, "espera = " + wait);
        // esperando o tempo previsto, o plano manda queimar (a nave anda na órbita)
        double t = clock + wait;
        MarsPlanner.Plan then = MarsPlanner.plan(leo(0.5 + N * wait, 0.0), t);
        assertTrue(Math.abs(then.phaseErrorDeg()) <= MarsPlanner.WINDOW_HALF_ANGLE_DEG + 0.1, "erro = " + then.phaseErrorDeg());
    }

    @Test
    void otherOrbitsGetAnExplanationNotANumber() {
        // na Lua
        FlightState moon = new FlightState(SolarSystem.MOON, new V3(30_000, 0, 0), new V3(0, 0, 8), 0, 0);
        assertEquals(MarsPlanner.Status.NOT_AROUND_EARTH, MarsPlanner.plan(moon, 0).status());
        // órbita para o OESTE
        FlightState west = new FlightState(SolarSystem.EARTH, new V3(R0, 0, 0), new V3(0, 0, -EARTH.circularSpeed(R0)), 0, 0);
        assertEquals(MarsPlanner.Status.WRONG_DIRECTION, MarsPlanner.plan(west, 0).status());
        // suborbital
        FlightState suborbital = new FlightState(SolarSystem.EARTH, new V3(R0, 0, 0), new V3(20, 0, 30), 0, 0);
        assertEquals(MarsPlanner.Status.NOT_IN_ORBIT, MarsPlanner.plan(suborbital, 0).status());
        // relógio pausado
        assertEquals(MarsPlanner.Status.NOT_IN_ORBIT, MarsPlanner.plan(leo(0, 0), 0, 0.0).status());
    }

    @Test
    void theInjectionSolverAimsTheArrivalAtTheRequestedPeriapsis() {
        double clock = firstBurn(0.0, 0.0);
        FlightState s = leo(N * clock, 0.0);
        double dv = MarsPlanner.injectionDeltaV(s, clock, 3_400.0, true);
        assertFalse(Double.isNaN(dv), "a janela do plano tem solução");
        assertTrue(dv > MarsPlanner.escapeSpeedGap(s) - 0.1 && dv < MarsPlanner.escapeSpeedGap(s) + 3.0, "dv = " + dv);
        MarsPlanner.Arrival arrival = MarsPlanner.arrival(leo(N * clock, dv), clock, MarsPlanner.DEFAULT_HORIZON);
        assertNotNull(arrival);
        assertTrue(arrival.prograde());
        // a correção de meio de curso fecha o resto (os alvos do Δv são aproximados)
        assertEquals(3_400.0, arrival.periapsisAltitude(), 100_000.0);
        // o voo até a esfera de Marte: entre 50.000 e 100.000 ticks (o do Hohmann seria 170.000)
        assertTrue(arrival.ticksToEntry() > 40_000 && arrival.ticksToEntry() < 110_000, "ticks até Marte = " + arrival.ticksToEntry());
        assertEquals(0.0, arrival.inclinationDeg(), 1e-6);
    }

    @Test
    void theMidcourseCorrectionRepairsASmallError() {
        double clock = firstBurn(0.0, 0.0);
        FlightState burned = leo(N * clock, MarsPlanner.injectionDeltaV(leo(N * clock, 0.0), clock, 3_400.0, true));
        // um erro de queima de 0,05 b/t: o periastro anda dezenas de milhares de blocos
        FlightState off = burned.withRV(burned.r(), burned.v().plus(burned.v().unit().times(0.05)));
        // 400 ticks depois da queima (a correção só acontece com a nave já longe da órbita baixa)
        Kepler.State later = Kepler.propagate(EARTH.mu(), off.r(), off.v(), 400.0);
        MarsPlanner.Correction c = MarsPlanner.correction(off.withRV(later.r(), later.v()), clock + 400.0, 3_400.0, true, 4.0, MarsPlanner.DEFAULT_HORIZON);
        assertNotNull(c);
        assertTrue(c.deltaV().length() < 1.0, "Δv da correção = " + c.deltaV().length());
        assertTrue(c.arrival().prograde());
        assertEquals(3_400.0, c.arrival().periapsisAltitude(), 15_000.0);
        assertTrue(MARS.radius() > 0);
    }

    @Test
    void theTableOfIdealPhasesCoversTheBurnArc() {
        MarsPlanner.Table table = MarsPlanner.table(R0);
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        for (double psi = 165.0; psi <= 300.0; psi += 5.0) {
            double phase = table.needed(Math.toRadians(psi));
            assertFalse(Double.isNaN(phase), "ψ = " + psi);
            low = Math.min(low, Math.toDegrees(phase));
            high = Math.max(high, Math.toDegrees(phase));
            assertTrue(table.transferTicks(Math.toRadians(psi)) > 50_000 && table.transferTicks(Math.toRadians(psi)) < 160_000);
        }
        assertEquals(28.0, low, 3.0);
        assertTrue(high > 60.0 && high < 75.0, "fase máxima = " + high);
        // fora da faixa de queima não há fase
        assertTrue(Double.isNaN(table.needed(Math.toRadians(100.0))));
    }
}
