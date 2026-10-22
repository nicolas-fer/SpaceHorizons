package com.spacehorizons.space.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;

/** Combustíveis por tipo (Tier 2): a carga, o Δv em fases com motores misturados e a queima tick a tick no integrador. */
class MixedFuelTest {
    private static final double EPS = 1.0e-9;

    private static EngineGroup chemical(double thrust, double isp) {
        return new EngineGroup("chemical", Propellant.CHEMICAL, false, thrust, isp, 1);
    }

    private static EngineGroup nuclear(double thrust, double isp) {
        return new EngineGroup("nuclear", Propellant.HYDROGEN, true, thrust, isp, 1);
    }

    private static RocketStats stats(double dry, FuelLoad capacity, EngineGroup... groups) {
        return RocketStats.of(dry, List.of(groups), capacity, 0.0, 0.0, groups.length, 1, 0, 0);
    }

    @Test
    void cargaSomaMassaPorDensidadeEONucleoNaoPesa() {
        FuelLoad f = FuelLoad.chemical(1000).with(Propellant.HYDROGEN, 2000).with(Propellant.XENON, 500).with(Propellant.NUCLEAR_CORE, 99_999);
        assertEquals(1000 * 0.0008 + 2000 * 0.0004 + 500 * 0.0012, f.massT(), EPS);
        assertEquals(3500, f.propellantMb(), EPS);
        assertFalse(f.onlyChemical());
        assertTrue(FuelLoad.chemical(5).onlyChemical());
        assertEquals(FuelLoad.chemical(5), FuelLoad.EMPTY.with(Propellant.CHEMICAL, 5));
        assertNotEquals(FuelLoad.chemical(5), FuelLoad.of(Propellant.HYDROGEN, 5));
        assertEquals(0.0, FuelLoad.chemical(-3).get(Propellant.CHEMICAL), EPS, "negativo vira zero");
        FuelLoad cap = FuelLoad.chemical(100).with(Propellant.HYDROGEN, 10);
        assertEquals(FuelLoad.chemical(100).with(Propellant.HYDROGEN, 10), f.clampTo(cap));
    }

    @Test
    void foguteQuimicoTemOMesmoDeltaVNasDuasContas() {
        RocketStats old = new RocketStats(10.0, 24.0, 1700.0, 128_000, 0.0, 0.0, 8, 32, 0, 0);
        assertTrue(old.chemicalOnly());
        assertEquals(1, old.groups().size());
        for (double fuel : new double[] {0, 1000, 64_000, 128_000}) {
            assertEquals(old.deltaV(fuel), old.deltaV(FuelLoad.chemical(fuel)), 1.0e-9 * Math.max(1, old.deltaV(fuel)));
            assertEquals(old.wetMass(fuel), old.wetMass(FuelLoad.chemical(fuel)), EPS);
            if (fuel > 0) {
                assertEquals(old.twr(fuel, 0.08), old.twr(FuelLoad.chemical(fuel), 0.08), 1.0e-12);
            }
        }
        // com os tanques vazios o TWR "ativo" é zero (nenhum motor tem o que queimar); o de antes conta o empuxo mesmo sem combustível
        assertEquals(0.0, old.twr(FuelLoad.EMPTY, 0.08), EPS);
    }

    @Test
    void doisMotoresComCombustiveisSeparadosQueimamEmFases() {
        // químico (F 24, Isp 1700) e nuclear (F 19,2, Isp 3400) juntos; o hidrogênio acaba depois do químico
        RocketStats st = stats(12.0, FuelLoad.chemical(10_000).with(Propellant.HYDROGEN, 40_000).with(Propellant.NUCLEAR_CORE, 1e9),
                chemical(24.0, 1700.0), nuclear(19.2, 3400.0));
        FuelLoad full = st.fullLoad();
        double ve1 = 1700 * 0.08;
        double ve2 = 3400 * 0.08;
        double flow1 = 24.0 / ve1;
        double flow2 = 19.2 / ve2;
        // fase 1: os dois até o químico acabar
        double chemT = 10_000 * 0.0008;
        double h2T = 40_000 * 0.0004;
        double t1 = chemT / flow1;
        double m0 = 12.0 + chemT + h2T;
        double m1 = m0 - (flow1 + flow2) * t1;
        double dv1 = (24.0 + 19.2) / (flow1 + flow2) * Math.log(m0 / m1);
        // fase 2: só o nuclear, com o hidrogênio que sobrou
        double h2Left = h2T - flow2 * t1;
        double dv2 = ve2 * Math.log(m1 / (m1 - h2Left));
        assertTrue(t1 < h2T / flow2, "o químico acaba primeiro neste caso");
        assertEquals(dv1 + dv2, st.deltaV(full), 1.0e-9);
        // menos que a soma ingênua, mais que só o químico
        assertTrue(st.deltaV(full) > ve1 * Math.log(m0 / (m0 - chemT)));
    }

    @Test
    void nucleoLimitaOHidrogenioQueSeQueima() {
        RocketStats st = stats(10.0, FuelLoad.of(Propellant.HYDROGEN, 40_000).with(Propellant.NUCLEAR_CORE, 40_000), nuclear(19.2, 3400.0));
        double ve = 3400 * 0.08;
        FuelLoad halfCore = FuelLoad.of(Propellant.HYDROGEN, 40_000).with(Propellant.NUCLEAR_CORE, 10_000);
        double m0 = 10.0 + 16.0;
        // só 10.000 mB de hidrogênio (4 t) são aquecidos; o resto é peso morto
        assertEquals(ve * Math.log(m0 / (m0 - 4.0)), st.deltaV(halfCore), 1.0e-9);
        assertEquals(0.0, st.deltaV(FuelLoad.of(Propellant.HYDROGEN, 40_000)), EPS, "sem núcleo o nuclear não empurra");
        assertEquals(0.0, st.activeThrust(FuelLoad.of(Propellant.HYDROGEN, 40_000)), EPS);
        assertEquals(19.2, st.activeThrust(halfCore), EPS);
    }

    @Test
    void queimaTickATickBateComODeltaVEmFases() {
        RocketStats st = stats(12.0, FuelLoad.chemical(6_000).with(Propellant.HYDROGEN, 20_000).with(Propellant.NUCLEAR_CORE, 15_000),
                chemical(24.0, 1700.0), nuclear(19.2, 3400.0));
        FuelLoad fuel = st.fullLoad();
        double expected = st.deltaV(fuel);
        double sum = 0.0;
        int ticks = 0;
        while (st.activeThrust(fuel) > 0.0 && ticks < 100_000) {
            FlightIntegrator.Burn b = FlightIntegrator.burn(fuel, st, 1.0);
            sum += b.dv();
            fuel = b.fuel();
            ticks++;
        }
        // o tick em que um combustível acaba mistura as duas fases (Δv com a exaustão média do tick): erro relativo ~1e-5
        assertEquals(expected, sum, expected * 2.0e-5, "a soma dos Δv dos ticks é o Δv em fases");
        assertEquals(0.0, fuel.get(Propellant.CHEMICAL), EPS);
        assertEquals(0.0, fuel.get(Propellant.NUCLEAR_CORE), EPS, "o núcleo acabou antes do hidrogênio");
        assertEquals(5_000, fuel.get(Propellant.HYDROGEN), 1.0e-6, "sobra o hidrogênio que o núcleo não aquece");
    }

    @Test
    void integradorComMotoresMisturadosGastaCadaCombustivelNoSeuRitmo() {
        RocketStats st = stats(12.0, FuelLoad.chemical(10_000).with(Propellant.XENON, 10_000),
                chemical(2.5, 1600.0), new EngineGroup("ion", Propellant.XENON, false, 0.1, 12_000.0, 1));
        V3 r = new V3(SolarSystem.EARTH.planet().radius() * 50, 0, 0);
        FlightState s = new FlightState(SolarSystem.EARTH, r, V3.ZERO, st.fullLoad(), 0.0, 0.0);
        Control c = new Control(1.0, new V3(0, 1, 0), false);
        FlightState next = FlightIntegrator.step(s, c, st, 0.0);
        double chemUsed = 10_000 - next.fuel().get(Propellant.CHEMICAL);
        double xeUsed = 10_000 - next.fuel().get(Propellant.XENON);
        assertEquals(2.5 / (1600 * 0.08) / 0.0008, chemUsed, 1.0e-9);
        assertEquals(0.1 / (12_000 * 0.08) / 0.0012, xeUsed, 1.0e-9);
        assertTrue(next.v().y() > 0.0);
    }
}
