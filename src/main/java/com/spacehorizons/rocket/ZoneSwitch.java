package com.spacehorizons.rocket;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.space.flight.FrameBridge.Local;
import com.spacehorizons.space.flight.Zones;

/**
 * Regras (lógica pura) de QUANDO o foguete troca de dimensão, além da altitude de {@link Zones}: a velocidade
 * horizontal relativa ao chão e a altura sobre o terreno decidem se a dimensão do planeta consegue carregar o terreno por
 * onde o foguete passa.
 *
 * <p>O gerador de chunks do planeta não acompanha 1.800 blocos/s (90 b/tick, a órbita da Terra de 655.360 de
 * circunferência): o servidor bloqueia esperando chunks ("Can't keep up"). Então a dimensão do planeta só serve a voo
 * LENTO e BAIXO; acima disso o foguete fica na dimensão de espaço, que é vazia (não gera nada) e onde o arrasto, o
 * paraquedas e o calor já funcionam, porque dependem do estado de voo (corpo dominante e altitude), não da dimensão. O
 * terreno da descida é pré-carregado à parte ({@link LandingPreloader}), então a troca, mais baixa, não espera a geração.
 *
 * <ul>
 *   <li><b>Entrar</b> na dimensão do planeta (abaixo da altitude de entrada de {@link Zones}) só a {@link #ENTER_HEIGHT_FRACTION}
 *       dela sobre o terreno (1.200 na Terra) e com velocidade horizontal {@code ≤ ENTER_MAX_HORIZONTAL}; ou à força,
 *       qualquer que seja a velocidade, a {@link #FORCE_ENTER_FRACTION} dela (600 na Terra): dali o piloto ainda tem
 *       segundos para frear e os chunks à frente já foram pedidos.</li>
 *   <li><b>Sair</b> da dimensão do planeta antes da altitude de saída quando a horizontal passa de
 *       {@link #LEAVE_MIN_HORIZONTAL} e o foguete está acima de {@link #LEAVE_MIN_ALTITUDE_FRACTION} da altitude de saída E
 *       de {@link #LEAVE_HEIGHT_FACTOR} vezes a altura de entrada sobre o terreno (1.500 na Terra), para o foguete que acabou
 *       de entrar à força, rápido, não sair na hora. Os 40 de sair contra os 25 de entrar são a histerese em velocidade.</li>
 * </ul>
 */
public final class ZoneSwitch {
    /** Velocidade horizontal (blocos/tick, métrica local relativa ao chão) máxima para ENTRAR na dimensão do planeta sem ser à força. */
    public static final double ENTER_MAX_HORIZONTAL = 25.0;
    /** Velocidade horizontal acima da qual o foguete SAI da dimensão do planeta antes da altitude de saída. */
    public static final double LEAVE_MIN_HORIZONTAL = 40.0;
    /** Fração da altitude de saída abaixo da qual a saída antecipada nunca acontece (decolagem e subida baixa). */
    public static final double LEAVE_MIN_ALTITUDE_FRACTION = 0.2;
    /** Fração da altitude de entrada ({@link Zones#enterAltitude}) sobre o terreno em que a entrada devagar acontece (4.000 vira 1.200 na Terra). */
    public static final double ENTER_HEIGHT_FRACTION = 0.3;
    /** Fração da altitude de entrada sobre o terreno em que a entrada é forçada, qualquer que seja a velocidade (600 na Terra). */
    public static final double FORCE_ENTER_FRACTION = 0.15;
    /** Múltiplo da altura de entrada sobre o terreno acima do qual a saída antecipada vale (histerese em altura). */
    public static final double LEAVE_HEIGHT_FACTOR = 1.25;
    /** Múltiplo de {@link #ENTER_MAX_HORIZONTAL} até o qual vale pré-carregar a dimensão do planeta (ver {@link #shouldPreload}). */
    public static final double PRELOAD_SPEED_FACTOR = 2.0;

    private ZoneSwitch() {
    }

    /** Velocidade horizontal ao chão (blocos/tick) de uma posição nas coordenadas da dimensão do planeta. */
    public static double horizontalSpeed(Local local) {
        return Math.hypot(local.vx(), local.vz());
    }

    /** Altura sobre o terreno abaixo da qual o foguete devagar entra na dimensão do planeta. */
    public static double enterHeight(PlanetDefinition planet) {
        return ENTER_HEIGHT_FRACTION * Zones.enterAltitude(planet);
    }

    /** Altura sobre o terreno abaixo da qual a entrada na dimensão do planeta é forçada. */
    public static double forceEnterHeight(PlanetDefinition planet) {
        return FORCE_ENTER_FRACTION * Zones.enterAltitude(planet);
    }

    /** Altura sobre o terreno acima da qual a saída antecipada (voo rápido) vale. */
    public static double leaveHeight(PlanetDefinition planet) {
        return LEAVE_HEIGHT_FACTOR * enterHeight(planet);
    }

    /**
     * O foguete que está no espaço, já abaixo da altitude de entrada, pode entrar na dimensão do planeta?
     * {@code heightAboveTerrain} é a altura sobre o terreno e {@code horizontalSpeed} a velocidade horizontal ao chão.
     */
    public static boolean canEnter(PlanetDefinition planet, double heightAboveTerrain, double horizontalSpeed) {
        if (heightAboveTerrain <= forceEnterHeight(planet)) {
            return true;
        }
        return heightAboveTerrain <= enterHeight(planet) && horizontalSpeed <= ENTER_MAX_HORIZONTAL;
    }

    /** A parte barata da saída antecipada (não precisa do terreno): alto o bastante e rápido. */
    public static boolean mayLeaveEarly(PlanetDefinition planet, double altitude, double horizontalSpeed) {
        return altitude >= LEAVE_MIN_ALTITUDE_FRACTION * Zones.exitAltitude(planet) && horizontalSpeed > LEAVE_MIN_HORIZONTAL;
    }

    /** O foguete que está na dimensão do planeta deve sair para o espaço antes da altitude de saída (voo rápido e alto sobre o terreno)? */
    public static boolean shouldLeaveEarly(PlanetDefinition planet, double altitude, double heightAboveTerrain, double horizontalSpeed) {
        return mayLeaveEarly(planet, altitude, horizontalSpeed) && heightAboveTerrain >= leaveHeight(planet);
    }

    /**
     * Vale pedir já os chunks da dimensão do planeta (para a troca não esperar por eles)? Só quando a entrada é iminente E a
     * velocidade é carregável ({@link #PRELOAD_SPEED_FACTOR} vezes a de entrar): pedir tickets à frente a 90 blocos por tick
     * (medido: a geração de ~300 chunks por probe, 43 s de tick) é pior do que esperar. O voo rápido pré-carrega só o ponto
     * de pouso previsto e o trecho final da descida ({@link LandingPreloader}).
     */
    public static boolean shouldPreload(double horizontalSpeed) {
        return horizontalSpeed <= PRELOAD_SPEED_FACTOR * ENTER_MAX_HORIZONTAL;
    }
}
