package com.spacehorizons.space.flight;

/**
 * Números do foguete para a física (calculados pelo foguete-entidade a partir do retrato dos blocos).
 *
 * <ul>
 *   <li>{@code dryMassT}: massa seca total (t);</li>
 *   <li>{@code thrust}: empuxo total a 100% (t·blocos/tick²);</li>
 *   <li>{@code ispTicks}: impulso específico (ticks); a velocidade de exaustão é {@code ve = Isp · G0};</li>
 *   <li>{@code fuelCapacityMb}: capacidade total dos tanques (mB);</li>
 *   <li>{@code dragK0}: coeficiente de arrasto do foguete ({@code k0 = G0/vt²}, vt = velocidade terminal);</li>
 *   <li>{@code parachuteK0}: idem com o paraquedas totalmente aberto (0 se não houver paraquedas);</li>
 *   <li>contagens de motores, tanques, paraquedas e pernas (informativas e para as regras de voo).</li>
 * </ul>
 */
public record RocketStats(double dryMassT, double thrust, double ispTicks, double fuelCapacityMb, double dragK0,
        double parachuteK0, int engines, int tanks, int parachutes, int legs) {

    public RocketStats {
        if (!(dryMassT > 0.0)) {
            throw new IllegalArgumentException("massa seca deve ser positiva: " + dryMassT);
        }
        if (thrust < 0.0 || fuelCapacityMb < 0.0 || dragK0 < 0.0 || parachuteK0 < 0.0) {
            throw new IllegalArgumentException("empuxo, combustível e arrasto não podem ser negativos");
        }
        if (thrust > 0.0 && !(ispTicks > 0.0)) {
            throw new IllegalArgumentException("Isp deve ser positivo quando há empuxo: " + ispTicks);
        }
    }

    /**
     * Foguete a partir das contagens de blocos, com as massas e os números do desenho. {@code otherBlocks} são os blocos
     * que não são motor, tanque, paraquedas, perna nem cockpit (casco e estrutura). {@code frontalArea} é a área (blocos²)
     * da seção projetada ao longo do eixo do nariz.
     *
     * <p>O arrasto é {@code k0 = κ · área / massa cheia}, limitado a velocidades terminais de 6 a 40 blocos/tick; um
     * foguete de 9 blocos² e 22 t tem vt = 16. O paraquedas (se houver ao menos um) leva a velocidade terminal a 0,5.
     */
    public static RocketStats of(int engines, int tanks, int parachutes, int legs, int cockpits, int otherBlocks,
            double frontalArea) {
        double dry = cockpits * FlightConstants.COCKPIT_MASS_T + engines * FlightConstants.ENGINE_MASS_T
                + tanks * FlightConstants.TANK_DRY_MASS_T + parachutes * FlightConstants.PARACHUTE_MASS_T
                + legs * FlightConstants.LEG_MASS_T + otherBlocks * FlightConstants.BLOCK_MASS_T;
        double capacity = tanks * FlightConstants.TANK_CAPACITY_MB;
        double fullMass = dry + capacity * FlightConstants.FUEL_T_PER_MB;
        double area = Math.max(1.0, frontalArea);
        double minK0 = FlightConstants.G0 / (FlightConstants.MAX_TERMINAL_SPEED * FlightConstants.MAX_TERMINAL_SPEED);
        double maxK0 = FlightConstants.G0 / (FlightConstants.MIN_TERMINAL_SPEED * FlightConstants.MIN_TERMINAL_SPEED);
        double k0 = Math.max(minK0, Math.min(maxK0, FlightConstants.DRAG_AREA_FACTOR * area / fullMass));
        double chuteK0 = parachutes > 0
                ? FlightConstants.G0 / (FlightConstants.PARACHUTE_TERMINAL_SPEED * FlightConstants.PARACHUTE_TERMINAL_SPEED)
                : 0.0;
        return new RocketStats(dry, engines * FlightConstants.ENGINE_THRUST, FlightConstants.ISP_TICKS, capacity, k0, chuteK0,
                engines, tanks, parachutes, legs);
    }

    /** Velocidade de exaustão (blocos/tick): {@code Isp · G0}. */
    public double exhaustVelocity() {
        return ispTicks * FlightConstants.G0;
    }

    /** Massa do combustível (t) para {@code fuelMb} mB. */
    public double fuelMassT(double fuelMb) {
        return Math.max(0.0, fuelMb) * FlightConstants.FUEL_T_PER_MB;
    }

    /** Massa total (t) com {@code fuelMb} mB nos tanques. */
    public double wetMass(double fuelMb) {
        return dryMassT + fuelMassT(fuelMb);
    }

    /** Δv restante (blocos/tick) com {@code fuelMb} mB: Tsiolkovsky de toda a carga. 0 sem empuxo. */
    public double deltaV(double fuelMb) {
        if (thrust <= 0.0 || fuelMb <= 0.0) {
            return 0.0;
        }
        return Tsiolkovsky.deltaV(exhaustVelocity(), wetMass(fuelMb), dryMassT);
    }

    /** Relação empuxo/peso a 100% com {@code fuelMb} mB, numa gravidade local {@code g} (blocos/tick²). */
    public double twr(double fuelMb, double g) {
        if (!(g > 0.0)) {
            return Double.POSITIVE_INFINITY;
        }
        return thrust / (wetMass(fuelMb) * g);
    }

    /** Vazão de massa a 100% (t/tick): {@code F / ve}. */
    public double fullThrottleFlow() {
        return thrust > 0.0 ? thrust / exhaustVelocity() : 0.0;
    }

    /** Vazão de combustível a 100% (mB/tick). */
    public double fullThrottleFuelMbPerTick() {
        return fullThrottleFlow() / FlightConstants.FUEL_T_PER_MB;
    }

    /** Aceleração (blocos/tick²) a {@code throttle} (0..1) com {@code fuelMb} mB: {@code F · throttle / m}. */
    public double acceleration(double fuelMb, double throttle) {
        return thrust * Math.max(0.0, Math.min(1.0, throttle)) / wetMass(fuelMb);
    }

    /** Duração (ticks) de uma queima de {@code dv} a 100% a partir de {@code fuelMb} mB; infinita sem empuxo. */
    public double burnTime(double fuelMb, double dv) {
        if (thrust <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return Tsiolkovsky.burnTime(wetMass(fuelMb), fullThrottleFlow(), exhaustVelocity(), dv);
    }
}
