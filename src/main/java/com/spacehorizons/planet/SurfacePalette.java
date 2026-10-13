package com.spacehorizons.planet;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocos e bioma de cada SurfaceType. Chunks e LODs do DH leem daqui para que a cor de longe
 * seja idêntica à de perto (é isso que evita o "salto" visual entre camadas).
 */
public final class SurfacePalette {
    /**
     * Bioma de todos os tipos lunares (data/spacehorizons/worldgen/biome/lunar.json): sem precipitação,
     * sem features, sem mobs naturais, água e céu neutros. Os minecraft:the_void não serve porque carrega
     * a feature void_start_platform, que construiria uma plataforma de obsidiana.
     */
    public static final ResourceKey<Biome> LUNAR = ResourceKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "lunar"));

    private SurfacePalette() {
    }

    public static BlockState top(SurfaceType type) {
        return switch (type) {
            case OCEAN, BEACH, DESERT -> Blocks.SAND.defaultBlockState();
            case PLAINS, FOREST -> Blocks.GRASS_BLOCK.defaultBlockState();
            case MOUNTAIN -> Blocks.STONE.defaultBlockState();
            case SNOW -> Blocks.SNOW_BLOCK.defaultBlockState();
            case POLAR_ICE -> Blocks.PACKED_ICE.defaultBlockState();
            case REGOLITH -> Blocks.CONCRETE_POWDER.pick(DyeColor.LIGHT_GRAY).defaultBlockState();
            case MARE -> Blocks.SMOOTH_BASALT.defaultBlockState();
            case HIGHLAND -> Blocks.ANDESITE.defaultBlockState();
            case CRATER_FLOOR -> Blocks.TUFF.defaultBlockState();
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
            case REGOLITH, HIGHLAND -> Blocks.ANDESITE.defaultBlockState();
            case MARE -> Blocks.BASALT.defaultBlockState();
            case CRATER_FLOOR -> Blocks.TUFF.defaultBlockState();
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
            case REGOLITH, MARE, HIGHLAND, CRATER_FLOOR -> LUNAR;
        };
    }
}
