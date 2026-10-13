package com.spacehorizons.planet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Ponto de acesso único ao terreno de cada planeta (cacheado, thread-safe). */
public final class PlanetTerrains {
    private static final Map<PlanetDefinition, PlanetTerrain> CACHE = new ConcurrentHashMap<>();

    private PlanetTerrains() {
    }

    public static PlanetTerrain get(PlanetDefinition planet) {
        return CACHE.computeIfAbsent(planet, PlanetTerrains::create);
    }

    // STUB: o agente de worldgen troca por uma implementação real baseada em ruído na esfera
    private static PlanetTerrain create(PlanetDefinition planet) {
        return new PlanetTerrain() {
            @Override
            public PlanetDefinition planet() {
                return planet;
            }

            @Override
            public int surfaceHeight(int blockX, int blockZ) {
                return planet.seaLevel() + 4;
            }

            @Override
            public SurfaceType surfaceType(int blockX, int blockZ) {
                return SurfaceType.PLAINS;
            }
        };
    }
}
