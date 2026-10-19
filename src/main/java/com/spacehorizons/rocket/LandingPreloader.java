package com.spacehorizons.rocket;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.spacehorizons.rocket.ImpactPredictor.Point;
import com.spacehorizons.rocket.ImpactPredictor.Prediction;

/**
 * Pré-carga do local de pouso (lógica pura): dado o impacto previsto ({@link ImpactPredictor}), escolhe os chunks (uma área de
 * {@code (2·IMPACT_RADIUS+1)²} em volta do impacto e o trecho final da trajetória) e decide, a cada atualização, quais pedir,
 * renovar e soltar, com a taxa de pedidos limitada para não afogar o gerador. Quem chama dá o {@link Sink} (tickets assíncronos
 * do servidor); aqui nada espera chunk.
 */
public final class LandingPreloader {
    /** Raio da área em volta do impacto previsto, em chunks (3 = 7×7). */
    public static final int IMPACT_RADIUS = 3;
    /** Raio dos chunks em volta de cada ponto do trecho final da trajetória. */
    public static final int PATH_RADIUS = 1;
    /** Altura (sobre o terreno do impacto) do trecho final da trajetória que é pré-carregado. */
    public static final double PATH_HEIGHT = 1_500.0;
    /** Teto de chunks mantidos pedidos ao mesmo tempo. */
    public static final int MAX_CHUNKS = 400;
    /** Máximo de chunks novos pedidos por atualização (uma por segundo: é a taxa por segundo). */
    public static final int MAX_NEW_PER_UPDATE = 40;
    /** Máximo de chunks pedidos e ainda não carregados; acima disso não se pede mais (o gerador está ocupado). */
    public static final int MAX_PENDING = 96;
    /** Só pré-carrega com o impacto a menos de tantos ticks (1 minuto): antes disso a previsão ainda muda demais. */
    public static final int MAX_TICKS_TO_IMPACT = 1_200;
    /** Ticks entre duas atualizações (1 por segundo). */
    public static final int UPDATE_INTERVAL = 20;

    /** O mundo, visto pela pré-carga: pedir, soltar e consultar chunks (a consulta não pode esperar). */
    public interface Sink {
        void add(int chunkX, int chunkZ);

        void remove(int chunkX, int chunkZ);

        boolean loaded(int chunkX, int chunkZ);
    }

    /** O que o painel mostra. */
    public enum State {
        /** Sem previsão de impacto no alcance: nada a mostrar. */
        IDLE,
        LOADING,
        READY
    }

    private final Set<Long> requested = new LinkedHashSet<>();
    private List<Long> desired = List.of();
    private State state = State.IDLE;
    private int percent;
    private @Nullable Prediction last;

    /** O estado do painel. */
    public State state() {
        return this.state;
    }

    /** Porcentagem de chunks da área já carregados (0..100). */
    public int percent() {
        return this.percent;
    }

    /** A última previsão usada, ou null. */
    public @Nullable Prediction prediction() {
        return this.last;
    }

    /** Quantos chunks estão pedidos agora. */
    public int requestedCount() {
        return this.requested.size();
    }

    /**
     * Vale prever o impacto agora? Só descendo (depois do apoastro: subindo, o "impacto" balístico cairia a milhares de blocos à frente
     * e a pré-carga seria desperdício) e abaixo da altitude de saída do corpo (em órbita ou na subida não se carrega terreno: a
     * circularização muda o "impacto" a cada segundo).
     */
    public static boolean worthPredicting(double altitude, double exitAltitude, double verticalSpeed) {
        return verticalSpeed < 0.0 && altitude < exitAltitude;
    }

    // ---------------------------------------------------------------- seleção

    /** Empacota (x, z) de chunk numa chave. */
    public static long key(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
    }

    public static int keyX(long key) {
        return (int) key;
    }

    public static int keyZ(long key) {
        return (int) (key >> 32);
    }

    /**
     * Os chunks a ter carregados, em ordem de prioridade: a área em volta do impacto (do centro para fora) e depois o trecho final
     * da trajetória, do chão para cima. {@code circumference} > 0 leva X à costura do planeta (o terreno é periódico). No máximo
     * {@link #MAX_CHUNKS}.
     */
    public static List<Long> select(Prediction p, double circumference) {
        Set<Long> set = new LinkedHashSet<>();
        int ix = chunkOf(ChunkGate.normalize(p.x(), circumference));
        int iz = chunkOf(p.z());
        for (int ring = 0; ring <= IMPACT_RADIUS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                        set.add(key(ix + dx, iz + dz));
                    }
                }
            }
        }
        long lastCenter = Long.MIN_VALUE;
        List<Point> path = p.path();
        for (int i = path.size() - 1; i >= 0 && set.size() < MAX_CHUNKS; i--) {
            Point pt = path.get(i);
            int cx = chunkOf(ChunkGate.normalize(pt.x(), circumference));
            int cz = chunkOf(pt.z());
            long center = key(cx, cz);
            if (center == lastCenter) {
                continue;
            }
            lastCenter = center;
            for (int dx = -PATH_RADIUS; dx <= PATH_RADIUS; dx++) {
                for (int dz = -PATH_RADIUS; dz <= PATH_RADIUS; dz++) {
                    if (set.size() < MAX_CHUNKS) {
                        set.add(key(cx + dx, cz + dz));
                    }
                }
            }
        }
        return new ArrayList<>(set);
    }

    private static int chunkOf(double blockCoordinate) {
        return (int) Math.floor(blockCoordinate) >> 4;
    }

    // ---------------------------------------------------------------- atualização

    /**
     * Uma atualização (1 por segundo). {@code prediction} null (sem impacto no alcance, pousou) solta tudo. Senão: solta o que a
     * previsão nova não quer mais, renova o que fica (o ticket expira), pede chunks novos (até {@link #MAX_NEW_PER_UPDATE} e
     * enquanto os pendentes não passam de {@link #MAX_PENDING}) e recalcula o progresso.
     */
    public void update(@Nullable Prediction prediction, double circumference, Sink sink) {
        if (prediction == null) {
            releaseAll(sink);
            return;
        }
        this.last = prediction;
        this.desired = select(prediction, circumference);
        Set<Long> wanted = new HashSet<>(this.desired);
        List<Long> stale = new ArrayList<>();
        for (long k : this.requested) {
            if (!wanted.contains(k)) {
                stale.add(k);
            }
        }
        for (long k : stale) {
            sink.remove(keyX(k), keyZ(k));
            this.requested.remove(k);
        }
        int pending = 0;
        for (long k : this.requested) {
            sink.add(keyX(k), keyZ(k)); // renova o ticket
            if (!sink.loaded(keyX(k), keyZ(k))) {
                pending++;
            }
        }
        int fresh = 0;
        for (long k : this.desired) {
            if (fresh >= MAX_NEW_PER_UPDATE || pending >= MAX_PENDING) {
                break;
            }
            if (this.requested.add(k)) {
                sink.add(keyX(k), keyZ(k));
                fresh++;
                pending++;
            }
        }
        refresh(sink);
    }

    /** Recalcula só o progresso (barato: uma consulta por chunk da área), sem pedir nem soltar nada. */
    public void refresh(Sink sink) {
        if (this.last == null || this.desired.isEmpty()) {
            this.state = State.IDLE;
            this.percent = 0;
            return;
        }
        int loaded = 0;
        for (long k : this.desired) {
            if (sink.loaded(keyX(k), keyZ(k))) {
                loaded++;
            }
        }
        this.percent = 100 * loaded / this.desired.size();
        this.state = loaded == this.desired.size() ? State.READY : State.LOADING;
    }

    /** Solta todos os tickets (pouso, batida, sem previsão) e volta a {@link State#IDLE}. */
    public void releaseAll(Sink sink) {
        for (long k : this.requested) {
            sink.remove(keyX(k), keyZ(k));
        }
        this.requested.clear();
        this.desired = List.of();
        this.last = null;
        this.state = State.IDLE;
        this.percent = 0;
    }
}
