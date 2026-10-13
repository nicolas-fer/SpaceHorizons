package com.spacehorizons.planet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CurvatureMathTest {
    @Test
    void groundPointStaysOnSphere() {
        double radius = 10_000;
        double camAlt = 300;
        double[] out = new double[3];
        // ponto no nível do mar a 4.000 blocos: depois da curva deve ficar a exatamente R do centro
        CurvatureMath.curve(radius, camAlt, 4_000, -camAlt, 0, out);
        double cx = out[0];
        double cy = out[1] + camAlt + radius;
        assertEquals(radius, Math.hypot(cx, cy), 1e-6);
    }

    @Test
    void originIsUnchanged() {
        double[] out = new double[3];
        CurvatureMath.curve(10_000, 64, 0, 5, 0, out);
        assertEquals(5, out[1], 1e-9);
    }
}
