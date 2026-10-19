package com.spacehorizons.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** As coordenadas da dimensão de espaço aberto: viés de Y, eixos iguais aos da Terra no spawn e a ida e volta. */
class SpaceDimensionSkyTest {
    private static final double EPS = 1e-9;

    @Test
    void typeIsTheSpaceDimensionOfTheMod() {
        assertEquals("spacehorizons:space", SpaceDimension.TYPE.toString());
    }

    @Test
    void toSpaceAndFromSpaceAreInverses() {
        double[][] points = {{0, 0, 0}, {123.5, 20_000, -77.25}, {-65_000, 80_000, 4_000}, {5_000, -40_000, 60_000}};
        double[] space = new double[3];
        double[] back = new double[3];
        for (double[] p : points) {
            SpaceDimension.toSpace(p[0], p[1], p[2], space);
            SpaceDimension.fromSpace(space[0], space[1], space[2], back);
            assertEquals(p[0], back[0], EPS);
            assertEquals(p[1], back[1], EPS);
            assertEquals(p[2], back[2], EPS);
        }
    }

    @Test
    void theEarthCentreSitsAtTheBiasInY() {
        double[] dim = new double[3];
        SpaceDimension.fromSpace(0, 0, 0, dim);
        assertEquals(0, dim[0], EPS);
        assertEquals(SpaceDimension.Y_BIAS, dim[1], EPS);
        assertEquals(0, dim[2], EPS);
    }

    /** Os eixos da dimensão são a base local da Terra no spawn: o que torna o sol e as estrelas do vanilla corretos. */
    @Test
    void localToSpaceHasTheEarthSpawnBasisAsColumns() {
        double[] basis = new double[9]; // linhas: leste, cima, norte, nos eixos fixos na Terra (= os do espaço: pose identidade)
        SpaceFrame.localBasis(SolarSystem.EARTH, 0, 0, basis);
        double[] m = SpaceDimension.LOCAL_TO_SPACE;
        for (int axis = 0; axis < 3; axis++) {
            for (int component = 0; component < 3; component++) {
                assertEquals(basis[axis * 3 + component], m[component * 3 + axis], EPS, "eixo " + axis + " componente " + component);
            }
        }
    }

    @Test
    void localToSpaceIsARotation() {
        double[] m = SpaceDimension.LOCAL_TO_SPACE;
        double det = m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6]) + m[2] * (m[3] * m[7] - m[4] * m[6]);
        assertEquals(1.0, det, EPS);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double dot = 0;
                for (int k = 0; k < 3; k++) {
                    dot += m[i * 3 + k] * m[j * 3 + k];
                }
                assertEquals(i == j ? 1.0 : 0.0, dot, EPS);
            }
        }
    }

    /** Um ponto qualquer da vertical do spawn da Terra é o mesmo ponto do espaço dos dois lados, a menos do viés. */
    @Test
    void theSpawnVerticalOfTheEarthIsTheSameLineInSpace() {
        double[] fromEarth = new double[3];
        double[] dim = new double[3];
        for (double y : new double[] {63, 1_000, 5_063, 30_000}) {
            SpaceFrame.toSpace(SolarSystem.EARTH, 0, 0, y, 0, fromEarth);
            SpaceDimension.fromSpace(fromEarth[0], fromEarth[1], fromEarth[2], dim);
            // x = z = 0 e y = raio + altitude + viés
            assertEquals(0, dim[0], 1e-6);
            assertEquals(0, dim[2], 1e-6);
            assertEquals(SolarSystem.EARTH.planet().radius() + (y - SolarSystem.EARTH.planet().seaLevel()) + SpaceDimension.Y_BIAS, dim[1], 1e-6);
        }
    }

    /**
     * Documenta a faixa de Y que o contrato produz: perto da Terra fica folgada (4.570 a 35.430), mas a órbita da Lua passa
     * por x do espaço de -60.000 a +60.000, e Y = x do espaço + viés vai a quase -40.000 (por isso o vácuo do mundo é
     * desligado no espaço: ver EntityMixin).
     */
    @Test
    void theMoonOrbitReachesNegativeYInTheSpaceDimension() {
        double[] m = new double[3];
        double[] dim = new double[3];
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (double clock = 0; clock < 8 * SunModel.DAY_TICKS; clock += 500) {
            SolarSystem.MOON.positionAt(clock, m);
            SpaceDimension.fromSpace(m[0], m[1], m[2], dim);
            minY = Math.min(minY, dim[1]);
            maxY = Math.max(maxY, dim[1]);
        }
        assertTrue(minY < -570_000, "mínimo " + minY);
        assertTrue(maxY > 610_000, "máximo " + maxY);
        // acima do spawn da Terra a altitude de saída (5.000) cai em Y positivo; o lado do nadir da Terra (10x maior que antes)
        // fica em Y negativo, como a Lua: o vácuo do mundo está desligado na dimensão (ver EntityMixin)
        SpaceDimension.fromSpace(SolarSystem.EARTH.planet().radius() + 5_000, 0, 0, dim);
        assertTrue(dim[1] > SpaceDimension.Y_BIAS + 5_000);
    }
}
