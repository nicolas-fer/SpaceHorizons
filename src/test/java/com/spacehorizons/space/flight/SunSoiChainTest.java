package com.spacehorizons.space.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.Heliocentric;
import com.spacehorizons.space.SolarSystem;

/** As esferas de influência encadeadas (Sol ⊃ Terra ⊃ Lua, Sol ⊃ Marte) e a órbita heliocêntrica. */
class SunSoiChainTest {
    private static final BodyPhysics EARTH = BodyPhysics.of(SolarSystem.EARTH);
    private static final BodyPhysics MOON = BodyPhysics.of(SolarSystem.MOON);
    private static final BodyPhysics MARS = BodyPhysics.of(SolarSystem.MARS);
    private static final BodyPhysics SUN = BodyPhysics.of(SolarSystem.SUN);

    private static V3 earthH(double clock) {
        double[] p = new double[3];
        Heliocentric.earthPosition(clock, p);
        return V3.of(p);
    }

    private static V3 marsH(double clock) {
        double[] p = new double[3];
        Heliocentric.marsPosition(clock, p);
        return V3.of(p);
    }

    /** Posição e velocidade ABSOLUTAS em H (por tick de jogo, taxa 1) de um estado, em qualquer corpo dominante. */
    private static V3[] absoluteH(FlightState s, double clock) {
        double[] eP = new double[3];
        double[] eV = new double[3];
        Heliocentric.earthPosition(clock, eP);
        Heliocentric.earthVelocity(clock, eV);
        if (s.body() == SolarSystem.SUN) {
            return new V3[] {s.r(), s.v()};
        }
        if (s.body() == SolarSystem.MARS) {
            double[] mP = new double[3];
            double[] mV = new double[3];
            Heliocentric.marsPosition(clock, mP);
            Heliocentric.marsVelocity(clock, mV);
            return new V3[] {s.r().plus(V3.of(mP)), s.v().plus(V3.of(mV))};
        }
        double[] r = new double[3];
        double[] v = new double[3];
        V3 center = s.body() == SolarSystem.MOON ? SoiTracker.position(SolarSystem.MOON, clock) : V3.ZERO;
        V3 centerV = s.body() == SolarSystem.MOON ? SoiTracker.velocity(SolarSystem.MOON, clock, 1.0) : V3.ZERO;
        V3 rr = s.r().plus(center);
        V3 vv = s.v().plus(centerV);
        Heliocentric.fromEarthSystem(clock, rr.x(), rr.y(), rr.z(), r);
        Heliocentric.fromEarthSystem(clock, vv.x(), vv.y(), vv.z(), v);
        return new V3[] {V3.of(eP).plus(V3.of(r)), V3.of(eV).plus(V3.of(v))};
    }

    @Test
    void theSpheresAreNestedAndDoNotOverlap() {
        // a esfera da Lua cabe dentro da da Terra, com folga para a histerese
        assertTrue(EARTH.soiEnterRadius() > SolarSystem.EARTH_MOON_DISTANCE + MOON.soiExitRadius());
        assertEquals(833_852.0, EARTH.soiRadius(), 1.0);
        assertEquals(520_268.0, MARS.soiRadius(), 1.0);
        // no pior caso (oposição) as esferas da Terra e de Marte, com histerese, não se tocam
        double minimumDistance = Heliocentric.MARS_ORBIT_RADIUS - Heliocentric.EARTH_ORBIT_RADIUS;
        assertTrue(EARTH.soiExitRadius() + MARS.soiExitRadius() < minimumDistance);
        // o sol é a esfera de tudo
        assertEquals(Double.POSITIVE_INFINITY, SUN.soiRadius());
    }

    @Test
    void earthToSunAndBackConservesTheHeliocentricStateAndIsIdentity() {
        double clock = 123_456.0;
        V3 dir = new V3(0.3, 0.1, -0.9).unit();
        FlightState inside = new FlightState(SolarSystem.EARTH, dir.times(EARTH.soiExitRadius() - 100), new V3(3, 1, 20), 0, 0);
        assertSame(SolarSystem.EARTH, SoiTracker.update(inside, clock).body());
        FlightState outside = new FlightState(SolarSystem.EARTH, dir.times(EARTH.soiExitRadius() + 100), new V3(3, 1, 20), 0, 0);
        FlightState sun = SoiTracker.update(outside, clock);
        assertSame(SolarSystem.SUN, sun.body());
        V3[] before = absoluteH(outside, clock);
        V3[] after = absoluteH(sun, clock);
        assertEquals(0.0, before[0].distanceTo(after[0]), 1e-6);
        assertEquals(0.0, before[1].distanceTo(after[1]), 1e-9);
        // o raio relativo à Terra é o mesmo: só girou de I a H
        assertEquals(outside.r().length(), sun.r().minus(earthH(clock)).length(), 1e-6);
        // histerese: logo depois de entrar na esfera da Terra pelo lado de fora, o mesmo estado volta à Terra e a ida e volta é a identidade
        V3 backR = sun.r().plus(sun.v().times(0.0));
        FlightState moved = new FlightState(SolarSystem.SUN, earthH(clock).plus(sun.r().minus(earthH(clock)).unit().times(EARTH.soiEnterRadius() - 50)), sun.v(), 0, 0);
        FlightState earth = SoiTracker.update(moved, clock);
        assertSame(SolarSystem.EARTH, earth.body());
        assertEquals(EARTH.soiEnterRadius() - 50, earth.r().length(), 1e-6);
        V3[] a = absoluteH(moved, clock);
        V3[] b = absoluteH(earth, clock);
        assertEquals(0.0, a[0].distanceTo(b[0]), 1e-6);
        assertEquals(0.0, a[1].distanceTo(b[1]), 1e-7);
        assertTrue(backR.length() > 0);
        // entre 95% e 105% da esfera não troca em nenhum sentido
        FlightState between = new FlightState(SolarSystem.SUN, earthH(clock).plus(dir.times(EARTH.soiRadius())), new V3(0, 0, 100), 0, 0);
        assertSame(SolarSystem.SUN, SoiTracker.update(between, clock).body());
    }

    @Test
    void marsAndTheSunShareTheSameAxes() {
        double clock = 77_000.0;
        V3 dir = new V3(-0.5, 0.2, 0.7).unit();
        FlightState outside = new FlightState(SolarSystem.SUN, marsH(clock).plus(dir.times(MARS.soiEnterRadius() - 10)), new V3(1, 2, 3), 0, 0);
        FlightState mars = SoiTracker.update(outside, clock);
        assertSame(SolarSystem.MARS, mars.body());
        assertEquals(MARS.soiEnterRadius() - 10, mars.r().length(), 1e-6);
        V3[] a = absoluteH(outside, clock);
        V3[] b = absoluteH(mars, clock);
        assertEquals(0.0, a[0].distanceTo(b[0]), 1e-6);
        assertEquals(0.0, a[1].distanceTo(b[1]), 1e-7);
        // a saída de Marte (105%) devolve ao sol, e a posição e a velocidade absolutas se conservam
        FlightState leaving = new FlightState(SolarSystem.MARS, dir.times(MARS.soiExitRadius() + 10), new V3(5, 0, -4), 0, 0);
        FlightState sun = SoiTracker.update(leaving, clock);
        assertSame(SolarSystem.SUN, sun.body());
        V3[] c = absoluteH(leaving, clock);
        V3[] d = absoluteH(sun, clock);
        assertEquals(0.0, c[0].distanceTo(d[0]), 1e-6);
        assertEquals(0.0, c[1].distanceTo(d[1]), 1e-7);
    }

    @Test
    void theMoonStillSwitchesInsideTheEarthSphere() {
        double clock = 6_000;
        V3 moon = SoiTracker.position(SolarSystem.MOON, clock);
        FlightState near = new FlightState(SolarSystem.EARTH, moon.plus(new V3(MOON.soiEnterRadius() - 5, 0, 0)), new V3(1, 2, 3), 0, 0);
        assertSame(SolarSystem.MOON, SoiTracker.update(near, clock).body());
        assertSame(SolarSystem.MOON, SoiTracker.dominantAt(moon.plus(new V3(50_000, 0, 0)), clock));
        assertSame(SolarSystem.EARTH, SoiTracker.dominantAt(new V3(20_000, 0, 0), clock));
    }

    @Test
    void dominantAtFindsTheSunAndMarsFarFromTheEarth() {
        double clock = 150_000;
        assertSame(SolarSystem.SUN, SoiTracker.dominantAt(new V3(2_000_000, 0, 0), clock));
        double[] mars = new double[3];
        Heliocentric.marsInEarthSystem(clock, mars);
        assertSame(SolarSystem.MARS, SoiTracker.dominantAt(V3.of(mars).plus(new V3(100_000, 0, 0)), clock));
        assertSame(SolarSystem.EARTH, SoiTracker.dominantAt(new V3(700_000, 0, 0).plus(new V3(0, 0, 1)), clock));
    }

    @Test
    void aHeliocentricCircularOrbitStaysCircularAndKeepsItsPeriod() {
        // a Terra no raio dela com a velocidade circular: o integrador (velocity-Verlet, 1 tick) fecha a volta em um ano, com o raio e a energia constantes
        RocketStats stats = RocketStats.of(1, 1, 1, 0, 0, 0, 1.0);
        double r0 = Heliocentric.EARTH_ORBIT_RADIUS;
        FlightState s = new FlightState(SolarSystem.SUN, new V3(r0, 0, 0), new V3(0, 0, Heliocentric.earthSpeed()), 0, 0);
        double energy0 = 0.5 * s.v().lengthSquared() - Heliocentric.MU_SUN / r0;
        double worstRadius = 0.0;
        int ticks = (int) Heliocentric.EARTH_PERIOD_TICKS;
        for (int i = 0; i < ticks; i++) {
            s = FlightIntegrator.step(s, new Control(0.0, new V3(0, 1, 0), false), stats, i);
            worstRadius = Math.max(worstRadius, Math.abs(s.r().length() - r0));
        }
        assertTrue(worstRadius < 1.0, "desvio máximo do raio = " + worstRadius);
        double energy = 0.5 * s.v().lengthSquared() - Heliocentric.MU_SUN / s.r().length();
        assertEquals(energy0, energy, Math.abs(energy0) * 1e-9);
        // depois de um ano está de volta ao ponto de partida (erro de fase de poucos blocos em 240.000 ticks)
        assertTrue(s.r().distanceTo(new V3(r0, 0, 0)) < 500.0, "distância ao ponto de partida = " + s.r().distanceTo(new V3(r0, 0, 0)));
    }

    @Test
    void theHohmannTransferReachesTheMartianOrbit() {
        double mu = Heliocentric.MU_SUN;
        double r1 = Heliocentric.EARTH_ORBIT_RADIUS;
        double r2 = Heliocentric.MARS_ORBIT_RADIUS;
        V3 r = new V3(r1, 0, 0);
        V3 v = new V3(0, 0, Heliocentric.earthSpeed() + Hohmann.dv1(mu, r1, r2));
        double time = Hohmann.flightTime(mu, r1, r2);
        Kepler.State arrival = Kepler.propagate(mu, r, v, time);
        assertEquals(r2, arrival.r().length(), 1.0);
        // chega devagar o bastante para Marte (84,8) alcançar: o Δv de circularização é o de Hohmann
        assertEquals(Hohmann.dv2(mu, r1, r2), Math.sqrt(mu / r2) - arrival.v().length(), 1e-6);
        // e o integrador do jogo concorda com Kepler
        RocketStats stats = RocketStats.of(1, 1, 1, 0, 0, 0, 1.0);
        FlightState s = new FlightState(SolarSystem.SUN, r, v, 0, 0);
        int ticks = (int) Math.round(time);
        for (int i = 0; i < ticks; i++) {
            s = FlightIntegrator.step(s, new Control(0.0, new V3(0, 1, 0), false), stats, i);
        }
        Kepler.State exact = Kepler.propagate(mu, r, v, ticks);
        assertEquals(0.0, s.r().distanceTo(exact.r()), 50.0);
    }

    @Test
    void marsAtmosphereIsThinForTheParachute() {
        // a densidade de Marte no chão é uma fração da da Terra, e o paraquedas ainda dá um pouso suave com a gravidade dele
        assertEquals(0.15, MARS.surfaceDensity(), 1e-12);
        assertEquals(1.0, EARTH.surfaceDensity(), 1e-12);
        assertEquals(0.15, MARS.dragDensity(0.0), 1e-12);
        assertEquals(0.0, MARS.dragDensity(1_100.0), 1e-12);
        assertEquals(0.0, MOON.dragDensity(0.0), 1e-12);
        double terminal = Math.sqrt(MARS.surfaceGravity() / (FlightConstants.G0 / (FlightConstants.PARACHUTE_TERMINAL_SPEED * FlightConstants.PARACHUTE_TERMINAL_SPEED)
                * MARS.dragDensity(0.0)));
        assertTrue(terminal < FlightConstants.LANDING_MAX_VERTICAL, "velocidade terminal do paraquedas em Marte = " + terminal);
        // no ar da Terra o mesmo paraquedas é mais lento que em Marte
        assertTrue(terminal > FlightConstants.PARACHUTE_TERMINAL_SPEED);
    }
}
