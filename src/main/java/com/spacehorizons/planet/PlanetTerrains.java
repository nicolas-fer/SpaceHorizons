package com.spacehorizons.planet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.spacehorizons.planet.terrain.MoonTerrain;
import com.spacehorizons.planet.terrain.SphericalTerrain;

/** Ponto de acesso único ao terreno de cada planeta (cacheado, thread-safe). */
public final class PlanetTerrains {
    private static final Map<PlanetDefinition, PlanetTerrain> CACHE = new ConcurrentHashMap<>();

    private PlanetTerrains() {
    }

    public static PlanetTerrain get(PlanetDefinition planet) {
        return CACHE.computeIfAbsent(planet, PlanetTerrains::create);
    }

    private static PlanetTerrain create(PlanetDefinition planet) {
        return switch (planet.style()) {
            case EARTH -> new SphericalTerrain(planet);
            case MOON -> new MoonTerrain(planet);
        };
    }
}
