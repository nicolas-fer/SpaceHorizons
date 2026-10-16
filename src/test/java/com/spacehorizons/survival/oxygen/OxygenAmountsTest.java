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
}
