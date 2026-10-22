package com.spacehorizons.planet.spec;

import static com.spacehorizons.planet.spec.SpecCodecs.require;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spacehorizons.planet.SurfaceType;

import net.minecraft.resources.Identifier;

/**
 * Paleta de superfície e subsolo ({@code "surface"} no JSON). Para cada papel da superfície: o bloco de topo, o enchimento
 * (as 3 camadas abaixo), o bioma (o mesmo que o world preset deve mapear para o papel) e, opcional, a cor de longe (malha
 * orbital e esfera vista do espaço; sem ela vale a cor de mapa do bloco de topo). {@code subsurface} é a rocha abaixo do
 * enchimento. Blocos aceitam reserva (ver {@link SpecCodecs#BLOCK_CHOICE}). Dado puro: os blocos são resolvidos pelo
 * {@code planet.SurfacePalette} depois do registro.
 */
public record SurfaceSpec(List<Identifier> subsurface, Map<SurfaceType, Layer> types) {
    public static final Codec<SurfaceSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
            SpecCodecs.BLOCK_CHOICE.fieldOf("subsurface").forGetter(SurfaceSpec::subsurface),
            Codec.unboundedMap(SurfaceType.CODEC, Layer.CODEC).fieldOf("types").forGetter(SurfaceSpec::types))
            .apply(i, SurfaceSpec::new));

    public SurfaceSpec {
        subsurface = List.copyOf(subsurface);
        require(!types.isEmpty(), "surface.types vazio");
        EnumMap<SurfaceType, Layer> copy = new EnumMap<>(SurfaceType.class);
        copy.putAll(types);
        types = java.util.Collections.unmodifiableMap(copy);
    }

    public record Layer(List<Identifier> top, List<Identifier> filler, Identifier biome, Optional<Integer> color) {
        public static final Codec<Layer> CODEC = RecordCodecBuilder.create(i -> i.group(
                SpecCodecs.BLOCK_CHOICE.fieldOf("top").forGetter(Layer::top),
                SpecCodecs.BLOCK_CHOICE.fieldOf("filler").forGetter(Layer::filler),
                Identifier.CODEC.fieldOf("biome").forGetter(Layer::biome),
                SpecCodecs.COLOR.optionalFieldOf("color").forGetter(Layer::color))
                .apply(i, Layer::new));

        public Layer {
            top = List.copyOf(top);
            filler = List.copyOf(filler);
        }
    }
}
