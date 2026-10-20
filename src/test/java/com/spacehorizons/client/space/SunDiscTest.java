package com.spacehorizons.client.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A malha do disco do sol: redonda (todo vértice de um anel à mesma distância do centro), alfa decrescente e a borda some. */
class SunDiscTest {
    @Test
    void cadaAnelEhUmCirculo() {
        SunDisc.Mesh mesh = SunDisc.mesh();
        assertEquals(mesh.colors().length * 3, mesh.positions().length);
        for (int v = 0; v < mesh.vertexCount(); v++) {
            float x = mesh.positions()[v * 3];
            float y = mesh.positions()[v * 3 + 1];
            double r = Math.hypot(x, y);
            double degrees = Math.toDegrees(Math.atan(r));
            // o raio de cada vértice é o de um dos anéis da tabela
            boolean onRing = false;
            for (float ring : SunDisc.RING_DEGREES) {
                onRing |= Math.abs(degrees - ring) < 1.0E-3;
            }
            assertTrue(onRing, "vértice fora dos anéis: " + degrees + " graus");
            assertEquals(0.0F, mesh.positions()[v * 3 + 2], 0.0F);
        }
    }

    @Test
    void oBrilhoCaiAteZeroNaBorda() {
        for (int i = 1; i < SunDisc.RING_ALPHA.length; i++) {
            assertTrue(SunDisc.RING_ALPHA[i] <= SunDisc.RING_ALPHA[i - 1], "alfa crescendo no anel " + i);
        }
        assertEquals(0.0F, SunDisc.RING_ALPHA[SunDisc.RING_ALPHA.length - 1]);
        assertEquals(1.0F, SunDisc.RING_ALPHA[0]);
        // o núcleo (anéis 0 e 1) é opaco e sem ponto fraco
        SunDisc.Mesh mesh = SunDisc.mesh();
        assertEquals(255, mesh.colors()[0] >>> 24);
    }
}
