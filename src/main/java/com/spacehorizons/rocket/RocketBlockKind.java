package com.spacehorizons.rocket;

/**
 * Papel de um bloco do mundo para a montagem do foguete. Lógica pura (sem registros do Minecraft), para que a busca e a
 * validação da montagem sejam testáveis. Quem converte um BlockState nisto é {@link RocketKinds}.
 */
public enum RocketBlockKind {
    /** Vazio ou substituível (ar, grama, neve fina): não entra no foguete e não o impede. */
    AIR(false),
    /** Plataforma de lançamento: a busca para nela; o foguete não a leva. */
    PAD(false),
    COCKPIT(true),
    ENGINE(true),
    FUEL_TANK(true),
    PARACHUTE(true),
    LEG(true),
    HULL(true),
    /** Qualquer outro bloco ligado (vidro, baú, placas...): entra como estrutura, com a massa padrão. */
    STRUCTURE(true),
    /** Terreno natural encostado no foguete: a montagem é recusada. */
    TERRAIN(false),
    /** Bloco que nunca vira parte de foguete (bedrock, dureza -1, fluido...): a montagem é recusada. */
    FORBIDDEN(false),
    /** Chunk não carregado: a montagem é recusada. */
    UNLOADED(false),
    // --- Tier 2 (docs/PLANO_TIERS.md, "Tier 2: conteúdo"): no fim, para não mudar a ordem dos de antes ---
    /** Motor nuclear térmico: queima hidrogênio enquanto o núcleo (barras de combustível nuclear) durar. */
    NUCLEAR_ENGINE(true),
    /** Motor iônico: queima xenônio com a energia dos geradores {@link #RTG}; empuxo minúsculo, só serve no espaço. */
    ION_ENGINE(true),
    /** Motor de fusão: queima o hélio-3 guardado nele mesmo. */
    FUSION_ENGINE(true),
    /** Tanque de hidrogênio (mesma regra de coluna do tanque químico). */
    HYDROGEN_TANK(true),
    /** Tanque de xenônio (mesma regra de coluna do tanque químico). */
    XENON_TANK(true),
    /** Gerador radioisotópico: a energia do motor iônico. */
    RTG(true);

    private final boolean part;

    RocketBlockKind(boolean part) {
        this.part = part;
    }

    /** O bloco, se ligado ao foguete, vai junto com ele. */
    public boolean isPart() {
        return this.part;
    }

    /** Qualquer motor (o químico e os do Tier 2). */
    public boolean isEngine() {
        return this == ENGINE || this == NUCLEAR_ENGINE || this == ION_ENGINE || this == FUSION_ENGINE;
    }

    /** Qualquer tanque (o químico e os do Tier 2). */
    public boolean isTank() {
        return this == FUEL_TANK || this == HYDROGEN_TANK || this == XENON_TANK;
    }
}
