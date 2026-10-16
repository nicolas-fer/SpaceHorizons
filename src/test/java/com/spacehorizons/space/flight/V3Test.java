package com.spacehorizons.space.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class V3Test {
    @Test
    void basicArithmetic() {
        V3 a = new V3(1, 2, 3);
        V3 b = new V3(-4, 5, 0.5);
        assertEquals(new V3(-3, 7, 3.5), a.plus(b));
        assertEquals(new V3(5, -3, 2.5), a.minus(b));
        assertEquals(new V3(2, 4, 6), a.times(2));
        assertEquals(new V3(-1, -2, -3), a.negate());
        assertEquals(-4 + 10 + 1.5, a.dot(b), 1e-15);
        assertEquals(14, a.lengthSquared(), 1e-15);
        assertEquals(Math.sqrt(14), a.length(), 1e-15);
    }

    @Test
    void crossProductIsRightHandedAndPerpendicular() {
        V3 x = new V3(1, 0, 0);
        V3 y = new V3(0, 1, 0);
        assertEquals(new V3(0, 0, 1), x.cross(y));
        assertEquals(new V3(0, 0, -1), y.cross(x));
        V3 a = new V3(1.5, -2, 0.3);
        V3 b = new V3(0.2, 4, -1);
        V3 c = a.cross(b);
        assertEquals(0.0, c.dot(a), 1e-12);
        assertEquals(0.0, c.dot(b), 1e-12);
    }

    @Test
    void unitHasLengthOneAndZeroStaysZero() {
        assertEquals(1.0, new V3(3, -4, 12).unit().length(), 1e-15);
        assertEquals(V3.ZERO, V3.ZERO.unit());
        assertTrue(V3.ZERO.unit().isFinite());
    }

    @Test
    void angleIsStableNearZeroAndPi() {
        V3 a = new V3(1, 0, 0);
        assertEquals(0.0, a.angleTo(new V3(1, 1e-12, 0)), 1e-11);
        assertEquals(Math.PI, a.angleTo(new V3(-1, 1e-12, 0)), 1e-11);
        assertEquals(Math.PI / 2, a.angleTo(new V3(0, 5, 0)), 1e-15);
        assertEquals(0.0, a.angleTo(V3.ZERO), 0.0);
    }

    @Test
    void arrayConversionAndDistance() {
        V3 v = V3.of(new double[] {1, 2, 3});
        assertEquals(new V3(1, 2, 3), v);
        assertEquals(3, v.toArray().length);
        assertEquals(5.0, new V3(1, 1, 1).distanceTo(new V3(4, 5, 1)), 1e-15);
        assertFalse(new V3(Double.NaN, 0, 0).isFinite());
    }
}
