package com.spacehorizons.worldgen;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrain;
import com.spacehorizons.planet.PlanetTerrains;
import com.spacehorizons.planet.SurfaceType;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Bioma por coluna a partir do SurfaceType do terreno. Os biomas vêm do datapack
 * (mapa "biomes"), que deve espelhar SurfacePalette.biome(): assim o chunk e o LOD do DH têm a mesma cor.
 * As coordenadas de quart (4 blocos) são convertidas para bloco e passam por wrapX.
 */
public final class PlanetBiomeSource extends BiomeSource {
    private static final Codec<SurfaceType> SURFACE_KEY = Codec.STRING.xmap(
            s -> SurfaceType.valueOf(s.toUpperCase(Locale.ROOT)),
            t -> t.name().toLowerCase(Locale.ROOT));

    public static final MapCodec<PlanetBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("planet").forGetter(s -> s.planet.id()),
            Codec.unboundedMap(SURFACE_KEY, Biome.CODEC).fieldOf("biomes").forGetter(s -> s.biomes))
            .apply(instance, (id, biomes) -> new PlanetBiomeSource(PlanetChunkGenerator.planetOf(id), biomes)));

    private final PlanetDefinition planet;
    private final PlanetTerrain terrain;
    private final Map<SurfaceType, Holder<Biome>> biomes;
    /**
     * Bioma por coluna de quart (qx, qz). O fillBiomesFromNoise chama o resolver para cada qy, e o
     * SurfaceType não depende de y; sem o cache o ruído do terreno seria refeito em toda a altura.
     */
    private final Map<Long, Holder<Biome>> columnCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHED_COLUMNS = 1 << 16;

    public PlanetBiomeSource(PlanetDefinition planet, Map<SurfaceType, Holder<Biome>> biomes) {
        this.planet = planet;
        this.terrain = PlanetTerrains.get(planet);
        EnumMap<SurfaceType, Holder<Biome>> copy = new EnumMap<>(SurfaceType.class);
        copy.putAll(biomes);
        for (SurfaceType type : SurfaceType.values()) {
            if (!copy.containsKey(type)) {
                throw new IllegalArgumentException("falta bioma para " + type + " em " + planet.id());
            }
        }
        this.biomes = copy;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.values().stream().distinct();
    }

    @Override
    public BiomeResolver createResolver(Climate.Sampler sampler) {
        return (qx, qy, qz) -> {
            long key = ((long) qx << 32) ^ (qz & 0xFFFFFFFFL);
            Holder<Biome> cached = columnCache.get(key);
            if (cached != null) {
                return cached;
            }
            // quart -> bloco; o terreno já faz o wrap em X internamente
            int x = qx << 2;
            int z = qz << 2;
            Holder<Biome> biome = biomes.get(terrain.surfaceType(x, z));
            // limite simples: o mapa só cresce com a área explorada, então descarta tudo ao passar do teto
            if (columnCache.size() >= MAX_CACHED_COLUMNS) {
                columnCache.clear();
            }
            columnCache.put(key, biome);
            return biome;
        };
    }
}
