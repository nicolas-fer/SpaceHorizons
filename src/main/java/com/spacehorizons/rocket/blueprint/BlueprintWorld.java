package com.spacehorizons.rocket.blueprint;

import com.spacehorizons.rocket.RocketAssembler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** A planta no mundo de verdade (servidor e cliente): comparar com o que está construído e salvar o foguete de uma plataforma. */
public final class BlueprintWorld {
    private BlueprintWorld() {
    }

    /** O que está no mundo, normalizado; null se o lugar está vazio (ar, fluido ou bloco substituível como grama e neve fina). */
    public static @Nullable BlockState occupant(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.canBeReplaced()) {
            return null;
        }
        return BlueprintStates.normalize(state);
    }

    /** A planta posta na plataforma em {@code pad} contra o mundo (faltando, errado, certo por bloco). */
    public static BlueprintCheck.Progress check(BlockGetter level, BlockPos pad, Blueprint blueprint) {
        BlockState[] palette = BlueprintStates.palette(blueprint);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        return BlueprintCheck.check(blueprint, (entry, dx, dy, dz) -> {
            BlockState expected = palette[blueprint.stateIndex(entry)];
            cursor.set(pad.getX() + dx, pad.getY() + dy, pad.getZ() + dz);
            return BlueprintCheck.compare(expected, expected == null ? null : occupant(level, cursor));
        });
    }

    /** A planta do foguete sobre a plataforma em {@code pad} (a mesma busca do painel), ou null se não há nada sobre ela. */
    public static @Nullable Blueprint capture(Level level, BlockPos pad, String name, String author) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        return BlueprintCapture.capture(RocketAssembler.worldGrid(level), (x, y, z) -> BlueprintStates.serialize(level.getBlockState(cursor.set(x, y, z))),
                pad.getX(), pad.getY(), pad.getZ(), name, author);
    }

    /**
     * Põe no mundo os blocos da planta que faltam ou estão errados (só o autoteste e quem quiser construir em criativo por código): os estados
     * normalizados, com flag 2 (sem atualizar vizinhos), como a construção por código dos outros cenários. Devolve quantos blocos pôs.
     */
    public static int placeAll(Level level, BlockPos pad, Blueprint blueprint) {
        BlockState[] palette = BlueprintStates.palette(blueprint);
        int placed = 0;
        for (int i = 0; i < blueprint.size(); i++) {
            BlockState state = palette[blueprint.stateIndex(i)];
            if (state == null) {
                continue;
            }
            BlockPos pos = pad.offset(blueprint.dx(i), blueprint.dy(i), blueprint.dz(i));
            if (!state.equals(occupant(level, pos))) {
                level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                placed++;
            }
        }
        return placed;
    }
}
