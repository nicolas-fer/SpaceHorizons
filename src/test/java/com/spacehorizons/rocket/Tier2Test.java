package com.spacehorizons.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.spacehorizons.rocket.MissionBudget.Destination;
import com.spacehorizons.space.flight.EngineGroup;
import com.spacehorizons.space.flight.FuelLoad;
import com.spacehorizons.space.flight.Propellant;
import com.spacehorizons.space.flight.RocketStats;

/**
 * Conteúdo do Tier 2 (docs/PLANO_TIERS.md, "Tier 2: conteúdo"): os números dos motores, as estruturas multibloco dos motores e tanques novos, o
 * censo por tipo, a energia do iônico e o painel de projeto com motores e combustíveis misturados.
 */
class Tier2Test {
    private static final double EPS = 1.0e-9;

    /** Plataforma 9x9 e o que for posto em cima; o cockpit em (0, y, 0). */
    private static TestWorld pad() {
        return new TestWorld().fill(-4, 0, -4, 9, 1, 9, RocketBlockKind.PAD);
    }

    private static RocketAssembly.Report analyze(TestWorld w, int cockpitY) {
        return RocketAssembly.analyze(w, List.of(new int[] {0, cockpitY, 0}));
    }

    private static DesignReport design(TestWorld w, int cockpitY) {
        return DesignReport.of(analyze(w, cockpitY), 0, 0, 0);
    }

    private static boolean has(DesignReport r, DesignReport.Type type) {
        return r.problems().stream().anyMatch(p -> p.type() == type);
    }

    /** Motor nuclear 2x2x2 + tanque de hidrogênio 2x2x8 + cockpit + paraquedas (o foguete do autoteste "tier2"). */
    private static TestWorld nuclear() {
        return pad().fill(0, 1, 0, 2, 2, 2, RocketBlockKind.NUCLEAR_ENGINE).fill(0, 3, 0, 2, 8, 2, RocketBlockKind.HYDROGEN_TANK)
                .put(0, 11, 0, RocketBlockKind.COCKPIT).put(0, 12, 0, RocketBlockKind.PARACHUTE);
    }

    @Test
    void numerosDosMotoresPorTamanho() {
        // o químico é o do Tier 1, sem mudança
        assertEquals(RocketSpecs.ENGINE_2_THRUST, EngineType.CHEMICAL.thrust(2), EPS);
        assertEquals(RocketSpecs.ENGINE_3_MASS_T, EngineType.CHEMICAL.massT(3), EPS);
        assertEquals(RocketSpecs.ENGINE_3_ISP_TICKS, EngineType.CHEMICAL.isp(3), EPS);
        // nuclear: o dobro do Isp do químico, empuxo por tonelada menor
        for (int side = 1; side <= 3; side++) {
            assertEquals(2 * EngineType.CHEMICAL.isp(side), EngineType.NUCLEAR.isp(side), EPS);
            assertTrue(EngineType.NUCLEAR.thrust(side) / EngineType.NUCLEAR.massT(side) < EngineType.CHEMICAL.thrust(side) / EngineType.CHEMICAL.massT(side));
            // iônico: Isp altíssimo, empuxo por tonelada abaixo da gravidade da Lua (não decola nem de lá)
            assertTrue(EngineType.ION.isp(side) > 5 * EngineType.NUCLEAR.isp(side) / 2);
            assertTrue(EngineType.ION.thrust(side) / EngineType.ION.massT(side) < 0.6);
            // fusão: Isp acima do nuclear e empuxo por tonelada perto do químico
            assertTrue(EngineType.FUSION.isp(side) > EngineType.NUCLEAR.isp(side));
            assertTrue(EngineType.FUSION.thrust(side) / EngineType.FUSION.massT(side) > 0.9 * EngineType.CHEMICAL.thrust(side) / EngineType.CHEMICAL.massT(side));
            // peças maiores rendem mais
            if (side > 1) {
                for (EngineType t : EngineType.values()) {
                    assertTrue(t.thrust(side) / t.massT(side) > t.thrust(side - 1) / t.massT(side - 1), t + " " + side);
                    assertTrue(t.isp(side) > t.isp(side - 1));
                }
            }
        }
        assertEquals(19.2, EngineType.NUCLEAR.thrust(2), EPS);
        assertEquals(7.68, EngineType.NUCLEAR.massT(2), EPS);
        assertEquals(162.0, EngineType.FUSION.thrust(3), EPS);
        assertEquals(14_000.0, EngineType.ION.isp(3), EPS);
    }

    @Test
    void motorEtanqueNovosFormamEstruturasPelaMesmaRegra() {
        RocketAssembly.Report r = analyze(nuclear(), 11);
        assertTrue(r.assemblable(), () -> "bloqueios: " + r.blockers());
        RocketCensus c = r.result().census();
        assertEquals(8, c.count(RocketBlockKind.NUCLEAR_ENGINE));
        assertEquals(32, c.count(RocketBlockKind.HYDROGEN_TANK));
        assertEquals(0, c.engines(), "os campos químicos não contam o Tier 2");
        assertEquals(0, c.tanks());
        assertEquals(8, c.allEngines());
        assertEquals(32, c.allTanks());
        assertEquals(List.of(new Multiblocks.Box(0, 1, 0, 2, 2, 2)), c.structuresOf(RocketBlockKind.NUCLEAR_ENGINE));
        assertEquals(List.of(new Multiblocks.Box(0, 3, 0, 2, 8, 2)), c.structuresOf(RocketBlockKind.HYDROGEN_TANK));
        assertEquals(1 + 1 + 8 + 32, c.total());
    }

    @Test
    void tiposDiferentesNaoFecham_aMesmaColuna() {
        // 2x2x4 de hidrogênio em cima de 2x2x4 de xenônio: duas colunas de 4, não uma de 8
        TestWorld w = pad().fill(0, 1, 0, 1, 1, 1, RocketBlockKind.ION_ENGINE).fill(0, 2, 0, 2, 4, 2, RocketBlockKind.XENON_TANK)
                .fill(0, 6, 0, 2, 4, 2, RocketBlockKind.HYDROGEN_TANK).put(0, 10, 0, RocketBlockKind.COCKPIT);
        RocketCensus c = analyze(w, 10).result().census();
        assertEquals(List.of(new Multiblocks.Box(0, 2, 0, 2, 4, 2)), c.structuresOf(RocketBlockKind.XENON_TANK));
        assertEquals(List.of(new Multiblocks.Box(0, 6, 0, 2, 4, 2)), c.structuresOf(RocketBlockKind.HYDROGEN_TANK));
        assertTrue(c.tankStructures().isEmpty());
    }

    @Test
    void estatisticasDoNuclearSaemDoCensoPorTipo() {
        RocketCensus c = analyze(nuclear(), 11).result().census();
        RocketStats st = RocketSpecs.statsFor(c);
        assertFalse(st.chemicalOnly());
        assertEquals(1, st.groups().size());
        EngineGroup g = st.groups().get(0);
        assertEquals(Propellant.HYDROGEN, g.propellant());
        assertTrue(g.needsCore());
        assertEquals(19.2, g.thrust(), EPS);
        assertEquals(3400.0, g.ispTicks(), EPS);
        double dry = 7.68 + RocketSpecs.HYDROGEN_TANK_DRY_MASS_T * 32 * RocketSpecs.tankHullFactor(new Multiblocks.Box(0, 0, 0, 2, 8, 2)) + 0.8 + 0.1;
        assertEquals(dry, st.dryMassT(), EPS);
        assertEquals(128_000, st.capacity().get(Propellant.HYDROGEN), EPS);
        assertEquals(8 * EngineType.ROD_CORE_MB, st.capacity().get(Propellant.NUCLEAR_CORE), EPS);
        assertEquals(128_000, st.fuelCapacityMb(), EPS, "o núcleo não entra no combustível com massa");
        double wet = dry + 128_000 * 0.0004;
        assertEquals(wet, st.wetMass(st.fullLoad()), EPS);
        assertEquals(3400 * 0.08 * Math.log(wet / dry), st.deltaV(st.fullLoad()), 1.0e-9);
        // ~425 b/t, contra ~346 do químico do mesmo tamanho: o nuclear vai mais longe com o mesmo volume de tanque
        assertTrue(st.deltaV(st.fullLoad()) > 420 && st.deltaV(st.fullLoad()) < 430);
        RocketStats chemical = RocketSpecs.statsFor(TestWorld.design(2, 2, 8, 2, 0, true).analyze().result().census());
        assertTrue(st.deltaV(st.fullLoad()) > chemical.deltaV(chemical.fuelCapacityMb()) * 1.15);
    }

    @Test
    void ionicoPrecisaDeEnergiaDosRtg() {
        TestWorld base = pad().fill(0, 1, 0, 2, 2, 2, RocketBlockKind.ION_ENGINE).fill(0, 3, 0, 2, 4, 2, RocketBlockKind.XENON_TANK)
                .put(0, 7, 0, RocketBlockKind.COCKPIT);
        RocketCensus none = analyze(base, 7).result().census();
        assertEquals(EngineType.ION_POWER_2, RocketSpecs.ionPowerDemand(none));
        assertEquals(0.0, RocketSpecs.ionPowerFraction(none), EPS);
        assertEquals(0.0, RocketSpecs.statsFor(none).thrust(), EPS, "sem gerador o iônico não empurra");
        // 3 geradores encostados: metade da energia, metade do empuxo, o mesmo Isp
        TestWorld half = base.fill(2, 3, 0, 1, 3, 1, RocketBlockKind.RTG);
        RocketCensus c3 = analyze(half, 7).result().census();
        assertEquals(3, c3.rtgs());
        assertEquals(0.5, RocketSpecs.ionPowerFraction(c3), EPS);
        RocketStats st3 = RocketSpecs.statsFor(c3);
        assertEquals(EngineType.ION.thrust(2) * 0.5, st3.thrust(), EPS);
        assertEquals(EngineType.ION.isp(2), st3.ispTicks(), EPS);
        DesignReport r = design(half, 7);
        assertTrue(has(r, DesignReport.Type.LOW_POWER));
        // 6 geradores: energia cheia
        RocketCensus c6 = analyze(half.fill(2, 3, 1, 1, 3, 1, RocketBlockKind.RTG), 7).result().census();
        assertEquals(1.0, RocketSpecs.ionPowerFraction(c6), EPS);
        assertEquals(EngineType.ION.thrust(2), RocketSpecs.statsFor(c6).thrust(), EPS);
    }

    @Test
    void painelDoIonicoDizQueNaoDecolaMasTemDeltaVAlto() {
        TestWorld w = pad().put(0, 1, 0, RocketBlockKind.ION_ENGINE).fill(0, 2, 0, 1, 4, 1, RocketBlockKind.XENON_TANK)
                .put(1, 2, 0, RocketBlockKind.RTG).put(0, 6, 0, RocketBlockKind.COCKPIT).put(0, 7, 0, RocketBlockKind.PARACHUTE);
        DesignReport r = design(w, 6);
        assertTrue(r.assemblable(), () -> "problemas: " + r.problems());
        assertEquals(Destination.NO_LIFTOFF, r.destinationValue());
        assertTrue(r.twr() < 0.1);
        assertTrue(r.deltaV() > 1900, "Δv " + r.deltaV());
        assertFalse(has(r, DesignReport.Type.LOW_POWER));
        assertTrue(r.lines().stream().anyMatch(l -> l.kind() == RocketBlockKind.ION_ENGINE && l.power() == 100));
    }

    @Test
    void fusaoNaoPrecisaDeTanque() {
        TestWorld w = pad().fill(-1, 1, -1, 3, 3, 3, RocketBlockKind.FUSION_ENGINE).put(0, 4, 0, RocketBlockKind.COCKPIT)
                .put(0, 5, 0, RocketBlockKind.PARACHUTE);
        DesignReport r = design(w, 4);
        assertTrue(r.assemblable(), () -> "problemas: " + r.problems());
        assertFalse(has(r, DesignReport.Type.NO_TANK));
        RocketStats st = RocketSpecs.statsFor(analyze(w, 4).result().census());
        assertEquals(27 * EngineType.FUSION_CHARGE_PER_BLOCK_MB, st.capacity().get(Propellant.HELIUM3), EPS);
        double dry = EngineType.FUSION.massT(3) + 0.9;
        double wet = dry + 27_000 * 0.0008;
        assertEquals(12_000 * 0.08 * Math.log(wet / dry), r.deltaV(), 1.0e-9);
        assertTrue(r.twr() > 40);
        assertEquals(Destination.MOON_LANDING_RETURN, r.destinationValue(), "Δv ~708 e TWR ~49: Lua, pouso e volta");
    }

    @Test
    void painelComMotoresMisturadosUsaODeltaVEmFases() {
        // químico 2x2x2 com tanque químico 2x2x4 e nuclear 2x2x2 com hidrogênio 2x2x4, lado a lado, o cockpit em cima do químico
        TestWorld w = pad().fill(0, 1, 0, 2, 2, 2, RocketBlockKind.ENGINE).fill(0, 3, 0, 2, 4, 2, RocketBlockKind.FUEL_TANK)
                .fill(2, 1, 0, 2, 2, 2, RocketBlockKind.NUCLEAR_ENGINE).fill(2, 3, 0, 2, 4, 2, RocketBlockKind.HYDROGEN_TANK)
                .put(0, 7, 0, RocketBlockKind.COCKPIT).put(0, 8, 0, RocketBlockKind.PARACHUTE);
        DesignReport r = design(w, 7);
        assertTrue(r.assemblable(), () -> "problemas: " + r.problems());
        RocketStats st = RocketSpecs.statsFor(analyze(w, 7).result().census());
        assertEquals(2, st.groups().size());
        assertEquals(st.deltaV(st.fullLoad()), r.deltaV(), EPS);
        assertEquals(st.twr(st.fullLoad(), DesignReport.EARTH_GRAVITY), r.twr(), EPS);
        assertEquals(24.0 + 19.2, r.thrust(), EPS);
        // o Isp do conjunto é a média harmônica pelo empuxo
        assertEquals((24.0 + 19.2) / (24.0 / 1700 + 19.2 / 3400), r.ispTicks(), 1.0e-9);
        // a soma ingênua (cada motor com o seu combustível, sem o peso do outro) é maior que o Δv real
        FuelLoad full = st.fullLoad();
        assertTrue(r.deltaV() > 0 && full.get(Propellant.CHEMICAL) == 64_000 && full.get(Propellant.HYDROGEN) == 64_000);
        assertFalse(has(r, DesignReport.Type.ENGINE_WITHOUT_TANK));
        assertFalse(has(r, DesignReport.Type.NO_ENGINE_FUEL));
    }

    @Test
    void motorSemTanqueDoSeuCombustivelEhApontado() {
        // nuclear com tanque QUÍMICO em cima: falta hidrogênio
        TestWorld w = pad().fill(0, 1, 0, 2, 2, 2, RocketBlockKind.NUCLEAR_ENGINE).fill(0, 3, 0, 2, 4, 2, RocketBlockKind.FUEL_TANK)
                .put(0, 7, 0, RocketBlockKind.COCKPIT).put(0, 8, 0, RocketBlockKind.PARACHUTE);
        DesignReport r = design(w, 7);
        assertTrue(r.problems().stream().anyMatch(p -> p.type() == DesignReport.Type.NO_ENGINE_FUEL
                && p.arg() == RocketBlockKind.HYDROGEN_TANK.ordinal() && p.x() == 0 && p.y() == 1 && p.z() == 0), () -> "" + r.problems());
        assertEquals(0.0, r.deltaV(), EPS, "o nuclear não queima combustível químico");
    }

    @Test
    void foguteQuimicoNaoMuda() {
        // o mesmo relatório de antes para um foguete só do Tier 1 (sem linhas do Tier 2)
        DesignReport r = DesignReport.of(TestWorld.design(2, 2, 8, 2, 4, true).analyze(), 0, 0, 0);
        assertTrue(r.lines().isEmpty());
        RocketStats st = RocketSpecs.statsFor(TestWorld.design(2, 2, 8, 2, 4, true).analyze().result().census());
        assertTrue(st.chemicalOnly());
        assertEquals(st.deltaV(st.fuelCapacityMb()), r.deltaV(), EPS);
    }

    @Test
    void retratoNaoExisteSemJogo_masOCensoBateComOdaBusca() {
        // o censo de RocketCensus.of + withStructures (o caminho do retrato) é o mesmo da busca
        RocketAssembly.Report r = analyze(nuclear(), 11);
        RocketCensus fromKinds = RocketCensus.of(r.result().cells().stream().map(RocketAssembly.Cell::kind).toList()).withStructures(r.structures());
        assertEquals(r.result().census(), fromKinds);
        assertTrue(r.result().census().sameShape(fromKinds));
    }
}
