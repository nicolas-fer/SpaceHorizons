package com.spacehorizons.space.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TsiolkovskyTest {
    @Test
    void deltaVIsExhaustVelocityTimesLogOfTheMassRatio() {
        assertEquals(40.0 * Math.log(22.0 / 7.0), Tsiolkovsky.deltaV(40, 22, 7), 1e-12);
        assertEquals(0.0, Tsiolkovsky.deltaV(40, 5, 5), 0.0);
    }

    @Test
    void finalMassInvertsDeltaV() {
        double m1 = Tsiolkovsky.finalMass(22, 40, 48);
        assertEquals(22 * Math.exp(-48.0 / 40.0), m1, 1e-12);
        assertEquals(48.0, Tsiolkovsky.deltaV(40, 22, m1), 1e-12);
        assertEquals(22 - m1, Tsiolkovsky.propellantFor(22, 40, 48), 1e-12);
    }

    @Test
    void burnTimeMatchesTheClosedFormAndTheTickByTickSum() {
        double m0 = 22;
        double flow = 0.3125; // 5 motores a 100%
        double ve = 40;
        double dv = 20;
        double closed = Tsiolkovsky.burnTime(m0, flow, ve, dv);
        assertEquals(m0 / flow * (1 - Math.exp(-dv / ve)), closed, 1e-12);
        // somando tick a tick o Δv exato até gastar 20 b/t
        double m = m0;
        double gained = 0;
        int ticks = 0;
        while (gained < dv) {
            double next = m - flow;
            double step = Tsiolkovsky.deltaV(ve, m, next);
            if (gained + step > dv) {
                ticks += 1;
                break;
            }
            gained += step;
            m = next;
            ticks++;
        }
        assertEquals(closed, ticks, 1.0);
    }
}
