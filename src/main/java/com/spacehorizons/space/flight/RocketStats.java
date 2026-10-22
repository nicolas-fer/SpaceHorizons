package com.spacehorizons.space.flight;

import java.util.List;

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
 *   <li>contagens de motores, tanques, paraquedas e pernas (informativas e para as regras de voo);</li>
 *   <li>{@code groups}: os motores por tipo ({@link EngineGroup}: cada tipo queima o seu {@link Propellant}) e {@code capacity}, a
 *       capacidade de cada combustível (Tier 2, docs/PLANO_TIERS.md). {@code thrust}, {@code ispTicks} e {@code fuelCapacityMb} são os
 *       totais deles (empuxo somado, Isp pela vazão, mB com massa).</li>
 * </ul>
 *
 * <p>Os métodos com {@code double fuelMb} são os do foguete químico (só {@link Propellant#CHEMICAL}, o Tier 1, inalterados); os com
 * {@link FuelLoad} valem para qualquer foguete, com motores e combustíveis misturados.
 */
public record RocketStats(double dryMassT, double thrust, double ispTicks, double fuelCapacityMb, double dragK0,
        double parachuteK0, int engines, int tanks, int parachutes, int legs, List<EngineGroup> groups, FuelLoad capacity) {

    /** Foguete químico (Tier 1): um grupo de motores químicos com este empuxo e Isp, e esta capacidade de combustível químico. */
    public RocketStats(double dryMassT, double thrust, double ispTicks, double fuelCapacityMb, double dragK0, double parachuteK0, int engines,
            int tanks, int parachutes, int legs) {
        this(dryMassT, thrust, ispTicks, fuelCapacityMb, dragK0, parachuteK0, engines, tanks, parachutes, legs,
                thrust > 0.0 ? List.of(new EngineGroup("chemical", Propellant.CHEMICAL, false, thrust, ispTicks, engines)) : List.of(),
                FuelLoad.chemical(fuelCapacityMb));
    }

    /**
     * Foguete com motores por tipo ({@code groups}) e capacidade por combustível: o empuxo total, o Isp do conjunto (média harmônica pelo
     * empuxo) e a capacidade em mB (só os combustíveis com massa) saem deles. Sem empuxo, o Isp fica o do motor químico básico.
     */
    public static RocketStats of(double dryMassT, List<EngineGroup> groups, FuelLoad capacity, double dragK0, double parachuteK0, int engines,
            int tanks, int parachutes, int legs) {
        double thrust = 0.0;
        double flow = 0.0;
        for (EngineGroup g : groups) {
            if (g.thrust() > 0.0) {
                thrust += g.thrust();
                flow += g.thrust() / g.ispTicks();
            }
        }
        double isp = flow > 0.0 ? thrust / flow : FlightConstants.ISP_TICKS;
        return new RocketStats(dryMassT, thrust, isp, capacity.propellantMb(), dragK0, parachuteK0, engines, tanks, parachutes, legs,
                List.copyOf(groups), capacity);
    }

    public RocketStats {
        groups = List.copyOf(groups);
        capacity = capacity == null ? FuelLoad.EMPTY : capacity;
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

    // ------------------------------------------------------------------ combustíveis misturados (Tier 2)

    /** Só motores químicos (o foguete do Tier 1): os métodos de {@code double fuelMb} valem por inteiro. */
    public boolean chemicalOnly() {
        for (EngineGroup g : this.groups) {
            if (g.propellant() != Propellant.CHEMICAL || g.needsCore()) {
                return false;
            }
        }
        return this.capacity.onlyChemical();
    }

    /** Os tanques cheios (e os motores carregados): a carga do projeto. */
    public FuelLoad fullLoad() {
        return this.capacity;
    }

    /** Massa total (t) com a carga {@code fuel}. */
    public double wetMass(FuelLoad fuel) {
        return this.dryMassT + fuel.massT();
    }

    /** Empuxo a 100% dos grupos que ainda têm combustível (t·blocos/tick²). */
    public double activeThrust(FuelLoad fuel) {
        double thrust = 0.0;
        for (EngineGroup g : this.groups) {
            if (g.active(fuel)) {
                thrust += g.thrust();
            }
        }
        return thrust;
    }

    /** Isp efetivo dos grupos que ainda têm combustível ({@code ΣF / Σ(F/Isp)}); o Isp total se nenhum tem. */
    public double activeIsp(FuelLoad fuel) {
        double thrust = 0.0;
        double flow = 0.0;
        for (EngineGroup g : this.groups) {
            if (g.active(fuel)) {
                thrust += g.thrust();
                flow += g.thrust() / g.ispTicks();
            }
        }
        return flow > 0.0 ? thrust / flow : this.ispTicks;
    }

    /** Aceleração (blocos/tick²) a {@code throttle} (0..1) com a carga {@code fuel}: só empurram os grupos com combustível. */
    public double acceleration(FuelLoad fuel, double throttle) {
        return activeThrust(fuel) * Math.max(0.0, Math.min(1.0, throttle)) / wetMass(fuel);
    }

    /** Relação empuxo/peso a 100% com a carga {@code fuel}, numa gravidade local {@code g}. */
    public double twr(FuelLoad fuel, double g) {
        if (!(g > 0.0)) {
            return Double.POSITIVE_INFINITY;
        }
        return activeThrust(fuel) / (wetMass(fuel) * g);
    }

    /**
     * Δv restante (blocos/tick) com a carga {@code fuel}, todos os motores a 100% ao mesmo tempo, cada um queimando o seu combustível. Em
     * FASES: enquanto todos os grupos têm combustível, a vazão soma e o Isp é a média harmônica pelo empuxo; quando um combustível acaba
     * (ou o núcleo do nuclear), o grupo dele para e os outros seguem, com a massa que sobrou (o combustível que nenhum motor queima é peso
     * morto). Com um só grupo é Tsiolkovsky de toda a carga dele.
     */
    public double deltaV(FuelLoad fuel) {
        int n = Propellant.count();
        double[] left = new double[n];
        for (Propellant p : Propellant.values()) {
            left[p.ordinal()] = fuel.get(p);
        }
        double mass = this.dryMassT + fuel.massT();
        double dv = 0.0;
        for (int phase = 0; phase <= this.groups.size() + 1; phase++) {
            double thrust = 0.0;
            double flowT = 0.0;
            double[] rateMb = new double[n];
            for (EngineGroup g : this.groups) {
                if (!activeWith(g, left)) {
                    continue;
                }
                double f = g.fullFlowT();
                thrust += g.thrust();
                flowT += f;
                double mb = f / g.propellant().tonsPerMb();
                rateMb[g.propellant().ordinal()] += mb;
                if (g.needsCore()) {
                    rateMb[Propellant.NUCLEAR_CORE.ordinal()] += mb;
                }
            }
            if (flowT <= 0.0) {
                break;
            }
            // a fase dura até o primeiro combustível em uso acabar
            double ticks = Double.POSITIVE_INFINITY;
            int ends = -1;
            for (int i = 0; i < n; i++) {
                if (rateMb[i] > 0.0 && left[i] / rateMb[i] < ticks) {
                    ticks = left[i] / rateMb[i];
                    ends = i;
                }
            }
            double burned = flowT * ticks;
            double m1 = Math.max(this.dryMassT, mass - burned);
            dv += (thrust / flowT) * Math.log(mass / m1);
            mass = m1;
            for (int i = 0; i < n; i++) {
                left[i] = Math.max(0.0, left[i] - rateMb[i] * ticks);
            }
            left[ends] = 0.0;
        }
        return dv;
    }

    /** Tempo (ticks) de queima a 100% até o primeiro combustível em uso acabar, com a carga dada; infinito sem empuxo. */
    public double firstBurnoutTicks(FuelLoad fuel) {
        double[] rateMb = new double[Propellant.count()];
        for (EngineGroup g : this.groups) {
            if (!g.active(fuel)) {
                continue;
            }
            double mb = g.fullFlowT() / g.propellant().tonsPerMb();
            rateMb[g.propellant().ordinal()] += mb;
            if (g.needsCore()) {
                rateMb[Propellant.NUCLEAR_CORE.ordinal()] += mb;
            }
        }
        double ticks = Double.POSITIVE_INFINITY;
        for (Propellant p : Propellant.values()) {
            if (rateMb[p.ordinal()] > 0.0) {
                ticks = Math.min(ticks, fuel.get(p) / rateMb[p.ordinal()]);
            }
        }
        return ticks;
    }

    private static boolean activeWith(EngineGroup g, double[] left) {
        if (g.thrust() <= 0.0 || left[g.propellant().ordinal()] <= 0.0) {
            return false;
        }
        return !g.needsCore() || left[Propellant.NUCLEAR_CORE.ordinal()] > 0.0;
    }
}
