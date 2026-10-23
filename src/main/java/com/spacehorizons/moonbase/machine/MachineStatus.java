package com.spacehorizons.moonbase.machine;

/** Situação de uma máquina da base lunar (a ordem é o id enviado à tela; só se acrescenta no fim). */
public enum MachineStatus {
    /** Nada a fazer. */
    IDLE,
    /** Falta energia. */
    NO_POWER,
    /** Falta a entrada (gelo, regolito, tanque, galão). */
    NO_INPUT,
    /** A saída está cheia (tanque ou galão cheio, reservatório cheio). */
    OUTPUT_FULL,
    WORKING,
    /** Só funciona na Lua (o hélio-3 vem do regolito lunar). */
    WRONG_PLANET,
    /** Gerador de oxigênio: sala selada e pressurizada. */
    PRESSURIZED,
    /** Gerador de oxigênio: sala selada, a pressão está subindo. */
    PRESSURIZING,
    /** Gerador de oxigênio: a sala vaza (porta aberta ou parede quebrada). */
    LEAK,
    /** Gerador de oxigênio: o ar alcançável passa do teto de volume. */
    TOO_BIG,
    /** Gerador de oxigênio: nenhum lado está livre (sem sala). */
    BLOCKED,
    /** Gerador de oxigênio: sala selada mas sem oxigênio no reservatório. */
    NO_OXYGEN;

    private static final MachineStatus[] VALUES = values();

    public static MachineStatus byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : IDLE;
    }
}
