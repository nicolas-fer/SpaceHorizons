package com.spacehorizons.space.flight;

import com.spacehorizons.space.Heliocentric;
import com.spacehorizons.space.SolarSystem;

/**
 * Troca do corpo dominante da gravidade (patched conics): esferas de influência de Laplace encadeadas, com histerese de 5%.
 * <pre>
 *   Sol  ⊃  Terra  ⊃  Lua
 *   Sol  ⊃  Marte
 * </pre>
 * O estado é sempre relativo ao corpo dominante, então toda troca é EXATA em posição e velocidade absolutas (só muda a origem, e,
 * entre a Terra e o Sol, o referencial: I e H giram um em relação ao outro, ver {@link Heliocentric}).
 *
 * <h2>Dentro do sistema Terra-Lua (I)</h2>
 * A Lua anda em círculo kepleriano, a ~38 blocos/tick, e a velocidade relativa ao entrar na esfera dela é a da transferência menos
 * essa, não a da Lua em E (~120 blocos/tick):
 * <pre>
 *   Terra → Lua:  r' = r − centro_Lua(clock),  v' = v − velocidade_Lua(clock)
 *   Lua → Terra:  r' = r + centro_Lua(clock),  v' = v + velocidade_Lua(clock)
 * </pre>
 * Entra na Lua a 95% do raio da esfera (91.457 blocos) e só volta à Terra a 105% (101.085).
 *
 * <h2>Entre o sol e os planetas (H)</h2>
 * O sol é o corpo dominante do espaço interplanetário e o estado dele (e o de Marte) vive em H, eixos que não giram:
 * <pre>
 *   Sol → Marte:  r' = r − centro_Marte(clock),  v' = v − velocidade_Marte(clock)               (mesmos eixos)
 *   Sol → Terra:  r' = Ry(θ_T)·(r − centro_Terra), v' = Ry(θ_T)·(v − velocidade_Terra)            (de H para I)
 *   Terra → Sol:  r' = centro_Terra + Ry(−θ_T)·r,  v' = velocidade_Terra + Ry(−θ_T)·v             (de I para H)
 * </pre>
 * A Terra tem esfera de ~834.000 blocos (Laplace contra o sol) e Marte ~521.000; a da Lua cabe dentro da da Terra.
 */
public final class SoiTracker {
    private SoiTracker() {
    }

    /** {@link #update(FlightState, double, double)} com o relógio a taxa 1. */
    public static FlightState update(FlightState s, double clock) {
        return update(s, clock, 1.0);
    }

    /**
     * Troca o corpo dominante, se for o caso, no instante {@code clock} (relógio do estado, isto é, depois do passo).
     * {@code clockRate} é a taxa do relógio por tick de jogo: a velocidade de um corpo vem em blocos por tick de RELÓGIO e
     * é multiplicada por ela (0 com o relógio pausado: a Lua e os planetas ficam parados).
     */
    public static FlightState update(FlightState s, double clock, double clockRate) {
        SolarSystem.Body body = s.body();
        if (body == SolarSystem.EARTH) {
            if (s.r().length() > BodyPhysics.of(SolarSystem.EARTH).soiExitRadius()) {
                return earthToSun(s, clock, clockRate);
            }
            V3 center = position(SolarSystem.MOON, clock);
            V3 relative = s.r().minus(center);
            if (relative.length() < BodyPhysics.of(SolarSystem.MOON).soiEnterRadius()) {
                return s.withBody(SolarSystem.MOON, relative, s.v().minus(velocity(SolarSystem.MOON, clock, clockRate)));
            }
            return s;
        }
        if (body == SolarSystem.MOON) {
            if (s.r().length() > BodyPhysics.of(SolarSystem.MOON).soiExitRadius()) {
                return s.withBody(SolarSystem.EARTH, s.r().plus(position(SolarSystem.MOON, clock)),
                        s.v().plus(velocity(SolarSystem.MOON, clock, clockRate)));
            }
            return s;
        }
        if (body == SolarSystem.MARS) {
            if (s.r().length() > BodyPhysics.of(SolarSystem.MARS).soiExitRadius()) {
                return s.withBody(SolarSystem.SUN, s.r().plus(position(SolarSystem.MARS, clock)),
                        s.v().plus(velocity(SolarSystem.MARS, clock, clockRate)));
            }
            return s;
        }
        // o sol: entra na esfera de Marte ou na da Terra
        V3 marsCenter = position(SolarSystem.MARS, clock);
        V3 toMars = s.r().minus(marsCenter);
        if (toMars.length() < BodyPhysics.of(SolarSystem.MARS).soiEnterRadius()) {
            return s.withBody(SolarSystem.MARS, toMars, s.v().minus(velocity(SolarSystem.MARS, clock, clockRate)));
        }
        V3 earthCenter = earthHelioPosition(clock);
        V3 toEarth = s.r().minus(earthCenter);
        if (toEarth.length() < BodyPhysics.of(SolarSystem.EARTH).soiEnterRadius()) {
            return sunToEarth(s, toEarth, clock, clockRate);
        }
        return s;
    }

    /** Terra → Sol: a posição e a velocidade relativas à Terra (em I) viram heliocêntricas (em H). */
    private static FlightState earthToSun(FlightState s, double clock, double clockRate) {
        double[] r = new double[3];
        double[] v = new double[3];
        Heliocentric.fromEarthSystem(clock, s.r().x(), s.r().y(), s.r().z(), r);
        Heliocentric.fromEarthSystem(clock, s.v().x(), s.v().y(), s.v().z(), v);
        V3 absoluteR = earthHelioPosition(clock).plus(V3.of(r));
        V3 absoluteV = earthHelioVelocity(clock, clockRate).plus(V3.of(v));
        return s.withBody(SolarSystem.SUN, absoluteR, absoluteV);
    }

    /** Sol → Terra: {@code toEarth} é a posição relativa à Terra em H; o estado novo vive em I. */
    private static FlightState sunToEarth(FlightState s, V3 toEarth, double clock, double clockRate) {
        V3 relativeV = s.v().minus(earthHelioVelocity(clock, clockRate));
        double[] r = new double[3];
        double[] v = new double[3];
        Heliocentric.toEarthSystem(clock, toEarth.x(), toEarth.y(), toEarth.z(), r);
        Heliocentric.toEarthSystem(clock, relativeV.x(), relativeV.y(), relativeV.z(), v);
        return s.withBody(SolarSystem.EARTH, V3.of(r), V3.of(v));
    }

    /**
     * Corpo dominante de um ponto absoluto de I (o sistema Terra-Lua, a Terra na origem), SEM histerese (para criar um estado a partir
     * de uma posição, por exemplo ao entrar vindo da dimensão de espaço): a Lua dentro da esfera de Laplace dela, a Terra dentro da
     * dela, Marte dentro da sua e o sol no resto.
     */
    public static SolarSystem.Body dominantAt(V3 absolute, double clock) {
        if (absolute.length() < BodyPhysics.of(SolarSystem.EARTH).soiRadius()) {
            if (absolute.minus(position(SolarSystem.MOON, clock)).length() < BodyPhysics.of(SolarSystem.MOON).soiRadius()) {
                return SolarSystem.MOON;
            }
            return SolarSystem.EARTH;
        }
        double[] mars = new double[3];
        Heliocentric.marsInEarthSystem(clock, mars);
        if (absolute.minus(V3.of(mars)).length() < BodyPhysics.of(SolarSystem.MARS).soiRadius()) {
            return SolarSystem.MARS;
        }
        return SolarSystem.SUN;
    }

    /** Posição do centro do corpo no referencial de estado do PAI dele (I para a Lua, H para Marte). */
    static V3 position(SolarSystem.Body b, double clock) {
        double[] out = new double[3];
        b.inertialPositionAt(clock, out);
        return V3.of(out);
    }

    /** Posição da Terra em H (relativa ao sol). */
    static V3 earthHelioPosition(double clock) {
        double[] out = new double[3];
        Heliocentric.earthPosition(clock, out);
        return V3.of(out);
    }

    /** Velocidade da Terra em H por tick de jogo. */
    static V3 earthHelioVelocity(double clock, double clockRate) {
        double[] out = new double[3];
        Heliocentric.earthVelocity(clock, out);
        return V3.of(out).times(clockRate);
    }

    /** Velocidade do centro por tick de jogo (a do SolarSystem é por tick de relógio). */
    static V3 velocity(SolarSystem.Body b, double clock, double clockRate) {
        double[] out = new double[3];
        b.inertialVelocityAt(clock, out);
        return V3.of(out).times(clockRate);
    }
}
