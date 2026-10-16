package com.spacehorizons.rocket;

/**
 * Estado de voo e controle do foguete no servidor. Nesta fase (protótipo 3a-1, sem voo) é um STUB: guarda o último
 * comando do piloto e mantém o modo {@link FlightMode#LANDED}. A etapa 3b (frente B1) troca o corpo por física de verdade
 * (integrador, combustível, pouso, paraquedas) sem mudar a forma de {@code RocketEntity.flight()}.
 */
public final class RocketFlight {
    /** Ticks sem pacote depois dos quais o comando do piloto é tratado como neutro (rede ruim, alt-tab). */
    public static final int CONTROL_TIMEOUT_TICKS = 10;

    private FlightMode mode = FlightMode.LANDED;
    private RocketControlPayload control = RocketControlPayload.NEUTRAL;
    private long controlTick = Long.MIN_VALUE / 2;

    public FlightMode mode() {
        return this.mode;
    }

    /** Guarda o comando (já saneado) recebido no tick {@code tick}. */
    public void applyControl(RocketControlPayload payload, long tick) {
        this.control = payload.sanitized();
        this.controlTick = tick;
    }

    /** O comando em vigor no tick {@code tick}: o último recebido, ou neutro se faz mais de {@link #CONTROL_TIMEOUT_TICKS} ticks. */
    public RocketControlPayload control(long tick) {
        return tick - this.controlTick > CONTROL_TIMEOUT_TICKS ? RocketControlPayload.NEUTRAL : this.control;
    }

    /** Um tick de simulação do servidor. Stub: o foguete só fica pousado. */
    public void tick(RocketEntity rocket) {
        this.mode = FlightMode.LANDED;
    }

    public void setMode(FlightMode mode) {
        this.mode = mode;
    }
}
