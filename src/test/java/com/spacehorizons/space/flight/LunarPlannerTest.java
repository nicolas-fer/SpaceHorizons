package com.spacehorizons.space.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.flight.LunarPlanner.Arrival;
import com.spacehorizons.space.flight.LunarPlanner.Correction;
import com.spacehorizons.space.flight.LunarPlanner.ReturnBurn;
import com.spacehorizons.space.flight.TransferPlanner.Plan;

/** O planejador da chegada à Lua (correção de meio de curso) e da volta (queima de injeção Lua para Terra). */
class LunarPlannerTest {
    private static final BodyPhysics EARTH = BodyPhysics.of(SolarSystem.EARTH);
    private static final BodyPhysics MOON = BodyPhysics.of(SolarSystem.MOON);
    private static final double LEO = EARTH.radius() + 5_600;

    /** Órbita circular equatorial para o leste (o plano do equador da Terra: +X, +Z em I) no ângulo {@code phi}. */
    private static FlightState equatorial(double r1, double phi) {
        V3 radial = new V3(Math.cos(phi), 0, Math.sin(phi));
        V3 tangent = new V3(-Math.sin(phi), 0, Math.cos(phi));
        return new FlightState(SolarSystem.EARTH, radial.times(r1), tangent.times(EARTH.circularSpeed(r1)), 0, 0);
    }

    /** O estado logo depois da TLI impulsiva do planejador, queimada no centro da próxima janela; {@code clockOut[0]} é o relógio dele. */
    private static FlightState afterTli(double clock0, double phi0, double[] clockOut) {
        FlightState start = equatorial(LEO, phi0);
        Plan plan = TransferPlanner.moon(start, clock0, 1.0);
        assertTrue(plan.usable(), "plano: " + plan.status());
        Kepler.State coasted = Kepler.propagate(EARTH.mu(), start.r(), start.v(), plan.timeToWindowTicks());
        clockOut[0] = clock0 + plan.timeToWindowTicks();
        return new FlightState(SolarSystem.EARTH, coasted.r(), coasted.v().plus(coasted.v().unit().times(plan.tliDeltaV())), 0, 0);
    }

    @Test
    void tliArrivesWithItsOutOfPlaneMissSetByWhereTheMoonIsRelativeToTheEquator() {
        // a TLI do planejador mira o centro da Lua PROJETADO no plano da órbita: o que falta é a Lua estar fora do plano do equador (5° da
        // órbita dela), até ~100.000 blocos no plano B. No relógio 15.000 ela cruza o equador na chegada (miss ~0); no 5.000, não
        double[] clock = new double[1];
        Arrival near = LunarPlanner.arrival(afterTli(15_000, 0.4, clock), clock[0], 30_000);
        Arrival far = LunarPlanner.arrival(afterTli(5_000, 0.4, clock), clock[0], 30_000);
        assertNotNull(near);
        assertNotNull(far);
        assertTrue(Math.abs(near.bNormal()) < 3_000, "perto do nó: " + near.bNormal());
        assertTrue(Math.abs(far.bNormal()) > 50_000, "longe do nó: " + far.bNormal());
        assertEquals(16.1, near.relativeSpeed(), 0.5);
    }

    @Test
    void theCorrectionMovesThePeriapsisToTheRequestedAltitudeOnTheFarSideForARetrogradeOrbit() {
        double[] clock = new double[1];
        FlightState s = afterTli(15_000, 0.4, clock);
        Correction c = LunarPlanner.correction(s, clock[0], 1_500, true, V3.ZERO, 30_000);
        assertNotNull(c);
        assertEquals(1_500, c.arrival().periapsisAltitude(), 25);
        assertTrue(c.arrival().normalH() < -0.9, "retrógrada: " + c.arrival().normalH());
        assertTrue(c.arrival().bRadial() > 0, "lado de fora da Lua");
        assertTrue(Math.abs(c.arrival().bNormal()) < 200, "no plano");
        // levar o vetor de impacto de ~5.000 a ~70.000 blocos custa ~3 b/t
        assertTrue(c.deltaV().length() > 2.0 && c.deltaV().length() < 4.5, "Δv da correção " + c.deltaV().length());
        // o lado da Terra dá a órbita prógrada
        Correction pro = LunarPlanner.correction(s, clock[0], 1_500, false, V3.ZERO, 30_000);
        assertNotNull(pro);
        assertTrue(pro.arrival().normalH() > 0.9, "prógrada: " + pro.arrival().normalH());
    }

    @Test
    void noArrivalForAnOrbitThatNeverGetsNearTheMoon() {
        assertNull(LunarPlanner.arrival(equatorial(LEO, 0.0), 1_000, 30_000));
    }

    @Test
    void sphereEntryRadiusMatchesTheTracker() {
        assertEquals(91_457.7, MOON.soiEnterRadius(), 1.0);
    }

    @Test
    void theReturnBurnFromALowLunarOrbitCostsAboutEightBlocksPerTickAndHitsTheEarthPeriapsis() {
        // órbita circular prógrada a 1.500 de altitude, no plano da Lua (o equador dela é o plano da órbita: o polo é a normal)
        double clock = 20_000;
        double r = MOON.radius() + 1_500;
        V3 normal = LunarPlanner.moonNormal(clock);
        V3 radial = LunarPlanner.moonPosition(clock).unit();
        V3 tangent = normal.cross(radial).unit();
        FlightState llo = new FlightState(SolarSystem.MOON, radial.times(r), tangent.times(MOON.circularSpeed(r)), 0, 0);
        double period = Orbit.of(llo, clock).period();
        ReturnBurn burn = LunarPlanner.returnBurn(llo, clock, 1_000, period * 1.05);
        assertNotNull(burn);
        assertEquals(1_000, burn.earthPeriapsisAltitude(), 30);
        assertTrue(burn.deltaV() > 6.0 && burn.deltaV() < 11.0, "Δv da volta " + burn.deltaV());
        assertTrue(burn.ticksToBurn() >= 0 && burn.ticksToBurn() <= period * 1.05);
        assertTrue(burn.ticksToExit() > 3_000 && burn.ticksToExit() < 12_000, "saída da esfera em " + burn.ticksToExit());
    }

    @Test
    void theReturnBurnIsNullAroundTheEarth() {
        assertNull(LunarPlanner.returnBurn(equatorial(LEO, 0.0), 1_000, 1_000, 10_000));
    }
}
