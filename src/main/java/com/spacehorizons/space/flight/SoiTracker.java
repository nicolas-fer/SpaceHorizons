package com.spacehorizons.space.flight;

import com.spacehorizons.space.SolarSystem;

/**
 * Troca do corpo dominante da gravidade (patched conics): esfera de influência de Laplace com histerese de 5%.
 *
 * <p>O estado é sempre relativo ao corpo dominante, então a troca é só uma mudança de origem e de referencial de
 * velocidade, EXATA (a posição e a velocidade absolutas se conservam):
 * <pre>
 *   Terra → Lua:  r' = r − centro_Lua(clock),  v' = v − velocidade_Lua(clock)
 *   Lua → Terra:  r' = r + centro_Lua(clock),  v' = v + velocidade_Lua(clock)
 * </pre>
 * Entra na Lua a 95% do raio da esfera (9.146 blocos) e só volta à Terra a 105% (10.108): entre os dois a troca não
 * acontece, então não há vai e vem na fronteira. Esta esfera é a física; a regra antiga ({@code SphereOfInfluence}) fica
 * só para o autopiloto criativo.
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
     * é multiplicada por ela (0 com o relógio pausado: a Lua fica parada).
     */
    public static FlightState update(FlightState s, double clock, double clockRate) {
        if (s.body() == SolarSystem.EARTH) {
            for (SolarSystem.Body other : SolarSystem.BODIES) {
                if (other == SolarSystem.EARTH) {
                    continue;
                }
                V3 center = position(other, clock);
                V3 relative = s.r().minus(center);
                if (relative.length() < BodyPhysics.of(other).soiEnterRadius()) {
                    return s.withBody(other, relative, s.v().minus(velocity(other, clock, clockRate)));
                }
            }
            return s;
        }
        if (s.r().length() > BodyPhysics.of(s.body()).soiExitRadius()) {
            return s.withBody(SolarSystem.EARTH, s.r().plus(position(s.body(), clock)),
                    s.v().plus(velocity(s.body(), clock, clockRate)));
        }
        return s;
    }

    /**
     * Corpo dominante de um ponto absoluto do espaço, SEM histerese (para criar um estado a partir de uma posição, por
     * exemplo ao entrar vindo da dimensão de espaço): a Lua dentro da esfera de Laplace, a Terra no resto.
     */
    public static SolarSystem.Body dominantAt(V3 absolute, double clock) {
        for (SolarSystem.Body other : SolarSystem.BODIES) {
            if (other != SolarSystem.EARTH
                    && absolute.minus(position(other, clock)).length() < BodyPhysics.of(other).soiRadius()) {
                return other;
            }
        }
        return SolarSystem.EARTH;
    }

    static V3 position(SolarSystem.Body b, double clock) {
        double[] out = new double[3];
        b.positionAt(clock, out);
        return V3.of(out);
    }

    /** Velocidade do centro por tick de jogo (a do SolarSystem é por tick de relógio). */
    static V3 velocity(SolarSystem.Body b, double clock, double clockRate) {
        double[] out = new double[3];
        b.velocityAt(clock, out);
        return V3.of(out).times(clockRate);
    }
}
