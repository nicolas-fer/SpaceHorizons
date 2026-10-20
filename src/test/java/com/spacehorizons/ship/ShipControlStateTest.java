package com.spacehorizons.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.spacehorizons.rocket.RocketControlPayload;

/** O comando do piloto da nave no servidor (alavanca do acelerador, bordas do SAS, prazos sem piloto) e as contas do combustível. */
class ShipControlStateTest {
    private static RocketControlPayload cmd(float pitch, float throttle, int flags) {
        return new RocketControlPayload(pitch, 0.0F, 0.0F, throttle, (byte) flags);
    }

    @Test
    void comandoValeEntreOsPacotes() {
        ShipControlState s = new ShipControlState();
        s.apply(cmd(1.0F, 0.6F, 0), 100);
        assertEquals(1.0F, s.control(105).pitch());
        assertEquals(0.6F, s.control(110).throttle(), 1.0e-6F);
    }

    @Test
    void semPacoteOsGirosVoltamAZeroMasOAceleradorFicaComoUmaAlavanca() {
        ShipControlState s = new ShipControlState();
        s.apply(cmd(1.0F, 0.6F, 0), 100);
        RocketControlPayload late = s.control(100 + ShipControlState.CONTROL_TIMEOUT_TICKS + 1);
        assertEquals(0.0F, late.pitch());
        assertEquals(0.6F, late.throttle(), 1.0e-6F);
    }

    @Test
    void bordaDoSasContaUmaVezPorAperto() {
        ShipControlState s = new ShipControlState();
        s.apply(cmd(0, 0, RocketControlPayload.FLAG_NEXT_SAS), 1);
        assertEquals(RocketControlPayload.FLAG_NEXT_SAS, s.takeEdges());
        assertEquals(0, s.takeEdges());
        s.apply(cmd(0, 0, RocketControlPayload.FLAG_NEXT_SAS), 2);     // ainda apertado: sem nova borda
        assertEquals(0, s.takeEdges());
        s.apply(cmd(0, 0, 0), 3);
        s.apply(cmd(0, 0, RocketControlPayload.FLAG_NEXT_SAS), 4);
        assertEquals(RocketControlPayload.FLAG_NEXT_SAS, s.takeEdges());
    }

    @Test
    void semPilotoOGiroCaiMasOAceleradorSegueTrintaSegundosEDepoisCorta() {
        ShipControlState s = new ShipControlState();
        s.apply(cmd(1.0F, 0.8F, 0), 1);
        for (int i = 0; i < ShipControlState.PILOT_GRACE_TICKS + 1; i++) {
            s.trackPilot(false);
        }
        assertEquals(0.0F, s.control(5).pitch(), "o comando de giro foi descartado");
        assertEquals(0.8F, s.control(5).throttle(), 1.0e-6F, "o acelerador continua");
        for (int i = 0; i < ShipControlState.UNATTENDED_THROTTLE_TICKS; i++) {
            s.trackPilot(false);
        }
        assertEquals(0.0F, s.control(5).throttle(), "passado o prazo o motor é cortado");
    }

    @Test
    void pilotoPresenteZeraAContagem() {
        ShipControlState s = new ShipControlState();
        s.apply(cmd(0, 0.5F, 0), 1);
        for (int i = 0; i < 15; i++) {
            s.trackPilot(false);
        }
        s.trackPilot(true);
        assertEquals(0, s.noPilotTicks());
    }

    @Test
    void combustivelSomaOQueMudouNosTanquesPorFora() {
        // o voo repartiu 10.000; os tanques hoje têm 12.000 (um galão despejado): o voo passa a ter 12.000 menos o que gastou
        assertEquals(12_000.0, ShipFuel.reconcile(10_000.0, 10_000, 12_000, 32_000.0), 1.0e-9);
        assertEquals(9_000.0, ShipFuel.reconcile(9_000.0, 10_000, 10_000, 32_000.0), 1.0e-9, "sem mudança externa o voo manda");
        assertEquals(32_000.0, ShipFuel.reconcile(30_000.0, 10_000, 14_000, 32_000.0), 1.0e-9, "limitado à capacidade");
        assertEquals(0.0, ShipFuel.reconcile(1_000.0, 10_000, 0, 32_000.0), 1.0e-9, "tanques tirados levam o combustível");
    }
}
