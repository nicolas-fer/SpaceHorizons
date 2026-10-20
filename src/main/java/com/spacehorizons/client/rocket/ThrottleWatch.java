package com.spacehorizons.client.rocket;

/**
 * Vigia se o servidor está aplicando o acelerador que o piloto comanda (lógica pura): compara o acelerador local com o dado
 * sincronizado do foguete a cada tick do cliente e só acusa divergência depois de {@link #GRACE_TICKS} ticks seguidos acima de
 * {@link #TOLERANCE} (o pacote e a resposta levam alguns ticks; ao apertar "cheio" ou "corta" a diferença é grande por um instante).
 */
public final class ThrottleWatch {
    /** Diferença (0 a 1) acima da qual o aplicado e o comandado divergem. */
    public static final float TOLERANCE = 0.03F;
    /** Ticks seguidos de divergência até acusar. */
    public static final int GRACE_TICKS = 10;

    private int diverging;

    /** Um tick do cliente com o acelerador comandado e o que o servidor sincronizou. */
    public void tick(float commanded, float applied) {
        if (Math.abs(commanded - applied) > TOLERANCE) {
            this.diverging = Math.min(this.diverging + 1, 10_000);
        } else {
            this.diverging = 0;
        }
    }

    /** O servidor não está aplicando o comando (há tempo suficiente de divergência). */
    public boolean notAccepted() {
        return this.diverging >= GRACE_TICKS;
    }

    public void reset() {
        this.diverging = 0;
    }
}
