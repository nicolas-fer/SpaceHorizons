package com.spacehorizons.client.devtest;

import java.util.Locale;
import java.util.function.Consumer;

import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import com.spacehorizons.rocket.AttitudeControl;
import com.spacehorizons.rocket.FlightMachine.Snapshot;
import com.spacehorizons.rocket.FlightMode;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.flight.AttitudeMath;
import com.spacehorizons.space.flight.AttitudeMath.SasMode;
import com.spacehorizons.space.flight.BodyPhysics;
import com.spacehorizons.space.flight.FlightState;
import com.spacehorizons.space.flight.FrameBridge;
import com.spacehorizons.space.flight.FrameBridge.Local;
import com.spacehorizons.space.flight.Kepler;
import com.spacehorizons.space.flight.LunarPlanner;
import com.spacehorizons.space.flight.MarsPlanner;
import com.spacehorizons.space.flight.Orbit;
import com.spacehorizons.space.flight.RocketStats;
import com.spacehorizons.space.flight.TransferPlanner;
import com.spacehorizons.space.flight.V3;

/**
 * O piloto de teste da MISSÃO COMPLETA (Terra → órbita baixa → TLI → captura na Lua → pouso → decolagem → volta → reentrada → pouso),
 * lógica pura: a cada tick recebe o instantâneo do voo ({@link Snapshot}) e o relógio e devolve o comando ({@link Out}). Serve ao
 * cenário {@code mission} do autoteste (no servidor, pelo foguete de verdade) e ao teste unitário que roda a missão inteira com a
 * {@code FlightMachine} sem o jogo, que é onde a guiagem é ajustada.
 *
 * <p>Usa só o que o jogo dá ao jogador: a telemetria do estado de voo, o {@link TransferPlanner} (janela e Δv da TLI) e o
 * {@link LunarPlanner} (correção de meio de curso, queima de volta). As fases estão em {@link Phase}; cada fim de fase vai ao log com o
 * apoastro/periastro, o Δv gasto (pela equação do foguete, em cima do combustível) e o tempo.
 */
final class MissionPilot {
    /** As fases da missão, em ordem. */
    enum Phase {
        PAD, ASCENT, COAST_TO_APOAPSIS, CIRCULARIZE, LEO_WAIT, TLI, COAST_TO_MOON, CAPTURE_COAST, CAPTURE, LUNAR_ORBIT, LUNAR_BRAKE,
        LUNAR_DESCENT, MOON_LANDED, LUNAR_ASCENT, LUNAR_COAST_TO_APOAPSIS, LUNAR_CIRCULARIZE, LUNAR_ORBIT_2, TEI_WAIT, TEI, COAST_HOME,
        EARTH_ENTRY, EARTH_LANDING,
        /** A viagem a Marte (sistema solar centrado no sol): espera da janela, injeção, voo, captura, descida com paraquedas e pouso. */
        MARS_LEO_WAIT, MARS_TMI, MARS_COAST, MARS_CAPTURE_COAST, MARS_CAPTURE, MARS_ORBIT, MARS_BRAKE, MARS_DESCENT, MARS_LANDED,
        DONE, FAILED
    }

    /** O comando de um tick. */
    record Out(double pitch, double yaw, double throttle, boolean parachute, boolean nextSas) {
        static final Out IDLE = new Out(0.0, 0.0, 0.0, false, false);
    }

    /** Altura (y da dimensão) da superfície sólida no ponto (x, z) do corpo; NaN se não se sabe. */
    interface Terrain {
        double surfaceY(SolarSystem.Body body, double x, double z);
    }

    // ------------------------------------------------------------------ alvos da missão
    static final double LEO_APOAPSIS = 6_000.0;
    static final double LEO_PERIAPSIS = 5_200.0;
    /** Altitude (sobre o nível do mar da Lua) do periastro da chegada e da órbita lunar. */
    static final double LLO_ALTITUDE = 1_500.0;
    /** Periastro da Terra (altitude) da volta: dentro da atmosfera (1.800), para a reentrada frear no ar. */
    static final double ENTRY_PERIAPSIS_ALTITUDE = 1_000.0;
    /** Altitude (sobre o nível do mar de Marte) do periastro da chegada e da órbita de espera: acima da altitude de saída (2.656) e da atmosfera (1.100). */
    static final double MARS_ORBIT_ALTITUDE = 3_400.0;

    private static final double BURN_ALIGNMENT_RAD = Math.toRadians(2.0);

    private final RocketStats stats;
    private final double boxHalfHeight;
    private final Terrain terrain;
    private final Consumer<String> log;

    /** A missão é a de Marte (depois da órbita baixa vai à janela da injeção interplanetária, não à da TLI). */
    private boolean mars;
    private MarsPlanner.@Nullable Plan marsPlan;
    /** Decolagem DE Marte até a órbita de espera (cenário {@code marsorbit}): sobe, circulariza, fica em órbita e desce de volta. */
    private boolean marsLaunch;
    /** Ticks que o piloto fica na órbita de Marte antes de frear para descer. */
    private int orbitWait = 200;
    /** Apoastro da subida de Marte (altitude): acima da altitude de saída (2.656) e da atmosfera (1.100). */
    static final double MARS_LAUNCH_APOAPSIS = 3_600.0;
    static final double MARS_LAUNCH_PERIAPSIS = 3_300.0;

    private Phase phase = Phase.PAD;
    private int ticks;
    private int phaseTicks;
    private double phaseFuel;
    private double startFuel = Double.NaN;
    private int sasWanted;
    private boolean sasPulse;

    // manobra de correção em andamento (Δv inercial pedido, combustível no início)
    private @Nullable V3 maneuver;
    private double maneuverFuel;
    private double maneuverDv;
    private String maneuverName = "";
    private boolean maneuverResolved;
    private boolean maneuverToMoon;
    private boolean maneuverToMars;
    /** Periastro (altitude) que a correção do corpo dominante (Terra na volta, Marte na chegada) persegue. */
    private double maneuverPeriapsis = ENTRY_PERIAPSIS_ALTITUDE;
    private int nextCorrection;

    // queima planejada (TLI, captura, TEI)
    private double plannedDv;
    private double burnFuel;
    private double burnDelay;
    private TransferPlanner.@Nullable Plan plan;
    private int planTick = Integer.MIN_VALUE;
    private double tliMidTicks;

    // guiagem de descida e subida
    private double landingClearance = Double.NaN;
    private double lastClock;
    private int moonLandedTicks;
    private boolean entryLogged;
    private double maxHeat;

    MissionPilot(RocketStats stats, double boxHalfHeight, Terrain terrain, Consumer<String> log) {
        this.stats = stats;
        this.boxHalfHeight = boxHalfHeight;
        this.terrain = terrain;
        this.log = log;
    }

    Phase phase() {
        return this.phase;
    }

    /** Faz do piloto o da decolagem de Marte e volta (começa pousado em Marte); {@code orbitTicks} é a espera na órbita. */
    MissionPilot marsLaunch(int orbitTicks) {
        this.marsLaunch = true;
        this.orbitWait = orbitTicks;
        return this;
    }

    /** Faz do piloto o da missão a Marte: depois da órbita baixa segue as fases {@code MARS_*}. */
    MissionPilot toMars() {
        this.mars = true;
        return this;
    }

    /**
     * Taxa do relógio do voo (relógio por tick), a mesma que o voo usa para o chão girar: 0 com o relógio pausado (o autoteste pausa). Usada no
     * pouso no motor, onde a velocidade relativa ao chão decide o toque.
     */
    private double clockRate = 1.0;

    void clockRate(double rate) {
        this.clockRate = rate;
    }

    /** Pula para uma fase (testes de um trecho da missão, e o cenário que recomeça de um ponto). */
    void forcePhase(Phase to) {
        this.phase = to;
        this.phaseTicks = 0;
    }

    /** Parâmetros da subida da Lua (começo da curva, altitude em que chega à horizontal, expoente, inclinação máxima). */
    static double lunarStartAlt = 90.0;
    static double lunarFullAlt = 500.0;
    static double lunarExponent = 1.0;
    static double lunarMaxDeg = 88.0;

    int ticks() {
        return this.ticks;
    }

    boolean finished() {
        return this.phase == Phase.DONE || this.phase == Phase.FAILED;
    }

    // ------------------------------------------------------------------ o passo

    Out step(Snapshot s, double clock) {
        this.ticks++;
        this.phaseTicks++;
        this.lastClock = clock;
        if (Double.isNaN(this.startFuel)) {
            this.startFuel = s.fuelMb();
            this.phaseFuel = s.fuelMb();
        }
        if (finished()) {
            return Out.IDLE;
        }
        FlightState st = s.state();
        if (st == null) {
            return stepLanded(s);
        }
        this.maxHeat = Math.max(this.maxHeat, st.heat());
        Out out = switch (this.phase) {
            case ASCENT -> ascent(s, st, clock, Phase.COAST_TO_APOAPSIS, !this.marsLaunch);
            case COAST_TO_APOAPSIS -> coastToApoapsis(s, st, clock, Phase.CIRCULARIZE, false);
            case CIRCULARIZE -> this.marsLaunch ? circularize(s, st, clock, MARS_LAUNCH_PERIAPSIS, Phase.MARS_ORBIT)
                    : circularize(s, st, clock, LEO_PERIAPSIS, this.mars ? Phase.MARS_LEO_WAIT : Phase.LEO_WAIT);
            case LEO_WAIT -> leoWait(s, st, clock);
            case TLI -> tli(s, st, clock);
            case COAST_TO_MOON -> coastToMoon(s, st, clock);
            case CAPTURE_COAST -> captureCoast(s, st, clock);
            case CAPTURE -> capture(s, st, clock);
            case LUNAR_ORBIT -> lunarOrbit(s, st, clock);
            case LUNAR_BRAKE -> lunarBrake(s, st, clock);
            case LUNAR_DESCENT -> lunarDescent(s, st, clock, LUNAR_BRAKE_FRACTION);
            case LUNAR_ASCENT -> ascent(s, st, clock, Phase.LUNAR_COAST_TO_APOAPSIS, false);
            case LUNAR_COAST_TO_APOAPSIS -> coastToApoapsis(s, st, clock, Phase.LUNAR_CIRCULARIZE, true);
            case LUNAR_CIRCULARIZE -> circularize(s, st, clock, LLO_ALTITUDE - 150.0, Phase.LUNAR_ORBIT_2);
            case LUNAR_ORBIT_2 -> lunarOrbit2(s, st, clock);
            case TEI_WAIT -> teiWait(s, st, clock);
            case TEI -> tei(s, st, clock);
            case COAST_HOME -> coastHome(s, st, clock);
            case EARTH_ENTRY, EARTH_LANDING -> earthDescent(s, st, clock);
            case MARS_LEO_WAIT -> marsLeoWait(s, st, clock);
            case MARS_TMI -> marsInjection(s, st, clock);
            case MARS_COAST -> marsCoast(s, st, clock);
            case MARS_CAPTURE_COAST -> marsCaptureCoast(s, st, clock);
            case MARS_CAPTURE -> marsCapture(s, st, clock);
            case MARS_ORBIT -> marsOrbit(s, st, clock);
            case MARS_BRAKE -> marsBrake(s, st, clock);
            case MARS_DESCENT -> marsDescent(s, st, clock);
            default -> Out.IDLE;
        };
        return keepSas(s, out);
    }

    /** SAS em "estabilizar" sempre que está voando: uma borda por pacote (sobe num tick, desce no seguinte). */
    private Out keepSas(Snapshot s, Out out) {
        if (s.flying() && s.sas() != SasMode.STABILIZE && !this.sasPulse) {
            this.sasPulse = true;
            return new Out(out.pitch(), out.yaw(), out.throttle(), out.parachute(), true);
        }
        this.sasPulse = false;
        return out;
    }

    /** Pousado (na plataforma ou na Lua): sem estado de voo. */
    private Out stepLanded(Snapshot s) {
        switch (this.phase) {
            case PAD -> {
                if (this.phaseTicks == 1) {
                    return new Out(0.0, 0.0, 0.0, false, true); // desligado -> estabilizar
                }
                if (this.phaseTicks > 8) {
                    next(Phase.ASCENT, s, null);
                    return new Out(0.0, 0.0, 1.0, false, false);
                }
                return Out.IDLE;
            }
            case ASCENT -> {
                return new Out(0.0, 0.0, 1.0, false, false); // ainda no chão, acelerando (a decolagem acontece neste passo)
            }
            case LUNAR_DESCENT -> {
                // pousou: fim da descida
                next(Phase.MOON_LANDED, s, null);
                this.moonLandedTicks = 0;
                return Out.IDLE;
            }
            case MOON_LANDED -> {
                this.moonLandedTicks++;
                if (this.moonLandedTicks > 400) {
                    next(Phase.LUNAR_ASCENT, s, null);
                    return new Out(0.0, 0.0, 1.0, false, false);
                }
                return Out.IDLE;
            }
            case LUNAR_ASCENT -> {
                return new Out(0.0, 0.0, 1.0, false, false);
            }
            case EARTH_LANDING, EARTH_ENTRY -> {
                next(Phase.DONE, s, null);
                return Out.IDLE;
            }
            case MARS_DESCENT -> {
                // pousou em Marte: o fim da missão
                next(Phase.MARS_LANDED, s, null);
                next(Phase.DONE, s, null);
                return Out.IDLE;
            }
            default -> {
                return Out.IDLE;
            }
        }
    }

    // ------------------------------------------------------------------ transições e log

    private void next(Phase to, Snapshot s, @Nullable FlightState st) {
        double spent = dv(this.phaseFuel, s.fuelMb());
        String orbit = st == null ? "" : orbitText(st, this.lastClock);
        this.log.accept(String.format(Locale.ROOT, "FASE %s -> %s (tick %d, %d ticks na fase) | Δv da fase=%.2f b/t, Δv total=%.2f b/t, combustível=%.0f mB, "
                + "Δv restante=%.2f %s", this.phase, to, this.ticks, this.phaseTicks, spent, dv(this.startFuel, s.fuelMb()), s.fuelMb(),
                this.stats.deltaV(s.fuelMb()), orbit));
        this.phase = to;
        this.phaseTicks = 0;
        this.phaseFuel = s.fuelMb();
        this.maneuver = null;
        this.nextCorrection = 0;
        this.maneuverResolved = false;
    }

    private void fail(Snapshot s, String why) {
        this.log.accept("FALHA da missão na fase " + this.phase + ": " + why);
        this.phase = Phase.FAILED;
    }

    private double dv(double fuelFrom, double fuelTo) {
        if (fuelTo >= fuelFrom) {
            return 0.0;
        }
        return this.stats.exhaustVelocity() * Math.log(this.stats.wetMass(fuelFrom) / this.stats.wetMass(fuelTo));
    }

    private String orbitText(FlightState st, double clock) {
        BodyPhysics bp = BodyPhysics.of(st.body());
        Orbit o = Orbit.of(st, clock);
        return String.format(Locale.ROOT, "| %s: alt=%.0f Ap=%s Pe=%.0f v=%.2f", st.body().planet().id().getPath(), st.altitude(),
                o.bound() ? String.format(Locale.ROOT, "%.0f", o.apoapsis() - bp.radius()) : "aberta", o.periapsis() - bp.radius(), st.v().length());
    }

    // ------------------------------------------------------------------ subida e circularização (Terra e Lua)

    /**
     * Subida com curva de gravidade, para o leste. Terra: vertical até 100, inclina até a horizontal aos 3.000 (perfil de raiz); Lua: vertical até 60 e
     * inclina quase de uma vez (a gravidade é 1/6 e o empuxo sobra). Corta com o apoastro no alvo.
     */
    private Out ascent(Snapshot s, FlightState st, double clock, Phase after, boolean earth) {
        BodyPhysics bp = BodyPhysics.of(st.body());
        Orbit o = Orbit.of(st, clock);
        double apoapsisAlt = o.bound() ? o.apoapsis() - bp.radius() : Double.POSITIVE_INFINITY;
        double target = earth ? LEO_APOAPSIS : this.marsLaunch ? MARS_LAUNCH_APOAPSIS : LLO_ALTITUDE + 60.0;
        if (apoapsisAlt >= target && st.altitude() > 80.0) {
            next(after, s, st);
            return Out.IDLE;
        }
        double altitude = st.altitude();
        double startAlt = earth ? 100.0 : lunarStartAlt;
        double fullAlt = earth ? 3_000.0 : lunarFullAlt;
        double maxDeg = earth ? 90.0 : lunarMaxDeg;
        double exponent = earth ? 0.5 : lunarExponent;
        double frac = Math.pow(Math.max(0.0, Math.min(1.0, (altitude - startAlt) / (fullAlt - startAlt))), exponent);
        double theta = Math.toRadians(maxDeg) * frac;
        V3 up = st.r().unit();
        V3 east = east(st, clock);
        V3 desired = up.times(Math.cos(theta)).plus(east.times(Math.sin(theta)));
        double[] c = steer(s, desired);
        return new Out(c[0], c[1], 1.0, false, false);
    }

    /** Coasta (nariz no prógrado) até o apoastro. */
    private Out coastToApoapsis(Snapshot s, FlightState st, double clock, Phase after, boolean moon) {
        Orbit o = Orbit.of(st, clock);
        double[] c = steer(s, st.v().unit());
        if (!o.bound()) {
            next(after, s, st);
            return Out.IDLE;
        }
        if (o.timeTo(Math.PI) < 8.0 && st.verticalSpeed() > -0.5) {
            next(after, s, st);
            return new Out(c[0], c[1], 0.0, false, false);
        }
        if (this.phaseTicks > 30_000) {
            fail(s, "o apoastro não chegou");
        }
        return new Out(c[0], c[1], 0.0, false, false);
    }

    /**
     * Circulariza no apoastro: queima quase horizontal (o nariz sobe o bastante para a gravidade que a velocidade ainda não equilibra não
     * puxar a nave para baixo no meio da queima: com o nariz no prógrado a nave afundava e a órbita saía com o apoastro dezenas de milhares de
     * blocos acima) até o periastro passar do alvo.
     */
    private Out circularize(Snapshot s, FlightState st, double clock, double periapsisTarget, Phase after) {
        BodyPhysics bp = BodyPhysics.of(st.body());
        Orbit o = Orbit.of(st, clock);
        double periAlt = o.periapsis() - bp.radius();
        double[] c = steer(s, horizontalThrust(st, s.fuelMb()));
        if (periAlt >= periapsisTarget || s.fuelMb() < 50.0) {
            next(after, s, st);
            return new Out(c[0], c[1], 0.0, false, false);
        }
        double scale = st.body() == SolarSystem.EARTH ? 400.0 : 120.0;
        double throttle = Math.max(0.15, Math.min(1.0, (periapsisTarget - periAlt) / scale));
        if (this.phaseTicks > 3_000) {
            fail(s, "circularização não fechou");
        }
        return new Out(c[0], c[1], throttle, false, false);
    }

    /** Direção do empuxo para acelerar na horizontal segurando a altitude: a componente vertical cancela {@code g − v²/r} e a velocidade vertical. */
    private V3 horizontalThrust(FlightState st, double fuelMb) {
        BodyPhysics bp = BodyPhysics.of(st.body());
        V3 up = st.r().unit();
        double vv = st.v().dot(up);
        V3 horizontal = st.v().minus(up.times(vv));
        double vh = horizontal.length();
        double r = st.r().length();
        double a = this.stats.acceleration(fuelMb, 1.0);
        double aUp = bp.gravityAt(r) - vh * vh / r - 0.3 * vv;
        aUp = Math.max(-0.6 * a, Math.min(0.6 * a, aUp));
        double aH = Math.sqrt(Math.max(1.0e-12, a * a - aUp * aUp));
        return horizontal.unit().times(aH).plus(up.times(aUp)).unit();
    }

    // ------------------------------------------------------------------ LEO e TLI

    private Out leoWait(Snapshot s, FlightState st, double clock) {
        if (this.phaseTicks == 1) {
            this.plan = null;
        }
        double[] c = steer(s, st.v().unit());
        if (this.plan == null || this.ticks - this.planTick >= 20) {
            this.plan = TransferPlanner.moon(st, clock, 1.0);
            this.planTick = this.ticks;
            if (!this.plan.usable()) {
                fail(s, "o planejador da TLI não vale: " + this.plan.status());
                return Out.IDLE;
            }
            if (this.phaseTicks <= 2) {
                this.log.accept(String.format(Locale.ROOT, "PLANEJADOR da TLI: janela em %.0f ticks, Δv=%.2f b/t, voo de transferência=%.0f ticks, fase atual=%.1f ideal=%.1f, "
                        + "plano %.1f graus", this.plan.timeToWindowTicks(), this.plan.tliDeltaV(), this.plan.transferTicks(), this.plan.currentPhaseDeg(),
                        this.plan.idealPhaseDeg(), this.plan.planeAngleDeg()));
            }
        }
        double window = this.plan.timeToWindowTicks() - (this.ticks - this.planTick);
        double burn = this.stats.burnTime(s.fuelMb(), this.plan.tliDeltaV());
        if (window <= 0.5 * burn) {
            this.plannedDv = this.plan.tliDeltaV();
            this.burnFuel = s.fuelMb();
            next(Phase.TLI, s, st);
            this.log.accept(String.format(Locale.ROOT, "TLI: queima de %.2f b/t em %.0f ticks (janela em %.0f)", this.plannedDv, burn, window));
            return new Out(c[0], c[1], 0.0, false, false);
        }
        return new Out(c[0], c[1], 0.0, false, false);
    }

    private Out tli(Snapshot s, FlightState st, double clock) {
        BodyPhysics bp = BodyPhysics.of(st.body());
        double r = st.r().length();
        double d = SolarSystem.EARTH_MOON_DISTANCE;
        double needed = Math.sqrt(bp.mu() * (2.0 / r - 2.0 / (r + d)));
        double remaining = needed - st.v().length();
        double[] c = steer(s, st.v().unit());
        double aTick = this.stats.acceleration(s.fuelMb(), 1.0);
        if (remaining <= 0.01 || s.fuelMb() < 50.0) {
            next(Phase.COAST_TO_MOON, s, st);
            return new Out(c[0], c[1], 0.0, false, false);
        }
        return new Out(c[0], c[1], Math.max(0.04, Math.min(1.0, remaining / aTick)), false, false);
    }

    // ------------------------------------------------------------------ a ida

    private Out coastToMoon(Snapshot s, FlightState st, double clock) {
        if (st.body() == SolarSystem.MOON) {
            next(Phase.CAPTURE_COAST, s, st);
            return Out.IDLE;
        }
        Out burn = runManeuver(s, st, clock);
        if (burn != null) {
            return burn;
        }
        // correções de meio de curso: logo depois da TLI, na metade e antes da esfera
        int[] schedule = {250, 6_000, 12_000, 16_000};
        if (this.nextCorrection < schedule.length && this.phaseTicks >= schedule[this.nextCorrection]) {
            this.nextCorrection++;
            LunarPlanner.Correction corr = LunarPlanner.correction(st, clock, LLO_ALTITUDE, true, V3.ZERO, 40_000);
            LunarPlanner.Arrival now = LunarPlanner.arrival(st, clock, 40_000);
            this.log.accept(String.format(Locale.ROOT, "CORREÇÃO %d de meio de curso: chegada prevista %s | solução %s", this.nextCorrection,
                    arrivalText(now), corr == null ? "nenhuma" : String.format(Locale.ROOT, "Δv=%.3f b/t (%d iterações) -> periastro %.0f", corr.deltaV().length(),
                            corr.iterations(), corr.arrival().periapsisAltitude())));
            if (corr != null && corr.deltaV().length() > 0.005) {
                startManeuver(s, corr.deltaV(), "MCC" + this.nextCorrection, true);
            }
        }
        double[] c = steer(s, st.v().unit());
        return new Out(c[0], c[1], 0.0, false, false);
    }

    private static String arrivalText(LunarPlanner.@Nullable Arrival a) {
        if (a == null) {
            return "não entra na esfera da Lua";
        }
        return String.format(Locale.ROOT, "entrada em %.0f ticks, periastro=%.0f, bR=%.0f, bN=%.0f, v relativa=%.2f", a.ticksToEntry(), a.periapsisAltitude(),
                a.bRadial(), a.bNormal(), a.relativeSpeed());
    }

    /** Começa uma manobra de correção: gira o nariz para o Δv e, alinhado, resolve a correção de novo (o estado andou) e queima o módulo dele. */
    private void startManeuver(Snapshot s, V3 dv, String name, boolean toMoon) {
        startManeuver(s, dv, name, toMoon, false);
    }

    private void startManeuver(Snapshot s, V3 dv, String name, boolean toMoon, boolean toMars) {
        this.maneuverToMars = toMars;
        this.maneuver = dv;
        this.maneuverDv = dv.length();
        this.maneuverFuel = s.fuelMb();
        this.maneuverName = name;
        this.maneuverResolved = false;
        this.maneuverToMoon = toMoon;
    }

    /** Executa a manobra em andamento; null se não há. */
    private @Nullable Out runManeuver(Snapshot s, FlightState st, double clock) {
        V3 m = this.maneuver;
        if (m == null) {
            return null;
        }
        double[] c = steer(s, m.unit());
        double angle = AttitudeMath.noseDirection(s.attitude()).angleTo(m.unit());
        if (!this.maneuverResolved) {
            if (angle > BURN_ALIGNMENT_RAD) {
                return new Out(c[0], c[1], 0.0, false, false);
            }
            // alinhado: o estado andou desde que a correção foi calculada, então resolve de novo para o instante da queima
            V3 again = this.maneuverToMars ? resolveMarsCorrection(st, clock)
                    : this.maneuverToMoon ? resolveMoonCorrection(st, clock, m) : earthPeriapsisCorrection(st, clock, this.maneuverPeriapsis);
            this.maneuverResolved = true;
            if (again == null || again.length() < 0.004) {
                this.log.accept(this.maneuverName + ": nada a corrigir depois do alinhamento");
                this.maneuver = null;
                return Out.IDLE;
            }
            this.maneuver = again;
            this.maneuverDv = again.length();
            this.maneuverFuel = s.fuelMb();
            this.log.accept(String.format(Locale.ROOT, "%s: queima de %.3f b/t", this.maneuverName, this.maneuverDv));
            return new Out(c[0], c[1], 0.0, false, false);
        }
        double burned = dv(this.maneuverFuel, s.fuelMb());
        double remaining = this.maneuverDv - burned;
        if (remaining <= 0.003 || s.fuelMb() < 20.0) {
            this.log.accept(String.format(Locale.ROOT, "%s concluída: %.3f b/t em %d ticks", this.maneuverName, burned, this.phaseTicks));
            this.maneuver = null;
            return new Out(c[0], c[1], 0.0, false, false);
        }
        if (angle > BURN_ALIGNMENT_RAD * 3.0) {
            return new Out(c[0], c[1], 0.0, false, false);
        }
        double aTick = this.stats.acceleration(s.fuelMb(), 1.0);
        return new Out(c[0], c[1], Math.max(0.03, Math.min(1.0, remaining / aTick)), false, false);
    }

    private static @Nullable V3 resolveMoonCorrection(FlightState st, double clock, V3 guess) {
        LunarPlanner.Correction corr = LunarPlanner.correction(st, clock, LLO_ALTITUDE, true, guess, 40_000);
        return corr == null ? null : corr.deltaV();
    }

    // ------------------------------------------------------------------ captura

    private Out captureCoast(Snapshot s, FlightState st, double clock) {
        BodyPhysics bp = BodyPhysics.of(st.body());
        Orbit o = Orbit.of(st, clock);
        double[] c = steer(s, st.v().unit().negate());
        if (this.phaseTicks == 1) {
            this.log.accept(String.format(Locale.ROOT, "CHEGADA À LUA: %s | ainda a corrigir: periastro=%.0f, inclinação=%.1f graus", orbitText(st, clock),
                    o.periapsis() - bp.radius(), Math.toDegrees(o.inclination())));
        }
        double vPeri = o.speedAt(o.periapsis());
        double dvCapture = Math.max(0.0, vPeri - bp.circularSpeed(o.periapsis()));
        double burn = this.stats.burnTime(s.fuelMb(), dvCapture);
        double toPeri = o.bound() ? o.timeTo(0.0) : o.timeTo(0.0);
        if (Double.isNaN(toPeri) || toPeri < 0.0) {
            // já passou do periastro: sem captura (não deveria acontecer)
            if (st.distance() < bp.radius() + 3_000.0 || this.phaseTicks > 12_000) {
                fail(s, "passou do periastro sem capturar");
            }
            return new Out(c[0], c[1], 0.0, false, false);
        }
        // correção fina do periastro dentro da esfera (a 6.000 ticks dele): Δv radial pequeno
        if (toPeri < 0.5 * burn + 2.0) {
            this.burnFuel = s.fuelMb();
            this.plannedDv = dvCapture;
            next(Phase.CAPTURE, s, st);
            this.log.accept(String.format(Locale.ROOT, "CAPTURA: queima retrógrada de %.2f b/t em %.0f ticks no periastro (%.0f de altitude)", dvCapture, burn,
                    o.periapsis() - bp.radius()));
        }
        return new Out(c[0], c[1], 0.0, false, false);
    }

    private Out capture(Snapshot s, FlightState st, double clock) {
        BodyPhysics bp = BodyPhysics.of(st.body());
        double r = st.r().length();
        double remaining = st.v().length() - bp.circularSpeed(r);
        double[] c = steer(s, st.v().unit().negate());
        double aTick = this.stats.acceleration(s.fuelMb(), 1.0);
        Orbit o = Orbit.of(st, clock);
        if (remaining <= 0.02 && o.bound() || s.fuelMb() < 50.0) {
            next(Phase.LUNAR_ORBIT, s, st);
            return new Out(c[0], c[1], 0.0, false, false);
        }
        if (this.phaseTicks > 2_000) {
            fail(s, "a captura não fechou");
        }
        return new Out(c[0], c[1], Math.max(0.05, Math.min(1.0, remaining / aTick)), false, false);
    }

    // ------------------------------------------------------------------ pouso na Lua

    private Out lunarOrbit(Snapshot s, FlightState st, double clock) {
        double[] c = steer(s, st.v().unit().negate());
        if (this.phaseTicks == 1) {
            this.log.accept("ÓRBITA LUNAR: " + orbitText(st, clock));
        }
        if (this.phaseTicks > 200) {
            next(Phase.LUNAR_BRAKE, s, st);
        }
        return new Out(c[0], c[1], 0.0, false, false);
    }

    /** Freia contra a velocidade em relação ao chão da Lua (ela gira junto com a órbita) até quase parar. */
    private Out lunarBrake(Snapshot s, FlightState st, double clock) {
        V3 ground = st.v().minus(FrameBridge.rotationVelocity(st.body(), clock, 1.0, st.r()));
        double speed = ground.length();
        double[] c = steer(s, ground.unit().negate());
        double aTick = this.stats.acceleration(s.fuelMb(), 1.0);
        if (speed < 1.2 || s.fuelMb() < 20.0) {
            next(Phase.LUNAR_DESCENT, s, st);
            return new Out(c[0], c[1], 0.0, false, false);
        }
        double angle = AttitudeMath.noseDirection(s.attitude()).angleTo(ground.unit().negate());
        if (angle > 0.15) {
            return new Out(c[0], c[1], 0.0, false, false);
        }
        return new Out(c[0], c[1], Math.max(0.05, Math.min(1.0, (speed - 1.0) / aTick)), false, false);
    }

    /**
     * Descida motorizada com guiagem em malha fechada no referencial local do chão (leste, cima, norte): a velocidade vertical segue um perfil
     * de frenagem (ad constante até a altura de arredondamento, depois {@value #TOUCHDOWN_SPEED} b/t) e a horizontal ao chão é anulada; o empuxo
     * sai pelo vetor que soma as duas (o nariz o segue).
     */
    /** Fração do empuxo máximo com que a descida no motor planeja frear: na Lua sobra folga; na Terra, com a gravidade maior, freia antes. */
    private static final double LUNAR_BRAKE_FRACTION = 0.45;
    private static final double EARTH_POWERED_BRAKE_FRACTION = 0.2;

    private Out lunarDescent(Snapshot s, FlightState st, double clock, double brakeFraction) {
        SolarSystem.Body body = st.body();
        BodyPhysics bp = BodyPhysics.of(body);
        Local loc = FrameBridge.toDimension(body, clock, this.clockRate, st.r(), st.v());
        double surface = this.terrain.surfaceY(body, loc.x(), loc.z());
        double clearance = Double.isNaN(surface) ? loc.y() - body.planet().seaLevel() - 120.0 : loc.y() - this.boxHalfHeight - surface;
        this.landingClearance = clearance;
        double g = bp.gravityAt(st.r().length());
        double aMax = this.stats.acceleration(s.fuelMb(), 1.0);
        double ad = brakeFraction * aMax;
        double touchdown = brakeFraction < LUNAR_BRAKE_FRACTION ? EARTH_TOUCHDOWN_SPEED : TOUCHDOWN_SPEED;
        double target = Math.max(touchdown, Math.sqrt(2.0 * ad * Math.max(0.0, clearance - 4.0)) * 0.8);
        double vv = loc.vy();
        double aUp = g + 0.3 * (-target - vv);
        double ex = -0.25 * loc.vx();
        double ez = -0.25 * loc.vz();
        double limit = (clearance > 150.0 ? 0.8 : 0.35) * Math.max(aUp, 0.0);
        double mag = Math.hypot(ex, ez);
        if (mag > limit && mag > 0.0) {
            ex *= limit / mag;
            ez *= limit / mag;
        }
        double throttle = 0.0;
        double upDir = Math.max(aUp, 0.5 * Math.hypot(ex, ez));
        double ux = ex;
        double uy = Math.max(upDir, 0.15 * aMax);
        double uz = ez;
        if (aUp > 0.0) {
            throttle = Math.min(1.0, Math.sqrt(ex * ex + aUp * aUp + ez * ez) / aMax);
            uy = aUp;
        }
        // direção em coordenadas locais -> inercial
        Matrix3d m = AttitudeMath.localToSpaceMatrix(body, clock, loc.x(), loc.z());
        Vector3d d = m.transform(new Vector3d(ux, uy, uz));
        double[] c = steer(s, new V3(d.x, d.y, d.z).unit());
        // o motor só acende com o nariz perto da direção pedida
        double angle = AttitudeMath.noseDirection(s.attitude()).angleTo(new V3(d.x, d.y, d.z).unit());
        if (angle > 0.35) {
            throttle = 0.0;
        }
        if (this.phaseTicks % 40 == 0) {
            this.log.accept(String.format(Locale.ROOT, "DESCIDA LUNAR t=%d: altura=%.0f vv=%.2f vh=(%.2f, %.2f) alvo=%.2f acel=%.3f acelerador=%.2f combustível=%.0f zona=%s", this.phaseTicks,
                    clearance, vv, loc.vx(), loc.vz(), target, aUp, throttle, s.fuelMb(), s.zoneBody() == null ? "espaço" : "lua"));
        }
        if (this.phaseTicks > 12_000) {
            fail(s, "a descida não terminou");
        }
        return new Out(c[0], c[1], throttle, false, false);
    }

    private static final double TOUCHDOWN_SPEED = 0.55;
    /** Toque mirado no pouso no motor da Terra: com a gravidade maior a velocidade real fica acima da mirada (0,55 dava 0,95, perto do limite 1). */
    private static final double EARTH_TOUCHDOWN_SPEED = 0.3;

    // ------------------------------------------------------------------ decolagem da Lua e volta

    private Out lunarOrbit2(Snapshot s, FlightState st, double clock) {
        double[] c = steer(s, st.v().unit());
        if (this.phaseTicks == 1) {
            this.log.accept("ÓRBITA LUNAR (depois da decolagem): " + orbitText(st, clock));
        }
        if (this.phaseTicks > 100) {
            next(Phase.TEI_WAIT, s, st);
        }
        return new Out(c[0], c[1], 0.0, false, false);
    }

    private LunarPlanner.@Nullable ReturnBurn returnBurn;
    private double returnBurnAt;

    private Out teiWait(Snapshot s, FlightState st, double clock) {
        double[] c = steer(s, st.v().unit());
        if (this.phaseTicks == 1 || this.returnBurn == null) {
            Orbit o = Orbit.of(st, clock);
            this.returnBurn = LunarPlanner.returnBurn(st, clock, ENTRY_PERIAPSIS_ALTITUDE, o.period() * 1.05);
            if (this.returnBurn == null) {
                fail(s, "nenhuma queima de volta acerta a Terra nos próximos " + o.period() + " ticks");
                return Out.IDLE;
            }
            this.returnBurnAt = this.ticks + this.returnBurn.ticksToBurn();
            this.log.accept(String.format(Locale.ROOT, "VOLTA: queima prógrada de %.2f b/t em %.0f ticks (periastro da Terra previsto %.0f de altitude, saída da esfera em %.0f ticks)",
                    this.returnBurn.deltaV(), this.returnBurn.ticksToBurn(), this.returnBurn.earthPeriapsisAltitude(), this.returnBurn.ticksToExit()));
        }
        double burn = this.stats.burnTime(s.fuelMb(), this.returnBurn.deltaV());
        if (this.ticks >= this.returnBurnAt - 0.5 * burn) {
            this.plannedDv = this.returnBurn.deltaV();
            this.burnFuel = s.fuelMb();
            this.returnBurn = null;
            next(Phase.TEI, s, st);
        }
        return new Out(c[0], c[1], 0.0, false, false);
    }

    private Out tei(Snapshot s, FlightState st, double clock) {
        double burned = dv(this.burnFuel, s.fuelMb());
        double remaining = this.plannedDv - burned;
        double[] c = steer(s, st.v().unit());
        double aTick = this.stats.acceleration(s.fuelMb(), 1.0);
        if (remaining <= 0.01 || s.fuelMb() < 20.0 || st.body() == SolarSystem.EARTH) {
            next(Phase.COAST_HOME, s, st);
            return new Out(c[0], c[1], 0.0, false, false);
        }
        return new Out(c[0], c[1], Math.max(0.05, Math.min(1.0, remaining / aTick)), false, false);
    }

    /** A volta: correções do periastro da Terra e espera até a descida. */
    private Out coastHome(Snapshot s, FlightState st, double clock) {
        BodyPhysics bp = BodyPhysics.of(st.body());
        if (st.body() == SolarSystem.EARTH) {
            Out burn = runManeuver(s, st, clock);
            if (burn != null) {
                return burn;
            }
            Orbit o = Orbit.of(st, clock);
            double periAlt = o.periapsis() - bp.radius();
            int[] schedule = {300, 5_000, 12_000, 18_000};
            if (this.nextCorrection < schedule.length && this.phaseTicks >= schedule[this.nextCorrection] && st.distance() > 40_000.0) {
                this.nextCorrection++;
                V3 dv = earthPeriapsisCorrection(st, clock, ENTRY_PERIAPSIS_ALTITUDE);
                this.log.accept(String.format(Locale.ROOT, "CORREÇÃO %d da volta: periastro previsto %.0f (alvo %.0f), Δv=%s", this.nextCorrection, periAlt,
                        ENTRY_PERIAPSIS_ALTITUDE, dv == null ? "nenhum" : String.format(Locale.ROOT, "%.3f b/t", dv.length())));
                if (dv != null && dv.length() > 0.004) {
                    startManeuver(s, dv, "MCC-volta" + this.nextCorrection, false);
                }
            }
            if (st.altitude() < 14_000.0) {
                next(Phase.EARTH_ENTRY, s, st);
                return earthDescent(s, st, clock);
            }
        } else if (this.phaseTicks > 40_000) {
            fail(s, "a volta não saiu da esfera da Lua");
        }
        double[] c = steer(s, st.v().unit());
        return new Out(c[0], c[1], 0.0, false, false);
    }

    /** Δv ao longo da velocidade que leva o periastro (Terra) à altitude {@code targetAlt}; null se já está ou não tem solução. */
    private @Nullable V3 earthPeriapsisCorrection(FlightState st, double clock, double targetAlt) {
        BodyPhysics bp = BodyPhysics.of(st.body());
        double target = bp.radius() + targetAlt;
        V3 dir = st.v().unit();
        double x = 0.0;
        double f0 = periapsisAfter(st, clock, dir, 0.0) - target;
        if (Math.abs(f0) < 25.0) {
            return null;
        }
        double x1 = f0 > 0.0 ? -0.05 : 0.05;
        double f1 = periapsisAfter(st, clock, dir, x1) - target;
        double xa = 0.0;
        double fa = f0;
        double xb = x1;
        double fb = f1;
        for (int i = 0; i < 40 && Math.abs(fb) > 10.0 && fb != fa; i++) {
            double xn = xb - fb * (xb - xa) / (fb - fa);
            xn = Math.max(-5.0, Math.min(5.0, xn));
            xa = xb;
            fa = fb;
            xb = xn;
            fb = periapsisAfter(st, clock, dir, xb) - target;
        }
        x = xb;
        return Math.abs(fb) < 200.0 ? dir.times(x) : null;
    }

    private static double periapsisAfter(FlightState st, double clock, V3 dir, double dv) {
        BodyPhysics bp = BodyPhysics.of(st.body());
        return Orbit.fromState(bp.mu(), st.r(), st.v().plus(dir.times(dv)), V3.ZERO).periapsis();
    }

    // ------------------------------------------------------------------ reentrada e pouso na Terra

    private Out earthDescent(Snapshot s, FlightState st, double clock) {
        if (this.stats.parachuteK0() <= 0.0 && st.altitude() < 3_000.0) {
            // sem paraquedas (a nave): o pouso é no motor até o toque, como na Lua; a parte final da descida com paraquedas cortaria o motor
            if (this.phase == Phase.EARTH_ENTRY) {
                next(Phase.EARTH_LANDING, s, st);
            }
            return lunarDescent(s, st, clock, EARTH_POWERED_BRAKE_FRACTION);
        }
        SolarSystem.Body body = st.body();
        Local loc = FrameBridge.toDimension(body, clock, 1.0, st.r(), st.v());
        double surface = this.terrain.surfaceY(body, loc.x(), loc.z());
        double alt = st.altitude();
        double clearance = Double.isNaN(surface) ? Math.max(0.0, alt - 40.0) : loc.y() - this.boxHalfHeight - surface;
        if (s.zoneBody() == null && (Double.isNaN(surface) || alt > 3_000.0)) {
            clearance = Math.max(0.0, alt - 40.0);
        }
        this.landingClearance = clearance;
        V3 ground = st.v().minus(FrameBridge.rotationVelocity(body, clock, 1.0, st.r()));
        double speed = ground.length();
        V3 retro = ground.unit().negate();
        double[] c = steer(s, retro);
        double throttle = 0.0;
        boolean chute = false;
        double down = speed;
        double targetSpeed = Math.sqrt(25.0 + 2.0 * 0.15 * Math.max(0.0, clearance - 300.0));
        double angle = AttitudeMath.noseDirection(s.attitude()).angleTo(retro);
        if (down > targetSpeed + 0.2 && clearance < 3_000.0 && angle < 0.3) {
            throttle = Math.min(1.0, (down - targetSpeed) / 2.0);
        }
        if (clearance < 330.0 && speed < 8.0) {
            chute = true;
        }
        if (clearance < 330.0) {
            throttle = down > 1.5 && s.mode() != FlightMode.PARACHUTE ? Math.min(1.0, (down - 5.0) / 2.0) : 0.0;
            throttle = Math.max(0.0, throttle);
        }
        if (this.phase == Phase.EARTH_ENTRY && alt < 3_000.0) {
            next(Phase.EARTH_LANDING, s, st);
        }
        if (!this.entryLogged && alt < 1_800.0) {
            this.entryLogged = true;
            this.log.accept(String.format(Locale.ROOT, "REENTRADA: altitude=%.0f v ao chão=%.2f vv=%.2f vh=%.2f calor=%.2f %s", alt, speed, loc.vy(),
                    Math.hypot(loc.vx(), loc.vz()), st.heat(), orbitText(st, clock)));
        }
        if (this.ticks % 100 == 0 && alt < 8_000.0) {
            this.log.accept(String.format(Locale.ROOT, "DESCIDA TERRA t=%d: altitude=%.0f altura=%.0f vv=%.2f vh=%.2f calor=%.2f acelerador=%.2f paraquedas=%s combustível=%.0f zona=%s",
                    this.ticks, alt, clearance, loc.vy(), Math.hypot(loc.vx(), loc.vz()), st.heat(), throttle, s.mode(), s.fuelMb(),
                    s.zoneBody() == null ? "espaço" : "terra"));
        }
        if (this.phaseTicks > 30_000) {
            fail(s, "a descida da Terra não terminou");
        }
        return new Out(c[0], c[1], throttle, chute, false);
    }

    // ------------------------------------------------------------------ viagem a Marte

    private Out marsLeoWait(Snapshot s, FlightState st, double clock) {
        double[] c = steer(s, st.v().unit());
        if (this.phaseTicks == 1) {
            this.marsPlan = null;
        }
        if (this.marsPlan == null || this.ticks - this.planTick >= 20) {
            this.marsPlan = MarsPlanner.plan(st, clock, 1.0);
            this.planTick = this.ticks;
            if (!this.marsPlan.usable()) {
                fail(s, "o planejador de Marte não vale: " + this.marsPlan.status());
                return Out.IDLE;
            }
            if (this.phaseTicks <= 2) {
                this.log.accept(String.format(Locale.ROOT, "PLANEJADOR de Marte: fase atual=%.1f ideal aqui=%.1f, Δv=%.2f b/t, voo=%.0f ticks, próxima queima em %.0f ticks",
                        this.marsPlan.currentPhaseDeg(), this.marsPlan.idealPhaseDeg(), this.marsPlan.injectionDeltaV(), this.marsPlan.transferTicks(),
                        this.marsPlan.timeToWindowTicks()));
            }
        }
        if (this.marsPlan.burnNow()) {
            BodyPhysics bp = BodyPhysics.of(st.body());
            double burn = this.stats.burnTime(s.fuelMb(), this.marsPlan.injectionDeltaV());
            Kepler.State mid = Kepler.propagate(bp.mu(), st.r(), st.v(), 0.5 * burn);
            double dv = MarsPlanner.injectionDeltaV(st.withRV(mid.r(), mid.v()), clock + 0.5 * burn, MARS_ORBIT_ALTITUDE, true);
            if (!Double.isNaN(dv)) {
                this.plannedDv = dv;
                this.burnFuel = s.fuelMb();
                next(Phase.MARS_TMI, s, st);
                this.log.accept(String.format(Locale.ROOT, "INJEÇÃO: queima prógrada de %.3f b/t (%.0f ticks), fase %.1f", dv, burn, this.marsPlan.currentPhaseDeg()));
            }
        }
        return new Out(c[0], c[1], 0.0, false, false);
    }

    private Out marsInjection(Snapshot s, FlightState st, double clock) {
        double remaining = this.plannedDv - dv(this.burnFuel, s.fuelMb());
        double[] c = steer(s, st.v().unit());
        double aTick = this.stats.acceleration(s.fuelMb(), 1.0);
        if (remaining <= 0.005 || s.fuelMb() < 50.0) {
            next(Phase.MARS_COAST, s, st);
            return new Out(c[0], c[1], 0.0, false, false);
        }
        return new Out(c[0], c[1], Math.max(0.04, Math.min(1.0, remaining / aTick)), false, false);
    }

    private Out marsCoast(Snapshot s, FlightState st, double clock) {
        if (st.body() == SolarSystem.MARS) {
            next(Phase.MARS_CAPTURE_COAST, s, st);
            return Out.IDLE;
        }
        Out burn = runManeuver(s, st, clock);
        if (burn != null) {
            return burn;
        }
        int[] schedule = {400, 15_000, 35_000, 55_000};
        if (this.nextCorrection < schedule.length && this.phaseTicks >= schedule[this.nextCorrection]) {
            this.nextCorrection++;
            MarsPlanner.Arrival now = MarsPlanner.arrival(st, clock, MarsPlanner.DEFAULT_HORIZON);
            MarsPlanner.Correction corr = MarsPlanner.correction(st, clock, MARS_ORBIT_ALTITUDE, true, 6.0, MarsPlanner.DEFAULT_HORIZON);
            this.log.accept(String.format(Locale.ROOT, "CORREÇÃO %d de meio de curso (Marte): chegada prevista %s | solução %s", this.nextCorrection,
                    now == null ? "fora da esfera de Marte" : String.format(Locale.ROOT, "em %.0f ticks, periastro=%.0f, v relativa=%.2f, %s", now.ticksToEntry(),
                            now.periapsisAltitude(), now.relativeSpeed(), now.prograde() ? "prógrada" : "retrógrada"),
                    corr == null ? "nenhuma" : String.format(Locale.ROOT, "Δv=%.3f b/t -> periastro %.0f", corr.deltaV().length(), corr.arrival().periapsisAltitude())));
            if (corr != null && corr.deltaV().length() > 0.01) {
                startManeuver(s, corr.deltaV(), "MCC" + this.nextCorrection, false, true);
            }
        }
        if (this.phaseTicks > 320_000) {
            fail(s, "a viagem a Marte não chegou à esfera dele");
        }
        double[] c = steer(s, st.v().unit());
        return new Out(c[0], c[1], 0.0, false, false);
    }

    private @Nullable V3 resolveMarsCorrection(FlightState st, double clock) {
        MarsPlanner.Correction corr = MarsPlanner.correction(st, clock, MARS_ORBIT_ALTITUDE, true, 6.0, MarsPlanner.DEFAULT_HORIZON);
        return corr == null ? null : corr.deltaV();
    }

    private Out marsCaptureCoast(Snapshot s, FlightState st, double clock) {
        BodyPhysics bp = BodyPhysics.of(st.body());
        Orbit o = Orbit.of(st, clock);
        double[] c = steer(s, st.v().unit().negate());
        if (this.phaseTicks == 1) {
            this.log.accept(String.format(Locale.ROOT, "CHEGADA A MARTE: %s | inclinação=%.1f graus", orbitText(st, clock), Math.toDegrees(o.inclination())));
        }
        Out burnOut = runManeuver(s, st, clock);
        if (burnOut != null) {
            return burnOut;
        }
        int[] schedule = {200, 4_000};
        if (this.nextCorrection < schedule.length && this.phaseTicks >= schedule[this.nextCorrection]) {
            this.nextCorrection++;
            V3 dvCorr = earthPeriapsisCorrection(st, clock, MARS_ORBIT_ALTITUDE);
            this.log.accept(String.format(Locale.ROOT, "CORREÇÃO %d da chegada: periastro %.0f (alvo %.0f), Δv=%s", this.nextCorrection, o.periapsis() - bp.radius(),
                    MARS_ORBIT_ALTITUDE, dvCorr == null ? "nenhum" : String.format(Locale.ROOT, "%.3f b/t", dvCorr.length())));
            if (dvCorr != null && dvCorr.length() > 0.004) {
                this.maneuverPeriapsis = MARS_ORBIT_ALTITUDE;
                startManeuver(s, dvCorr, "MCC-Marte" + this.nextCorrection, false, false);
            }
        }
        double vPeri = o.speedAt(o.periapsis());
        double dvCapture = Math.max(0.0, vPeri - bp.circularSpeed(o.periapsis()));
        double burn = this.stats.burnTime(s.fuelMb(), dvCapture);
        double toPeri = o.timeTo(0.0);
        if (Double.isNaN(toPeri) || toPeri < 0.0) {
            fail(s, "passou do periastro de Marte sem capturar");
            return Out.IDLE;
        }
        if (toPeri < 0.5 * burn + 2.0 && this.maneuver == null) {
            this.burnFuel = s.fuelMb();
            this.plannedDv = dvCapture;
            next(Phase.MARS_CAPTURE, s, st);
            this.log.accept(String.format(Locale.ROOT, "CAPTURA em Marte: queima retrógrada de %.2f b/t em %.0f ticks no periastro (%.0f de altitude)", dvCapture, burn,
                    o.periapsis() - bp.radius()));
        }
        return new Out(c[0], c[1], 0.0, false, false);
    }

    private Out marsCapture(Snapshot s, FlightState st, double clock) {
        BodyPhysics bp = BodyPhysics.of(st.body());
        double remaining = st.v().length() - bp.circularSpeed(st.r().length());
        double[] c = steer(s, st.v().unit().negate());
        double aTick = this.stats.acceleration(s.fuelMb(), 1.0);
        if (remaining <= 0.02 && Orbit.of(st, clock).bound() || s.fuelMb() < 50.0) {
            next(Phase.MARS_ORBIT, s, st);
            return new Out(c[0], c[1], 0.0, false, false);
        }
        if (this.phaseTicks > 3_000) {
            fail(s, "a captura em Marte não fechou");
        }
        return new Out(c[0], c[1], Math.max(0.05, Math.min(1.0, remaining / aTick)), false, false);
    }

    private Out marsOrbit(Snapshot s, FlightState st, double clock) {
        double[] c = steer(s, st.v().unit().negate());
        if (this.phaseTicks == 1) {
            this.log.accept("ÓRBITA DE MARTE: " + orbitText(st, clock));
        }
        if (this.phaseTicks > this.orbitWait) {
            next(Phase.MARS_BRAKE, s, st);
        }
        return new Out(c[0], c[1], 0.0, false, false);
    }

    /** Freia contra a velocidade em relação ao chão de Marte (ele gira) até quase parar. */
    private Out marsBrake(Snapshot s, FlightState st, double clock) {
        V3 ground = st.v().minus(FrameBridge.rotationVelocity(st.body(), clock, 1.0, st.r()));
        double speed = ground.length();
        double[] c = steer(s, ground.unit().negate());
        double aTick = this.stats.acceleration(s.fuelMb(), 1.0);
        if (speed < 1.2 || s.fuelMb() < 20.0) {
            next(Phase.MARS_DESCENT, s, st);
            return new Out(c[0], c[1], 0.0, false, false);
        }
        double angle = AttitudeMath.noseDirection(s.attitude()).angleTo(ground.unit().negate());
        if (angle > 0.15) {
            return new Out(c[0], c[1], 0.0, false, false);
        }
        return new Out(c[0], c[1], Math.max(0.05, Math.min(1.0, (speed - 1.0) / aTick)), false, false);
    }

    /**
     * Descida em Marte: cai da órbita de espera (a atmosfera fina quase não freia), freia no motor contra a velocidade ao chão até menos de
     * {@value #MARS_CHUTE_SPEED} b/t perto do chão, pede o paraquedas (a densidade de Marte decide o quanto ele freia) e corta o motor: o toque
     * é a velocidade terminal do paraquedas em Marte.
     */
    private static final double MARS_CHUTE_SPEED = 6.0;

    private Out marsDescent(Snapshot s, FlightState st, double clock) {
        SolarSystem.Body body = st.body();
        Local loc = FrameBridge.toDimension(body, clock, this.clockRate, st.r(), st.v());
        double surface = this.terrain.surfaceY(body, loc.x(), loc.z());
        double clearance = Double.isNaN(surface) ? loc.y() - body.planet().seaLevel() - 120.0 : loc.y() - this.boxHalfHeight - surface;
        this.landingClearance = clearance;
        V3 ground = st.v().minus(FrameBridge.rotationVelocity(body, clock, this.clockRate, st.r()));
        double speed = ground.length();
        V3 retro = ground.unit().negate();
        double[] c = steer(s, retro);
        double aMax = this.stats.acceleration(s.fuelMb(), 1.0);
        double throttle = 0.0;
        boolean chute = clearance < 1_000.0;
        boolean opened = s.mode() == FlightMode.PARACHUTE;
        double angle = AttitudeMath.noseDirection(s.attitude()).angleTo(retro);
        // perfil de frenagem: nunca acima de MARS_CHUTE_SPEED perto do chão; o motor só acende com o nariz no retrógrado
        double limit = clearance > 1_200.0 ? 1.0e9 : MARS_CHUTE_SPEED;
        if (!opened && speed > limit && clearance < 1_200.0 && angle < 0.3) {
            throttle = Math.min(1.0, 0.2 + (speed - limit) * 0.1 / Math.max(aMax, 1.0e-6));
        }
        if (this.phaseTicks % 100 == 0) {
            this.log.accept(String.format(Locale.ROOT, "DESCIDA MARTE t=%d: altura=%.0f v ao chão=%.2f vv=%.2f vh=%.2f acelerador=%.2f paraquedas=%s combustível=%.0f zona=%s", this.phaseTicks,
                    clearance, speed, loc.vy(), Math.hypot(loc.vx(), loc.vz()), throttle, s.mode(), s.fuelMb(), s.zoneBody() == null ? "espaço" : "marte"));
        }
        if (this.phaseTicks > 20_000) {
            fail(s, "a descida de Marte não terminou");
        }
        return new Out(c[0], c[1], throttle, chute, false);
    }

    double maxHeat() {
        return this.maxHeat;
    }

    double landingClearance() {
        return this.landingClearance;
    }

    // ------------------------------------------------------------------ atitude

    /** Direção do leste (inercial) no ponto: a da velocidade do chão por rotação, ω × r. */
    private static V3 east(FlightState st, double clock) {
        return FrameBridge.rotationVelocity(st.body(), clock, 1.0, st.r()).unit();
    }

    /** Comando proporcional de arfagem e guinada para apontar o nariz para {@code desired} (inercial): {pitch, yaw}. */
    private static double[] steer(Snapshot s, V3 desired) {
        V3 nose = AttitudeMath.noseDirection(s.attitude());
        double angle = nose.angleTo(desired);
        if (angle < 1.0e-4 || desired.lengthSquared() == 0.0) {
            return new double[] {0.0, 0.0};
        }
        V3 axis = nose.cross(desired);
        if (axis.length() < 1.0e-9) {
            axis = nose.cross(new V3(1.0, 0.0, 0.0));
        }
        axis = axis.unit();
        Vector3d inBody = s.attitude().transformInverse(axis.x(), axis.y(), axis.z(), new Vector3d());
        double k = 0.35 * angle / AttitudeControl.MAX_SPIN;
        return new double[] {Math.max(-1.0, Math.min(1.0, inBody.x * k)), Math.max(-1.0, Math.min(1.0, inBody.z * k))};
    }
}
