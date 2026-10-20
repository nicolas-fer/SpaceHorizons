package com.spacehorizons.ship;

import com.spacehorizons.rocket.RocketControlPayload;

/**
 * O último comando do piloto de uma nave, no servidor (lógica pura, o espelho do que {@code RocketFlight} faz para o foguete): guarda o
 * comando saneado, detecta a borda dos bits de ação (SAS) e decide o comando em vigor a cada tick.
 *
 * <ul>
 *   <li>Passados {@link #CONTROL_TIMEOUT_TICKS} ticks sem pacote, os giros voltam a zero (o manche solto), mas o ACELERADOR fica onde o piloto o
 *       deixou: ele é uma alavanca, não um manche (a mesma correção do foguete).</li>
 *   <li>Sem piloto no assento por mais de {@link #PILOT_GRACE_TICKS} ticks, o comando de giro é descartado, e o acelerador continua
 *       por mais {@link #UNATTENDED_THROTTLE_TICKS} ticks (30 s), para quem se levanta andar pela nave durante uma queima; depois a nave corta o motor sozinha.</li>
 * </ul>
 */
public final class ShipControlState {
    public static final int CONTROL_TIMEOUT_TICKS = 10;
    public static final int PILOT_GRACE_TICKS = 20;
    /** Quanto tempo o acelerador segue ligado sem piloto (depois o motor é cortado). */
    public static final int UNATTENDED_THROTTLE_TICKS = 600;

    private RocketControlPayload control = RocketControlPayload.NEUTRAL;
    private long controlTick = Long.MIN_VALUE / 2;
    private int edges;
    private int noPilotTicks;
    private int packets;

    /** Guarda o comando (já saneado) recebido no tick {@code tick}; os bits de ação que subiram viram bordas. */
    public void apply(RocketControlPayload payload, long tick) {
        RocketControlPayload clean = payload.sanitized();
        this.edges |= clean.flags() & ~this.control.flags();
        this.control = clean;
        this.controlTick = tick;
        this.packets++;
        this.noPilotTicks = 0;
    }

    /** O comando em vigor no tick {@code tick}. */
    public RocketControlPayload control(long tick) {
        if (tick - this.controlTick <= CONTROL_TIMEOUT_TICKS) {
            return this.control;
        }
        return this.control.throttle() > 0.0F ? new RocketControlPayload(0.0F, 0.0F, 0.0F, this.control.throttle(), (byte) 0) : RocketControlPayload.NEUTRAL;
    }

    /** A cada tick: com piloto zera a contagem; sem ele os giros são descartados e, passado o prazo, o acelerador também. */
    public void trackPilot(boolean present) {
        if (present) {
            this.noPilotTicks = 0;
            return;
        }
        this.noPilotTicks++;
        if (this.noPilotTicks > UNATTENDED_THROTTLE_TICKS) {
            this.control = RocketControlPayload.NEUTRAL;
        } else if (this.noPilotTicks > PILOT_GRACE_TICKS && this.control.throttle() > 0.0F) {
            this.control = new RocketControlPayload(0.0F, 0.0F, 0.0F, this.control.throttle(), (byte) 0);
        } else if (this.noPilotTicks > PILOT_GRACE_TICKS) {
            this.control = RocketControlPayload.NEUTRAL;
        }
    }

    /** Os bits de ação que subiram desde a última chamada (e os zera). */
    public int takeEdges() {
        int taken = this.edges;
        this.edges = 0;
        return taken;
    }

    /** O acelerador que está valendo agora (para o autoteste e o painel). */
    public double throttle() {
        return this.control.throttle();
    }

    /** Ajusta o acelerador (ao carregar a nave do disco ou criar o voo): vale até o primeiro pacote do piloto. */
    public void setThrottle(double throttle) {
        this.control = new RocketControlPayload(0.0F, 0.0F, 0.0F, (float) Math.max(0.0, Math.min(1.0, throttle)), (byte) 0);
    }

    public int packets() {
        return this.packets;
    }

    public int noPilotTicks() {
        return this.noPilotTicks;
    }
}
