package com.spacehorizons.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.spacehorizons.SpaceHorizons;

/**
 * Valida as conquistas do mod (data/spacehorizons/advancement): JSON válido, ids de item existentes, pais existentes,
 * textos nos dois idiomas e uma conquista de desbloqueio para cada receita do livro de receitas.
 */
class AdvancementsTest {
    private static final String NS = SpaceHorizons.MODID;
    private static final Path RAIZ = pastaDeDados();

    private static Path pastaDeDados() {
        try {
            URL pasta = AdvancementsTest.class.getClassLoader().getResource("data/" + NS + "/advancement");
            assertTrue(pasta != null, "pasta de conquistas ausente");
            return Path.of(pasta.toURI());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String ler(String caminho) throws IOException {
        try (InputStream in = AdvancementsTest.class.getClassLoader().getResourceAsStream(caminho)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JsonObject json(Path arquivo) throws IOException {
        try (Reader r = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    /** Todas as conquistas: id (sem namespace, ex. "recipes/misc/steel_plate") -> JSON. */
    private static Map<String, JsonObject> conquistas() throws Exception {
        Map<String, JsonObject> mapa = new java.util.TreeMap<>();
        try (Stream<Path> arquivos = Files.walk(RAIZ)) {
            for (Path p : arquivos.filter(a -> a.toString().endsWith(".json")).toList()) {
                String id = RAIZ.relativize(p).toString().replace('\\', '/');
                mapa.put(id.substring(0, id.length() - ".json".length()), json(p));
            }
        }
        return mapa;
    }

    private static boolean itemExiste(String ref) throws IOException {
        if (ref.startsWith("#")) {
            String[] partes = ref.substring(1).split(":", 2);
            return ler("data/" + partes[0] + "/tags/item/" + partes[1] + ".json") != null;
        }
        String[] partes = ref.split(":", 2);
        assertTrue(partes.length == 2, "id sem namespace: " + ref);
        if (partes[0].equals(NS)) {
            return ContentIds.ITEMS.contains(partes[1]) || ContentIds.BLOCKS.contains(partes[1]);
        }
        return ler("assets/" + partes[0] + "/items/" + partes[1] + ".json") != null;
    }

    /** Percorre o JSON todo, coletando referências a itens ("items", "icon.id") e a receitas ("recipes"). */
    private static void coletar(String chave, JsonElement e, List<String> itens, List<String> receitas, boolean noIcone) {
        if (e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> filho : e.getAsJsonObject().entrySet()) {
                coletar(filho.getKey(), filho.getValue(), itens, receitas, filho.getKey().equals("icon"));
            }
        } else if (e.isJsonArray()) {
            for (JsonElement filho : e.getAsJsonArray()) {
                coletar(chave, filho, itens, receitas, false);
            }
        } else if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            if (chave.equals("items")) {
                itens.add(e.getAsString());
            } else if (chave.equals("recipes")) {
                receitas.add(e.getAsString());
            }
        }
        if (noIcone && e.isJsonObject() && e.getAsJsonObject().has("id")) {
            itens.add(e.getAsJsonObject().get("id").getAsString());
        }
    }

    @Test
    void todasSaoJsonValidoComEstruturaMinima() throws Exception {
        Map<String, JsonObject> todas = conquistas();
        assertFalse(todas.isEmpty(), "nenhuma conquista encontrada");
        List<String> problemas = new ArrayList<>();
        for (Map.Entry<String, JsonObject> c : todas.entrySet()) {
            JsonObject o = c.getValue();
            if (!o.has("criteria") || o.getAsJsonObject("criteria").isEmpty()) {
                problemas.add(c.getKey() + ": sem criteria");
                continue;
            }
            Set<String> criterios = o.getAsJsonObject("criteria").keySet();
            if (!o.has("requirements")) {
                problemas.add(c.getKey() + ": sem requirements");
                continue;
            }
            Set<String> usados = new HashSet<>();
            for (JsonElement grupo : o.getAsJsonArray("requirements")) {
                for (JsonElement nome : grupo.getAsJsonArray()) {
                    usados.add(nome.getAsString());
                }
            }
            if (!criterios.equals(usados)) {
                problemas.add(c.getKey() + ": requirements " + usados + " difere de criteria " + criterios);
            }
            for (JsonElement crit : o.getAsJsonObject("criteria").asMap().values()) {
                if (!crit.getAsJsonObject().has("trigger")) {
                    problemas.add(c.getKey() + ": critério sem trigger");
                }
            }
        }
        assertTrue(problemas.isEmpty(), String.join("\n", problemas));
    }

    @Test
    void idsDeItemEReceitasCitadosExistem() throws Exception {
        List<String> problemas = new ArrayList<>();
        for (Map.Entry<String, JsonObject> c : conquistas().entrySet()) {
            List<String> itens = new ArrayList<>();
            List<String> receitas = new ArrayList<>();
            coletar("", c.getValue(), itens, receitas, false);
            for (String item : itens) {
                if (!itemExiste(item)) {
                    problemas.add(c.getKey() + " referencia o item " + item + " que não existe");
                }
            }
            for (String receita : receitas) {
                String[] p = receita.split(":", 2);
                if (!p[0].equals(NS) || ler("data/" + NS + "/recipe/" + p[1] + ".json") == null) {
                    problemas.add(c.getKey() + " referencia a receita " + receita + " que não existe no mod");
                }
            }
        }
        assertTrue(problemas.isEmpty(), String.join("\n", problemas));
    }

    @Test
    void paisExistemEChegamAUmaRaiz() throws Exception {
        Map<String, JsonObject> todas = conquistas();
        List<String> problemas = new ArrayList<>();
        int raizes = 0;
        for (Map.Entry<String, JsonObject> c : todas.entrySet()) {
            JsonObject o = c.getValue();
            if (!o.has("parent")) {
                raizes++;
                if (!o.has("display") || !o.getAsJsonObject("display").has("background")) {
                    problemas.add(c.getKey() + ": raiz sem display.background (não abre aba)");
                }
                continue;
            }
            String pai = o.get("parent").getAsString();
            String[] p = pai.split(":", 2);
            boolean existe = p[0].equals(NS) ? todas.containsKey(p[1])
                    : ler("data/" + p[0] + "/advancement/" + p[1] + ".json") != null;
            if (!existe) {
                problemas.add(c.getKey() + ": pai " + pai + " não existe");
            }
            // corrente de pais do mod sem ciclos
            Set<String> visto = new HashSet<>();
            String atual = c.getKey();
            while (atual != null && todas.containsKey(atual) && todas.get(atual).has("parent")) {
                if (!visto.add(atual)) {
                    problemas.add(c.getKey() + ": ciclo de pais");
                    break;
                }
                String[] pp = todas.get(atual).get("parent").getAsString().split(":", 2);
                atual = pp[0].equals(NS) ? pp[1] : null;
            }
        }
        // uma raiz de aba ("root") e nenhuma outra sem pai
        assertTrue(raizes == 1, "esperada exatamente 1 conquista sem pai, achei " + raizes);
        assertTrue(todas.containsKey("root"), "falta a conquista root");
        assertTrue(problemas.isEmpty(), String.join("\n", problemas));
    }

    @Test
    void textosExistemEmEnUsEPtBr() throws Exception {
        List<String> problemas = new ArrayList<>();
        for (String idioma : List.of("en_us", "pt_br")) {
            String texto = ler("assets/" + NS + "/lang/" + idioma + ".json");
            assertTrue(texto != null, "lang ausente: " + idioma);
            JsonObject lang = JsonParser.parseString(texto).getAsJsonObject();
            for (Map.Entry<String, JsonObject> c : conquistas().entrySet()) {
                if (!c.getValue().has("display")) {
                    continue;
                }
                JsonObject display = c.getValue().getAsJsonObject("display");
                for (String campo : new String[] {"title", "description"}) {
                    String chave = display.getAsJsonObject(campo).get("translate").getAsString();
                    if (!lang.has(chave) || lang.get(chave).getAsString().isBlank()) {
                        problemas.add(idioma + " sem a chave " + chave + " (" + c.getKey() + ")");
                    }
                }
            }
        }
        assertTrue(problemas.isEmpty(), String.join("\n", problemas));
    }

    @Test
    void todaReceitaDoLivroTemConquistaDeDesbloqueio() throws Exception {
        Set<String> desbloqueadas = new HashSet<>();
        for (Map.Entry<String, JsonObject> c : conquistas().entrySet()) {
            JsonObject o = c.getValue();
            if (!c.getKey().startsWith("recipes/")) {
                continue;
            }
            assertTrue(o.has("rewards") && o.getAsJsonObject("rewards").has("recipes"), c.getKey() + " sem rewards.recipes");
            assertTrue(o.get("parent").getAsString().equals("minecraft:recipes/root"),
                    c.getKey() + " deve ter pai minecraft:recipes/root");
            JsonArray lista = o.getAsJsonObject("rewards").getAsJsonArray("recipes");
            for (JsonElement r : lista) {
                desbloqueadas.add(r.getAsString());
            }
        }
        URL pasta = AdvancementsTest.class.getClassLoader().getResource("data/" + NS + "/recipe");
        assertTrue(pasta != null, "pasta de receitas ausente");
        List<String> faltando = new ArrayList<>();
        // só as receitas diretamente em recipe/ (as de recipe/refining/ são da máquina, fora do livro de receitas)
        try (Stream<Path> arquivos = Files.list(Path.of(pasta.toURI()))) {
            for (Path p : arquivos.filter(a -> a.toString().endsWith(".json")).toList()) {
                String nome = p.getFileName().toString();
                String id = NS + ":" + nome.substring(0, nome.length() - ".json".length());
                if (!desbloqueadas.contains(id)) {
                    faltando.add(id);
                }
            }
        }
        assertTrue(faltando.isEmpty(), "receitas sem conquista de desbloqueio: " + faltando);
    }
}
