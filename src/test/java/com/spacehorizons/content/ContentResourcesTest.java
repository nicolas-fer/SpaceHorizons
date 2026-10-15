package com.spacehorizons.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

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
                exige(ASSETS + "textures/block/" + id + ".png");
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
