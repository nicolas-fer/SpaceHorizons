package com.spacehorizons.space.flight;

/**
 * Elementos orbitais (dois corpos) de um estado relativo ao corpo dominante.
 *
 * <ul>
 *   <li>{@code a}: semieixo maior (negativo na hipérbole; infinito na parábola);</li>
 *   <li>{@code e}: excentricidade (circular &lt; 1e-9; ligada &lt; 1; hipérbole &gt; 1);</li>
 *   <li>{@code inclination}: ângulo (rad, 0..π) entre o momento angular e o polo de referência {@code P}. Com
 *       P = −norte (ver {@link BodyPhysics#pole}), uma órbita para LESTE dá ~0 e para OESTE ~π;</li>
 *   <li>{@code trueAnomaly}: anomalia verdadeira atual, em [0, 2π);</li>
 *   <li>{@code periapsis}, {@code apoapsis}: distâncias ao centro (o apoastro é infinito se não ligada). Para
 *       altitudes subtraia o raio do corpo;</li>
 *   <li>{@code period}: período em ticks (infinito se não ligada);</li>
 *   <li>{@code bound}: energia negativa.</li>
 * </ul>
 *
 * Java puro, sem estado; as fórmulas são as clássicas (vetor excentricidade, vis-viva, equação de Kepler).
 */
public record Orbit(double mu, double a, double e, double inclination, double trueAnomaly, double periapsis,
        double apoapsis, double period, boolean bound) {
    /** Abaixo disto a órbita é tratada como circular (anomalia verdadeira 0 por convenção). */
    public static final double CIRCULAR_E = 1.0e-9;
    /** Abaixo disto a energia é considerada nula (parábola): {@code a} infinito. */
    private static final double PARABOLIC_ENERGY = 1.0e-12;

    /**
     * Elementos a partir de {@code r} e {@code v} relativos ao corpo, nos eixos do espaço, e do polo {@code pole}
     * (P = −norte do corpo, ver {@link BodyPhysics#pole}).
     */
    public static Orbit fromState(double mu, V3 r, V3 v, V3 pole) {
        V3 h = r.cross(v);
        double rn = r.length();
        double vn = v.length();
        double hn = h.length();
        V3 ev = v.cross(h).times(1.0 / mu).minus(r.times(1.0 / rn));
        double e = ev.length();
        double energy = 0.5 * vn * vn - mu / rn;
        boolean parabolic = Math.abs(energy) < PARABOLIC_ENERGY;
        boolean bound = energy < 0.0 && !parabolic;
        if (bound) {
            e = Math.min(e, 1.0);
        } else {
            e = Math.max(e, 1.0);
        }
        double a = parabolic ? Double.POSITIVE_INFINITY : -mu / (2.0 * energy);
        double inclination = hn < 1.0e-12 ? 0.0 : Math.acos(clamp(h.dot(pole) / (hn * pole.length()), -1.0, 1.0));
        double nu = 0.0;
        if (e > CIRCULAR_E) {
            nu = Math.acos(clamp(ev.dot(r) / (e * rn), -1.0, 1.0));
            if (r.dot(v) < 0.0) {
                nu = 2.0 * Math.PI - nu;
            }
        }
        double periapsis;
        double apoapsis;
        double period;
        if (parabolic) {
            periapsis = hn * hn / (2.0 * mu);
            apoapsis = Double.POSITIVE_INFINITY;
            period = Double.POSITIVE_INFINITY;
        } else {
            // a(1 − e) e a(1 + e) valem também na órbita radial degenerada (h = 0), onde p = h²/mu daria 0/0
            periapsis = a * (1.0 - e);
            apoapsis = bound ? a * (1.0 + e) : Double.POSITIVE_INFINITY;
            period = bound ? 2.0 * Math.PI * Math.sqrt(a * a * a / mu) : Double.POSITIVE_INFINITY;
        }
        return new Orbit(mu, a, e, inclination, nu, periapsis, apoapsis, period, bound);
    }

    /** Elementos do estado de voo em {@code clock} (o polo vem do corpo dominante). */
    public static Orbit of(FlightState s, double clock) {
        BodyPhysics bp = BodyPhysics.of(s.body());
        return fromState(bp.mu(), s.r(), s.v(), bp.pole(clock));
    }

    /** Sentido de LESTE (inclinação &lt; 90°) com o polo P = −norte; senão OESTE. */
    public boolean eastward() {
        return inclination < 0.5 * Math.PI;
    }

    /** Semilatus rectum {@code p = a(1 − e²)} (distância ao centro em ν = ±90°). */
    public double semiLatusRectum() {
        return Double.isInfinite(a) ? 2.0 * periapsis : a * (1.0 - e * e);
    }

    /** Velocidade (vis-viva) à distância {@code r} do centro. */
    public double speedAt(double r) {
        double invA = Double.isInfinite(a) ? 0.0 : 1.0 / a;
        return Math.sqrt(Math.max(0.0, mu * (2.0 / r - invA)));
    }

    /** Movimento médio (rad/tick) de uma órbita ligada. */
    public double meanMotion() {
        return Math.sqrt(mu / Math.abs(a * a * a));
    }

    /**
     * Ticks até a anomalia verdadeira {@code target} (rad). Na elipse é sempre {@code >= 0} (próxima passagem); na
     * hipérbole pode ser negativo (já passou). NaN na parábola e para ângulos que a hipérbole não alcança (além das
     * assíntotas). Serve a "tempo até o apoastro/periastro" ({@code timeTo(π)}, {@code timeTo(0)}) e a "queime em T−x".
     */
    public double timeTo(double target) {
        if (Double.isInfinite(a)) {
            return Double.NaN;
        }
        if (bound) {
            double n = meanMotion();
            double d = (meanAnomaly(target) - meanAnomaly(trueAnomaly)) % (2.0 * Math.PI);
            if (d < 0.0) {
                d += 2.0 * Math.PI;
            }
            return d / n;
        }
        double now = hyperbolicMeanAnomaly(trueAnomaly);
        double then = hyperbolicMeanAnomaly(target);
        return (then - now) / meanMotion();
    }

    /** Anomalia média de uma elipse para a anomalia verdadeira {@code nu}. */
    private double meanAnomaly(double nu) {
        double ecc = Math.min(e, 1.0);
        double eccentric = 2.0 * Math.atan2(Math.sqrt(Math.max(0.0, 1.0 - ecc)) * Math.sin(0.5 * nu),
                Math.sqrt(1.0 + ecc) * Math.cos(0.5 * nu));
        return eccentric - ecc * Math.sin(eccentric);
    }

    /** Anomalia média da hipérbole; NaN além das assíntotas. */
    private double hyperbolicMeanAnomaly(double nu) {
        double wrapped = Math.atan2(Math.sin(nu), Math.cos(nu));
        double x = Math.sqrt((e - 1.0) / (e + 1.0)) * Math.tan(0.5 * wrapped);
        if (Math.abs(x) >= 1.0) {
            return Double.NaN;
        }
        // Math.atanh não existe no Java: atanh(x) = ½ ln((1+x)/(1−x))
        double f = Math.log((1.0 + x) / (1.0 - x));
        return e * Math.sinh(f) - f;
    }

    /**
     * Anomalia verdadeira (0..π, o ramo de SAÍDA; o de chegada é o negativo) em que o raio vale {@code r}; NaN se a
     * órbita não alcança esse raio (ou é circular com outro raio). Serve a "entrada na esfera de influência" e "impacto".
     */
    public double trueAnomalyAtRadius(double r) {
        if (e < CIRCULAR_E) {
            return Math.abs(r - periapsis) < 1.0e-6 * periapsis ? 0.0 : Double.NaN;
        }
        double cos = (semiLatusRectum() / r - 1.0) / e;
        // tolerância de arredondamento: exatamente no periastro ou no apoastro o cosseno pode passar de 1 por 1e-16
        if (Double.isNaN(cos) || cos < -1.0 - 1.0e-9 || cos > 1.0 + 1.0e-9) {
            return Double.NaN;
        }
        return Math.acos(clamp(cos, -1.0, 1.0));
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
