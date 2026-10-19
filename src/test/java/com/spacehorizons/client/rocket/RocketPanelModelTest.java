package com.spacehorizons.client.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.spacehorizons.client.rocket.RocketPanelModel.Row;
import com.spacehorizons.client.rocket.RocketPanelModel.Tone;
import com.spacehorizons.client.rocket.RocketPanelModel.Warning;
import com.spacehorizons.rocket.RocketTelemetryPayload;

/** Formatação do painel: unidades em b/s, "—" para o que não existe, avisos e planejador. */
class RocketPanelModelTest {
    private static RocketTelemetryPayload sample(double alt, double vv, double apo, double peri, byte planner, boolean burnNow, boolean held,
            float heat, float chute) {
        return new RocketTelemetryPayload("earth", true, alt, vv, 0.5, 3.0, apo, peri, 28.5, 1234.0, 1500.0, 4000.0, 2.5, 1.4, 0.8F, (byte) 1,
                (byte) 1, heat, chute, planner, 73.0F, 114.0F, 1.55F, burnNow, held, (byte) 0, (byte) 0);
    }

    private static RocketTelemetryPayload plain() {
        return sample(1.0, 0.0, 1.0, 1.0, (byte) -1, false, false, 0F, 0F);
    }

    @Test
    void velocidadeVaiDeBlocosPorTickParaBlocosPorSegundo() {
        assertEquals("20.0 b/s", RocketPanelModel.speed(1.0));
        assertEquals("-10.0 b/s", RocketPanelModel.speed(-0.5));
        assertEquals("0.0 b/s", RocketPanelModel.speed(0.0));
        assertEquals("120 b/s", RocketPanelModel.speed(6.0));
        assertEquals(RocketPanelModel.NONE, RocketPanelModel.speed(Double.NaN));
    }

    @Test
    void distanciaCompactaAcimaDeDezMil() {
        assertEquals("842 b", RocketPanelModel.distance(842.4));
        assertEquals("9999 b", RocketPanelModel.distance(9999.0));
        assertEquals("12.3k b", RocketPanelModel.distance(12345.0));
        assertEquals("250k b", RocketPanelModel.distance(250000.0));
        assertEquals(RocketPanelModel.NONE, RocketPanelModel.distance(Double.POSITIVE_INFINITY));
        assertEquals(RocketPanelModel.NONE, RocketPanelModel.distance(Double.NaN));
    }

    @Test
    void tempoEmMinutosESegundos() {
        assertEquals("0:00", RocketPanelModel.time(0.0));
        assertEquals("1:00", RocketPanelModel.time(1200.0));
        assertEquals("2:03", RocketPanelModel.time(2460.0));
        assertEquals("1:01:01", RocketPanelModel.time(20.0 * 3661.0));
        assertEquals(RocketPanelModel.NONE, RocketPanelModel.time(Double.NaN));
        assertEquals(RocketPanelModel.NONE, RocketPanelModel.time(-5.0));
    }

    @Test
    void combustivelEAceleradorEmTexto() {
        assertEquals("1234 / 4000 mB", RocketPanelModel.fuel(1234.4, 4000.0));
        assertEquals("0 / 0 mB", RocketPanelModel.fuel(-3.0, 0.0));
        assertEquals("55%", RocketPanelModel.percent(0.55));
        assertEquals("100%", RocketPanelModel.percent(1.7));
        assertEquals(0.25F, RocketPanelModel.fuelFraction(1000.0, 4000.0), 1.0E-6F);
        assertEquals(0.0F, RocketPanelModel.fuelFraction(10.0, 0.0));
        assertEquals(1.0F, RocketPanelModel.fuelFraction(9000.0, 4000.0));
    }

    @Test
    void linhasDeVooComOrbitaAberta() {
        List<Row> rows = RocketPanelModel.flightRows(sample(842.0, 3.0, Double.POSITIVE_INFINITY, -200.0, (byte) -1, false, false, 0F, 0F));
        assertEquals(8, rows.size());
        assertEquals("hud.spacehorizons.rocket.altitude", rows.get(0).labelKey());
        assertEquals("842 b", rows.get(0).value());
        assertEquals("60.0 b/s", rows.get(1).value());
        assertEquals(RocketPanelModel.NONE, rows.get(4).value(), "apoastro de órbita aberta é —");
        assertEquals("-200 b", rows.get(5).value());
        assertEquals(Tone.NORMAL, rows.get(5).tone(), "periastro abaixo do chão só avisa se a órbita é fechada");
        assertEquals("28.5°", rows.get(6).value());
        assertEquals("1:02", rows.get(7).value());
    }

    @Test
    void periastroNoChaoComOrbitaFechadaAvisa() {
        List<Row> rows = RocketPanelModel.flightRows(sample(5000.0, 1.0, 9000.0, -300.0, (byte) -1, false, false, 0F, 0F));
        assertEquals(Tone.WARN, rows.get(5).tone());
    }

    @Test
    void descendoRapidoPertoDoChaoFicaVermelho() {
        assertEquals(Tone.BAD, RocketPanelModel.flightRows(sample(100.0, -3.0, 1.0, 1.0, (byte) -1, false, false, 0F, 0F)).get(1).tone());
        assertEquals(Tone.WARN, RocketPanelModel.flightRows(sample(100.0, -1.5, 1.0, 1.0, (byte) -1, false, false, 0F, 0F)).get(1).tone());
        assertEquals(Tone.NORMAL, RocketPanelModel.flightRows(sample(4000.0, -3.0, 1.0, 1.0, (byte) -1, false, false, 0F, 0F)).get(1).tone());
    }

    @Test
    void semTelemetriaTodasAsLinhasMostramTraco() {
        List<Row> rows = RocketPanelModel.flightRows(null);
        assertEquals(8, rows.size());
        assertTrue(rows.stream().allMatch(r -> r.value().equals(RocketPanelModel.NONE)));
        assertEquals(RocketPanelModel.NONE, RocketPanelModel.deltaV(null));
        assertEquals(RocketPanelModel.NONE, RocketPanelModel.twr(null));
    }

    @Test
    void deltaVETwr() {
        assertEquals("50.0 b/s", RocketPanelModel.deltaV(plain()));
        assertEquals("1.40", RocketPanelModel.twr(plain()));
    }

    @Test
    void avisosEmOrdemDeImportancia() {
        RocketTelemetryPayload t = sample(1.0, 0.0, 1.0, 1.0, (byte) 0, true, true, 0.9F, 0.5F);
        List<Warning> w = RocketPanelModel.warnings(t, 0.9F);
        assertEquals(List.of("hud.spacehorizons.rocket.burn_now", "hud.spacehorizons.rocket.heat", "hud.spacehorizons.rocket.waiting_terrain",
                "hud.spacehorizons.rocket.parachute"), w.stream().map(Warning::key).toList());
        assertEquals("50%", w.get(3).args()[0]);
    }

    @Test
    void calorSoAvisaAcimaDeSetentaPorCento() {
        assertTrue(RocketPanelModel.warnings(plain(), 0.7F).isEmpty(), "0,7 exato ainda não avisa");
        assertEquals(1, RocketPanelModel.warnings(plain(), 0.71F).size());
        assertEquals(Tone.BAD, RocketPanelModel.warnings(plain(), 1.5F).get(0).tone());
        assertTrue(RocketPanelModel.warnings(null, 0.0F).isEmpty());
        assertEquals(1, RocketPanelModel.warnings(null, 0.9F).size(), "o calor vem do dado sincronizado e vale sem telemetria");
    }

    @Test
    void planejadorComPlanoValido() {
        List<Row> rows = RocketPanelModel.plannerRows(sample(1.0, 0.0, 1.0, 1.0, (byte) 0, true, false, 0F, 0F));
        assertEquals(2, rows.size());
        assertEquals("73.0° / 114.0°", rows.get(0).value());
        assertEquals(Tone.GOOD, rows.get(0).tone(), "dentro da janela destaca");
        assertEquals("31.0 b/s", rows.get(1).value());
        assertEquals(Tone.NORMAL, RocketPanelModel.plannerRows(sample(1.0, 0.0, 1.0, 1.0, (byte) 0, false, false, 0F, 0F)).get(0).tone());
    }

    @Test
    void planejadorComOrbitaErradaOuSemPlano() {
        assertEquals("hud.spacehorizons.rocket.planner.wrong_direction",
                RocketPanelModel.plannerRows(sample(1.0, 0.0, 1.0, 1.0, (byte) 3, false, false, 0F, 0F)).get(0).labelKey());
        assertEquals("hud.spacehorizons.rocket.planner.not_in_orbit",
                RocketPanelModel.plannerRows(sample(1.0, 0.0, 1.0, 1.0, (byte) 2, false, false, 0F, 0F)).get(0).labelKey());
        assertFalse(RocketPanelModel.hasPlanner(sample(1.0, 0.0, 1.0, 1.0, (byte) 1, false, false, 0F, 0F)), "perto da Lua: sem planejador");
        assertFalse(RocketPanelModel.hasPlanner(plain()));
        assertFalse(RocketPanelModel.hasPlanner(sample(1.0, 0.0, 1.0, 1.0, (byte) 9, false, false, 0F, 0F)), "status desconhecido é ignorado");
        assertFalse(RocketPanelModel.hasPlanner(null));
    }

    private static RocketTelemetryPayload withLanding(byte state, byte percent) {
        RocketTelemetryPayload t = plain();
        return new RocketTelemetryPayload(t.body(), t.inSpace(), t.altitude(), t.verticalSpeed(), t.horizontalSpeed(), t.orbitalSpeed(),
                t.apoapsisAltitude(), t.periapsisAltitude(), t.inclination(), t.timeToApoapsis(), t.fuelMb(), t.fuelCapacityMb(), t.deltaV(), t.twr(),
                t.throttle(), t.sasMode(), t.flightMode(), t.heat(), t.chute(), t.plannerStatus(), t.phaseNow(), t.phaseIdeal(), t.tliDeltaV(),
                t.burnNow(), t.heldForChunks(), state, percent);
    }

    @Test
    void semPrevisaoDeImpactoNaoHaLinhaDePouso() {
        assertNull(RocketPanelModel.landingRow(withLanding((byte) 0, (byte) 0)));
        assertEquals(8, RocketPanelModel.flightRows(withLanding((byte) 0, (byte) 0)).size());
    }

    @Test
    void carregandoMostraAPorcentagemEProntoMostraPronto() {
        Row loading = RocketPanelModel.landingRow(withLanding((byte) 1, (byte) 62));
        assertEquals("hud.spacehorizons.rocket.landing_loading", loading.labelKey());
        assertEquals("62%", loading.value());
        assertEquals(Tone.WARN, loading.tone());
        Row ready = RocketPanelModel.landingRow(withLanding((byte) 2, (byte) 100));
        assertEquals("hud.spacehorizons.rocket.landing_ready", ready.labelKey());
        assertEquals(Tone.GOOD, ready.tone());
        // a linha entra no fim dos dados de voo
        List<Row> rows = RocketPanelModel.flightRows(withLanding((byte) 1, (byte) 62));
        assertEquals(9, rows.size());
        assertEquals("hud.spacehorizons.rocket.landing_loading", rows.get(8).labelKey());
    }

    @Test
    void aPorcentagemForaDaFaixaEhLimitada() {
        assertEquals("100%", RocketPanelModel.landingRow(withLanding((byte) 1, (byte) 120)).value());
        assertEquals("0%", RocketPanelModel.landingRow(withLanding((byte) 1, (byte) -5)).value());
    }
}
