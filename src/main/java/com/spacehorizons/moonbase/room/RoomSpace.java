package com.spacehorizons.moonbase.room;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * O mundo como o scanner de salas o vê: uma célula é PAREDE se o bloco segura o ar, AR se não segura e VAZIO se o chunk não
 * está carregado ou a altura sai do mundo (conta como vazamento, porque não dá para saber o que há ali).
 *
 * Segura o ar: bloco de colisão em cubo cheio (pedra, aço, vidro, a eclusa da nave fechada...), porta e alçapão FECHADOS. Não
 * seguram (vazam): ar, laje, escada, grade, vidraça, cerca, tocha, porta ou alçapão abertos. As paredes de uma sala selada
 * devem ser de blocos inteiros.
 */
public final class RoomSpace implements RoomScan.Space {
    private final Level level;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public RoomSpace(Level level) {
        this.level = level;
    }

    @Override
    public RoomScan.Cell at(int x, int y, int z) {
        if (level.isOutsideBuildHeight(y)) {
            return RoomScan.Cell.VOID;
        }
        cursor.set(x, y, z);
        if (!level.hasChunkAt(cursor)) {
            return RoomScan.Cell.VOID;
        }
        return isWall(level, cursor, level.getBlockState(cursor)) ? RoomScan.Cell.WALL : RoomScan.Cell.AIR;
    }

    /** O bloco segura o ar? */
    public static boolean isWall(Level level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.getBlock() instanceof DoorBlock) {
            return !state.getValue(DoorBlock.OPEN);
        }
        if (state.getBlock() instanceof TrapDoorBlock) {
            return !state.getValue(TrapDoorBlock.OPEN);
        }
        return state.isCollisionShapeFullBlock(level, pos);
    }
}
