package com.spacehorizons.space.flight;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import com.spacehorizons.space.Heliocentric;
import com.spacehorizons.space.SolarSystem;

/**
 * Planejador da viagem Terra → Marte (sistema solar centrado no sol), no mesmo padrão de {@link TransferPlanner} (a janela e o Δv da
 * injeção, no painel) e de {@link LunarPlanner} (a previsão da chegada e a correção de meio de curso).
 *
 * <p>A previsão usa EXATAMENTE as esferas de influência da física ({@link SoiTracker}) e a propagação analítica de dois corpos
 * ({@link Kepler}): a Terra até sair da esfera dela (105% de Laplace), o sol dali até entrar na de Marte (95%). Nunca integra passo a
 * passo: uma viagem de 100.000 ticks custa algumas centenas de resoluções da equação de Kepler. O relógio tem taxa 1 (um tick de jogo
 * por tick de relógio): o planejador não vale com o relógio pausado.
 *
 * <h2>Geometria (por que a janela não é a de Hohmann)</h2>
 * O sistema é plano (Terra e Marte orbitam no equador da Terra), então a queima de saída de uma órbita equatorial para o LESTE já está no
 * plano certo. A esfera da Terra é finita e grande (~834.000 blocos, 21% do raio da órbita dela): a saída é uma TROCA DE ORIGEM, não uma
 * assíntota. A nave sai da esfera do lado do apoastro da órbita de fuga, com a velocidade que tiver ali, e o que vale é o estado
 * heliocêntrico naquele ponto: a Terra mais o vetor posição de ~875.000 blocos, e a velocidade da Terra mais a que sobrou. O Δv mínimo
 * é o de uma elipse cujo apoastro é o raio de saída (30 b/t a partir de 5.600 de altitude); um pouco acima dele a nave sai com poucos
 * blocos/tick relativos à Terra e segue uma elipse heliocêntrica que cruza a órbita de Marte em ~75.000 a ~90.000 ticks. Qualquer ponto da
 * órbita baixa entre ~165° e ~300° (do lado do sol da Terra, depois do meio da órbita) serve, cada um com a sua fase ideal entre a Terra
 * e Marte (de ~34° a ~66° de Marte à frente da Terra): a busca é numérica sobre a própria física ({@link #neededPhase}), não a fórmula
 * de Hohmann.
 */
public final class MarsPlanner {
    private static final BodyPhysics EARTH = BodyPhysics.of(SolarSystem.EARTH);
    private static final BodyPhysics MARS = BodyPhysics.of(SolarSystem.MARS);
    private static final BodyPhysics SUN = BodyPhysics.of(SolarSystem.SUN);
    private static final V3 POLE = new V3(0.0, -1.0, 0.0);

    /** Passo da varredura heliocêntrica longe de Marte (ticks): a esfera de Marte tem 494.000 de raio de entrada. */
    private static final double SCAN_STEP = 1_000.0;
    /** Prazo padrão da previsão da chegada (ticks): a viagem leva ~90.000. */
    public static final double DEFAULT_HORIZON = 300_000.0;
    /** Margem acima do Δv mínimo (blocos/tick) que o painel sugere: a nave sai da esfera da Terra a ~11 blocos/tick relativos. */
    public static final double INJECTION_MARGIN = 1.0;
    /** Meia largura da janela de queima (graus de fase entre a Terra e Marte): o que a correção de meio de curso ainda absorve. */
    public static final double WINDOW_HALF_ANGLE_DEG = 2.0;
    /** Excentricidade máxima da órbita baixa para o planejador valer. */
    public static final double MAX_ECCENTRICITY = 0.15;
    /** Faixa de pontos de queima (ângulo da nave na órbita em I, graus) em que a transferência tem Δv mínimo. */
    static final double BURN_ARC_START_DEG = 165.0;
    static final double BURN_ARC_END_DEG = 300.0;
    private static final double TABLE_STEP_DEG = 2.5;

    private MarsPlanner() {
    }

    // ------------------------------------------------------------------ chegada

    /**
     * A passagem pela esfera de Marte. {@code ticksToEntry} é o tempo até a entrada e {@code ticksToPeriapsis} até o periastro (a partir do
     * instante da previsão); {@code periapsisAltitude} é sobre o nível do mar de Marte. {@code relativeSpeed} é a velocidade relativa a Marte
     * na entrada, {@code bSigned} o parâmetro de impacto da hipérbole com sinal (positivo = órbita PRÓGRADA em torno de Marte, o sentido da
     * rotação dele) e {@code inclinationDeg} a inclinação dela.
     */
    public record Arrival(double ticksToEntry, double ticksToPeriapsis, double periapsisAltitude, double relativeSpeed, double bSigned,
            double inclinationDeg) {
        public boolean prograde() {
            return bSigned > 0.0;
        }
    }

    /** Correção: o Δv inercial (em H, eixos do estado) a aplicar agora e a chegada prevista depois dele. */
    public record Correction(V3 deltaV, Arrival arrival, int iterations) {
    }

    /** O estado heliocêntrico de uma nave que saiu da esfera da Terra e o tempo (ticks, desde o instante da previsão) em que isso aconteceu. */
    private record SunState(FlightState state, double offset) {
    }

    /** O estado de Marte (esfera dele) na entrada: relativo a Marte, e quando. */
    private record Entry(double ticks, FlightState state) {
    }

    /**
     * Prevê a chegada a Marte do estado {@code s} (dominado pela Terra ou pelo sol) em {@code clock}, até {@code horizon} ticks adiante.
     * Null se a nave não entra na esfera de Marte nesse prazo.
     */
    public static @Nullable Arrival arrival(FlightState s, double clock, double horizon) {
        Entry entry = entry(s, clock, horizon);
        if (entry == null) {
            return null;
        }
        FlightState m = entry.state();
        Orbit o = Orbit.fromState(MARS.mu(), m.r(), m.v(), POLE);
        V3 h = m.r().cross(m.v());
        double vInf2 = m.v().lengthSquared() - 2.0 * MARS.mu() / m.r().length();
        double b = vInf2 > 1.0e-9 ? h.length() / Math.sqrt(vInf2) : h.length();
        double toPeri = o.timeTo(0.0);
        return new Arrival(entry.ticks(), entry.ticks() + (Double.isNaN(toPeri) ? 0.0 : toPeri), o.periapsis() - MARS.radius(), m.v().length(),
                h.dot(POLE) >= 0.0 ? b : -b, Math.toDegrees(o.inclination()));
    }

    private static @Nullable SunState toSun(FlightState s, double clock, double horizon) {
        return toSun(s, clock, horizon, true);
    }

    /** A nave bate na esfera da Lua antes de sair da da Terra? Varre a perna da Terra por Kepler (a Lua anda em círculo, em I). */
    private static boolean crossesMoon(FlightState s, double clock, double exit) {
        BodyPhysics moon = BodyPhysics.of(SolarSystem.MOON);
        double enter = moon.soiEnterRadius();
        for (double t = 0.0; t <= exit; t += 250.0) {
            Kepler.State k = Kepler.propagate(EARTH.mu(), s.r(), s.v(), t);
            if (k.r().minus(SoiTracker.position(SolarSystem.MOON, clock + t)).length() < enter) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable SunState toSun(FlightState s, double clock, double horizon, boolean checkMoon) {
        if (s.body() == SolarSystem.SUN) {
            return new SunState(s, 0.0);
        }
        if (s.body() != SolarSystem.EARTH) {
            return null;
        }
        double exit = exitTime(s);
        if (Double.isNaN(exit) || exit < 0.0 || exit > horizon) {
            return null;
        }
        if (checkMoon && crossesMoon(s, clock, exit)) {
            return null; // a Lua está no caminho: esta janela não vale
        }
        Kepler.State k = Kepler.propagate(EARTH.mu(), s.r(), s.v(), exit + 0.01);
        FlightState sun = SoiTracker.update(s.withRV(k.r(), k.v()), clock + exit + 0.01, 1.0);
        if (sun.body() != SolarSystem.SUN) {
            return null;
        }
        return new SunState(sun, exit + 0.01);
    }

    /**
     * O estado de Marte na entrada da esfera (relativo a ele) e quando. Primeiro a saída da esfera da Terra (se está nela), por Kepler;
     * depois a varredura heliocêntrica até a distância a Marte cair abaixo do raio de entrada.
     */
    private static @Nullable Entry entry(FlightState s, double clock, double horizon) {
        SunState ss = toSun(s, clock, horizon);
        if (ss == null) {
            return null;
        }
        FlightState sun = ss.state();
        double t0 = ss.offset();
        double enter = MARS.soiEnterRadius();
        if (distanceToMars(sun, clock + t0, 0.0) < enter) {
            return entryAt(sun, clock + t0, t0, 0.0);
        }
        double previous = 0.0;
        double span = horizon - t0;
        double dt = SCAN_STEP;
        while (dt <= span + SCAN_STEP) {
            double d = distanceToMars(sun, clock + t0, dt);
            if (d < enter) {
                double lo = previous;
                double hi = dt;
                for (int i = 0; i < 50; i++) {
                    double mid = 0.5 * (lo + hi);
                    if (distanceToMars(sun, clock + t0, mid) < enter) {
                        hi = mid;
                    } else {
                        lo = mid;
                    }
                }
                return entryAt(sun, clock + t0, t0, hi);
            }
            previous = dt;
            // longe de Marte o passo é grande; perto, fino (a nave e Marte andam dezenas de blocos/tick um em relação ao outro)
            dt += d > 4.0 * enter ? SCAN_STEP : 0.25 * SCAN_STEP;
        }
        return null;
    }

    /** Ticks até a nave (na Terra) sair da esfera dela, ou NaN. */
    private static double exitTime(FlightState s) {
        double exitR = EARTH.soiExitRadius();
        if (s.r().length() > exitR) {
            return 0.0;
        }
        Orbit o = Orbit.fromState(EARTH.mu(), s.r(), s.v(), POLE);
        double nu = o.trueAnomalyAtRadius(exitR);
        if (Double.isNaN(nu)) {
            return Double.NaN;
        }
        return o.timeTo(nu);
    }

    /** Distância (blocos) da nave em {@code dt} ticks (Kepler em torno do sol) ao centro de Marte, no relógio {@code clock + dt}. */
    private static double distanceToMars(FlightState sun, double clock, double dt) {
        Kepler.State k = Kepler.propagate(SUN.mu(), sun.r(), sun.v(), dt);
        double[] mars = new double[3];
        Heliocentric.marsPosition(clock + dt, mars);
        double dx = k.r().x() - mars[0];
        double dy = k.r().y() - mars[1];
        double dz = k.r().z() - mars[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static Entry entryAt(FlightState sun, double clock, double offset, double dt) {
        Kepler.State k = Kepler.propagate(SUN.mu(), sun.r(), sun.v(), dt);
        double[] m = new double[3];
        double[] mv = new double[3];
        Heliocentric.marsPosition(clock + dt, m);
        Heliocentric.marsVelocity(clock + dt, mv);
        V3 r = k.r().minus(V3.of(m));
        V3 v = k.v().minus(V3.of(mv));
        return new Entry(offset + dt, new FlightState(SolarSystem.MARS, r, v, sun.fuel(), 0.0, 0.0));
    }

    // ------------------------------------------------------------------ erro de pontaria e correções

    /**
     * Erro de pontaria COM SINAL (blocos) do estado {@code s} em {@code clock}: o parâmetro de impacto da hipérbole de chegada se a nave
     * entra na esfera de Marte, senão a menor distância da trajetória heliocêntrica ao centro de Marte (a nave passa por fora da esfera).
     * Positivo = passa do lado que dá órbita PRÓGRADA em torno de Marte. NaN se a trajetória nunca chega perto (3 esferas) no prazo.
     */
    public static double signedMiss(FlightState s, double clock, double horizon) {
        Arrival a = arrival(s, clock, horizon);
        if (a != null) {
            return a.bSigned();
        }
        SunState ss = toSun(s, clock, horizon);
        if (ss == null) {
            return Double.NaN;
        }
        double best = Double.POSITIVE_INFINITY;
        double bestSign = 1.0;
        double[] mars = new double[3];
        double[] marsV = new double[3];
        for (double dt = 0.0; dt <= horizon - ss.offset(); dt += SCAN_STEP * 0.5) {
            Kepler.State k = Kepler.propagate(SUN.mu(), ss.state().r(), ss.state().v(), dt);
            double c = clock + ss.offset() + dt;
            Heliocentric.marsPosition(c, mars);
            V3 rel = k.r().minus(V3.of(mars));
            double d = rel.length();
            if (d < best) {
                Heliocentric.marsVelocity(c, marsV);
                best = d;
                bestSign = rel.cross(k.v().minus(V3.of(marsV))).dot(POLE) >= 0.0 ? 1.0 : -1.0;
            }
        }
        return best > 3.0 * MARS.soiRadius() ? Double.NaN : bestSign * best;
    }

    /** O parâmetro de impacto (módulo, blocos) da hipérbole de chegada que dá o periastro {@code periapsisRadius} para a velocidade {@code vInf}. */
    static double impactForPeriapsis(double periapsisRadius, double vInf) {
        return periapsisRadius * Math.sqrt(1.0 + 2.0 * MARS.mu() / (periapsisRadius * vInf * vInf));
    }

    /** O parâmetro de impacto com sinal que dá o periastro de altitude {@code periapsisAltitude} para uma chegada a {@code vInf}. */
    private static double targetImpact(double periapsisAltitude, double vInf, boolean prograde) {
        double b = impactForPeriapsis(MARS.radius() + periapsisAltitude, vInf);
        return prograde ? b : -b;
    }

    /**
     * O Δv (módulo) ao longo de {@code direction} (unitária, nos eixos do estado) que leva o erro de pontaria ao {@code target} (blocos, com
     * sinal), a partir do Δv {@code from} até {@code to} em passos de {@code step}: acha o primeiro cruzamento do alvo e o refina por
     * bissecção. NaN se não há raiz na faixa.
     */
    private static double solveAlong(FlightState s, double clock, V3 direction, double target, double from, double to, double step, double horizon) {
        double previousX = from;
        double previousF = miss(s, clock, direction, from, target, horizon);
        for (double x = from + step; x <= to + 1.0e-9; x += step) {
            double f = miss(s, clock, direction, x, target, horizon);
            if (!Double.isNaN(previousF) && !Double.isNaN(f) && previousF * f <= 0.0 && Math.abs(previousF) + Math.abs(f) < 4.0 * MARS.soiRadius()) {
                double lo = previousX;
                double hi = x;
                double flo = previousF;
                for (int i = 0; i < 40; i++) {
                    double mid = 0.5 * (lo + hi);
                    double fm = miss(s, clock, direction, mid, target, horizon);
                    if (Double.isNaN(fm)) {
                        break;
                    }
                    if (flo * fm <= 0.0) {
                        hi = mid;
                    } else {
                        lo = mid;
                        flo = fm;
                    }
                    if (hi - lo < 1.0e-6) {
                        break;
                    }
                }
                return 0.5 * (lo + hi);
            }
            previousX = x;
            previousF = f;
        }
        return Double.NaN;
    }

    private static double miss(FlightState s, double clock, V3 direction, double x, double target, double horizon) {
        return signedMiss(s.withRV(s.r(), s.v().plus(direction.times(x))), clock, horizon) - target;
    }

    /**
     * Correção de meio de curso: o Δv (prógrado ou retrógrado, ao longo da velocidade, ou radial no plano) de menor módulo que leva o periastro
     * da chegada a {@code targetPeriapsisAltitude} no sentido pedido. {@code state} é do sol (a nave já saiu da Terra). Null se nenhuma
     * correção até {@code maxDeltaV} resolve.
     */
    public static @Nullable Correction correction(FlightState s, double clock, double targetPeriapsisAltitude, boolean prograde, double maxDeltaV,
            double horizon) {
        Arrival now = arrival(s, clock, horizon);
        double vInf = now != null ? Math.sqrt(Math.max(1.0, now.relativeSpeed() * now.relativeSpeed() - 2.0 * MARS.mu() / MARS.soiEnterRadius())) : 35.0;
        double target = targetImpact(targetPeriapsisAltitude, vInf, prograde);
        V3 along = s.v().unit();
        V3 radial = along.cross(POLE).unit();
        double best = Double.NaN;
        V3 bestDirection = null;
        for (V3 direction : new V3[] {along, along.negate(), radial, radial.negate()}) {
            double x = solveAlong(s, clock, direction, target, 0.0, maxDeltaV, maxDeltaV / 40.0, horizon);
            if (!Double.isNaN(x) && (Double.isNaN(best) || x < best)) {
                best = x;
                bestDirection = direction;
            }
        }
        if (bestDirection == null) {
            return null;
        }
        // o alvo do parâmetro de impacto depende da velocidade de chegada, que a correção muda: refaz com a velocidade nova (até 3 vezes)
        Arrival after = arrival(s.withRV(s.r(), s.v().plus(bestDirection.times(best))), clock, horizon);
        int iterations = 0;
        while (after != null && iterations < 3 && Math.abs(after.periapsisAltitude() - targetPeriapsisAltitude) > 150.0) {
            iterations++;
            double v = Math.sqrt(Math.max(1.0, after.relativeSpeed() * after.relativeSpeed() - 2.0 * MARS.mu() / MARS.soiEnterRadius()));
            double x = solveAlong(s, clock, bestDirection, targetImpact(targetPeriapsisAltitude, v, prograde), Math.max(-maxDeltaV, best - 1.0),
                    Math.min(maxDeltaV, best + 1.0), 0.02, horizon);
            if (Double.isNaN(x)) {
                break;
            }
            best = x;
            after = arrival(s.withRV(s.r(), s.v().plus(bestDirection.times(best))), clock, horizon);
        }
        return after == null ? null : new Correction(bestDirection.times(best), after, iterations);
    }

    /**
     * O Δv prógrado (módulo) que, aplicado AGORA a um estado em órbita da Terra, leva a chegada a Marte ao periastro de altitude
     * {@code targetPeriapsisAltitude} no sentido pedido; NaN se a janela não está aberta neste ponto da órbita (nenhum Δv até 8 b/t acima do
     * mínimo acerta).
     */
    public static double injectionDeltaV(FlightState s, double clock, double targetPeriapsisAltitude, boolean prograde) {
        double min = escapeSpeedGap(s);
        double target = targetImpact(targetPeriapsisAltitude, 35.0, prograde);
        V3 along = s.v().unit();
        double first = solveAlong(s, clock, along, target, Math.max(0.0, min - 0.05), min + 8.0, 0.2, DEFAULT_HORIZON);
        if (Double.isNaN(first)) {
            return Double.NaN;
        }
        // refina com a velocidade de chegada real (o alvo do parâmetro de impacto depende dela)
        Arrival a = arrival(s.withRV(s.r(), s.v().plus(along.times(first))), clock, DEFAULT_HORIZON);
        if (a == null) {
            return first;
        }
        double vInf = Math.sqrt(Math.max(1.0, a.relativeSpeed() * a.relativeSpeed() - 2.0 * MARS.mu() / MARS.soiEnterRadius()));
        double second = solveAlong(s, clock, along, targetImpact(targetPeriapsisAltitude, vInf, prograde), Math.max(0.0, first - 1.0), first + 1.0, 0.05,
                DEFAULT_HORIZON);
        return Double.isNaN(second) ? first : second;
    }

    /**
     * Δv prógrado mínimo que leva a nave (numa órbita baixa quase circular) a SAIR da esfera da Terra: o de uma elipse cujo apoastro é o raio
     * de saída. Menos que isso a nave fica presa à Terra.
     */
    static double escapeSpeedGap(FlightState s) {
        double r = s.r().length();
        double exit = EARTH.soiExitRadius();
        double needed = Math.sqrt(EARTH.mu() * (2.0 / r - 2.0 / (r + exit)));
        return Math.max(0.0, needed - s.v().length());
    }

    // ------------------------------------------------------------------ painel: janela e Δv da injeção

    /** Situação do plano. Só {@link #OK} traz números. */
    public enum Status {
        OK,
        /** O corpo dominante não é a Terra (a nave já está na Lua, em Marte ou no espaço interplanetário). */
        NOT_AROUND_EARTH,
        /** Não está numa órbita baixa e razoavelmente circular da Terra (suborbital, elíptica demais, alta demais). */
        NOT_IN_ORBIT,
        /** Órbita no sentido contrário ao dos planetas (oeste): é preciso orbitar para o LESTE. */
        WRONG_DIRECTION
    }

    /**
     * Resultado do planejador (ângulos em graus, tempos em ticks, Δv em blocos/tick).
     *
     * @param prograde a órbita atual segue para o leste
     * @param currentPhaseDeg quanto Marte está à frente da Terra na órbita em volta do sol (0..360) agora
     * @param idealPhaseDeg a fase ideal para queimar a partir do ponto em que a nave está agora (NaN fora da faixa de queima)
     * @param phaseErrorDeg atual menos ideal, em (−180, 180]
     * @param injectionDeltaV Δv prógrado da injeção a partir da velocidade atual (o mínimo para sair da esfera da Terra mais a margem)
     * @param transferTicks tempo de voo previsto até a esfera de Marte, para a injeção no ponto de queima atual
     * @param timeToWindowTicks ticks até a próxima queima possível (a nave no ponto certo da órbita com a fase certa); 0 se é agora
     * @param burnNow estamos dentro de ±{@link #WINDOW_HALF_ANGLE_DEG} da fase ideal e dentro da faixa de queima: queime agora, para o leste
     * @param burnAngleDeg o ponto da órbita (ângulo em I) em que a queima de agora vale
     */
    public record Plan(Status status, boolean prograde, double currentPhaseDeg, double idealPhaseDeg, double phaseErrorDeg,
            double injectionDeltaV, double transferTicks, double timeToWindowTicks, boolean burnNow, double burnAngleDeg) {

        static Plan unavailable(Status status, boolean prograde) {
            double nan = Double.NaN;
            return new Plan(status, prograde, nan, nan, nan, nan, nan, nan, false, nan);
        }

        public boolean usable() {
            return status == Status.OK;
        }
    }

    /** {@link #plan(FlightState, double, double)} com o relógio a taxa 1. */
    public static Plan plan(FlightState s, double clock) {
        return plan(s, clock, 1.0);
    }

    /**
     * Plano da injeção para o estado {@code s} em {@code clock}. A órbita é tratada como circular no raio atual (excentricidade até
     * {@value #MAX_ECCENTRICITY}); a janela vem da tabela de fase ideal por ponto de queima ({@link #table}), que a física própria gera.
     * Com o relógio pausado ({@code clockRate} 0) o plano não vale (os planetas não andam).
     */
    public static Plan plan(FlightState s, double clock, double clockRate) {
        if (s.body() != SolarSystem.EARTH) {
            return Plan.unavailable(Status.NOT_AROUND_EARTH, false);
        }
        V3 r = s.r();
        V3 h = r.cross(s.v());
        boolean prograde = h.dot(POLE) > 0.0;
        double r1 = r.length();
        Orbit orbit = Orbit.fromState(EARTH.mu(), r, s.v(), POLE);
        if (!orbit.bound() || orbit.e() > MAX_ECCENTRICITY || orbit.periapsis() <= EARTH.radius() || r1 > 0.5 * SolarSystem.EARTH_MOON_DISTANCE
                || h.length() < 1.0e-9 || clockRate <= 0.0) {
            return Plan.unavailable(Status.NOT_IN_ORBIT, prograde);
        }
        if (!prograde) {
            return Plan.unavailable(Status.WRONG_DIRECTION, false);
        }
        Table table = table(r1);
        double dv = escapeSpeedGap(s) + INJECTION_MARGIN;
        double psi = Math.atan2(r.z(), r.x());
        double n = h.length() / (r1 * r1);
        double relativeRate = (Heliocentric.EARTH_MEAN_MOTION - Heliocentric.MARS_MEAN_MOTION) * clockRate;
        double phase = Heliocentric.marsPhaseAhead(clock);
        double ideal = table.needed(psi);
        double error = Double.isNaN(ideal) ? Double.NaN : wrapPi(phase - ideal);
        boolean burnNow = !Double.isNaN(error) && Math.abs(Math.toDegrees(error)) <= WINDOW_HALF_ANGLE_DEG
                && toSun(s.withRV(s.r(), s.v().plus(s.v().unit().times(dv))), clock, 400_000.0) != null;
        // a próxima queima: varre o tempo (a nave anda na órbita, a fase de Marte diminui) até a fase bater com a ideal do ponto onde a nave estiver
        double wait = burnNow ? 0.0 : Double.NaN;
        double burnAngle = burnNow ? psi : Double.NaN;
        double step = 0.02 / n;
        double horizon = 2.0 * Math.PI / relativeRate * 1.05;
        for (double t = step; Double.isNaN(wait) && t < horizon; t += step) {
            double needed = table.needed(psi + n * t);
            if (!Double.isNaN(needed) && Math.abs(Math.toDegrees(wrapPi(Heliocentric.marsPhaseAhead(clock + clockRate * t) - needed))) <= WINDOW_HALF_ANGLE_DEG) {
                wait = t;
                burnAngle = psi + n * t;
            }
        }
        double transfer = table.transferTicks(burnNow ? psi : burnAngle);
        return new Plan(Status.OK, true, Math.toDegrees(phase), Double.isNaN(ideal) ? Double.NaN : Math.toDegrees(ideal),
                Double.isNaN(error) ? Double.NaN : Math.toDegrees(error), dv, transfer, wait,
                burnNow, Double.isNaN(burnAngle) ? Double.NaN : Math.toDegrees(wrap2Pi(burnAngle)));
    }

    // ------------------------------------------------------------------ tabela da fase ideal por ponto de queima

    /** A fase ideal (Marte à frente da Terra, rad) e o tempo de voo até a órbita de Marte, para cada ponto de queima da faixa. */
    static final class Table {
        private final double[] phase;
        private final double[] flight;
        private final double start;
        private final double step;

        private Table(double[] phase, double[] flight, double start, double step) {
            this.phase = phase;
            this.flight = flight;
            this.start = start;
            this.step = step;
        }

        /** Fase ideal (rad) para queimar com a nave no ângulo {@code psi} (qualquer múltiplo de 2π); NaN fora da faixa de queima. */
        double needed(double psi) {
            return interpolate(phase, psi);
        }

        double transferTicks(double psi) {
            return interpolate(flight, psi);
        }

        private double interpolate(double[] values, double psi) {
            double a = wrap2Pi(psi - start);
            double position = a / step;
            int i = (int) Math.floor(position);
            if (i < 0 || i >= values.length) {
                return Double.NaN;
            }
            if (i == values.length - 1) {
                return values[i];
            }
            double lo = values[i];
            double hi = values[i + 1];
            if (Double.isNaN(lo) || Double.isNaN(hi)) {
                return Double.NaN;
            }
            double f = position - i;
            return lo + (hi - lo) * f;
        }
    }

    private static final Map<Long, Table> TABLES = new ConcurrentHashMap<>();

    /** A tabela para uma órbita circular de raio {@code r1} (guardada por faixas de 500 blocos). */
    static Table table(double r1) {
        long key = Math.round(r1 / 500.0);
        return TABLES.computeIfAbsent(key, k -> buildTable(k * 500.0));
    }

    private static Table buildTable(double r) {
        double start = Math.toRadians(BURN_ARC_START_DEG);
        double step = Math.toRadians(TABLE_STEP_DEG);
        int n = (int) Math.round((BURN_ARC_END_DEG - BURN_ARC_START_DEG) / TABLE_STEP_DEG) + 1;
        double[] phase = new double[n];
        double[] flight = new double[n];
        double vc = EARTH.circularSpeed(r);
        double dv = Math.sqrt(EARTH.mu() * (2.0 / r - 2.0 / (r + EARTH.soiExitRadius()))) - vc + INJECTION_MARGIN;
        for (int i = 0; i < n; i++) {
            double psi = start + i * step;
            double[] needed = neededPhase(r, psi, dv);
            phase[i] = needed == null ? Double.NaN : needed[0];
            flight[i] = needed == null ? Double.NaN : needed[1];
        }
        return new Table(phase, flight, start, step);
    }

    /**
     * A fase (Marte à frente da Terra, rad) com que uma queima prógrada de {@code dv} a partir da órbita circular de raio {@code r}, no ponto de
     * ângulo {@code psi} (em I), cruza a órbita de Marte justamente onde Marte estará, e o tempo de voo até lá. A conta é feita com a Terra em
     * θ = 0 (o referencial I é preso à linha Terra-sol, então vale para qualquer relógio). Null se a trajetória não chega à órbita de Marte.
     */
    static double @Nullable [] neededPhase(double r, double psi, double dv) {
        V3 pos = new V3(r * Math.cos(psi), 0.0, r * Math.sin(psi));
        V3 tangent = new V3(-Math.sin(psi), 0.0, Math.cos(psi));
        V3 vel = tangent.times(EARTH.circularSpeed(r) + dv);
        FlightState s = new FlightState(SolarSystem.EARTH, pos, vel, 0.0, 0.0);
        double clock = 0.0; // θ_T = 0
        SunState ss = toSun(s, clock, 400_000.0, false);
        if (ss == null) {
            return null;
        }
        double target = Heliocentric.MARS_ORBIT_RADIUS;
        double previousT = 0.0;
        double previousR = ss.state().r().length();
        for (double dt = 500.0; dt < 500_000.0; dt += 500.0) {
            Kepler.State k = Kepler.propagate(SUN.mu(), ss.state().r(), ss.state().v(), dt);
            double rr = k.r().length();
            if (previousR < target && rr >= target) {
                double lo = previousT;
                double hi = dt;
                for (int i = 0; i < 50; i++) {
                    double mid = 0.5 * (lo + hi);
                    if (Kepler.propagate(SUN.mu(), ss.state().r(), ss.state().v(), mid).r().length() < target) {
                        lo = mid;
                    } else {
                        hi = mid;
                    }
                }
                Kepler.State c = Kepler.propagate(SUN.mu(), ss.state().r(), ss.state().v(), lo);
                double flight = ss.offset() + lo;
                double marsAtBurn = Math.atan2(c.r().z(), c.r().x()) - Heliocentric.MARS_MEAN_MOTION * flight;
                return new double[] {wrap2Pi(marsAtBurn), flight};
            }
            previousT = dt;
            previousR = rr;
        }
        return null;
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
