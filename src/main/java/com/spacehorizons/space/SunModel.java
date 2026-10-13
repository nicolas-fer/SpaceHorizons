package com.spacehorizons.space;

/**
 * Sol único do sistema, no referencial do espaço. Java puro e sem estado.
 *
 * O sol gira no plano equatorial (X–Z do espaço) com o relógio do Overworld, de modo que no spawn da Terra
 * (lat 0, lon 0) ele coincide com o sol do vanilla: nasce no leste no tick 0, meio-dia no 6000, põe no 12000.
 * Em qualquer outro ponto (outra longitude, outra latitude, outro corpo) a direção local sai de SpaceFrame,
 * e daí o "horário efetivo": o tick do dia em que o sol do vanilla teria a mesma altura no céu.
 */
public final class SunModel {
    public static final long DAY_TICKS = 24_000L;
    private static final long NOON = 6_000L;

    private SunModel() {
    }

    /** Ângulo do sol em radianos (0 = meio-dia no spawn da Terra) para um tick do relógio do Overworld. */
    public static double angle(double clockTicks) {
        return 2.0 * Math.PI * ((clockTicks - NOON) / DAY_TICKS);
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

    /** Direção do sol nos eixos do Minecraft (x leste, y cima, z norte) no ponto (x, z) do corpo. */
    public static void localDirection(SolarSystem.Body body, double x, double z, double clockTicks, double[] out) {
        double[] s = new double[3];
        directionInSpace(clockTicks, s);
        SpaceFrame.spaceToLocal(body, x, z, s[0], s[1], s[2], out);
    }

    /**
     * Tick do dia (0..24000) em que o sol do vanilla teria a mesma altura que o sol local: de manhã quando o sol
     * está no lado leste do céu, de tarde no lado oeste. Usado para dirigir céu e luz do chão pelo vanilla.
     */
    public static double effectiveTimeOfDay(double[] localSun) {
        double up = Math.max(-1.0, Math.min(1.0, localSun[1]));
        double a = Math.acos(up);
        // sol a leste (x > 0) = manhã (ângulo negativo antes do meio-dia)
        if (localSun[0] > 0.0) {
            a = -a;
        }
        double t = NOON + a / (2.0 * Math.PI) * DAY_TICKS;
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
