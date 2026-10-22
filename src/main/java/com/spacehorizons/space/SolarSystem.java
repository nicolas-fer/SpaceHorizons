package com.spacehorizons.space;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleFunction;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.space.flight.FlightConstants;

/**
 * Posição e orientação dos corpos no "espaço", em blocos, em DOIS referenciais com a mesma origem (o centro da Terra):
 * <ul>
 *   <li><b>E, preso à Terra</b> ({@link #poseAt}, {@link #positionAt}, {@link #velocityAt}): a Terra fica parada e o sol
 *       gira em volta uma vez por dia do jogo ({@link SunModel}). É o referencial dos "lugares": dimensões, renderização,
 *       coordenadas do jogador, piloto automático criativo;</li>
 *   <li><b>I, inercial</b> ({@link #inertialPoseAt}, {@link #inertialPositionAt}, {@link #inertialVelocityAt}): o sol fica
 *       fixo e a Terra gira para o leste. É o referencial da física do foguete e da órbita da Lua. A ponte entre os dois
 *       é {@link InertialFrame}.</li>
 * </ul>
 * O tempo é sempre o relógio do Overworld em ticks (o mesmo do sol; {@code /time set} move tudo), igual no cliente e no
 * servidor.
 *
 * <h2>A Lua</h2>
 * Tem órbita kepleriana circular em I: raio {@link #EARTH_MOON_DISTANCE}, inclinação {@link #ORBIT_INCLINATION} em relação
 * ao equador, período {@link #ORBITAL_PERIOD_TICKS} ({@code 2π·sqrt(a³/μ_Terra)}, ~4,12 dias) e sentido PRÓGRADO (o da
 * rotação da Terra, para o leste). Como o sol é fixo em I, o ciclo de fases é o próprio período orbital; a fase inicial
 * faz do tick 18.000 do dia 0 a lua cheia à meia-noite (a Lua em +X, o sol em −X). Em E ela nasce a leste e se põe a oeste
 * a cada ~1,32 dia ({@code 1/(1/24000 − 1/período)}) e o plano da órbita oscila ao longo do dia. A rotação é sincronizada:
 * os eixos do corpo giram com a órbita, de modo que o ponto lon=π (x = ±C/2 na dimensão da Lua) aponta sempre para a
 * Terra.
 *
 * <h2>Eixos</h2>
 * Cada corpo tem eixos próprios fixos nele: o ponto (lat 0, lon 0) aponta para +X, leste (+lon, +X do Minecraft) para
 * +Z e +lat (+Z do Minecraft) para +Y (ver SpaceFrame). A {@link Pose} do corpo num instante leva esses eixos para os
 * do espaço; a Terra tem a identidade.
 */
public final class SolarSystem {
    /** Distância centro a centro Terra–Lua (comprimida: ~5,75 raios terrestres em vez de 60). */
    public static final double EARTH_MOON_DISTANCE = 600_000;

    /** Parâmetro gravitacional da Terra (blocos³/tick²): {@code G0 · gravidade de superfície · R²}, o mesmo de BodyPhysics. */
    public static final double EARTH_MU = FlightConstants.G0 * Planets.EARTH.surfaceGravity() * Planets.EARTH.radius()
            * Planets.EARTH.radius();

    /** Movimento médio da Lua (rad por tick de relógio): {@code sqrt(μ/a³)}. */
    public static final double MOON_MEAN_MOTION = Math.sqrt(EARTH_MU / (EARTH_MOON_DISTANCE * EARTH_MOON_DISTANCE * EARTH_MOON_DISTANCE));

    /** Período orbital da Lua em I (ticks): ~98.985, ~4,12 dias do jogo. É também o ciclo de fases (o sol é fixo em I). */
    public static final double ORBITAL_PERIOD_TICKS = 2.0 * Math.PI / MOON_MEAN_MOTION;

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
        /** Movimento em I (inercial). */
        private final DoubleFunction<Pose> inertialMotion;
        /** Movimento em E (preso à Terra). */
        private final DoubleFunction<Pose> motion;
        private final Pose reference;
        /** Um instante em cache: o mesmo relógio é pedido dezenas de vezes por frame. Imutável e volátil: seguro entre threads. */
        private volatile Cached cached;
        private volatile Cached cachedInertial;

        private Body(PlanetDefinition planet, DoubleFunction<Pose> inertialMotion, DoubleFunction<Pose> motion, Pose reference) {
            this.planet = planet;
            this.inertialMotion = inertialMotion;
            this.motion = motion;
            this.reference = reference;
        }

        public PlanetDefinition planet() {
            return planet;
        }

        /** Posição e orientação do corpo no relógio {@code clock} (ticks do Overworld, com fração), em E (preso à Terra). */
        public Pose poseAt(double clock) {
            Cached c = cached;
            if (c != null && c.clock == clock) {
                return c.pose;
            }
            Pose pose = motion.apply(clock);
            cached = new Cached(clock, pose);
            return pose;
        }

        /** Posição e orientação do corpo em {@code clock}, em I (inercial: o sol fixo, a Terra girando). */
        public Pose inertialPoseAt(double clock) {
            Cached c = cachedInertial;
            if (c != null && c.clock == clock) {
                return c.pose;
            }
            Pose pose = inertialMotion.apply(clock);
            cachedInertial = new Cached(clock, pose);
            return pose;
        }

        /** Configuração de referência (ver a documentação da classe). */
        public Pose referencePose() {
            return reference;
        }

        /** Centro do corpo no espaço em {@code clock}, em E. out = {x, y, z}. */
        public void positionAt(double clock, double[] out) {
            Pose p = poseAt(clock);
            out[0] = p.x();
            out[1] = p.y();
            out[2] = p.z();
        }

        /** Centro do corpo em {@code clock}, em I. out = {x, y, z}. */
        public void inertialPositionAt(double clock, double[] out) {
            Pose p = inertialPoseAt(clock);
            out[0] = p.x();
            out[1] = p.y();
            out[2] = p.z();
        }

        /** Velocidade do centro do corpo em E (blocos por tick de relógio), por diferença central. out = {vx, vy, vz}. */
        public void velocityAt(double clock, double[] out) {
            centralDifference(motion, clock, out);
        }

        /** Velocidade do centro do corpo em I (blocos por tick de relógio), por diferença central. out = {vx, vy, vz}. */
        public void inertialVelocityAt(double clock, double[] out) {
            centralDifference(inertialMotion, clock, out);
        }

        private static void centralDifference(DoubleFunction<Pose> f, double clock, double[] out) {
            double h = 0.5;
            Pose a = f.apply(clock - h);
            Pose b = f.apply(clock + h);
            out[0] = (b.x() - a.x()) / (2 * h);
            out[1] = (b.y() - a.y()) / (2 * h);
            out[2] = (b.z() - a.z()) / (2 * h);
        }

        /** Centro na configuração de referência (a Lua em (600000, 0, 0)). */
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

    /**
     * A Terra fica parada na origem com a identidade em E (o referencial dela); em I seus eixos giram: o corpo leva os
     * eixos presos a ela a I por {@code Ry(−φ)} (ver {@link InertialFrame}).
     */
    public static final Body EARTH = new Body(Planets.EARTH,
            clock -> new Pose(0, 0, 0, InertialFrame.rotationY(-InertialFrame.angle(clock))),
            clock -> new Pose(0, 0, 0, IDENTITY), new Pose(0, 0, 0, IDENTITY));
    public static final Body MOON = new Body(Planets.MOON, SolarSystem::moonInertialPose, SolarSystem::moonPose,
            new Pose(EARTH_MOON_DISTANCE, 0, 0, IDENTITY));

    public static final List<Body> BODIES = List.of(EARTH, MOON);

    private SolarSystem() {
    }

    public static Optional<Body> bodyOf(PlanetDefinition planet) {
        return BODIES.stream().filter(b -> b.planet().equals(planet)).findFirst();
    }

    /** Corpos "de céu" dos planetas fora do sistema (por id do planeta), criados sob demanda. */
    private static final Map<PlanetDefinition, Body> DETACHED = new ConcurrentHashMap<>();

    /**
     * Corpo que dá o céu (sol, dia e noite, estrelas) de um planeta: o do sistema quando ele está em {@link #BODIES}; senão
     * um corpo DESTACADO, que não entra em nenhuma física, órbita, zona ou travessia (não está em BODIES) e só gira, para
     * que o sol do lugar dê uma volta a cada {@code day_length_ticks} dos parâmetros do planeta. É o caso de Marte até o
     * sistema centrado no sol (etapa 3 dos tiers); a posição é a origem, porque só as direções importam para o céu.
     */
    public static Body skyBodyOf(PlanetDefinition planet) {
        Optional<Body> body = bodyOf(planet);
        return body.isPresent() ? body.get() : DETACHED.computeIfAbsent(planet, SolarSystem::detached);
    }

    /**
     * Corpo destacado com dia de {@code D} ticks. O sol local do ponto (0, 0) do corpo fica no ângulo β(t) = 2π·skyEase(fração
     * do dia local desde o meio-dia), com o meio-dia no tick 6.000 do dia 0 e a mesma curva do vanilla (SunModel), então com
     * D = 24.000 o céu é o da Terra. Como o sol local é {@code a_sol(t) − γ} para um corpo girado por Ry(γ) em E, a pose em E é
     * Ry(a_sol − β) e a inercial, Ry(π − β) (E = Ry(φ_Terra)·I, φ_Terra = a_sol − π).
     */
    private static Body detached(PlanetDefinition planet) {
        double day = com.spacehorizons.planet.Planets.spec(planet).dayLengthTicks();
        DoubleFunction<Pose> inertial = clock -> new Pose(0, 0, 0, InertialFrame.rotationY(Math.PI - localSunAngle(clock, day)));
        DoubleFunction<Pose> fixed = clock -> new Pose(0, 0, 0,
                InertialFrame.rotationY(SunModel.angle(clock) - localSunAngle(clock, day)));
        return new Body(planet, inertial, fixed, new Pose(0, 0, 0, IDENTITY));
    }

    /** Ângulo do sol local de um corpo destacado (rad, 0 = meio-dia no ponto (0, 0)) com dia de {@code day} ticks. */
    static double localSunAngle(double clock, double day) {
        double fraction = (clock - 6_000.0) / day;
        fraction -= Math.floor(fraction);
        return 2.0 * Math.PI * SunModel.skyEase(fraction);
    }

    /**
     * Elongação da Lua (rad): ângulo dela menos o do sol no plano da órbita. 0 = lua nova, -π = cheia (a Lua gira para o
     * leste: o ângulo diminui com o tempo, uma volta a cada {@link #ORBITAL_PERIOD_TICKS}; o sol é fixo em I).
     */
    public static double moonElongation(double clock) {
        return -Math.PI - MOON_MEAN_MOTION * (clock - FULL_MOON_TICK);
    }

    /**
     * Ângulo da Lua no plano da órbita EM I (rad, mesma convenção do sol: direção (cos, 0, -sen) antes da inclinação).
     * O sol fica em π nesse referencial (direção −X), por isso a elongação é este ângulo menos π.
     */
    public static double moonAngle(double clock) {
        return moonElongation(clock) + Math.PI;
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

    /** Pose da Lua em E: a de I girada por {@code Ry(φ)} (posição e eixos do corpo). */
    private static Pose moonPose(double clock) {
        Pose inertial = moonInertialPose(clock);
        double[] earth = InertialFrame.rotationY(InertialFrame.angle(clock));
        double[] r = inertial.rotation();
        double[] out = new double[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                out[row * 3 + col] = earth[row * 3] * r[col] + earth[row * 3 + 1] * r[3 + col] + earth[row * 3 + 2] * r[6 + col];
            }
        }
        double[] p = new double[3];
        InertialFrame.toEarthFixed(clock, inertial.x(), inertial.y(), inertial.z(), p);
        return new Pose(p[0], p[1], p[2], out);
    }

    /**
     * Pose da Lua em I: R = Rz(i)·Ry(θ). A primeira coluna de R (o eixo +X do corpo, lat 0 lon 0) é a direção Terra→Lua;
     * logo o ponto lon=π (-X do corpo) aponta sempre para a Terra. A segunda (norte) é a normal da órbita.
     */
    private static Pose moonInertialPose(double clock) {
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
