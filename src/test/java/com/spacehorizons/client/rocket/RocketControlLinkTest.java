package com.spacehorizons.client.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.client.rocket.RocketControlLink.Keys;
import com.spacehorizons.rocket.RocketControlPayload;

/** O acelerador do piloto e o envio do comando (ao mudar, batimento a cada 5 ticks, flags de borda uma vez). */
class RocketControlLinkTest {
    private static final Keys NONE = Keys.NONE;

    /** Só acelerador: subir, descer, cheio, cortar. */
    private static Keys throttle(boolean up, boolean down, boolean full, boolean cut) {
        return new Keys(false, false, false, false, false, false, up, down, full, cut, false, false, false);
    }

    @Test
    void aceleradorSobeEDesceDoisCentesimosPorTick() {
        RocketThrottle t = new RocketThrottle();
        for (int i = 0; i < 10; i++) {
            t.tick(true, false, false, false);
        }
        assertEquals(0.2F, t.value(), 1.0E-5F);
        for (int i = 0; i < 5; i++) {
            t.tick(false, true, false, false);
        }
        assertEquals(0.1F, t.value(), 1.0E-5F);
    }

    @Test
    void aceleradorFicaEntreZeroEUm() {
        RocketThrottle t = new RocketThrottle();
        for (int i = 0; i < 100; i++) {
            t.tick(true, false, false, false);
        }
        assertEquals(1.0F, t.value());
        for (int i = 0; i < 100; i++) {
            t.tick(false, true, false, false);
        }
        assertEquals(0.0F, t.value());
        t.set(Float.NaN);
        assertEquals(0.0F, t.value());
        t.set(7.0F);
        assertEquals(1.0F, t.value());
    }

    @Test
    void cheioECortaSaltamECortarVence() {
        RocketThrottle t = new RocketThrottle();
        t.set(0.3F);
        assertEquals(1.0F, t.tick(false, false, true, false));
        assertEquals(0.0F, t.tick(false, false, false, true));
        assertEquals(0.0F, t.tick(true, false, true, true), "cortar vence cheio");
        assertEquals(0.0F, t.tick(true, true, false, false), "subir e descer juntos se anulam");
    }

    @Test
    void sentidosDosEixos() {
        RocketControlLink link = new RocketControlLink();
        link.reset(0.0F);
        // W + D + E
        RocketControlPayload p = link.payloadFor(new Keys(true, false, false, true, false, true, false, false, false, false, false, false, false));
        assertEquals(1.0F, p.pitch());
        assertEquals(1.0F, p.yaw());
        assertEquals(1.0F, p.roll());
        // S + A + Q
        RocketControlPayload q = link.payloadFor(new Keys(false, true, true, false, true, false, false, false, false, false, false, false, false));
        assertEquals(-1.0F, q.pitch());
        assertEquals(-1.0F, q.yaw());
        assertEquals(-1.0F, q.roll());
        // W + S se anulam
        RocketControlPayload z = link.payloadFor(new Keys(true, true, false, false, false, false, false, false, false, false, false, false, false));
        assertEquals(0.0F, z.pitch());
    }

    @Test
    void primeiroPacoteSaiLogoESemMudancaSoNoBatimento() {
        RocketControlLink link = new RocketControlLink();
        link.reset(0.0F);
        assertNotNull(link.tick(NONE), "o primeiro tick sempre envia");
        for (int i = 0; i < RocketControlLink.HEARTBEAT_TICKS - 1; i++) {
            assertNull(link.tick(NONE), "sem mudança não envia no tick " + i);
        }
        assertNotNull(link.tick(NONE), "batimento no quinto tick");
        assertNull(link.tick(NONE));
    }

    @Test
    void mudancaEnviaNaHora() {
        RocketControlLink link = new RocketControlLink();
        link.reset(0.0F);
        link.tick(NONE);
        RocketControlPayload sent = link.tick(throttle(true, false, false, false));
        assertNotNull(sent);
        assertEquals(0.02F, sent.throttle(), 1.0E-5F);
        RocketControlPayload next = link.tick(throttle(true, false, false, false));
        assertNotNull(next, "o acelerador continua mudando enquanto Shift está apertado");
        assertEquals(0.04F, next.throttle(), 1.0E-5F);
    }

    @Test
    void flagsDeBordaSaemUmaVezPorAperto() {
        RocketControlLink link = new RocketControlLink();
        link.reset(0.0F);
        link.tick(NONE);
        Keys sas = new Keys(false, false, false, false, false, false, false, false, false, false, true, false, false);
        RocketControlPayload a = link.tick(sas);
        assertNotNull(a);
        assertTrue(a.has(RocketControlPayload.FLAG_NEXT_SAS));
        // dois apertos em ticks seguidos: os dois saem (pacote com flag sempre envia)
        RocketControlPayload b = link.tick(sas);
        assertNotNull(b);
        assertTrue(b.has(RocketControlPayload.FLAG_NEXT_SAS));
        // sem aperto a flag some do pacote seguinte: o servidor não a vê repetida
        RocketControlPayload c = link.tick(NONE);
        assertNotNull(c, "voltar ao neutro é uma mudança e sai");
        assertEquals(0, c.flags());
        assertNull(link.tick(NONE));
    }

    @Test
    void cheioECortaMandamFlagEOAceleradorAjustado() {
        RocketControlLink link = new RocketControlLink();
        link.reset(0.4F);
        RocketControlPayload full = link.tick(throttle(false, false, true, false));
        assertEquals(1.0F, full.throttle());
        assertTrue(full.has(RocketControlPayload.FLAG_FULL_THROTTLE));
        RocketControlPayload cut = link.tick(throttle(false, false, false, true));
        assertEquals(0.0F, cut.throttle());
        assertTrue(cut.has(RocketControlPayload.FLAG_CUT_THROTTLE));
        assertFalse(cut.has(RocketControlPayload.FLAG_FULL_THROTTLE));
        assertEquals(0.0F, link.throttle());
    }

    @Test
    void paraquedasEDesmontarSaoFlags() {
        RocketControlLink link = new RocketControlLink();
        link.reset(0.0F);
        RocketControlPayload p = link.tick(new Keys(false, false, false, false, false, false, false, false, false, false, false, true, true));
        assertTrue(p.has(RocketControlPayload.FLAG_PARACHUTE));
        assertTrue(p.has(RocketControlPayload.FLAG_DISASSEMBLE));
        assertFalse(p.has(RocketControlPayload.FLAG_NEXT_SAS));
    }

    @Test
    void resetPegaOAceleradorDoFoguete() {
        RocketControlLink link = new RocketControlLink();
        link.reset(0.55F);
        assertEquals(0.55F, link.throttle(), 1.0E-6F);
        assertEquals(0.55F, link.payloadFor(NONE).throttle(), 1.0E-6F);
    }
}
