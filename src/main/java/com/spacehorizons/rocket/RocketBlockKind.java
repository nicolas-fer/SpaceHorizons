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
    UNLOADED(false);

    private final boolean part;

    RocketBlockKind(boolean part) {
        this.part = part;
    }

    /** O bloco, se ligado ao foguete, vai junto com ele. */
    public boolean isPart() {
        return this.part;
    }
}
