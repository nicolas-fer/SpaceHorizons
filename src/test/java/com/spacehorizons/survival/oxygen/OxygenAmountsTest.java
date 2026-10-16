package com.spacehorizons.survival.oxygen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OxygenAmountsTest {
    @Test
    void clampKeepsTheAmountInsideTheCapacity() {
        assertEquals(0, OxygenAmounts.clamp(-5, 100));
        assertEquals(100, OxygenAmounts.clamp(250, 100));
        assertEquals(40, OxygenAmounts.clamp(40, 100));
        assertEquals(0, OxygenAmounts.clamp(10, 0));
    }

    @Test
    void acceptableAndExtractableRespectSpaceAndContent() {
        assertEquals(60, OxygenAmounts.acceptable(40, 100, 500));
        assertEquals(20, OxygenAmounts.acceptable(40, 100, 20));
        assertEquals(0, OxygenAmounts.acceptable(100, 100, 5));
        assertEquals(0, OxygenAmounts.acceptable(40, 100, -3));
        assertEquals(40, OxygenAmounts.extractable(40, 100, 500));
        assertEquals(7, OxygenAmounts.extractable(40, 100, 7));
        assertEquals(0, OxygenAmounts.extractable(0, 100, 7));
        assertEquals(0, OxygenAmounts.extractable(40, 100, -1));
    }

    @Test
    void barWidthUsesTheHotbarScale() {
        assertEquals(0, OxygenAmounts.barWidth(0, 100));
        assertEquals(13, OxygenAmounts.barWidth(100, 100));
        assertEquals(7, OxygenAmounts.barWidth(50, 100));
        assertEquals(0, OxygenAmounts.barWidth(50, 0));
    }

    @Test
    void tankCapacitiesAreTwoAndSixMinutes() {
        assertEquals("2:00", OxygenAmounts.formatTime(OxygenAmounts.SMALL_TANK_CAPACITY));
        assertEquals("6:00", OxygenAmounts.formatTime(OxygenAmounts.LARGE_TANK_CAPACITY));
        assertEquals(3 * OxygenAmounts.SMALL_TANK_CAPACITY, OxygenAmounts.LARGE_TANK_CAPACITY);
    }

    @Test
    void formatTimeShowsMinutesAndSecondsRoundingUp() {
        assertEquals("0:00", OxygenAmounts.formatTime(0));
        assertEquals("0:00", OxygenAmounts.formatTime(-30), "negativo conta como vazio");
        assertEquals("0:01", OxygenAmounts.formatTime(1), "um tick de oxigênio ainda não é vazio");
        assertEquals("0:01", OxygenAmounts.formatTime(20));
        assertEquals("0:02", OxygenAmounts.formatTime(21));
        assertEquals("0:59", OxygenAmounts.formatTime(1_180));
        assertEquals("1:00", OxygenAmounts.formatTime(1_200));
        assertEquals("1:30", OxygenAmounts.formatTime(1_800));
        assertEquals("10:05", OxygenAmounts.formatTime(12_100));
        assertEquals("1789569:43", OxygenAmounts.formatTime(Integer.MAX_VALUE), "sem estouro de int");
    }
}
