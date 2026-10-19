package com.spacehorizons.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** O voo rápido do piloto: liga acima de 20 b/tick, desliga abaixo de 10, e as distâncias só diminuem. */
class FastFlightTest {
    @Test
    void turnsOnAboveTheEnterSpeedAndOffBelowTheExitSpeed() {
        FastFlight f = new FastFlight();
        assertFalse(f.update(FastFlight.ENTER_SPEED));
        assertTrue(f.update(FastFlight.ENTER_SPEED + 0.1));
        assertTrue(f.update(FastFlight.EXIT_SPEED));
        assertFalse(f.update(FastFlight.EXIT_SPEED - 0.1));
    }

    @Test
    void hysteresisKeepsItOnBetweenTheTwoSpeeds() {
        FastFlight f = new FastFlight();
        f.update(50.0);
        for (double v = 19.0; v >= FastFlight.EXIT_SPEED; v -= 1.0) {
            assertTrue(f.update(v), "a " + v);
        }
        f.reset();
        for (double v = 11.0; v <= FastFlight.ENTER_SPEED; v += 1.0) {
            assertFalse(f.update(v), "subindo, a " + v);
        }
    }

    @Test
    void anUnknownSpeedKeepsTheState() {
        FastFlight f = new FastFlight();
        f.update(50.0);
        assertTrue(f.update(Double.NaN));
        f.reset();
        assertFalse(f.update(Double.NaN));
    }

    @Test
    void distancesNeverGoUp() {
        assertEquals(4, FastFlight.reduced(12, FastFlight.FAST_VIEW_DISTANCE));
        assertEquals(3, FastFlight.reduced(3, FastFlight.FAST_VIEW_DISTANCE));
    }
}
