package com.spacehorizons.space.flight;

import com.spacehorizons.planet.atmosphere.AtmosphereModel;

/**
 * Um passo (1 tick) da dinâmica de voo no referencial do corpo dominante (eixos inerciais de I, o referencial inercial: ver {@code InertialFrame}).
 *
 * <ol>
 *   <li><b>Empuxo</b> com Tsiolkovsky EXATO no tick: a vazão é {@code F·acelerador/ve} (limitada ao combustível que
 *       resta, então o último tick de uma queima é parcial) e o Δv do tick é {@code ve·ln(m0/m1)}, somado como aceleração
 *       constante nos dois meios chutes. O combustível é consumido na mesma conta.</li>
 *   <li><b>Gravidade</b> do corpo por velocity-Verlet (kick-drift-kick): erro de fase de ~1 bloco em dezenas de
 *       órbitas e energia conservada a 1e-13.</li>
 *   <li><b>Arrasto</b> implícito e separado do passo: {@code v ← v/(1 + k0·ρ·|v|)}, com {@code ρ} de
 *       {@link AtmosphereModel#density}. É estável e nunca troca o sinal da velocidade, mesmo com o paraquedas aberto.</li>
 *   <li><b>Paraquedas</b>: o pedido só começa a abrir com atmosfera e abaixo de
 *       {@link FlightConstants#PARACHUTE_OPEN_MAX_SPEED}; depois a rampa de 80 ticks termina sozinha.</li>
 *   <li><b>Aquecimento</b> como índice visual: {@code Q = √ρ·(|v|/28,9)³}, {@code H += A·Q − B·H}.</li>
 * </ol>
 *
 * Contato com o chão, troca de corpo e zonas NÃO são daqui: o chamador testa o chão depois do passo
 * ({@link LandingRules}), troca de corpo com {@link SoiTracker} e de dimensão com {@link Zones}. Não há vento nem
 * sustentação; a velocidade relativa ao ar é a inercial menos {@code ω × r}: a atmosfera gira com a Terra, então o foguete parado na pista
 * (que tem ~27 blocos/tick em I no equador) não sente arrasto, e o aquecimento usa a mesma velocidade relativa.
 */
public final class FlightIntegrator {
    private FlightIntegrator() {
    }

    /**
     * Avança o estado em 1 tick. {@code clock} é o relógio no INÍCIO do tick (só entra na velocidade do ar, que gira com o corpo; a integração em si independe dele). Se {@code c} pede empuxo sem combustível, motor ou direção, ele é
     * ignorado.
     */
    public static FlightState step(FlightState s, Control c, RocketStats st, double clock) {
        return step(s, c, st, clock, 1.0);
    }

    /**
     * O mesmo passo com a taxa do relógio ({@code clockRate}, relógio por tick): o ar gira com o corpo na mesma taxa que o chão ({@link
     * FrameBridge#rotationVelocity}). Com o relógio pausado (taxa 0) o chão não gira e o ar também não; com a taxa fixa em 1, o arrasto
     * empurrava o veículo para o lado com o chão parado.
     */
    public static FlightState step(FlightState s, Control c, RocketStats st, double clock, double clockRate) {
        BodyPhysics bp = BodyPhysics.of(s.body());
        double mu = bp.mu();
        V3 r = s.r();
        V3 v = s.v();
        FuelLoad load = s.fuel();

        // 1. empuxo: Tsiolkovsky exato no tick
        V3 thrustAccel = V3.ZERO;
        double throttle = c.throttle();
        if (st.chemicalOnly() && load.onlyChemical()) {
            // o foguete químico do Tier 1: a conta de sempre, bit a bit
            double fuel = Math.max(0.0, s.fuelMb());
            if (throttle > 0.0 && fuel > 0.0 && st.engines() > 0 && st.thrust() > 0.0
                    && c.thrustDirection().lengthSquared() > 0.0) {
                double ve = st.exhaustVelocity();
                double m0 = st.wetMass(fuel);
                double wanted = st.thrust() * throttle / ve;
                double available = fuel * FlightConstants.FUEL_T_PER_MB;
                boolean emptied = wanted >= available;
                double flow = emptied ? available : wanted;
                double dv = Tsiolkovsky.deltaV(ve, m0, m0 - flow);
                thrustAccel = c.thrustDirection().times(dv);
                fuel = emptied ? 0.0 : fuel - flow / FlightConstants.FUEL_T_PER_MB;
            }
            load = load.with(Propellant.CHEMICAL, fuel);
        } else if (throttle > 0.0 && st.engines() > 0 && c.thrustDirection().lengthSquared() > 0.0) {
            Burn burn = burn(load, st, Math.min(1.0, throttle));
            thrustAccel = c.thrustDirection().times(burn.dv);
            load = burn.fuel;
        }

        // 2. kick-drift-kick com a gravidade do corpo dominante
        V3 kick = gravity(mu, r).plus(thrustAccel).times(0.5);
        v = v.plus(kick);
        r = r.plus(v);
        v = v.plus(gravity(mu, r).plus(thrustAccel).times(0.5));

        // 3. atmosfera: arrasto, paraquedas e calor
        AtmosphereModel atmosphere = bp.atmosphere();
        double rho = atmosphere.density(bp.altitude(r.length()));
        double heat = s.heat();
        double chute = s.chute();
        if (rho > 0.0) {
            V3 air = atmosphere.present() ? FrameBridge.rotationVelocity(s.body(), clock, clockRate, r) : V3.ZERO;
            V3 relative = v.minus(air);
            double speed = relative.length();
            if (chute <= 0.0 && c.parachute() && st.parachutes() > 0 && canDeployParachute(rho, speed)) {
                chute = 1.0 / FlightConstants.PARACHUTE_RAMP_TICKS;
            } else {
                chute = advanceChute(chute);
            }
            double k0 = st.dragK0() + chute * (st.parachuteK0() - st.dragK0());
            heat = stepHeat(heat, rho, speed);
            v = air.plus(applyDrag(relative, k0, rho));
        } else {
            heat = stepHeat(heat, 0.0, 0.0);
            chute = advanceChute(chute);
        }
        return new FlightState(s.body(), r, v, load, heat, chute);
    }

    /** O resultado da queima de um tick: o Δv (blocos/tick) e a carga que sobrou. */
    record Burn(double dv, FuelLoad fuel) {
    }

    /**
     * Um tick de queima com motores de tipos diferentes (Tier 2): cada grupo pede {@code F·acelerador/ve} toneladas do SEU combustível
     * (e o nuclear, a mesma quantidade em mB de núcleo); se um combustível não basta para o tick, os grupos que o usam queimam só o que há
     * (o último tick é parcial). O Δv do tick é Tsiolkovsky exato com a vazão somada e a velocidade de exaustão média pela vazão
     * ({@code Σ(ṁ·ve)/Σṁ}, que dá {@code ΣF/Σṁ} sem limite de combustível).
     */
    static Burn burn(FuelLoad fuel, RocketStats st, double throttle) {
        int n = Propellant.count();
        double[] wantedMb = new double[n];
        for (EngineGroup g : st.groups()) {
            if (!g.active(fuel)) {
                continue;
            }
            double mb = g.thrust() * throttle / g.exhaustVelocity() / g.propellant().tonsPerMb();
            wantedMb[g.propellant().ordinal()] += mb;
            if (g.needsCore()) {
                wantedMb[Propellant.NUCLEAR_CORE.ordinal()] += mb;
            }
        }
        // a fração do pedido que cada combustível atende neste tick
        double[] share = new double[n];
        for (Propellant p : Propellant.values()) {
            double want = wantedMb[p.ordinal()];
            share[p.ordinal()] = want > 0.0 ? Math.min(1.0, fuel.get(p) / want) : 1.0;
        }
        double flowT = 0.0;
        double impulse = 0.0;
        double[] usedMb = new double[n];
        for (EngineGroup g : st.groups()) {
            if (!g.active(fuel)) {
                continue;
            }
            double s = share[g.propellant().ordinal()];
            if (g.needsCore()) {
                s = Math.min(s, share[Propellant.NUCLEAR_CORE.ordinal()]);
            }
            double flow = g.thrust() * throttle / g.exhaustVelocity() * s;
            flowT += flow;
            impulse += flow * g.exhaustVelocity();
            double mb = flow / g.propellant().tonsPerMb();
            usedMb[g.propellant().ordinal()] += mb;
            if (g.needsCore()) {
                usedMb[Propellant.NUCLEAR_CORE.ordinal()] += mb;
            }
        }
        if (flowT <= 0.0) {
            return new Burn(0.0, fuel);
        }
        double m0 = st.wetMass(fuel);
        double dv = Tsiolkovsky.deltaV(impulse / flowT, m0, m0 - flowT);
        FuelLoad left = fuel;
        for (Propellant p : Propellant.values()) {
            if (usedMb[p.ordinal()] > 0.0) {
                // o combustível que se esgotou neste tick vai a zero exato (sem resto de ponto flutuante)
                double rest = fuel.get(p) - usedMb[p.ordinal()];
                left = left.with(p, rest <= 1.0e-9 * Math.max(1.0, fuel.get(p)) ? 0.0 : rest);
            }
        }
        return new Burn(dv, left);
    }

    /** Um tick da rampa do paraquedas: só anda se já começou a abrir, e fecha em 1,0 exato (sem resto de ponto flutuante). */
    private static double advanceChute(double chute) {
        if (chute <= 0.0 || chute >= 1.0) {
            return chute;
        }
        double next = chute + 1.0 / FlightConstants.PARACHUTE_RAMP_TICKS;
        return next > 1.0 - 1.0e-9 ? 1.0 : next;
    }

    /** Aceleração da gravidade {@code −mu·r/|r|³}; nula no centro (evita NaN). */
    static V3 gravity(double mu, V3 r) {
        double d2 = r.lengthSquared();
        if (d2 < 1.0e-12) {
            return V3.ZERO;
        }
        return r.times(-mu / (d2 * Math.sqrt(d2)));
    }

    /**
     * Arrasto implícito de 1 tick sobre a velocidade relativa ao ar: {@code v/(1 + k0·ρ·|v|)}. Nunca inverte o sinal nem
     * aumenta o módulo, qualquer que seja {@code k0·ρ·|v|}.
     */
    public static V3 applyDrag(V3 relativeVelocity, double k0, double rho) {
        double speed = relativeVelocity.length();
        return relativeVelocity.times(1.0 / (1.0 + k0 * rho * speed));
    }

    /** O paraquedas só abre com atmosfera ({@code ρ > 0}) e abaixo de {@link FlightConstants#PARACHUTE_OPEN_MAX_SPEED}. */
    public static boolean canDeployParachute(double rho, double airSpeed) {
        return rho > 0.0 && airSpeed < FlightConstants.PARACHUTE_OPEN_MAX_SPEED;
    }

    /** Índice de aquecimento {@code Q = √ρ·(v/28,9)³}. */
    public static double heatIndex(double rho, double speed) {
        double ratio = speed / FlightConstants.HEAT_REFERENCE_SPEED;
        return Math.sqrt(Math.max(0.0, rho)) * ratio * ratio * ratio;
    }

    /** Medidor de calor depois de 1 tick: {@code H + A·Q − B·H}, em [0, {@link FlightConstants#HEAT_MAX}]. */
    public static double stepHeat(double heat, double rho, double speed) {
        double next = heat + FlightConstants.HEAT_GAIN * heatIndex(rho, speed) - FlightConstants.HEAT_DECAY * heat;
        return Math.max(0.0, Math.min(FlightConstants.HEAT_MAX, next));
    }
}
