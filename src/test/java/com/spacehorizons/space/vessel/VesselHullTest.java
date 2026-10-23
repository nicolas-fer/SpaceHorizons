package com.spacehorizons.space.vessel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.zip.DeflaterOutputStream;

import org.junit.jupiter.api.Test;

/** O casco externo esparso (montagem, codec e malha) das outras naves e foguetes (fase 2d). */
class VesselHullTest {
    private static final int STEEL = 0x778899;
    private static final int BRICK = 0xB04A3A;

    private static boolean has(VesselHull hull, int x, int y, int z) {
        int index = hull.index(x, y, z);
        for (int i = 0; i < hull.cellCount(); i++) {
            if (hull.cellIndex(i) == index) {
                return true;
            }
        }
        return false;
    }

    private static VesselHull solid(int n) {
        return VesselHullBuilder.build(n, n, n, (x, y, z) -> STEEL, n / 2.0F, n / 2.0F, n / 2.0F);
    }

    @Test
    void cuboMacicoSoTemACascaEOInteriorSome() {
        VesselHull hull = solid(5);
        assertEquals(125 - 27, hull.cellCount(), "5x5x5 menos o miolo 3x3x3");
        assertFalse(has(hull, 2, 2, 2));
        assertTrue(has(hull, 0, 0, 0));
        assertTrue(has(hull, 4, 2, 2));
    }

    @Test
    void cascoOcoFechadoNaoMostraOQueEstaDentro() {
        // casca de 7x7x7 com um bloco flutuando no meio do ar fechado: não toca o espaço, não entra
        VesselHull hull = VesselHullBuilder.build(7, 7, 7, (x, y, z) -> {
            boolean shell = x == 0 || y == 0 || z == 0 || x == 6 || y == 6 || z == 6;
            return shell ? STEEL : (x == 3 && y == 3 && z == 3 ? BRICK : 0);
        }, 3.5F, 3.5F, 3.5F);
        assertEquals(7 * 7 * 7 - 5 * 5 * 5, hull.cellCount());
        assertFalse(has(hull, 3, 3, 3));
    }

    @Test
    void umFuroNaCascaDeixaOInteriorAparecer() {
        VesselHull hull = VesselHullBuilder.build(7, 7, 7, (x, y, z) -> {
            boolean shell = x == 0 || y == 0 || z == 0 || x == 6 || y == 6 || z == 6;
            if (x == 0 && y == 3 && z == 3) {
                return 0;       // o furo
            }
            return shell ? STEEL : (x == 1 && y == 3 && z == 3 ? BRICK : 0);
        }, 3.5F, 3.5F, 3.5F);
        assertTrue(has(hull, 1, 3, 3), "o bloco logo atrás do furo toca o ar de fora");
    }

    @Test
    void cascosGrandesViramGradeMaisGrossa() {
        VesselHull fine = solid(32);
        assertEquals(1, fine.scale);
        VesselHull coarse = VesselHullBuilder.build(32, 32, 32, (x, y, z) -> STEEL, 16.0F, 16.0F, 16.0F, 2000);
        assertEquals(2, coarse.scale, "5.768 células de casco passam de 2.000: células de 2 (1.352)");
        assertEquals(16, coarse.sizeX);
        assertTrue(coarse.cellCount() <= 2000);
        assertEquals(fine.radius(), coarse.radius(), 1.0e-9, "o raio em blocos não muda com a escala");
    }

    @Test
    void codecDeRedeIdaEVolta() {
        VesselHull hull = VesselHullBuilder.build(9, 12, 9, (x, y, z) -> (x + y) % 3 == 0 ? BRICK : STEEL, 4.5F, 6.0F, 4.5F);
        VesselHull back = VesselHull.decode(hull.encode());
        assertEquals(hull.cellCount(), back.cellCount());
        assertEquals(hull.sizeY, back.sizeY);
        assertEquals(hull.pivotY, back.pivotY);
        assertEquals(hull.paletteSize(), back.paletteSize());
        for (int i = 0; i < hull.cellCount(); i++) {
            assertEquals(hull.cellIndex(i), back.cellIndex(i));
            assertEquals(hull.cellRgb(i), back.cellRgb(i));
        }
    }

    @Test
    void codecRecusaEntradaRuimSemExplodir() {
        assertThrows(IllegalArgumentException.class, () -> VesselHull.decode(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> VesselHull.decode(new byte[] {9, 1, 2, 3}), "formato desconhecido");
        byte[] good = solid(6).encode();
        byte[] truncated = java.util.Arrays.copyOf(good, good.length / 2);
        assertThrows(IllegalArgumentException.class, () -> VesselHull.decode(truncated));
        byte[] flipped = good.clone();
        for (int i = 4; i < flipped.length; i += 3) {
            flipped[i] ^= 0x5A;
        }
        // lixo no meio do fluxo: ou recusa, ou lê algo coerente; nunca outra exceção
        try {
            VesselHull.decode(flipped);
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    void codecRecusaPacoteQueSeExpandeDemais() throws Exception {
        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        packed.write(1);
        try (DeflaterOutputStream deflate = new DeflaterOutputStream(packed)) {
            deflate.write(new byte[20 * 1024 * 1024]);       // 20 MiB de zeros comprimem para quase nada
        }
        byte[] bomb = packed.toByteArray();
        assertTrue(bomb.length < 100_000);
        assertThrows(IllegalArgumentException.class, () -> VesselHull.decode(bomb));
    }

    @Test
    void cascoRecusaIndicesForaDeOrdemOuDoVolume() {
        assertThrows(IllegalArgumentException.class, () -> new VesselHull(2, 2, 2, 1, 1, 1, 1, new int[] {1}, new int[] {3, 2}, new short[] {0, 0}));
        assertThrows(IllegalArgumentException.class, () -> new VesselHull(2, 2, 2, 1, 1, 1, 1, new int[] {1}, new int[] {8}, new short[] {0}));
        assertThrows(IllegalArgumentException.class, () -> new VesselHull(2, 2, 2, 1, 1, 1, 1, new int[] {1}, new int[] {1}, new short[] {1}));
    }

    @Test
    void malhaDeUmaCelulaTemSeisFacesEPosicoesRelativasAoPivo() {
        VesselHull one = new VesselHull(1, 1, 1, 1, 0.5F, 0.5F, 0.5F, new int[] {STEEL}, new int[] {0}, new short[] {0});
        VesselMesh.Mesh mesh = VesselMesh.build(one);
        assertEquals(12, mesh.triangleCount(), "6 faces, 2 triângulos cada");
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (float p : mesh.positions()) {
            min = Math.min(min, p);
            max = Math.max(max, p);
        }
        assertEquals(-0.5F, min, 1.0e-6F);
        assertEquals(0.5F, max, 1.0e-6F);
        assertEquals(0xFF000000 | STEEL, mesh.colors()[0]);
    }

    @Test
    void facesColadasNaoSaoDesenhadas() {
        VesselHull two = new VesselHull(2, 1, 1, 1, 1.0F, 0.5F, 0.5F, new int[] {STEEL}, new int[] {0, 1}, new short[] {0, 0});
        assertEquals(20, VesselMesh.build(two).triangleCount(), "10 faces visíveis de 12");
        // a escala engrossa as células: uma célula de 4 blocos vira uma caixa de 4
        VesselHull big = new VesselHull(1, 1, 1, 4, 2.0F, 2.0F, 2.0F, new int[] {STEEL}, new int[] {0}, new short[] {0});
        float max = 0.0F;
        for (float p : VesselMesh.build(big).positions()) {
            max = Math.max(max, Math.abs(p));
        }
        assertEquals(2.0F, max, 1.0e-6F);
    }

    @Test
    void caixaDoImpostorTemDozeTriangulosNasExtensoesPedidas() {
        VesselMesh.Mesh box = VesselMesh.box(1.0F, 2.0F, 3.0F, 0xE8B27A);
        assertEquals(12, box.triangleCount());
        float maxX = 0;
        float maxY = 0;
        float maxZ = 0;
        for (int i = 0; i < box.vertexCount(); i++) {
            maxX = Math.max(maxX, Math.abs(box.positions()[i * 3]));
            maxY = Math.max(maxY, Math.abs(box.positions()[i * 3 + 1]));
            maxZ = Math.max(maxZ, Math.abs(box.positions()[i * 3 + 2]));
        }
        assertArrayEquals(new float[] {1.0F, 2.0F, 3.0F}, new float[] {maxX, maxY, maxZ}, 1.0e-6F);
    }

    @Test
    void cascoDeUmaNaveDeVerdadeCabeNumPacoteSimples() {
        // uma casca de 64x64x64 (a nave de teste do estaleiro): ~24.000 células de casco, bem abaixo do teto, e os bytes ficam longe do 1 MiB do pacote
        VesselHull hull = VesselHullBuilder.build(64, 64, 64, (x, y, z) -> {
            boolean shell = x == 0 || y == 0 || z == 0 || x == 63 || y == 63 || z == 63;
            return shell ? ((x + z) % 7 == 0 ? BRICK : STEEL) : 0;
        }, 32.0F, 32.0F, 32.0F);
        assertEquals(64 * 64 * 64 - 62 * 62 * 62, hull.cellCount());
        assertEquals(1, hull.scale);
        assertTrue(hull.encode().length < 100_000, "bytes=" + hull.encode().length);
    }
}
