package com.spacehorizons.planet;

/**
 * Projeção esférica exata, espelho em Java de assets/spacehorizons/shaders/include/curvature.glsl.
 * Chunks, LODs do DH e qualquer código de CPU (colisão visual, picking) devem usar esta mesma fórmula.
 *
 * Entrada: posição relativa à câmera num mundo plano. A câmera está sobre o ponto tangente da esfera,
 * a camAlt blocos acima do nível do mar. Saída: posição relativa à câmera depois de "dobrar" o mundo.
 *
 * y' = h·cosθ − 2R·sin²(θ/2) − camAlt, onde h = y + camAlt e θ = dist/R. A forma com sin² evita
 * o cancelamento catastrófico de (R+h)·cosθ − R quando R é grande e se roda em float.
 */
public final class CurvatureMath {
    private CurvatureMath() {
    }

    public static void curve(double radius, double cameraAltitude, double x, double y, double z, double[] out) {
        double dist = Math.sqrt(x * x + z * z);
        if (dist < 1.0e-6) {
            out[0] = x;
            out[1] = y;
            out[2] = z;
            return;
        }
        double theta = dist / radius;
        double h = y + cameraAltitude;
        double r = radius + h;
        double scale = r * Math.sin(theta) / dist;
        double halfSin = Math.sin(theta * 0.5);
        out[0] = x * scale;
        out[1] = h * Math.cos(theta) - 2.0 * radius * halfSin * halfSin - cameraAltitude;
        out[2] = z * scale;
    }
}
