package com.spacehorizons.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class InertialFrameTest {
    private static final double[] CLOCKS = {0, 1, 999.5, 6000, 12_345.25, 18_000, 23_999, 47_000.75, 114_000, 500_000.5};

    @Test
    void roundTripIsTheIdentityForPositionsAndVelocities() {
        Random rng = new Random(11);
        for (double clock : CLOCKS) {
            for (int i = 0; i < 20; i++) {
                double[] r = {rng.nextGaussian() * 1e5, rng.nextGaussian() * 1e5, rng.nextGaussian() * 1e5};
                double[] v = {rng.nextGaussian() * 30, rng.nextGaussian() * 30, rng.nextGaussian() * 30};
                double[] e = new double[3];
                double[] back = new double[3];
                InertialFrame.toEarthFixed(clock, r[0], r[1], r[2], e);
                InertialFrame.toInertial(clock, e[0], e[1], e[2], back);
                for (int k = 0; k < 3; k++) {
                    assertEquals(r[k], back[k], 1e-7);
                }
                double[] ve = new double[3];
                double[] vBack = new double[3];
                InertialFrame.velocityToEarthFixed(clock, 1.0, r[0], r[2], v[0], v[1], v[2], ve);
                InertialFrame.velocityToInertial(clock, 1.0, r[0], r[2], ve[0], ve[1], ve[2], vBack);
                for (int k = 0; k < 3; k++) {
                    assertEquals(v[k], vBack[k], 1e-9);
                }
            }
        }
    }

    @Test
    void sunIsExactlyTheSameInEarthFixedAxesAndFixedInInertialOnes() {
        double[] sunE = new double[3];
        double[] sunI = new double[3];
        for (double clock : CLOCKS) {
            SunModel.directionInSpace(clock, sunE);
            // o sol de E é o de sempre: (cos a, 0, -sen a) com a = SunModel.angle
            double a = SunModel.angle(clock);
            assertEquals(Math.cos(a), sunE[0], 1e-15);
            assertEquals(0.0, sunE[1], 0.0);
            assertEquals(-Math.sin(a), sunE[2], 1e-15);
            // em I ele não se mexe: fica em −X a qualquer hora
            InertialFrame.toInertial(clock, sunE[0], sunE[1], sunE[2], sunI);
            assertEquals(-1.0, sunI[0], 1e-12, "clock " + clock);
            assertEquals(0.0, sunI[1], 1e-12);
            assertEquals(0.0, sunI[2], 1e-12);
        }
    }

    @Test
    void earthAndInertialAxesCoincideAtMidnightOfDayZero() {
        double[] out = new double[3];
        InertialFrame.toEarthFixed(18_000, 3.0, 4.0, 5.0, out);
        assertEquals(3.0, out[0], 1e-9);
        assertEquals(4.0, out[1], 1e-12);
        assertEquals(5.0, out[2], 1e-9);
    }

    @Test
    void earthRotatesEastwardWithAMeanPeriodOfOneDay() {
        // um ponto fixo de I anda para o OESTE em E (o sol nasce a leste e se põe a oeste): em lon 0, +Z é o leste e o ponto
        // que estava em +X vai para −Z
        double[] a = new double[3];
        double[] b = new double[3];
        InertialFrame.toEarthFixed(18_000, 1, 0, 0, a);
        InertialFrame.toEarthFixed(18_100, 1, 0, 0, b);
        assertTrue(b[2] < a[2], "o ponto fixo de I deveria andar para o oeste (−Z) em E");
        // a taxa média é 2π/24.000 (uma volta por dia) e o ângulo total em um dia é 2π
        double sum = 0;
        int n = 24_000;
        for (int t = 0; t < n; t++) {
            sum += InertialFrame.rate(3_000.5 + t);
        }
        assertEquals(2.0 * Math.PI, sum, 1e-3);
        assertEquals(2.0 * Math.PI / 24_000.0, sum / n, 1e-8);
    }

    @Test
    void rateIsTheDerivativeOfTheAngleAndStaysWithinTheVanillaCurve() {
        double min = Double.MAX_VALUE;
        double max = 0;
        for (double clock = 0; clock < 24_000; clock += 37.3) {
            double h = 0.01;
            double numeric = (unwrap(InertialFrame.angle(clock + h) - InertialFrame.angle(clock - h))) / (2 * h);
            assertEquals(numeric, InertialFrame.rate(clock), 1e-9, "clock " + clock);
            min = Math.min(min, InertialFrame.rate(clock));
            max = Math.max(max, InertialFrame.rate(clock));
        }
        double mean = 2.0 * Math.PI / 24_000.0;
        // a curva do sol do vanilla: ~0,67 vezes a média ao meio-dia e ~1,19 à meia-noite
        assertEquals(0.666, min / mean, 0.02);
        assertEquals(1.19, max / mean, 0.03);
    }

    @Test
    void velocityConversionIsTheDerivativeOfThePositionConversion() {
        Random rng = new Random(5);
        for (double clock : CLOCKS) {
            double[] r = {rng.nextGaussian() * 1e5, rng.nextGaussian() * 1e5, rng.nextGaussian() * 1e5};
            double[] v = {rng.nextGaussian() * 30, rng.nextGaussian() * 30, rng.nextGaussian() * 30};
            double[] ve = new double[3];
            InertialFrame.velocityToEarthFixed(clock, 1.0, r[0], r[2], v[0], v[1], v[2], ve);
            double h = 0.01;
            double[] a = new double[3];
            double[] b = new double[3];
            InertialFrame.toEarthFixed(clock - h, r[0] - v[0] * h, r[1] - v[1] * h, r[2] - v[2] * h, a);
            InertialFrame.toEarthFixed(clock + h, r[0] + v[0] * h, r[1] + v[1] * h, r[2] + v[2] * h, b);
            for (int k = 0; k < 3; k++) {
                assertEquals((b[k] - a[k]) / (2 * h), ve[k], 5e-3, "clock " + clock);
            }
            // com o relógio parado a Terra não gira: só o giro do vetor
            double[] still = new double[3];
            InertialFrame.velocityToEarthFixed(clock, 0.0, r[0], r[2], v[0], v[1], v[2], still);
            double[] rotated = new double[3];
            InertialFrame.toEarthFixed(clock, v[0], v[1], v[2], rotated);
            for (int k = 0; k < 3; k++) {
                assertEquals(rotated[k], still[k], 1e-9);
            }
        }
    }

    /** Leva uma diferença de ângulos a (−π, π]: o ângulo do sol dá a volta em 2π. */
    private static double unwrap(double d) {
        double w = d % (2.0 * Math.PI);
        if (w > Math.PI) {
            w -= 2.0 * Math.PI;
        } else if (w <= -Math.PI) {
            w += 2.0 * Math.PI;
        }
        return w;
    }
}
