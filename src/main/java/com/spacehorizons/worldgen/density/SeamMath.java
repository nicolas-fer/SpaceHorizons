package com.spacehorizons.worldgen.density;

import java.util.function.IntToDoubleFunction;

/**
 * Matemática pura da costura em X (x = ±C/2) do planeta. Sem Minecraft, para ser testável.
 *
 * Uma função f(x) qualquer vira uma g(x) periódica de período C e contínua assim:
 *  - longe da costura (distância |d| >= W), g(x) = f(r), com r = x embrulhado em [-C/2, C/2);
 *  - dentro da faixa |d| < W, g mistura A = f(C/2 + d) (o conteúdo do lado "esquerdo" da costura)
 *    com B = f(-C/2 + d) (o do lado "direito"), com peso suave que vai de 0 (só A) a 1 (só B).
 *
 * d é a distância com sinal até a costura mais próxima: negativa antes dela (x perto de +C/2) e positiva
 * depois (x perto de -C/2, que é a mesma costura vista do outro lado). Como A e B só dependem de d, os dois
 * lados da costura calculam exatamente a mesma coisa: periodicidade e continuidade por construção.
 *
 * A mistura preserva variância (cos·A + sin·B): ruído independente misturado a 50% continuaria com desvio
 * de 0,7 numa média simples, o que achataria o relevo na faixa; assim o terreno da faixa tem a mesma "energia".
 */
public final class SeamMath {
    /** Meia largura padrão da faixa de mistura, em blocos (a faixa toda tem o dobro, ~3% da Terra). */
    public static final int DEFAULT_HALF_BAND = 1024;

    private SeamMath() {
    }

    /** Índice k tal que x - k*C fica em [-C/2, C/2). */
    public static int wrapIndex(int x, int circumference) {
        return Math.floorDiv(x + circumference / 2, circumference);
    }

    /** x embrulhado em [-C/2, C/2). */
    public static int canonical(int x, int circumference) {
        return x - wrapIndex(x, circumference) * circumference;
    }

    /** Distância com sinal até a costura (ver a descrição da classe): em [-C/2, C/2). */
    public static int seamOffset(int x, int circumference) {
        int r = canonical(x, circumference);
        return r >= 0 ? r - circumference / 2 : r + circumference / 2;
    }

    public static boolean inBand(int offset, int halfBand) {
        return Math.abs(offset) < halfBand;
    }

    /** Peso de B em [0, 1]: 0 em d <= -W, 1 em d >= W, smoothstep no meio (0,5 na costura). */
    public static double weightB(int offset, int halfBand) {
        if (offset <= -halfBand) {
            return 0.0;
        }
        if (offset >= halfBand) {
            return 1.0;
        }
        double t = (offset + halfBand) / (2.0 * halfBand);
        return t * t * (3.0 - 2.0 * t);
    }

    /** Deslocamento a somar a x para avaliar A (fora da faixa, é o próprio embrulho em [-C/2, C/2)). */
    public static int shiftA(int x, int circumference, int halfBand) {
        int d = seamOffset(x, circumference);
        if (!inBand(d, halfBand)) {
            return canonical(x, circumference) - x;
        }
        return circumference / 2 + d - x;
    }

    /** Deslocamento a somar a x para avaliar B; só faz sentido dentro da faixa. */
    public static int shiftB(int x, int circumference, int halfBand) {
        return -circumference / 2 + seamOffset(x, circumference) - x;
    }

    /** cos·a + sin·b com o ângulo em [0, pi/2] proporcional ao peso. */
    public static double mix(double a, double b, double weightB) {
        if (weightB <= 0.0) {
            return a;
        }
        if (weightB >= 1.0) {
            return b;
        }
        double angle = weightB * (Math.PI / 2.0);
        return Math.cos(angle) * a + Math.sin(angle) * b;
    }

    /** g(x) a partir de f: a referência de como o sampler avalia, usada nos testes. */
    public static double evaluate(IntToDoubleFunction f, int x, int circumference, int halfBand) {
        int d = seamOffset(x, circumference);
        if (!inBand(d, halfBand)) {
            return f.applyAsDouble(canonical(x, circumference));
        }
        double a = f.applyAsDouble(circumference / 2 + d);
        double b = f.applyAsDouble(-circumference / 2 + d);
        return mix(a, b, weightB(d, halfBand));
    }

    /** Distância em blocos de x até a costura mais próxima (sempre >= 0). */
    public static int distanceToSeam(int x, int circumference) {
        return Math.abs(seamOffset(x, circumference));
    }

    /** Verdadeiro se x está a menos de margin blocos da costura (de qualquer lado, inclusive além dela). */
    public static boolean nearSeam(int x, int circumference, int margin) {
        return distanceToSeam(x, circumference) < margin;
    }
}
