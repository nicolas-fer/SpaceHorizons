package com.spacehorizons.survival.fuel;

import com.mojang.serialization.Codec;

/**
 * Regras puras do combustível guardado no galão (quantidade em mB, sem fluido no mundo). Sem dependência de
 * registros do Minecraft, para ser testável. O data component do galão guarda só um inteiro dentro destes limites.
 */
public final class CanisterFuel {
    /** Capacidade de um galão, em mB. */
    public static final int CAPACITY = 1000;

    /** Codec do data component: inteiro de 0 a {@link #CAPACITY}; valores fora disso são recusados ao ler o JSON/NBT. */
    public static final Codec<Integer> CODEC = Codec.intRange(0, CAPACITY);

    private CanisterFuel() {
    }

    /** Força a quantidade para dentro de [0, {@link #CAPACITY}]. */
    public static int clamp(int mb) {
        return Math.max(0, Math.min(CAPACITY, mb));
    }

    /** Quanto ainda cabe no galão. */
    public static int space(int current) {
        return CAPACITY - clamp(current);
    }

    /** Quantidade depois de somar {@code add} (negativo retira), sempre dentro dos limites. */
    public static int add(int current, int add) {
        return clamp(clamp(current) + add);
    }

    /** Fração de preenchimento, de 0 a 1. */
    public static float fraction(int current) {
        return clamp(current) / (float) CAPACITY;
    }

    /** Largura da barra de 13 px da hotbar (mesma escala da durabilidade). */
    public static int barWidth(int current) {
        return Math.round(13.0F * fraction(current));
    }
}
