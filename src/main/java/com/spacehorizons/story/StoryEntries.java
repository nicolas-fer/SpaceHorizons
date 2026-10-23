package com.spacehorizons.story;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.spacehorizons.story.StoryEntry.Kind;
import com.spacehorizons.story.StoryEntry.Reward;
import com.spacehorizons.story.StoryEntry.Site;

/**
 * Os nove registros da trama "o sinal lunar" (texto no lang; resumo e ordem em docs/PLANO_SOBREVIVENCIA.md):
 *
 * <ol>
 *   <li>Observatório Aurora (Terra): {@code voss_01}, {@code voss_02} (diários) e {@code aurora_terminal} (planta orbital e bússola da base).</li>
 *   <li>Base Selene-1 (Lua): {@code reyes_01}, {@code reyes_02} (diários) e {@code selene_terminal} (planta lunar e bússola da sonda).</li>
 *   <li>Destroços da sonda Vigia-3 (Lua): {@code voss_03}, {@code mars_hook} (diários) e {@code vigia_terminal} (o sinal vem de Marte).</li>
 * </ol>
 */
public final class StoryEntries {
    public static final String AURORA_TERMINAL = "aurora_terminal";
    public static final String SELENE_TERMINAL = "selene_terminal";
    public static final String VIGIA_TERMINAL = "vigia_terminal";
    public static final String MARS_HOOK = "mars_hook";

    public static final List<StoryEntry> ALL = List.of(
            new StoryEntry("voss_01", Kind.DIARY, Site.EARTH_STATION, List.of()),
            new StoryEntry("voss_02", Kind.DIARY, Site.EARTH_STATION, List.of()),
            new StoryEntry(AURORA_TERMINAL, Kind.TERMINAL, Site.EARTH_STATION, List.of(Reward.BLUEPRINT_ORBITAL, Reward.COMPASS_MOON_BASE)),
            new StoryEntry("reyes_01", Kind.DIARY, Site.MOON_BASE, List.of()),
            new StoryEntry("reyes_02", Kind.DIARY, Site.MOON_BASE, List.of()),
            new StoryEntry(SELENE_TERMINAL, Kind.TERMINAL, Site.MOON_BASE, List.of(Reward.BLUEPRINT_LUNAR, Reward.COMPASS_MOON_PROBE)),
            new StoryEntry("voss_03", Kind.DIARY, Site.MOON_PROBE, List.of()),
            new StoryEntry(MARS_HOOK, Kind.DIARY, Site.MOON_PROBE, List.of()),
            new StoryEntry(VIGIA_TERMINAL, Kind.TERMINAL, Site.MOON_PROBE, List.of(Reward.COMPASS_MARS)));

    /** O ponto do sinal em Marte (o gancho do Tier 2): coordenadas do mundo de Marte, dentro da calota polar de 75 graus. */
    public static final int MARS_SIGNAL_X = 3_000;
    public static final int MARS_SIGNAL_Z = -1_500;

    private StoryEntries() {
    }

    public static @Nullable StoryEntry byId(String id) {
        for (StoryEntry e : ALL) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        return null;
    }

    /** A chave de lang de uma parte do texto de um registro. */
    public static String textKey(String id, String part) {
        return "story.spacehorizons." + id + "." + part;
    }

    /** Os registros de um lugar, na ordem da trama. */
    public static List<StoryEntry> of(Site site) {
        return ALL.stream().filter(e -> e.site() == site).toList();
    }

    /** O registro do terminal de um lugar (todo lugar tem exatamente um). */
    public static StoryEntry terminalOf(Site site) {
        return ALL.stream().filter(e -> e.site() == site && e.isTerminal()).findFirst().orElseThrow();
    }

    /** O valor do estado {@code record} do bloco do terminal ({@code aurora}, {@code selene}, {@code vigia}): o id do registro sem o sufixo. */
    public static String terminalRecordName(StoryEntry terminal) {
        String id = terminal.id();
        return id.substring(0, id.length() - "_terminal".length()).toLowerCase(Locale.ROOT);
    }

    public static @Nullable StoryEntry terminalForRecord(String record) {
        StoryEntry entry = byId(record + "_terminal");
        return entry != null && entry.isTerminal() ? entry : null;
    }
}
