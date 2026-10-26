package com.spacehorizons.rocket;

import org.jspecify.annotations.Nullable;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Uma mudança num bloco do retrato (porta que abre, conteúdo de baú que muda): é o que o servidor aplica ao retrato da entidade e manda aos
 * clientes no lugar do retrato inteiro ({@code PortraitDeltaPayload}); uma nave grande tem megabytes de retrato e uma porta mudou um estado.
 * O estado vai pelo id do registro de estados ({@code -1} = não mexe no estado) e o NBT do block entity vai opcional.
 *
 * @param pos posição local (asLong)
 * @param stateId id do estado em {@link Block#BLOCK_STATE_REGISTRY}, ou -1 para manter o estado
 * @param setNbt se esta mudança mexe no NBT do block entity
 * @param nbt o NBT novo (sem x/y/z); {@code null} com {@code setNbt} remove o block entity
 */
public record PortraitChange(long pos, int stateId, boolean setNbt, @Nullable CompoundTag nbt) {
    private static final int HAS_NBT = 1;
    private static final int NBT_PRESENT = 2;

    /** Troca o estado do bloco (e, se o novo for ar, o block entity some junto). */
    public static PortraitChange state(BlockPos pos, BlockState state) {
        return new PortraitChange(pos.asLong(), Block.BLOCK_STATE_REGISTRY.getId(state), false, null);
    }

    /** Troca o NBT do block entity da posição. */
    public static PortraitChange nbt(BlockPos pos, @Nullable CompoundTag nbt) {
        return new PortraitChange(pos.asLong(), -1, true, nbt == null ? null : nbt.copy());
    }

    public void write(ByteBuf out) {
        out.writeLong(this.pos);
        VarInt.write(out, this.stateId + 1);
        int flags = (this.setNbt ? HAS_NBT : 0) | (this.nbt != null ? NBT_PRESENT : 0);
        out.writeByte(flags);
        if (this.nbt != null) {
            ByteBufCodecs.COMPOUND_TAG.encode(out, this.nbt);
        }
    }

    public static PortraitChange read(ByteBuf in) {
        long pos = in.readLong();
        int stateId = VarInt.read(in) - 1;
        int flags = in.readByte();
        CompoundTag nbt = (flags & NBT_PRESENT) != 0 ? ByteBufCodecs.COMPOUND_TAG.decode(in) : null;
        return new PortraitChange(pos, stateId, (flags & HAS_NBT) != 0, nbt);
    }
}
