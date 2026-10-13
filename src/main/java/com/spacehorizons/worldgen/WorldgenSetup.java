package com.spacehorizons.worldgen;

import com.mojang.serialization.MapCodec;
import com.spacehorizons.SpaceHorizons;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WorldgenSetup {
    /** Gerador de planetas: o datapack escolhe pelo id "spacehorizons:planet". */
    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, SpaceHorizons.MODID);

    private static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, SpaceHorizons.MODID);

    private WorldgenSetup() {
    }

    public static void init(IEventBus modEventBus) {
        CHUNK_GENERATORS.register("planet", () -> PlanetChunkGenerator.CODEC);
        BIOME_SOURCES.register("planet", () -> PlanetBiomeSource.CODEC);
        CHUNK_GENERATORS.register(modEventBus);
        BIOME_SOURCES.register(modEventBus);

        // Eventos de jogo (bus do jogo, não o do mod): tick do wrap em X e comandos
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                PlanetWrap.onLevelTick(level);
            }
        });
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class,
                event -> PlanetCommands.register(event.getDispatcher()));
    }
}
