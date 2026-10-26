package com.spacehorizons.ship.deck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** O que os observadores recebem do convés: o pacote do estado local e a conta da posição remota no mundo. */
class DeckObserveTest {
    private static final Vector3f PIVOT = new Vector3f(13, 14, 13);

    private static DeckState state(double x, double y, double z, float yaw) {
        DeckState s = new DeckState();
        s.pos.set(x, y, z);
        s.yaw = yaw;
        s.pitch = 10.0F;
        s.onGround = true;
        return s;
    }

    @Test
    void codecDoPacoteVoltaIgual() {
        DeckObservePayload sent = DeckObservePayload.of(7, 12345, 987_654, state(5.5, 1.0, 20.25, -135.0F));
        ByteBuf buf = Unpooled.buffer();
        DeckObservePayload.CODEC.encode(buf, sent);
        DeckObservePayload got = DeckObservePayload.CODEC.decode(buf);
        assertEquals(sent, got);
        assertEquals(0, buf.readableBytes(), "o codec consome tudo o que escreveu");
        assertTrue(got.aboard());
        DeckState s = got.toState();
        assertEquals(5.5, s.pos.x);
        assertEquals(20.25, s.pos.z);
        assertEquals(-135.0F, s.yaw);
        assertTrue(s.onGround);
    }

    @Test
    void avisoDeSaidaVoltaComoSaida() {
        DeckObservePayload left = DeckObservePayload.left(3, 9, 100);
        ByteBuf buf = Unpooled.buffer();
        DeckObservePayload.CODEC.encode(buf, left);
        DeckObservePayload got = DeckObservePayload.CODEC.decode(buf);
        assertFalse(got.aboard());
        assertEquals(3, got.playerId());
        assertEquals(9, got.rocketId());
        assertEquals(100, got.stamp());
    }

    @Test
    void historiaInterpolaEntreTicksEFicaNasPontas() {
        DeckObservedHistory h = new DeckObservedHistory();
        assertNull(h.sample(5.0));
        h.record(1, 10, state(0, 1, 0, 0), 12.0);
        h.record(1, 11, state(1, 1, 0, 90), 13.0);
        h.record(1, 12, state(1, 1, 2, 90), 14.0);
        assertEquals(0.0, h.sample(9.0).pos.x, "antes da primeira amostra: a primeira");
        assertEquals(0.25, h.sample(10.25).pos.x, 1e-12);
        assertEquals(22.5, h.sample(10.25).yaw, 1e-4);
        assertEquals(1.0, h.sample(11.5).pos.x, 1e-12);
        assertEquals(1.0, h.sample(11.5).pos.z, 1e-12);
        assertEquals(2.0, h.sample(50.0).pos.z, 1e-12, "depois da última: a última, sem extrapolar");
    }

    @Test
    void historiaIgnoraTickRepetidoEMudaDeNave() {
        DeckObservedHistory h = new DeckObservedHistory();
        h.record(1, 10, state(0, 1, 0, 0), 12.0);
        h.record(1, 10, state(99, 1, 0, 0), 12.1);
        h.record(1, 9, state(98, 1, 0, 0), 12.2);
        assertEquals(1, h.size());
        h.record(2, 3, state(5, 1, 5, 0), 13.0);
        assertEquals(1, h.size(), "outra nave recomeça a história");
        assertEquals(2, h.rocketId());
        assertEquals(5.0, h.sample(3.0).pos.x);
    }

    @Test
    void olharAtravessaOsTrezentosESessentaPeloCaminhoCurto() {
        DeckObservedHistory h = new DeckObservedHistory();
        h.record(1, 0, state(0, 1, 0, 170), 0.0);
        h.record(1, 1, state(0, 1, 0, -170), 1.0);
        // de 170 a -170 pelo caminho curto são +20 graus: o meio é 180, não 0
        assertEquals(180.0, Math.abs(h.sample(0.5).yaw), 1e-3);
    }

    @Test
    void frescoSoComEstadoRecente() {
        DeckObservedHistory h = new DeckObservedHistory();
        assertFalse(h.fresh(0.0, 10.0));
        h.record(1, 10, state(0, 1, 0, 0), 12.0);
        assertTrue(h.fresh(20.0, 10.0));
        assertFalse(h.fresh(22.5, 10.0));
        h.clear();
        assertFalse(h.fresh(12.0, 10.0));
    }

    /**
     * A conta da posição remota: a pose interpolada entre dois ticks aplicada ao estado local interpolado entre os mesmos dois ticks fica a menos
     * de um milésimo de bloco do caminho exato do servidor (a nave sobe a 100 blocos por tick e gira 1 grau por tick; o tripulante anda a 0,2).
     */
    @Test
    void posicaoRemotaFicaPertoDoCaminhoExatoDoServidor() {
        double worst = 0.0;
        for (int tick = 100; tick < 140; tick++) {
            DeckFrame a = frameAt(tick);
            DeckFrame b = frameAt(tick + 1);
            DeckObservedHistory h = new DeckObservedHistory();
            h.record(1, tick, local(tick), 0.0);
            h.record(1, tick + 1, local(tick + 1), 1.0);
            for (double t = 0.0; t <= 1.0; t += 0.125) {
                DeckFrame frame = DeckFrame.lerp(a, b, t);
                DeckState s = h.sample(tick + t);
                assertNotNull(s);
                Vector3d seen = DeckObservedHistory.worldFeet(frame, s);
                // o caminho exato do servidor, no tempo fracionário: pose e estado do servidor em tick + t
                Vector3d exact = frameAt(tick + t).toWorld(local(tick + t).pos.x, local(tick + t).pos.y, local(tick + t).pos.z);
                worst = Math.max(worst, seen.distance(exact));
            }
        }
        assertTrue(worst < 0.001, "pior desvio " + worst);
    }

    @Test
    void naTicDoServidorAPosicaoRemotaEhExata() {
        DeckFrame frame = frameAt(120);
        DeckState s = local(120);
        DeckObservedHistory h = new DeckObservedHistory();
        h.record(1, 120, s, 0.0);
        h.record(1, 121, local(121), 1.0);
        Vector3d got = DeckObservedHistory.worldFeet(frame, h.sample(120.0));
        Vector3d want = frame.toWorld(s.pos.x, s.pos.y, s.pos.z);
        assertEquals(0.0, got.distance(want), 1e-9);
    }

    private static DeckFrame frameAt(double tick) {
        Quaternionf q = new Quaternionf().rotateX((float) Math.toRadians(30.0)).rotateY((float) Math.toRadians(tick));
        return new DeckFrame(100.0, 1000.0 + 100.0 * tick, -50.0, q, PIVOT);
    }

    private static DeckState local(double tick) {
        return state(5.5 + 0.2 * (tick - 100.0), 1.0, 12.5, 30.0F);
    }
}
