package com.spacehorizons.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.space.SpaceDimension;

/**
 * Os dados da dimensão de espaço aberto: o tipo, o bioma e a entrada no world preset. O jogo só reclama destes JSONs ao
 * criar um mundo, então aqui se confere o que importa para o céu e o custo da dimensão vazia.
 */
class SpaceDimensionResourcesTest {
    private static final String DATA = "data/" + SpaceHorizons.MODID + "/";

    private static JsonObject read(String path) throws IOException {
        try (InputStream in = SpaceDimensionResourcesTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "recurso ausente: " + path);
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void dimensionTypeIsAnEmptyOneSectionSkyWithoutCloudsOrWeather() throws IOException {
        JsonObject type = read(DATA + "dimension_type/space.json");
        // o piso do codec de altura é 16 (uma seção por chunk): tudo o que escala com as seções cai ao mínimo
        assertEquals(16, type.get("height").getAsInt());
        assertEquals(16, type.get("logical_height").getAsInt());
        assertEquals(0, type.get("min_y").getAsInt());
        // sem teto o vanilla liga o clima (a chuva da Terra apagaria estrelas e luz); has_ceiling desliga canHaveWeather
        assertTrue(type.get("has_ceiling").getAsBoolean());
        // o céu 15 vem de graça: a nave em y = 20.000+ fica iluminada sem depender de chunks
        assertTrue(type.get("has_skylight").getAsBoolean());
        // o passe do céu (e o evento onde o BodyRenderer roda) só existe se skybox != none
        assertEquals("overworld", type.get("skybox").getAsString());
        JsonObject attributes = type.getAsJsonObject("attributes");
        // alfa 0: sem nuvens
        assertEquals("#00000000", attributes.get("minecraft:visual/cloud_color").getAsString());
        assertEquals("#000000", attributes.get("minecraft:visual/sky_color").getAsString());
        assertEquals(1.0, attributes.get("minecraft:visual/star_brightness").getAsDouble(), 0);
        assertEquals(1.0, attributes.get("minecraft:visual/sky_light_factor").getAsDouble(), 0);
        // sem linha do tempo: nada muda o céu com o relógio (o sol e as estrelas vêm do SpaceSkyClient)
        assertFalse(type.has("timelines"));
        assertEquals("minecraft:overworld", type.get("default_clock").getAsString());
        assertFalse(type.get("has_ender_dragon_fight").getAsBoolean());
    }

    @Test
    void biomeHasNoPrecipitationAndNoSpawns() throws IOException {
        JsonObject biome = read(DATA + "worldgen/biome/space.json");
        assertFalse(biome.get("has_precipitation").getAsBoolean());
        JsonObject spawns = biome.getAsJsonObject("attributes").getAsJsonObject("minecraft:gameplay/natural_mob_spawns")
                .getAsJsonObject("argument").getAsJsonObject("spawns_by_category");
        for (var entry : spawns.entrySet()) {
            assertEquals(0, entry.getValue().getAsJsonArray().size(), "spawns de " + entry.getKey());
        }
        assertEquals(0, biome.getAsJsonArray("carvers").size());
        for (JsonElement step : biome.getAsJsonArray("features")) {
            assertEquals(0, step.getAsJsonArray().size());
        }
    }

    @Test
    void worldPresetRegistersTheSpaceDimensionWithAnEmptyFlatGenerator() throws IOException {
        JsonObject preset = read(DATA + "worldgen/world_preset/earth.json");
        JsonObject dimensions = preset.getAsJsonObject("dimensions");
        JsonObject space = dimensions.getAsJsonObject(SpaceDimension.TYPE.toString());
        assertNotNull(space, "a dimensão de espaço não está no world preset");
        assertEquals(SpaceDimension.TYPE.toString(), space.get("type").getAsString());
        JsonObject generator = space.getAsJsonObject("generator");
        assertEquals("minecraft:flat", generator.get("type").getAsString());
        JsonObject settings = generator.getAsJsonObject("settings");
        assertEquals(0, settings.getAsJsonArray("layers").size());
        assertFalse(settings.get("features").getAsBoolean());
        assertFalse(settings.get("lakes").getAsBoolean());
        assertEquals(0, settings.getAsJsonArray("structure_overrides").size());
        assertEquals("spacehorizons:space", settings.get("biome").getAsString());
        // as outras dimensões continuam lá
        assertNotNull(dimensions.get("minecraft:overworld"));
        assertNotNull(dimensions.get("spacehorizons:moon"));
    }
}
