package com.spacehorizons.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.IntToDoubleFunction;

import org.junit.jupiter.api.Test;

import com.spacehorizons.worldgen.density.SeamMath;

class SeamMathTest {
    private static final int C = 65_536;
    private static final int W = SeamMath.DEFAULT_HALF_BAND;

    /** Função suave e NÃO periódica em C: o pior caso para o wrapper (lados da costura bem diferentes). */
    private static final IntToDoubleFunction F = x -> Math.sin(x * 0.0021) + 0.5 * Math.sin(x * 0.00037 + 1.3) + x * 1e-5;

    @Test
    void canonicalFicaNoIntervaloEPreservaModulo() {
        for (int x = -3 * C; x <= 3 * C; x += 977) {
            int r = SeamMath.canonical(x, C);
            assertTrue(r >= -C / 2 && r < C / 2, "r fora de [-C/2, C/2): " + r);
            assertEquals(0, Math.floorMod(x - r, C));
        }
    }

    @Test
    void avaliacaoEPeriodicaComPeriodoC() {
        for (int x = -C; x <= C; x += 313) {
            double base = SeamMath.evaluate(F, x, C, W);
            for (int k = -2; k <= 2; k++) {
                assertEquals(base, SeamMath.evaluate(F, x + k * C, C, W), 0.0, "x=" + x + " k=" + k);
            }
        }
        // pontos de borda exatos
        for (int x : new int[] { -C / 2, C / 2, C / 2 - 1, -C / 2 + 1, 0, C / 2 - W, C / 2 + W, -C / 2 + W, -C / 2 - W }) {
            assertEquals(SeamMath.evaluate(F, x, C, W), SeamMath.evaluate(F, x + C, C, W), 0.0, "x=" + x);
        }
    }

    @Test
    void longeDaCosturaNaoMudaNada() {
        for (int x = -C / 2 + W; x <= C / 2 - W; x += 211) {
            assertEquals(F.applyAsDouble(x), SeamMath.evaluate(F, x, C, W), 0.0, "x=" + x);
        }
    }

    @Test
    void continuoAtravessandoACostura() {
        // maior variação de F entre blocos vizinhos na região: limite de Lipschitz do próprio F
        double lipschitz = 0.0021 + 0.5 * 0.00037 + 1e-5;
        // o peso varia com derivada máxima 1,5/(2W) por bloco, e o misturador tem ganho <= sqrt(2): folga para somar os dois efeitos
        double weightRate = 1.5 / (2.0 * W) * (Math.PI / 2.0);
        double amplitude = 2.0 * (1.0 + 0.5 + C * 1e-5);
        double bound = Math.sqrt(2.0) * lipschitz + weightRate * amplitude;
        for (int x = C / 2 - 2 * W; x < C / 2 + 2 * W; x++) {
            double step = Math.abs(SeamMath.evaluate(F, x + 1, C, W) - SeamMath.evaluate(F, x, C, W));
            assertTrue(step <= bound, "salto " + step + " > " + bound + " em x=" + x);
        }
        // e exatamente na costura os dois lados coincidem com a mistura 50/50 de A e B
        double expected = (F.applyAsDouble(C / 2) + F.applyAsDouble(-C / 2)) / Math.sqrt(2.0);
        assertEquals(expected, SeamMath.evaluate(F, C / 2, C, W), 1e-12);
        assertEquals(expected, SeamMath.evaluate(F, -C / 2, C, W), 1e-12);
    }

    @Test
    void continuidadeNasBordasDaFaixa() {
        // em d = -W só A e em d = +W só B: o mesmo que a avaliação fora da faixa
        int leftEdge = C / 2 - W;
        assertEquals(F.applyAsDouble(leftEdge), SeamMath.evaluate(F, leftEdge, C, W), 0.0);
        int rightEdge = -C / 2 + W;
        assertEquals(F.applyAsDouble(rightEdge), SeamMath.evaluate(F, rightEdge, C, W), 0.0);
    }

    @Test
    void pesoMonotonoDe0a1() {
        double previous = -1.0;
        for (int d = -2 * W; d <= 2 * W; d++) {
            double w = SeamMath.weightB(d, W);
            assertTrue(w >= previous, "peso decresceu em d=" + d);
            assertTrue(w >= 0.0 && w <= 1.0);
            previous = w;
        }
        assertEquals(0.0, SeamMath.weightB(-W, W), 0.0);
        assertEquals(0.5, SeamMath.weightB(0, W), 1e-12);
        assertEquals(1.0, SeamMath.weightB(W, W), 0.0);
    }

    @Test
    void misturaPreservaVarianciaDeRuidoIndependente() {
        // para a e b independentes com variância 1, cos²+sin² = 1: a variância da mistura é 1 em qualquer peso
        for (double w = 0.0; w <= 1.0; w += 0.1) {
            double angle = w * Math.PI / 2.0;
            assertEquals(1.0, Math.cos(angle) * Math.cos(angle) + Math.sin(angle) * Math.sin(angle), 1e-12);
        }
        assertEquals(3.0, SeamMath.mix(3.0, -5.0, 0.0), 0.0);
        assertEquals(-5.0, SeamMath.mix(3.0, -5.0, 1.0), 0.0);
    }

    @Test
    void deslocamentosSaoMultiplosDaCircunferencia() {
        // os volumes deslocados continuam alinhados às células do noise: o deslocamento é sempre k*C
        for (int x = -C; x <= C; x += 97) {
            assertEquals(0, Math.floorMod(SeamMath.shiftA(x, C, W), C), "shiftA x=" + x);
            if (SeamMath.inBand(SeamMath.seamOffset(x, C), W)) {
                assertEquals(0, Math.floorMod(SeamMath.shiftB(x, C, W), C), "shiftB x=" + x);
            }
        }
    }

    @Test
    void pertoDaCosturaValeDosDoisLados() {
        assertTrue(SeamMath.nearSeam(C / 2 - 10, C, 640));
        assertTrue(SeamMath.nearSeam(C / 2 + 10, C, 640));
        assertTrue(SeamMath.nearSeam(-C / 2 + 600, C, 640));
        assertFalse(SeamMath.nearSeam(0, C, 640));
        assertFalse(SeamMath.nearSeam(C / 2 - 1000, C, 640));
        assertEquals(0, SeamMath.distanceToSeam(C / 2, C));
        assertEquals(0, SeamMath.distanceToSeam(-C / 2, C));
    }
}
