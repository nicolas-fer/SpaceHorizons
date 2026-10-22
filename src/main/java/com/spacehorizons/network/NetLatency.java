package com.spacehorizons.network;

/**
 * Latência simulada, só para o autoteste do convés (S5): atrasa de {@link #delayMs} milissegundos a chegada de TODO pacote de jogo nas duas
 * pontas da conexão (servidor integrado incluído), então a ida e volta custa o dobro. Zero (o padrão) desliga e o mixin
 * ({@code ConnectionLatencyMixin}) não faz nada. Os pacotes saem na mesma ordem em que chegaram.
 */
public final class NetLatency {
    private static volatile int delayMs;

    /** Verdadeiro enquanto o mixin reentrega um pacote atrasado (para ele não ser atrasado de novo). */
    static final ThreadLocal<Boolean> REPLAYING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private NetLatency() {
    }

    /** Atraso por sentido, em milissegundos. */
    public static int delayMs() {
        return delayMs;
    }

    public static void setDelayMs(int ms) {
        delayMs = Math.max(0, ms);
    }

    public static boolean replaying() {
        return REPLAYING.get();
    }

    public static void setReplaying(boolean value) {
        REPLAYING.set(value);
    }
}
