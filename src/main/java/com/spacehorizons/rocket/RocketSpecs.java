package com.spacehorizons.rocket;

import com.spacehorizons.space.flight.RocketStats;

/**
 * Números de projeto das peças do foguete (docs/DESIGN_FOGUETE.md, seção 1 e "Multiblocos") e o cálculo de {@link RocketStats} a
 * partir do censo ({@link RocketCensus}: blocos e estruturas). Lógica pura, testável.
 *
 * <p>Estruturas ({@link Multiblocks}): um motor formado empurra mais e queima melhor por tonelada que os mesmos blocos avulsos, e um
 * tanque formado carrega a mesma carga com menos casco por bloco. Quanto maior, melhor por massa.
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
    /** Capacidade de um tanque, em mB (4 galões); a de uma coluna formada é este valor vezes o volume. */
    public static final int TANK_CAPACITY_MB = 4000;

    /**
     * Motores formados. Cada cubo tem 8 (2x2x2) ou 27 (3x3x3) blocos; os números abaixo são o que os blocos avulsos dariam, melhorado:
     * empuxo x1,2 e x1,5, massa x0,8 e x0,7, Isp +100 e +200 ticks. Empuxo por tonelada: 4,17 (avulso), 6,25 (2x2x2) e 8,93 (3x3x3).
     */
    public static final double ENGINE_2_THRUST = 8 * ENGINE_THRUST * 1.2;
    public static final double ENGINE_2_MASS_T = 8 * ENGINE_MASS_T * 0.8;
    public static final double ENGINE_2_ISP_TICKS = 1700.0;
    public static final double ENGINE_3_THRUST = 27 * ENGINE_THRUST * 1.5;
    public static final double ENGINE_3_MASS_T = 27 * ENGINE_MASS_T * 0.7;
    public static final double ENGINE_3_ISP_TICKS = 1800.0;

    /**
     * Casco de um tanque formado: a massa seca por bloco do tanque avulso vezes um fator que cai com a razão casca/volume. Fator
     * {@code 0,4 + 0,6 * A / (6 V)}, com A as faces expostas da coluna e V o volume (um cubo isolado, A = 6 V, vale 1; uma coluna
     * 3x3x12 vale 0,55). Exemplos de massa seca: 1x1x4 = 0,68 t (avulsos: 0,8), 2x2x8 = 4,0 t (6,4), 3x3x10 = 9,96 t (18).
     */
    public static final double TANK_HULL_FLOOR = 0.4;

    /** Velocidade terminal (ρ = 1) em subida e com o paraquedas aberto, em blocos/tick; k0 = G0 / vt². */
    public static final double TERMINAL_SPEED_ASCENT = 16.0;
    public static final double TERMINAL_SPEED_PARACHUTE = 0.5;
    /** Gravidade de referência, em blocos/tick² (mesmo valor de FlightConstants.G0 da física pura). */
    private static final double G0 = 0.08;

    private RocketSpecs() {
    }

    /** Massa seca de um bloco do papel dado, em toneladas (zero para o que não vai no foguete); avulso, sem estrutura. */
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

    /** Massa seca de uma coluna de tanques formada, em toneladas. */
    public static double tankStructureMassT(Multiblocks.Box box) {
        double factor = TANK_HULL_FLOOR + (1.0 - TANK_HULL_FLOOR) * box.surface() / (6.0 * box.volume());
        return TANK_DRY_MASS_T * box.volume() * factor;
    }

    /** Capacidade de combustível de uma coluna de tanques formada, em mB. */
    public static int tankStructureCapacityMb(Multiblocks.Box box) {
        return TANK_CAPACITY_MB * box.volume();
    }

    /** Empuxo de um motor formado de lado 2 ou 3 (t·blocos/tick²). */
    public static double engineStructureThrust(int side) {
        return side == Multiblocks.ENGINE_LARGE ? ENGINE_3_THRUST : ENGINE_2_THRUST;
    }

    public static double engineStructureMassT(int side) {
        return side == Multiblocks.ENGINE_LARGE ? ENGINE_3_MASS_T : ENGINE_2_MASS_T;
    }

    public static double engineStructureIsp(int side) {
        return side == Multiblocks.ENGINE_LARGE ? ENGINE_3_ISP_TICKS : ENGINE_2_ISP_TICKS;
    }

    /** Empuxo total do censo: avulsos mais estruturas. */
    public static double thrustOf(RocketCensus c) {
        double thrust = c.soloEngines() * ENGINE_THRUST;
        for (Multiblocks.Box box : c.engineStructures()) {
            thrust += engineStructureThrust(box.side());
        }
        return thrust;
    }

    /**
     * Isp efetivo: os motores queimam juntos, então a vazão de massa soma ({@code F/ve}) e o Isp do conjunto é {@code ΣF / Σ(F/Isp)}
     * (a média harmônica ponderada pelo empuxo). Com motores iguais dá o Isp deles.
     */
    public static double ispOf(RocketCensus c) {
        double thrust = 0.0;
        double flow = 0.0;
        double solo = c.soloEngines() * ENGINE_THRUST;
        thrust += solo;
        flow += solo / ENGINE_ISP_TICKS;
        for (Multiblocks.Box box : c.engineStructures()) {
            double f = engineStructureThrust(box.side());
            thrust += f;
            flow += f / engineStructureIsp(box.side());
        }
        return flow > 0.0 ? thrust / flow : ENGINE_ISP_TICKS;
    }

    /** Massa seca total do censo, em toneladas (com a de estruturas); o mínimo é a de um bloco de estrutura. */
    public static double dryMassOf(RocketCensus c) {
        double dry = c.cockpits() * COCKPIT_MASS_T + c.soloEngines() * ENGINE_MASS_T + c.looseTanks() * TANK_DRY_MASS_T
                + c.parachutes() * PARACHUTE_MASS_T + c.legs() * LEG_MASS_T + (c.hulls() + c.structures()) * STRUCTURE_MASS_T;
        for (Multiblocks.Box box : c.engineStructures()) {
            dry += engineStructureMassT(box.side());
        }
        for (Multiblocks.Box box : c.tankStructures()) {
            dry += tankStructureMassT(box);
        }
        // a entidade nasce vazia (antes de receber o retrato) e RocketStats exige massa seca positiva
        return Math.max(dry, STRUCTURE_MASS_T);
    }

    /** Estatísticas de um foguete com este censo (blocos e estruturas). */
    public static RocketStats statsFor(RocketCensus c) {
        double dragK0 = G0 / (TERMINAL_SPEED_ASCENT * TERMINAL_SPEED_ASCENT);
        double parachuteK0 = c.parachutes() > 0 ? G0 / (TERMINAL_SPEED_PARACHUTE * TERMINAL_SPEED_PARACHUTE) : 0.0;
        return new RocketStats(dryMassOf(c), thrustOf(c), ispOf(c), (double) c.tanks() * TANK_CAPACITY_MB, dragK0, parachuteK0, c.engines(),
                c.tanks(), c.parachutes(), c.legs());
    }
}
