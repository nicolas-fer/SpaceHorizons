package com.spacehorizons.worldgen;

import com.mojang.serialization.MapCodec;
import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.worldgen.density.LatitudeCoolingFunction;
import com.spacehorizons.worldgen.density.PeriodicXFunction;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WorldgenSetup {
    /** Gerador de planetas: o datapack escolhe pelo id "spacehorizons:planet". */
    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, SpaceHorizons.MODID);

    private static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, SpaceHorizons.MODID);

    /** Funções de densidade do planeta com o gerador vanilla (periodicidade em X e clima por latitude). */
    private static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, SpaceHorizons.MODID);

    private WorldgenSetup() {
    }

    public static void init(IEventBus modEventBus) {
        CHUNK_GENERATORS.register("planet", () -> PlanetChunkGenerator.CODEC);
        // Terra: NoiseBasedChunkGenerator do vanilla com noise settings próprias (ver data/.../noise_settings/earth.json)
        CHUNK_GENERATORS.register("vanilla_planet", () -> PlanetNoiseChunkGenerator.CODEC);
        BIOME_SOURCES.register("planet", () -> PlanetBiomeSource.CODEC);
        DENSITY_FUNCTION_TYPES.register("periodic_x", () -> PeriodicXFunction.CODEC);
        DENSITY_FUNCTION_TYPES.register("latitude_cooling", () -> LatitudeCoolingFunction.CODEC);
        CHUNK_GENERATORS.register(modEventBus);
        BIOME_SOURCES.register(modEventBus);
        DENSITY_FUNCTION_TYPES.register(modEventBus);

        // Eventos de jogo (bus do jogo, não o do mod): tick do wrap em X e comandos
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                PlanetWrap.onLevelTick(level);
                PlanetGravity.onLevelTick(level);
            }
        });
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class,
                event -> PlanetGravity.onEntityJoin(event.getLevel(), event.getEntity()));
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class,
                event -> PlanetCommands.register(event.getDispatcher()));
    }
}
