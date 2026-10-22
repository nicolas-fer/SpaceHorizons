package com.spacehorizons.planet.spec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Água ({@code "water"} no JSON): se o planeta tem oceano até o nível do mar e as cores da água rasa e funda vistas de longe
 * (malha orbital e esfera do espaço). O gelo é um papel de superfície como os outros (a calota polar, {@code polar.cap_type}).
 * Por enquanto só o estilo Terra tem oceano (o gerador por parâmetros ainda não enche bacias), e o carregador recusa o resto.
 */
public record WaterSpec(boolean ocean, int shallowColor, int deepColor) {
    public static final Codec<WaterSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("ocean").forGetter(WaterSpec::ocean),
            SpecCodecs.COLOR.optionalFieldOf("shallow_color", 0x3F76E4).forGetter(WaterSpec::shallowColor),
            SpecCodecs.COLOR.optionalFieldOf("deep_color", 0x1A3A8C).forGetter(WaterSpec::deepColor))
            .apply(i, WaterSpec::new));
}
