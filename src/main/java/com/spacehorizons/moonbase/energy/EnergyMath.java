package com.spacehorizons.moonbase.energy;

/**
 * Regras puras da energia da base lunar (sem Minecraft), testáveis sem iniciar o jogo. Unidade: UE (unidade de energia).
 *
 * Modelo (docs/PLANO_SOBREVIVENCIA.md, "Etapa 4"): não há rede com dono nem cálculo global. Cada bloco de energia guarda o
 * seu número de UE e, a cada tick, EMPURRA energia para os vizinhos por face ({@link #flow}); o cabo é só um bloco de
 * passagem. Não há varredura de grafo nem cache para invalidar: ligar ou desligar um bloco muda o fluxo no tick seguinte.
 * A energia se conserva: cada transferência tira de um e põe no outro.
 *
 * Papéis ({@link Role}): painel produz e nunca recebe; bateria e cabo guardam e passam adiante; máquina só consome.
 */
public final class EnergyMath {
    /** UE gerados por tick por um painel solar com o sol a pino (8 UE/t = 160 UE/s). */
    public static final int PANEL_OUTPUT_PER_TICK = 8;
    /** Abaixo desta altura do sol (seno da elevação) o painel não gera nada (sol quase no horizonte). */
    public static final double PANEL_MIN_SUN = 0.05;
    public static final int PANEL_CAPACITY = 400;
    /** Bateria: 200.000 UE = 25.000 ticks de um painel (21 min de sol a pino). */
    public static final int BATTERY_CAPACITY = 200_000;
    /** Cabo: capacidade grande o bastante para a diferença que a equalização exige por trecho ser uma fração pequena (cadeias longas não estrangulam). */
    public static final int CABLE_CAPACITY = 1_000;
    /** Buffer de energia de cada máquina (cabe em short, que é o que a tela sincroniza). */
    public static final int MACHINE_CAPACITY = 4_000;
    /** Quanto passa por face por tick. */
    public static final int TRANSFER_PER_TICK = 200;

    private EnergyMath() {
    }

    /** Papel de um bloco de energia. */
    public enum Role {
        /** Painel solar: dá energia a qualquer vizinho que a aceite; não aceita. */
        PRODUCER,
        /** Bateria: guarda; dá a cabos e máquinas; entre armazenadores equaliza pela fração. */
        STORAGE,
        /** Cabo: passa adiante (pequena capacidade); entre armazenadores e cabos equaliza pela fração. */
        CABLE,
        /** Máquina: só consome, nunca dá. */
        CONSUMER
    }

    /**
     * Quanto o bloco {@code from} passa para o vizinho {@code to} neste tick.
     * <ul>
     *   <li>nunca de uma máquina, nunca para um painel;</li>
     *   <li>de um painel: para qualquer outro, o quanto couber;</li>
     *   <li>de bateria ou cabo para uma máquina: o quanto couber;</li>
     *   <li>entre bateria e cabo (qualquer direção) e entre cabos: só equaliza, ou seja, só passa de quem está mais cheio (em fração)
     *       e só até igualar as frações (isso evita o vai-e-vem e deixa a energia escoar pela fila).</li>
     * </ul>
     */
    public static int flow(Role from, int fromEnergy, int fromCapacity, Role to, int toEnergy, int toCapacity) {
        if (from == Role.CONSUMER || to == Role.PRODUCER || fromEnergy <= 0 || fromCapacity <= 0 || toCapacity <= 0) {
            return 0;
        }
        int space = toCapacity - toEnergy;
        if (space <= 0) {
            return 0;
        }
        int limit = Math.min(TRANSFER_PER_TICK, Math.min(fromEnergy, space));
        if (from == Role.PRODUCER || to == Role.CONSUMER) {
            return limit;
        }
        // armazenador e cabo entre si: equaliza as frações. a = fromEnergy*toCap, b = toEnergy*fromCap (long evita estouro)
        long surplus = (long) fromEnergy * toCapacity - (long) toEnergy * fromCapacity;
        if (surplus <= 0) {
            return 0;
        }
        int equalize = (int) (surplus / ((long) fromCapacity + toCapacity));
        return Math.max(0, Math.min(limit, equalize));
    }

    /**
     * Geração de um painel neste tick, em UE. {@code sunUp} é a altura do sol no lugar (seno da elevação, de -1 a 1) e
     * {@code seesSky} diz se nada cobre o painel. Sem céu ou com o sol abaixo de {@link #PANEL_MIN_SUN}: zero.
     */
    public static int solarOutput(double sunUp, boolean seesSky) {
        if (!seesSky || sunUp < PANEL_MIN_SUN) {
            return 0;
        }
        return Math.max(1, (int) Math.round(PANEL_OUTPUT_PER_TICK * Math.min(1.0, sunUp)));
    }

    /** Quanto cabe ainda num armazém. */
    public static int space(int energy, int capacity) {
        return Math.max(0, capacity - Math.max(0, energy));
    }

    /** Soma {@code add} sem passar da capacidade; devolve a energia nova. */
    public static int add(int energy, int capacity, int add) {
        return Math.max(0, Math.min(capacity, energy + add));
    }
}
