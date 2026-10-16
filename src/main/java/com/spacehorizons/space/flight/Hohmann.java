package com.spacehorizons.space.flight;

/**
 * Transferência de Hohmann entre duas órbitas circulares coplanares: a referência analítica da transferência até a Lua
 * (o alvo é o raio da órbita lunar) e do Δv de TLI do planejador. Todas as velocidades em blocos/tick, tempos em ticks.
 */
public final class Hohmann {
    private Hohmann() {
    }

    /** Semieixo maior da elipse de transferência. */
    public static double transferSemiMajor(double r1, double r2) {
        return 0.5 * (r1 + r2);
    }

    /** Módulo do primeiro impulso (saída da órbita de raio {@code r1}). */
    public static double dv1(double mu, double r1, double r2) {
        double at = transferSemiMajor(r1, r2);
        return Math.abs(Math.sqrt(mu * (2.0 / r1 - 1.0 / at)) - Math.sqrt(mu / r1));
    }

    /** Módulo do segundo impulso (circularização em {@code r2}). */
    public static double dv2(double mu, double r1, double r2) {
        double at = transferSemiMajor(r1, r2);
        return Math.abs(Math.sqrt(mu / r2) - Math.sqrt(mu * (2.0 / r2 - 1.0 / at)));
    }

    /** Tempo de voo: meio período da elipse de transferência. */
    public static double flightTime(double mu, double r1, double r2) {
        double at = transferSemiMajor(r1, r2);
        return Math.PI * Math.sqrt(at * at * at / mu);
    }

    /** Ângulo de fase (rad) que o alvo deve ter à frente na hora da queima: {@code π − ω · t}. */
    public static double phaseAngle(double flightTime, double targetAngularRate) {
        return Math.PI - targetAngularRate * flightTime;
    }
}
