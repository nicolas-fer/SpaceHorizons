package com.spacehorizons.rocket;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import com.spacehorizons.content.ModBlocks;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * A estrutura de tanques a que um bloco pertence, no mundo (servidor ou cliente): acha os tanques ligados, parte-os pela mesma regra do
 * censo ({@link Multiblocks#tileTanks}) e devolve a coluna formada que contém o bloco (ou o bloco sozinho). O combustível é da
 * ESTRUTURA: o galão enche qualquer bloco dela e a carga se reparte por igual entre os blocos; desmontar o foguete devolve o combustível
 * proporcional por bloco ({@link RocketLanding}).
 */
public final class TankStructures {
    /** Teto de tanques examinados de uma vez; um conjunto maior que isso trata o bloco olhado como avulso. */
    private static final int MAX_CLUSTER = 4096;

    private TankStructures() {
    }

    /** A estrutura ({@code box} é null se avulso), os blocos dela (posições do mundo), a carga somada e a capacidade, em mB. */
    public record Found(Multiblocks.@Nullable Box box,List<BlockPos> blocks, int fuelMb, int capacityMb) {
        public boolean formed() {
            return this.box != null;
        }
    }

    private static boolean isTank(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.ROCKET_FUEL_TANK.get());
    }

    /** A estrutura que contém {@code pos} (que deve ser um tanque). */
    public static Found at(BlockGetter level, BlockPos pos) {
        List<BlockPos> blocks = new ArrayList<>();
        Multiblocks.Box box = null;
        if (isTank(level, pos)) {
            LongArrayList cluster = cluster(level, pos);
            if (cluster.size() < MAX_CLUSTER) {
                box = Multiblocks.boxAt(Multiblocks.tileTanks(cluster.toLongArray()), pos.getX(), pos.getY(), pos.getZ());
            }
        }
        if (box != null) {
            for (int y = box.y(); y < box.y() + box.h(); y++) {
                for (int z = box.z(); z < box.z() + box.d(); z++) {
                    for (int x = box.x(); x < box.x() + box.w(); x++) {
                        blocks.add(new BlockPos(x, y, z));
                    }
                }
            }
        } else {
            blocks.add(pos.immutable());
        }
        int fuel = 0;
        for (BlockPos p : blocks) {
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof RocketFuelTankBlockEntity tank) {
                fuel += tank.fuelMb();
            }
        }
        return new Found(box, List.copyOf(blocks), fuel, blocks.size() * RocketSpecs.TANK_CAPACITY_MB);
    }

    private static LongArrayList cluster(BlockGetter level, BlockPos start) {
        LongArrayList cells = new LongArrayList();
        LongOpenHashSet seen = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start.asLong());
        queue.add(start.immutable());
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty() && cells.size() < MAX_CLUSTER) {
            BlockPos at = queue.poll();
            cells.add(at.asLong());
            for (Direction side : Direction.values()) {
                neighbor.setWithOffset(at, side);
                if (isTank(level, neighbor) && seen.add(neighbor.asLong())) {
                    queue.add(neighbor.immutable());
                }
            }
        }
        return cells;
    }

    /**
     * Põe {@code mb} (limitado à capacidade) na estrutura, repartido por igual entre os blocos. Servidor. Devolve quanto ficou na estrutura
     * depois, em mB.
     */
    public static int setTotal(BlockGetter level, Found found, int mb) {
        int[] caps = new int[found.blocks().size()];
        java.util.Arrays.fill(caps, RocketSpecs.TANK_CAPACITY_MB);
        int[] shares = RocketFuel.distribute(Math.max(0, Math.min(mb, found.capacityMb())), caps);
        int total = 0;
        for (int i = 0; i < shares.length; i++) {
            if (level.getBlockEntity(found.blocks().get(i)) instanceof RocketFuelTankBlockEntity tank) {
                tank.setFuel(shares[i]);
                total += shares[i];
            }
        }
        return total;
    }
}
