package com.spacehorizons.space.flight;

/**
 * Equação do foguete: {@code Δv = ve · ln(m0/m1)}. Massas em toneladas, velocidade de exaustão {@code ve} em
 * blocos/tick (ve = Isp · G0), vazão em t/tick.
 */
public final class Tsiolkovsky {
    private Tsiolkovsky() {
    }

    /** Δv ganho ao ir da massa {@code m0} (inicial) à {@code m1} (final). */
    public static double deltaV(double ve, double m0, double m1) {
        return ve * Math.log(m0 / m1);
    }

    /** Massa final depois de gastar um Δv: {@code m0 · e^(−Δv/ve)}. */
    public static double finalMass(double m0, double ve, double dv) {
        return m0 * Math.exp(-dv / ve);
    }

    /** Propelente (t) gasto para um Δv: {@code m0 · (1 − e^(−Δv/ve))}. */
    public static double propellantFor(double m0, double ve, double dv) {
        return m0 - finalMass(m0, ve, dv);
    }

    /** Duração (ticks) de uma queima de Δv com vazão constante {@code flow} (t/tick): {@code (m0/ṁ)(1 − e^(−Δv/ve))}. */
    public static double burnTime(double m0, double flow, double ve, double dv) {
        return m0 / flow * (1.0 - Math.exp(-dv / ve));
    }
}
