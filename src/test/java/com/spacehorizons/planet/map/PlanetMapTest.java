package com.spacehorizons.planet.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.spacehorizons.client.planetmesh.PlanetMeshGeometry;
import com.spacehorizons.network.PlanetMapChunks;
import com.spacehorizons.network.PlanetMapPayload;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetMath;
import com.spacehorizons.planet.PlanetTerrain;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.SurfaceType;
import com.spacehorizons.planet.terrain.SphericalTerrain;

class PlanetMapTest {
    private static final PlanetDefinition EARTH = Planets.EARTH;
    private static final int C = EARTH.circumferenceBlocks();
    private static final int WIDTH = 256;
    private static final String FINGERPRINT = "test.generator";

    /** Mapa de teste preenchido com o terreno analítico nos nós (faz as vezes do gerador real). */
    private static PlanetMap sample(long seed) {
        PlanetTerrain source = new SphericalTerrain(EARTH);
        PlanetMap map = new PlanetMap(EARTH, seed, WIDTH, FINGERPRINT);
        for (int row = 0; row < map.height(); row++) {
            for (int column = 0; column < map.width(); column++) {
                int x = (int) Math.round(map.nodeX(column));
                int z = (int) Math.round(map.nodeZ(row));
                map.set(column, row, source.surfaceHeight(x, z), source.surfaceColor(x, z), source.isWater(x, z), source.surfaceType(x, z));
            }
        }
        return map;
    }

    @Test
    void roundTripPreservesEverything() throws IOException {
        PlanetMap map = sample(42L);
        byte[] bytes = map.serialize();
        PlanetMap back = PlanetMap.deserialize(EARTH, bytes);
        assertTrue(map.sameContent(back));
        assertEquals(42L, back.seed());
        assertEquals(WIDTH, back.width());
        assertEquals(map.height(), back.height());
        assertEquals(FINGERPRINT, back.fingerprint());
        for (int row = 0; row < map.height(); row += 7) {
            for (int column = 0; column < map.width(); column += 5) {
                assertEquals(map.heightAtNode(column, row), back.heightAtNode(column, row));
                assertEquals(map.colorAtNode(column, row), back.colorAtNode(column, row));
                assertEquals(map.waterAtNode(column, row), back.waterAtNode(column, row));
                assertEquals(map.typeAtNode(column, row), back.typeAtNode(column, row));
            }
        }
        assertArrayEquals(bytes, back.serialize());
    }

    @Test
    void headerValidatesSeedSizeAndGenerator() {
        byte[] bytes = sample(7L).serialize();
        PlanetMap.Header header = PlanetMap.readHeader(bytes);
        assertNotNull(header);
        assertTrue(header.matches(EARTH, 7L, WIDTH, FINGERPRINT));
        assertFalse(header.matches(EARTH, 8L, WIDTH, FINGERPRINT), "outra semente");
        assertFalse(header.matches(EARTH, 7L, WIDTH * 2, FINGERPRINT), "outro tamanho");
        assertFalse(header.matches(EARTH, 7L, WIDTH, "outro.gerador"), "outro gerador");
        assertNull(PlanetMap.readHeader(new byte[] {1, 2, 3}));
        assertNull(PlanetMap.readHeader(new byte[64]), "magia errada");
    }

    @Test
    void deserializeRejectsGarbageAndTruncatedData() {
        byte[] bytes = sample(1L).serialize();
        assertThrows(IOException.class, () -> PlanetMap.deserialize(EARTH, new byte[100]));
        byte[] truncated = Arrays.copyOf(bytes, bytes.length / 2);
        assertThrows(IOException.class, () -> PlanetMap.deserialize(EARTH, truncated));
        // a Lua tem outra circunferência: a largura do arquivo não encaixa no mesmo número de linhas
        assertThrows(IOException.class, () -> PlanetMap.deserialize(Planets.MOON, bytes));
    }

    @Test
    void interpolationIsExactlyPeriodicInX() {
        PlanetMap map = sample(3L);
        Random rnd = new Random(5);
        for (int i = 0; i < 5_000; i++) {
            double x = rnd.nextDouble() * 3 * C - 1.5 * C;
            double z = (rnd.nextDouble() * 2 - 1) * EARTH.maxLatitudeBlocks();
            double h = map.heightAt(x, z);
            assertEquals(h, map.heightAt(x + C, z), 1.0e-6, "x=" + x + " z=" + z);
            assertEquals(h, map.heightAt(x - 2 * C, z), 1.0e-6);
            assertEquals(map.colorAt(x, z), map.colorAt(x + C, z));
            assertEquals(map.waterAt(x, z), map.waterAt(x - C, z));
            assertEquals(map.typeAt(x, z), map.typeAt(x + C, z));
        }
    }

    @Test
    void noSeamAcrossTheWrapColumn() {
        PlanetMap map = sample(11L);
        double eps = 0.01;
        for (int row = 0; row < map.height(); row += 3) {
            double z = map.nodeZ(row);
            if (z > EARTH.maxLatitudeBlocks()) {
                continue;
            }
            // pelos dois lados da costura x = ±C/2 a diferença é de uma fração de célula, nunca um degrau
            double left = map.heightAt(C / 2.0 - eps, z);
            double right = map.heightAt(-C / 2.0 + eps, z);
            assertEquals(left, right, 0.5, "costura na linha " + row);
            // a última coluna é vizinha da primeira: o meio entre as duas é a média (interpolação linear)
            double last = map.heightAtNode(map.width() - 1, row);
            double first = map.heightAtNode(0, row);
            double mid = map.heightAt(map.nodeX(map.width() - 1) + map.cellSize() / 2, z);
            assertEquals((last + first) / 2.0, mid, 1.0e-9);
        }
    }

    @Test
    void queriesAtNodesReturnTheNodeValues() {
        PlanetMap map = sample(13L);
        for (int row = 0; row < map.height(); row += 11) {
            if (map.nodeZ(row) > EARTH.maxLatitudeBlocks()) {
                continue;
            }
            for (int column = 0; column < map.width(); column += 13) {
                double x = map.nodeX(column);
                double z = map.nodeZ(row);
                assertEquals(map.heightAtNode(column, row), map.heightAt(x, z), 1.0e-9);
                assertEquals(map.colorAtNode(column, row), map.colorAt(x, z));
                assertEquals(map.typeAtNode(column, row), map.typeAt(x, z));
            }
        }
    }

    @Test
    void bilinearMidpointIsTheAverageOfTheFourNodes() {
        PlanetMap map = new PlanetMap(EARTH, 0L, WIDTH, FINGERPRINT);
        int c = 10;
        int r = 20;
        map.set(c, r, 100, 0x000000, false, SurfaceType.PLAINS);
        map.set(c + 1, r, 120, 0x200000, false, SurfaceType.PLAINS);
        map.set(c, r + 1, 140, 0x002000, false, SurfaceType.PLAINS);
        map.set(c + 1, r + 1, 160, 0x000020, false, SurfaceType.PLAINS);
        double x = map.nodeX(c) + map.cellSize() / 2;
        double z = map.nodeZ(r) + map.cellSize() / 2;
        assertEquals(130.0, map.heightAt(x, z), 1.0e-9);
        assertEquals(0x080808, map.colorAt(x, z));
    }

    @Test
    void latitudeIsClampedToTheMapEdge() {
        PlanetMap map = sample(17L);
        int maxLat = EARTH.maxLatitudeBlocks();
        assertEquals(-maxLat, map.nodeZ(0), 1.0e-9, "primeira linha em -maxLat");
        assertTrue(map.nodeZ(map.height() - 1) >= maxLat, "última linha cobre +maxLat");
        assertTrue(map.nodeZ(map.height() - 2) < maxLat, "sem linha sobrando");
        assertEquals(PlanetMap.rowsFor(EARTH, WIDTH), map.height());
        for (double x : new double[] {-30_000, -123.4, 0, 5_000.5, 32_000}) {
            assertEquals(map.heightAt(x, maxLat), map.heightAt(x, maxLat + 5_000), 1.0e-9);
            assertEquals(map.heightAt(x, -maxLat), map.heightAt(x, -maxLat - 123_456), 1.0e-9);
            assertEquals(map.colorAt(x, maxLat), map.colorAt(x, 10 * maxLat));
            assertEquals(map.waterAt(x, -maxLat), map.waterAt(x, -9 * maxLat));
        }
        // dentro dos limites ainda varia: o clamp não achata o mapa
        double a = map.heightAt(1_000, -maxLat + 1_000);
        double b = map.heightAt(1_000, maxLat - 1_000);
        double c = map.heightAt(1_000, 0);
        assertTrue(a != b || b != c, "o mapa tem relevo");
    }

    @Test
    void widthMustDivideTheCircumference() {
        assertThrows(IllegalArgumentException.class, () -> new PlanetMap(EARTH, 0L, 1000, FINGERPRINT));
        assertThrows(IllegalArgumentException.class, () -> new PlanetMap(EARTH, 0L, 4, FINGERPRINT));
    }

    @Test
    void terrainFallsBackUntilTheMapArrivesAndBumpsVersion() {
        PlanetTerrain analytic = new SphericalTerrain(EARTH);
        PlanetMapTerrain terrain = new PlanetMapTerrain(EARTH, analytic);
        assertFalse(terrain.hasMap());
        int version = terrain.version();
        assertEquals(analytic.surfaceHeight(1234, -2345), terrain.surfaceHeight(1234, -2345));
        assertEquals(analytic.surfaceType(1234, -2345), terrain.surfaceType(1234, -2345));

        PlanetMap map = new PlanetMap(EARTH, 0L, WIDTH, FINGERPRINT);
        for (int row = 0; row < map.height(); row++) {
            for (int column = 0; column < map.width(); column++) {
                map.set(column, row, 200, 0x123456, true, SurfaceType.OCEAN);
            }
        }
        terrain.setMap(map);
        assertTrue(terrain.hasMap());
        assertNotEquals(version, terrain.version());
        assertEquals(200, terrain.surfaceHeight(1234, -2345));
        assertEquals(0x123456, terrain.surfaceColor(-30_000, 100));
        assertTrue(terrain.isWater(40_000, 0), "x fora de [-C/2, C/2) é enrolado");
        assertEquals(SurfaceType.OCEAN, terrain.surfaceType(1, 1));

        int withMap = terrain.version();
        terrain.setMap(null);
        assertFalse(terrain.hasMap());
        assertNotEquals(withMap, terrain.version());
        assertEquals(analytic.surfaceHeight(1234, -2345), terrain.surfaceHeight(1234, -2345));
    }

    @Test
    void terrainAnswersAreExactlyPeriodicInX() {
        PlanetMapTerrain terrain = new PlanetMapTerrain(EARTH, new SphericalTerrain(EARTH));
        terrain.setMap(sample(23L));
        Random rnd = new Random(9);
        for (int i = 0; i < 5_000; i++) {
            int x = rnd.nextInt(2 * C) - C;
            int z = rnd.nextInt(2 * EARTH.maxLatitudeBlocks()) - EARTH.maxLatitudeBlocks();
            assertEquals(terrain.surfaceHeight(x, z), terrain.surfaceHeight(x + C, z));
            assertEquals(terrain.surfaceColor(x, z), terrain.surfaceColor(x - C, z));
            assertEquals(terrain.isWater(x, z), terrain.isWater(x + 2 * C, z));
            assertEquals(PlanetMath.wrapX(EARTH, x), PlanetMath.wrapX(EARTH, x + C));
        }
    }

    @Test
    void mapSurvivesChunkedTransport() throws IOException {
        byte[] bytes = sample(29L).serialize();
        List<PlanetMapPayload> pieces = PlanetMapChunks.split("spacehorizons:earth", bytes);
        for (PlanetMapPayload piece : pieces) {
            assertTrue(piece.data().length <= PlanetMapPayload.MAX_CHUNK);
        }
        PlanetMapChunks.Assembler assembler = new PlanetMapChunks.Assembler();
        byte[] complete = null;
        for (int i = 0; i < pieces.size(); i++) {
            byte[] result = assembler.accept(pieces.get(i));
            if (i < pieces.size() - 1) {
                assertNull(result);
            } else {
                complete = result;
            }
        }
        assertNotNull(complete);
        assertArrayEquals(bytes, complete);
        assertTrue(PlanetMap.deserialize(EARTH, complete).sameContent(PlanetMap.deserialize(EARTH, bytes)));
    }

    @Test
    void assemblerDropsOutOfOrderPiecesAndRestartsOnIndexZero() {
        byte[] bytes = new byte[PlanetMapPayload.MAX_CHUNK * 3 + 10];
        new Random(2).nextBytes(bytes);
        List<PlanetMapPayload> pieces = PlanetMapChunks.split("spacehorizons:earth", bytes);
        assertEquals(4, pieces.size());
        PlanetMapChunks.Assembler assembler = new PlanetMapChunks.Assembler();
        assertNull(assembler.accept(pieces.get(0)));
        assertNull(assembler.accept(pieces.get(2)), "pulou o pedaço 1: descarta");
        assertNull(assembler.accept(pieces.get(3)), "montagem já descartada");
        // recomeça do zero e fecha normalmente
        byte[] complete = null;
        for (PlanetMapPayload piece : pieces) {
            complete = assembler.accept(piece);
        }
        assertArrayEquals(bytes, complete);
    }

    @Test
    void emptyDataStillProducesOnePiece() {
        List<PlanetMapPayload> pieces = PlanetMapChunks.split("x", new byte[0]);
        assertEquals(1, pieces.size());
        assertArrayEquals(new byte[0], new PlanetMapChunks.Assembler().accept(pieces.get(0)));
    }

    @Test
    void orbitalVertexUsesMapColorAndWaterInsteadOfThePalette() {
        PlanetMap map = new PlanetMap(EARTH, 0L, WIDTH, FINGERPRINT);
        for (int row = 0; row < map.height(); row++) {
            for (int column = 0; column < map.width(); column++) {
                boolean sea = column >= WIDTH / 2;
                map.set(column, row, sea ? 20 : 100, sea ? 0x2244AA : 0xAA5511, sea, sea ? SurfaceType.OCEAN : SurfaceType.DESERT);
            }
        }
        PlanetMapTerrain terrain = new PlanetMapTerrain(EARTH, new SphericalTerrain(EARTH));
        PlanetMeshGeometry.Palette palette = new PlanetMeshGeometry.Palette(new int[SurfaceType.values().length], 0x3F76E4, 0x1A3A8C);
        double[] height = new double[1];

        // sem mapa vale a paleta (zeros) e o terreno de reserva
        int fallback = PlanetMeshGeometry.shadeVertex(terrain, palette, -20_000, 0, new double[0], 0, height);
        assertNotEquals(0xAA5511, fallback & 0xFFFFFF);

        terrain.setMap(map);
        int land = PlanetMeshGeometry.shadeVertex(terrain, palette, -20_000, 0, new double[0], 0, height);
        assertEquals(0xAA5511, land & 0xFFFFFF, "cor da terra vem do mapa");
        assertEquals(0, land >>> 24, "terra: sem máscara de água");
        assertEquals(100.0, height[0], 1.0e-9);

        int sea = PlanetMeshGeometry.shadeVertex(terrain, palette, 20_000, 0, new double[0], 0, height);
        assertEquals(255, sea >>> 24, "água vem da flag do mapa");
        assertEquals(EARTH.seaLevel(), height[0], 1.0e-9, "oceano é plano no nível do mar");
    }
}
