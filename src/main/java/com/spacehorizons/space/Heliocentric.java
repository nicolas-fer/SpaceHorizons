package com.spacehorizons.space;

/**
 * O sistema solar centrado no sol: as órbitas da Terra e de Marte em volta do sol (circulares, coplanares, em escala
 * comprimida) e a ponte entre o referencial HELIOCÊNTRICO (H) e o do sistema Terra-Lua (I). Java puro, sem estado.
 *
 * <h2>Os dois referenciais</h2>
 * <ul>
 *   <li><b>H, heliocêntrico</b>: a origem é o sol e os eixos não giram. É onde o foguete é integrado quando o corpo
 *       dominante é o Sol ou Marte, e onde as órbitas da Terra e de Marte são círculos keplerianos. Os planetas andam no
 *       sentido de +X para +Z (o mesmo da rotação da Terra e da órbita da Lua, o "prógrado").</li>
 *   <li><b>I, o do sistema Terra-Lua</b> (o de {@code InertialFrame}): a origem é a Terra e o sol fica FIXO em −X. É o
 *       referencial das dimensões, da Lua e da física do foguete dentro da esfera da Terra, e NÃO muda: o céu do dia na
 *       Terra e as fases da Lua continuam exatamente como eram. Em relação a H ele gira junto com a linha Terra-sol, uma
 *       volta por ano: {@code v_I = Ry(θ_T)·v_H}, com {@code θ_T} o ângulo da Terra em H. Dentro da esfera da Terra esse
 *       giro é desprezado (os eixos valem como inerciais, como valiam com o sol parado); na troca de esfera os vetores só
 *       giram por {@code Ry(±θ_T)} (sem o termo ω×r), como a troca Terra-Lua só muda a origem.</li>
 * </ul>
 *
 * <h2>A escala (decisão de design, ver {@code docs/PLANO_TIERS.md}, seção 11)</h2>
 * Não há distâncias reais: o ano da Terra tem {@link #EARTH_PERIOD_TICKS} ticks (10 dias do jogo), a Terra orbita a
 * {@link #EARTH_ORBIT_RADIUS} blocos do sol e Marte a 1,524 vez isso (a razão real), com o período que a lei de Kepler dá
 * ({@link #MARS_PERIOD_TICKS}, 1,881 ano). O parâmetro gravitacional do sol sai desses dois: {@link #MU_SUN}. A esfera de
 * influência de Laplace da Terra fica com ~834.000 blocos, a menor que ainda contém a órbita da Lua (600.000 mais a esfera
 * dela).
 */
public final class Heliocentric {
    /** Raio da órbita da Terra em torno do sol (blocos): 38 raios da Terra do mod (na vida real são 23.500). */
    public static final double EARTH_ORBIT_RADIUS = 4_000_000.0;

    /** Razão real entre as órbitas de Marte e da Terra (1,524 UA). */
    public static final double MARS_TO_EARTH_ORBIT = 1.524;

    /** Raio da órbita de Marte (blocos). */
    public static final double MARS_ORBIT_RADIUS = MARS_TO_EARTH_ORBIT * EARTH_ORBIT_RADIUS;

    /** Período da órbita da Terra (ticks): o ano do jogo, 10 dias de 24.000 ticks. */
    public static final double EARTH_PERIOD_TICKS = 240_000.0;

    /** Parâmetro gravitacional do sol (blocos³/tick²): o que dá à Terra o período {@link #EARTH_PERIOD_TICKS} no raio dela. */
    public static final double MU_SUN = 4.0 * Math.PI * Math.PI * EARTH_ORBIT_RADIUS * EARTH_ORBIT_RADIUS * EARTH_ORBIT_RADIUS
            / (EARTH_PERIOD_TICKS * EARTH_PERIOD_TICKS);

    /** Movimento médio da Terra em torno do sol (rad por tick de relógio). */
    public static final double EARTH_MEAN_MOTION = Math.sqrt(MU_SUN / (EARTH_ORBIT_RADIUS * EARTH_ORBIT_RADIUS * EARTH_ORBIT_RADIUS));

    /** Movimento médio de Marte (rad por tick de relógio). */
    public static final double MARS_MEAN_MOTION = Math.sqrt(MU_SUN / (MARS_ORBIT_RADIUS * MARS_ORBIT_RADIUS * MARS_ORBIT_RADIUS));

    /** Período da órbita de Marte (ticks): ~451.500, 1,881 ano. */
    public static final double MARS_PERIOD_TICKS = 2.0 * Math.PI / MARS_MEAN_MOTION;

    /** Raio do sol para a física (blocos): quem passa abaixo dele é queimado. Não tem relação com o tamanho do disco no céu. */
    public static final double SUN_RADIUS = 400_000.0;

    /** Ângulo da Terra em H no relógio 0 (rad). Em H a Terra gira de +X para +Z; escolhido 0 para H = I no relógio 0. */
    public static final double EARTH_PHASE0 = 0.0;

    /** Ângulo de Marte em H no relógio 0 (rad): fixa o relógio em que a janela de lançamento abre (docs/PLANO_TIERS.md, seção 11). */
    public static final double MARS_PHASE0 = Math.toRadians(75.0);

    private Heliocentric() {
    }

    /** Ângulo da Terra em H (rad, sem reduzir a 2π) no relógio {@code clock}. */
    public static double earthAngle(double clock) {
        return EARTH_PHASE0 + EARTH_MEAN_MOTION * clock;
    }

    /** Ângulo de Marte em H (rad, sem reduzir) no relógio {@code clock}. */
    public static double marsAngle(double clock) {
        return MARS_PHASE0 + MARS_MEAN_MOTION * clock;
    }

    /** Posição da Terra em H. out = {x, y, z}. */
    public static void earthPosition(double clock, double[] out) {
        circle(EARTH_ORBIT_RADIUS, earthAngle(clock), out);
    }

    /** Velocidade da Terra em H (blocos por tick de RELÓGIO). */
    public static void earthVelocity(double clock, double[] out) {
        circleVelocity(EARTH_ORBIT_RADIUS, EARTH_MEAN_MOTION, earthAngle(clock), out);
    }

    /** Posição de Marte em H. out = {x, y, z}. */
    public static void marsPosition(double clock, double[] out) {
        circle(MARS_ORBIT_RADIUS, marsAngle(clock), out);
    }

    /** Velocidade de Marte em H (blocos por tick de RELÓGIO). */
    public static void marsVelocity(double clock, double[] out) {
        circleVelocity(MARS_ORBIT_RADIUS, MARS_MEAN_MOTION, marsAngle(clock), out);
    }

    private static void circle(double radius, double angle, double[] out) {
        out[0] = radius * Math.cos(angle);
        out[1] = 0.0;
        out[2] = radius * Math.sin(angle);
    }

    private static void circleVelocity(double radius, double rate, double angle, double[] out) {
        out[0] = -radius * rate * Math.sin(angle);
        out[1] = 0.0;
        out[2] = radius * rate * Math.cos(angle);
    }

    /** Velocidade circular da Terra em torno do sol (blocos/tick), ~104,7. */
    public static double earthSpeed() {
        return EARTH_ORBIT_RADIUS * EARTH_MEAN_MOTION;
    }

    /** Velocidade circular de Marte em torno do sol (blocos/tick), ~84,8. */
    public static double marsSpeed() {
        return MARS_ORBIT_RADIUS * MARS_MEAN_MOTION;
    }

    // ------------------------------------------------------------------ H <-> I

    /** Vetor de H para I (o sistema Terra-Lua) no relógio {@code clock}: {@code Ry(θ_T)·v}. {@code out} não pode ser a entrada. */
    public static void toEarthSystem(double clock, double x, double y, double z, double[] out) {
        double t = earthAngle(clock);
        double c = Math.cos(t);
        double s = Math.sin(t);
        out[0] = c * x + s * z;
        out[1] = y;
        out[2] = -s * x + c * z;
    }

    /** Vetor de I para H: {@code Ry(−θ_T)·v}. */
    public static void fromEarthSystem(double clock, double x, double y, double z, double[] out) {
        double t = earthAngle(clock);
        double c = Math.cos(t);
        double s = Math.sin(t);
        out[0] = c * x - s * z;
        out[1] = y;
        out[2] = s * x + c * z;
    }

    /** Posição de Marte em I (relativa à Terra, nos eixos do sistema Terra-Lua). */
    public static void marsInEarthSystem(double clock, double[] out) {
        double[] m = new double[3];
        double[] e = new double[3];
        marsPosition(clock, m);
        earthPosition(clock, e);
        toEarthSystem(clock, m[0] - e[0], m[1] - e[1], m[2] - e[2], out);
    }

    /** Distância Terra-Marte (blocos). */
    public static double earthMarsDistance(double clock) {
        double[] m = new double[3];
        marsInEarthSystem(clock, m);
        return Math.sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2]);
    }

    /** Ângulo de fase heliocêntrico (rad, em [0, 2π)): quanto Marte está À FRENTE da Terra na órbita. */
    public static double marsPhaseAhead(double clock) {
        double a = marsAngle(clock) - earthAngle(clock);
        a %= 2.0 * Math.PI;
        return a < 0.0 ? a + 2.0 * Math.PI : a;
    }

    /** Período sinódico Terra-Marte (ticks): de uma janela de lançamento à seguinte, ~512.000. */
    public static double synodicPeriod() {
        return 2.0 * Math.PI / (EARTH_MEAN_MOTION - MARS_MEAN_MOTION);
    }
}
