package com.spacehorizons.ship.deck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.AABB;

/** O raio da mira contra a grade do retrato: o bloco certo, a face, o mais perto ganha e a forma parcial (porta aberta) conta. */
class PortraitRayTest {
    private static final AABB FULL = new AABB(0, 0, 0, 1, 1, 1);

    private static PortraitRay.Cells solid(int... xyz) {
        Set<Long> cells = new HashSet<>();
        for (int i = 0; i < xyz.length; i += 3) {
            cells.add(key(xyz[i], xyz[i + 1], xyz[i + 2]));
        }
        return (x, y, z, out) -> {
            if (cells.contains(key(x, y, z))) {
                out.accept(FULL);
            }
        };
    }

    private static long key(int x, int y, int z) {
        return ((long) x << 40) ^ ((long) y << 20) ^ z;
    }

    @Test
    void acertaOBlocoAFrenteComAFaceDeEntrada() {
        PortraitRay.Hit hit = PortraitRay.cast(0.5, 1.5, 0.5, 1, 0, 0, 6, 8, 4, 8, solid(3, 1, 0));
        assertNotNull(hit);
        assertEquals(3, hit.x());
        assertEquals(1, hit.y());
        assertEquals(0, hit.z());
        assertEquals(4, hit.face(), "o raio no sentido +x entra pela face oeste");
        assertEquals(2.5, hit.t(), 1e-9);
        assertEquals(3.0, hit.hx(), 1e-9);
    }

    @Test
    void oMaisPertoGanhaEOAlcanceCorta() {
        PortraitRay.Cells cells = solid(2, 1, 0, 5, 1, 0);
        PortraitRay.Hit hit = PortraitRay.cast(0.5, 1.5, 0.5, 1, 0, 0, 6, 8, 4, 8, cells);
        assertNotNull(hit);
        assertEquals(2, hit.x());
        assertNull(PortraitRay.cast(0.5, 1.5, 0.5, 1, 0, 0, 1.0, 8, 4, 8, solid(5, 1, 0)), "além do alcance");
    }

    @Test
    void raioDiagonalEParaBaixo() {
        PortraitRay.Hit down = PortraitRay.cast(2.5, 3.5, 2.5, 0, -1, 0, 5, 6, 6, 6, solid(2, 0, 2));
        assertNotNull(down);
        assertEquals(1, down.face(), "de cima para baixo atinge a face de cima");
        assertEquals(1.0, down.hy(), 1e-9);
        PortraitRay.Hit diagonal = PortraitRay.cast(0.5, 0.5, 0.5, 1, 1, 1, 8, 8, 8, 8, solid(3, 3, 3));
        assertNotNull(diagonal);
        assertEquals(3, diagonal.x());
        assertEquals(3, diagonal.z());
    }

    @Test
    void formaParcialDeixaORaioPassar() {
        // uma porta aberta: placa fina de 3/16 só no lado oeste da célula
        AABB door = new AABB(0, 0, 0, 3.0 / 16.0, 1, 1);
        PortraitRay.Cells cells = (x, y, z, out) -> {
            if (x == 2 && y == 1 && z == 0) {
                out.accept(door);
            }
        };
        // o raio vai a 0,6 de altura sobre a placa: acerta a face oeste dela
        PortraitRay.Hit hit = PortraitRay.cast(0.5, 1.6, 0.5, 1, 0, 0, 6, 8, 4, 8, cells);
        assertNotNull(hit);
        assertEquals(2.0, hit.hx(), 1e-9);
        // o raio que entra por trás da placa, vindo do outro lado, acerta a face leste
        PortraitRay.Hit back = PortraitRay.cast(4.5, 1.6, 0.5, -1, 0, 0, 6, 8, 4, 8, cells);
        assertNotNull(back);
        assertEquals(5, back.face());
        assertEquals(2.0 + 3.0 / 16.0, back.hx(), 1e-9);
        // por cima da célula (y em 2,5) não há nada
        assertNull(PortraitRay.cast(0.5, 2.5, 0.5, 1, 0, 0, 6, 8, 4, 8, cells));
    }

    @Test
    void origemDentroDeUmaCaixaNaoASeleciona() {
        PortraitRay.Hit hit = PortraitRay.cast(1.5, 1.5, 0.5, 1, 0, 0, 6, 8, 4, 8, solid(1, 1, 0, 4, 1, 0));
        assertNotNull(hit);
        assertEquals(4, hit.x());
    }

    @Test
    void foraDaCaixaEDirecaoNulaNaoAcertam() {
        assertNull(PortraitRay.cast(0.5, 0.5, 0.5, 0, 0, 0, 6, 4, 4, 4, solid(1, 0, 0)));
        assertNull(PortraitRay.cast(0.5, 0.5, 0.5, -1, 0, 0, 6, 4, 4, 4, solid(1, 0, 0)));
        assertNull(PortraitRay.cast(9.5, 0.5, 0.5, -1, 0, 0, 6, 4, 4, 4, solid(1, 0, 0)), "origem fora da caixa a mais de 6 blocos");
    }

    @Test
    void direcaoNaoUnitariaMedeEmBlocos() {
        PortraitRay.Hit hit = PortraitRay.cast(0.5, 1.5, 0.5, 4, 0, 0, 6, 8, 4, 8, solid(3, 1, 0));
        assertNotNull(hit);
        assertEquals(2.5, hit.t(), 1e-9);
    }
}
