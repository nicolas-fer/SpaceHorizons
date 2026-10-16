package com.spacehorizons.survival.oxygen;

import java.util.Locale;

/**
 * Contas puras de um recipiente de oxigênio (quantidade e capacidade em ticks de respiração). Sem dependência de
 * registros, para ser testável; {@link OxygenStorage} aplica isto aos data components de um ItemStack.
 */
public final class OxygenAmounts {
    /** Capacidade do tanque pequeno: 2.400 ticks de respiração = 2 minutos. */
    public static final int SMALL_TANK_CAPACITY = 2_400;
    /** Capacidade do tanque grande: 7.200 ticks de respiração = 6 minutos (o triplo do pequeno). */
    public static final int LARGE_TANK_CAPACITY = 7_200;
    /** Ticks de jogo por segundo, para converter a carga em tempo de respiração. */
    public static final int TICKS_PER_SECOND = 20;

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

    /**
     * Tempo de respiração de uma quantidade, no formato "m:ss". Arredonda para cima: só mostra "0:00" quando o
     * recipiente está mesmo vazio.
     */
    public static String formatTime(int ticks) {
        long seconds = (Math.max(0L, ticks) + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
