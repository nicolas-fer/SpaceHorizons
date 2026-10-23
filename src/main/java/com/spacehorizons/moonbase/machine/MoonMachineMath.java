package com.spacehorizons.moonbase.machine;

/**
 * Números e conversões puros das máquinas da base lunar (sem Minecraft). Tudo o que balanceia a produção fica aqui.
 *
 * <pre>
 *  gelo lunar (1 item) ──eletrolisador──▶ 500 mB de hidrogênio + 1.200 de oxigênio (100 ticks, 20 UE/t = 2.000 UE)
 *  gelo lunar (1 item) ──refinaria lunar─▶ 400 mB de combustível químico do Tier 1 (120 ticks, 30 UE/t = 3.600 UE)
 *  gelo lunar (1 item) ──gerador de O2 ──▶ 1.200 de oxigênio no reservatório da sala
 *  regolito (1 bloco)  ──extrator de He-3▶ 250 mB de hélio-3 (40 ticks, 10 UE/t = 400 UE); o clique do galão dá 125
 * </pre>
 * Um tanque de foguete cheio (4.000 mB de combustível) pede 10 gelos; o oxigênio de um gelo é 1 minuto de respiração de um jogador
 * (ou 3,3 min de uma sala de 75 células). Painel solar: 8 UE/t com o sol a pino, então o eletrolisador pede 2,5 painéis ao meio-dia.
 */
public final class MoonMachineMath {
    // --- eletrólise (eletrolisador) ---
    public static final int ICE_O2_UNITS = 1_200;
    public static final int ICE_H2_MB = 500;
    public static final int ELECTROLYSIS_TICKS = 100;
    public static final int ELECTROLYSIS_POWER = 20;
    public static final int ELECTROLYZER_O2_CAPACITY = 4_800;
    public static final int ELECTROLYZER_H2_CAPACITY = 4_000;
    /** Oxigênio que passa por tick do reservatório para o tanque do slot (enche um tanque pequeno em 100 ticks). */
    public static final int O2_OUTPUT_PER_TICK = 24;
    /** Hidrogênio que passa por tick do reservatório para o galão (como a refinaria). */
    public static final int H2_OUTPUT_PER_TICK = 10;

    // --- refinaria lunar ---
    public static final int REFINERY_FUEL_PER_ICE_MB = 400;
    public static final int REFINERY_TICKS = 120;
    public static final int REFINERY_POWER = 30;
    public static final int REFINERY_TANK_CAPACITY = 4_000;
    public static final int FUEL_OUTPUT_PER_TICK = 10;

    // --- extrator de hélio-3 ---
    public static final int HELIUM3_PER_REGOLITH_MB = 250;
    public static final int EXTRACTOR_TICKS = 40;
    public static final int EXTRACTOR_POWER = 10;

    // --- gerador de oxigênio (consumo de sala: ver RoomPressure) ---
    /** Oxigênio por gelo posto no gerador. */
    public static final int GENERATOR_O2_PER_ICE = ICE_O2_UNITS;
    /** Oxigênio por tick que o gerador puxa de um tanque do slot para o reservatório. */
    public static final int GENERATOR_TANK_DRAW_PER_TICK = 24;

    private MoonMachineMath() {
    }

    /** Resultado de um tick de progresso: o novo progresso e se a tarefa terminou. */
    public record Step(int progress, boolean done) {
    }

    /**
     * Um tick de progresso de uma tarefa de {@code total} ticks. Trabalhando: sobe 1 e, ao chegar no total, termina e zera. Parado
     * (sem energia, sem entrada, saída cheia): o progresso recua 2 por tick, como na refinaria e na fornalha.
     */
    public static Step advance(boolean working, int progress, int total) {
        if (!working) {
            return new Step(Math.max(0, progress - 2), false);
        }
        int next = progress + 1;
        return next >= Math.max(1, total) ? new Step(0, true) : new Step(next, false);
    }

    /** Quanto passa de um reservatório de {@code buffer} para um destino com {@code space} livres, com a taxa {@code rate}. */
    public static int transfer(int buffer, int space, int rate) {
        return Math.max(0, Math.min(rate, Math.min(buffer, space)));
    }

    /** O eletrolisador pode aceitar mais um gelo (cabe o hidrogênio e o oxigênio que ele produz)? */
    public static boolean canElectrolyze(int h2, int o2) {
        return h2 + ICE_H2_MB <= ELECTROLYZER_H2_CAPACITY && o2 + ICE_O2_UNITS <= ELECTROLYZER_O2_CAPACITY;
    }

    /** A refinaria lunar tem espaço para o combustível de mais um gelo? */
    public static boolean canRefine(int tankMb) {
        return tankMb + REFINERY_FUEL_PER_ICE_MB <= REFINERY_TANK_CAPACITY;
    }

    /** Gelos para encher {@code mb} de combustível químico (arredonda para cima). */
    public static int iceForFuel(int mb) {
        return (mb + REFINERY_FUEL_PER_ICE_MB - 1) / REFINERY_FUEL_PER_ICE_MB;
    }

    /** UE gastos para refinar um gelo. */
    public static int energyPerRefinedIce() {
        return REFINERY_TICKS * REFINERY_POWER;
    }

    /** UE gastos para eletrolisar um gelo. */
    public static int energyPerElectrolyzedIce() {
        return ELECTROLYSIS_TICKS * ELECTROLYSIS_POWER;
    }
}
