package com.spacehorizons.space.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;

class SphereOfInfluenceTest {
    private static final double STEP = 10.0;

    @Test
    void groundOfEachBodyBelongsToItself() {
        assertSame(SolarSystem.EARTH, SphereOfInfluence.dominant(SolarSystem.EARTH, 10_430, 0, 0));
        assertSame(SolarSystem.MOON,
                SphereOfInfluence.dominant(SolarSystem.MOON, SolarSystem.MOON.x() - 2_608, 0, 0));
        // mesmo com o corpo "errado" como atual, o chão do outro manda
        assertSame(SolarSystem.MOON, SphereOfInfluence.dominant(SolarSystem.EARTH, SolarSystem.MOON.x() - 2_608, 0, 0));
    }

    @Test
    void flyingUpFromTheSpawnCrossesToTheMoonExactlyOnce() {
        SolarSystem.Body current = SolarSystem.EARTH;
        int switches = 0;
        double switchX = 0;
        for (double x = 10_430; x <= SolarSystem.MOON.x() - 2_608; x += STEP) {
            SolarSystem.Body next = SphereOfInfluence.dominant(current, x, 0, 0);
            if (next != current) {
                switches++;
                switchX = x;
                current = next;
            }
        }
        assertEquals(1, switches);
        assertSame(SolarSystem.MOON, current);
        // a troca acontece bem antes da superfície lunar, com a Lua já dominando o quadro
        double altitudeOverMoon = SolarSystem.MOON.x() - switchX - SolarSystem.MOON.planet().radius();
        assertTrue(altitudeOverMoon > 3_000 && altitudeOverMoon < 20_000, "altitude de troca " + altitudeOverMoon);
    }

    @Test
    void hysteresisKeepsTheCurrentBodyInTheBand() {
        double outbound = firstSwitchX(SolarSystem.EARTH, 10_430, STEP);
        double inbound = firstSwitchX(SolarSystem.MOON, SolarSystem.MOON.x() - 2_608, -STEP);
        // indo, troca para a Lua depois do ponto de equilíbrio; voltando, troca para a Terra antes dele: sem pingue-pongue
        assertTrue(outbound > inbound, "ida " + outbound + " volta " + inbound);
        assertTrue(outbound - inbound > 500, "banda de histerese " + (outbound - inbound));
        // dentro da banda, cada lado fica no corpo em que está
        double mid = (outbound + inbound) / 2;
        assertSame(SolarSystem.EARTH, SphereOfInfluence.dominant(SolarSystem.EARTH, mid, 0, 0));
        assertSame(SolarSystem.MOON, SphereOfInfluence.dominant(SolarSystem.MOON, mid, 0, 0));
    }

    @Test
    void nearestHasNoHysteresis() {
        assertSame(SolarSystem.EARTH, SphereOfInfluence.nearest(SolarSystem.BODIES, 20_000, 0, 0));
        assertSame(SolarSystem.MOON, SphereOfInfluence.nearest(SolarSystem.BODIES, SolarSystem.MOON.x() - 5_000, 0, 0));
    }

    /** Anda de x0 em passos de dx até o corpo atual mudar e devolve o x da troca. */
    private static double firstSwitchX(SolarSystem.Body start, double x0, double dx) {
        SolarSystem.Body current = start;
        for (double x = x0; x > 0 && x < SolarSystem.MOON.x(); x += dx) {
            if (SphereOfInfluence.dominant(current, x, 0, 0) != current) {
                return x;
            }
        }
        throw new AssertionError("não houve troca");
    }
}
