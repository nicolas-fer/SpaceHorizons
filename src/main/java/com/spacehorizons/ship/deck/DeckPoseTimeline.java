package com.spacehorizons.ship.deck;

import java.util.ArrayDeque;
import java.util.Iterator;

import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * A linha do tempo da pose da nave no cliente de quem está a pé no convés (S5). O vanilla leva a entidade ao último ponto recebido a cada tick do
 * cliente; com a rede atrasando os pacotes em tempos diferentes, um tick do cliente às vezes consome zero atualizações do servidor e o seguinte
 * consome duas, e a nave (e a câmera presa a ela) anda aos trancos: a 100 blocos por tick um tick perdido são 100 blocos de salto. Aqui cada pose que
 * chega vem com o NÚMERO DO TICK DO SERVIDOR em que valeu, e o desenho (o casco, a câmera e o tripulante) lê a linha do tempo num instante fracionário
 * do tempo do servidor, atrás do mais recente por {@link #DELAY_TICKS}: interpolação de posição e slerp da atitude entre dois ticks do servidor, sempre
 * na mesma cadência, qualquer que seja o atraso de cada pacote.
 *
 * <p>O relógio do servidor é estimado como no estado virtual da nave ({@code ShipRenderClient}): o deslocamento entre o tempo do cliente e o do
 * servidor segue o menor (chegada − tick) (o pacote de menor atraso) com passos limitados nos dois sentidos, e a chegada de cada pacote não importa.
 * O tempo do cliente é o do jogo (tick e parcial do quadro), o mesmo em que o vanilla desenha o resto do mundo e o próprio jogador, para a nave e o
 * tripulante nunca divergirem. Lógica pura, testada sem o jogo.
 */
public final class DeckPoseTimeline {
    /**
     * Quanto o desenho fica atrás do relógio estimado do servidor (ticks). Tem de cobrir o tick inteiro entre duas poses (o desenho precisa de uma pose
     * DEPOIS do instante mostrado) mais a variação de chegada dos pacotes e do quadro (até ~0,6 tick medidos): 2 ticks (100 ms) é folga com sobra.
     */
    public static final double DELAY_TICKS = 2.0;
    /** Quanto o deslocamento do relógio pode subir por pacote (ticks): segue mudanças reais do atraso sem acompanhar o ruído de chegada. */
    private static final double OFFSET_RELAX = 0.0004;
    /** Quanto ele pode DESCER por pacote (ticks): um pacote que chega "cedo" demais não faz o tempo mostrado saltar (a 100 b/t, 0,01 tick são 1 bloco). */
    private static final double OFFSET_SLEW_DOWN = 0.002;
    private static final int CAPACITY = 32;
    /** Sem pacote por tantos ticks, a linha do tempo está velha e o desenho volta ao do vanilla. */
    private static final double STALE_TICKS = 10.0;

    private record Sample(int stamp, double x, double y, double z, double qx, double qy, double qz, double qw) {
    }

    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private double offset = Double.NaN;
    private double lastArrival = Double.NEGATIVE_INFINITY;

    /** Uma pose chegou: o tick do servidor em que valeu, a posição, a atitude e o tempo do cliente (ticks, com fração) da chegada. */
    public void record(int stamp, double x, double y, double z, Quaternionfc attitude, double arrivalTick) {
        Sample last = this.samples.peekLast();
        if (last != null && stamp <= last.stamp) {
            return;
        }
        double raw = arrivalTick - stamp;
        if (Double.isNaN(this.offset)) {
            this.offset = raw;
        } else if (raw < this.offset) {
            this.offset = Math.max(raw, this.offset - OFFSET_SLEW_DOWN);
        } else {
            this.offset = Math.min(raw, this.offset + OFFSET_RELAX);
        }
        this.lastArrival = arrivalTick;
        Quaterniond q = new Quaterniond(attitude.x(), attitude.y(), attitude.z(), attitude.w()).normalize();
        this.samples.addLast(new Sample(stamp, x, y, z, q.x, q.y, q.z, q.w));
        while (this.samples.size() > CAPACITY) {
            this.samples.removeFirst();
        }
    }

    /** Há amostras suficientes para interpolar e recentes (em relação ao tempo do cliente {@code nowTick}). */
    public boolean active(double nowTick) {
        return this.samples.size() >= 3 && nowTick - this.lastArrival <= STALE_TICKS;
    }

    public void clear() {
        this.samples.clear();
        this.offset = Double.NaN;
        this.lastArrival = Double.NEGATIVE_INFINITY;
    }

    public int size() {
        return this.samples.size();
    }

    /** O deslocamento atual do relógio (ticks do cliente menos ticks do servidor), NaN antes da primeira pose. */
    public double offset() {
        return this.offset;
    }

    /** O tick do servidor que o desenho mostra no tempo do cliente {@code clientTick} (fracionário). */
    public double serverTimeAt(double clientTick) {
        return clientTick - this.offset - DELAY_TICKS;
    }

    /**
     * A pose no tempo do cliente {@code clientTick}: interpolada entre os dois ticks do servidor em volta de {@link #serverTimeAt}; antes da primeira
     * amostra e depois da última, a pose da ponta (nunca extrapola). Null se a linha do tempo está vazia.
     */
    public @Nullable DeckFrame sample(double clientTick, Vector3f pivot) {
        Sample[] pair = this.pair(this.serverTimeAt(clientTick));
        if (pair == null) {
            return null;
        }
        Sample a = pair[0];
        Sample b = pair[1];
        double t = a == b ? 0.0 : Math.max(0.0, Math.min(1.0, (this.serverTimeAt(clientTick) - a.stamp) / (b.stamp - a.stamp)));
        Quaterniond q = new Quaterniond(a.qx, a.qy, a.qz, a.qw).slerp(new Quaterniond(b.qx, b.qy, b.qz, b.qw), t, new Quaterniond());
        return new DeckFrame(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t,
                new Quaternionf((float) q.x, (float) q.y, (float) q.z, (float) q.w), pivot);
    }

    /** A atitude em dupla precisão (para medir o tremor sem o ruído dos 32 bits); null se vazia. */
    public @Nullable Quaterniond sampleAttitude(double clientTick) {
        Sample[] pair = this.pair(this.serverTimeAt(clientTick));
        if (pair == null) {
            return null;
        }
        Sample a = pair[0];
        Sample b = pair[1];
        double t = a == b ? 0.0 : Math.max(0.0, Math.min(1.0, (this.serverTimeAt(clientTick) - a.stamp) / (b.stamp - a.stamp)));
        return new Quaterniond(a.qx, a.qy, a.qz, a.qw).slerp(new Quaterniond(b.qx, b.qy, b.qz, b.qw), t, new Quaterniond());
    }

    /** As duas amostras em volta do tempo do servidor {@code serverTime} (a mesma duas vezes nas pontas); null se vazia. */
    private @Nullable Sample[] pair(double serverTime) {
        if (this.samples.isEmpty()) {
            return null;
        }
        Sample first = this.samples.peekFirst();
        Sample last = this.samples.peekLast();
        if (serverTime <= first.stamp) {
            return new Sample[] {first, first};
        }
        if (serverTime >= last.stamp) {
            return new Sample[] {last, last};
        }
        Sample previous = first;
        Iterator<Sample> it = this.samples.iterator();
        it.next();
        while (it.hasNext()) {
            Sample next = it.next();
            if (serverTime < next.stamp) {
                return new Sample[] {previous, next};
            }
            previous = next;
        }
        return new Sample[] {last, last};
    }
}
