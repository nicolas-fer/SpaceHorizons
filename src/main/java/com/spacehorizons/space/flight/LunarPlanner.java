package com.spacehorizons.space.flight;

import org.jspecify.annotations.Nullable;

import com.spacehorizons.space.SolarSystem;

/**
 * Planejador das duas pontas da missão lunar, além da TLI ({@link TransferPlanner}): a CHEGADA (previsão da passagem pela esfera
 * da Lua e a correção de meio de curso que leva o periastro à altitude pedida) e a VOLTA (a queima de injeção Lua → Terra, a
 * partir de uma órbita lunar, que leva o periastro da Terra à altitude da reentrada).
 *
 * <p>Tudo em I (eixos inerciais), com a mesma patched conics da física ({@link SoiTracker}): a Terra até entrar na esfera da Lua
 * (95% de Laplace), a Lua dali até sair dela (105%). A propagação é analítica ({@link Kepler}), sem integrar passo a passo: a
 * previsão de uma transferência de 22.000 ticks custa algumas centenas de resoluções da equação de Kepler. A Lua tem relógio de
 * taxa 1 (um tick de jogo por tick de relógio); o planejador não vale com o relógio pausado.
 *
 * <p>Geometria da chegada: a nave que sai de uma Hohmann chega à Lua a ~17 blocos/tick, vindo de frente (a Lua a alcança). O vetor
 * de impacto fica no plano perpendicular à velocidade relativa, entre a direção radial (para fora da Terra) e a normal da órbita
 * da Lua: passar do lado de fora da Lua dá órbita lunar RETRÓGRADA, do lado da Terra, prógrada; fora do plano, polar.
 */
public final class LunarPlanner {
    /** Passo da varredura do tempo (ticks): a 100 ticks a Lua anda 3.800 blocos e a nave outros ~2.000; a esfera tem 91.000 de raio. */
    private static final double SCAN_STEP = 100.0;
    private static final BodyPhysics EARTH = BodyPhysics.of(SolarSystem.EARTH);
    private static final BodyPhysics MOON = BodyPhysics.of(SolarSystem.MOON);

    private LunarPlanner() {
    }

    /**
     * A passagem pela esfera da Lua. {@code ticksToEntry} é o tempo até a entrada e {@code ticksToPeriapsis} até o periastro
     * (a partir do instante da previsão). {@code periapsisAltitude} é sobre o nível do mar da Lua. {@code normalH} e {@code radialH}
     * são as componentes do momento angular UNITÁRIO em torno da Lua na normal da órbita dela (negativa = retrógrada) e na direção
     * Terra → Lua (negativa e positiva = plano inclinado). {@code relativeSpeed} é a velocidade relativa à Lua na entrada.
     * {@code bRadial} e {@code bNormal} são as coordenadas (blocos) do VETOR DE IMPACTO da hipérbole no plano perpendicular à
     * velocidade relativa: radial (para fora da Terra: positivo = lado de fora da Lua, órbita retrógrada) e na normal da órbita
     * lunar (fora do plano). {@code targetBRadial} é o valor de {@code bRadial} que daria o periastro de 0 de altitude + o pedido
     * (preenchido por {@link #correction}); 0 aqui.
     */
    public record Arrival(double ticksToEntry, double ticksToPeriapsis, double periapsisAltitude, double normalH, double radialH,
            double relativeSpeed, double bRadial, double bNormal, double targetBRadial) {
    }

    /** Resultado da correção: o Δv inercial a aplicar agora e a chegada prevista depois dele. */
    public record Correction(V3 deltaV, Arrival arrival, int iterations) {
    }

    /** Queima de volta: ticks até o impulso, Δv (módulo, prógrado), a previsão do periastro da Terra (altitude) e a saída da esfera. */
    public record ReturnBurn(double ticksToBurn, double deltaV, double earthPeriapsisAltitude, double ticksToExit) {
    }

    // ------------------------------------------------------------------ chegada

    /**
     * Prevê a chegada à Lua do estado {@code s} (em órbita da Terra) em {@code clock}, até {@code horizon} ticks adiante. Null se
     * a nave não entra na esfera da Lua nesse prazo (ou não está ligada à Terra).
     */
    public static @Nullable Arrival arrival(FlightState s, double clock, double horizon) {
        if (s.body() != SolarSystem.EARTH) {
            return null;
        }
        double entry = soiEntry(s.r(), s.v(), clock, horizon);
        if (Double.isNaN(entry)) {
            return null;
        }
        Kepler.State e = Kepler.propagate(EARTH.mu(), s.r(), s.v(), entry);
        V3 relR = e.r().minus(moonPosition(clock + entry));
        V3 relV = e.v().minus(moonVelocity(clock + entry));
        Orbit o = Orbit.fromState(MOON.mu(), relR, relV, V3.ZERO);
        V3 h = relR.cross(relV).unit();
        double toPeri = entry + periTime(o);
        V3 normal = moonNormal(clock + toPeri);
        V3 radial = moonPosition(clock + toPeri).unit();
        // vetor de impacto: |b| = (mu/v∞²)·√(e²−1) na direção ŝ × ĥ, com ŝ = (ê + √(e²−1)·q̂)/e a assíntota de chegada; as coordenadas são as
        // do PLANO B (perpendicular a ŝ): T no plano da órbita da Lua, apontando para fora da Terra (positivo = lado de fora da Lua, retrógrada) e
        // N perpendicular aos dois (fora do plano)
        double bT = 0.0;
        double bN = 0.0;
        if (!o.bound() && o.e() > 1.0 + 1.0e-9) {
            V3 ev = relV.cross(relR.cross(relV)).times(1.0 / MOON.mu()).minus(relR.unit());
            V3 eHat = ev.unit();
            V3 q = h.cross(eHat);
            double root = Math.sqrt(o.e() * o.e() - 1.0);
            V3 asymptote = eHat.plus(q.times(root)).times(1.0 / o.e());
            double vInf2 = relV.lengthSquared() - 2.0 * MOON.mu() / relR.length();
            V3 b = asymptote.cross(h).times(MOON.mu() / vInf2 * root);
            V3 tAxis = asymptote.cross(normal).unit();
            if (tAxis.dot(radial) < 0.0) {
                tAxis = tAxis.negate();
            }
            V3 nAxis = asymptote.cross(tAxis).unit();
            bT = b.dot(tAxis);
            bN = b.dot(nAxis);
        }
        return new Arrival(entry, toPeri, o.periapsis() - MOON.radius(), h.dot(normal), h.dot(radial), relV.length(), bT, bN, 0.0);
    }

    private static double periTime(Orbit o) {
        double t = o.timeTo(0.0);
        return Double.isNaN(t) ? 0.0 : t;
    }

    /** O {@code bRadial} que dá o periastro {@code periapsisRadius} (distância ao centro) para a velocidade relativa {@code vInf}. */
    static double impactForPeriapsis(double periapsisRadius, double vInf) {
        return periapsisRadius * Math.sqrt(1.0 + 2.0 * MOON.mu() / (periapsisRadius * vInf * vInf));
    }

    /** Primeiro instante (ticks a partir de agora) em que a nave entra na esfera da Lua, ou NaN. */
    private static double soiEntry(V3 r, V3 v, double clock, double horizon) {
        double enter = MOON.soiEnterRadius();
        double previous = 0.0;
        if (distance(r, v, clock, 0.0) < enter) {
            return 0.0;
        }
        for (double t = SCAN_STEP; t <= horizon + SCAN_STEP; t += SCAN_STEP) {
            if (distance(r, v, clock, t) < enter) {
                double lo = previous;
                double hi = t;
                for (int i = 0; i < 40; i++) {
                    double mid = 0.5 * (lo + hi);
                    if (distance(r, v, clock, mid) < enter) {
                        hi = mid;
                    } else {
                        lo = mid;
                    }
                }
                return hi;
            }
            previous = t;
        }
        return Double.NaN;
    }

    private static double distance(V3 r, V3 v, double clock, double t) {
        Kepler.State k = Kepler.propagate(EARTH.mu(), r, v, t);
        return k.r().minus(moonPosition(clock + t)).length();
    }

    // ------------------------------------------------------------------ correção de meio de curso

    /**
     * O Δv inercial que, aplicado AGORA (impulso) ao estado {@code s}, leva o periastro lunar a {@code targetAltitude} numa órbita
     * retrógrada (se {@code retrograde}; senão prógrada) e no plano da Lua. Gauss-Newton de norma mínima sobre as três componentes
     * do Δv, com derivadas por diferenças finitas; {@code guess} é o ponto de partida (a solução depende do lado em que a nave
     * passa: o chute escolhe o lado). Null se não converge.
     */
    public static @Nullable Correction correction(FlightState s, double clock, double targetAltitude, boolean retrograde, V3 guess,
            double horizon) {
        V3 dv = guess;
        double sign = retrograde ? -1.0 : 1.0;
        double[] f = residual(s, clock, dv, targetAltitude, sign, horizon);
        if (f == null) {
            return null;
        }
        for (int iteration = 1; iteration <= 40; iteration++) {
            if (Math.abs(f[0]) < 0.02 && Math.abs(f[1]) < 0.02) {
                Arrival a = arrival(apply(s, dv), clock, horizon);
                return a == null ? null : new Correction(dv, a, iteration);
            }
            // Jacobiana 3x3 (resíduos x componentes do Δv), por diferenças de 0,02 blocos/tick
            double h = 0.02;
            double[][] jac = new double[3][3];
            V3[] axes = {new V3(1, 0, 0), new V3(0, 1, 0), new V3(0, 0, 1)};
            for (int c = 0; c < 3; c++) {
                double[] g = residual(s, clock, dv.plus(axes[c].times(h)), targetAltitude, sign, horizon);
                if (g == null) {
                    return null;
                }
                for (int rr = 0; rr < 3; rr++) {
                    jac[rr][c] = (g[rr] - f[rr]) / h;
                }
            }
            double[] step = solve3(jac, f);
            if (step == null) {
                return null;
            }
            // busca em linha: reduz o passo até o resíduo diminuir (a chegada pode até sumir com um passo grande demais)
            double norm = Math.sqrt(step[0] * step[0] + step[1] * step[1] + step[2] * step[2]);
            double scale = norm > 1.5 ? 1.5 / norm : 1.0;
            double current = f[0] * f[0] + f[1] * f[1] + f[2] * f[2];
            boolean moved = false;
            for (int k = 0; k < 12; k++, scale *= 0.5) {
                V3 trial = dv.minus(new V3(step[0], step[1], step[2]).times(scale));
                double[] g = residual(s, clock, trial, targetAltitude, sign, horizon);
                if (g != null && g[0] * g[0] + g[1] * g[1] + g[2] * g[2] < current) {
                    dv = trial;
                    f = g;
                    moved = true;
                    break;
                }
            }
            if (!moved) {
                return null;
            }
        }
        return null;
    }

    /**
     * Resíduos (em 1.000 blocos): a coordenada radial do vetor de impacto contra a que dá o periastro pedido (positiva = lado de fora
     * da Lua, retrógrada; negativa = lado da Terra, prógrada) e a fora do plano (zero). O terceiro é nulo (só dá folga à matriz).
     */
    private static double @Nullable [] residual(FlightState s, double clock, V3 dv, double targetAltitude, double sign, double horizon) {
        Arrival a = arrival(apply(s, dv), clock, horizon);
        if (a == null || a.bRadial() == 0.0 && a.bNormal() == 0.0) {
            return null;
        }
        double vInf = Math.sqrt(Math.max(1.0e-6, a.relativeSpeed() * a.relativeSpeed() - 2.0 * MOON.mu() / MOON.soiEnterRadius()));
        double want = -sign * impactForPeriapsis(MOON.radius() + targetAltitude, vInf);
        return new double[] {(a.bRadial() - want) / 1_000.0, a.bNormal() / 1_000.0, 0.0};
    }

    private static FlightState apply(FlightState s, V3 dv) {
        return s.withRV(s.r(), s.v().plus(dv));
    }

    /** Resolve J·x = f com a Jacobiana quase singular (a 3a linha só guia): Cramer com regularização. */
    private static double @Nullable [] solve3(double[][] a, double[] f) {
        // mínimos quadrados regularizados: (JᵀJ + λI) x = Jᵀ f
        double[][] m = new double[3][4];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double sum = 0.0;
                for (int k = 0; k < 3; k++) {
                    sum += a[k][i] * a[k][j];
                }
                m[i][j] = sum + (i == j ? 1.0e-6 : 0.0);
            }
            double rhs = 0.0;
            for (int k = 0; k < 3; k++) {
                rhs += a[k][i] * f[k];
            }
            m[i][3] = rhs;
        }
        for (int col = 0; col < 3; col++) {
            int pivot = col;
            for (int row = col + 1; row < 3; row++) {
                if (Math.abs(m[row][col]) > Math.abs(m[pivot][col])) {
                    pivot = row;
                }
            }
            double[] t = m[col];
            m[col] = m[pivot];
            m[pivot] = t;
            if (Math.abs(m[col][col]) < 1.0e-18) {
                return null;
            }
            for (int row = col + 1; row < 3; row++) {
                double k = m[row][col] / m[col][col];
                for (int c = col; c < 4; c++) {
                    m[row][c] -= k * m[col][c];
                }
            }
        }
        double[] x = new double[3];
        for (int row = 2; row >= 0; row--) {
            double sum = m[row][3];
            for (int c = row + 1; c < 3; c++) {
                sum -= m[row][c] * x[c];
            }
            x[row] = sum / m[row][row];
        }
        return x;
    }

    // ------------------------------------------------------------------ volta

    /**
     * A queima prógrada, a partir de uma órbita ligada à Lua, que leva o periastro da Terra a {@code targetAltitude} (sobre o nível
     * do mar da Terra) pelo menor Δv, queimando em algum instante dos próximos {@code window} ticks. {@code s} é o estado relativo
     * à Lua em {@code clock}. Null se nenhuma queima prógrada acerta (a geometria não deixa: espere e tente de novo).
     */
    public static @Nullable ReturnBurn returnBurn(FlightState s, double clock, double targetAltitude, double window) {
        if (s.body() != SolarSystem.MOON) {
            return null;
        }
        Orbit o = Orbit.of(s, clock);
        if (!o.bound()) {
            return null;
        }
        double target = EARTH.radius() + targetAltitude;
        ReturnBurn best = null;
        double stepT = Math.max(20.0, o.period() / 90.0);
        for (double tau = 0.0; tau <= window; tau += stepT) {
            Kepler.State at = Kepler.propagate(MOON.mu(), s.r(), s.v(), tau);
            double speed = at.v().length();
            double escape = MOON.escapeSpeed(at.r().length());
            double lowDv = Math.max(0.0, escape - speed) + 0.05;
            // varre o Δv de cima para baixo até o periastro da Terra cruzar o alvo
            double previousDv = Double.NaN;
            double previousF = Double.NaN;
            for (double dv = lowDv; dv <= lowDv + 14.0; dv += 0.25) {
                double rp = earthPeriapsis(at, clock + tau, dv);
                if (Double.isNaN(rp)) {
                    previousDv = Double.NaN;
                    continue;
                }
                double f = rp - target;
                if (!Double.isNaN(previousF) && previousF > 0.0 && f <= 0.0) {
                    double lo = previousDv;
                    double hi = dv;
                    for (int i = 0; i < 30; i++) {
                        double mid = 0.5 * (lo + hi);
                        double rmid = earthPeriapsis(at, clock + tau, mid);
                        if (Double.isNaN(rmid) || rmid > target) {
                            lo = mid;
                        } else {
                            hi = mid;
                        }
                    }
                    double dvHit = 0.5 * (lo + hi);
                    if (best == null || dvHit < best.deltaV()) {
                        best = new ReturnBurn(tau, dvHit, earthPeriapsis(at, clock + tau, dvHit) - EARTH.radius(), exitTime(at, dvHit));
                    }
                    break;
                }
                previousDv = dv;
                previousF = f;
            }
        }
        return best;
    }

    /** Periastro (distância ao centro) da órbita da TERRA depois de queimar {@code dv} prógrado em {@code at} (estado da Lua), ou NaN. */
    static double earthPeriapsis(Kepler.State at, double clockAtBurn, double dv) {
        V3 v = at.v().plus(at.v().unit().times(dv));
        double exit = exitTime(at.r(), v, MOON.soiExitRadius());
        if (Double.isNaN(exit)) {
            return Double.NaN;
        }
        Kepler.State out = Kepler.propagate(MOON.mu(), at.r(), v, exit);
        V3 earthR = out.r().plus(moonPosition(clockAtBurn + exit));
        V3 earthV = out.v().plus(moonVelocity(clockAtBurn + exit));
        Orbit eo = Orbit.fromState(EARTH.mu(), earthR, earthV, V3.ZERO);
        return eo.periapsis();
    }

    private static double exitTime(Kepler.State at, double dv) {
        V3 v = at.v().plus(at.v().unit().times(dv));
        return exitTime(at.r(), v, MOON.soiExitRadius());
    }

    /** Ticks até a distância à Lua passar de {@code radius} (a nave escapando), ou NaN se não escapa em 60.000 ticks. */
    private static double exitTime(V3 r, V3 v, double radius) {
        double energy = 0.5 * v.lengthSquared() - MOON.mu() / r.length();
        if (energy <= 0.0) {
            return Double.NaN;
        }
        double previous = 0.0;
        for (double t = SCAN_STEP; t <= 60_000.0; t += SCAN_STEP) {
            if (Kepler.propagate(MOON.mu(), r, v, t).r().length() > radius) {
                double lo = previous;
                double hi = t;
                for (int i = 0; i < 40; i++) {
                    double mid = 0.5 * (lo + hi);
                    if (Kepler.propagate(MOON.mu(), r, v, mid).r().length() > radius) {
                        hi = mid;
                    } else {
                        lo = mid;
                    }
                }
                return hi;
            }
            previous = t;
        }
        return Double.NaN;
    }

    // ------------------------------------------------------------------ corpos

    static V3 moonPosition(double clock) {
        double[] out = new double[3];
        SolarSystem.MOON.inertialPositionAt(clock, out);
        return V3.of(out);
    }

    /** Velocidade da Lua por tick de jogo com o relógio a taxa 1. */
    static V3 moonVelocity(double clock) {
        double[] out = new double[3];
        SolarSystem.MOON.inertialVelocityAt(clock, out);
        return V3.of(out);
    }

    /** Normal da órbita da Lua no sentido do movimento (a mesma convenção do {@link TransferPlanner}). */
    static V3 moonNormal(double clock) {
        double[] rot = SolarSystem.MOON.inertialPoseAt(clock).rotation();
        return new V3(-rot[1], -rot[4], -rot[7]);
    }
}
