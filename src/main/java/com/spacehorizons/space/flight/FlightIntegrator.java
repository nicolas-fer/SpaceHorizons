package com.spacehorizons.space.flight;

import com.spacehorizons.planet.atmosphere.AtmosphereModel;

/**
 * Um passo (1 tick) da dinâmica de voo no referencial do corpo dominante (eixos inerciais paralelos aos do espaço).
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
 * sustentação; a velocidade relativa ao ar é a inercial menos {@code ω × r} (zero na Terra, a única com atmosfera).
 */
public final class FlightIntegrator {
    private FlightIntegrator() {
    }

    /**
     * Avança o estado em 1 tick. {@code clock} é o relógio no INÍCIO do tick (só entra na velocidade do ar de corpos que
     * giram; a integração em si independe dele). Se {@code c} pede empuxo sem combustível, motor ou direção, ele é
     * ignorado.
     */
    public static FlightState step(FlightState s, Control c, RocketStats st, double clock) {
        BodyPhysics bp = BodyPhysics.of(s.body());
        double mu = bp.mu();
        V3 r = s.r();
        V3 v = s.v();
        double fuel = Math.max(0.0, s.fuelMb());

        // 1. empuxo: Tsiolkovsky exato no tick
        V3 thrustAccel = V3.ZERO;
        double throttle = c.throttle();
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
            V3 air = atmosphere.present() ? FrameBridge.rotationVelocity(s.body(), clock, 1.0, r) : V3.ZERO;
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
        return new FlightState(s.body(), r, v, fuel, heat, chute);
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
