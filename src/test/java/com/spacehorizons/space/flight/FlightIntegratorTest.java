package com.spacehorizons.space.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;

class FlightIntegratorTest {
    private static final BodyPhysics EARTH = BodyPhysics.of(SolarSystem.EARTH);
    private static final BodyPhysics MOON = BodyPhysics.of(SolarSystem.MOON);
    private static final double LEO = EARTH.radius() + 6_000;
    /** Foguete de órbita: 2 motores, 5 tanques, 22 t com os tanques cheios. */
    private static final RocketStats ORBITER = RocketStats.of(2, 5, 1, 0, 1, 30, 9.0);
    private static final RocketStats MINIMAL = RocketStats.of(1, 1, 0, 0, 1, 3, 1.0);

    private static double energy(double mu, V3 r, V3 v) {
        return 0.5 * v.lengthSquared() - mu / r.length();
    }

    private static FlightState coast(FlightState start, RocketStats stats, int ticks) {
        FlightState s = start;
        for (int i = 0; i < ticks; i++) {
            s = FlightIntegrator.step(s, Control.IDLE, stats, 0);
        }
        return s;
    }

    // ------------------------------------------------------------------ gravidade (Verlet contra Kepler)

    @Test
    void verletTracksKeplerOverAWholeTransferEllipse() {
        double speed = Math.sqrt(EARTH.mu() * (2.0 / LEO - 2.0 / (LEO + 60_000)));
        V3 r0 = new V3(LEO, 0, 0);
        V3 v0 = new V3(0, 0, speed);
        int ticks = 15_911;
        FlightState s = coast(new FlightState(SolarSystem.EARTH, r0, v0, 0, 0), MINIMAL, ticks);
        Kepler.State k = Kepler.propagate(EARTH.mu(), r0, v0, ticks);
        assertTrue(s.r().distanceTo(k.r()) < 1.0, "diferença = " + s.r().distanceTo(k.r()));
        double e0 = energy(EARTH.mu(), r0, v0);
        assertTrue(Math.abs((energy(EARTH.mu(), s.r(), s.v()) - e0) / e0) < 1e-12);
    }

    @Test
    void verletTracksKeplerOnTheLunarHyperbola() {
        V3 r0 = new V3(MOON.radius() + 500, 0, 0);
        V3 v0 = new V3(0, 0, 10.05);
        FlightState s = coast(new FlightState(SolarSystem.MOON, r0, v0, 0, 0), MINIMAL, 3_000);
        Kepler.State k = Kepler.propagate(MOON.mu(), r0, v0, 3_000);
        assertTrue(s.r().distanceTo(k.r()) < 0.02, "diferença = " + s.r().distanceTo(k.r()));
    }

    @Test
    void lowOrbitKeepsAConstantRadiusForTenOrbits() {
        double vc = EARTH.circularSpeed(LEO);
        V3 r0 = new V3(LEO, 0, 0);
        V3 v0 = new V3(0, 0, vc);
        FlightState s = new FlightState(SolarSystem.EARTH, r0, v0, 0, 0);
        double minR = Double.MAX_VALUE;
        double maxR = 0;
        int ticks = (int) Math.round(10 * EARTH.period(LEO));
        for (int i = 0; i < ticks; i++) {
            s = FlightIntegrator.step(s, Control.IDLE, MINIMAL, 0);
            minR = Math.min(minR, s.r().length());
            maxR = Math.max(maxR, s.r().length());
        }
        assertTrue(maxR - minR < 0.05, "o raio oscilou " + (maxR - minR));
        Kepler.State k = Kepler.propagate(EARTH.mu(), r0, v0, ticks);
        assertTrue(s.r().distanceTo(k.r()) < 2.0);
    }

    @Test
    void noAtmosphereMeansNoDragAndNoHeatInSpaceOrOnTheMoon() {
        FlightState s = new FlightState(SolarSystem.MOON, new V3(MOON.radius() + 50, 0, 0), new V3(0, 0, 5), 0, 0.3);
        FlightState next = FlightIntegrator.step(s, Control.IDLE, MINIMAL, 0);
        // só a gravidade age: a energia se conserva e o calor só decai
        assertEquals(energy(MOON.mu(), s.r(), s.v()), energy(MOON.mu(), next.r(), next.v()), 1e-9);
        assertEquals(0.3 * (1 - FlightConstants.HEAT_DECAY), next.heat(), 1e-12);
    }

    // ------------------------------------------------------------------ empuxo e combustível

    /** Estado muito longe de tudo: a gravidade (~1e-12) não atrapalha medir o Δv do empuxo. */
    private static FlightState farAway(double fuelMb) {
        return new FlightState(SolarSystem.EARTH, new V3(1e9, 0, 0), V3.ZERO, fuelMb, 0);
    }

    @Test
    void burnGivesExactlyTheTsiolkovskyDeltaV() {
        double fuel = ORBITER.fuelCapacityMb();
        FlightState s = farAway(fuel);
        double m0 = ORBITER.wetMass(fuel);
        for (int i = 0; i < 100; i++) {
            s = FlightIntegrator.step(s, Control.thrust(1.0, new V3(0, 0, 1)), ORBITER, 0);
        }
        double used = fuel - s.fuelMb();
        assertTrue(used > 0);
        double m1 = ORBITER.wetMass(s.fuelMb());
        assertEquals(Tsiolkovsky.deltaV(40, m0, m1), s.v().z(), 1e-6);
        // 100 ticks a 100% com 2 motores: vazão de 0,125 t/tick = 156,25 mB/tick
        assertEquals(100 * 156.25, used, 1e-6);
        // o empuxo foi só ao longo de +Z
        assertEquals(0.0, s.v().x(), 1e-6);
        assertEquals(0.0, s.v().y(), 1e-6);
    }

    @Test
    void throttleScalesTheFuelFlow() {
        FlightState full = FlightIntegrator.step(farAway(10_000), Control.thrust(1.0, new V3(0, 0, 1)), ORBITER, 0);
        FlightState half = FlightIntegrator.step(farAway(10_000), Control.thrust(0.5, new V3(0, 0, 1)), ORBITER, 0);
        assertEquals(2.0, (10_000 - full.fuelMb()) / (10_000 - half.fuelMb()), 1e-9);
        assertTrue(half.v().z() < full.v().z());
        assertTrue(half.v().z() > 0.49 * full.v().z());
    }

    @Test
    void lastTickOfABurnIsPartialAndTheTotalDeltaVMatchesTheFullTank() {
        // combustível para poucos ticks: acaba no meio de um tick
        double fuel = 500;
        FlightState s = farAway(fuel);
        double m0 = ORBITER.wetMass(fuel);
        for (int i = 0; i < 10; i++) {
            s = FlightIntegrator.step(s, Control.thrust(1.0, new V3(0, 0, 1)), ORBITER, 0);
        }
        assertEquals(0.0, s.fuelMb(), 0.0);
        assertEquals(ORBITER.deltaV(fuel), s.v().z(), 1e-6);
        assertEquals(Tsiolkovsky.deltaV(40, m0, ORBITER.dryMassT()), s.v().z(), 1e-6);
        // depois de acabar, o empuxo não faz mais nada
        FlightState after = FlightIntegrator.step(s, Control.thrust(1.0, new V3(0, 0, 1)), ORBITER, 0);
        assertEquals(s.v().z(), after.v().z(), 1e-9);
        assertEquals(0.0, after.fuelMb(), 0.0);
    }

    @Test
    void thrustIsIgnoredWithoutFuelEnginesOrDirection() {
        V3 dir = new V3(0, 0, 1);
        assertEquals(0.0, FlightIntegrator.step(farAway(0), Control.thrust(1.0, dir), ORBITER, 0).v().z(), 1e-9);
        RocketStats noEngine = RocketStats.of(0, 1, 0, 0, 1, 2, 1.0);
        assertEquals(0.0, FlightIntegrator.step(farAway(4_000), Control.thrust(1.0, dir), noEngine, 0).v().z(), 1e-9);
        FlightState noDirection = FlightIntegrator.step(farAway(4_000), Control.thrust(1.0, V3.ZERO), ORBITER, 0);
        assertEquals(4_000, noDirection.fuelMb(), 0.0);
    }

    @Test
    void controlNormalizesItsInputs() {
        Control c = new Control(7.5, new V3(0, 0, 10), false);
        assertEquals(1.0, c.throttle(), 0.0);
        assertEquals(1.0, c.thrustDirection().length(), 1e-15);
        assertEquals(0.0, new Control(-1, V3.ZERO, false).throttle(), 0.0);
        assertEquals(0.0, new Control(Double.NaN, V3.ZERO, false).throttle(), 0.0);
        assertEquals(V3.ZERO, new Control(1, null, false).thrustDirection());
    }

    @Test
    void tliBurnGivesTheHohmannApoapsis() {
        // queima de 5,8234 b/t em órbita baixa de 6.000 com TWR alto: a perda de gravidade é pequena (< 10 blocos)
        RocketStats strong = RocketStats.of(5, 21, 0, 0, 1, 30, 9.0);
        double vc = EARTH.circularSpeed(LEO);
        FlightState s = new FlightState(SolarSystem.EARTH, new V3(LEO, 0, 0), new V3(0, 0, vc), 30_000, 0);
        double remaining = Hohmann.dv1(EARTH.mu(), LEO, 60_000);
        int ticks = 0;
        while (remaining > 1e-9 && ticks++ < 1_000) {
            double m0 = strong.wetMass(s.fuelMb());
            double fullTickDv = Tsiolkovsky.deltaV(40, m0, m0 - strong.fullThrottleFlow());
            // no último tick o acelerador é reduzido para entregar exatamente o Δv que falta
            double throttle = remaining >= fullTickDv ? 1.0
                    : (m0 - Tsiolkovsky.finalMass(m0, 40, remaining)) / strong.fullThrottleFlow();
            s = FlightIntegrator.step(s, Control.thrust(throttle, s.v().unit()), strong, 0);
            remaining -= Tsiolkovsky.deltaV(40, m0, strong.wetMass(s.fuelMb()));
        }
        // TWR em órbita ~5: a queima dura poucos ticks e a perda de gravidade é de dezenas de blocos
        assertTrue(ticks < 40, "queima de " + ticks + " ticks");
        Orbit o = Orbit.fromState(EARTH.mu(), s.r(), s.v(), new V3(0, -1, 0));
        assertEquals(60_000, o.apoapsis(), 200, "apoastro = " + o.apoapsis());
    }

    // ------------------------------------------------------------------ atmosfera

    @Test
    void dragNeverFlipsTheSignOrAddsSpeed() {
        V3 v = new V3(3, -4, 12);
        for (double k0 : new double[] {0, 1e-4, 0.32, 10, 1e6}) {
            for (double rho : new double[] {0, 0.001, 1}) {
                V3 out = FlightIntegrator.applyDrag(v, k0, rho);
                assertTrue(out.dot(v) >= 0.0, "k0=" + k0 + " rho=" + rho);
                assertTrue(out.length() <= v.length() + 1e-12);
                // continua na mesma direção
                assertEquals(0.0, out.cross(v).length(), 1e-9);
            }
        }
        assertEquals(v, FlightIntegrator.applyDrag(v, 0.32, 0.0));
        assertEquals(V3.ZERO, FlightIntegrator.applyDrag(V3.ZERO, 0.32, 1.0));
    }

    @Test
    void fallingRocketSettlesAtItsTerminalVelocity() {
        // mantém a altitude fixa (ρ = 1) e deixa só a velocidade evoluir: gravidade e arrasto se equilibram em √(g/k0)
        RocketStats rocket = RocketStats.of(2, 5, 0, 0, 1, 30, 9.0);
        V3 r0 = new V3(EARTH.radius() + 100, 0, 0);
        FlightState s = new FlightState(SolarSystem.EARTH, r0, V3.ZERO, 16_000, 0);
        for (int i = 0; i < 3_000; i++) {
            FlightState next = FlightIntegrator.step(s, Control.IDLE, rocket, 0);
            s = next.withRV(r0, next.v());
        }
        double terminal = Math.sqrt(EARTH.gravityAt(r0.length()) / rocket.dragK0());
        assertEquals(terminal, s.speed(), 0.01 * terminal);
        assertEquals(16.0, s.speed(), 1.0);
        // a velocidade só tem componente radial (para baixo)
        assertTrue(s.v().x() < 0);
        assertEquals(0.0, s.v().y(), 1e-9);
    }

    @Test
    void parachuteOpensWithARampAndLandsSoftly() {
        FlightState s = new FlightState(SolarSystem.EARTH, new V3(EARTH.radius() + 900, 0, 0), new V3(-4, 0, 0), 0, 0);
        // pede o paraquedas no primeiro tick
        s = FlightIntegrator.step(s, new Control(0, V3.ZERO, true), ORBITER, 0);
        assertEquals(1.0 / 80, s.chute(), 1e-12);
        double previous = s.chute();
        int ticks = 1;
        while (s.chute() < 1.0 && ticks < 200) {
            s = FlightIntegrator.step(s, Control.IDLE, ORBITER, 0);
            assertTrue(s.chute() >= previous);
            previous = s.chute();
            ticks++;
        }
        assertEquals(80, ticks);
        assertEquals(1.0, s.chute(), 1e-12);
        // desce até perto do chão: a velocidade vira a terminal do paraquedas
        for (int i = 0; i < 4_000 && s.altitude() > 40; i++) {
            s = FlightIntegrator.step(s, Control.IDLE, ORBITER, 0);
        }
        assertTrue(s.altitude() <= 40, "altitude = " + s.altitude());
        assertTrue(Math.abs(s.verticalSpeed()) < 0.7, "v vertical = " + s.verticalSpeed());
        assertTrue(Math.abs(s.verticalSpeed()) > 0.3);
        assertEquals(LandingRules.Outcome.SOFT_LANDING, LandingRules.contact(s.verticalSpeed(), 0, 0));
    }

    @Test
    void parachuteDoesNotOpenInVacuumTooFastOrWithoutOne() {
        // no vácuo
        FlightState space = new FlightState(SolarSystem.EARTH, new V3(EARTH.radius() + 3_000, 0, 0), new V3(-2, 0, 0), 0, 0);
        assertEquals(0.0, FlightIntegrator.step(space, new Control(0, V3.ZERO, true), ORBITER, 0).chute(), 0.0);
        // depressa demais (>= 8 b/t) dentro da atmosfera
        FlightState fast = new FlightState(SolarSystem.EARTH, new V3(EARTH.radius() + 900, 0, 0), new V3(-12, 0, 0), 0, 0);
        assertEquals(0.0, FlightIntegrator.step(fast, new Control(0, V3.ZERO, true), ORBITER, 0).chute(), 0.0);
        // sem paraquedas no foguete
        FlightState slow = new FlightState(SolarSystem.EARTH, new V3(EARTH.radius() + 900, 0, 0), new V3(-4, 0, 0), 0, 0);
        assertEquals(0.0, FlightIntegrator.step(slow, new Control(0, V3.ZERO, true), MINIMAL, 0).chute(), 0.0);
        assertTrue(FlightIntegrator.canDeployParachute(0.5, 7.9));
        assertFalse(FlightIntegrator.canDeployParachute(0.5, 8.0));
        assertFalse(FlightIntegrator.canDeployParachute(0.0, 1.0));
    }

    @Test
    void parachuteKeepsOpeningOnceStarted() {
        FlightState s = new FlightState(SolarSystem.EARTH, new V3(EARTH.radius() + 900, 0, 0), new V3(-4, 0, 0), 0, 0);
        s = FlightIntegrator.step(s, new Control(0, V3.ZERO, true), ORBITER, 0);
        // o pedido some, mas a rampa segue até abrir (o paraquedas não fecha)
        for (int i = 0; i < 100; i++) {
            s = FlightIntegrator.step(s, Control.IDLE, ORBITER, 0);
        }
        assertEquals(1.0, s.chute(), 1e-12);
    }

    // ------------------------------------------------------------------ calor

    @Test
    void heatIndexGrowsWithSpeedAndDensityAndVanishesInVacuum() {
        assertEquals(0.0, FlightIntegrator.heatIndex(0, 30), 0.0);
        assertEquals(1.0, FlightIntegrator.heatIndex(1.0, 28.9), 1e-12);
        assertTrue(FlightIntegrator.heatIndex(0.5, 30) > FlightIntegrator.heatIndex(0.5, 20));
        assertTrue(FlightIntegrator.heatIndex(0.8, 30) > FlightIntegrator.heatIndex(0.2, 30));
        // índice de referência: 0,15 a 0,19 em LEO a vt = 10 (ρ ~ 0,1..0,2 a ~26 b/t)
        double q = FlightIntegrator.heatIndex(0.1, 26);
        assertTrue(q > 0.08 && q < 0.4, "Q = " + q);
    }

    @Test
    void heatMeterRisesInTheAirAndFallsBackInVacuum() {
        // mergulha na atmosfera a 30 b/t
        FlightState s = new FlightState(SolarSystem.EARTH, new V3(EARTH.radius() + 1_200, 0, 0), new V3(-20, 0, 25), 0, 0);
        double peak = 0;
        for (int i = 0; i < 60; i++) {
            s = FlightIntegrator.step(s, Control.IDLE, MINIMAL, 0);
            peak = Math.max(peak, s.heat());
        }
        assertTrue(peak > 0.05, "pico = " + peak);
        assertTrue(peak <= FlightConstants.HEAT_MAX);
        // no vácuo o medidor esfria com constante de tempo de 100 ticks
        FlightState hot = new FlightState(SolarSystem.EARTH, new V3(EARTH.radius() + 6_000, 0, 0), new V3(0, 0, 23), 0, 0.8);
        FlightState cooled = coast(hot, MINIMAL, 100);
        assertEquals(0.8 * Math.pow(1 - FlightConstants.HEAT_DECAY, 100), cooled.heat(), 1e-12);
        assertEquals(0.0, FlightIntegrator.stepHeat(0.0, 0.0, 0.0), 0.0);
        assertEquals(FlightConstants.HEAT_MAX, FlightIntegrator.stepHeat(FlightConstants.HEAT_MAX, 1.0, 1_000), 0.0);
    }

    // ------------------------------------------------------------------ subida

    @Test
    void minimalRocketClimbsAboveTheAtmosphere() {
        // subida vertical com o foguete mínimo (TWR 6): passa de 1.800 de altitude e o apoastro fica acima de 5.000
        FlightState s = new FlightState(SolarSystem.EARTH, new V3(EARTH.radius() + 1, 0, 0), V3.ZERO, 4_000, 0);
        for (int i = 0; i < 2_000 && s.fuelMb() > 0; i++) {
            s = FlightIntegrator.step(s, Control.thrust(1.0, s.r().unit()), MINIMAL, 0);
        }
        assertEquals(0.0, s.fuelMb(), 0.0);
        // coasting: o foguete sobe depois de apagar
        Orbit o = Orbit.fromState(EARTH.mu(), s.r(), s.v(), new V3(0, -1, 0));
        assertTrue(o.apoapsis() - EARTH.radius() > 5_000, "apoastro = " + (o.apoapsis() - EARTH.radius()));
    }
}
