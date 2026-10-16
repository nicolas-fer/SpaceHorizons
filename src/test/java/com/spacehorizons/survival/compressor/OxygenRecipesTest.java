package com.spacehorizons.survival.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.spacehorizons.SpaceHorizons;

/** Receitas dos tanques e do compressor: o grande custa mais que o pequeno, e todas usam placas de aço. */
class OxygenRecipesTest {
    private static JsonObject receita(String id) throws IOException {
        try (InputStream in = OxygenRecipesTest.class.getClassLoader()
                .getResourceAsStream("data/" + SpaceHorizons.MODID + "/recipe/" + id + ".json")) {
            assertTrue(in != null, "receita ausente: " + id);
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /** Quantas posições do padrão da receita usam o item dado. */
    private static int contar(JsonObject receita, String item) {
        JsonObject chaves = receita.getAsJsonObject("key");
        JsonArray padrao = receita.getAsJsonArray("pattern");
        int total = 0;
        for (var linha : padrao) {
            for (char c : linha.getAsString().toCharArray()) {
                if (c != ' ' && chaves.get(String.valueOf(c)).getAsString().equals(item)) {
                    total++;
                }
            }
        }
        return total;
    }

    @Test
    void tanqueGrandeCustaMaisPlacasQueOPequeno() throws IOException {
        int pequeno = contar(receita("oxygen_tank_small"), SpaceHorizons.MODID + ":steel_plate");
        int grande = contar(receita("oxygen_tank_large"), SpaceHorizons.MODID + ":steel_plate");
        assertEquals(5, pequeno);
        assertEquals(7, grande);
        assertTrue(grande > pequeno);
    }

    @Test
    void tanquesTemValvulaDeCobreEVidro() throws IOException {
        for (String id : new String[] {"oxygen_tank_small", "oxygen_tank_large"}) {
            assertEquals(1, contar(receita(id), "minecraft:copper_ingot"), id);
            assertEquals(1, contar(receita(id), "minecraft:glass_pane"), id);
        }
    }

    @Test
    void compressorUsaCircuitoEPistao() throws IOException {
        JsonObject r = receita("oxygen_compressor");
        assertEquals(1, contar(r, SpaceHorizons.MODID + ":basic_circuit"));
        assertEquals(1, contar(r, "minecraft:piston"));
        assertEquals(5, contar(r, SpaceHorizons.MODID + ":steel_plate"));
    }
}
