package com.spacehorizons.ship.deck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** A linha do tempo da pose da nave: suave com a chegada dos pacotes irregular, exata nos ticks e sem extrapolar. */
class DeckPoseTimelineTest {
    private static final Vector3f PIVOT = new Vector3f(13, 14, 13);

    /** A pose do servidor no tick k: sobe 100 blocos por tick e gira 1 grau por tick em torno do eixo da nave. */
    private static Quaternionf attitude(int k) {
        return new Quaternionf().rotateX((float) Math.toRadians(30.0)).rotateY((float) Math.toRadians(k));
    }

    @Test
    void naoAtivaSemAmostrasSuficientesNemVelha() {
        DeckPoseTimeline t = new DeckPoseTimeline();
        assertFalse(t.active(0.0));
        t.record(10, 0, 1000, 0, attitude(10), 12.0);
        t.record(11, 0, 1100, 0, attitude(11), 13.0);
        assertFalse(t.active(13.0), "duas amostras não bastam");
        t.record(12, 0, 1200, 0, attitude(12), 14.0);
        assertTrue(t.active(14.0));
        assertFalse(t.active(14.0 + 11.0), "sem pacote há mais de 10 ticks está velha");
        assertNull(new DeckPoseTimeline().sample(5.0, PIVOT));
    }

    @Test
    void ignoraTickRepetidoOuAntigo() {
        DeckPoseTimeline t = new DeckPoseTimeline();
        t.record(10, 0, 1000, 0, attitude(10), 12.0);
        t.record(10, 0, 9999, 0, attitude(10), 12.1);
        t.record(9, 0, 8888, 0, attitude(9), 12.2);
        t.record(11, 0, 1100, 0, attitude(11), 13.0);
        t.record(12, 0, 1200, 0, attitude(12), 14.0);
        assertEquals(3, t.size());
    }

    @Test
    void interpolaExatoEntreDoisTicksDoServidor() {
        DeckPoseTimeline t = new DeckPoseTimeline();
        for (int k = 100; k <= 110; k++) {
            t.record(k, 5, 1000 + 100.0 * k, -3, attitude(k), k + 2.0);   // atraso constante de 2 ticks
        }
        assertEquals(2.0, t.offset(), 1.0E-9);
        // o tempo do servidor mostrado é cliente - atraso(2) - folga(DELAY); no cliente k + 2 + DELAY + 0,25 é o servidor k + 0,25
        double client = 105 + 2.0 + DeckPoseTimeline.DELAY_TICKS + 0.25;
        DeckFrame f = t.sample(client, PIVOT);
        assertNotNull(f);
        assertEquals(1000 + 100.0 * 105.25, f.cy(), 1.0E-6);
        assertEquals(5.0, f.cx(), 1.0E-9);
        // a atitude por slerp: 1 grau por tick, então 105,25 graus de guinada
        Quaternionf expected = attitude(105).slerp(attitude(106), 0.25F, new Quaternionf());
        assertEquals(1.0F, Math.abs(f.attitude().dot(expected)), 1.0E-6F);
    }

    @Test
    void naoExtrapolaNasPontas() {
        DeckPoseTimeline t = new DeckPoseTimeline();
        for (int k = 100; k <= 104; k++) {
            t.record(k, 0, 100.0 * k, 0, attitude(k), k + 1.0);
        }
        DeckFrame late = t.sample(1_000.0, PIVOT);
        DeckFrame early = t.sample(-1_000.0, PIVOT);
        assertEquals(100.0 * 104, late.cy(), 1.0E-9, "depois da última, fica nela");
        assertEquals(100.0 * 100, early.cy(), 1.0E-9, "antes da primeira, fica nela");
    }

    @Test
    void chegadaIrregularNaoViraTrancoNoDesenho() {
        // o servidor manda um tick por tick; os pacotes chegam com até 0,4 tick de variação (o tick do cliente às vezes vê zero atualizações e
        // às vezes duas); o desenho, em quadros de 0,17 tick, anda a velocidade CONSTANTE (100 blocos por tick) depois de aquecer
        Random random = new Random(42);
        DeckPoseTimeline t = new DeckPoseTimeline();
        int next = 100;
        double previous = Double.NaN;
        double worst = 0.0;
        for (double client = 100.0; client < 400.0; client += 0.17) {
            // chegam todos os pacotes cujo instante de chegada (tick do servidor + 2 de atraso + variação) já passou
            while (next + 2.0 + random.nextDouble() * 0.4 <= client) {
                t.record(next, 0, 100.0 * next, 0, attitude(next), client);
                next++;
            }
            if (!t.active(client)) {
                continue;
            }
            DeckFrame f = t.sample(client, PIVOT);
            if (!Double.isNaN(previous) && client > 130.0) {
                double speed = (f.cy() - previous) / 0.17;
                worst = Math.max(worst, Math.abs(speed - 100.0) / 100.0);
            }
            previous = f.cy();
        }
        assertTrue(worst < 0.02, "variação da velocidade do desenho " + worst);
    }

    @Test
    void atitudeEmDuplaPrecisaoEMonotonaEConstante() {
        DeckPoseTimeline t = new DeckPoseTimeline();
        for (int k = 100; k <= 140; k++) {
            t.record(k, 0, 0, 0, attitude(k), k + 2.0);
        }
        // só os 32 últimos ticks ficam guardados (109 a 140): o tempo mostrado tem de cair dentro deles
        double step = Math.toRadians(1.0);
        Quaterniond previous = null;
        for (double client = 120.0; client < 138.0; client += 0.0625) {
            Quaterniond q = t.sampleAttitude(client);
            if (previous != null) {
                double angle = 2.0 * Math.acos(Math.min(1.0, Math.abs(q.dot(previous))));
                assertEquals(step * 0.0625, angle, step * 0.0625 * 0.02, "passo de " + client);
            }
            previous = q;
        }
    }

    @Test
    void deslocamentoInfladoPorRajadaInicialDesceDeUmaVez() {
        // um cliente ocupado processa os primeiros pacotes 31 ticks atrasados; quando a rede normaliza, o relógio não pode levar minutos para voltar
        DeckPoseTimeline t = new DeckPoseTimeline();
        t.record(100, 0, 1000, 0, attitude(100), 131.0);
        t.record(101, 0, 1100, 0, attitude(101), 132.0);
        t.record(102, 0, 1200, 0, attitude(102), 133.0);
        assertEquals(31.0, t.offset(), 1e-9);
        t.record(103, 0, 1300, 0, attitude(103), 106.0);
        assertEquals(3.0, t.offset(), 1e-9, "uma queda de mais de 3 ticks vai direto ao novo valor");
        t.record(104, 0, 1400, 0, attitude(104), 106.9);
        assertEquals(3.0 - 0.002, t.offset(), 1e-9, "uma queda pequena segue limitada a 0,002 por pacote");
    }

    @Test
    void relogioAdiantadoSobeDeUmaVezEFicaAtrasDaPoseMaisNova() {
        // o cliente recuperou ticks atrasados: o relógio dele andou 7 ticks à frente do que os pacotes sustentam
        DeckPoseTimeline t = new DeckPoseTimeline();
        for (int k = 0; k < 6; k++) {
            t.record(100 + k, 0, 1000 + 10 * k, 0, attitude(100 + k), 120.0 + k);
        }
        assertEquals(20.0, t.offset(), 1e-9);
        double now = 130.0;
        assertTrue(t.starved(now), "o tempo mostrado (" + t.serverTimeAt(now) + ") passou da pose mais nova (105)");
        t.record(106, 0, 1060, 0, attitude(106), 133.0);
        assertEquals(27.0, t.offset(), 1e-9, "um pacote 7 ticks mais tarde que o esperado sobe o deslocamento direto");
        assertFalse(t.starved(133.0));
        t.record(107, 0, 1070, 0, attitude(107), 134.5);
        assertEquals(27.0 + 0.0004, t.offset(), 1e-9, "uma variação pequena segue limitada");
    }
}
