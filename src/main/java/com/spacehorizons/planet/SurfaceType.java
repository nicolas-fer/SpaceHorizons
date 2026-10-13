package com.spacehorizons.planet;

import java.util.EnumSet;
import java.util.Set;

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
    // Lua
    /** Planície de regolito claro. */
    REGOLITH,
    /** Mar lunar: bacia baixa de basalto escuro. */
    MARE,
    /** Terra alta, rochosa e acinzentada. */
    HIGHLAND,
    /** Fundo plano de uma cratera. */
    CRATER_FLOOR;

    private static final Set<SurfaceType> EARTH_TYPES = EnumSet.of(OCEAN, BEACH, PLAINS, FOREST, DESERT, MOUNTAIN, SNOW,
            POLAR_ICE);
    private static final Set<SurfaceType> MOON_TYPES = EnumSet.of(REGOLITH, MARE, HIGHLAND, CRATER_FLOOR);

    /** Tipos que o terreno de um estilo pode produzir; o bioma de cada um é obrigatório no datapack do planeta. */
    public static Set<SurfaceType> usedBy(TerrainStyle style) {
        return switch (style) {
            case EARTH -> EARTH_TYPES;
            case MOON -> MOON_TYPES;
        };
    }
}
