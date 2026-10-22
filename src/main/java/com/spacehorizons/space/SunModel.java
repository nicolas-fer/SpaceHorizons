package com.spacehorizons.space;

/**
 * Sol único do sistema, no referencial do espaço. Java puro e sem estado.
 *
 * O sol gira no plano equatorial (X–Z do espaço) com o relógio do Overworld, de modo que no spawn da Terra
 * (lat 0, lon 0) ele coincide com o sol do vanilla: meio-dia no tick 6000, pondo-se no oeste e nascendo no leste.
 * O vanilla (26.x) não gira o sol de forma linear: o atributo sun_angle usa uma curva de Bézier cúbica
 * (0,362; 0,241; 0,638; 0,759) sobre o dia inteiro, que deixa o sol mais lento perto do meio-dia e da meia-noite.
 * O modelo usa a MESMA curva, então o disco, as cores do céu e a luz do chão (todos presos aos ticks do relógio)
 * batem com o vanilla no spawn, e o terminador visto do espaço bate com o do chão.
 * Em qualquer outro ponto (outra longitude, outra latitude, outro corpo) a direção local sai de SpaceFrame,
 * e daí o "horário efetivo": o tick do dia em que o sol do vanilla teria a mesma altura no céu.
 * Os corpos têm orientação que muda com o tempo (SolarSystem): na Lua, de rotação sincronizada, o sol local percorre o
 * céu uma vez a cada 8 dias do jogo (o dia lunar, igual ao ciclo de fases visto da Terra).
 */
public final class SunModel {
    public static final long DAY_TICKS = 24_000L;
    private static final long NOON = 6_000L;
    /** Pontos de controle da curva do vanilla (a simétrica é 1 - x, 1 - y). */
    private static final double EASE_X1 = 0.362;
    private static final double EASE_Y1 = 0.241;
    private static final double TWO_PI = 2.0 * Math.PI;

    private SunModel() {
    }

    /** Um eixo da Bézier cúbica de pontos (0, p1, 1 - p1, 1) no parâmetro t. */
    private static double bezier(double p1, double t) {
        double u = 1.0 - t;
        return 3.0 * u * u * t * p1 + 3.0 * u * t * t * (1.0 - p1) + t * t * t;
    }

    /** Resolve o parâmetro t da curva em que o eixo {@code p1} vale {@code target} (curva monótona, bisseção). */
    private static double solveBezier(double p1, double target) {
        double lo = 0.0;
        double hi = 1.0;
        for (int i = 0; i < 60; i++) {
            double mid = 0.5 * (lo + hi);
            if (bezier(p1, mid) < target) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return 0.5 * (lo + hi);
    }

    /** Curva do ângulo do sol: fração do dia (0..1 desde o meio-dia) para fração do giro (0..1). */
    public static double skyEase(double dayFraction) {
        double x = Math.max(0.0, Math.min(1.0, dayFraction));
        return bezier(EASE_Y1, solveBezier(EASE_X1, x));
    }

    /** Inversa de {@link #skyEase}: fração do giro para fração do dia. */
    public static double skyEaseInverse(double turnFraction) {
        double y = Math.max(0.0, Math.min(1.0, turnFraction));
        return bezier(EASE_X1, solveBezier(EASE_Y1, y));
    }

    /** Derivada de {@link #skyEase} em relação à fração do dia (inclinação da curva; média 1). */
    public static double skyEaseSlope(double dayFraction) {
        double x = Math.max(0.0, Math.min(1.0, dayFraction));
        double t = solveBezier(EASE_X1, x);
        return bezierSlope(EASE_Y1, t) / bezierSlope(EASE_X1, t);
    }

    /** Derivada de {@link #bezier} em relação ao parâmetro t. */
    private static double bezierSlope(double p1, double t) {
        double u = 1.0 - t;
        return 3.0 * p1 * (u * u - 2.0 * u * t) + 3.0 * (1.0 - p1) * (2.0 * u * t - t * t) + 3.0 * t * t;
    }

    /**
     * Velocidade angular do sol (rad por tick de relógio): a derivada de {@link #angle}. Média 2π/{@link #DAY_TICKS}, mas
     * a curva do vanilla a faz variar de ~0,67 a ~1,19 vezes a média (mais lenta ao meio-dia, mais rápida à meia-noite).
     */
    public static double angularRate(double clockTicks) {
        double fraction = (clockTicks - NOON) / DAY_TICKS;
        fraction -= Math.floor(fraction);
        return TWO_PI / DAY_TICKS * skyEaseSlope(fraction);
    }

    /** Ângulo do sol em radianos [0, 2π) (0 = meio-dia no spawn da Terra) para um tick do relógio do Overworld. */
    public static double angle(double clockTicks) {
        double fraction = (clockTicks - NOON) / DAY_TICKS;
        fraction -= Math.floor(fraction);
        return TWO_PI * skyEase(fraction);
    }

    /**
     * Direção do sol no espaço (unitária). É o sol local do vanilla no spawn, (-sen a, cos a, 0) em leste/cima/norte,
     * levado ao espaço pela base do spawn (leste = +Z, cima = +X, norte = +Y).
     */
    public static void directionInSpace(double clockTicks, double[] out) {
        double a = angle(clockTicks);
        out[0] = Math.cos(a);
        out[1] = 0.0;
        out[2] = -Math.sin(a);
    }

    /**
     * Direção do sol nos eixos do Minecraft (x leste, y cima, z norte) no ponto (x, z) do corpo no instante dado. Usa a
     * orientação do corpo naquele instante: a Terra não gira neste referencial (o sol é que dá a volta, uma por dia do
     * jogo); a Lua gira com a órbita, então o "dia" dela dura um ciclo de fases inteiro (8 dias do jogo).
     */
    public static void localDirection(SolarSystem.Body body, double x, double z, double clockTicks, double[] out) {
        double[] s = new double[3];
        // Terra e Lua: o sol no infinito (a direção de sempre); Marte: a direção verdadeira do centro dele ao sol
        SolarSystem.sunDirectionFrom(body, clockTicks, s);
        SpaceFrame.spaceToLocal(body, clockTicks, x, z, s[0], s[1], s[2], out);
    }

    /**
     * Ângulo do sol do vanilla (rad, 0 = meio-dia, negativo de manhã, em (-π, π]) cujo sol, no plano leste-cima, tem a
     * mesma altura que o sol local: -acos(up) com o sol a leste, +acos(up) a oeste.
     */
    public static double vanillaSkyAngle(double[] localSun) {
        double up = Math.max(-1.0, Math.min(1.0, localSun[1]));
        double a = Math.acos(up);
        // sol a leste (x > 0) = manhã (ângulo negativo antes do meio-dia)
        return localSun[0] > 0.0 ? -a : a;
    }

    /**
     * Giro em torno da vertical (rad, convenção do JOML {@code rotationY}: x' = x cos + z sen, z' = -x sen + z cos) que leva o
     * sol do vanilla de {@link #vanillaSkyAngle}, (-sen a, cos a, 0), ao sol local. Os dois têm a mesma altura, então o giro
     * só corrige o azimute: é o que inclina o caminho do sol em latitudes altas. 0 quando o sol está no zênite ou no nadir.
     */
    public static double azimuthTilt(double[] localSun) {
        double horizontal = Math.hypot(localSun[0], localSun[2]);
        if (horizontal < 1.0e-9) {
            return 0.0;
        }
        double sign = localSun[0] > 0.0 ? 1.0 : -1.0;
        return Math.atan2(-localSun[2] * sign, localSun[0] * sign);
    }

    /**
     * Tick do dia (0..24000) em que o sol do vanilla teria a mesma altura que o sol local: de manhã quando o sol
     * está no lado leste do céu, de tarde no lado oeste. Usado para dirigir céu e luz do chão pelo vanilla.
     */
    public static double effectiveTimeOfDay(double[] localSun) {
        double a = vanillaSkyAngle(localSun);
        double turn = a / TWO_PI;
        turn -= Math.floor(turn);
        double t = NOON + skyEaseInverse(turn) * DAY_TICKS;
        return Math.floorMod((long) Math.floor(t), DAY_TICKS) + (t - Math.floor(t));
    }

    /**
     * Relógio efetivo completo para o ponto: mantém o número do dia do relógio real (fases da lua do vanilla e afins)
     * e troca só o tick dentro do dia, escolhendo o dia vizinho mais próximo para o valor não pular para trás à toa.
     */
    public static double effectiveClock(SolarSystem.Body body, double x, double z, double clockTicks) {
        double[] local = new double[3];
        localDirection(body, x, z, clockTicks, local);
        double tod = effectiveTimeOfDay(local);
        double dayStart = Math.floor(clockTicks / DAY_TICKS) * DAY_TICKS;
        double candidate = dayStart + tod;
        double delta = candidate - clockTicks;
        if (delta > DAY_TICKS / 2.0) {
            candidate -= DAY_TICKS;
        } else if (delta < -DAY_TICKS / 2.0) {
            candidate += DAY_TICKS;
        }
        return candidate;
    }
}
