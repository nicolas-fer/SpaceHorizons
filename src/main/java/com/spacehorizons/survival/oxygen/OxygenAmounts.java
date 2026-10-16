package com.spacehorizons.survival.oxygen;

/**
 * Contas puras de um recipiente de oxigênio (quantidade e capacidade em ticks de respiração). Sem dependência de
 * registros, para ser testável; {@link OxygenStorage} aplica isto aos data components de um ItemStack.
 */
public final class OxygenAmounts {
    private OxygenAmounts() {
    }

    /** Força a quantidade para dentro de [0, capacidade]. */
    public static int clamp(int amount, int capacity) {
        return Math.max(0, Math.min(Math.max(0, capacity), amount));
    }

    /** Quanto de {@code offered} cabe num recipiente com {@code amount} de {@code capacity}. */
    public static int acceptable(int amount, int capacity, int offered) {
        return Math.max(0, Math.min(offered, capacity - clamp(amount, capacity)));
    }

    /** Quanto dá para tirar ({@code wanted}) de um recipiente com {@code amount}. */
    public static int extractable(int amount, int capacity, int wanted) {
        return Math.max(0, Math.min(wanted, clamp(amount, capacity)));
    }

    /** Largura da barra de 13 px da hotbar (mesma escala da durabilidade). */
    public static int barWidth(int amount, int capacity) {
        return capacity <= 0 ? 0 : Math.round(13.0F * clamp(amount, capacity) / capacity);
    }
}
