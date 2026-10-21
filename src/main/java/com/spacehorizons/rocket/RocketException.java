package com.spacehorizons.rocket;

import java.util.Locale;

import net.minecraft.network.chat.Component;

/**
 * Recusa de montar ou de pousar o foguete, com o motivo tipado e uma mensagem traduzível
 * ({@code message.spacehorizons.rocket.<motivo em minúsculas>}). Montar valida tudo antes de remover o primeiro bloco,
 * então uma exceção destas significa que o mundo não foi tocado.
 */
public final class RocketException extends Exception {
    public enum Reason {
        /** O ponto de partida não é um cockpit. */
        NO_COCKPIT,
        MULTIPLE_COCKPITS,
        NO_ENGINE,
        NO_TANK,
        /** Nave: nenhum motor para decolar (nem bocal de impulso para baixo, nem motor de foguete). */
        NO_LIFTOFF_ENGINE,
        /** Nenhum bloco do foguete encosta numa plataforma de lançamento. */
        NO_PAD,
        TOO_LARGE,
        TOUCHES_TERRAIN,
        FORBIDDEN_BLOCK,
        UNLOADED_CHUNK,
        PACKET_TOO_LARGE,
        /** Pouso: o espaço onde os blocos voltariam tem bloco que não pode ser substituído. */
        SPACE_OCCUPIED,
        /** Pouso: o foguete está inclinado demais para voltar a blocos. */
        NOT_UPRIGHT,
        /** Pouso: o foguete não está pousado (em voo). */
        NOT_LANDED,
        /** Comando: não há foguete por perto. */
        NO_ROCKET,
        /** Sem permissão para mexer nesta posição (proteção de spawn etc.). */
        NOT_ALLOWED
    }

    private final Reason reason;
    private final transient Object[] args;

    public RocketException(Reason reason, Object... args) {
        super(reason.name() + (args.length == 0 ? "" : " " + java.util.Arrays.toString(args)));
        this.reason = reason;
        this.args = args;
    }

    public Reason reason() {
        return this.reason;
    }

    public Object[] args() {
        return this.args.clone();
    }

    public String translationKey() {
        return "message.spacehorizons.rocket." + this.reason.name().toLowerCase(Locale.ROOT);
    }

    /** A mensagem para o jogador. */
    public Component component() {
        return Component.translatable(this.translationKey(), this.args);
    }
}
