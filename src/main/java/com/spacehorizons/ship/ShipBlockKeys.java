package com.spacehorizons.ship;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.HolderLookup;

/** As chaves que o {@link ShipManifest} usa para blocos e block entities, num lugar só para os dois lados da conversão calcularem igual. */
final class ShipBlockKeys {
    private ShipBlockKeys() {
    }

    static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    static void addBlock(ShipManifest.Builder builder, long localPos, BlockState state) {
        builder.block(localPos, blockId(state), state.toString());
    }

    /** O NBT de um block entity como o retrato o guarda: com os metadados e sem x/y/z. */
    static CompoundTag nbtOf(BlockEntity blockEntity, HolderLookup.Provider registries) {
        CompoundTag tag = blockEntity.saveWithFullMetadata(registries);
        tag.remove("x");
        tag.remove("y");
        tag.remove("z");
        return tag;
    }

    /** As chaves de primeiro nível em que dois NBT diferem (para dizer ONDE na mensagem de perda). */
    static String differingKeys(CompoundTag a, CompoundTag b) {
        java.util.TreeSet<String> keys = new java.util.TreeSet<>(a.keySet());
        keys.addAll(b.keySet());
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            if (!java.util.Objects.equals(a.get(key), b.get(key))) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(key);
            }
        }
        return sb.toString();
    }
}
