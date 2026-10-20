package com.spacehorizons.ship;

/** Uma conversão recusada antes de tocar no mundo (a mensagem, em pt-BR, vai ao jogador). */
public final class ShipConversionException extends Exception {
    private static final long serialVersionUID = 1L;

    public ShipConversionException(String message) {
        super(message);
    }
}
