package com.spacehorizons.ship;

import java.util.function.BooleanSupplier;

/**
 * O estado da âncora no ar para o painel do piloto e para a tecla (fase 2f, {@code docs/PLANO_NAVE_ATMOSFERA.md}). Lógica pura: {@link #classify}
 * escolhe o primeiro impedimento, na ordem em que o jogador os resolve (o mais barato de checar primeiro; o volume livre, que varre o mundo, por último).
 * O id (ordinal) vai pela rede em {@link AnchorStatusPayload}; a ordem do enum é o protocolo.
 */
public enum AnchorStatus {
    /** Não é uma nave montada pela ponte, ou está no estaleiro ou no espaço: sem indicação. */
    NOT_SHIP,
    /** Pode ancorar agora. */
    READY,
    /** A nave não tem gerador de antigravidade. */
    NO_GENERATOR,
    /** Tem gerador, mas nenhum carregado. */
    NO_ENERGY,
    /** Há blocos no volume da nave ou encostados nela. */
    NO_ROOM,
    /** Velocidade acima do limite. */
    TOO_FAST,
    /** Inclinada além do limite. */
    TOO_TILTED,
    /** Já está ancorando (acomodação ou conversão em andamento). */
    BUSY,
    /** Paraquedas aberto. */
    PARACHUTE,
    /** Acabou de ancorar (só avisado ao piloto que estava a bordo). */
    ANCHORED;

    private static final AnchorStatus[] VALUES = values();

    public static AnchorStatus byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NOT_SHIP;
    }

    /** A chave da tradução (sufixo) deste estado: {@code hud.spacehorizons.anchor.state.<key>} e {@code message.spacehorizons.anchor.<key>}. */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Recusa que o jogador precisa ouvir (tudo, menos pronto, sem indicação e já ancorada). */
    public boolean refuses() {
        return this != READY && this != ANCHORED;
    }

    /**
     * O primeiro impedimento, ou {@link #READY}. {@code roomFree} só é chamado se todo o resto passar (varre o mundo).
     *
     * @param anchorable    nave montada pela ponte, com blocos, fora do estaleiro e do espaço
     * @param needGenerator o gerador é exigido (a tecla sim; o comando de dev do spike não)
     * @param generators    quantos geradores a nave tem
     * @param charged       quantos estão carregados
     */
    public static AnchorStatus classify(boolean anchorable, boolean busy, boolean parachute, double speed, double tiltDegrees, boolean needGenerator,
            int generators, int charged, BooleanSupplier roomFree) {
        if (!anchorable) {
            return NOT_SHIP;
        }
        if (busy) {
            return BUSY;
        }
        if (parachute) {
            return PARACHUTE;
        }
        if (speed > ShipAnchors.MAX_SPEED) {
            return TOO_FAST;
        }
        if (tiltDegrees > ShipAnchors.MAX_TILT_DEGREES) {
            return TOO_TILTED;
        }
        if (needGenerator) {
            if (generators <= 0) {
                return NO_GENERATOR;
            }
            if (charged <= 0) {
                return NO_ENERGY;
            }
        }
        return roomFree.getAsBoolean() ? READY : NO_ROOM;
    }
}
