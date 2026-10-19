package com.spacehorizons.space.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;

class SoiTrackerTest {
    private static final BodyPhysics MOON = BodyPhysics.of(SolarSystem.MOON);
    private static final double[] CLOCKS = {0, 3_000, 7_000, 18_000, 40_000.5, 123_456};

    private static V3 moonPos(double clock) {
        return SoiTracker.position(SolarSystem.MOON, clock);
    }

    private static V3 moonVel(double clock) {
        return SoiTracker.velocity(SolarSystem.MOON, clock, 1.0);
    }

    /** Estado da Terra à distância {@code d} do centro da Lua, numa direção qualquer. */
    private static FlightState earthStateAt(double clock, double d, V3 v) {
        V3 dir = new V3(-0.6, 0.3, 0.5).unit();
        return new FlightState(SolarSystem.EARTH, moonPos(clock).plus(dir.times(d)), v, 1_234, 0.4, 0.5);
    }

    @Test
    void entersTheMoonAtNinetyFivePercentOfTheSphere() {
        double clock = 7_000;
        FlightState outside = earthStateAt(clock, MOON.soiEnterRadius() + 1, new V3(1, 2, 3));
        assertSame(SolarSystem.EARTH, SoiTracker.update(outside, clock).body());
        FlightState inside = earthStateAt(clock, MOON.soiEnterRadius() - 1, new V3(1, 2, 3));
        FlightState moon = SoiTracker.update(inside, clock);
        assertSame(SolarSystem.MOON, moon.body());
        assertEquals(MOON.soiEnterRadius() - 1, moon.r().length(), 1e-6);
        // entre 95% e 100% do raio de Laplace a Terra ainda é a dominante de quem vem de fora
        FlightState between = earthStateAt(clock, MOON.soiRadius(), new V3(1, 2, 3));
        assertSame(SolarSystem.EARTH, SoiTracker.update(between, clock).body());
    }

    @Test
    void leavesTheMoonAtOneHundredFivePercent() {
        double clock = 7_000;
        V3 dir = new V3(0.2, -0.9, 0.4).unit();
        FlightState inside = new FlightState(SolarSystem.MOON, dir.times(MOON.soiExitRadius() - 1), new V3(0, 1, 0), 0, 0);
        assertSame(SolarSystem.MOON, SoiTracker.update(inside, clock).body());
        FlightState beyond = new FlightState(SolarSystem.MOON, dir.times(MOON.soiExitRadius() + 1), new V3(0, 1, 0), 0, 0);
        FlightState earth = SoiTracker.update(beyond, clock);
        assertSame(SolarSystem.EARTH, earth.body());
        assertTrue(earth.r().distanceTo(moonPos(clock).plus(beyond.r())) < 1e-9);
    }

    @Test
    void conversionConservesTheAbsoluteStateAndRoundTripsExactly() {
        for (double clock : CLOCKS) {
            V3 vel = new V3(1.5, -2.5, 7);
            FlightState earth = earthStateAt(clock, 8_000, vel);
            FlightState moon = SoiTracker.update(earth, clock);
            assertSame(SolarSystem.MOON, moon.body());
            // posição e velocidade absolutas iguais (relativo ao corpo + o corpo)
            assertEquals(0.0, moon.r().plus(moonPos(clock)).distanceTo(earth.r()), 1e-9);
            assertEquals(0.0, moon.v().plus(moonVel(clock)).distanceTo(earth.v()), 1e-12);
            // o combustível, o calor e o paraquedas atravessam a troca
            assertEquals(1_234, moon.fuelMb(), 0.0);
            assertEquals(0.4, moon.heat(), 0.0);
            assertEquals(0.5, moon.chute(), 0.0);
            // ida e volta: sai a 105% e reentra na Terra com o estado original
            FlightState pushed = moon.withRV(moon.r().unit().times(MOON.soiExitRadius() + 5), moon.v());
            FlightState back = SoiTracker.update(pushed, clock);
            assertSame(SolarSystem.EARTH, back.body());
            assertEquals(0.0, back.r().distanceTo(pushed.r().plus(moonPos(clock))), 1e-9);
            assertEquals(0.0, back.v().distanceTo(pushed.v().plus(moonVel(clock))), 1e-12);
        }
    }

    @Test
    void exactRoundTripOfTheSameStateAtTheSameClock() {
        double clock = 7_000;
        FlightState earth = earthStateAt(clock, 8_000, new V3(1, 2, 3));
        FlightState moon = SoiTracker.update(earth, clock);
        // volta pela conta manual: mesma origem e mesma velocidade, erro de arredondamento apenas
        V3 r = moon.r().plus(moonPos(clock));
        V3 v = moon.v().plus(moonVel(clock));
        assertEquals(0.0, r.distanceTo(earth.r()), 1e-9);
        assertEquals(0.0, v.distanceTo(earth.v()), 1e-12);
    }

    @Test
    void hysteresisAvoidsDoubleSwitchingAtTheBoundary() {
        // distâncias à Lua visitadas em sequência: entra a < 91.457 e só sai a > 101.085
        double clock = 7_000;
        double[] distances = {96_000, 93_000, 90_000, 99_000, 92_000, 100_500, 102_000, 97_000, 100_500, 91_000};
        SolarSystem.Body[] expected = {SolarSystem.EARTH, SolarSystem.EARTH, SolarSystem.MOON, SolarSystem.MOON,
                SolarSystem.MOON, SolarSystem.MOON, SolarSystem.EARTH, SolarSystem.EARTH, SolarSystem.EARTH,
                SolarSystem.MOON};
        V3 dir = new V3(-0.6, 0.3, 0.5).unit();
        FlightState s = earthStateAt(clock, 200_000, V3.ZERO);
        int switches = 0;
        for (int i = 0; i < distances.length; i++) {
            SolarSystem.Body before = s.body();
            V3 relative = dir.times(distances[i]);
            FlightState moved = before == SolarSystem.EARTH ? s.withRV(moonPos(clock).plus(relative), s.v())
                    : s.withRV(relative, s.v());
            s = SoiTracker.update(moved, clock);
            assertSame(expected[i], s.body(), "distância " + distances[i]);
            if (s.body() != before) {
                switches++;
            }
        }
        assertEquals(3, switches);
    }

    @Test
    void clockRateScalesTheMoonVelocity() {
        double clock = 7_000;
        FlightState earth = earthStateAt(clock, 8_000, new V3(1, 2, 3));
        FlightState paused = SoiTracker.update(earth, clock, 0.0);
        assertEquals(0.0, paused.v().distanceTo(earth.v()), 0.0);
        FlightState normal = SoiTracker.update(earth, clock, 1.0);
        FlightState fast = SoiTracker.update(earth, clock, 2.0);
        assertEquals(0.0, earth.v().minus(normal.v()).times(2.0).distanceTo(earth.v().minus(fast.v())), 1e-12);
    }

    @Test
    void dominantBodyOfAnAbsolutePoint() {
        double clock = 9_000;
        assertSame(SolarSystem.EARTH, SoiTracker.dominantAt(new V3(20_000, 0, 0), clock));
        assertSame(SolarSystem.MOON, SoiTracker.dominantAt(moonPos(clock).plus(new V3(50_000, 0, 0)), clock));
        assertSame(SolarSystem.EARTH, SoiTracker.dominantAt(moonPos(clock).plus(new V3(110_000, 0, 0)), clock));
    }

    @Test
    void earthIsNeverLeftByItself() {
        // longe da Lua, a Terra continua dominante mesmo a distâncias enormes
        FlightState far = new FlightState(SolarSystem.EARTH, new V3(-500_000, 0, 0), V3.ZERO, 0, 0);
        assertSame(SolarSystem.EARTH, SoiTracker.update(far, 5_000).body());
    }
}
