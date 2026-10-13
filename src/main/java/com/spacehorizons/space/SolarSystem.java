package com.spacehorizons.space;

import java.util.List;
import java.util.Optional;
import java.util.function.DoubleFunction;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;

/**
 * Posição e orientação dos corpos no "espaço": um referencial único, em blocos, preso à Terra (a Terra fica parada na
 * origem e o sol gira em volta uma vez por dia do jogo, ver {@link SunModel}). O tempo é sempre o relógio do Overworld
 * em ticks (o mesmo do sol; {@code /time set} move os dois), igual no cliente e no servidor.
 *
 * <h2>A Lua</h2>
 * Orbita a Terra num círculo de raio {@link #EARTH_MOON_DISTANCE}, inclinado {@link #ORBIT_INCLINATION} em relação ao
 * equador. O ângulo da Lua no plano da órbita é o do sol menos uma volta a cada {@link #SYNODIC_PERIOD_TICKS} (8 dias do
 * jogo, como as 8 fases do vanilla): ela nasce e se põe todo dia (~1,14 dia entre dois nasceres) e muda de fase de modo
 * que o dia 0 tem lua cheia à meia-noite, como no vanilla. A rotação é sincronizada: os eixos do corpo giram com a
 * órbita, de modo que o ponto lon=π (x = ±C/2 na dimensão da Lua) aponta sempre para a Terra.
 *
 * <h2>Eixos</h2>
 * Cada corpo tem eixos próprios fixos nele: o ponto (lat 0, lon 0) aponta para +X, leste (+lon, +X do Minecraft) para
 * +Z e +lat (+Z do Minecraft) para +Y (ver SpaceFrame). A {@link Pose} do corpo num instante leva esses eixos para os
 * do espaço; a Terra tem a identidade.
 */
public final class SolarSystem {
    /** Distância centro a centro Terra–Lua (comprimida: ~5,75 raios terrestres em vez de 60). */
    public static final double EARTH_MOON_DISTANCE = 60_000;

    /** Ciclo de fases (sinódico) da Lua: 8 dias do jogo, as 8 fases do vanilla. */
    public static final double SYNODIC_PERIOD_TICKS = 8.0 * SunModel.DAY_TICKS;

    /** Inclinação da órbita lunar em relação ao equador (plano do sol): evita eclipse a cada lua nova e cheia. */
    public static final double ORBIT_INCLINATION = Math.toRadians(5.0);

    /** Tick da lua cheia do dia 0: meia-noite, como no vanilla (fase 0 = lua cheia). */
    private static final double FULL_MOON_TICK = 18_000.0;

    private static final double[] IDENTITY = {1, 0, 0, 0, 1, 0, 0, 0, 1};

    /**
     * Posição do centro do corpo no espaço e rotação dos eixos do corpo para os do espaço. {@code rotation} é uma
     * matriz 3x3 em ordem de linhas: espaço = R · eixos-do-corpo (as colunas são os eixos do corpo vistos do espaço).
     * Imutável por convenção: ninguém altera o array.
     */
    public record Pose(double x, double y, double z, double[] rotation) {
        /** Vetor nos eixos do corpo → mesmo vetor nos eixos do espaço (só rotação; {@code out} não pode ser o array de entrada). */
        public void rotate(double bx, double by, double bz, double[] out) {
            double[] r = rotation;
            out[0] = r[0] * bx + r[1] * by + r[2] * bz;
            out[1] = r[3] * bx + r[4] * by + r[5] * bz;
            out[2] = r[6] * bx + r[7] * by + r[8] * bz;
        }

        /** Vetor nos eixos do espaço → eixos do corpo (rotação inversa = transposta). */
        public void unrotate(double vx, double vy, double vz, double[] out) {
            double[] r = rotation;
            out[0] = r[0] * vx + r[3] * vy + r[6] * vz;
            out[1] = r[1] * vx + r[4] * vy + r[7] * vz;
            out[2] = r[2] * vx + r[5] * vy + r[8] * vz;
        }
    }

    /**
     * Um corpo: o planeta e como ele se move. {@link #poseAt} dá posição e orientação num instante; {@link #x()},
     * {@link #y()} e {@link #z()} são a posição da configuração de REFERÊNCIA (a Lua no eixo +X, sem inclinação, com os
     * eixos alinhados aos da Terra), útil só para contas estáticas e testes; o movimento real é sempre por {@code poseAt}.
     */
    public static final class Body {
        private record Cached(double clock, Pose pose) {
        }

        private final PlanetDefinition planet;
        private final DoubleFunction<Pose> motion;
        private final Pose reference;
        /** Um instante em cache: o mesmo relógio é pedido dezenas de vezes por frame. Imutável e volátil: seguro entre threads. */
        private volatile Cached cached;

        private Body(PlanetDefinition planet, DoubleFunction<Pose> motion, Pose reference) {
            this.planet = planet;
            this.motion = motion;
            this.reference = reference;
        }

        public PlanetDefinition planet() {
            return planet;
        }

        /** Posição e orientação do corpo no relógio {@code clock} (ticks do Overworld, com fração). */
        public Pose poseAt(double clock) {
            Cached c = cached;
            if (c != null && c.clock == clock) {
                return c.pose;
            }
            Pose pose = motion.apply(clock);
            cached = new Cached(clock, pose);
            return pose;
        }

        /** Configuração de referência (ver a documentação da classe). */
        public Pose referencePose() {
            return reference;
        }

        /** Centro do corpo no espaço em {@code clock}. out = {x, y, z}. */
        public void positionAt(double clock, double[] out) {
            Pose p = poseAt(clock);
            out[0] = p.x();
            out[1] = p.y();
            out[2] = p.z();
        }

        /** Velocidade do centro do corpo no espaço (blocos/tick), por diferença central. out = {vx, vy, vz}. */
        public void velocityAt(double clock, double[] out) {
            double h = 0.5;
            Pose a = motion.apply(clock - h);
            Pose b = motion.apply(clock + h);
            out[0] = (b.x() - a.x()) / (2 * h);
            out[1] = (b.y() - a.y()) / (2 * h);
            out[2] = (b.z() - a.z()) / (2 * h);
        }

        /** Centro na configuração de referência (a Lua em (60000, 0, 0)). */
        public double x() {
            return reference.x();
        }

        public double y() {
            return reference.y();
        }

        public double z() {
            return reference.z();
        }
    }

    /** A Terra fica parada na origem com a identidade: o espaço é o referencial dela. */
    public static final Body EARTH = new Body(Planets.EARTH, clock -> new Pose(0, 0, 0, IDENTITY), new Pose(0, 0, 0, IDENTITY));
    public static final Body MOON = new Body(Planets.MOON, SolarSystem::moonPose, new Pose(EARTH_MOON_DISTANCE, 0, 0, IDENTITY));

    public static final List<Body> BODIES = List.of(EARTH, MOON);

    private SolarSystem() {
    }

    public static Optional<Body> bodyOf(PlanetDefinition planet) {
        return BODIES.stream().filter(b -> b.planet().equals(planet)).findFirst();
    }

    /**
     * Elongação da Lua (rad): ângulo dela menos o do sol no plano da órbita. 0 = lua nova, -π = cheia (a Lua gira para o
     * leste do sol: o ângulo diminui com o tempo, uma volta a cada {@link #SYNODIC_PERIOD_TICKS}).
     */
    public static double moonElongation(double clock) {
        return -Math.PI - 2.0 * Math.PI * (clock - FULL_MOON_TICK) / SYNODIC_PERIOD_TICKS;
    }

    /** Ângulo da Lua no plano da órbita (rad, mesma convenção do sol: direção (cos, 0, -sen) antes da inclinação). */
    public static double moonAngle(double clock) {
        return SunModel.angle(clock) + moonElongation(clock);
    }

    /** Fração iluminada da Lua vista da Terra (0 = nova, 1 = cheia), pelo ângulo entre o sol e a Lua. */
    public static double moonIlluminatedFraction(double clock) {
        double[] sun = new double[3];
        SunModel.directionInSpace(clock, sun);
        double[] m = new double[3];
        MOON.positionAt(clock, m);
        double cosElongation = (sun[0] * m[0] + sun[1] * m[1] + sun[2] * m[2]) / EARTH_MOON_DISTANCE;
        return 0.5 * (1.0 - cosElongation);
    }

    /**
     * Pose da Lua: R = Rz(i)·Ry(θ). A primeira coluna de R (o eixo +X do corpo, lat 0 lon 0) é a direção Terra→Lua;
     * logo o ponto lon=π (-X do corpo) aponta sempre para a Terra. A segunda (norte) é a normal da órbita.
     */
    private static Pose moonPose(double clock) {
        double theta = moonAngle(clock);
        double c = Math.cos(theta);
        double s = Math.sin(theta);
        double ci = Math.cos(ORBIT_INCLINATION);
        double si = Math.sin(ORBIT_INCLINATION);
        double[] r = {
                ci * c, -si, ci * s,
                si * c, ci, si * s,
                -s, 0.0, c};
        return new Pose(EARTH_MOON_DISTANCE * r[0], EARTH_MOON_DISTANCE * r[3], EARTH_MOON_DISTANCE * r[6], r);
    }
}
