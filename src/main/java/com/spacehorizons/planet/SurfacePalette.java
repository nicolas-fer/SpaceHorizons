package com.spacehorizons.planet;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocos e bioma de cada SurfaceType. Chunks e LODs do DH leem daqui para que a cor de longe
 * seja idêntica à de perto (é isso que evita o "salto" visual entre camadas).
 */
public final class SurfacePalette {
    private SurfacePalette() {
    }

    public static BlockState top(SurfaceType type) {
        return switch (type) {
            case OCEAN, BEACH, DESERT -> Blocks.SAND.defaultBlockState();
            case PLAINS, FOREST -> Blocks.GRASS_BLOCK.defaultBlockState();
            case MOUNTAIN -> Blocks.STONE.defaultBlockState();
            case SNOW -> Blocks.SNOW_BLOCK.defaultBlockState();
            case POLAR_ICE -> Blocks.PACKED_ICE.defaultBlockState();
        };
    }

    /** Camada logo abaixo do topo (alguns blocos), antes da pedra. */
    public static BlockState filler(SurfaceType type) {
        return switch (type) {
            case OCEAN, BEACH -> Blocks.SAND.defaultBlockState();
            case DESERT -> Blocks.SANDSTONE.defaultBlockState();
            case PLAINS, FOREST -> Blocks.DIRT.defaultBlockState();
            case MOUNTAIN, SNOW -> Blocks.STONE.defaultBlockState();
            case POLAR_ICE -> Blocks.PACKED_ICE.defaultBlockState();
        };
    }

    public static ResourceKey<Biome> biome(SurfaceType type) {
        return switch (type) {
            case OCEAN -> Biomes.OCEAN;
            case BEACH -> Biomes.BEACH;
            case PLAINS -> Biomes.PLAINS;
            case FOREST -> Biomes.FOREST;
            case DESERT -> Biomes.DESERT;
            case MOUNTAIN -> Biomes.STONY_PEAKS;
            case SNOW -> Biomes.SNOWY_SLOPES;
            case POLAR_ICE -> Biomes.ICE_SPIKES;
        };
    }
}
