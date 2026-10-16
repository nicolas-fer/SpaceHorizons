package com.spacehorizons.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.spacehorizons.SpaceHorizons;

/**
 * Recursos do traje espacial: o equipment asset aponta camadas que existem (PNG 64x32 do layout da armadura), as
 * quatro peças estão nas tags de armadura do jogo e na de reparo, e os textos do HUD e das conquistas existem nos
 * dois idiomas.
 */
class SuitResourcesTest {
    private static final String ASSETS = "assets/" + SpaceHorizons.MODID + "/";
    private static final String DATA = "data/" + SpaceHorizons.MODID + "/";
    private static final List<String> PIECES = List.of(ContentIds.SPACE_HELMET, ContentIds.SPACE_CHESTPLATE,
            ContentIds.SPACE_LEGGINGS, ContentIds.SPACE_BOOTS);

    private static byte[] bytes(String caminho) throws IOException {
        try (InputStream in = SuitResourcesTest.class.getClassLoader().getResourceAsStream(caminho)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    private static JsonObject json(String caminho) throws IOException {
        byte[] dados = bytes(caminho);
        assertTrue(dados != null, "recurso ausente: " + caminho);
        return JsonParser.parseString(new String(dados, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void theFourPiecesAreRegisteredInContentIds() {
        for (String piece : PIECES) {
            assertTrue(ContentIds.ITEMS.contains(piece), piece + " fora de ContentIds.ITEMS");
        }
    }

    @Test
    void equipmentAssetPointsToExistingWearableLayers() throws IOException {
        JsonObject layers = json(ASSETS + "equipment/space_suit.json").getAsJsonObject("layers");
        for (String type : List.of("humanoid", "humanoid_leggings")) {
            assertTrue(layers.has(type), "camada " + type + " ausente");
            for (JsonElement layer : layers.getAsJsonArray(type)) {
                String texture = layer.getAsJsonObject().get("texture").getAsString();
                String[] parts = texture.split(":", 2);
                byte[] png = bytes("assets/" + parts[0] + "/textures/entity/equipment/" + type + "/" + parts[1] + ".png");
                assertTrue(png != null, "textura ausente: " + type + "/" + texture);
                ByteBuffer header = ByteBuffer.wrap(png, 16, 8);
                assertEquals(64, header.getInt(), "largura de " + type);
                assertEquals(32, header.getInt(), "altura de " + type);
            }
        }
    }

    @Test
    void piecesAreInTheArmorTagsAndRepairTag() throws IOException {
        List<List<String>> tags = List.of(
                List.of("head_armor", ContentIds.SPACE_HELMET), List.of("chest_armor", ContentIds.SPACE_CHESTPLATE),
                List.of("leg_armor", ContentIds.SPACE_LEGGINGS), List.of("foot_armor", ContentIds.SPACE_BOOTS));
        for (List<String> tag : tags) {
            JsonObject json = json("data/minecraft/tags/item/" + tag.get(0) + ".json");
            assertTrue(json.getAsJsonArray("values").contains(JsonParser.parseString("\"" + SpaceHorizons.MODID + ":" + tag.get(1) + "\"")),
                    tag.get(1) + " fora de " + tag.get(0));
        }
        JsonArray repairs = json(DATA + "tags/item/repairs_space_suit.json").getAsJsonArray("values");
        assertTrue(repairs.contains(JsonParser.parseString("\"spacehorizons:steel_plate\"")), "reparo sem a placa de aço");
    }

    @Test
    void thePiecesAreNotInTheFreezeImmuneTag() throws IOException {
        // uma peça só na tag deixaria o jogador imune ao congelamento (vale UMA peça), contra a regra das quatro:
        // confere todas as cópias da tag no classpath (a do jogo e uma eventual do mod)
        java.util.Enumeration<java.net.URL> copies = SuitResourcesTest.class.getClassLoader()
                .getResources("data/minecraft/tags/item/freeze_immune_wearables.json");
        while (copies.hasMoreElements()) {
            try (InputStream in = copies.nextElement().openStream()) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(!text.contains(SpaceHorizons.MODID + ":"), "peça do traje na tag freeze_immune_wearables");
            }
        }
    }

    @Test
    void hudAndAdvancementTextsExistInBothLanguages() throws IOException {
        List<String> keys = List.of("hud.spacehorizons.oxygen.tanks", "hud.spacehorizons.oxygen.seconds_no_tanks",
                "advancements.spacehorizons.suit_up.title", "advancements.spacehorizons.suit_up.description",
                "advancements.spacehorizons.first_breath.title", "advancements.spacehorizons.first_breath.description");
        for (String language : List.of("en_us", "pt_br")) {
            JsonObject lang = json(ASSETS + "lang/" + language + ".json");
            for (String key : keys) {
                assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(), language + " sem " + key);
            }
        }
    }

    @Test
    void everyPieceHasARecipeThatCraftsItself() throws IOException {
        for (String piece : PIECES) {
            JsonObject recipe = json(DATA + "recipe/" + piece + ".json");
            assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
            assertEquals(SpaceHorizons.MODID + ":" + piece, recipe.getAsJsonObject("result").get("id").getAsString());
        }
    }
}
