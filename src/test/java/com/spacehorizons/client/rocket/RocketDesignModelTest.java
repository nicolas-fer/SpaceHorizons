package com.spacehorizons.client.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.spacehorizons.client.rocket.RocketDesignModel.State;
import com.spacehorizons.rocket.DesignReport;
import com.spacehorizons.rocket.DesignReport.Problem;
import com.spacehorizons.rocket.DesignReport.Type;
import com.spacehorizons.rocket.MissionBudget;
import com.spacehorizons.rocket.MissionBudget.Destination;

/** O que o painel de projeto escreve: linhas, destinos, o que falta e os problemas, sem o jogo. */
class RocketDesignModelTest {
    private static DesignReport report(Destination destination, double deltaV, double twr, List<Problem> problems) {
        Destination next = MissionBudget.next(destination);
        return new DesignReport(10, 64, -3, true, 66, 3, 8, 3, 16.3, 160.3, 180_000.0, 22.5, 1600.0, twr, deltaV, 9, 9, 0, 0, 45, 0, 1, 1, true,
                (byte) destination.ordinal(), next == null ? 0.0 : MissionBudget.required(next), problems);
    }

    @Test
    void asLinhasMostramOsNumerosDoRelatorioEmUnidadesDoPainelDeVoo() {
        DesignReport r = report(Destination.MOON_LANDING_RETURN, 292.6, 1.75, List.of());
        var rows = RocketDesignModel.rows(r);
        assertEquals(9, rows.size());
        assertEquals("66 (3x8x3)", rows.get(0).value());
        assertEquals("16.3 t", rows.get(1).value());
        assertEquals("160.3 t", rows.get(2).value());
        assertEquals("22.5", rows.get(3).value());
        assertEquals("1.75", rows.get(5).value());
        assertEquals(RocketPanelModel.Tone.GOOD, rows.get(5).tone());
        assertEquals("5.852 b/s", rows.get(6).value(), "Δv em b/s (292,6 b/t x 20)");
        assertEquals("9 (9 x 1x1)", rows.get(7).value());
        assertEquals("45 (1 col)", rows.get(8).value());
    }

    @Test
    void twrBaixoFicaAmareloEAbaixoDe1Vermelho() {
        assertEquals(RocketPanelModel.Tone.WARN, RocketDesignModel.rows(report(Destination.SUBORBITAL, 100, 1.1, List.of())).get(5).tone());
        assertEquals(RocketPanelModel.Tone.BAD, RocketDesignModel.rows(report(Destination.NO_LIFTOFF, 100, 0.9, List.of())).get(5).tone());
    }

    @Test
    void destinosMarcamOQueAlcancaEOMelhor() {
        var d = RocketDesignModel.destinations(report(Destination.ORBIT, 100.0, 1.5, List.of()));
        assertEquals(4, d.size());
        assertEquals(State.REACHED, d.get(0).state(), "salto suborbital");
        assertEquals(State.BEST, d.get(1).state(), "órbita");
        assertEquals(State.MISSING, d.get(2).state());
        assertEquals(State.MISSING, d.get(3).state());
        assertEquals("design.spacehorizons.destination.suborbital", d.get(0).nameKey());
        assertEquals("1.050 b/s", d.get(0).required(), "50 b/t com 5% de margem, em b/s");
        assertEquals("design.spacehorizons.destination.moon_landing_return", d.get(3).nameKey());
    }

    @Test
    void naoDecolaNaoAlcancaDestinoNenhumEDiaOMotivo() {
        DesignReport r = report(Destination.NO_LIFTOFF, 10.0, 0.5, List.of());
        assertTrue(RocketDesignModel.destinations(r).stream().allMatch(x -> x.state() == State.MISSING));
        assertEquals("design.spacehorizons.destination.no_liftoff", RocketDesignModel.headlineKey(r));
    }

    @Test
    void falTaParaOProximoDestinoSoQuandoFalta() {
        DesignReport orbit = report(Destination.ORBIT, 100.0, 1.5, List.of());
        String missing = RocketDesignModel.missingForNext(orbit);
        assertEquals(String.format(java.util.Locale.ROOT, "%,.0f b/s", (MissionBudget.required(Destination.MOON_RETURN) - 100.0) * 20.0).replace(',', '.'), missing);
        assertNull(RocketDesignModel.missingForNext(report(Destination.MOON_LANDING_RETURN, 300.0, 1.5, List.of())), "já é o melhor");
        assertNull(RocketDesignModel.missingForNext(DesignReport.nothing(0, 0, 0)));
    }

    @Test
    void problemasTemChaveArgumentosEGravidade() {
        DesignReport r = report(Destination.ORBIT, 100.0, 1.5, List.of(new Problem(Type.TOUCHES_TERRAIN, 4, 5, 6, 0), new Problem(Type.NO_PARACHUTE, 1, 9, 1, 0),
                new Problem(Type.LOW_TWR, 0, 0, 0, 105), new Problem(Type.MULTIPLE_COCKPITS, 2, 3, 4, 2)));
        var p = RocketDesignModel.problems(r);
        assertEquals(4, p.size());
        assertEquals("design.spacehorizons.problem.touches_terrain", p.get(0).key());
        assertTrue(p.get(0).blocking());
        assertEquals(List.of(4, 5, 6), List.of(p.get(0).args()));
        assertEquals("design.spacehorizons.problem.no_parachute", p.get(1).key());
        assertTrue(!p.get(1).blocking());
        assertEquals("1.05", p.get(2).args()[0]);
        assertEquals(2, p.get(3).args()[0]);
    }

    @Test
    void textoDeMotoresETanquesMostraEstruturas() {
        DesignReport r = new DesignReport(0, 0, 0, true, 100, 3, 8, 3, 1, 1, 1, 1, 1, 1.5, 100, 35, 3, 1, 1, 90, 2, 1, 1, true, (byte) 3, 0.0, List.of());
        assertEquals("35 (1 x 3x3x3, 1 x 2x2x2, 3 x 1x1)", RocketDesignModel.enginesText(r));
        assertEquals("90 (1 col, 2 av)", RocketDesignModel.tanksText(r));
    }

    @Test
    void semNadaSobreAPlataformaNaoHaLinhas() {
        DesignReport nothing = DesignReport.nothing(1, 2, 3);
        assertTrue(RocketDesignModel.rows(nothing).isEmpty());
        assertEquals("nada sobre a plataforma", RocketDesignModel.describe(nothing));
        assertTrue(RocketDesignModel.destinations(nothing).stream().allMatch(x -> x.state() == State.MISSING));
    }
}
