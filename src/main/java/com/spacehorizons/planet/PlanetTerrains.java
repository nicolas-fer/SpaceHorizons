package com.spacehorizons.planet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.spacehorizons.planet.terrain.SphericalTerrain;

/** Ponto de acesso único ao terreno de cada planeta (cacheado, thread-safe). */
public final class PlanetTerrains {
    private static final Map<PlanetDefinition, PlanetTerrain> CACHE = new ConcurrentHashMap<>();

    private PlanetTerrains() {
    }

    public static PlanetTerrain get(PlanetDefinition planet) {
        return CACHE.computeIfAbsent(planet, SphericalTerrain::new);
    }
}
