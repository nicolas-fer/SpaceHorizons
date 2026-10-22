package com.spacehorizons.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.planet.Planets;

/** O céu de Marte enquanto ele não está no sistema solar: um corpo destacado que só gira com o dia de Marte. */
class DetachedSkyBodyTest {
    private static final double DAY = Planets.spec(Planets.MARS).dayLengthTicks();

    private static double[] sun(SolarSystem.Body body, double x, double z, double clock) {
        double[] out = new double[3];
        SunModel.localDirection(body, x, z, clock, out);
        return out;
    }

    @Test
    void earthAndMoonKeepTheirRealBodies() {
        assertSame(SolarSystem.EARTH, SolarSystem.skyBodyOf(Planets.EARTH));
        assertSame(SolarSystem.MOON, SolarSystem.skyBodyOf(Planets.MOON));
    }

    @Test
    void marsIsDetachedFromPhysicsAndTravel() {
        SolarSystem.Body mars = SolarSystem.skyBodyOf(Planets.MARS);
        assertSame(mars, SolarSystem.skyBodyOf(Planets.MARS));
        assertFalse(SolarSystem.BODIES.contains(mars), "Marte não pode entrar na física orbital antes da etapa 3");
        assertTrue(SolarSystem.bodyOf(Planets.MARS).isEmpty());
    }

    @Test
    void noonAtTick6000AndMidnightHalfASolLater() {
        SolarSystem.Body mars = SolarSystem.skyBodyOf(Planets.MARS);
        double[] noon = sun(mars, 0, 0, 6_000);
        assertEquals(1.0, noon[1], 1e-9, "sol a pino no meio-dia do ponto (0, 0)");
        double[] midnight = sun(mars, 0, 0, 6_000 + DAY / 2);
        assertEquals(-1.0, midnight[1], 1e-9);
        // nascer a leste antes do meio-dia (x > 0 é leste nos eixos do Minecraft)
        double[] morning = sun(mars, 0, 0, 6_000 - DAY / 8);
        assertTrue(morning[0] > 0 && morning[1] > 0, "o sol da manhã deveria estar a leste e acima do horizonte");
    }

    @Test
    void theLocalDayLastsOneSol() {
        SolarSystem.Body mars = SolarSystem.skyBodyOf(Planets.MARS);
        for (double t = 0; t < 200_000; t += 7_919) {
            double[] a = sun(mars, 1_234, -567, t);
            double[] b = sun(mars, 1_234, -567, t + DAY);
            for (int i = 0; i < 3; i++) {
                assertEquals(a[i], b[i], 1e-6, "t=" + t);
            }
        }
        // e não é o dia da Terra: um dia do jogo depois o sol já não está no mesmo lugar
        double[] a = sun(mars, 0, 0, 6_000);
        double[] b = sun(mars, 0, 0, 6_000 + SunModel.DAY_TICKS);
        assertTrue(b[1] < a[1] - 1e-4);
    }

    @Test
    void effectiveClockFollowsTheMartianSun() {
        SolarSystem.Body mars = SolarSystem.skyBodyOf(Planets.MARS);
        // no meio-dia de Marte o céu do vanilla tem de ser o do meio-dia (tick 6000 do dia)
        double sol3noon = 6_000 + 3 * DAY;
        double effective = SunModel.effectiveClock(mars, 0, 0, sol3noon);
        assertEquals(6_000, Math.floorMod(Math.round(effective), 24_000L), 2);
    }
}
