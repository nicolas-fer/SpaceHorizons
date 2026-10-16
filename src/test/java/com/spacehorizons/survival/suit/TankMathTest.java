package com.spacehorizons.survival.suit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.spacehorizons.survival.oxygen.OxygenMath;

class TankMathTest {
    // ---- ordem de consumo: o mais vazio (com oxigênio) primeiro ----

    @Test
    void drawsFromTheEmptiestContainerFirst() {
        // posições: 0 = 2400 (cheio), 1 = 300, 2 = 1200
        assertArrayEquals(new int[] {0, 1, 0}, TankMath.plan(new int[] {2400, 300, 1200}, 1));
        assertArrayEquals(new int[] {0, 0, 1}, TankMath.plan(new int[] {2400, 0, 1200}, 1));
    }

    @Test
    void tiesGoToTheLowerPosition() {
        assertArrayEquals(new int[] {1, 0, 0}, TankMath.plan(new int[] {500, 500, 500}, 1));
        assertArrayEquals(new int[] {0, 1, 0}, TankMath.plan(new int[] {700, 400, 400}, 1));
    }

    @Test
    void emptyContainersAreSkipped() {
        assertArrayEquals(new int[] {0, 0, 1}, TankMath.plan(new int[] {0, 0, 9000}, 1));
        assertArrayEquals(new int[] {0, 0, 0}, TankMath.plan(new int[] {0, 0, 0}, 1));
        assertArrayEquals(new int[0], TankMath.plan(new int[0], 1));
    }

    @Test
    void spillsOverToTheNextContainerWhenTheFirstRunsOut() {
        // pede 5: acaba o de 3 e completa com 2 do de 100
        assertArrayEquals(new int[] {2, 3}, TankMath.plan(new int[] {100, 3}, 5));
        // o de 3 vira zero e o próximo tick já vem do outro
        assertArrayEquals(new int[] {1, 0}, TankMath.plan(new int[] {100, 0}, 1));
    }

    @Test
    void neverTakesMoreThanAvailableOrWanted() {
        assertArrayEquals(new int[] {5, 7}, TankMath.plan(new int[] {5, 7}, 100));
        assertArrayEquals(new int[] {0, 0}, TankMath.plan(new int[] {5, 7}, 0));
        assertArrayEquals(new int[] {0, 0}, TankMath.plan(new int[] {5, 7}, -3));
        assertEquals(1, TankMath.total(TankMath.plan(new int[] {5, 7}, 1)));
    }

    @Test
    void conservesOxygenForAnyInventory() {
        Random random = new Random(20260);
        for (int round = 0; round < 500; round++) {
            int[] amounts = new int[random.nextInt(8)];
            for (int i = 0; i < amounts.length; i++) {
                amounts[i] = random.nextInt(4) == 0 ? 0 : random.nextInt(3000);
            }
            int wanted = random.nextInt(4000);
            int[] taken = TankMath.plan(amounts, wanted);
            assertEquals(amounts.length, taken.length);
            long expected = Math.min(wanted, TankMath.total(amounts));
            assertEquals(expected, TankMath.total(taken), "round " + round);
            for (int i = 0; i < amounts.length; i++) {
                assertTrue(taken[i] >= 0 && taken[i] <= amounts[i], "round " + round + " slot " + i);
            }
            // quem foi tocado é sempre o mais vazio: nenhum recipiente mais vazio ficou intacto antes de um mais cheio
            for (int i = 0; i < amounts.length; i++) {
                for (int j = 0; j < amounts.length; j++) {
                    boolean iBefore = amounts[i] < amounts[j] || (amounts[i] == amounts[j] && i < j);
                    if (amounts[i] > 0 && taken[j] > 0 && iBefore) {
                        assertEquals(amounts[i], taken[i], "round " + round + ": " + i + " devia ser esgotado antes de " + j);
                    }
                }
            }
        }
    }

    @Test
    void drainingOnePerTickEmptiesOneContainerAtATime() {
        int[] amounts = {5, 3, 4};
        StringBuilder order = new StringBuilder();
        for (int tick = 0; tick < 12; tick++) {
            int[] taken = TankMath.plan(amounts, OxygenMath.CONSUMPTION_PER_TICK);
            for (int i = 0; i < amounts.length; i++) {
                if (taken[i] > 0) {
                    order.append(i);
                }
                amounts[i] -= taken[i];
            }
        }
        // 3 do recipiente 1, depois 4 do 2, depois 5 do 0; nada sobra e nada é pedido a mais
        assertEquals("111222200000", order.toString());
        assertArrayEquals(new int[] {0, 0, 0}, amounts);
    }

    // ---- total ----

    @Test
    void totalSumsAndSaturates() {
        assertEquals(0, TankMath.total(new int[0]));
        assertEquals(3600, TankMath.total(new int[] {2400, 1200}));
        assertEquals(10, TankMath.total(new int[] {10, -5}));
        assertEquals(Integer.MAX_VALUE, TankMath.total(new int[] {Integer.MAX_VALUE, 5}));
    }

    // ---- relógio do HUD ----

    @Test
    void clockShowsMinutesAndSecondsRoundedUp() {
        assertEquals("0:00", TankMath.formatClock(0));
        assertEquals("0:00", TankMath.formatClock(-4));
        assertEquals("0:01", TankMath.formatClock(1));
        assertEquals("0:01", TankMath.formatClock(20));
        assertEquals("0:02", TankMath.formatClock(21));
        assertEquals("1:45", TankMath.formatClock(2100));
        assertEquals("2:00", TankMath.formatClock(2400));
        assertEquals("2:00", TankMath.formatClock(2399));
        assertEquals("6:00", TankMath.formatClock(7200));
        assertEquals("10:05", TankMath.formatClock(12100));
    }
}
