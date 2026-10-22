package com.spacehorizons.planet;

import java.util.Locale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** Família de terreno de um planeta: escolhe a implementação de PlanetTerrain e o gerador de chunks. */
public enum TerrainStyle {
    /** Continentes, oceanos e clima por latitude: o mundo vem do gerador vanilla (Overworld com costura em X). */
    EARTH,
    /**
     * Relevo analítico descrito pelos parâmetros do planeta ({@code terrain.relief} no JSON): planícies, terras altas,
     * crateras, montanhas e vulcões opcionais, calota polar; sem oceano. A Lua e Marte.
     */
    PARAMETRIC;

    public static final Codec<TerrainStyle> CODEC = Codec.STRING.comapFlatMap(text -> {
        try {
            return DataResult.success(valueOf(text.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "estilo de terreno desconhecido: " + text + " (earth ou parametric)");
        }
    }, style -> style.name().toLowerCase(Locale.ROOT));
}
