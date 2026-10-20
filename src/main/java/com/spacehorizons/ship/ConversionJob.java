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
    /** Os números dos ticks do servidor em que o job trabalhou (o tempo de cada um só existe no fim dele: ver {@link #sampleServerTicks}). */
    private final List<Integer> jobTickIds = new ArrayList<>();
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
        this.jobTickIds.add(server.getTickCount());
        boolean done;
        try {
            boolean[] finished = new boolean[1];
            // a conversão repõe e remove os blocos já com os estados certos: sem refazer o conjunto do motor a cada bloco
            com.spacehorizons.rocket.EngineForming.suspended(() -> finished[0] = step(server, budget));
            done = finished[0];
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

    /**
     * Lê o tempo do tick COMPLETO do servidor de cada tick em que o job trabalhou (inclui o nosso trabalho, que roda no fim do tick). Só
     * vale a partir do tick seguinte ao último do job, e só para os últimos 95 ticks (o servidor guarda 100).
     */
    final void sampleServerTicks(MinecraftServer server) {
        long[] times = server.getTickTimesNanos();
        int now = server.getTickCount();
        for (int id : this.jobTickIds) {
            if (id < now && now - id <= 95) {
                double ms = times[Math.floorMod(id, times.length)] / 1.0e6;
                this.serverMsTotal += ms;
                this.serverMsMax = Math.max(this.serverMsMax, ms);
                this.serverSamples++;
            }
        }
    }

    final ConversionReport report() {
        int[] c = counts();
        return new ConversionReport(this.direction, shipId(), c[0], c[1], c[2], this.ticks, this.startupMs, this.jobMsTotal, this.jobMsMax,
                (System.nanoTime() - this.startNanos) / 1.0e6 + this.startupMs, this.serverSamples == 0 ? 0.0 : this.serverMsTotal / this.serverSamples,
                this.serverMsMax, this.packetBytes, List.copyOf(this.losses), List.copyOf(this.notes), this.error == null, this.error == null ? "" : this.error,
                ConversionBudget.unitsPerTick(), ConversionBudget.millisPerTick());
    }
}
