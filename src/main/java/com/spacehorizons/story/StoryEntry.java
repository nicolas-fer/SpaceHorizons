package com.spacehorizons.story;

import java.util.List;

/**
 * Um registro da trama do sinal lunar (docs/PLANO_SOBREVIVENCIA.md, "Etapa 5: a trama"): um diário (item que o jogador lê) ou um terminal (bloco
 * que o jogador usa e que entrega uma recompensa uma vez por jogador). O texto não fica aqui: vem do lang, nas chaves
 * {@code story.spacehorizons.<id>.title}, {@code .author} e {@code .body}.
 *
 * @param id      id estável do registro (vai no componente {@code journal_entry} do diário e na conquista)
 * @param kind    diário ou terminal
 * @param site    onde o registro é achado
 * @param rewards recompensas do terminal, na ordem de entrega (vazio nos diários)
 */
public record StoryEntry(String id, Kind kind, Site site, List<Reward> rewards) {
    public enum Kind {
        DIARY, TERMINAL
    }

    /** Os lugares da trama: a estação na Terra e a base e os destroços da sonda na Lua. */
    public enum Site {
        EARTH_STATION("earth_station"), MOON_BASE("moon_base"), MOON_PROBE("moon_probe");

        private final String id;

        Site(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }

        public boolean onMoon() {
            return this != EARTH_STATION;
        }
    }

    /** O que um terminal entrega. */
    public enum Reward {
        /** A planta do foguete orbital (item). */
        BLUEPRINT_ORBITAL("blueprint_orbital"),
        /** A planta do foguete lunar (item); também a destrava no menu das plantas em sobrevivência. */
        BLUEPRINT_LUNAR("blueprint_lunar"),
        /** Bússola que aponta para a base lunar (só aponta na Lua). */
        COMPASS_MOON_BASE("compass_moon_base"),
        /** Bússola que aponta para os destroços da sonda (só aponta na Lua). */
        COMPASS_MOON_PROBE("compass_moon_probe"),
        /** Bússola que aponta para o ponto do sinal em Marte (só aponta em Marte) e as coordenadas no chat: o gancho do Tier 2. */
        COMPASS_MARS("compass_mars");

        private final String id;

        Reward(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }
    }

    public StoryEntry {
        rewards = List.copyOf(rewards);
        if (kind == Kind.DIARY && !rewards.isEmpty()) {
            throw new IllegalArgumentException("diário não entrega recompensa: " + id);
        }
    }

    public boolean isTerminal() {
        return this.kind == Kind.TERMINAL;
    }

    /** A chave de lang de uma parte do texto ({@code title}, {@code author} ou {@code body}). */
    public String textKey(String part) {
        return StoryEntries.textKey(this.id, part);
    }
}
