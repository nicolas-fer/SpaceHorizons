package com.spacehorizons.ship;

import java.util.function.LongSupplier;

/**
 * Orçamento de UM tick de uma conversão em lotes (D10): unidades de trabalho (1 = um bloco colocado; ler uma célula custa uma fração)
 * e um teto de tempo. O job gasta unidades e para quando {@link #exhausted} diz que acabou, retomando no tick seguinte. Lógica
 * pura (o relógio é injetável), testada sem o jogo.
 */
public final class ConversionBudget {
    /** Configuração global (comando {@code /spacehorizons ship budget}): unidades por tick e milissegundos por tick (0 = sem teto de tempo). */
    private static volatile int unitsPerTick = 1500;
    private static volatile int millisPerTick = 20;

    private final LongSupplier clock;
    private int units;
    private final long deadlineNanos;

    /** {@code deadlineNanos <= 0}: sem teto de tempo. */
    public ConversionBudget(int units, long millis, LongSupplier clock) {
        this.clock = clock;
        this.units = units;
        this.deadlineNanos = millis > 0 ? clock.getAsLong() + millis * 1_000_000L : Long.MAX_VALUE;
    }

    /** O orçamento de um tick com a configuração atual. */
    public static ConversionBudget forTick() {
        return new ConversionBudget(unitsPerTick, millisPerTick, System::nanoTime);
    }

    public static int unitsPerTick() {
        return unitsPerTick;
    }

    public static int millisPerTick() {
        return millisPerTick;
    }

    public static void configure(int units, int millis) {
        unitsPerTick = Math.max(1, units);
        millisPerTick = Math.max(0, millis);
    }

    /** Gasta {@code n} unidades. */
    public void spend(int n) {
        this.units -= n;
    }

    /** Acabou o orçamento do tick (unidades ou tempo)? Sempre dá pelo menos um passo por tick: o chamador gasta antes de perguntar. */
    public boolean exhausted() {
        return this.units <= 0 || this.clock.getAsLong() >= this.deadlineNanos;
    }

    public int unitsLeft() {
        return this.units;
    }
}
