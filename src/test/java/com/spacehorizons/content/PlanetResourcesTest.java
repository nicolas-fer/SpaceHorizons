package com.spacehorizons.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.SurfaceType;
import com.spacehorizons.planet.TerrainStyle;
import com.spacehorizons.planet.spec.AtmosphereSpec;
import com.spacehorizons.planet.spec.PlanetSpec;
import com.spacehorizons.planet.spec.SurfaceSpec;

/**
 * Os parâmetros de planeta e os dados do jogo que dependem deles têm de bater: o world preset mapeia cada papel da paleta ao
 * mesmo bioma que o DH usa, e o tipo de dimensão (e o bioma) aplica as cores de céu e neblina e as nuvens declaradas.
 */
class PlanetResourcesTest {
    private static final String DATA = "data/" + SpaceHorizons.MODID + "/";

    private static JsonObject read(String path) throws IOException {
        try (InputStream in = PlanetResourcesTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "recurso ausente: " + path);
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06x", rgb);
    }

    @Test
    void worldPresetBiomesMatchEachParametricPalette() throws IOException {
        JsonObject dimensions = read(DATA + "worldgen/world_preset/earth.json").getAsJsonObject("dimensions");
        for (PlanetDefinition planet : Planets.ALL) {
            if (planet.style() != TerrainStyle.PARAMETRIC) {
                continue;
            }
            JsonObject entry = dimensions.getAsJsonObject(planet.id().toString());
            assertNotNull(entry, "world preset sem a dimensão " + planet.id());
            assertEquals(planet.id().toString(), entry.get("type").getAsString());
            JsonObject generator = entry.getAsJsonObject("generator");
            assertEquals("spacehorizons:planet", generator.get("type").getAsString());
            assertEquals(planet.id().toString(), generator.get("planet").getAsString());
            JsonObject biomes = generator.getAsJsonObject("biome_source").getAsJsonObject("biomes");
            Map<SurfaceType, SurfaceSpec.Layer> palette = Planets.spec(planet).surface().types();
            assertEquals(palette.size(), biomes.size(), "papéis do preset e da paleta de " + planet.id());
            for (Map.Entry<SurfaceType, SurfaceSpec.Layer> layer : palette.entrySet()) {
                assertEquals(layer.getValue().biome().toString(), biomes.get(layer.getKey().key()).getAsString(),
                        planet.id() + " " + layer.getKey());
                String biome = layer.getValue().biome().getPath();
                if (layer.getValue().biome().getNamespace().equals(SpaceHorizons.MODID)) {
                    read(DATA + "worldgen/biome/" + biome + ".json");
                }
            }
        }
    }

    @Test
    void dimensionTypesApplyTheDeclaredSkyFogAndClouds() throws IOException {
        for (PlanetDefinition planet : Planets.ALL) {
            PlanetSpec spec = Planets.spec(planet);
            JsonObject attributes = read(DATA + "dimension_type/" + planet.id().getPath() + ".json").getAsJsonObject("attributes");
            AtmosphereSpec atmosphere = spec.atmosphere();
            if (atmosphere.air().isEmpty()) {
                assertEquals("#000000", attributes.get("minecraft:visual/sky_color").getAsString(), planet.id().toString());
                assertEquals("#00000000", attributes.get("minecraft:visual/cloud_color").getAsString(), planet.id().toString());
                continue;
            }
            AtmosphereSpec.Air air = atmosphere.air().get();
            assertEquals(hex(air.skyColor()), attributes.get("minecraft:visual/sky_color").getAsString(), planet.id().toString());
            assertEquals(hex(air.fogColor()), attributes.get("minecraft:visual/fog_color").getAsString(), planet.id().toString());
            boolean invisibleClouds = "#00000000".equals(attributes.get("minecraft:visual/cloud_color").getAsString());
            assertEquals(!air.clouds(), invisibleClouds, planet.id() + " nuvens");
        }
    }

    @Test
    void marsBiomeKeepsTheCaramelSkyAndDust() throws IOException {
        JsonObject attributes = read(DATA + "worldgen/biome/martian.json").getAsJsonObject("attributes");
        AtmosphereSpec.Air air = Planets.spec(Planets.MARS).atmosphere().air().orElseThrow();
        // o atributo do bioma vence o do tipo de dimensão: os dois precisam ter a cor de Marte
        assertEquals(hex(air.skyColor()), attributes.get("minecraft:visual/sky_color").getAsString());
        assertEquals(hex(air.fogColor()), attributes.get("minecraft:visual/fog_color").getAsString());
        assertTrue(attributes.has("minecraft:visual/ambient_particles"), "poeira no ar de Marte");
        JsonObject type = read(DATA + "dimension_type/mars.json");
        assertEquals(-64, type.get("min_y").getAsInt());
        assertEquals(384, type.get("height").getAsInt());
    }
}
