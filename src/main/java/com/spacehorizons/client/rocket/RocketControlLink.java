package com.spacehorizons.client.rocket;

import com.spacehorizons.rocket.RocketControlPayload;

import org.jspecify.annotations.Nullable;

/**
 * Do estado das teclas ao {@link RocketControlPayload} que o piloto manda ao servidor. Lógica pura: a cada tick recebe as
 * teclas, atualiza o acelerador local e diz se há pacote a enviar (ao mudar, e a cada {@link #HEARTBEAT_TICKS} ticks
 * mesmo sem mudança, porque o servidor zera comandos sem pacote há 10 ticks).
 *
 * <h2>Sinais (os eixos do corpo do foguete, ver {@code AttitudeMath})</h2>
 * Arfagem positiva (W) gira em torno de +X do corpo e leva o nariz para a "frente" do piloto (para baixo da tela do
 * cockpit); guinada positiva (D) leva o nariz para a direita; rolagem positiva (E) é no sentido horário visto pelo
 * piloto. Os mesmos sinais valem na câmera do cockpit ({@link CockpitCamera}).
 */
public final class RocketControlLink {
    /** Intervalo do pacote de batimento, em ticks. */
    public static final int HEARTBEAT_TICKS = 5;

    /**
     * As teclas de um tick. As seis primeiras e as duas de acelerador contínuo são "mantidas"; as cinco últimas são de
     * borda (verdadeiras só no tick do aperto).
     */
    public record Keys(boolean forward, boolean backward, boolean left, boolean right, boolean rollLeft, boolean rollRight,
            boolean throttleUp, boolean throttleDown, boolean fullThrottle, boolean cutThrottle, boolean nextSas, boolean parachute,
            boolean disassemble) {
        public static final Keys NONE = new Keys(false, false, false, false, false, false, false, false, false, false, false, false, false);
    }

    private final RocketThrottle throttle = new RocketThrottle();
    private @Nullable RocketControlPayload last;
    private int sinceSend;

    /** Ao embarcar: o acelerador começa no que o foguete já tem e o próximo pacote é sempre enviado. */
    public void reset(float throttleNow) {
        this.throttle.set(throttleNow);
        this.last = null;
        this.sinceSend = 0;
    }

    public float throttle() {
        return this.throttle.value();
    }

    /** O pacote para as teclas deste tick (sem atualizar o estado de envio). */
    public RocketControlPayload payloadFor(Keys keys) {
        float t = this.throttle.tick(keys.throttleUp, keys.throttleDown, keys.fullThrottle, keys.cutThrottle);
        int flags = 0;
        if (keys.nextSas) {
            flags |= RocketControlPayload.FLAG_NEXT_SAS;
        }
        if (keys.parachute) {
            flags |= RocketControlPayload.FLAG_PARACHUTE;
        }
        if (keys.disassemble) {
            flags |= RocketControlPayload.FLAG_DISASSEMBLE;
        }
        if (keys.fullThrottle && !keys.cutThrottle) {
            flags |= RocketControlPayload.FLAG_FULL_THROTTLE;
        }
        if (keys.cutThrottle) {
            flags |= RocketControlPayload.FLAG_CUT_THROTTLE;
        }
        return new RocketControlPayload(axis(keys.forward, keys.backward), axis(keys.right, keys.left), axis(keys.rollRight, keys.rollLeft), t,
                (byte) flags);
    }

    /**
     * Um tick: atualiza o acelerador e devolve o pacote a enviar, ou null se não há nada a mandar agora. Pacotes com flag
     * de borda sempre saem (uma vez por aperto); os demais saem ao mudar e no batimento.
     */
    public @Nullable RocketControlPayload tick(Keys keys) {
        RocketControlPayload now = this.payloadFor(keys);
        this.sinceSend++;
        boolean send = now.flags() != 0 || this.last == null || !now.equals(this.last) || this.sinceSend >= HEARTBEAT_TICKS;
        if (!send) {
            return null;
        }
        this.last = now;
        this.sinceSend = 0;
        return now;
    }

    private static float axis(boolean positive, boolean negative) {
        return positive == negative ? 0.0F : (positive ? 1.0F : -1.0F);
    }
}
