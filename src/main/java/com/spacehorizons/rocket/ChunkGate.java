package com.spacehorizons.rocket;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chunks à frente do foguete (lógica pura): quais pedir e quando segurar a simulação. A 20 a 90 blocos por tick o foguete cruza
 * vários chunks por tick e o vanilla só carrega o que o jogador "vê"; então o servidor pede, à frente, um CORREDOR contíguo de
 * chunks (a pegada do foguete ao longo da trajetória prevista, ver {@link #corridor}) e SEGURA o passo se faltar chunk nos
 * próximos {@link #GATING_STEPS} ticks, até {@link #MAX_HELD_TICKS} ticks (depois segue com aviso: um chunk que nunca carrega não
 * pode prender o piloto para sempre).
 *
 * <p>Antes eram sondas esparsas (ticks 1, 2, 4, 8, 16, 32 à frente, raio 0): os chunks ENTRE as sondas só eram pedidos quando
 * entravam na janela de 4 ticks, e a geração demora mais que isso, então o foguete andava um passo e esperava outro (HOLD de 50%
 * entre 20 e 90 blocos por tick, com o servidor ocioso). O corredor é pedido com antecedência proporcional à latência de geração
 * medida ({@link Requester#lookaheadTicks}), em ordem de proximidade e com teto de pedidos novos por tick e de pendentes
 * ({@link Requester}), para a geração andar em paralelo à viagem sem afogar o gerador nem o cliente com rajadas.
 */
public final class ChunkGate {
    /** Ticks à frente cujos chunks (pegada inteira) precisam estar carregados para a simulação andar. */
    public static final int[] GATING_STEPS = {1, 2, 4};
    /** Teto de ticks segurados seguidos antes de seguir mesmo sem os chunks. */
    public static final int MAX_HELD_TICKS = 200;
    /** Raio do ticket nos chunks mais próximos (mantém o chunk central em ticking de entidades); nos outros é 0. */
    public static final int NEAR_RADIUS = 2;
    /** Até este tick à frente o ticket tem {@link #NEAR_RADIUS}. */
    public static final int NEAR_STEPS = 2;
    /** Espaçamento (blocos) das amostras ao longo da trajetória: menos que um chunk, para o corredor não ter buracos. */
    static final double SAMPLE_SPACING = 8.0;
    /** Teto de amostras por corredor (a conta é refeita a cada tick). */
    static final int MAX_SAMPLES = 1024;

    /** Um chunk do corredor: o chunk, os ticks à frente em que a pegada chega a ele, o raio do ticket e se ele conta para segurar o passo. */
    public record Cell(int chunkX, int chunkZ, int ticksAhead, int radius, boolean gating) {
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
     * O corredor de chunks a partir da posição {@code (x, z)} com a velocidade das coordenadas {@code (vx, vz)} por tick: todos os
     * chunks que a pegada ({@code x ± halfExtent}, {@code z ± halfExtent}) toca ao longo dos próximos {@code lookaheadTicks} ticks, do mais
     * perto ao mais longe. Os chunks da pegada nos ticks de {@link #GATING_STEPS} (e no tick 0) são os que seguram o passo. {@code
     * circumference} > 0 normaliza X na costura do planeta (o terreno é periódico); 0 não normaliza (espaço aberto).
     */
    public static List<Cell> corridor(double x, double z, double vx, double vz, double circumference, double halfExtent, int lookaheadTicks) {
        Map<Long, Cell> cells = new LinkedHashMap<>();
        // 1) a pegada nos ticks que seguram o passo (e o do próprio foguete)
        addFootprint(cells, x, z, 0.0, circumference, halfExtent, 0, true);
        for (int g : GATING_STEPS) {
            addFootprint(cells, x + vx * g, z + vz * g, g, circumference, halfExtent, g, true);
        }
        // 2) o corredor contínuo até o fim da antecedência
        int last = Math.max(0, lookaheadTicks);
        double distance = Math.hypot(vx, vz) * last;
        int samples = distance < 1.0 ? 0 : (int) Math.min(MAX_SAMPLES, Math.ceil(distance / SAMPLE_SPACING));
        for (int i = 1; i <= samples; i++) {
            double t = last * (double) i / samples;
            addFootprint(cells, x + vx * t, z + vz * t, t, circumference, halfExtent, (int) Math.ceil(t), false);
        }
        List<Cell> ordered = new ArrayList<>(cells.values());
        ordered.sort((a, b) -> Integer.compare(a.ticksAhead(), b.ticksAhead()));
        return ordered;
    }

    private static void addFootprint(Map<Long, Cell> cells, double px, double pz, double t, double circumference, double halfExtent, int ticks,
            boolean gating) {
        double nx = normalize(px, circumference);
        int cx0 = (int) Math.floor(nx - halfExtent) >> 4;
        int cx1 = (int) Math.floor(nx + halfExtent) >> 4;
        int cz0 = (int) Math.floor(pz - halfExtent) >> 4;
        int cz1 = (int) Math.floor(pz + halfExtent) >> 4;
        int radius = ticks <= NEAR_STEPS ? NEAR_RADIUS : 0;
        for (int ax = cx0; ax <= cx1; ax++) {
            int wx = wrapChunk(ax, circumference);
            for (int az = cz0; az <= cz1; az++) {
                long key = key(wx, az);
                Cell old = cells.get(key);
                if (old == null) {
                    cells.put(key, new Cell(wx, az, ticks, radius, gating));
                } else if (gating && !old.gating()) {
                    cells.put(key, new Cell(wx, az, Math.min(old.ticksAhead(), ticks), Math.max(old.radius(), radius), true));
                }
            }
        }
    }

    static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /** Leva o chunk X para a faixa de chunks de um planeta de {@code circumference} blocos (a costura); 0 não normaliza. */
    static int wrapChunk(int chunkX, double circumference) {
        if (!(circumference > 0.0)) {
            return chunkX;
        }
        int period = (int) Math.round(circumference / 16.0);
        int half = period / 2;
        return Math.floorMod(chunkX + half, period) - half;
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

    // ---------------------------------------------------------------- pedidos

    /** Onde os pedidos de chunk são feitos (o nível do servidor, em produção; um dublê nos testes). */
    public interface Sink {
        /** Pede (ou renova) o ticket do chunk com o raio dado. */
        void add(int chunkX, int chunkZ, int radius);

        /** O chunk está carregado? */
        boolean loaded(int chunkX, int chunkZ);
    }

    /**
     * Pede o corredor aos poucos e mede a latência da geração. A cada tick: chunks novos do corredor são pedidos do mais perto ao mais
     * longe, até {@link #MAX_NEW_PER_TICK} e enquanto houver menos de {@link #MAX_PENDING} pedidos ainda sem carregar (os que seguram o
     * passo são sempre pedidos); os já pedidos são renovados a cada {@link #RENEW_TICKS} (o ticket ENDER_PEARL expira em 40); os que saíram
     * do corredor são esquecidos. A latência (ticks entre pedir e carregar) vira uma média móvel que dá a antecedência do corredor.
     */
    public static final class Requester {
        /** Pedidos novos por tick, no máximo (não afoga o gerador nem manda ao cliente uma rajada de chunks). */
        public static final int MAX_NEW_PER_TICK = 32;
        /** Chunks pedidos e ainda não carregados, no máximo (a fila do gerador). */
        public static final int MAX_PENDING = 192;
        /** Ticks entre renovações de um ticket (o ENDER_PEARL vale 40). */
        public static final int RENEW_TICKS = 15;
        /** Ticks sem renovar depois dos quais o chunk é esquecido (saiu do corredor). */
        static final int FORGET_TICKS = 60;
        /** Antecedência mínima e máxima do corredor, em ticks. */
        public static final int MIN_LOOKAHEAD = 12;
        public static final int MAX_LOOKAHEAD = 80;
        /** Latência assumida (ticks) até a primeira medição. */
        static final double INITIAL_LATENCY = 10.0;
        static final double LATENCY_SMOOTHING = 0.15;

        private static final class Entry {
            final long firstTick;
            long lastAdd;
            int radius;
            boolean loaded;

            Entry(long tick, int radius) {
                this.firstTick = tick;
                this.lastAdd = tick;
                this.radius = radius;
            }
        }

        private final Map<Long, Entry> requested = new LinkedHashMap<>();
        private double latency = INITIAL_LATENCY;
        private int pending;
        private int addedLastTick;

        /** Antecedência (ticks) do corredor: a janela de segurar mais o dobro da latência medida, entre os limites. */
        public int lookaheadTicks() {
            int ticks = (int) Math.round(GATING_STEPS[GATING_STEPS.length - 1] + 2.0 * this.latency);
            return Math.max(MIN_LOOKAHEAD, Math.min(MAX_LOOKAHEAD, ticks));
        }

        /** Latência média de geração medida (ticks entre pedir e carregar). */
        public double latency() {
            return this.latency;
        }

        /** Chunks pedidos e ainda não carregados. */
        public int pending() {
            return this.pending;
        }

        /** Chunks conhecidos (pedidos nos últimos {@link #FORGET_TICKS} ticks). */
        public int tracked() {
            return this.requested.size();
        }

        /** Pedidos feitos (novos ou renovações) no último {@link #update}. */
        public int addedLastTick() {
            return this.addedLastTick;
        }

        /**
         * Um tick: pede o corredor {@code cells} em {@code sink} e devolve se algum chunk que segura o passo ainda não está carregado.
         */
        public boolean update(long tick, List<Cell> cells, Sink sink) {
            int added = 0;
            int newRequests = 0;
            boolean missing = false;
            for (Cell cell : cells) {
                long key = key(cell.chunkX(), cell.chunkZ());
                Entry entry = this.requested.get(key);
                if (entry == null) {
                    boolean allowed = cell.gating() || (newRequests < MAX_NEW_PER_TICK && this.pending < MAX_PENDING);
                    if (allowed) {
                        entry = new Entry(tick, cell.radius());
                        entry.loaded = sink.loaded(cell.chunkX(), cell.chunkZ());
                        this.requested.put(key, entry);
                        sink.add(cell.chunkX(), cell.chunkZ(), cell.radius());
                        added++;
                        newRequests++;
                        if (!entry.loaded) {
                            this.pending++;
                        }
                    }
                } else if (tick - entry.lastAdd >= RENEW_TICKS || cell.radius() > entry.radius) {
                    entry.radius = Math.max(entry.radius, cell.radius());
                    entry.lastAdd = tick;
                    sink.add(cell.chunkX(), cell.chunkZ(), entry.radius);
                    added++;
                }
                if (cell.gating() && !sink.loaded(cell.chunkX(), cell.chunkZ())) {
                    missing = true;
                }
            }
            this.addedLastTick = added;
            refresh(tick, sink);
            return missing;
        }

        /** Marca os que carregaram (medindo a latência) e esquece os que saíram do corredor. */
        private void refresh(long tick, Sink sink) {
            var it = this.requested.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                Entry entry = e.getValue();
                if (!entry.loaded) {
                    int cx = (int) (e.getKey() >> 32);
                    int cz = (int) (long) e.getKey();
                    if (sink.loaded(cx, cz)) {
                        entry.loaded = true;
                        this.pending--;
                        long waited = tick - entry.firstTick;
                        if (waited > 0) {
                            this.latency += LATENCY_SMOOTHING * (waited - this.latency);
                        }
                    }
                }
                if (tick - entry.lastAdd > FORGET_TICKS) {
                    if (!entry.loaded) {
                        this.pending--;
                    }
                    it.remove();
                }
            }
        }

        /** Esquece tudo (o voo acabou, os tickets expiram sozinhos). */
        public void reset() {
            this.requested.clear();
            this.pending = 0;
            this.addedLastTick = 0;
            this.latency = INITIAL_LATENCY;
        }
    }
}
