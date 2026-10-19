package com.spacehorizons.rocket;

/**
 * "Voo rápido" do piloto (lógica pura): acima de {@link #ENTER_SPEED} blocos/tick em relação ao chão o cliente do piloto
 * reduz a distância de visão e de simulação e pausa a geração de LOD do Distant Horizons (a 25 b/tick o gerador já não
 * acompanha e o tick do servidor chega a ~1 s); abaixo de {@link #EXIT_SPEED} tudo volta ao que o jogador tinha. A diferença
 * entre as duas velocidades é a histerese: o piloto freando perto do limite não liga e desliga a cada tick.
 *
 * <p>Os valores reduzidos seguem a ideia de {@code Autopilot.adjustDistances}: nunca sobem o que o jogador já tem abaixo.
 */
public final class FastFlight {
    /** Velocidade ao chão (blocos/tick) acima da qual o voo é rápido. */
    public static final double ENTER_SPEED = 20.0;
    /** Velocidade ao chão (blocos/tick) abaixo da qual o voo deixa de ser rápido. */
    public static final double EXIT_SPEED = 10.0;
    /** Distância de visão (chunks) durante o voo rápido. */
    public static final int FAST_VIEW_DISTANCE = 4;
    /** Distância de simulação (chunks) durante o voo rápido (o mínimo que o vanilla aceita na opção é 5). */
    public static final int FAST_SIMULATION_DISTANCE = 5;

    private boolean fast;

    /** Atualiza com a velocidade ao chão (módulo da vertical e da horizontal) e devolve se o voo é rápido. */
    public boolean update(double groundSpeed) {
        if (!Double.isFinite(groundSpeed)) {
            return this.fast;
        }
        if (this.fast) {
            this.fast = groundSpeed >= EXIT_SPEED;
        } else {
            this.fast = groundSpeed > ENTER_SPEED;
        }
        return this.fast;
    }

    public boolean fast() {
        return this.fast;
    }

    public void reset() {
        this.fast = false;
    }

    /** A distância durante o voo rápido: a do jogador, limitada a {@code cap} (nunca aumenta). */
    public static int reduced(int current, int cap) {
        return Math.min(current, cap);
    }
}
