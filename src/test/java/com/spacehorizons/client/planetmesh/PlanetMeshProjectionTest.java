package com.spacehorizons.client.planetmesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;

class PlanetMeshProjectionTest {
    private static final PlanetDefinition EARTH = Planets.EARTH;
    private static final double R = EARTH.radius();

    @Test
    void weightIsZeroNearTheGroundAndNearTheNadir() {
        assertEquals(0.0, PlanetMeshProjection.weight(R, 0.0, 9_000.0, 2_000.0), 0.0);
        assertEquals(0.0, PlanetMeshProjection.weight(R, PlanetMeshProjection.ALTITUDE_START, 9_000.0, 2_000.0), 0.0);
        assertEquals(0.0, PlanetMeshProjection.weight(R, 40_000.0, 1_999.0, 2_000.0), 0.0);
        assertEquals(1.0, PlanetMeshProjection.weight(R, 40_000.0, 2_000.0 + 0.31 * R, 2_000.0), 1e-12);
        assertEquals(1.0, PlanetMeshProjection.weight(R, PlanetMeshProjection.ALTITUDE_FULL, 20_000.0, 2_000.0), 1e-12);
    }

    @Test
    void weightIsMonotonicInAltitudeAndDistance() {
        double previous = -1.0;
        for (double altitude = 0; altitude <= 20_000; altitude += 250) {
            double w = PlanetMeshProjection.weight(R, altitude, 8_000.0, 2_000.0);
            assertTrue(w >= previous - 1e-12);
            assertTrue(w >= 0.0 && w <= 1.0);
            previous = w;
        }
        previous = -1.0;
        for (double arc = 0; arc <= 20_000; arc += 250) {
            double w = PlanetMeshProjection.weight(R, 15_000.0, arc, 2_000.0);
            assertTrue(w >= previous - 1e-12);
            previous = w;
        }
    }

    @Test
    void zeroWeightIsTheAzimuthalChartOfChunksAndDh() {
        double[] xz = new double[2];
        PlanetMeshProjection.terrainPoint(EARTH, 100.0, -50.0, 0.4, 1.0, 0.0, xz);
        assertEquals(100.0 + R * 0.4 * Math.cos(1.0), xz[0], 1e-9);
        assertEquals(-50.0 + R * 0.4 * Math.sin(1.0), xz[1], 1e-9);
    }

    @Test
    void nadirIsAlwaysTheCameraColumn() {
        double[] xz = new double[2];
        for (double w : new double[] {0.0, 0.3, 1.0}) {
            PlanetMeshProjection.terrainPoint(EARTH, 4_321.0, 6_000.0, 0.0, 0.0, w, xz);
            assertEquals(4_321.0, xz[0], 1e-6);
            assertEquals(6_000.0, xz[1], 1e-6);
        }
    }

    @Test
    void fixedChartIsAnIsometryOfTheSphere() {
        // a carta lat/lon é uma esfera fixa: a distância angular entre duas direções é a distância angular
        // entre os pontos lat/lon que elas mostram, qualquer que seja a câmera (nada "nada" ao mover a câmera)
        Random random = new Random(7);
        double[] a = new double[2];
        double[] b = new double[2];
        double[] da = new double[3];
        double[] db = new double[3];
        for (int i = 0; i < 400; i++) {
            double camX = (random.nextDouble() - 0.5) * 60_000.0;
            double camZ = (random.nextDouble() - 0.5) * 2.0 * EARTH.maxLatitudeBlocks();
            double theta1 = random.nextDouble() * 2.6;
            double phi1 = random.nextDouble() * 2.0 * Math.PI;
            double theta2 = random.nextDouble() * 2.6;
            double phi2 = random.nextDouble() * 2.0 * Math.PI;
            PlanetMeshProjection.direction(theta1, phi1, da);
            PlanetMeshProjection.direction(theta2, phi2, db);
            double expected = Math.acos(Math.max(-1, Math.min(1, da[0] * db[0] + da[1] * db[1] + da[2] * db[2])));

            PlanetMeshProjection.terrainPoint(EARTH, camX, camZ, theta1, phi1, 1.0, a);
            PlanetMeshProjection.terrainPoint(EARTH, camX, camZ, theta2, phi2, 1.0, b);
            double lat1 = a[1] / R;
            double lat2 = b[1] / R;
            double dLon = (a[0] - b[0]) / R;
            double cos = Math.sin(lat1) * Math.sin(lat2) + Math.cos(lat1) * Math.cos(lat2) * Math.cos(dLon);
            double actual = Math.acos(Math.max(-1, Math.min(1, cos)));
            assertEquals(expected, actual, 1e-6, "par " + i);
        }
    }

    @Test
    void fixedChartKeepsTheTrueSphericalDistanceWhileThePlaneDoesNot() {
        // longe do equador a distância no plano (usada pela carta azimutal) é maior que o arco real
        double[] xz = new double[2];
        double camX = 0.0;
        double camZ = 9_000.0; // ~49° de latitude
        double theta = 0.6;
        double phi = 0.0; // para leste
        PlanetMeshProjection.terrainPoint(EARTH, camX, camZ, theta, phi, 1.0, xz);
        double lat = xz[1] / R;
        double lon = xz[0] / R;
        double lat0 = camZ / R;
        double cos = Math.sin(lat0) * Math.sin(lat) + Math.cos(lat0) * Math.cos(lat) * Math.cos(lon);
        assertEquals(theta, Math.acos(cos), 1e-9);
        assertTrue(Math.hypot(xz[0] - camX, xz[1] - camZ) > R * theta * 1.1, "no plano o ponto fica mais longe que o arco");
    }

    @Test
    void blendedChartInterpolatesWithoutJumping() {
        double[] a = new double[2];
        double[] b = new double[2];
        double previousX = Double.NaN;
        for (double w = 0.0; w <= 1.0; w += 0.05) {
            PlanetMeshProjection.terrainPoint(EARTH, 30_000.0, 5_000.0, 0.7, 2.2, w, a);
            if (!Double.isNaN(previousX)) {
                assertTrue(Math.abs(a[0] - previousX) < 0.3 * R, "salto em x com w = " + w);
            }
            previousX = a[0];
        }
        // as pontas coincidem com as duas cartas
        PlanetMeshProjection.terrainPoint(EARTH, 30_000.0, 5_000.0, 0.7, 2.2, 0.0, a);
        PlanetMeshProjection.terrainPoint(EARTH, 30_000.0, 5_000.0, 0.7, 2.2, 1.0, b);
        assertEquals(30_000.0 + R * 0.7 * Math.cos(2.2), a[0], 1e-9);
        assertTrue(Math.abs(a[0] - b[0]) < EARTH.circumferenceBlocks(), "pontas devem estar no mesmo ramo ou módulo C");
    }

    // ---- análise do erro de "nadar" (os números da tabela do javadoc de PlanetMeshProjection) ----

    /** Direção azimutal (referencial da câmera) do ponto a (dx, dz) blocos da câmera. */
    private static double[] azimuthal(double dx, double dz) {
        double d = Math.hypot(dx, dz);
        if (d < 1e-9) {
            return new double[] {0, 1, 0};
        }
        double theta = d / R;
        return new double[] {Math.sin(theta) * dx / d, Math.cos(theta), Math.sin(theta) * dz / d};
    }

    private static double[] rotateAboutZ(double[] v, double angle) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        return new double[] {c * v[0] - s * v[1], s * v[0] + c * v[1], v[2]};
    }

    private static double angle(double[] a, double[] b) {
        double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        return Math.acos(Math.max(-1, Math.min(1, dot)));
    }

    /** Pior deslocamento (blocos) de um ponto da calota visível ao mover a câmera D blocos para leste, na carta azimutal. */
    static double azimuthalSwimBlocks(double altitude, double shift) {
        double cap = Math.acos(R / (R + altitude));
        double worst = 0.0;
        for (int i = 1; i <= 40; i++) {
            double theta = 0.95 * cap * i / 40.0;
            for (int k = 0; k < 72; k++) {
                double phi = 2.0 * Math.PI * k / 72.0;
                double dx = R * theta * Math.cos(phi);
                double dz = R * theta * Math.sin(phi);
                double[] before = azimuthal(dx, dz);
                double[] after = azimuthal(dx - shift, dz);
                double alpha = shift / R;
                double error = Math.min(angle(after, rotateAboutZ(before, alpha)), angle(after, rotateAboutZ(before, -alpha)));
                worst = Math.max(worst, error * R);
            }
        }
        return worst;
    }

    @Test
    void azimuthalSwimMatchesTheDocumentedTable() {
        // altitude, deslocamento, erro esperado (blocos)
        double[][] table = {
                {600, 3_000, 113}, {2_000, 3_000, 305}, {5_000, 3_000, 594}, {15_000, 3_000, 1_062}, {40_000, 3_000, 1_415},
                {5_000, 500, 97}, {15_000, 500, 176}, {40_000, 500, 235},
        };
        for (double[] row : table) {
            double error = azimuthalSwimBlocks(row[0], row[1]);
            assertEquals(row[2], error, Math.max(25.0, row[2] * 0.08), "altitude " + row[0] + " deslocamento " + row[1]);
        }
    }

    @Test
    void swimGrowsWithAltitudeAndShift() {
        assertTrue(azimuthalSwimBlocks(15_000, 3_000) > azimuthalSwimBlocks(5_000, 3_000));
        assertTrue(azimuthalSwimBlocks(15_000, 3_000) > azimuthalSwimBlocks(15_000, 500));
        assertTrue(azimuthalSwimBlocks(15_000, 0) < 1.0);
    }
}
