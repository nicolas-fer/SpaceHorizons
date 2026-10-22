package com.spacehorizons.rocket;

import java.util.ArrayDeque;
import java.util.Map;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mantém o estado dos blocos de motor ({@link RocketEngineBlock#FORM}) no mundo: quando um motor é posto ou quebrado, acha o conjunto
 * de motores ligados a ele, parte esse conjunto em estruturas pela MESMA regra do censo ({@link Multiblocks#tileEngines}) e põe em
 * cada bloco o papel certo (mestre, parte ou avulso). Só roda no servidor.
 */
public final class EngineForming {
    /** Teto de motores examinados de uma vez (um conjunto maior que isso só é refeito até aqui). */
    private static final int MAX_CLUSTER = 4096;
    /** Atualização só para o cliente, sem recalcular vizinhos nem repetir o {@code onPlace}. */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;

    /** Reentrada (o servidor é um thread só) e supressão (a reposição em massa do pouso já traz os estados certos). */
    private static boolean running;
    private static int suspended;

    private EngineForming() {
    }

    /** Roda {@code work} sem refazer estados a cada bloco posto (o pouso e o estaleiro repõem blocos com os estados já corretos). */
    public static void suspended(Runnable work) {
        suspended++;
        try {
            work.run();
        } finally {
            suspended--;
        }
    }

    /** O motor em {@code pos} acabou de ser posto: refaz o conjunto dele. */
    public static void refresh(LevelAccessor level, BlockPos pos) {
        if (running || suspended > 0) {
            return;
        }
        running = true;
        try {
            apply(level, cluster(level, pos, level.getBlockState(pos).getBlock()));
        } finally {
            running = false;
        }
    }

    /** Um bloco saiu de {@code removed}: refaz os conjuntos de motor que o tocavam. */
    public static void refreshAround(LevelAccessor level, BlockPos removed) {
        if (running || suspended > 0) {
            return;
        }
        running = true;
        try {
            BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
            LongOpenHashSet done = new LongOpenHashSet();
            for (Direction side : Direction.values()) {
                neighbor.setWithOffset(removed, side);
                if (isEngine(level, neighbor) && !done.contains(neighbor.asLong())) {
                    LongArrayList cluster = cluster(level, neighbor.immutable(), level.getBlockState(neighbor).getBlock());
                    for (long cell : cluster) {
                        done.add(cell);
                    }
                    apply(level, cluster);
                }
            }
        } finally {
            running = false;
        }
    }

    /** Um motor de qualquer tipo (o químico e os do Tier 2 são todos {@link RocketEngineBlock}). */
    private static boolean isEngine(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof RocketEngineBlock;
    }

    /**
     * Os motores do MESMO bloco ({@code block}) ligados por face a {@code start} (inclusive), em posições do mundo (BlockPos.asLong): cada tipo
     * de motor forma estruturas só com o seu bloco. Teto de {@value #MAX_CLUSTER} blocos.
     */
    public static LongArrayList cluster(net.minecraft.world.level.BlockGetter level, BlockPos start, Block block) {
        LongArrayList cells = new LongArrayList();
        if (!level.getBlockState(start).is(block)) {
            return cells;
        }
        LongOpenHashSet seen = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start.asLong());
        queue.add(start);
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty() && cells.size() < MAX_CLUSTER) {
            BlockPos at = queue.poll();
            cells.add(at.asLong());
            for (Direction side : Direction.values()) {
                neighbor.setWithOffset(at, side);
                if (level.getBlockState(neighbor).is(block) && seen.add(neighbor.asLong())) {
                    queue.add(neighbor.immutable());
                }
            }
        }
        return cells;
    }

    private static void apply(LevelAccessor level, LongArrayList cluster) {
        if (cluster.isEmpty()) {
            return;
        }
        // BlockPos.asLong e RocketAssembly.pack são a mesma codificação
        Map<Long, Multiblocks.EngineRole> roles = Multiblocks.engineRoles(Multiblocks.tileEngines(cluster.toLongArray()));
        for (long cell : cluster) {
            BlockPos pos = BlockPos.of(cell);
            BlockState current = level.getBlockState(pos);
            if (!(current.getBlock() instanceof RocketEngineBlock)) {
                continue;
            }
            BlockState wanted = stateFor(current, roles.getOrDefault(cell, Multiblocks.EngineRole.SOLO));
            if (wanted != current) {
                level.setBlock(pos, wanted, FLAGS);
            }
        }
    }

    /** O estado de motor com o papel dado; o mestre 2x2x2 é refeito virado para o norte (o modelo se estende para +x e +z). */
    public static BlockState stateFor(BlockState engine, Multiblocks.EngineRole role) {
        BlockState state = engine.setValue(RocketEngineBlock.FORM, EngineForm.of(role));
        return state.setValue(RocketEngineBlock.FACING, Direction.NORTH);
    }
}
