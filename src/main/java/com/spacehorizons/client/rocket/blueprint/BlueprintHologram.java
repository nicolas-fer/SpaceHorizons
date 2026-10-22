package com.spacehorizons.client.rocket.blueprint;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.rocket.blueprint.Blueprint;
import com.spacehorizons.rocket.blueprint.BlueprintCheck;
import com.spacehorizons.rocket.blueprint.BlueprintWorld;
import com.spacehorizons.rocket.blueprint.LaunchPadBlockEntity;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * O estado do holograma no cliente (só o render thread e o tick do cliente, que são a mesma thread): para cada plataforma com planta, a
 * comparação planta × mundo, refeita no máximo a cada {@link #CHECK_INTERVAL} ticks (não por quadro); e as malhas dos fantasmas, uma por
 * planta, assadas em worker thread.
 */
public final class BlueprintHologram {
    /** Ticks entre duas comparações da mesma plataforma. */
    public static final int CHECK_INTERVAL = 10;
    /** Uma plataforma sem consulta por este tempo (ticks) sai do cache. */
    private static final int FORGET_AFTER = 200;
    /** Teto de plataformas ligadas que o painel percorre atrás da que tem a planta. */
    private static final int MAX_PADS = 512;

    /** A comparação de uma plataforma: a planta comparada e o resultado. */
    public static final class State {
        final Blueprint blueprint;
        BlueprintCheck.@Nullable Progress progress;
        long checkedAt;
        long usedAt;

        State(Blueprint blueprint) {
            this.blueprint = blueprint;
        }

        public Blueprint blueprint() {
            return this.blueprint;
        }

        public BlueprintCheck.@Nullable Progress progress() {
            return this.progress;
        }
    }

    private static final Map<BlockPos, State> STATES = new HashMap<>();
    private static final Map<Blueprint, CompletableFuture<GhostMesh>> MESHES = new WeakHashMap<>();
    /** A plataforma com a planta achada a partir de outra (painel), com o tick da busca. */
    private static final Map<BlockPos, Object[]> ANCHORS = new HashMap<>();

    private BlueprintHologram() {
    }

    /** A comparação da plataforma {@code pad} com a planta {@code blueprint}, refeita se passou o intervalo ou se a planta mudou. */
    public static State state(Level level, BlockPos pad, Blueprint blueprint) {
        long now = level.getGameTime();
        State s = STATES.get(pad);
        if (s == null || s.blueprint != blueprint) {
            s = new State(blueprint);
            STATES.put(pad.immutable(), s);
        }
        s.usedAt = now;
        if (s.progress == null || now - s.checkedAt >= CHECK_INTERVAL || now < s.checkedAt) {
            s.progress = BlueprintWorld.check(level, pad, blueprint);
            s.checkedAt = now;
        }
        return s;
    }

    /** Força a próxima comparação (o autoteste, logo depois de pôr blocos). */
    public static void invalidate() {
        for (State s : STATES.values()) {
            s.progress = null; // (não mexer em checkedAt: now - Long.MIN_VALUE estoura)
        }
        ANCHORS.clear();
    }

    /** A malha dos fantasmas da planta, ou null enquanto assa. */
    public static @Nullable GhostMesh mesh(Blueprint blueprint) {
        CompletableFuture<GhostMesh> future = MESHES.get(blueprint);
        if (future == null) {
            Minecraft minecraft = Minecraft.getInstance();
            BlockStateModelSet models = minecraft.getModelManager().getBlockStateModelSet();
            BlockColors colors = minecraft.getBlockColors();
            future = CompletableFuture.supplyAsync(() -> GhostMesh.bake(blueprint, models, colors), Util.backgroundExecutor());
            future.exceptionally(t -> {
                SpaceHorizons.LOGGER.error("Falha ao assar o holograma da planta {}", blueprint, t);
                return null;
            });
            MESHES.put(blueprint, future);
        }
        return future.isDone() && !future.isCompletedExceptionally() ? future.join() : null;
    }

    /** Recarga de recursos: o atlas muda e os UVs antigos não valem (o renderer da plataforma chama ao ser recriado). */
    public static void clearMeshes() {
        MESHES.clear();
    }

    /** Tira do cache as plataformas sem consulta recente (chamado no tick do cliente). */
    static void prune(Level level) {
        long now = level.getGameTime();
        Iterator<State> it = STATES.values().iterator();
        while (it.hasNext()) {
            State s = it.next();
            if (now - s.usedAt > FORGET_AFTER || now < s.usedAt) {
                it.remove();
            }
        }
        ANCHORS.values().removeIf(a -> now - (long) a[1] > FORGET_AFTER || now < (long) a[1]);
    }

    static void clearAll() {
        STATES.clear();
        ANCHORS.clear();
        MESHES.clear();
    }

    /** A planta ativa numa plataforma (lida do block entity do cliente), ou null. */
    public static @Nullable Blueprint blueprintAt(Level level, BlockPos pad) {
        return level.getBlockEntity(pad) instanceof LaunchPadBlockEntity be ? be.blueprint() : null;
    }

    /**
     * A plataforma com planta ligada à plataforma {@code pad} (ela mesma, ou outra do mesmo conjunto de plataformas), para o painel de qualquer
     * plataforma do conjunto mostrar o progresso. A busca é refeita a cada 20 ticks por plataforma consultada.
     */
    public static @Nullable BlockPos anchorFor(Level level, BlockPos pad) {
        if (blueprintAt(level, pad) != null) {
            return pad;
        }
        long now = level.getGameTime();
        Object[] cached = ANCHORS.get(pad);
        if (cached != null && now - (long) cached[1] < 20 && now >= (long) cached[1]) {
            BlockPos found = (BlockPos) cached[0];
            return found != null && blueprintAt(level, found) != null ? found : null;
        }
        BlockPos found = null;
        LongOpenHashSet seen = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(pad.asLong());
        queue.add(pad.immutable());
        while (!queue.isEmpty() && found == null) {
            BlockPos at = queue.poll();
            for (Direction side : Direction.values()) {
                BlockPos next = at.relative(side);
                if (seen.size() < MAX_PADS && level.getBlockState(next).is(ModBlocks.LAUNCH_PAD.get()) && seen.add(next.asLong())) {
                    if (blueprintAt(level, next) != null) {
                        found = next;
                        break;
                    }
                    queue.add(next);
                }
            }
        }
        ANCHORS.put(pad.immutable(), new Object[] {found, now});
        return found;
    }

    /** O progresso da planta ligada à plataforma {@code pad} (painel e autoteste), ou null se não há planta. */
    public static @Nullable State forPanel(Level level, BlockPos pad) {
        BlockPos anchor = anchorFor(level, pad);
        if (anchor == null) {
            return null;
        }
        Blueprint blueprint = blueprintAt(level, anchor);
        return blueprint == null ? null : state(level, anchor, blueprint);
    }
}
