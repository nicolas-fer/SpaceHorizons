package com.spacehorizons.client.planetmesh;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.SurfaceColors;
import com.spacehorizons.planet.SurfacePalette;
import com.spacehorizons.planet.SurfaceType;
import com.spacehorizons.planet.spec.WaterSpec;

import net.minecraft.resources.Identifier;

/**
 * Cores da malha orbital de um planeta. A terra vem da paleta do planeta: a cor declarada para o papel nos parâmetros dele
 * ou, sem ela, a cor de mapa do bloco de topo (a mesma fonte de chunks e DH, então não há salto de tom); a água vem dos
 * parâmetros da água, porque o topo do OCEAN na paleta é o fundo de areia.
 *
 * Também usada pelos corpos vistos de fora (client/space), para a cor ser a mesma. Só chamar depois que os blocos estão
 * registrados (na primeira renderização de um level).
 */
public final class PlanetMeshPalette {
    private static final Map<Identifier, PlanetMeshGeometry.Palette> CACHE = new ConcurrentHashMap<>();

    private PlanetMeshPalette() {
    }

    /** Paleta do planeta (cacheada por id). */
    public static PlanetMeshGeometry.Palette of(PlanetDefinition planet) {
        return CACHE.computeIfAbsent(planet.id(), id -> create(planet));
    }

    private static PlanetMeshGeometry.Palette create(PlanetDefinition planet) {
        SurfacePalette surface = SurfacePalette.of(planet);
        SurfaceType[] types = SurfaceType.values();
        int[] land = new int[types.length];
        for (SurfaceType type : types) {
            if (!surface.types().contains(type)) {
                // papel que o planeta não produz: nunca é lido, mas fica com a cor padrão em vez de preto
                land[type.ordinal()] = SurfaceColors.of(type);
                continue;
            }
            int declared = surface.declaredColor(type);
            int color = declared >= 0 ? declared : surface.top(type).getBlock().defaultMapColor().col;
            if (declared < 0 && type == SurfaceType.FOREST) {
                // floresta: mesmo bloco de topo da planície, mas o DH mostra mais escura por causa das copas
                color = scale(color, 0.75);
            }
            land[type.ordinal()] = color;
        }
        WaterSpec water = Planets.spec(planet).water();
        return new PlanetMeshGeometry.Palette(land, water.shallowColor(), water.deepColor());
    }

    private static int scale(int rgb, double factor) {
        int r = (int) Math.round((rgb >> 16 & 0xFF) * factor);
        int g = (int) Math.round((rgb >> 8 & 0xFF) * factor);
        int b = (int) Math.round((rgb & 0xFF) * factor);
        return r << 16 | g << 8 | b;
    }
}
