package com.spacehorizons.rocket;

import java.util.ArrayList;
import java.util.List;

/**
 * Chunks à frente do foguete (lógica pura): quais pedir e quando segurar a simulação. A 30 a 90 blocos por tick o foguete
 * cruza vários chunks por tick e o vanilla só carrega o que o jogador "vê"; então a cada tick o servidor pede tickets
 * {@code ENDER_PEARL} à frente (extrapolando a velocidade das coordenadas) e SEGURA o passo se faltar chunk nos próximos
 * {@link #GATING_STEPS}, até {@link #MAX_HELD_TICKS} ticks (depois segue com aviso: um chunk que nunca carrega não pode
 * prender o piloto para sempre).
 */
public final class ChunkGate {
    /** Ticks à frente cujos chunks são pedidos (0 é o chunk do próprio foguete, que precisa estar em ticking). */
    public static final int[] REQUEST_STEPS = {0, 1, 2, 4, 8, 16, 32};
    /** Ticks à frente cujos chunks precisam estar carregados para a simulação andar. */
    public static final int[] GATING_STEPS = {1, 2, 4};
    /** Teto de ticks segurados seguidos antes de seguir mesmo sem os chunks. */
    public static final int MAX_HELD_TICKS = 200;
    /** Raio do ticket nos passos mais próximos (mantém o chunk central em ticking de entidades); nos outros é 0. */
    public static final int NEAR_RADIUS = 2;
    /** Até este passo o ticket tem {@link #NEAR_RADIUS}. */
    public static final int NEAR_STEPS = 2;

    /** Um chunk a pedir: o passo (ticks à frente), o chunk, o raio do ticket e se ele conta para segurar o passo. */
    public record Probe(int step, int chunkX, int chunkZ, int radius, boolean gating) {
    }

    /** O que fazer neste tick. */
    public enum Decision {
        /** Os chunks estão prontos (ou o teto estourou): avança. */
        GO,
        /** Falta chunk: segura o passo. */
        HOLD,
        /** O teto estourou neste tick: avança, e quem chama avisa uma vez. */
        GIVE_UP
    }

    private int held;

    /**
     * Os chunks a pedir a partir da posição {@code (x, z)} com a velocidade das coordenadas {@code (vx, vz)} por tick.
     * {@code circumference} > 0 normaliza X na costura do planeta (o terreno é periódico); 0 não normaliza (espaço aberto).
     */
    public static List<Probe> probes(double x, double z, double vx, double vz, double circumference) {
        List<Probe> probes = new ArrayList<>(REQUEST_STEPS.length);
        for (int k : REQUEST_STEPS) {
            double px = normalize(x + vx * k, circumference);
            int cx = (int) Math.floor(px) >> 4;
            int cz = (int) Math.floor(z + vz * k) >> 4;
            boolean gating = false;
            for (int g : GATING_STEPS) {
                gating |= g == k;
            }
            probes.add(new Probe(k, cx, cz, k <= NEAR_STEPS ? NEAR_RADIUS : 0, gating));
        }
        return probes;
    }

    /** Leva X para [-C/2, C/2) (a costura do planeta). */
    public static double normalize(double x, double circumference) {
        if (!(circumference > 0.0)) {
            return x;
        }
        double half = circumference / 2.0;
        return x - Math.floor((x + half) / circumference) * circumference;
    }

    /** Decide o tick: {@code missing} é "algum chunk dos passos de segurar não está carregado". */
    public Decision decide(boolean missing) {
        if (!missing) {
            this.held = 0;
            return Decision.GO;
        }
        this.held++;
        if (this.held > MAX_HELD_TICKS) {
            return this.held == MAX_HELD_TICKS + 1 ? Decision.GIVE_UP : Decision.GO;
        }
        return Decision.HOLD;
    }

    /** Ticks seguidos segurados (0 se anda). */
    public int heldTicks() {
        return this.held;
    }

    /** Segurando agora (para a telemetria). */
    public boolean isHolding() {
        return this.held > 0 && this.held <= MAX_HELD_TICKS;
    }

    public void reset() {
        this.held = 0;
    }
}
