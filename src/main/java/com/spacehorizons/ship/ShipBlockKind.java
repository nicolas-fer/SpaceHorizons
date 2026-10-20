package com.spacehorizons.ship;

/**
 * Papel de um bloco da nave para o censo ({@link ShipCensus}) e a especificação ({@link ShipSpecs}). Lógica pura (sem registros do
 * Minecraft), como {@code RocketBlockKind}; quem converte um BlockState nisto é {@link ShipKinds}.
 */
public enum ShipBlockKind {
    /** Ar: não entra na nave. */
    AIR,
    /** Ponte de comando: o núcleo que define a nave (um só). */
    BRIDGE,
    /** Motor de impulso: empuxo para a proa. */
    ENGINE,
    /** Tanque de combustível (o mesmo bloco do foguete). */
    FUEL_TANK,
    /** Janela de vidro. */
    WINDOW,
    /** Assento do piloto (ou de um tripulante). */
    SEAT,
    /** Porta/eclusa. */
    AIRLOCK,
    /** Qualquer outro bloco (casco, vidro vanilla, baús, lâmpadas...): entra como estrutura, com a massa padrão. */
    STRUCTURE
}
