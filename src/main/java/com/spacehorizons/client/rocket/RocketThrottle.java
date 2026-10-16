package com.spacehorizons.client.rocket;

/**
 * O acelerador do piloto, mantido no cliente (0 a 1): Shift sobe e Ctrl desce de forma contínua, Z vai a cheio e X corta
 * de uma vez. Lógica pura (sem Minecraft), para ser testada. O valor local é o que o piloto "comanda": o servidor recebe
 * esse número no {@code RocketControlPayload} e devolve o que aplicou no dado sincronizado do foguete.
 */
public final class RocketThrottle {
    /** Quanto o acelerador varia por tick com Shift ou Ctrl apertado (de 0 a 1 em 50 ticks, 2,5 s). */
    public static final float STEP_PER_TICK = 0.02F;

    private float value;

    public float value() {
        return this.value;
    }

    /** Define o valor (ao embarcar, o que o foguete já tem), limitado a 0..1; NaN vira zero. */
    public void set(float throttle) {
        this.value = Float.isFinite(throttle) ? Math.max(0.0F, Math.min(1.0F, throttle)) : 0.0F;
    }

    /**
     * Um tick de entrada. Cortar vence cheio, e cheio vence subir e descer; subir e descer juntos se anulam.
     *
     * @return o acelerador depois do tick
     */
    public float tick(boolean up, boolean down, boolean full, boolean cut) {
        if (cut) {
            this.value = 0.0F;
        } else if (full) {
            this.value = 1.0F;
        } else if (up != down) {
            this.set(this.value + (up ? STEP_PER_TICK : -STEP_PER_TICK));
        }
        return this.value;
    }
}
