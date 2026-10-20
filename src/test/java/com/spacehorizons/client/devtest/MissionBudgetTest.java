package com.spacehorizons.client.devtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.spacehorizons.rocket.MissionBudget;
import com.spacehorizons.rocket.MissionBudget.Destination;
import com.spacehorizons.space.flight.RocketStats;

/**
 * O orçamento de Δv dos destinos ({@link MissionBudget}) contra o simulador da missão ({@link MissionSim}, a mesma {@code FlightMachine} do
 * jogo): o painel de projeto nunca pode prometer o que o simulador mostra que o foguete não faz.
 */
class MissionBudgetTest {
    private static final double CLOCK = 68_300;

    private static double spent(RocketStats stats, double fuelFrom, double fuelTo) {
        return stats.exhaustVelocity() * Math.log(stats.wetMass(fuelFrom) / stats.wetMass(fuelTo));
    }

    private static double twrOf(RocketStats s) {
        return s.twr(s.fuelCapacityMb(), 0.08);
    }

    private static Destination classify(RocketStats s) {
        return MissionBudget.reachable(s.deltaV(s.fuelCapacityMb()), twrOf(s), s.parachutes() > 0);
    }

    @Test
    void asFasesSomamOsDestinosEFicamEmOrdem() {
        assertEquals(263.1, MissionBudget.measured(Destination.MOON_LANDING_RETURN), 0.2, "a soma da tabela de docs/MISSAO_LUA.md (263,1 arredondado)");
        assertEquals(78.8, MissionBudget.measured(Destination.ORBIT), 1.0e-9);
        double previous = -1.0;
        for (Destination d : List.of(Destination.SUBORBITAL, Destination.ORBIT, Destination.MOON_RETURN, Destination.MOON_LANDING_RETURN)) {
            assertTrue(MissionBudget.measured(d) > previous, d + " custa mais que o anterior");
            assertEquals(MissionBudget.measured(d) * 1.05, MissionBudget.required(d), 1.0e-9, "a margem é de 5%");
            previous = MissionBudget.measured(d);
        }
        assertEquals(Destination.HOP, MissionBudget.next(Destination.NO_LIFTOFF));
        assertNull(MissionBudget.next(Destination.MOON_LANDING_RETURN));
    }

    @Test
    void osFoguetesDeTesteCaemNoDestinoCerto() {
        assertEquals(Destination.MOON_LANDING_RETURN, classify(MissionSim.ROCKET_M), "o foguete da missão (Δv 292,6) fecha tudo");
        assertEquals(Destination.MOON_RETURN, classify(MissionSim.ROCKET_A), "o foguete A (Δv 253) vai à Lua e volta mas não fecha o pouso lunar");
    }

    @Test
    void empuxoEParaquedasLimitamODestino() {
        // com muito Δv mas TWR <= 1: não decola
        assertEquals(Destination.NO_LIFTOFF, MissionBudget.reachable(500.0, 1.0, true));
        assertEquals(Destination.NO_LIFTOFF, MissionBudget.reachable(500.0, 0.4, true));
        assertEquals(Destination.NO_LIFTOFF, MissionBudget.reachable(0.0, 2.0, true));
        // TWR entre 1 e 1,2: só o salto, por mais Δv que tenha
        assertEquals(Destination.SUBORBITAL, MissionBudget.reachable(500.0, 1.1, true));
        // sem paraquedas a volta da Lua não é prometida (a órbita sim)
        assertEquals(Destination.ORBIT, MissionBudget.reachable(500.0, 2.0, false));
        // pouco Δv: decola mas não passa da atmosfera
        assertEquals(Destination.HOP, MissionBudget.reachable(20.0, 2.0, true));
        // nas bordas da margem
        assertEquals(Destination.ORBIT, MissionBudget.reachable(MissionBudget.required(Destination.ORBIT), 2.0, true));
        assertEquals(Destination.SUBORBITAL, MissionBudget.reachable(MissionBudget.required(Destination.ORBIT) - 0.01, 2.0, true));
    }

    @Test
    void oSimuladorGastaATabelaAteAOrbitaEAtePousarNaLua() {
        // o foguete da missão com os tanques cheios: o Δv gasto até a órbita baixa e até o pouso lunar bate com as somas da tabela
        RocketStats stats = MissionSim.ROCKET_M;
        MissionSim sim = new MissionSim(stats, MissionSim.BOX_M, CLOCK, line -> { });
        double full = stats.fuelCapacityMb();
        sim.runUntil(MissionPilot.Phase.LEO_WAIT, 40_000);
        assertFalse(sim.crashed, sim.crashInfo);
        assertEquals(MissionPilot.Phase.LEO_WAIT, sim.pilot.phase());
        double toOrbit = spent(stats, full, sim.snap.fuelMb());
        assertEquals(MissionBudget.LOW_ORBIT, toOrbit, 6.0, "órbita baixa: o simulador gastou " + toOrbit);
        assertTrue(toOrbit <= MissionBudget.required(Destination.ORBIT), "o orçamento com margem cobre o gasto até a órbita");

        sim.runUntil(MissionPilot.Phase.MOON_LANDED, 120_000);
        assertFalse(sim.crashed, sim.crashInfo);
        double toMoon = spent(stats, full, sim.snap.fuelMb());
        double expected = MissionBudget.LOW_ORBIT + MissionBudget.TRANSFER + MissionBudget.CAPTURE + MissionBudget.LUNAR_LANDING;
        assertEquals(expected, toMoon, 10.0, "até pousar na Lua o simulador gastou " + toMoon);
        assertTrue(toMoon <= expected * MissionBudget.MARGIN);
    }

    @Test
    void oSimuladorFechaALuaEVoltaSemPousarComOFoguetesAeNaoComOPousoLunar() {
        // "Lua e volta": vai à órbita lunar (captura) e volta à Terra, sem pousar na Lua. O piloto de teste não tem esse roteiro pronto: roda a
        // missão até a órbita lunar e pula para a espera da volta (a queima de volta é a mesma planejada pela LunarPlanner)
        for (RocketStats stats : List.of(MissionSim.ROCKET_A, MissionSim.ROCKET_M)) {
            MissionSim sim = new MissionSim(stats, stats == MissionSim.ROCKET_A ? MissionSim.BOX_A : MissionSim.BOX_M, CLOCK, line -> { });
            double full = stats.fuelCapacityMb();
            sim.runUntil(MissionPilot.Phase.LUNAR_ORBIT, 120_000);
            assertFalse(sim.crashed, sim.crashInfo);
            sim.pilot.forcePhase(MissionPilot.Phase.TEI_WAIT);
            sim.run(160_000);
            String why = sim.crashInfo + " " + sim.events + " fase=" + sim.pilot.phase();
            assertFalse(sim.crashed, why);
            assertEquals(MissionPilot.Phase.DONE, sim.pilot.phase(), why);
            double used = spent(stats, full, sim.snap.fuelMb());
            assertEquals(MissionBudget.measured(Destination.MOON_RETURN), used, 12.0, "Lua e volta (sem pousar): o simulador gastou " + used);
            assertTrue(used <= MissionBudget.required(Destination.MOON_RETURN), "o orçamento com margem cobre o gasto: " + used);
        }
    }

    @Test
    void oFogueteAcimaDoLimiteDoDestinoDeFatoChegaEOAbaixoNaoPromete() {
        // a classificação segue o simulador nos dois lados do limite da Lua e volta com pouso: o A não fecha (253 < 276), o M sim (292,6)
        assertTrue(MissionSim.ROCKET_A.deltaV(MissionSim.ROCKET_A.fuelCapacityMb()) < MissionBudget.required(Destination.MOON_LANDING_RETURN));
        assertTrue(MissionSim.ROCKET_M.deltaV(MissionSim.ROCKET_M.fuelCapacityMb()) >= MissionBudget.required(Destination.MOON_LANDING_RETURN));
    }

    // ---------------------------------------------------------------- projetos do painel contra o simulador

    private record Built(RocketStats stats, com.spacehorizons.rocket.RocketCensus census, com.spacehorizons.rocket.FlightMachine.Box box) {
    }

    private static Built build(int engineSide, int tankW, int tankH, int tankD, int hulls, boolean parachute) {
        var report = com.spacehorizons.rocket.TestWorld.design(engineSide, tankW, tankH, tankD, hulls, parachute).analyze();
        var result = report.result();
        RocketStats stats = com.spacehorizons.rocket.RocketSpecs.statsFor(result.census());
        var box = new com.spacehorizons.rocket.FlightMachine.Box(Math.max(result.sizeX(), result.sizeZ()) / 2.0, result.sizeY() / 2.0,
                Math.max(result.sizeX(), result.sizeZ()) / 2.0);
        return new Built(stats, result.census(), box);
    }

    private static Destination promised(Built b) {
        return classify(b.stats());
    }

    @Test
    void projetoQueNaoDecolaFicaNoChaoNoSimulador() {
        Built b = build(1, 3, 10, 3, 0, true);
        assertEquals(Destination.NO_LIFTOFF, promised(b));
        MissionSim sim = new MissionSim(b.stats(), b.box(), CLOCK, line -> { });
        sim.run(3_000);
        assertEquals(com.spacehorizons.rocket.FlightMode.LANDED, sim.snap.mode(), "o empuxo não vence o peso: continua no chão");
        assertTrue(sim.events.isEmpty(), sim.events.toString());
    }

    @Test
    void projetoDeSaltoSuborbitalSobeAcimaDaAtmosferaNoSimulador() {
        Built b = build(1, 1, 8, 1, 0, true);
        assertEquals(Destination.SUBORBITAL, promised(b), "TWR " + twrOf(b.stats()) + " entre 1 e 1,2: só o salto");
        MissionSim sim = new MissionSim(b.stats(), b.box(), CLOCK, line -> { });
        sim.run(25_000);
        assertTrue(sim.events.stream().anyMatch(e -> e.contains("LEAVE_ZONE")), "passou da altitude de saída (5.000): " + sim.events);
    }

    @Test
    void projetoDeOrbitaChegaAOrbitaNoSimulador() {
        Built b = build(2, 1, 4, 1, 0, true);
        assertEquals(Destination.ORBIT, promised(b), "Δv " + b.stats().deltaV(b.stats().fuelCapacityMb()));
        MissionSim sim = new MissionSim(b.stats(), b.box(), CLOCK, line -> { });
        sim.runUntil(MissionPilot.Phase.LEO_WAIT, 40_000);
        assertFalse(sim.crashed, sim.crashInfo);
        assertEquals(MissionPilot.Phase.LEO_WAIT, sim.pilot.phase(), sim.events + " " + sim.pilot.phase());
    }

    @Test
    void projetoDeLuaEVoltaFechaALuaEVoltaNoSimulador() {
        Built b = build(3, 2, 8, 2, 20, true);
        assertEquals(Destination.MOON_RETURN, promised(b), "Δv " + b.stats().deltaV(b.stats().fuelCapacityMb()));
        MissionSim sim = new MissionSim(b.stats(), b.box(), CLOCK, line -> { });
        sim.runUntil(MissionPilot.Phase.LUNAR_ORBIT, 120_000);
        assertFalse(sim.crashed, sim.crashInfo);
        sim.pilot.forcePhase(MissionPilot.Phase.TEI_WAIT);
        sim.run(160_000);
        assertFalse(sim.crashed, sim.crashInfo + " " + sim.events);
        assertEquals(MissionPilot.Phase.DONE, sim.pilot.phase(), sim.events + " " + sim.pilot.phase());
    }

    @Test
    void projetoDeLuaPousoEVoltaFechaAMissaoInteiraNoSimulador() {
        // como o foguete da missão (camada 3x3 de motores avulsos e coluna 3x3x5), mas com a coluna formada (casco mais leve)
        Built b = build(0, 3, 5, 3, 8, true);
        assertEquals(Destination.MOON_LANDING_RETURN, promised(b), "Δv " + b.stats().deltaV(b.stats().fuelCapacityMb()));
        MissionSim sim = new MissionSim(b.stats(), b.box(), CLOCK, line -> { });
        sim.run(160_000);
        assertFalse(sim.crashed, sim.crashInfo + " " + sim.events);
        assertEquals(MissionPilot.Phase.DONE, sim.pilot.phase(), sim.events + " " + sim.pilot.phase());
    }
}
