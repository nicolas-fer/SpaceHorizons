package com.spacehorizons.rocket;

import com.spacehorizons.space.flight.RocketStats;

/**
 * Números de projeto das peças do foguete (docs/DESIGN_FOGUETE.md, seção 1) e o cálculo de {@link RocketStats} a partir
 * da contagem de blocos. Lógica pura, testável.
 */
public final class RocketSpecs {
    /** Massa seca de cada papel, em toneladas. */
    public static final double COCKPIT_MASS_T = 0.8;
    public static final double ENGINE_MASS_T = 0.6;
    public static final double TANK_DRY_MASS_T = 0.2;
    public static final double PARACHUTE_MASS_T = 0.1;
    public static final double LEG_MASS_T = 0.1;
    /** Casco e qualquer outro bloco de estrutura. */
    public static final double STRUCTURE_MASS_T = 0.1;

    /** Empuxo de um motor básico, em t·blocos/tick². */
    public static final double ENGINE_THRUST = 2.5;
    /** Isp do motor básico, em ticks (velocidade de exaustão 40 blocos/tick). */
    public static final double ENGINE_ISP_TICKS = 1600.0;
    /** Capacidade de um tanque, em mB (4 galões). */
    public static final int TANK_CAPACITY_MB = 4000;

    /** Velocidade terminal (ρ = 1) em subida e com o paraquedas aberto, em blocos/tick; k0 = G0 / vt². */
    public static final double TERMINAL_SPEED_ASCENT = 16.0;
    public static final double TERMINAL_SPEED_PARACHUTE = 0.5;
    /** Gravidade de referência, em blocos/tick² (mesmo valor de FlightConstants.G0 da física pura). */
    private static final double G0 = 0.08;

    private RocketSpecs() {
    }

    /** Massa seca de um bloco do papel dado, em toneladas (zero para o que não vai no foguete). */
    public static double massOf(RocketBlockKind kind) {
        return switch (kind) {
            case COCKPIT -> COCKPIT_MASS_T;
            case ENGINE -> ENGINE_MASS_T;
            case FUEL_TANK -> TANK_DRY_MASS_T;
            case PARACHUTE -> PARACHUTE_MASS_T;
            case LEG -> LEG_MASS_T;
            case HULL, STRUCTURE -> STRUCTURE_MASS_T;
            default -> 0.0;
        };
    }

    /** Estatísticas de um foguete com esta contagem de blocos. */
    public static RocketStats statsFor(RocketCensus c) {
        double dry = c.cockpits() * COCKPIT_MASS_T + c.engines() * ENGINE_MASS_T + c.tanks() * TANK_DRY_MASS_T
                + c.parachutes() * PARACHUTE_MASS_T + c.legs() * LEG_MASS_T + (c.hulls() + c.structures()) * STRUCTURE_MASS_T;
        // a entidade nasce vazia (antes de receber o retrato) e RocketStats exige massa seca positiva
        dry = Math.max(dry, STRUCTURE_MASS_T);
        double dragK0 = G0 / (TERMINAL_SPEED_ASCENT * TERMINAL_SPEED_ASCENT);
        double parachuteK0 = c.parachutes() > 0 ? G0 / (TERMINAL_SPEED_PARACHUTE * TERMINAL_SPEED_PARACHUTE) : 0.0;
        return new RocketStats(dry, c.engines() * ENGINE_THRUST, ENGINE_ISP_TICKS, (double) c.tanks() * TANK_CAPACITY_MB,
                dragK0, parachuteK0, c.engines(), c.tanks(), c.parachutes(), c.legs());
    }
}
