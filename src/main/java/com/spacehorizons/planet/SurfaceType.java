package com.spacehorizons.planet;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * Papel de uma coluna da superfície. Os blocos, o bioma e a cor de cada papel são do planeta (a paleta em
 * {@code data/spacehorizons/planet/<id>.json}, ver {@link SurfacePalette}): "mare" é basalto escuro na Lua e terracota
 * escura em Marte. Só a Terra (gerador vanilla) usa os papéis de Terra; o terreno por parâmetros usa os outros.
 */
public enum SurfaceType {
    // Terra
    OCEAN,
    BEACH,
    PLAINS,
    FOREST,
    DESERT,
    MOUNTAIN,
    SNOW,
    POLAR_ICE,
    // Terreno por parâmetros (Lua, Marte...)
    /** Planície de regolito (o solo comum). */
    REGOLITH,
    /** Planície baixa e escura (mar lunar, planícies basálticas). */
    MARE,
    /** Terra alta, rochosa. */
    HIGHLAND,
    /** Fundo plano de uma cratera. */
    CRATER_FLOOR,
    /** Encosta e caldeira de um vulcão em escudo. */
    VOLCANIC;

    /** Os papéis que o terreno estilo Terra produz (o datapack do planeta precisa de bioma para cada um). */
    public static final Set<SurfaceType> EARTH_TYPES = Collections.unmodifiableSet(
            EnumSet.of(OCEAN, BEACH, PLAINS, FOREST, DESERT, MOUNTAIN, SNOW, POLAR_ICE));

    /** Nome no JSON (minúsculas: "crater_floor"). */
    public static final Codec<SurfaceType> CODEC = Codec.STRING.comapFlatMap(SurfaceType::parse, SurfaceType::key);

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    private static DataResult<SurfaceType> parse(String text) {
        try {
            return DataResult.success(valueOf(text.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "tipo de superfície desconhecido: " + text);
        }
    }
}
