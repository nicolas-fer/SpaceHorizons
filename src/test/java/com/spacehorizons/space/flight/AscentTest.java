package com.spacehorizons.space.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;

/**
 * Prova de viabilidade da missão com os números do desenho: um piloto automático simples (subida vertical, programa de
 * arfagem por altitude, corte quando o apoastro chega ao alvo, espera e circularização) leva foguetes do desenho até a
 * órbita usando só {@link FlightIntegrator}, {@link RocketStats} e {@link Orbit}.
 */
class AscentTest {
    private static final BodyPhysics EARTH = BodyPhysics.of(SolarSystem.EARTH);
    private static final V3 UP = new V3(1, 0, 0);
    /** Leste no spawn da Terra (a Terra não gira no referencial do espaço): +Z. */
    private static final V3 EAST = new V3(0, 0, 1);

    /** Resultado de uma subida. */
    record Ascent(FlightState state, int ticks, double fuelUsedMb, double periapsisAltitude, double apoapsisAltitude,
            double peakHeat, boolean reachedOrbit) {
    }

    static Ascent ascend(RocketStats stats, double fuelMb, double targetAltitude, double pitchStart, double pitchEnd) {
        FlightState s = new FlightState(SolarSystem.EARTH, UP.times(EARTH.radius() + 1.0), V3.ZERO, fuelMb, 0);
        double startFuel = fuelMb;
        double peakHeat = 0;
        int ticks = 0;
        boolean orbit = false;
        // 1. subida com programa de arfagem por altitude até o apoastro chegar ao alvo
        while (ticks < 6_000 && s.fuelMb() > 0) {
            Orbit o = Orbit.of(s, 0);
            if (o.bound() && o.apoapsis() - EARTH.radius() >= targetAltitude) {
                break;
            }
            V3 up = s.r().unit();
            double fraction = Math.max(0.0, Math.min(1.0, (s.altitude() - pitchStart) / (pitchEnd - pitchStart)));
            double theta = Math.toRadians(90.0 * Math.sqrt(fraction));
            V3 east = EAST.minus(up.times(EAST.dot(up))).unit();
            V3 direction = up.times(Math.cos(theta)).plus(east.times(Math.sin(theta)));
            s = FlightIntegrator.step(s, Control.thrust(1.0, direction), stats, ticks);
            peakHeat = Math.max(peakHeat, s.heat());
            ticks++;
        }
        // 2. espera até o apoastro (sem empuxo)
        for (int guard = 0; guard < 20_000; guard++) {
            Orbit o = Orbit.of(s, 0);
            if (!o.bound() || s.verticalSpeed() <= 0.2) {
                break;
            }
            s = FlightIntegrator.step(s, Control.IDLE, stats, ticks);
            ticks++;
        }
        // 3. circulariza no apoastro: queima prógrada até o periastro chegar ao alvo
        while (ticks < 30_000 && s.fuelMb() > 0) {
            Orbit o = Orbit.of(s, 0);
            if (o.periapsis() - EARTH.radius() >= targetAltitude * 0.9) {
                orbit = true;
                break;
            }
            double remaining = Math.max(0.0, EARTH.circularSpeed(s.distance()) - o.speedAt(s.distance()));
            double fullTickDv = stats.thrust() / stats.wetMass(s.fuelMb());
            double throttle = Math.min(1.0, Math.max(0.02, remaining / fullTickDv));
            s = FlightIntegrator.step(s, Control.thrust(throttle, s.v().unit()), stats, ticks);
            ticks++;
        }
        Orbit finalOrbit = Orbit.of(s, 0);
        return new Ascent(s, ticks, startFuel - s.fuelMb(), finalOrbit.periapsis() - EARTH.radius(),
                finalOrbit.apoapsis() - EARTH.radius(), peakHeat, orbit);
    }

    @Test
    void lunarRocketReachesOrbitSpendingAboutOneHundredAndTwelveBlocksPerTick() {
        // 21 tanques + 5 motores (Δv 251): sobe até ~6.000 de altitude gastando o Δv de ~112 (a velocidade circular de ~90 mais
        // as perdas de gravidade e arrasto; com a Terra 10x maior e Isp 1.600 o programa de arfagem vai até 3.000 de altitude)
        RocketStats rocket = RocketStats.of(5, 21, 1, 0, 1, 30, 9.0);
        Ascent a = ascend(rocket, rocket.fuelCapacityMb(), 6_000, 100, 3_000);
        assertTrue(a.reachedOrbit());
        assertTrue(a.periapsisAltitude() > 5_000, "periastro = " + a.periapsisAltitude() + " (acima da altitude de saída)");
        assertTrue(a.apoapsisAltitude() < 8_000, "apoastro = " + a.apoapsisAltitude());
        double spent = rocket.deltaV(rocket.fuelCapacityMb()) - rocket.deltaV(a.state().fuelMb());
        assertTrue(spent > 100 && spent < 125, "Δv gasto = " + spent);
        assertTrue(a.peakHeat() < FlightConstants.HEAT_WARNING, "calor = " + a.peakHeat());
        // a subida leva ~1 minuto (medido: ~1.135 ticks)
        assertTrue(a.ticks() > 800 && a.ticks() < 1_600, "ticks = " + a.ticks());
        // e deixa mais de 100 de Δv para a Lua (medido: ~138)
        assertTrue(rocket.deltaV(a.state().fuelMb()) > 100);
    }

    @Test
    void orbitRocketReachesOrbitWithItsFullTankAndLittleMargin() {
        // 5 tanques + 2 motores (Δv 166): chega à órbita com uma guiagem simples (gasta ~115, sobram ~50)
        RocketStats rocket = RocketStats.of(2, 5, 1, 0, 1, 30, 9.0);
        Ascent a = ascend(rocket, rocket.fuelCapacityMb(), 6_000, 300, 2_500);
        assertTrue(a.reachedOrbit());
        assertTrue(a.periapsisAltitude() > 5_000, "periastro = " + a.periapsisAltitude());
        assertTrue(a.state().fuelMb() >= 0);
        assertTrue(rocket.deltaV(a.state().fuelMb()) < 100, "sobra pouco Δv: " + rocket.deltaV(a.state().fuelMb()));
    }

    @Test
    void minimalRocketReachesSpaceAndOnlyJustReachesOrbitWithAGoodProgram() {
        // o foguete mínimo (Δv 126, Isp 1.600) passa dos 5.000 e só chega à órbita (circular, ~6.000) com um programa de arfagem bom
        // (medido: 111 de Δv gastos, sobram ~14); com os outros programas faz um salto suborbital
        RocketStats rocket = RocketStats.of(1, 1, 0, 0, 1, 3, 1.0);
        Ascent best = ascend(rocket, rocket.fuelCapacityMb(), 6_000, 100, 900);
        assertTrue(best.reachedOrbit(), "programa 100 -> 900");
        assertTrue(rocket.deltaV(best.state().fuelMb()) < 30, "sobra pouco Δv: " + rocket.deltaV(best.state().fuelMb()));
        for (double[] program : new double[][] {{100, 1_500}, {100, 3_000}, {300, 2_500}}) {
            Ascent a = ascend(rocket, rocket.fuelCapacityMb(), 6_000, program[0], program[1]);
            assertEquals(false, a.reachedOrbit(), "programa " + program[0] + " -> " + program[1]);
        }
        // subida vertical: passa da atmosfera (1.800) e do limite da zona de espaço (5.000)
        FlightState s = new FlightState(SolarSystem.EARTH, UP.times(EARTH.radius() + 1), V3.ZERO, rocket.fuelCapacityMb(), 0);
        double peak = 0;
        for (int i = 0; i < 3_000; i++) {
            s = FlightIntegrator.step(s, Control.thrust(1.0, s.r().unit()), rocket, i);
            peak = Math.max(peak, s.altitude());
        }
        assertTrue(peak > 5_000, "apogeu = " + peak);
    }
}
