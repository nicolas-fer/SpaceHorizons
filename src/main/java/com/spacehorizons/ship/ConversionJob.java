package com.spacehorizons.ship;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.MinecraftServer;

/**
 * Uma conversão que roda em lotes ao longo de vários ticks (D10 do plano): a base mede o tempo gasto em cada tick e amostra o tempo do
 * tick completo do servidor, e entrega tudo no {@link ConversionReport}. Os jobs só rodam no thread do servidor, um de cada vez
 * ({@link ShipConversions}).
 */
abstract class ConversionJob {
    final String direction;
    final long startNanos = System.nanoTime();
    double startupMs;
    int ticks;
    private double jobMsTotal;
    private double jobMsMax;
    private double serverMsTotal;
    private double serverMsMax;
    private int serverSamples;
    final List<String> losses = new ArrayList<>();
    final List<String> notes = new ArrayList<>();
    String error;
    int packetBytes;

    ConversionJob(String direction, double startupMs) {
        this.direction = direction;
        this.startupMs = startupMs;
    }

    /** O id da nave envolvida. */
    abstract int shipId();

    /** Um passo dentro do orçamento; true quando acabou (com ou sem erro). */
    abstract boolean step(MinecraftServer server, ConversionBudget budget);

    /** Para um job que não vai terminar (o foguete sumiu, o servidor parou): desfaz o que for preciso. */
    abstract void abort(MinecraftServer server, String reason);

    /** Dados para o relatório: blocos, block entities e itens da nave. */
    abstract int[] counts();

    /** Chamado pelo gerenciador a cada tick: mede o passo. */
    final boolean run(MinecraftServer server, ConversionBudget budget) {
        long t0 = System.nanoTime();
        // o tick anterior do servidor já terminou (o próprio inclui o nosso trabalho, que roda no fim do tick): é a melhor amostra do custo total
        long[] times = server.getTickTimesNanos();
        long previous = times[(int) Math.floorMod((long) server.getTickCount() - 1L, (long) times.length)];
        if (this.ticks > 0 && previous > 0L) {
            double ms = previous / 1.0e6;
            this.serverMsTotal += ms;
            this.serverMsMax = Math.max(this.serverMsMax, ms);
            this.serverSamples++;
        }
        boolean done;
        try {
            done = step(server, budget);
        } catch (RuntimeException e) {
            this.error = e.getClass().getSimpleName() + ": " + e.getMessage();
            com.spacehorizons.SpaceHorizons.LOGGER.error("[ship-conv] erro no job de conversão", e);
            try {
                abort(server, this.error);
            } catch (RuntimeException e2) {
                com.spacehorizons.SpaceHorizons.LOGGER.error("[ship-conv] erro ao desfazer", e2);
            }
            done = true;
        }
        double ms = (System.nanoTime() - t0) / 1.0e6;
        this.ticks++;
        this.jobMsTotal += ms;
        this.jobMsMax = Math.max(this.jobMsMax, ms);
        return done;
    }

    final ConversionReport report() {
        int[] c = counts();
        return new ConversionReport(this.direction, shipId(), c[0], c[1], c[2], this.ticks, this.startupMs, this.jobMsTotal, this.jobMsMax,
                (System.nanoTime() - this.startNanos) / 1.0e6 + this.startupMs, this.serverSamples == 0 ? 0.0 : this.serverMsTotal / this.serverSamples,
                this.serverMsMax, this.packetBytes, List.copyOf(this.losses), List.copyOf(this.notes), this.error == null, this.error == null ? "" : this.error,
                ConversionBudget.unitsPerTick(), ConversionBudget.millisPerTick());
    }
}
