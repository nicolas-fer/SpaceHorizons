package com.spacehorizons.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.longs.LongArrayList;

/** Detecção das estruturas de tanque e de motor: formas válidas, inválidas e bordas, sem o jogo. */
class MultiblocksTest {
    private static LongArrayList box(int x, int y, int z, int w, int h, int d) {
        LongArrayList cells = new LongArrayList();
        for (int yy = y; yy < y + h; yy++) {
            for (int zz = z; zz < z + d; zz++) {
                for (int xx = x; xx < x + w; xx++) {
                    cells.add(RocketAssembly.pack(xx, yy, zz));
                }
            }
        }
        return cells;
    }

    private static Multiblocks.Tiling tanks(LongArrayList cells) {
        return Multiblocks.tileTanks(cells.toLongArray());
    }

    private static Multiblocks.Tiling engines(LongArrayList cells) {
        return Multiblocks.tileEngines(cells.toLongArray());
    }

    // ---------------------------------------------------------------- tanques: a regra

    @Test
    void todaColunaRetangularDeLargura1a3EAltura4a12FormaUmaEstrutura() {
        for (int w = 1; w <= 3; w++) {
            for (int d = 1; d <= 3; d++) {
                for (int h = 4; h <= 12; h++) {
                    Multiblocks.Tiling t = tanks(box(10, 20, -5, w, h, d));
                    assertEquals(1, t.boxes().size(), w + "x" + d + "x" + h);
                    assertEquals(new Multiblocks.Box(10, 20, -5, w, h, d), t.boxes().get(0));
                    assertEquals(0, t.looseCount(), w + "x" + d + "x" + h + " sem avulsos");
                }
            }
        }
    }

    @Test
    void exemplosDoPlano() {
        assertEquals(new Multiblocks.Box(0, 0, 0, 1, 8, 1), tanks(box(0, 0, 0, 1, 8, 1)).boxes().get(0));
        assertEquals(new Multiblocks.Box(0, 0, 0, 2, 8, 2), tanks(box(0, 0, 0, 2, 8, 2)).boxes().get(0));
        assertEquals(new Multiblocks.Box(0, 0, 0, 3, 10, 3), tanks(box(0, 0, 0, 3, 10, 3)).boxes().get(0));
    }

    @Test
    void baixoDemaisNaoFormaEFicaAvulso() {
        for (int h = 1; h <= 3; h++) {
            Multiblocks.Tiling t = tanks(box(0, 0, 0, 2, h, 2));
            assertTrue(t.boxes().isEmpty(), "altura " + h + " é menor que 4");
            assertEquals(4 * h, t.looseCount());
        }
    }

    @Test
    void largoDemaisEParteEmColunasEmVezDeRecusar() {
        // 4 de largura: a partição gulosa toma 3x?x? e depois uma coluna 1x?x?; nada fica perdido
        Multiblocks.Tiling t = tanks(box(0, 0, 0, 4, 5, 1));
        assertEquals(2, t.boxes().size());
        assertEquals(0, t.looseCount());
        assertEquals(20, t.boxes().stream().mapToInt(Multiblocks.Box::volume).sum());
        assertEquals(new Multiblocks.Box(0, 0, 0, 3, 5, 1), t.boxes().get(0));
        assertEquals(new Multiblocks.Box(3, 0, 0, 1, 5, 1), t.boxes().get(1));
    }

    @Test
    void altoDemaisParteEmDuasColunas() {
        Multiblocks.Tiling t = tanks(box(0, 0, 0, 3, 16, 3));
        assertEquals(2, t.boxes().size());
        assertEquals(12, t.boxes().get(0).h());
        assertEquals(4, t.boxes().get(1).h(), "o resto (4) ainda forma uma coluna");
        assertEquals(0, t.looseCount());
        // 14 de altura: 12 e 2, e o resto de 2 é pequeno demais: fica avulso
        Multiblocks.Tiling t14 = tanks(box(0, 0, 0, 1, 14, 1));
        assertEquals(1, t14.boxes().size());
        assertEquals(2, t14.looseCount());
    }

    @Test
    void buracoNaColunaImpedeAFormaCompleta() {
        LongArrayList cells = box(0, 0, 0, 3, 6, 3);
        cells.rem(RocketAssembly.pack(1, 3, 1)); // tira o bloco central do meio
        Multiblocks.Tiling t = tanks(cells);
        long formed = t.boxes().stream().mapToLong(Multiblocks.Box::volume).sum();
        assertEquals(cells.size(), formed + t.looseCount(), "todo bloco é de uma estrutura ou avulso");
        assertTrue(t.boxes().stream().noneMatch(b -> b.contains(1, 3, 1)), "a caixa nunca inclui o buraco");
        assertTrue(t.boxes().stream().noneMatch(b -> b.w() == 3 && b.d() == 3 && b.h() >= 4), "a coluna 3x3 inteira não se forma com buraco");
    }

    @Test
    void formaEmLNaoEUmaColuna() {
        LongArrayList cells = box(0, 0, 0, 2, 5, 1);
        cells.addAll(box(0, 0, 1, 1, 5, 1)); // um braço em z: L visto de cima
        Multiblocks.Tiling t = tanks(cells);
        assertTrue(t.boxes().stream().noneMatch(b -> b.w() * b.d() == 3), "L não é retangular");
        assertEquals(15, t.boxes().stream().mapToInt(Multiblocks.Box::volume).sum() + t.looseCount());
    }

    @Test
    void doisTanquesSeparadosPorUmVazioSaoEstruturasDiferentes() {
        LongArrayList cells = box(0, 0, 0, 1, 4, 1);
        cells.addAll(box(2, 0, 0, 1, 4, 1));
        assertEquals(2, tanks(cells).boxes().size());
    }

    @Test
    void particaoEhDeterministicaEIndependeDaOrdemDeEntrada() {
        LongArrayList cells = box(0, 0, 0, 3, 7, 3);
        cells.addAll(box(5, 2, 5, 2, 6, 2));
        Multiblocks.Tiling a = tanks(cells);
        LongArrayList reversed = new LongArrayList(cells);
        java.util.Collections.reverse(reversed);
        Multiblocks.Tiling b = tanks(reversed);
        assertEquals(a.boxes(), b.boxes());
        assertEquals(a.looseCount(), b.looseCount());
    }

    @Test
    void coordenadasNegativasEAltasFuncionam() {
        Multiblocks.Tiling t = tanks(box(-100, -60, -300, 2, 5, 3));
        assertEquals(new Multiblocks.Box(-100, -60, -300, 2, 5, 3), t.boxes().get(0));
        Multiblocks.Tiling u = tanks(box(327_000, 900, 5, 1, 4, 1));
        assertEquals(new Multiblocks.Box(327_000, 900, 5, 1, 4, 1), u.boxes().get(0));
    }

    @Test
    void semCelulasNaoHaNada() {
        assertTrue(tanks(new LongArrayList()).boxes().isEmpty());
        assertEquals(0, engines(new LongArrayList()).looseCount());
    }

    // ---------------------------------------------------------------- motores

    @Test
    void cubosCheiosDe2e3FormamMotor() {
        assertEquals(new Multiblocks.Box(4, 1, 4, 2, 2, 2), engines(box(4, 1, 4, 2, 2, 2)).boxes().get(0));
        assertEquals(new Multiblocks.Box(4, 1, 4, 3, 3, 3), engines(box(4, 1, 4, 3, 3, 3)).boxes().get(0));
    }

    @Test
    void camadaPlanaDeMotoresNaoFormaNada() {
        // os foguetes de teste (A: 2x2x1, missão: 3x3x1) continuam com motores avulsos
        Multiblocks.Tiling a = engines(box(0, 1, 0, 2, 1, 2));
        assertTrue(a.boxes().isEmpty());
        assertEquals(4, a.looseCount());
        Multiblocks.Tiling m = engines(box(-1, 1, -1, 3, 1, 3));
        assertTrue(m.boxes().isEmpty());
        assertEquals(9, m.looseCount());
    }

    @Test
    void cuboIncompletoCaiParaOMenor() {
        LongArrayList cells = box(0, 0, 0, 3, 3, 3);
        cells.rem(RocketAssembly.pack(2, 2, 2));
        Multiblocks.Tiling t = engines(cells);
        assertTrue(t.boxes().stream().noneMatch(b -> b.side() == 3), "faltando um bloco, o 3x3x3 não forma");
        assertTrue(t.boxes().stream().allMatch(b -> b.side() == 2));
        assertEquals(26, t.boxes().stream().mapToInt(Multiblocks.Box::volume).sum() + t.looseCount());
    }

    @Test
    void doisCubosDe2LadoALadoFormamDoisMotores() {
        Multiblocks.Tiling t = engines(box(0, 0, 0, 4, 2, 2));
        assertEquals(2, t.boxes().size());
        assertEquals(0, t.looseCount());
    }

    @Test
    void cuboDe4x4x4DaUmDe3EUmaSobraDeCubosDe2() {
        Multiblocks.Tiling t = engines(box(0, 0, 0, 4, 4, 4));
        long threes = t.boxes().stream().filter(b -> b.side() == 3).count();
        assertEquals(1, threes, "a partição pega o maior primeiro, no canto mínimo");
        assertEquals(64, t.boxes().stream().mapToInt(Multiblocks.Box::volume).sum() + t.looseCount());
    }

    @Test
    void papeisDoMotor2x2x2TemUmMestreNoCantoMinimo() {
        Multiblocks.Tiling t = engines(box(10, 5, 10, 2, 2, 2));
        Map<Long, Multiblocks.EngineRole> roles = Multiblocks.engineRoles(t);
        assertEquals(8, roles.size());
        assertEquals(Multiblocks.EngineRole.MASTER_2, roles.get(RocketAssembly.pack(10, 5, 10)));
        assertEquals(1, roles.values().stream().filter(r -> r == Multiblocks.EngineRole.MASTER_2).count());
        assertEquals(7, roles.values().stream().filter(r -> r == Multiblocks.EngineRole.PART).count());
    }

    @Test
    void papeisDoMotor3x3x3TemUmMestreNoCentro() {
        Multiblocks.Tiling t = engines(box(0, 0, 0, 3, 3, 3));
        Map<Long, Multiblocks.EngineRole> roles = Multiblocks.engineRoles(t);
        assertEquals(27, roles.size());
        assertEquals(Multiblocks.EngineRole.MASTER_3, roles.get(RocketAssembly.pack(1, 1, 1)));
        assertEquals(26, roles.values().stream().filter(r -> r == Multiblocks.EngineRole.PART).count());
    }

    @Test
    void motoresAvulsosSaoSolo() {
        Multiblocks.Tiling t = engines(box(0, 0, 0, 3, 1, 1));
        Map<Long, Multiblocks.EngineRole> roles = Multiblocks.engineRoles(t);
        assertEquals(3, roles.size());
        assertTrue(roles.values().stream().allMatch(r -> r == Multiblocks.EngineRole.SOLO));
    }

    @Test
    void caixaSabeSeContemACelulaESuasMedidas() {
        Multiblocks.Box box = new Multiblocks.Box(1, 2, 3, 2, 4, 3);
        assertTrue(box.contains(1, 2, 3));
        assertTrue(box.contains(2, 5, 5));
        assertTrue(!box.contains(3, 2, 3) && !box.contains(1, 6, 3) && !box.contains(1, 2, 6));
        assertEquals(24, box.volume());
        assertEquals(2 * (2 * 3 + 2 * 4 + 3 * 4), box.surface());
        Multiblocks.Tiling t = tanks(box(1, 2, 3, 2, 4, 3));
        assertNotNull(Multiblocks.boxAt(t, 2, 3, 4));
        assertNull(Multiblocks.boxAt(t, 9, 9, 9));
    }
}
