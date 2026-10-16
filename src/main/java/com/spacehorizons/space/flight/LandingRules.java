package com.spacehorizons.space.flight;

/**
 * Regras do contato com o chão: quando a base do foguete encosta, o pouso é suave se a velocidade vertical
 * ({@value FlightConstants#LANDING_MAX_VERTICAL} blocos/tick = 20 blocos/s), a velocidade horizontal em relação ao chão
 * ({@value FlightConstants#LANDING_MAX_HORIZONTAL}) e a inclinação em relação à vertical
 * ({@value FlightConstants#LANDING_MAX_TILT_DEG}°) estiverem dentro dos limites; senão é uma batida.
 */
public final class LandingRules {
    /** Resultado do contato. */
    public enum Outcome {
        /** Pouso suave: o foguete fica de pé. */
        SOFT_LANDING,
        /** Batida: o foguete se desfaz. */
        CRASH
    }

    /** Dano de batida: o equivalente à queda de {@code v²/(2·G0)} blocos, menos os 3 que o vanilla perdoa. */
    private static final double SAFE_FALL_BLOCKS = 3.0;

    private LandingRules() {
    }

    /**
     * Desfecho do contato. {@code vVertical} é a velocidade vertical (o sinal não importa; só o módulo), {@code
     * vHorizontal} a velocidade horizontal em relação ao chão e {@code tiltDeg} a inclinação do nariz em relação à
     * vertical do local, em graus.
     */
    public static Outcome contact(double vVertical, double vHorizontal, double tiltDeg) {
        boolean soft = Math.abs(vVertical) <= FlightConstants.LANDING_MAX_VERTICAL
                && Math.abs(vHorizontal) <= FlightConstants.LANDING_MAX_HORIZONTAL
                && Math.abs(tiltDeg) <= FlightConstants.LANDING_MAX_TILT_DEG;
        return soft ? Outcome.SOFT_LANDING : Outcome.CRASH;
    }

    /** Velocidade total de impacto a partir das componentes vertical e horizontal. */
    public static double impactSpeed(double vVertical, double vHorizontal) {
        return Math.hypot(vVertical, vHorizontal);
    }

    /**
     * Dano ao piloto numa batida (pontos de vida), proporcional à velocidade como uma queda: a altura equivalente de
     * queda livre é {@code v²/(2·G0)} e o vanilla perdoa 3 blocos. Zero para velocidades baixas.
     */
    public static double impactDamage(double speed) {
        double equivalentFall = speed * speed / (2.0 * FlightConstants.G0);
        return Math.max(0.0, equivalentFall - SAFE_FALL_BLOCKS);
    }
}
