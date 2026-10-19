package com.spacehorizons.space;

/**
 * O referencial INERCIAL (I) e a ponte para o referencial PRESO À TERRA (E, o do {@link SolarSystem}).
 *
 * <p>E é o referencial de tudo que é "lugar": dimensões, renderização, coordenadas do jogador. Nele a Terra fica parada e o
 * sol dá uma volta por dia ({@link SunModel}). I é o referencial em que a física do foguete e a órbita da Lua valem: nele
 * o sol fica FIXO (em −X) e a Terra gira para o leste. Os dois compartilham a origem (o centro da Terra) e o eixo polar
 * (+Y); só diferem por um giro em torno de Y:
 * <pre>
 *   r_E = Ry(φ) · r_I,   φ(t) = ângulo_do_sol(t) − π,   Ry(φ) = [cos 0 sen; 0 1 0; −sen 0 cos]
 * </pre>
 * Com isso o sol de E é exatamente o de {@link SunModel#directionInSpace} ({@code Ry(φ)·(−1,0,0) = (cos a, 0, −sen a)}), o
 * que o deixa fixo em I, e a Terra gira em I a {@code dφ/dt} para o leste (uma direção fixa de I anda para o oeste em E,
 * como o sol). O giro herda a curva do sol do vanilla: a taxa varia de ~0,67 a ~1,19 vezes a média 2π/24.000 (ver
 * {@link SunModel#angularRate}); a conta fica coerente porque tudo (o sol, a Lua, o chão) usa este mesmo φ. Em
 * {@code clock = 18.000} (meia-noite do dia 0) φ = 0 e os dois referenciais coincidem.
 *
 * <p>Vetores (posição relativa ao centro da Terra, direção) vão de um referencial ao outro só pela rotação; a velocidade
 * ganha ainda o termo do giro: {@code v_E = Ry(φ)·v_I + φ̇·Ry'(φ)·r_I}. As taxas são por tick de RELÓGIO; quem converte
 * velocidade por tick de jogo passa {@code clockRate} (ticks de relógio por tick de jogo).
 */
public final class InertialFrame {
    private InertialFrame() {
    }

    /** Ângulo φ (rad) que leva I a E no instante {@code clock}: {@code r_E = Ry(φ)·r_I}. */
    public static double angle(double clock) {
        return SunModel.angle(clock) - Math.PI;
    }

    /** Velocidade angular de rotação da Terra em relação a I (rad por tick de relógio): a taxa do sol em E. */
    public static double rate(double clock) {
        return SunModel.angularRate(clock);
    }

    /** Matriz de rotação Ry(φ) em ordem de linhas (E = Ry(φ)·I). */
    public static double[] rotationY(double phi) {
        double c = Math.cos(phi);
        double s = Math.sin(phi);
        return new double[] {c, 0, s, 0, 1, 0, -s, 0, c};
    }

    /** Vetor de I → E no instante {@code clock}. {@code out} não pode ser um array de entrada. */
    public static void toEarthFixed(double clock, double x, double y, double z, double[] out) {
        double phi = angle(clock);
        double c = Math.cos(phi);
        double s = Math.sin(phi);
        out[0] = c * x + s * z;
        out[1] = y;
        out[2] = -s * x + c * z;
    }

    /** Vetor de E → I no instante {@code clock}. */
    public static void toInertial(double clock, double x, double y, double z, double[] out) {
        double phi = angle(clock);
        double c = Math.cos(phi);
        double s = Math.sin(phi);
        out[0] = c * x - s * z;
        out[1] = y;
        out[2] = s * x + c * z;
    }

    /**
     * Velocidade em E de um ponto que está em {@code r} (relativo ao centro da Terra, em I) e tem velocidade {@code v} em
     * I: {@code Ry(φ)·v + φ̇·Ry'(φ)·r}, com {@code φ̇} multiplicado por {@code clockRate} (blocos por tick de jogo, se
     * {@code v} também for).
     */
    public static void velocityToEarthFixed(double clock, double clockRate, double rx, double rz, double vx, double vy,
            double vz, double[] out) {
        double phi = angle(clock);
        double rate = rate(clock) * clockRate;
        double c = Math.cos(phi);
        double s = Math.sin(phi);
        out[0] = c * vx + s * vz + rate * (-s * rx + c * rz);
        out[1] = vy;
        out[2] = -s * vx + c * vz + rate * (-c * rx - s * rz);
    }

    /** Inverso de {@link #velocityToEarthFixed}: a mesma posição {@code r} (em I) e a velocidade em E → velocidade em I. */
    public static void velocityToInertial(double clock, double clockRate, double rx, double rz, double vx, double vy,
            double vz, double[] out) {
        double phi = angle(clock);
        double rate = rate(clock) * clockRate;
        double c = Math.cos(phi);
        double s = Math.sin(phi);
        double ex = vx - rate * (-s * rx + c * rz);
        double ez = vz - rate * (-c * rx - s * rz);
        out[0] = c * ex - s * ez;
        out[1] = vy;
        out[2] = s * ex + c * ez;
    }
}
