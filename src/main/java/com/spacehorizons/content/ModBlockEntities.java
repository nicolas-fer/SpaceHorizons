package com.spacehorizons.content;

import java.util.Set;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.moonbase.machine.MoonBlockEntity;
import com.spacehorizons.rocket.EngineChargeBlockEntity;
import com.spacehorizons.rocket.RocketFuelTankBlockEntity;
import com.spacehorizons.rocket.blueprint.LaunchPadBlockEntity;
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RocketFuelTankBlockEntity>> ROCKET_FUEL_TANK =
            TYPES.register(ContentIds.ROCKET_FUEL_TANK,
                    () -> new BlockEntityType<>(RocketFuelTankBlockEntity::new, Set.of(ModBlocks.ROCKET_FUEL_TANK.get(), ModBlocks.HYDROGEN_TANK.get(),
                            ModBlocks.XENON_TANK.get())));

    /** A carga dos motores do Tier 2 que guardam algo dentro: o núcleo do nuclear e o hélio-3 do de fusão. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EngineChargeBlockEntity>> ENGINE_CHARGE =
            TYPES.register("engine_charge",
                    () -> new BlockEntityType<>(EngineChargeBlockEntity::new, Set.of(ModBlocks.NUCLEAR_ENGINE.get(), ModBlocks.FUSION_ENGINE.get())));

    /** A planta de montagem ativa na plataforma de lançamento (o holograma). */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaunchPadBlockEntity>> LAUNCH_PAD =
            TYPES.register(ContentIds.LAUNCH_PAD, () -> new BlockEntityType<>(LaunchPadBlockEntity::new, Set.of(ModBlocks.LAUNCH_PAD.get())));

    /** Blocos de energia e máquinas da base lunar (um tipo só; o {@code MachineKind} vem do bloco). */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MoonBlockEntity>> MOON =
            TYPES.register("moon", () -> new BlockEntityType<>(MoonBlockEntity::new, Set.of(ModBlocks.SOLAR_PANEL.get(), ModBlocks.BATTERY.get(),
                    ModBlocks.POWER_CABLE.get(), ModBlocks.OXYGEN_GENERATOR.get(), ModBlocks.HELIUM3_EXTRACTOR.get(), ModBlocks.ELECTROLYZER.get(),
                    ModBlocks.LUNAR_REFINERY.get())));

    private ModBlockEntities() {
    }
}
