package com.spacehorizons.client.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceDimension;
import com.spacehorizons.space.SpaceFrame;
import com.spacehorizons.space.SunModel;

/**
 * O {@link SkyObserver} reproduz as contas que o BodyRenderer fazia para o chão de um planeta (BodySky, SpaceFrame,
 * SunModel) e, no espaço aberto, coincide com a Terra vista do spawn dela: é o que garante o céu sem salto na troca.
 */
class SkyObserverTest {
    private static final double[] CLOCKS = {0, 6_000, 18_000, 70_000.5, 123_456.25};
    /** {x, y, z} de uma câmera em cada planeta (longitudes e latitudes variadas, de baixa a alta altitude). */
    private static final double[][] EARTH_POINTS = {{0, 63, 0}, {1_234.5, 5_063, 2_000}, {-9_000, 400, -3_500}, {32_000, 20_000, 6_000}};
    private static final double[][] MOON_POINTS = {{0, 63, 0}, {8_192, 300, 0}, {-3_000, 1_500, 1_000}};

    private static void assertApparent(BodySky.Apparent expected, BodySky.Apparent actual) {
        assertEquals(expected.x(), actual.x(), 1e-9);
        assertEquals(expected.y(), actual.y(), 1e-9);
        assertEquals(expected.z(), actual.z(), 1e-9);
        assertEquals(expected.distance(), actual.distance(), 1e-6);
        // asin(R / d) tem derivada infinita com a câmera quase na superfície (d ≈ R): a diferença de ordem de soma aparece ali
        assertEquals(expected.angularRadius(), actual.angularRadius(), 1e-6);
    }

    private static void assertVec(double[] expected, double[] actual, double eps) {
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], eps, "componente " + i);
        }
    }

    @Test
    void planetObserverReproducesBodySkyFromDimension() {
        for (double clock : CLOCKS) {
            for (SolarSystem.Body home : SolarSystem.BODIES) {
                double[][] points = home == SolarSystem.EARTH ? EARTH_POINTS : MOON_POINTS;
                for (double[] p : points) {
                    SkyObserver observer = SkyObserver.forPlanet(home, clock, p[0], p[1], p[2]);
                    for (SolarSystem.Body target : SolarSystem.BODIES) {
                        assertApparent(BodySky.fromDimension(home, clock, p[0], p[1], p[2], target), observer.apparent(target));
                    }
                }
            }
        }
    }

    @Test
    void planetObserverReproducesMeshToLocalAndTheLocalSun() {
        for (double clock : CLOCKS) {
            for (SolarSystem.Body home : SolarSystem.BODIES) {
                double[][] points = home == SolarSystem.EARTH ? EARTH_POINTS : MOON_POINTS;
                for (double[] p : points) {
                    SkyObserver observer = SkyObserver.forPlanet(home, clock, p[0], p[1], p[2]);
                    for (SolarSystem.Body target : SolarSystem.BODIES) {
                        double[] expected = new double[9];
                        double[] actual = new double[9];
                        SpaceFrame.meshToLocal(home, clock, p[0], p[2], target, expected);
                        observer.meshToLocal(target, actual);
                        assertVec(expected, actual, 1e-9);
                    }
                    double[] sun = new double[3];
                    double[] expectedSun = new double[3];
                    observer.sun(sun);
                    SunModel.localDirection(home, p[0], p[2], clock, expectedSun);
                    assertVec(expectedSun, sun, 1e-9);
                }
            }
        }
    }

    @Test
    void planetObserverIsAtTheSpacePositionOfTheCamera() {
        for (double clock : CLOCKS) {
            double[] expected = new double[3];
            SpaceFrame.toSpace(SolarSystem.MOON, clock, 8_192, 300, 0, expected);
            SkyObserver observer = SkyObserver.forPlanet(SolarSystem.MOON, clock, 8_192, 300, 0);
            assertVec(expected, observer.pos(), 1e-9);
            assertEquals(SolarSystem.MOON, observer.home());
        }
    }

    /**
     * O espaço coincide com a Terra vista do spawn dela (lon 0, lat 0): na mesma altitude e no mesmo instante, a Lua, a Terra, o sol
     * e a orientação das esferas são idênticos nos dois lados da troca de dimensão.
     */
    @Test
    void spaceObserverCoincidesWithTheEarthSeenFromItsSpawn() {
        for (double clock : CLOCKS) {
            for (double altitude : new double[] {500, 4_900, 5_000, 12_000, 30_000}) {
                double y = SolarSystem.EARTH.planet().seaLevel() + altitude;
                SkyObserver earth = SkyObserver.forPlanet(SolarSystem.EARTH, clock, 0, y, 0);
                double[] s = new double[3];
                double[] dim = new double[3];
                SpaceFrame.toSpace(SolarSystem.EARTH, clock, 0, y, 0, s);
                SpaceDimension.fromSpace(s[0], s[1], s[2], dim);
                SkyObserver space = SkyObserver.forSpace(clock, dim[0], dim[1], dim[2]);
                assertNull(space.home());

                for (SolarSystem.Body target : SolarSystem.BODIES) {
                    assertApparent(earth.apparent(target), space.apparent(target));
                    double[] a = new double[9];
                    double[] b = new double[9];
                    earth.meshToLocal(target, a);
                    space.meshToLocal(target, b);
                    assertVec(a, b, 1e-9);
                }
                double[] sunEarth = new double[3];
                double[] sunSpace = new double[3];
                earth.sun(sunEarth);
                space.sun(sunSpace);
                assertVec(sunEarth, sunSpace, 1e-9);
            }
        }
    }

    @Test
    void spaceSunMatchesTheVanillaSunAtTheEarthSpawn() {
        double[] sun = new double[3];
        // meio-dia no tick 6000: o sol a pino; meia-noite no 18000: no nadir; o nascer (tick 0) é a leste e o pôr (12000) a oeste
        SkyObserver.forSpace(6_000, 0, 30_000, 0).sun(sun);
        assertVec(new double[] {0, 1, 0}, sun, 1e-9);
        SkyObserver.forSpace(18_000, 0, 30_000, 0).sun(sun);
        assertVec(new double[] {0, -1, 0}, sun, 1e-9);
        SkyObserver.forSpace(0, 0, 30_000, 0).sun(sun);
        assertTrue(sun[0] > 0.8 && Math.abs(sun[1]) < 0.6, "o nascer é a leste, perto do horizonte: " + sun[0] + ", " + sun[1]);
        SkyObserver.forSpace(12_000, 0, 30_000, 0).sun(sun);
        assertTrue(sun[0] < -0.8 && Math.abs(sun[1]) < 0.6, "o pôr é a oeste, perto do horizonte: " + sun[0] + ", " + sun[1]);
    }

    @Test
    void spaceObserverSeesBothBodiesAsOtherBodies() {
        // meio do caminho entre os centros da Terra e da Lua (a 60.000 um do outro), em vários instantes
        for (double clock : CLOCKS) {
            double[] m = new double[3];
            SolarSystem.MOON.positionAt(clock, m);
            double[] dim = new double[3];
            SpaceDimension.fromSpace(m[0] / 2, m[1] / 2, m[2] / 2, dim);
            SkyObserver observer = SkyObserver.forSpace(clock, dim[0], dim[1], dim[2]);
            BodySky.Apparent earth = observer.apparent(SolarSystem.EARTH);
            BodySky.Apparent moon = observer.apparent(SolarSystem.MOON);
            assertEquals(30_000, earth.distance(), 1e-6);
            assertEquals(30_000, moon.distance(), 1e-6);
            assertEquals(Math.asin(SolarSystem.EARTH.planet().radius() / 30_000.0), earth.angularRadius(), 1e-12);
            assertEquals(Math.asin(SolarSystem.MOON.planet().radius() / 30_000.0), moon.angularRadius(), 1e-12);
            // em direções opostas
            assertEquals(-moon.x(), earth.x(), 1e-9);
            assertEquals(-moon.y(), earth.y(), 1e-9);
            assertEquals(-moon.z(), earth.z(), 1e-9);
        }
    }
}
