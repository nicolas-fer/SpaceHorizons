package com.spacehorizons.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

/** Geometria do estaleiro: caixa da nave dentro da célula, alinhada a chunks, e o layout da nave de teste dentro da caixa. */
class ShipYardTest {
    @Test
    void naveDe64FicaCentradaNaCelulaZeroAlinhadaAChunks() {
        ShipYard.Box box = ShipYard.box(0, 64, 64, 64);
        assertEquals(480, box.minX());
        assertEquals(480, box.minZ());
        assertEquals(ShipYard.BASE_Y, box.minY());
        assertEquals(0, box.minX() & 15);
        assertEquals(16, box.chunkColumns(), "64x64 = 4x4 colunas de chunk");
        assertEquals(30, box.chunkMinX());
        assertEquals(33, box.chunkMaxX());
    }

    @Test
    void caixaFicaSempreDentroDaPropriaCelula() {
        int[] tamanhos = {16, 32, 47, 64, 100, 128};
        for (int index = 0; index < 50; index++) {
            for (int size : tamanhos) {
                ShipYard.Box box = ShipYard.box(index, size, size, size);
                assertTrue(ShipYard.cellContains(index, box.minX(), box.minZ()), "canto mínimo, célula " + index + ", tamanho " + size);
                assertTrue(ShipYard.cellContains(index, box.maxX(), box.maxZ()), "canto máximo, célula " + index + ", tamanho " + size);
                for (int other = 0; other < 50; other++) {
                    if (other != index) {
                        assertFalse(ShipYard.cellContains(other, box.minX(), box.minZ()), "a célula " + other + " não contém a nave da " + index);
                    }
                }
            }
        }
    }

    @Test
    void alturaMaximaCabeNaDimensao() {
        ShipYard.Box box = ShipYard.box(3, ShipYard.MAX_X, ShipYard.MAX_Y, ShipYard.MAX_Z);
        assertTrue(box.maxY() < ShipYard.HEIGHT - 64, "sobra vão em cima da maior nave: topo " + box.maxY());
        assertTrue(box.minY() >= 16);
    }

    @Test
    void tamanhoForaDoLimiteERecusado() {
        assertThrows(IllegalArgumentException.class, () -> ShipYard.box(0, 8, 64, 64));
        assertThrows(IllegalArgumentException.class, () -> ShipYard.box(0, 129, 64, 64));
        assertThrows(IllegalArgumentException.class, () -> ShipYard.box(0, 64, 257, 64));
    }

    @Test
    void layoutDaNaveDeTesteFicaDentroDaCaixa() {
        for (int size : new int[] {40, 48, 64, 128}) {
            ShipYard.Box box = ShipYard.box(7, size, size, size);
            ShipBuilder.Layout l = ShipBuilder.layout(box);
            for (BlockPos p : new BlockPos[] {l.spawn(), l.doorLower(), l.chest(), l.furnace(), l.lamp(), l.lever(), l.lightProbe(),
                    l.floorWindowCenter(), l.ceilingWindowCenter(), l.cabinMin(), l.cabinMax()}) {
                assertTrue(box.contains(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), "tamanho " + size + ": " + p + " fora da caixa");
            }
            // a cabine cabe no salão e o jogador nasce fora dela
            assertTrue(l.cabinMax().getX() < box.maxX() - 1 && l.cabinMax().getZ() < box.maxZ() - 1);
            assertTrue(l.spawn().getX() < l.cabinMin().getX());
            // as janelas de vidro do piso (9x9) e do teto (17x17) cabem dentro das paredes
            assertTrue(l.floorWindowCenter().getX() - 4 > box.minX() && l.floorWindowCenter().getZ() - 4 > box.minZ());
            assertTrue(l.ceilingWindowCenter().getX() - 8 > box.minX() && l.ceilingWindowCenter().getX() + 8 < box.maxX());
        }
    }
}
