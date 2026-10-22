package com.spacehorizons.planet;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.planet.spec.SurfaceSpec;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocos e bioma de cada papel da superfície de um planeta, da paleta dos parâmetros dele ({@code "surface"} no JSON).
 * Chunks e LODs do DH leem daqui para que a cor de longe seja idêntica à de perto (é isso que evita o "salto" visual entre
 * camadas). Os blocos são resolvidos no primeiro uso, depois do registro, escolhendo o primeiro id da lista que existe.
 */
public final class SurfacePalette {
    private static final Map<Identifier, SurfacePalette> CACHE = new ConcurrentHashMap<>();

    private final Identifier planet;
    private final SurfaceSpec spec;
    private final Map<SurfaceType, BlockState> top = new EnumMap<>(SurfaceType.class);
    private final Map<SurfaceType, BlockState> filler = new EnumMap<>(SurfaceType.class);
    private final Map<SurfaceType, ResourceKey<Biome>> biomes = new EnumMap<>(SurfaceType.class);
    private final BlockState subsurface;

    private SurfacePalette(Identifier planet, SurfaceSpec spec) {
        this.planet = planet;
        this.spec = spec;
        for (Map.Entry<SurfaceType, SurfaceSpec.Layer> entry : spec.types().entrySet()) {
            SurfaceSpec.Layer layer = entry.getValue();
            top.put(entry.getKey(), resolve(planet, layer.top()));
            filler.put(entry.getKey(), resolve(planet, layer.filler()));
            biomes.put(entry.getKey(), ResourceKey.create(Registries.BIOME, layer.biome()));
        }
        this.subsurface = resolve(planet, spec.subsurface());
    }

    /** Paleta do planeta (cacheada). Só chamar depois que os blocos estão registrados. */
    public static SurfacePalette of(PlanetDefinition planet) {
        return CACHE.computeIfAbsent(planet.id(), id -> new SurfacePalette(id, Planets.spec(planet).surface()));
    }

    /**
     * Primeiro bloco da lista que existe no registro. Nenhum existindo é erro do JSON do planeta (o último da lista deve ser
     * um bloco vanilla que sempre existe).
     */
    public static BlockState resolve(Identifier planet, List<Identifier> choices) {
        for (int i = 0; i < choices.size(); i++) {
            Identifier id = choices.get(i);
            if (BuiltInRegistries.BLOCK.containsKey(id)) {
                if (i > 0) {
                    SpaceHorizons.LOGGER.info("Planeta {}: {} ainda não existe, usando {} no lugar", planet, choices.get(0), id);
                }
                Block block = BuiltInRegistries.BLOCK.getValue(id);
                return block.defaultBlockState();
            }
        }
        throw new IllegalStateException("planeta " + planet + ": nenhum dos blocos existe: " + choices);
    }

    public Identifier planet() {
        return planet;
    }

    /** Papéis que a paleta cobre. */
    public Set<SurfaceType> types() {
        return spec.types().keySet();
    }

    public BlockState top(SurfaceType type) {
        return require(top, type);
    }

    /** Camada logo abaixo do topo (alguns blocos), antes do subsolo. */
    public BlockState filler(SurfaceType type) {
        return require(filler, type);
    }

    /** Rocha abaixo do enchimento. */
    public BlockState subsurface() {
        return subsurface;
    }

    public ResourceKey<Biome> biome(SurfaceType type) {
        return require(biomes, type);
    }

    /** Cor de longe declarada para o papel (0xRRGGBB), ou -1 se a paleta não declara (vale a cor de mapa do topo). */
    public int declaredColor(SurfaceType type) {
        SurfaceSpec.Layer layer = spec.types().get(type);
        return layer != null && layer.color().isPresent() ? layer.color().get() & 0xFFFFFF : -1;
    }

    private <T> T require(Map<SurfaceType, T> map, SurfaceType type) {
        T value = map.get(type);
        if (value == null) {
            throw new IllegalArgumentException("a paleta do planeta " + planet + " não tem o papel " + type);
        }
        return value;
    }
}
