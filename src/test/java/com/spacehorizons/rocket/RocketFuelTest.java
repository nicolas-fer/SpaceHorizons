package com.spacehorizons.rocket;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/** Repartição do combustível entre os tanques ao pousar. */
class RocketFuelTest {
    @Test
    void limitesDeUmTanque() {
        assertEquals(0, RocketFuel.clamp(-5));
        assertEquals(RocketSpecs.TANK_CAPACITY_MB, RocketFuel.clamp(9999));
        assertEquals(3000, RocketFuel.space(1000));
        assertEquals(0, RocketFuel.space(RocketSpecs.TANK_CAPACITY_MB));
    }

    @Test
    void tanquesIguaisEnchemPorIgual() {
        assertArrayEquals(new int[] {500, 500}, RocketFuel.distribute(1000, new int[] {4000, 4000}));
        assertArrayEquals(new int[] {2000, 2000}, RocketFuel.distribute(4000, new int[] {4000, 4000}));
        assertArrayEquals(new int[] {4000, 4000}, RocketFuel.distribute(8000, new int[] {4000, 4000}));
    }

    @Test
    void umTanqueSoRecebeTudoLimitadoACapacidade() {
        assertArrayEquals(new int[] {1000}, RocketFuel.distribute(1000, new int[] {4000}));
        assertArrayEquals(new int[] {4000}, RocketFuel.distribute(99999, new int[] {4000}));
    }

    @Test
    void semCombustivelOuSemTanqueNaoRepartenada() {
        assertArrayEquals(new int[] {0, 0}, RocketFuel.distribute(0, new int[] {4000, 4000}));
        assertArrayEquals(new int[] {0, 0}, RocketFuel.distribute(-10, new int[] {4000, 4000}));
        assertArrayEquals(new int[0], RocketFuel.distribute(1000, new int[0]));
    }

    @Test
    void asobraDeArredondamentoNaoCriaNemPerdeCombustivel() {
        int[] partes = RocketFuel.distribute(1000, new int[] {4000, 4000, 4000});   // 333,33 cada
        assertEquals(1000, partes[0] + partes[1] + partes[2]);
        assertTrue(partes[0] >= 333 && partes[0] <= 334);
        assertArrayEquals(new int[] {334, 333, 333}, partes, "a sobra de 1 mB vai para o primeiro");
    }

    @Test
    void somaSempreExataENenhumTanquePassaDaCapacidade() {
        Random random = new Random(11);
        for (int t = 0; t < 500; t++) {
            int n = 1 + random.nextInt(6);
            int[] caps = new int[n];
            long soma = 0;
            for (int i = 0; i < n; i++) {
                caps[i] = 1000 * (1 + random.nextInt(8));
                soma += caps[i];
            }
            int total = random.nextInt((int) soma + 2000);
            int[] partes = RocketFuel.distribute(total, caps);
            long dado = 0;
            for (int i = 0; i < n; i++) {
                assertTrue(partes[i] >= 0 && partes[i] <= caps[i], "tanque " + i + " fora de [0, capacidade]");
                dado += partes[i];
            }
            assertEquals(Math.min(total, soma), dado, "combustível criado ou perdido com total=" + total);
        }
    }
}
