package com.spacehorizons.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Random;

import org.junit.jupiter.api.Test;

class SpaceDimensionTest {
    @Test
    void typeIsTheSpaceDimensionOfTheMod() {
        assertEquals("spacehorizons", SpaceDimension.TYPE.getNamespace());
        assertEquals("space", SpaceDimension.TYPE.getPath());
        assertNotNull(SpaceDimension.LOCAL_TO_SPACE);
        assertEquals(20_000.0, SpaceDimension.Y_BIAS, 0.0);
    }

    @Test
    void localToSpaceIsTheLocalBasisOfTheEarthSpawn() {
        // colunas da matriz = leste, cima, norte do spawn da Terra (SpaceFrame.localBasis(EARTH, 0, 0))
        double[] basis = new double[9];
        SpaceFrame.localBasis(SolarSystem.EARTH, 0, 0, basis);
        double[] m = SpaceDimension.LOCAL_TO_SPACE;
        for (int axis = 0; axis < 3; axis++) {
            for (int row = 0; row < 3; row++) {
                assertEquals(basis[axis * 3 + row], m[row * 3 + axis], 1e-15, "eixo " + axis + " linha " + row);
            }
        }
    }

    @Test
    void localToSpaceIsAProperRotation() {
        double[] m = SpaceDimension.LOCAL_TO_SPACE;
        double det = m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6]) + m[2] * (m[3] * m[7] - m[4] * m[6]);
        assertEquals(1.0, det, 0.0);
    }

    @Test
    void mappingFollowsTheDesign() {
        // (x,y,z)_dim = (sz, sx + 20000, sy)
        double[] dim = new double[3];
        SpaceDimension.fromSpace(10, 20, 30, dim);
        assertEquals(30, dim[0], 0.0);
        assertEquals(20_010, dim[1], 0.0);
        assertEquals(20, dim[2], 0.0);
        double[] space = new double[3];
        SpaceDimension.toSpace(30, 20_010, 20, space);
        assertEquals(10, space[0], 0.0);
        assertEquals(20, space[1], 0.0);
        assertEquals(30, space[2], 0.0);
    }

    @Test
    void toSpaceAndFromSpaceAreInverseAndAgreeWithTheMatrix() {
        Random rng = new Random(11);
        for (int i = 0; i < 200; i++) {
            double x = rng.nextGaussian() * 50_000;
            double y = 20_000 + rng.nextGaussian() * 40_000;
            double z = rng.nextGaussian() * 50_000;
            double[] space = new double[3];
            SpaceDimension.toSpace(x, y, z, space);
            double[] back = new double[3];
            SpaceDimension.fromSpace(space[0], space[1], space[2], back);
            assertEquals(x, back[0], 1e-9);
            assertEquals(y, back[1], 1e-9);
            assertEquals(z, back[2], 1e-9);
            // espaço = M · (local − viés)
            double[] m = SpaceDimension.LOCAL_TO_SPACE;
            double ly = y - SpaceDimension.Y_BIAS;
            // o viés entra só em Y local, que vira o X do espaço
            assertEquals(m[0] * x + m[1] * ly + m[2] * z, space[0], 1e-9);
            assertEquals(m[3] * x + m[4] * ly + m[5] * z, space[1], 1e-9);
            assertEquals(m[6] * x + m[7] * ly + m[8] * z, space[2], 1e-9);
        }
    }

    @Test
    void theEarthSpawnVerticalStaysVerticalInTheSpaceDimension() {
        // subir a partir do spawn da Terra (altura h sobre o raio) = +Y na dimensão, sem deslocamento em X e Z
        double radius = SolarSystem.EARTH.planet().radius();
        double[] dim = new double[3];
        SpaceDimension.fromSpace(radius + 5_000, 0, 0, dim);
        assertEquals(0, dim[0], 0.0);
        assertEquals(radius + 25_000, dim[1], 1e-9);
        assertEquals(0, dim[2], 0.0);
    }
}
