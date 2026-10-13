package com.spacehorizons.worldgen;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrain;
import com.spacehorizons.planet.PlanetTerrains;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.SurfacePalette;
import com.spacehorizons.planet.SurfaceType;

import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Gera colunas a partir de PlanetTerrain: bedrock, pedra, filler, topo e, só em planetas com oceano, água até o nível do mar.
 * Sem estruturas nem cavernas por enquanto. Não guarda estado mutável, então é seguro entre threads.
 */
public final class PlanetChunkGenerator extends ChunkGenerator {
    /** Altura da dimensão (datapack: min_y -64, height 384). */
    public static final int MIN_Y = -64;
    public static final int HEIGHT = 384;

    public static final MapCodec<PlanetChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("planet").forGetter(g -> g.planet.id()),
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource))
            .apply(instance, (id, source) -> new PlanetChunkGenerator(planetOf(id), source)));

    private final PlanetDefinition planet;
    private final PlanetTerrain terrain;

    public PlanetChunkGenerator(PlanetDefinition planet, BiomeSource biomeSource) {
        super(biomeSource);
        this.planet = planet;
        this.terrain = PlanetTerrains.get(planet);
    }

    static PlanetDefinition planetOf(Identifier id) {
        return Planets.byId(id).orElseThrow(() -> new IllegalArgumentException("planeta desconhecido: " + id));
    }

    public PlanetDefinition planet() {
        return planet;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState,
            StructureManager structureManager, BiomeManager biomeManager, WorldGenRegion region,
            Set<Holder<Biome>> biomes) {
        int topY = MIN_Y + HEIGHT - 1;
        int sea = planet.seaLevel();
        var pos = chunk.getPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = pos.getMinBlockX() + lx;
                int wz = pos.getMinBlockZ() + lz;
                int surface = terrain.surfaceHeight(wx, wz);
                SurfaceType type = terrain.surfaceType(wx, wz);
                int top = Math.min(surface, topY);
                // água sobre a coluna quando o terreno fica abaixo do mar (só em planetas com oceano)
                int waterTop = planet.hasOcean() ? Math.max(top, Math.min(sea, topY)) : top;
                for (int y = MIN_Y; y <= waterTop; y++) {
                    BlockState state;
                    if (y == MIN_Y) {
                        state = Blocks.BEDROCK.defaultBlockState();
                    } else if (y > top) {
                        state = Blocks.WATER.defaultBlockState();
                    } else if (y == top) {
                        state = SurfacePalette.top(type);
                    } else if (y >= top - 3) {
                        state = SurfacePalette.filler(type);
                    } else {
                        state = Blocks.STONE.defaultBlockState();
                    }
                    setLocal(chunk, lx, y, lz, state);
                }
            }
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    private static void setLocal(ChunkAccess chunk, int lx, int y, int lz, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(y)).setBlockState(lx, y & 15, lz, state, false);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // sem mobs de geração por enquanto
    }

    @Override
    public int getGenDepth() {
        return HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return planet.seaLevel();
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types types, LevelHeightAccessor level, RandomState randomState) {
        int h = terrain.surfaceHeight(x, z);
        // OCEAN_FLOOR ignora a água; WORLD_SURFACE enxerga a superfície da água
        if (planet.hasOcean()
                && (types == Heightmap.Types.WORLD_SURFACE || types == Heightmap.Types.WORLD_SURFACE_WG)) {
            h = Math.max(h, planet.seaLevel());
        }
        return Math.min(h + 1, MIN_Y + HEIGHT);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        int h = terrain.surfaceHeight(x, z);
        int sea = planet.seaLevel();
        BlockState[] states = new BlockState[level.getHeight()];
        for (int i = 0; i < states.length; i++) {
            int y = level.getMinY() + i;
            if (y == MIN_Y) {
                states[i] = Blocks.BEDROCK.defaultBlockState();
            } else if (y <= h) {
                states[i] = Blocks.STONE.defaultBlockState();
            } else if (planet.hasOcean() && y <= sea) {
                states[i] = Blocks.WATER.defaultBlockState();
            } else {
                states[i] = Blocks.AIR.defaultBlockState();
            }
        }
        return new NoiseColumn(level.getMinY(), states);
    }

    @Override
    public void addDebugScreenInfo(java.util.List<String> info, RandomState randomState, BlockPos pos,
            net.minecraft.world.level.levelgen.densityfunction.SamplerContext context) {
        info.add("SpaceHorizons planeta: " + planet.id());
    }
}
