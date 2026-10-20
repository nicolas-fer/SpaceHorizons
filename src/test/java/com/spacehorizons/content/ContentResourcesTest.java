package com.spacehorizons.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.spacehorizons.SpaceHorizons;

/**
 * Pega esquecimentos ao adicionar conteúdo: todo item/bloco registrado precisa de lang (en_us e pt_br),
 * item model, model, textura e, no caso de blocos, blockstate e loot table.
 */
class ContentResourcesTest {
    private static final String ASSETS = "assets/" + SpaceHorizons.MODID + "/";
    private static final String DATA = "data/" + SpaceHorizons.MODID + "/";

    /** Todos os itens, incluindo os BlockItems (ModItems/ModBlocks não carregam sem o bootstrap do jogo). */
    private static List<String> itemIds() {
        return Stream.concat(ContentIds.ITEMS.stream(), ContentIds.BLOCKS.stream()).toList();
    }

    private static List<String> blockIds() {
        return ContentIds.BLOCKS;
    }

    /** Lê um recurso do classpath; null se não existir. */
    private static String ler(String caminho) throws IOException {
        try (InputStream in = ContentResourcesTest.class.getClassLoader().getResourceAsStream(caminho)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void exige(String caminho) throws IOException {
        assertTrue(ler(caminho) != null, "recurso ausente: " + caminho);
    }

    /**
     * O bloco tem textura: o PNG próprio ({@code textures/block/<id>.png}) ou, nos blocos com textura PROVISÓRIA (a arte final ainda não
     * existe, as naves da fase 1b), um modelo cujas texturas (as do próprio modelo e as dos modelos pais) existem de verdade.
     */
    private static void exigeTexturaDoBloco(String id) throws IOException {
        if (getResource(ASSETS + "textures/block/" + id + ".png")) {
            return;
        }
        Set<String> texturas = new HashSet<>();
        coletaTexturas("models/block/" + id, texturas, 0);
        assertFalse(texturas.isEmpty(), "bloco " + id + " sem textures/block/" + id + ".png e sem texturas no modelo");
        for (String textura : texturas) {
            String ns = textura.contains(":") ? textura.substring(0, textura.indexOf(':')) : "minecraft";
            String caminho = textura.contains(":") ? textura.substring(textura.indexOf(':') + 1) : textura;
            assertTrue(getResource("assets/" + ns + "/textures/" + caminho + ".png"), "textura provisória ausente de " + id + ": " + textura);
        }
    }

    private static boolean getResource(String caminho) {
        return ContentResourcesTest.class.getClassLoader().getResource(caminho) != null;
    }

    /** Junta as texturas (sem as referências "#x") do modelo e dos pais; {@code modelo} é o caminho sob assets/&lt;ns&gt;/. */
    private static void coletaTexturas(String modelo, Set<String> out, int fundo) throws IOException {
        assertTrue(fundo < 8, "cadeia de modelos pais longa demais em " + modelo);
        String json = ler(ASSETS + modelo + ".json");
        if (json == null) {
            return;      // pai do vanilla (cube_all, orientable...): as texturas dele são referências "#x" preenchidas pelo filho
        }
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        if (o.has("textures")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("textures").entrySet()) {
                JsonElement v = e.getValue();
                String textura = v.isJsonObject() ? v.getAsJsonObject().get("sprite").getAsString() : v.getAsString();
                if (!textura.startsWith("#")) {
                    out.add(textura);
                }
            }
        }
        if (o.has("parent")) {
            String pai = o.get("parent").getAsString();
            if (pai.startsWith(SpaceHorizons.MODID + ":")) {
                coletaTexturas("models/" + pai.substring(SpaceHorizons.MODID.length() + 1), out, fundo + 1);
            }
        }
    }

    @Test
    void naoHaIdsDuplicados() {
        assertFalse(itemIds().isEmpty());
        assertTrue(itemIds().stream().distinct().count() == itemIds().size(), "id repetido em ContentIds");
    }

    /** Pega o esquecimento inverso: item com arquivo em items/ mas fora de ContentIds (ficaria sem checagem). */
    @Test
    void todoItemModelEstaListadoEmContentIds() throws Exception {
        Path pasta = Path.of(ContentResourcesTest.class.getClassLoader().getResource(ASSETS + "items").toURI());
        try (Stream<Path> arquivos = Files.list(pasta)) {
            for (Path arquivo : arquivos.toList()) {
                String id = arquivo.getFileName().toString().replace(".json", "");
                assertTrue(itemIds().contains(id), "items/" + id + ".json não está em ContentIds");
            }
        }
    }

    @Test
    void todoItemTemLangModeloETextura() throws IOException {
        for (String id : itemIds()) {
            for (String idioma : List.of("en_us", "pt_br")) {
                String lang = ler(ASSETS + "lang/" + idioma + ".json");
                boolean ehBloco = blockIds().contains(id);
                String chave = (ehBloco ? "block." : "item.") + SpaceHorizons.MODID + "." + id;
                assertTrue(lang.contains("\"" + chave + "\""), idioma + " sem a chave " + chave);
            }
            exige(ASSETS + "items/" + id + ".json");
            if (blockIds().contains(id)) {
                exige(ASSETS + "models/block/" + id + ".json");
                exigeTexturaDoBloco(id);
            } else {
                exige(ASSETS + "models/item/" + id + ".json");
                exige(ASSETS + "textures/item/" + id + ".png");
            }
        }
    }

    @Test
    void todoBlocoTemBlockstateELootTable() throws IOException {
        for (String id : blockIds()) {
            exige(ASSETS + "blockstates/" + id + ".json");
            String loot = ler(DATA + "loot_table/blocks/" + id + ".json");
            assertTrue(loot != null, "loot table ausente para " + id);
            assertTrue(loot.contains(SpaceHorizons.MODID + ":" + id), "loot table de " + id + " não dropa o próprio bloco");
        }
    }

    @Test
    void abaDoCriativoTemNomeTraduzido() throws IOException {
        String chave = "\"itemGroup." + SpaceHorizons.MODID + ".main\"";
        for (String idioma : List.of("en_us", "pt_br")) {
            assertTrue(ler(ASSETS + "lang/" + idioma + ".json").contains(chave), idioma + " sem o nome da aba");
        }
    }
}
