package com.spacehorizons.ship;

import com.spacehorizons.space.flight.RocketStats;
import com.spacehorizons.space.flight.V3;

/**
 * Números de projeto das peças da nave (docs/PLANO_NAVES.md, seção 6) e o cálculo de {@link RocketStats} a partir da contagem de
 * blocos ({@link ShipCensus}); o espelho de {@code RocketSpecs} para as naves. Lógica pura, testável.
 *
 * <p>A nave é muito maior que o foguete (milhares de blocos), por isso a estrutura pesa pouco por bloco (0,02 t contra 0,1 t) e o motor
 * de impulso empurra mais e gasta mais ({@link #ENGINE_THRUST} e {@link #ENGINE_ISP_TICKS}: empuxo maior e Isp menor, como diz o plano).
 * O combustível é o dos tanques do foguete ({@code rocket_fuel_tank}, 4.000 mB cada), dentro da nave.
 */
public final class ShipSpecs {
    /** Massa seca de cada papel, em toneladas. */
    public static final double BRIDGE_MASS_T = 2.0;
    public static final double ENGINE_MASS_T = 1.5;
    public static final double TANK_DRY_MASS_T = 0.3;
    public static final double WINDOW_MASS_T = 0.02;
    public static final double SEAT_MASS_T = 0.05;
    public static final double AIRLOCK_MASS_T = 0.1;
    /** Casco e qualquer outro bloco da nave. */
    public static final double STRUCTURE_MASS_T = 0.02;

    /** Empuxo de um motor de impulso, em t·blocos/tick². */
    public static final double ENGINE_THRUST = 8.0;
    /** Isp do motor de impulso, em ticks (velocidade de exaustão 80 blocos/tick, contra 128 do foguete). */
    public static final double ENGINE_ISP_TICKS = 1000.0;
    /** Capacidade de um tanque, em mB (o mesmo tanque do foguete). */
    public static final int TANK_CAPACITY_MB = 4000;
    /** Velocidade terminal (ρ = 1) da nave, em blocos/tick, que define o arrasto; k0 = G0 / vt². */
    public static final double TERMINAL_SPEED = 24.0;
    /** Gravidade de referência, em blocos/tick² (mesmo valor de FlightConstants.G0). */
    private static final double G0 = 0.08;

    private ShipSpecs() {
    }

    /** Massa seca de um bloco do papel dado, em toneladas (zero para o ar). */
    public static double massOf(ShipBlockKind kind) {
        return switch (kind) {
            case BRIDGE -> BRIDGE_MASS_T;
            case ENGINE -> ENGINE_MASS_T;
            case FUEL_TANK -> TANK_DRY_MASS_T;
            case WINDOW -> WINDOW_MASS_T;
            case SEAT -> SEAT_MASS_T;
            case AIRLOCK -> AIRLOCK_MASS_T;
            case STRUCTURE -> STRUCTURE_MASS_T;
            case AIR -> 0.0;
        };
    }

    /** Massa seca total de uma nave com esta contagem, em toneladas. */
    public static double dryMassT(ShipCensus c) {
        return c.bridges() * BRIDGE_MASS_T + c.engines() * ENGINE_MASS_T + c.tanks() * TANK_DRY_MASS_T + c.windows() * WINDOW_MASS_T
                + c.seats() * SEAT_MASS_T + c.airlocks() * AIRLOCK_MASS_T + c.structures() * STRUCTURE_MASS_T;
    }

    /** Abaixo disto o empuxo líquido dos motores conta como zero (motores que se anulam). */
    private static final double MIN_NET_THRUST = 1.0e-9;

    /** Estatísticas de uma nave com esta contagem de blocos, com todos os motores empurrando pela proa (a nave sem blocos tem a massa mínima de um bloco de estrutura). */
    public static RocketStats statsFor(ShipCensus c) {
        return statsFor(c, ShipArrangement.DEFAULT);
    }

    /**
     * Estatísticas de uma nave com esta contagem e esta disposição ({@link ShipArrangement}). O empuxo é o MÓDULO da soma vetorial dos motores
     * (cada um empurra para o lado oposto ao bocal): {@code F = |Σ T·dᵢ|}. Motores que se anulam gastam combustível sem empurrar: o integrador
     * do foguete tem um empuxo e um Isp só, então o Isp efetivo é {@code Isp·F/(n·T)}, o que mantém a vazão de massa {@code n·T/ve} dos
     * {@code n} motores (nave com todos os motores no mesmo sentido: {@code F = n·T} e o Isp de projeto, como antes). Sem empuxo líquido
     * (motores que se anulam exatamente) o empuxo é 0 e nada é queimado.
     */
    public static RocketStats statsFor(ShipCensus c, ShipArrangement arrangement) {
        double dry = Math.max(dryMassT(c), STRUCTURE_MASS_T);
        double dragK0 = G0 / (TERMINAL_SPEED * TERMINAL_SPEED);
        int engines = c.engines();
        double net = engines > 0 ? arrangement.netThrust(engines).length() * ENGINE_THRUST : 0.0;
        double total = Math.max(engines, arrangement.countedEngines()) * ENGINE_THRUST;
        if (net < MIN_NET_THRUST) {
            net = 0.0;
        }
        double isp = total > 0.0 ? ENGINE_ISP_TICKS * net / total : ENGINE_ISP_TICKS;
        return new RocketStats(dry, net, isp, (double) c.tanks() * TANK_CAPACITY_MB, dragK0, 0.0, engines, c.tanks(), 0, 0);
    }

    /** O eixo do empuxo líquido nos eixos canônicos da nave (unitário); a proa canônica ({@code -Z}) quando não há empuxo líquido. */
    public static V3 thrustAxis(ShipCensus c, ShipArrangement arrangement) {
        V3 net = c.engines() > 0 ? arrangement.netThrust(c.engines()) : V3.ZERO;
        return net.length() * ENGINE_THRUST < MIN_NET_THRUST ? BOW_AXIS : net.unit();
    }

    /** A proa da nave canônica nos eixos do corpo (x direita, y cima, z trás): {@code -Z}. */
    public static final V3 BOW_AXIS = new V3(0.0, 0.0, -1.0);

    /** Eficiência dos motores, de 0 a 1: o empuxo líquido sobre a soma dos empuxos (1 com todos no mesmo sentido, 0 se se anulam). */
    public static double engineEfficiency(ShipCensus c, ShipArrangement arrangement) {
        int total = Math.max(c.engines(), arrangement.countedEngines());
        return total > 0 ? Math.min(1.0, arrangement.netThrust(c.engines()).length() / total) : 0.0;
    }
}
