package com.spacehorizons.client.atmosphere;

import java.util.Optional;

import com.spacehorizons.planet.atmosphere.AtmosphereModel;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;

import net.minecraft.world.level.Level;

/** Atmosfera do planeta onde a câmera está e a altitude dela sobre o nível do mar (a mesma de CurvatureState). */
record AtmosphereSample(PlanetDefinition planet, AtmosphereModel model, double altitude) {
    static Optional<AtmosphereSample> of(Level level, double cameraY) {
        return Planets.forLevel(level)
                .map(planet -> new AtmosphereSample(planet, AtmosphereModel.of(planet), cameraY - planet.seaLevel()));
    }
}
