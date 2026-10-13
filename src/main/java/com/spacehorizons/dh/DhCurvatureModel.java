package com.spacehorizons.dh;

/**
 * Modelo puro (sem classes do DH) da curvatura que o Distant Horizons aplica nos LODs.
 *
 * O DH não tem raio em blocos: a config "earthCurveRatio" define R_dh = 6.371.000 / ratio no
 * shader (terrain/gl/vert.vert). O shader usa a altura ABSOLUTA do vértice (vPosition.y + minHeight)
 * como altura acima do raio, então o "nível zero" do DH é o Y=0 do Minecraft, não o nível do mar.
 * Por isso o raio que o DH enxerga ao nível do mar é R_dh + seaLevel.
 *
 * Esta classe reproduz essa conta para escolher o ratio e para testar o erro contra CurvatureMath.
 */
public final class DhCurvatureModel {
    /** Numerador fixo do DH: R_dh = EARTH_NUMERATOR / ratio. */
    public static final double EARTH_NUMERATOR = 6_371_000.0;

    private DhCurvatureModel() {
    }

    /**
     * Ratio inteiro (a config do DH só aceita inteiros) que aproxima o raio do planeta ao nível do mar.
     * A quantização deixa o raio do DH a até ~0,1% do real (EARTH: 615 contra 614,5). O teste de solo
     * em DhCurvatureModelTest fixa o erro abaixo de 1 bloco até 4.000 blocos.
     */
    public static int ratioFor(double planetRadius, int seaLevel) {
        return (int) Math.round(EARTH_NUMERATOR / (planetRadius - seaLevel));
    }

    /** Raio que o DH usa no shader (uEarthRadius) para um ratio. */
    public static double dhEarthRadius(int ratio) {
        return EARTH_NUMERATOR / ratio;
    }

    /**
     * Replica do vertex shader do DH para um vértice no solo/altura {@code heightAboveSea} a
     * {@code distance} blocos do ponto tangente. Saída igual à de CurvatureMath.curve (mesmo frame:
     * câmera na origem), para que o erro entre as duas possa ser medido.
     */
    public static void dhCurve(double uEarthRadius, int seaLevel, double cameraAltitude,
            double distance, double heightAboveSea, double[] out) {
        double absY = seaLevel + heightAboveSea;
        double relY = absY - (seaLevel + cameraAltitude);
        double localRadius = uEarthRadius + absY;
        double phi = distance / localRadius;
        // sin(phi)/phi tende a 1 quando phi -> 0; evita 0/0 no ponto sob a câmera
        out[0] = phi < 1.0e-12 ? distance : distance * Math.sin(phi) / phi;
        out[1] = relY + (Math.cos(phi) - 1.0) * localRadius;
    }
}
