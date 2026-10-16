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
    void lunarRocketReachesOrbitSpendingAboutFortyFourBlocksPerTick() {
        // 21 tanques + 5 motores (Δv 78): sobe até ~3.000 de altitude gastando o Δv de ~44 do planejamento (43,5 a 46)
        RocketStats rocket = RocketStats.of(5, 21, 1, 0, 1, 30, 9.0);
        Ascent a = ascend(rocket, rocket.fuelCapacityMb(), 3_000, 100, 1_500);
        assertTrue(a.reachedOrbit());
        assertTrue(a.periapsisAltitude() > 1_800, "periastro = " + a.periapsisAltitude() + " (fora da atmosfera)");
        assertTrue(a.apoapsisAltitude() < 8_000, "apoastro = " + a.apoapsisAltitude());
        double spent = rocket.deltaV(rocket.fuelCapacityMb()) - rocket.deltaV(a.state().fuelMb());
        assertTrue(spent > 40 && spent < 50, "Δv gasto = " + spent);
        assertTrue(a.peakHeat() < FlightConstants.HEAT_WARNING, "calor = " + a.peakHeat());
        // a subida leva ~25 segundos
        assertTrue(a.ticks() > 300 && a.ticks() < 800, "ticks = " + a.ticks());
        // e deixa mais de 30 de Δv para a Lua (o planejamento pede 66 a 82 no total)
        assertTrue(rocket.deltaV(a.state().fuelMb()) > 30);
    }

    @Test
    void orbitRocketJustReachesOrbitWithItsFullTank() {
        // 5 tanques + 2 motores (Δv 52): chega à órbita com uma guiagem simples, mas sem margem (o planejado é 48)
        RocketStats rocket = RocketStats.of(2, 5, 1, 0, 1, 30, 9.0);
        Ascent a = ascend(rocket, rocket.fuelCapacityMb(), 3_000, 100, 900);
        assertTrue(a.reachedOrbit());
        assertTrue(a.periapsisAltitude() > 1_800, "periastro = " + a.periapsisAltitude());
        assertTrue(a.state().fuelMb() >= 0);
    }

    @Test
    void minimalRocketCannotReachOrbitButReachesSpace() {
        // o foguete mínimo (Δv 39,5) faz um salto suborbital: passa dos 5.000 e cai de volta
        RocketStats rocket = RocketStats.of(1, 1, 0, 0, 1, 3, 1.0);
        for (double[] program : new double[][] {{100, 700}, {100, 1_500}, {300, 2_500}}) {
            Ascent a = ascend(rocket, rocket.fuelCapacityMb(), 3_000, program[0], program[1]);
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
