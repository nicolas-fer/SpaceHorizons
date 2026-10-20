package com.spacehorizons.ship;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.JsonOps;

/** Alocação de células do estaleiro: espiral, sem colisão, reaproveitamento com quarentena e persistência. */
class ShipYardAllocatorTest {
    @Test
    void espiralComecaNaOrigemEAndaEmVoltaDela() {
        int[][] esperado = {{0, 0}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}, {2, -1}, {2, 0}};
        for (int i = 0; i < esperado.length; i++) {
            assertArrayEquals(esperado[i], ShipYardAllocator.cellCoords(i), "célula " + i);
        }
    }

    @Test
    void cadaIndiceTemUmaCelulaDiferenteEVizinhosConsecutivosSaoAdjacentes() {
        Set<Long> vistas = new HashSet<>();
        int[] anterior = null;
        for (int i = 0; i < 5000; i++) {
            int[] c = ShipYardAllocator.cellCoords(i);
            assertTrue(vistas.add(((long) c[0] << 32) ^ (c[1] & 0xFFFFFFFFL)), "célula repetida no índice " + i);
            if (anterior != null) {
                assertEquals(1, Math.abs(c[0] - anterior[0]) + Math.abs(c[1] - anterior[1]), "índices " + (i - 1) + " e " + i + " devem ser vizinhos");
            }
            anterior = c;
        }
    }

    @Test
    void indiceNegativoERecusado() {
        assertThrows(IllegalArgumentException.class, () -> ShipYardAllocator.cellCoords(-1));
    }

    @Test
    void alocaEmSequenciaSemRepetir() {
        ShipYardAllocator a = new ShipYardAllocator();
        assertEquals(0, a.allocate());
        assertEquals(1, a.allocate());
        assertEquals(2, a.allocate());
        assertEquals(3, a.highWater());
    }

    @Test
    void celulaLiberadaFicaEmQuarentenaAteANovaSessao() {
        ShipYardAllocator a = new ShipYardAllocator();
        a.allocate();
        int b = a.allocate();
        a.allocate();
        a.release(b);
        assertEquals(1, a.pendingCount());
        assertEquals(3, a.allocate(), "na mesma sessão a célula liberada NÃO volta (pode haver chunks ou NBT residual)");
        a.startSession();
        assertEquals(0, a.pendingCount());
        assertEquals(1, a.freeCount());
        assertEquals(b, a.allocate(), "na sessão seguinte a célula é reaproveitada");
        assertEquals(4, a.allocate());
    }

    @Test
    void reaproveitaAMenorCelulaLivrePrimeiro() {
        ShipYardAllocator a = new ShipYardAllocator();
        for (int i = 0; i < 5; i++) {
            a.allocate();
        }
        a.release(3);
        a.release(1);
        a.startSession();
        assertEquals(1, a.allocate());
        assertEquals(3, a.allocate());
        assertEquals(5, a.allocate());
    }

    @Test
    void liberarDuasVezesOuCelulaDesconhecidaFalha() {
        ShipYardAllocator a = new ShipYardAllocator();
        a.allocate();
        a.release(0);
        assertThrows(IllegalStateException.class, () -> a.release(0));
        assertThrows(IllegalArgumentException.class, () -> a.release(7));
        a.startSession();
        assertThrows(IllegalStateException.class, () -> a.release(0), "livre não se libera de novo");
    }

    @Test
    void persisteELeDeVolta() {
        ShipYardAllocator a = new ShipYardAllocator();
        for (int i = 0; i < 6; i++) {
            a.allocate();
        }
        a.release(2);
        a.startSession();
        a.release(4);
        var json = ShipYardAllocator.CODEC.encodeStart(JsonOps.INSTANCE, a).getOrThrow();
        ShipYardAllocator b = ShipYardAllocator.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(6, b.highWater());
        assertEquals(1, b.freeCount());
        assertEquals(1, b.pendingCount());
        assertEquals(2, b.allocate());
        assertEquals(6, b.allocate(), "a 4 ainda está em quarentena");
        b.startSession();
        assertEquals(4, b.allocate());
        assertFalse(b.freeCount() > 0);
    }

    @Test
    void celulasVizinhasNaoSeTocamNemComANaveMaxima() {
        // a maior nave (128 x 256 x 128) centrada na célula deixa >= 448 blocos de vazio até a vizinha, em X e em Z
        ShipYard.Box a = ShipYard.box(0, ShipYard.MAX_X, ShipYard.MAX_Y, ShipYard.MAX_Z);
        for (int i = 1; i < 9; i++) {
            ShipYard.Box b = ShipYard.box(i, ShipYard.MAX_X, ShipYard.MAX_Y, ShipYard.MAX_Z);
            int gapX = Math.max(b.minX() - (a.maxX() + 1), a.minX() - (b.maxX() + 1));
            int gapZ = Math.max(b.minZ() - (a.maxZ() + 1), a.minZ() - (b.maxZ() + 1));
            assertTrue(Math.max(gapX, gapZ) >= 448, "folga entre a célula 0 e a " + i + ": " + gapX + ", " + gapZ);
        }
    }
}
