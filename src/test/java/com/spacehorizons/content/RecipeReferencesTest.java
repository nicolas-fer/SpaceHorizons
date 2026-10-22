package com.spacehorizons.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.spacehorizons.SpaceHorizons;

/**
 * Garante que toda receita do mod só referencia itens que existem: do mod (ContentIds) ou do Minecraft
 * (confere o item model em assets/minecraft/items/, que está no jar do jogo no classpath de teste).
 * Tags ("#ns:tag") são conferidas em data/ns/tags/item/.
 */
class RecipeReferencesTest {
    /** Texto entre aspas com forma de id (com namespace), com '#' opcional para tags. */
    private static final Pattern ID = Pattern.compile("\"(#?)([a-z0-9_.-]+):([a-z0-9_./-]+)\"");
    /** Remove "type": "..." (tipo da receita não é um item). */
    private static final Pattern TIPO = Pattern.compile("\"type\"\\s*:\\s*\"[^\"]*\"");
    /** Remove "components": {...} do resultado (as chaves são ids de data component, não de item; ex. o galão cheio do Tier 2). */
    private static final Pattern COMPONENTES = Pattern.compile("\"components\"\\s*:\\s*\\{[^}]*\\}");

    private static String ler(String caminho) throws IOException {
        try (InputStream in = RecipeReferencesTest.class.getClassLoader().getResourceAsStream(caminho)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<Path> receitas() throws Exception {
        URL pasta = RecipeReferencesTest.class.getClassLoader().getResource("data/" + SpaceHorizons.MODID + "/recipe");
        assertTrue(pasta != null, "pasta de receitas ausente");
        try (Stream<Path> arquivos = Files.list(Path.of(pasta.toURI()))) {
            return arquivos.filter(p -> p.toString().endsWith(".json")).toList();
        }
    }

    private static boolean itemDoModExiste(String id) {
        return ContentIds.ITEMS.contains(id) || ContentIds.BLOCKS.contains(id);
    }

    @Test
    void receitasSoReferenciamIdsExistentes() throws Exception {
        List<String> problemas = new ArrayList<>();
        List<Path> arquivos = receitas();
        assertFalse(arquivos.isEmpty(), "nenhuma receita encontrada");
        for (Path arquivo : arquivos) {
            String json = COMPONENTES.matcher(TIPO.matcher(Files.readString(arquivo)).replaceAll("")).replaceAll("");
            Matcher m = ID.matcher(json);
            while (m.find()) {
                boolean tag = !m.group(1).isEmpty();
                String ns = m.group(2);
                String id = m.group(3);
                String ref = (tag ? "#" : "") + ns + ":" + id;
                boolean ok;
                if (tag) {
                    ok = ler("data/" + ns + "/tags/item/" + id + ".json") != null;
                } else if (ns.equals(SpaceHorizons.MODID)) {
                    ok = itemDoModExiste(id);
                } else {
                    ok = ler("assets/" + ns + "/items/" + id + ".json") != null;
                }
                if (!ok) {
                    problemas.add(arquivo.getFileName() + " referencia " + ref + " que não existe");
                }
            }
        }
        assertTrue(problemas.isEmpty(), String.join("\n", problemas));
    }
}
