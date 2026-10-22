package com.spacehorizons.planet.spec;

import static com.spacehorizons.planet.spec.SpecCodecs.require;
import static com.spacehorizons.planet.spec.SpecCodecs.requireRange;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * Veio de minério no subsolo ({@code "ores"} no JSON), lido por {@code planet.terrain.OreVeins}: o minério fica onde duas
 * superfícies de ruído 3D de escala {@code scale} blocos passam ao mesmo tempo perto de zero (|a| e |b| abaixo de
 * {@code thickness}), o que dá tubos finos e sinuosos, e só onde um terceiro ruído, de escala {@code gateScale}, passa de
 * {@code gateThreshold} (os veios aparecem em regiões, não espalhados). Só substitui o bloco de subsolo, entre minY e maxY.
 * {@code salt} separa veios diferentes do mesmo planeta.
 */
public record OreVeinSpec(List<Identifier> block, int minY, int maxY, double scale, double thickness, double gateScale,
        double gateThreshold, int salt) {
    public static final Codec<OreVeinSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
            SpecCodecs.BLOCK_CHOICE.fieldOf("block").forGetter(OreVeinSpec::block),
            Codec.INT.fieldOf("min_y").forGetter(OreVeinSpec::minY),
            Codec.INT.fieldOf("max_y").forGetter(OreVeinSpec::maxY),
            Codec.DOUBLE.fieldOf("scale").forGetter(OreVeinSpec::scale),
            Codec.DOUBLE.fieldOf("thickness").forGetter(OreVeinSpec::thickness),
            Codec.DOUBLE.fieldOf("gate_scale").forGetter(OreVeinSpec::gateScale),
            Codec.DOUBLE.fieldOf("gate_threshold").forGetter(OreVeinSpec::gateThreshold),
            Codec.INT.optionalFieldOf("salt", 0).forGetter(OreVeinSpec::salt))
            .apply(i, OreVeinSpec::new));

    public OreVeinSpec {
        block = List.copyOf(block);
        require(minY < maxY, "ores: min_y deve ser menor que max_y: " + minY + " / " + maxY);
        require(minY >= -63 && maxY <= 319, "ores: faixa de y fora do mundo: " + minY + ".." + maxY);
        requireRange(scale, 2, 4096, "ores.scale");
        requireRange(thickness, 0.001, 0.5, "ores.thickness");
        requireRange(gateScale, 2, 65536, "ores.gate_scale");
        requireRange(gateThreshold, -1, 1, "ores.gate_threshold");
    }
}
