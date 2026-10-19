package com.spacehorizons.space.flight;

import com.spacehorizons.space.SolarSystem;

/**
 * Planejador da transferência Terra → Lua (TLI) a partir de uma órbita baixa da Terra: o "planejador" do painel.
 *
 * <h2>Como a conta é feita</h2>
 * A Hohmann até o raio da órbita lunar ({@link Hohmann}) leva {@code t_f} ticks e termina no apoastro, do lado oposto ao
 * ponto da queima. Para a Lua estar lá na chegada, no momento da queima ela precisa estar à frente da nave, no plano da
 * órbita, por um ÂNGULO DE FASE ideal {@code π − Δθ}, onde {@code Δθ} é o quanto ela anda em {@code t_f} (calculado com
 * a Lua REAL, que tem velocidade angular não uniforme, ±33%: o ideal varia de ~55° a ~100° conforme a hora do dia).
 * O planejador acha, resolvendo a equação com a Lua verdadeira, o instante da próxima janela e se é "queime agora".
 *
 * <h2>Sentido</h2>
 * No referencial do espaço a Lua orbita para o OESTE (ver {@link BodyPhysics#pole}). A nave tem que orbitar no mesmo
 * sentido: lançada para o leste, a captura lunar custaria ~17 blocos/tick. Em órbita para o leste o plano devolve
 * {@link Status#WRONG_DIRECTION}.
 *
 * <p>O alvo é o CENTRO da Lua: depois da TLI faça uma correção de meio de curso para passar a ~500 blocos de altitude. O
 * Δv e a fase supõem órbita quase circular no raio atual (excentricidade ≤ {@value #MAX_ECCENTRICITY}).
 */
public final class TransferPlanner {
    /** Meia largura da janela de queima (graus de fase): 4° são ~42.000 blocos na Lua, dentro da esfera de 96.270. */
    public static final double WINDOW_HALF_ANGLE_DEG = 4.0;
    /** Excentricidade máxima para o planejador valer (órbita "baixa e circular"). */
    public static final double MAX_ECCENTRICITY = 0.15;
    /** Raio máximo da órbita de saída: metade da distância à Lua. */
    public static final double MAX_ORBIT_RADIUS = 0.5 * SolarSystem.EARTH_MOON_DISTANCE;
    /** Acima da velocidade angular máxima da Lua (2,79e-4 rad por tick de relógio): limita a varredura de janelas. */
    private static final double MOON_MAX_ANGULAR_RATE = 3.0e-4;
    /** Passo da varredura em radianos de movimento da nave (0,05 rad ≈ 3° de órbita). */
    private static final double SCAN_STEP_RAD = 0.05;

    /** Situação do plano. Só {@link #OK} traz números. */
    public enum Status {
        /** Plano válido. */
        OK,
        /** O corpo dominante não é a Terra (já está perto da Lua, por exemplo). */
        NOT_AROUND_EARTH,
        /** Não está numa órbita baixa e razoavelmente circular da Terra (suborbital, elíptica demais, alta demais). */
        NOT_IN_ORBIT,
        /** Órbita no sentido contrário ao da Lua (leste): é preciso orbitar para o OESTE. */
        WRONG_DIRECTION
    }

    /**
     * Resultado do planejador (ângulos em graus, tempos em ticks, Δv em blocos/tick).
     *
     * @param westward a órbita atual segue para o oeste, o sentido da Lua
     * @param currentPhaseDeg ângulo (0..360) com que a Lua está à frente da nave no plano da órbita agora
     * @param idealPhaseDeg ângulo (0..360) que ela deveria ter à frente para a TLI dar certo agora
     * @param phaseErrorDeg atual menos ideal, em (−180, 180]: positivo = ainda falta (espere), negativo = a janela passou
     * @param tliDeltaV Δv prógrado da TLI a partir da velocidade atual
     * @param transferTicks tempo de voo da transferência (meio período da elipse)
     * @param timeToWindowTicks ticks até o centro da próxima janela (pode ser negativo dentro da janela)
     * @param burnNow estamos dentro de ±{@link #WINDOW_HALF_ANGLE_DEG} do ideal: queime agora, para o oeste
     * @param planeAngleDeg ângulo entre o plano da órbita e o da Lua (a Lua é inclinada 5° ao equador)
     */
    public record Plan(Status status, boolean westward, double currentPhaseDeg, double idealPhaseDeg, double phaseErrorDeg,
            double tliDeltaV, double transferTicks, double timeToWindowTicks, boolean burnNow, double planeAngleDeg) {

        /** Plano sem números (todos NaN). */
        static Plan unavailable(Status status, boolean westward, double planeAngleDeg) {
            double nan = Double.NaN;
            return new Plan(status, westward, nan, nan, nan, nan, nan, nan, false, planeAngleDeg);
        }

        public boolean usable() {
            return status == Status.OK;
        }
    }

    private TransferPlanner() {
    }

    /** {@link #moon(FlightState, double, double)} com o relógio a taxa 1. */
    public static Plan moon(FlightState s, double clock) {
        return moon(s, clock, 1.0);
    }

    /**
     * Plano da TLI para o estado {@code s} em {@code clock}. {@code clockRate} é a taxa do relógio por tick de jogo
     * (o futuro da Lua é {@code clock + clockRate · t}); com 0 a Lua não anda e o plano só espera a nave dar a volta.
     */
    public static Plan moon(FlightState s, double clock, double clockRate) {
        if (s.body() != SolarSystem.EARTH) {
            return Plan.unavailable(Status.NOT_AROUND_EARTH, false, Double.NaN);
        }
        BodyPhysics earth = BodyPhysics.of(SolarSystem.EARTH);
        V3 r = s.r();
        V3 v = s.v();
        double r1 = r.length();
        Orbit orbit = Orbit.of(s, clock);
        V3 h = r.cross(v);
        double[] rot = SolarSystem.MOON.poseAt(clock).rotation();
        V3 moonNormal = new V3(rot[1], rot[4], rot[7]);
        double planeAngle = Math.toDegrees(h.angleTo(moonNormal));
        if (!orbit.bound() || orbit.e() > MAX_ECCENTRICITY || orbit.periapsis() <= earth.radius() || r1 > MAX_ORBIT_RADIUS
                || h.length() < 1.0e-9) {
            return Plan.unavailable(Status.NOT_IN_ORBIT, h.dot(moonNormal) > 0.0, planeAngle);
        }
        boolean westward = h.dot(moonNormal) > 0.0;
        if (!westward) {
            return Plan.unavailable(Status.WRONG_DIRECTION, false, planeAngle);
        }

        double mu = earth.mu();
        double d = SolarSystem.EARTH_MOON_DISTANCE;
        double transfer = Hohmann.flightTime(mu, r1, d);
        double transferSpeed = Math.sqrt(mu * (2.0 / r1 - 2.0 / (r1 + d)));
        double dv = Math.max(0.0, transferSpeed - v.length());

        // base do plano da órbita: e1 = posição agora, e2 = sentido do movimento
        V3 e1 = r.unit();
        V3 e2 = h.unit().cross(e1);
        double n = h.length() / (r1 * r1);
        double moonRate = wrapPi(planeAngle(clock + 50.0 * clockRate, e1, e2) - planeAngle(clock - 50.0 * clockRate, e1, e2))
                / 100.0;
        double relativeRate = n - moonRate;
        if (relativeRate < 1.0e-6) {
            return Plan.unavailable(Status.NOT_IN_ORBIT, true, planeAngle);
        }
        double window = Math.toRadians(WINDOW_HALF_ANGLE_DEG) / relativeRate;

        // f(t) = (ângulo da Lua na chegada) − (ângulo do apoastro): zero quando queimar em t acerta a Lua. A janela se
        // repete a cada período SINÓDICO (a nave dá a volta na Lua), maior que o orbital; como a Lua acelera e freia, a
        // varredura vai até o pior caso (a Lua na velocidade angular máxima) e para na primeira raiz
        double span = 2.0 * Math.PI / Math.max(n - MOON_MAX_ANGULAR_RATE * Math.abs(clockRate), 1.0e-5);
        double t0 = -window;
        double dt = SCAN_STEP_RAD / n;
        int steps = (int) Math.ceil(span / dt) + 1;
        double previousT = t0;
        double previousF = arrivalError(clock, clockRate, t0, transfer, n, e1, e2);
        double root = Double.NaN;
        for (int i = 1; i <= steps && Double.isNaN(root); i++) {
            double t = t0 + i * dt;
            double f = arrivalError(clock, clockRate, t, transfer, n, e1, e2);
            if (previousF > 0.0 && f <= 0.0 && Math.abs(previousF) < 2.5 && Math.abs(f) < 2.5) {
                double lo = previousT;
                double hi = t;
                for (int k = 0; k < 50; k++) {
                    double mid = 0.5 * (lo + hi);
                    if (arrivalError(clock, clockRate, mid, transfer, n, e1, e2) > 0.0) {
                        lo = mid;
                    } else {
                        hi = mid;
                    }
                }
                root = 0.5 * (lo + hi);
            }
            previousT = t;
            previousF = f;
        }
        if (Double.isNaN(root)) {
            // não deve acontecer (a varredura cobre o pior caso), mas OK só vale com números válidos
            return Plan.unavailable(Status.NOT_IN_ORBIT, true, planeAngle);
        }

        double current = wrap2Pi(planeAngle(clock, e1, e2));
        double sweep = wrapPi(planeAngle(clock + clockRate * transfer, e1, e2) - planeAngle(clock, e1, e2));
        double ideal = wrap2Pi(Math.PI - sweep);
        double error = wrapPi(current - ideal);
        boolean burnNow = Math.abs(Math.toDegrees(error)) <= WINDOW_HALF_ANGLE_DEG;
        return new Plan(Status.OK, true, Math.toDegrees(current), Math.toDegrees(ideal), Math.toDegrees(error), dv, transfer,
                root, burnNow, planeAngle);
    }

    /** Erro de chegada em (−π, π]: onde a Lua estará na chegada menos onde fica o apoastro, queimando em t. */
    private static double arrivalError(double clock, double clockRate, double t, double transfer, double n, V3 e1, V3 e2) {
        return wrapPi(planeAngle(clock + clockRate * (t + transfer), e1, e2) - n * t - Math.PI);
    }

    /** Ângulo da Lua em {@code clock} projetada no plano (e1, e2), a partir de e1 para e2. */
    private static double planeAngle(double clock, V3 e1, V3 e2) {
        V3 m = SoiTracker.position(SolarSystem.MOON, clock);
        return Math.atan2(m.dot(e2), m.dot(e1));
    }

    static double wrapPi(double a) {
        double w = a % (2.0 * Math.PI);
        if (w > Math.PI) {
            w -= 2.0 * Math.PI;
        } else if (w <= -Math.PI) {
            w += 2.0 * Math.PI;
        }
        return w;
    }

    static double wrap2Pi(double a) {
        double w = a % (2.0 * Math.PI);
        return w < 0.0 ? w + 2.0 * Math.PI : w;
    }
}
