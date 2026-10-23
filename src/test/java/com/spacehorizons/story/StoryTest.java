package com.spacehorizons.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.spacehorizons.content.ContentIds;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.travel.TravelMath;
import com.spacehorizons.story.StoryEntry.Reward;
import com.spacehorizons.story.StoryEntry.Site;

/** A trama do sinal lunar: registros, textos nos dois idiomas, recompensa uma vez por jogador, desenhos dos lugares e posições fixas na Lua. */
class StoryTest {
    private static String ler(String caminho) throws IOException {
        try (InputStream in = StoryTest.class.getClassLoader().getResourceAsStream(caminho)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JsonObject lang(String idioma) throws IOException {
        return JsonParser.parseString(ler("assets/spacehorizons/lang/" + idioma + ".json")).getAsJsonObject();
    }

    @Test
    void registrosSaoNoveComTresTerminais() {
        assertEquals(9, StoryEntries.ALL.size());
        assertEquals(9, StoryEntries.ALL.stream().map(StoryEntry::id).distinct().count());
        for (Site site : Site.values()) {
            assertEquals(3, StoryEntries.of(site).size(), "três registros por lugar: " + site);
            assertTrue(StoryEntries.terminalOf(site).isTerminal());
        }
        assertEquals(List.of(Reward.BLUEPRINT_ORBITAL, Reward.COMPASS_MOON_BASE), StoryEntries.byId("aurora_terminal").rewards());
        assertTrue(StoryEntries.byId("selene_terminal").rewards().contains(Reward.BLUEPRINT_LUNAR), "a planta lunar vem do terminal da base");
        assertNull(StoryEntries.byId("nada"));
        assertEquals("aurora", StoryEntries.terminalRecordName(StoryEntries.byId("aurora_terminal")));
        assertNull(StoryEntries.terminalForRecord("voss_01"), "diário não é terminal");
    }

    @Test
    void textosCarregamNosDoisIdiomas() throws IOException {
        for (String idioma : List.of("en_us", "pt_br")) {
            JsonObject lang = lang(idioma);
            for (StoryEntry entry : StoryEntries.ALL) {
                for (String parte : List.of("title", "author", "body")) {
                    String chave = entry.textKey(parte);
                    assertTrue(lang.has(chave) && !lang.get(chave).getAsString().isBlank(), idioma + " sem " + chave);
                }
                assertTrue(lang.get(entry.textKey("body")).getAsString().length() > 200, idioma + ": texto curto demais em " + entry.id());
                for (Reward reward : entry.rewards()) {
                    assertTrue(lang.has("story.spacehorizons.reward." + reward.id()), idioma + " sem a recompensa " + reward.id());
                    assertTrue(lang.has("message.spacehorizons.story.reward." + reward.id()), idioma + " sem a mensagem de " + reward.id());
                }
            }
            assertTrue(lang.has("item.spacehorizons.journal_entry") && lang.has("block.spacehorizons.data_terminal"));
        }
    }

    @Test
    void recompensaVemUmaVezPorJogador() {
        StoryEntry terminal = StoryEntries.byId("selene_terminal");
        StoryProgress novo = StoryProgress.EMPTY;
        assertFalse(novo.unlocksBlueprint("lunar"));
        StoryProgress.Claim primeira = novo.claim(terminal);
        assertTrue(primeira.first());
        assertEquals(terminal.rewards(), primeira.granted());
        assertTrue(primeira.progress().hasClaimed("selene_terminal") && primeira.progress().hasRead("selene_terminal"));
        assertTrue(primeira.progress().unlocksBlueprint("lunar"), "a planta lunar destrava ao levar o anexo do terminal da base");
        assertFalse(primeira.progress().unlocksBlueprint("orbital"));
        StoryProgress.Claim segunda = primeira.progress().claim(terminal);
        assertFalse(segunda.first());
        assertTrue(segunda.granted().isEmpty(), "a segunda vez não entrega nada");
        assertEquals(primeira.progress(), segunda.progress());
        // outro jogador (progresso vazio) leva a recompensa de novo, e ler um diário não destrava nada
        assertTrue(StoryProgress.EMPTY.claim(terminal).first());
        assertFalse(StoryProgress.EMPTY.withRead("voss_01").claim(StoryEntries.byId("aurora_terminal")).granted().isEmpty());
        assertFalse(StoryProgress.EMPTY.withRead("selene_terminal").unlocksBlueprint("lunar"));
        assertThrowsDiary();
    }

    private static void assertThrowsDiary() {
        boolean lancou = false;
        try {
            StoryProgress.EMPTY.claim(StoryEntries.byId("voss_01"));
        } catch (IllegalArgumentException e) {
            lancou = true;
        }
        assertTrue(lancou, "só terminal entrega recompensa");
    }

    @Test
    void desenhosTemOsTerminaisEBausDosRegistros() throws IOException {
        for (Site site : Site.values()) {
            SiteLayout layout = SiteLayouts.of(site);
            List<SiteLayout.Terminal> terminals = layout.terminals();
            assertEquals(1, terminals.size(), "um terminal em " + site);
            assertEquals(StoryEntries.terminalOf(site).id(), terminals.get(0).record() + "_terminal");
            assertEquals(1, layout.chests().size(), "um baú de diários em " + site);
            String loot = layout.chests().get(0).loot();
            assertEquals("story/" + site.id(), loot);
            String tabela = ler("data/spacehorizons/loot_table/" + loot + ".json");
            assertNotNull(tabela, "tabela de saque ausente: " + loot);
            for (StoryEntry entry : StoryEntries.of(site)) {
                if (!entry.isTerminal()) {
                    assertTrue(tabela.contains("\"" + entry.id() + "\""), loot + " não entrega o diário " + entry.id());
                }
            }
            for (SiteLayout.Op op : layout.ops()) {
                assertInside(layout, op);
            }
            for (String state : layout.states()) {
                assertTrue(state.matches("[a-z_]+:[a-z_]+(\\[[a-z_=,]+])?"), "estado malformado: " + state);
                if (state.startsWith("spacehorizons:")) {
                    String id = state.substring(14).replaceAll("\\[.*", "");
                    assertTrue(ContentIds.BLOCKS.contains(id), "bloco do mod inexistente: " + id);
                }
            }
        }
    }

    private static void assertInside(SiteLayout l, SiteLayout.Op op) {
        int[] b = switch (op) {
            case SiteLayout.Box x -> new int[] {x.x0(), x.y0(), x.z0(), x.x1(), x.y1(), x.z1()};
            case SiteLayout.Mix x -> new int[] {x.x0(), x.y0(), x.z0(), x.x1(), x.y1(), x.z1()};
            case SiteLayout.Chest x -> new int[] {x.x(), x.y(), x.z(), x.x(), x.y(), x.z()};
            case SiteLayout.Terminal x -> new int[] {x.x(), x.y(), x.z(), x.x(), x.y(), x.z()};
        };
        for (int i = 0; i < 6; i += 3) {
            assertTrue(b[i] >= 0 && b[i] < l.width() && b[i + 1] >= 0 && b[i + 1] < l.height() + 1 && b[i + 2] >= 0 && b[i + 2] < l.depth(),
                    l.id() + ": operação fora da pegada " + op);
        }
    }

    @Test
    void desgasteEDeterministico() {
        SiteLayout.Mix mix = new SiteLayout.Mix(0, 0, 0, 9, 0, 9, "a:a", "b:b", 30, 7);
        int b = 0;
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                String s = SiteLayout.stateAt(mix, x, 0, z);
                assertEquals(s, SiteLayout.stateAt(mix, x, 0, z));
                if (s.equals("b:b")) {
                    b++;
                }
            }
        }
        assertTrue(b > 10 && b < 55, "cerca de 30% trocados, vieram " + b);
        assertNull(SiteLayout.stateAt(mix, 10, 0, 0));
    }

    @Test
    void lugaresDaLuaTemPosicaoFixaPlanaPertoDoPontoVoltadoParaATerra() {
        double[] facing = new double[2];
        TravelMath.facingPoint(SolarSystem.MOON, facing);
        Set<String> vistos = new HashSet<>();
        for (Site site : StorySites.MOON_SITES) {
            StorySites.Placement p = StorySites.moon(site);
            assertEquals(p, StorySites.moon(site), "mesma posição sempre");
            assertTrue(p.roughness() <= 16, site + ": relevo plano o bastante sob a pegada, diferença " + p.roughness());
            double dist = Math.hypot(p.centerX() - facing[0], p.centerZ() - facing[1]);
            assertTrue(dist < 1500, site + " a " + dist + " blocos do ponto voltado para a Terra");
            assertTrue(Math.abs(p.centerX() + 81_920) > 300, "longe da costura em X: " + p.centerX());
            assertTrue(p.intersectsChunk(p.centerChunkX(), p.centerChunkZ()));
            assertFalse(p.intersectsChunk(p.centerChunkX() + 40, p.centerChunkZ()));
            assertTrue(vistos.add(p.minX() + "," + p.minZ()));
        }
        StorySites.Placement base = StorySites.moon(Site.MOON_BASE);
        StorySites.Placement probe = StorySites.moon(Site.MOON_PROBE);
        assertTrue(Math.hypot(base.centerX() - probe.centerX(), base.centerZ() - probe.centerZ()) > 300, "a sonda fica longe da base");
        assertEquals(List.of(base), StorySites.moonPlacementsTouching(base.centerChunkX(), base.centerChunkZ()));
    }

    @Test
    void conquistasDaTramaSaoUmaCorrente() throws IOException {
        List<String> nomes = List.of("story_observatory", "story_coordinates", "story_selene", "story_lunar_plans", "story_probe", "story_mars");
        String pai = "root";
        for (String nome : nomes) {
            JsonObject adv = JsonParser.parseString(ler("data/spacehorizons/advancement/" + nome + ".json")).getAsJsonObject();
            assertEquals("spacehorizons:" + pai, adv.get("parent").getAsString());
            String entry = adv.getAsJsonObject("criteria").getAsJsonObject("read").getAsJsonObject("conditions").get("entry").getAsString();
            assertNotNull(StoryEntries.byId(entry), nome + " cita um registro que não existe: " + entry);
            pai = nome;
        }
    }
}
