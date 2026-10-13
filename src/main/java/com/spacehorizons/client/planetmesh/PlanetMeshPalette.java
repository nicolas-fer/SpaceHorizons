package com.spacehorizons.client.planetmesh;

import com.spacehorizons.planet.SurfacePalette;
import com.spacehorizons.planet.SurfaceType;

/**
 * Cores da malha orbital. A terra vem da cor de mapa do bloco de topo da SurfacePalette (a mesma fonte
 * de chunks e DH, então não há salto de tom); a água é própria, porque o topo do OCEAN na paleta é o fundo de areia.
 *
 * Só chamar depois que os blocos estão registrados (na primeira renderização de um level).
 */
final class PlanetMeshPalette {
    /** Água rasa e funda, próximas do que o DH mostra para o oceano do overworld. */
    private static final int WATER_SHALLOW = 0x3F76E4;
    private static final int WATER_DEEP = 0x1A3A8C;

    private PlanetMeshPalette() {
    }

    static PlanetMeshGeometry.Palette create() {
        SurfaceType[] types = SurfaceType.values();
        int[] land = new int[types.length];
        for (SurfaceType type : types) {
            int color = SurfacePalette.top(type).getBlock().defaultMapColor().col;
            if (type == SurfaceType.FOREST) {
                // floresta: mesmo bloco de topo da planície, mas o DH mostra mais escura por causa das copas
                color = scale(color, 0.75);
            }
            land[type.ordinal()] = color;
        }
        return new PlanetMeshGeometry.Palette(land, WATER_SHALLOW, WATER_DEEP);
    }

    private static int scale(int rgb, double factor) {
        int r = (int) Math.round((rgb >> 16 & 0xFF) * factor);
        int g = (int) Math.round((rgb >> 8 & 0xFF) * factor);
        int b = (int) Math.round((rgb & 0xFF) * factor);
        return r << 16 | g << 8 | b;
    }
}
