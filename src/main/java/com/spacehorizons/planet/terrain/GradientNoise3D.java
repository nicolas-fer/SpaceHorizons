package com.spacehorizons.planet.terrain;

/**
 * Ruído de gradiente 3D (Perlin clássico) com permutação derivada de uma seed.
 * Java puro, sem estado mutável após a construção, então é seguro entre threads.
 */
final class GradientNoise3D {
    private static final int[] GRAD = {
            1, 1, 0, -1, 1, 0, 1, -1, 0, -1, -1, 0,
            1, 0, 1, -1, 0, 1, 1, 0, -1, -1, 0, -1,
            0, 1, 1, 0, -1, 1, 0, 1, -1, 0, -1, -1
    };

    /** Duplicada (512) para não precisar de máscara extra nos índices de hash. */
    private final int[] perm = new int[512];

    GradientNoise3D(long seed) {
        int[] base = new int[256];
        for (int i = 0; i < 256; i++) {
            base[i] = i;
        }
        // Fisher-Yates com splitmix64: a mesma seed sempre gera a mesma permutação
        long state = seed;
        for (int i = 255; i > 0; i--) {
            state = splitmix64(state);
            int j = (int) Math.floorMod(state, (long) (i + 1));
            int tmp = base[i];
            base[i] = base[j];
            base[j] = tmp;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = base[i & 255];
        }
    }

    /** Valor aproximadamente em [-1, 1]. */
    double noise(double x, double y, double z) {
        double fx = Math.floor(x);
        double fy = Math.floor(y);
        double fz = Math.floor(z);
        int xi = (int) fx & 255;
        int yi = (int) fy & 255;
        int zi = (int) fz & 255;
        double xf = x - fx;
        double yf = y - fy;
        double zf = z - fz;
        double u = fade(xf);
        double v = fade(yf);
        double w = fade(zf);

        int a = perm[xi] + yi;
        int aa = perm[a] + zi;
        int ab = perm[a + 1] + zi;
        int b = perm[xi + 1] + yi;
        int ba = perm[b] + zi;
        int bb = perm[b + 1] + zi;

        double x1 = lerp(grad(perm[aa], xf, yf, zf), grad(perm[ba], xf - 1, yf, zf), u);
        double x2 = lerp(grad(perm[ab], xf, yf - 1, zf), grad(perm[bb], xf - 1, yf - 1, zf), u);
        double y1 = lerp(x1, x2, v);

        double x3 = lerp(grad(perm[aa + 1], xf, yf, zf - 1), grad(perm[ba + 1], xf - 1, yf, zf - 1), u);
        double x4 = lerp(grad(perm[ab + 1], xf, yf - 1, zf - 1), grad(perm[bb + 1], xf - 1, yf - 1, zf - 1), u);
        double y2 = lerp(x3, x4, v);

        return lerp(y1, y2, w);
    }

    /** Soma de oitavas (fBm) normalizada para ficar em [-1, 1]. */
    double fbm(double x, double y, double z, int octaves) {
        double sum = 0;
        double amp = 1;
        double norm = 0;
        double freq = 1;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise(x * freq, y * freq, z * freq);
            norm += amp;
            amp *= 0.5;
            freq *= 2.0;
        }
        return sum / norm;
    }

    private static double grad(int hash, double x, double y, double z) {
        int g = (hash % 12) * 3;
        return GRAD[g] * x + GRAD[g + 1] * y + GRAD[g + 2] * z;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static long splitmix64(long state) {
        long z = state + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
