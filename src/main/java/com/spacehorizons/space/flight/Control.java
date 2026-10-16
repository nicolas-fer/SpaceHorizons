package com.spacehorizons.space.flight;

/**
 * Comando de um tick para o integrador: acelerador (0..1), direção INERCIAL do empuxo (unitária, nos eixos do espaço: é o
 * nariz do foguete) e o pedido de abrir o paraquedas.
 *
 * <p>O construtor normaliza: o acelerador vira 0..1 (NaN vira 0) e a direção vira unitária (nula fica nula, sem empuxo).
 */
public record Control(double throttle, V3 thrustDirection, boolean parachute) {
    /** Sem empuxo e sem paraquedas. */
    public static final Control IDLE = new Control(0.0, V3.ZERO, false);

    public Control {
        throttle = Double.isNaN(throttle) ? 0.0 : Math.max(0.0, Math.min(1.0, throttle));
        thrustDirection = thrustDirection == null ? V3.ZERO : thrustDirection.unit();
    }

    /** Empuxo {@code throttle} ao longo de {@code direction}, sem pedir o paraquedas. */
    public static Control thrust(double throttle, V3 direction) {
        return new Control(throttle, direction, false);
    }
}
