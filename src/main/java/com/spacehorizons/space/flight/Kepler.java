package com.spacehorizons.space.flight;

/**
 * Propagação analítica de dois corpos por variáveis universais (vale para elipse, parábola e hipérbole). É a "verdade"
 * contra a qual o integrador numérico é testado e o que as previsões (apoastro, entrada na esfera de influência) devem
 * usar: nunca prever integrando passo a passo.
 */
public final class Kepler {
    /** Estado (posição e velocidade) relativo ao corpo central. */
    public record State(V3 r, V3 v) {
    }

    private Kepler() {
    }

    /**
     * Estado depois de {@code dt} ticks (pode ser negativo) a partir de {@code r0}, {@code v0}, sob a gravidade
     * {@code mu} do corpo central, sem empuxo nem arrasto.
     */
    public static State propagate(double mu, V3 r0, V3 v0, double dt) {
        if (dt == 0.0) {
            return new State(r0, v0);
        }
        double r0n = r0.length();
        double v0n2 = v0.lengthSquared();
        double alpha = 2.0 / r0n - v0n2 / mu;
        double vr0 = r0.dot(v0) / r0n;
        double sqrtMu = Math.sqrt(mu);
        double target = sqrtMu * dt;

        // F(chi) é estritamente crescente (F' = r > 0): raiz única, achada por Newton protegido por bisseção
        double chi = solve(alpha, r0n, vr0, sqrtMu, target);
        double z = alpha * chi * chi;
        double c = stumpffC(z);
        double s = stumpffS(z);
        double f = 1.0 - chi * chi / r0n * c;
        double g = dt - chi * chi * chi / sqrtMu * s;
        V3 r = r0.times(f).plus(v0.times(g));
        double rn = r.length();
        double fDot = sqrtMu / (rn * r0n) * (z * s - 1.0) * chi;
        double gDot = 1.0 - chi * chi / rn * c;
        return new State(r, r0.times(fDot).plus(v0.times(gDot)));
    }

    private static double universalF(double chi, double alpha, double r0n, double vr0, double sqrtMu, double target) {
        double z = alpha * chi * chi;
        return r0n * vr0 / sqrtMu * chi * chi * stumpffC(z) + (1.0 - alpha * r0n) * chi * chi * chi * stumpffS(z)
                + r0n * chi - target;
    }

    private static double universalDf(double chi, double alpha, double r0n, double vr0, double sqrtMu) {
        double z = alpha * chi * chi;
        return r0n * vr0 / sqrtMu * chi * (1.0 - z * stumpffS(z)) + (1.0 - alpha * r0n) * chi * chi * stumpffC(z) + r0n;
    }

    private static double solve(double alpha, double r0n, double vr0, double sqrtMu, double target) {
        double guess = target / r0n;
        double lo;
        double hi;
        if (target > 0.0) {
            lo = 0.0;
            hi = Math.max(Math.abs(guess), 1.0e-9);
            for (int i = 0; i < 300 && universalF(hi, alpha, r0n, vr0, sqrtMu, target) < 0.0; i++) {
                hi *= 2.0;
            }
        } else {
            hi = 0.0;
            lo = -Math.max(Math.abs(guess), 1.0e-9);
            for (int i = 0; i < 300 && universalF(lo, alpha, r0n, vr0, sqrtMu, target) > 0.0; i++) {
                lo *= 2.0;
            }
        }
        double chi = Math.max(lo, Math.min(hi, guess));
        for (int i = 0; i < 200; i++) {
            double fv = universalF(chi, alpha, r0n, vr0, sqrtMu, target);
            if (fv > 0.0) {
                hi = chi;
            } else {
                lo = chi;
            }
            double next = chi - fv / universalDf(chi, alpha, r0n, vr0, sqrtMu);
            if (!(next > lo && next < hi)) {
                next = 0.5 * (lo + hi);
            }
            if (Math.abs(next - chi) <= 1.0e-14 * Math.max(1.0, Math.abs(chi))) {
                return next;
            }
            chi = next;
        }
        return chi;
    }

    /** Função de Stumpff C(z) = (1 − cos√z)/z, com série perto de 0 (a fórmula fechada perde precisão ali). */
    static double stumpffC(double z) {
        if (Math.abs(z) < 1.0e-2) {
            return 0.5 - z / 24.0 + z * z / 720.0 - z * z * z / 40_320.0 + z * z * z * z / 3_628_800.0;
        }
        if (z > 0.0) {
            return (1.0 - Math.cos(Math.sqrt(z))) / z;
        }
        return (Math.cosh(Math.sqrt(-z)) - 1.0) / -z;
    }

    /** Função de Stumpff S(z) = (√z − sin√z)/√z³, com série perto de 0. */
    static double stumpffS(double z) {
        if (Math.abs(z) < 1.0e-2) {
            return 1.0 / 6.0 - z / 120.0 + z * z / 5_040.0 - z * z * z / 362_880.0 + z * z * z * z / 39_916_800.0;
        }
        if (z > 0.0) {
            double s = Math.sqrt(z);
            return (s - Math.sin(s)) / (s * s * s);
        }
        double s = Math.sqrt(-z);
        return (Math.sinh(s) - s) / (s * s * s);
    }
}
