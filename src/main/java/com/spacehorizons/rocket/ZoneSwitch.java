package com.spacehorizons.rocket;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.space.flight.FrameBridge.Local;
import com.spacehorizons.space.flight.Zones;

/**
 * Regras (lógica pura) de QUANDO o foguete troca de dimensão, além da altitude de {@link Zones}: a velocidade
 * horizontal relativa ao chão decide se a dimensão do planeta consegue carregar o terreno por onde o foguete passa.
 *
 * <p>O gerador de chunks do planeta não acompanha 1.800 blocos/s (90 b/tick, a órbita da Terra de 655.360 de
 * circunferência): o servidor bloqueia esperando chunks ("Can't keep up"). Então a dimensão do planeta só serve a voo
 * LENTO, a ordem de grandeza da subida ({@link #ENTER_MAX_HORIZONTAL}); acima disso o foguete fica na dimensão de
 * espaço, que é vazia (não gera nada) e onde o arrasto e o calor já funcionam, porque dependem do estado de voo
 * (corpo dominante e altitude), não da dimensão.
 *
 * <ul>
 *   <li><b>Entrar</b> na dimensão do planeta (abaixo da altitude de entrada de {@link Zones}) só com velocidade
 *       horizontal {@code ≤ ENTER_MAX_HORIZONTAL}, ou à força, quando a altura sobre o terreno cai a
 *       {@link #FORCE_ENTER_FRACTION} da altitude de entrada (1.000 na Terra): dali o piloto ainda tem segundos para
 *       frear e os chunks à frente são pedidos antes.</li>
 *   <li><b>Sair</b> da dimensão do planeta antes da altitude de saída quando a horizontal passa de
 *       {@link #LEAVE_MIN_HORIZONTAL} acima de {@link #LEAVE_MIN_ALTITUDE_FRACTION} da altitude de saída (1.000 na
 *       Terra). Os 40 de sair contra os 25 de entrar são a histerese: o foguete não troca de ida e volta na fronteira.</li>
 * </ul>
 */
public final class ZoneSwitch {
    /** Velocidade horizontal (blocos/tick, métrica local relativa ao chão) máxima para ENTRAR na dimensão do planeta. */
    public static final double ENTER_MAX_HORIZONTAL = 25.0;
    /** Velocidade horizontal acima da qual o foguete SAI da dimensão do planeta antes da altitude de saída. */
    public static final double LEAVE_MIN_HORIZONTAL = 40.0;
    /** Fração da altitude de saída abaixo da qual a saída antecipada nunca acontece (decolagem e subida baixa). */
    public static final double LEAVE_MIN_ALTITUDE_FRACTION = 0.2;
    /** Fração da altitude de entrada sobre o terreno em que a entrada é forçada, qualquer que seja a velocidade. */
    public static final double FORCE_ENTER_FRACTION = 0.25;
    /** Múltiplo de {@link #ENTER_MAX_HORIZONTAL} até o qual vale pré-carregar a dimensão do planeta (ver {@link #shouldPreload}). */
    public static final double PRELOAD_SPEED_FACTOR = 2.0;

    private ZoneSwitch() {
    }

    /** Velocidade horizontal ao chão (blocos/tick) de uma posição nas coordenadas da dimensão do planeta. */
    public static double horizontalSpeed(Local local) {
        return Math.hypot(local.vx(), local.vz());
    }

    /** Altura sobre o terreno abaixo da qual a entrada na dimensão do planeta é forçada. */
    public static double forceEnterHeight(PlanetDefinition planet) {
        return FORCE_ENTER_FRACTION * Zones.enterAltitude(planet);
    }

    /**
     * O foguete que está no espaço, já abaixo da altitude de entrada, pode entrar na dimensão do planeta?
     * {@code heightAboveTerrain} é a altura sobre o terreno e {@code horizontalSpeed} a velocidade horizontal ao chão.
     */
    public static boolean canEnter(PlanetDefinition planet, double heightAboveTerrain, double horizontalSpeed) {
        return horizontalSpeed <= ENTER_MAX_HORIZONTAL || heightAboveTerrain <= forceEnterHeight(planet);
    }

    /** O foguete que está na dimensão do planeta deve sair para o espaço antes da altitude de saída (voo rápido e alto)? */
    public static boolean shouldLeaveEarly(PlanetDefinition planet, double altitude, double horizontalSpeed) {
        return altitude >= LEAVE_MIN_ALTITUDE_FRACTION * Zones.exitAltitude(planet) && horizontalSpeed > LEAVE_MIN_HORIZONTAL;
    }

    /**
     * Vale pedir já os chunks da dimensão do planeta (para a troca não esperar por eles)? Só quando a entrada é iminente E a
     * velocidade é carregável ({@link #PRELOAD_SPEED_FACTOR} vezes a de entrar): pedir tickets à frente a 90 blocos por tick
     * (medido: a geração de ~300 chunks por probe, 43 s de tick) é pior do que esperar. Quem chega à entrada forçada rápido
     * demais paga a espera do portão de chunks, e o piloto deve frear no espaço antes disso.
     */
    public static boolean shouldPreload(double horizontalSpeed) {
        return horizontalSpeed <= PRELOAD_SPEED_FACTOR * ENTER_MAX_HORIZONTAL;
    }
}
