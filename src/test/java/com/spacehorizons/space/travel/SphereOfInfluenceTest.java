package com.spacehorizons.space.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;

class SphereOfInfluenceTest {
    private static final double STEP = 10.0;
    private static final double EARTH_RADIUS = SolarSystem.EARTH.planet().radius();
    private static final double MOON_RADIUS = SolarSystem.MOON.planet().radius();
    private static final double D = SolarSystem.EARTH_MOON_DISTANCE;
    private static final double[] CLOCKS = {0, 6_000, 18_000, 99_999.5};

    /** Ponto a {@code distance} blocos do centro da Terra, na direção da Lua no instante. */
    private static double[] along(double clock, double distance) {
        double[] m = new double[3];
        SolarSystem.MOON.positionAt(clock, m);
        return new double[] {m[0] / D * distance, m[1] / D * distance, m[2] / D * distance};
    }

    private static SolarSystem.Body dominantAt(SolarSystem.Body current, double clock, double distance) {
        double[] p = along(clock, distance);
        return SphereOfInfluence.dominant(current, clock, p[0], p[1], p[2]);
    }

    @Test
    void groundOfEachBodyBelongsToItself() {
        for (double clock : CLOCKS) {
            assertSame(SolarSystem.EARTH, dominantAt(SolarSystem.EARTH, clock, EARTH_RADIUS));
            assertSame(SolarSystem.MOON, dominantAt(SolarSystem.MOON, clock, D - MOON_RADIUS));
            // mesmo com o corpo "errado" como atual, o chão do outro manda
            assertSame(SolarSystem.MOON, dominantAt(SolarSystem.EARTH, clock, D - MOON_RADIUS));
        }
    }

    @Test
    void flyingUpFromTheSpawnCrossesToTheMoonExactlyOnce() {
        for (double clock : CLOCKS) {
            SolarSystem.Body current = SolarSystem.EARTH;
            int switches = 0;
            double switchDistance = 0;
            for (double d = EARTH_RADIUS; d <= D - MOON_RADIUS; d += STEP) {
                SolarSystem.Body next = dominantAt(current, clock, d);
                if (next != current) {
                    switches++;
                    switchDistance = d;
                    current = next;
                }
            }
            assertEquals(1, switches);
            assertSame(SolarSystem.MOON, current);
            // a troca acontece bem antes da superfície lunar, com a Lua já dominando o quadro
            double altitudeOverMoon = D - switchDistance - MOON_RADIUS;
            assertTrue(altitudeOverMoon > 3_000 && altitudeOverMoon < 20_000, "altitude de troca " + altitudeOverMoon);
        }
    }

    @Test
    void hysteresisKeepsTheCurrentBodyInTheBand() {
        double clock = 18_000;
        double outbound = firstSwitchDistance(SolarSystem.EARTH, clock, EARTH_RADIUS, STEP);
        double inbound = firstSwitchDistance(SolarSystem.MOON, clock, D - MOON_RADIUS, -STEP);
        // indo, troca para a Lua depois do ponto de equilíbrio; voltando, troca para a Terra antes dele: sem pingue-pongue
        assertTrue(outbound > inbound, "ida " + outbound + " volta " + inbound);
        assertTrue(outbound - inbound > 500, "banda de histerese " + (outbound - inbound));
        // dentro da banda, cada lado fica no corpo em que está
        double mid = (outbound + inbound) / 2;
        assertSame(SolarSystem.EARTH, dominantAt(SolarSystem.EARTH, clock, mid));
        assertSame(SolarSystem.MOON, dominantAt(SolarSystem.MOON, clock, mid));
    }

    @Test
    void nearestHasNoHysteresis() {
        double[] far = along(0, 20_000);
        assertSame(SolarSystem.EARTH, SphereOfInfluence.nearest(SolarSystem.BODIES, 0, far[0], far[1], far[2]));
        double[] close = along(0, D - 5_000);
        assertSame(SolarSystem.MOON, SphereOfInfluence.nearest(SolarSystem.BODIES, 0, close[0], close[1], close[2]));
    }

    @Test
    void aShipLeftBehindIsCaughtByTheMovingMoonOncePerPassWithoutFlicker() {
        // nave parada no referencial da Terra a 52.000 blocos do centro, perto do plano da órbita: a Lua passa por ela uma vez por
        // dia lunar (~1,14 dia) e a esfera de influência dela a pega e solta, sem ficar pulando de um corpo ao outro na borda
        double[] p = {52_000, 0, 0};
        SolarSystem.Body current = SolarSystem.EARTH;
        int switches = 0;
        double lastSwitch = Double.NEGATIVE_INFINITY;
        double minGap = Double.MAX_VALUE;
        for (double clock = 0; clock < SolarSystem.SYNODIC_PERIOD_TICKS; clock += 5) {
            SolarSystem.Body next = SphereOfInfluence.dominant(current, clock, p[0], p[1], p[2]);
            if (next != current) {
                switches++;
                minGap = Math.min(minGap, clock - lastSwitch);
                lastSwitch = clock;
                current = next;
            }
        }
        // 7 passagens da Lua (uma por nascer) = 7 entradas e 7 saídas; cada passagem dentro da esfera dura centenas de ticks
        assertEquals(14, switches);
        assertTrue(minGap > 300, "trocas muito próximas: " + minGap);
    }

    /** Anda de d0 em passos de dd até o corpo atual mudar e devolve a distância da troca. */
    private static double firstSwitchDistance(SolarSystem.Body start, double clock, double d0, double dd) {
        SolarSystem.Body current = start;
        for (double d = d0; d > 0 && d < D; d += dd) {
            if (dominantAt(current, clock, d) != current) {
                return d;
            }
        }
        throw new AssertionError("não houve troca");
    }
}
