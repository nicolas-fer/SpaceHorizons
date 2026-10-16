package com.spacehorizons.content;

import java.util.Set;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.survival.compressor.OxygenCompressorBlockEntity;
import com.spacehorizons.survival.fuel.RefineryBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Tipos de block entity do mod. */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SpaceHorizons.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RefineryBlockEntity>> REFINERY = TYPES.register(
            ContentIds.REFINERY, () -> new BlockEntityType<>(RefineryBlockEntity::new, Set.of(ModBlocks.REFINERY.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OxygenCompressorBlockEntity>> OXYGEN_COMPRESSOR =
            TYPES.register(ContentIds.OXYGEN_COMPRESSOR,
                    () -> new BlockEntityType<>(OxygenCompressorBlockEntity::new, Set.of(ModBlocks.OXYGEN_COMPRESSOR.get())));

    private ModBlockEntities() {
    }
}
