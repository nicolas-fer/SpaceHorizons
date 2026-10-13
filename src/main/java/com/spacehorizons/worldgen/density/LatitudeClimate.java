package com.spacehorizons.worldgen.density;

/**
 * Deslocamento do parâmetro de clima "temperature" (o do multi-noise, em torno de -1..1) por latitude.
 *
 * Soma-se à temperatura do ruído do Overworld: leve aquecimento no equador, esfriamento quadrático até os polos.
 * Perto do equador a curva é quase plana (t=0,2 muda ~0,05 em relação ao equador), então a distribuição
 * vanilla de biomas fica intacta; a ~63° (90% da barreira polar) o deslocamento passa de -1,3, o que leva
 * quase todo o terreno às faixas de gelo e neve do mapa de biomas.
 */
public final class LatitudeClimate {
    /** Aquecimento no equador (t = 0). */
    public static final float EQUATOR_WARMTH = 0.12F;
    /** Esfriamento adicional que o polo (t = 1) recebe em relação ao equador. */
    public static final float POLE_COOLING = 1.7F;

    private LatitudeClimate() {
    }

    /** Fração de latitude em [0, 1]: |z| / maxLatitudeBlocks, travada no polo. */
    public static double fraction(double z, double maxLatitudeBlocks) {
        return Math.min(1.0, Math.abs(z) / maxLatitudeBlocks);
    }

    /** Deslocamento em função da fração de latitude: monótono decrescente de EQUATOR_WARMTH até -POLE_COOLING. */
    public static float offsetForFraction(double t) {
        double c = Math.max(0.0, Math.min(1.0, t));
        return (float) (EQUATOR_WARMTH - (EQUATOR_WARMTH + POLE_COOLING) * c * c);
    }

    public static float offset(double z, double maxLatitudeBlocks) {
        return offsetForFraction(fraction(z, maxLatitudeBlocks));
    }
}
