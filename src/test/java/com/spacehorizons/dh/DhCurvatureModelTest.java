package com.spacehorizons.dh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.planet.CurvatureMath;

class DhCurvatureModelTest {
    // mesmos valores de Planets.EARTH (C=65.536, seaLevel=63); sem importar a classe, que puxa Minecraft
    private static final double R = 65_536 / (2 * Math.PI);
    private static final int SEA = 63;

    @Test
    void ratioForEarthIsStableAndPositive() {
        int ratio = DhCurvatureModel.ratioFor(R, SEA);
        assertEquals(615, ratio);
    }

    @Test
    void groundErrorIsSmallUpToFourThousandBlocks() {
        int ratio = DhCurvatureModel.ratioFor(R, SEA);
        double dhR = DhCurvatureModel.dhEarthRadius(ratio);
        double[] ref = new double[3];
        double[] dh = new double[2];
        for (double camAlt : new double[] {64, 2000}) {
            for (double d : new double[] {1000, 4000}) {
                CurvatureMath.curve(R, camAlt, d, -camAlt, 0, ref);
                // ponto de solo: Y absoluto = seaLevel, câmera a camAlt acima dele
                DhCurvatureModel.dhCurve(dhR, SEA, camAlt, d, 0, dh);
                assertEquals(ref[0], dh[0], 1.0, "dx d=" + d + " cam=" + camAlt);
                assertEquals(ref[1], dh[1], 1.0, "dy d=" + d + " cam=" + camAlt);
            }
        }
    }

    @Test
    void elevatedErrorIsBoundedByDhApproximation() {
        // O shader do DH usa phi = d / (R + h) em vez de d / R: o erro cresce com h e d.
        // Medido: ~95 blocos de dy para h=64 e d=16.000. O teste fixa esse teto para detectar regressões.
        int ratio = DhCurvatureModel.ratioFor(R, SEA);
        double dhR = DhCurvatureModel.dhEarthRadius(ratio);
        double[] ref = new double[3];
        double[] dh = new double[2];
        double worst = 0;
        for (double camAlt : new double[] {64, 2000}) {
            for (double h : new double[] {0, 64}) {
                for (double d : new double[] {1000, 4000, 16_000}) {
                    CurvatureMath.curve(R, camAlt, d, h - camAlt, 0, ref);
                    DhCurvatureModel.dhCurve(dhR, SEA, camAlt, d, h, dh);
                    double e = Math.hypot(ref[0] - dh[0], ref[1] - dh[1]);
                    worst = Math.max(worst, e);
                }
            }
        }
        assertTrue(worst < 110, "erro máximo do DH: " + worst);
    }
}
