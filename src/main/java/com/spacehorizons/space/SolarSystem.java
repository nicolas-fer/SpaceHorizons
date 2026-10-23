package com.spacehorizons.space;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleFunction;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.TerrainStyle;
import com.spacehorizons.space.flight.FlightConstants;

import net.minecraft.resources.Identifier;

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

    /** O referencial do estado de voo de um corpo (ver {@link Body#frame}). */
    public enum Frame {
        /** I: origem na Terra, o sol fixo em −X ({@link InertialFrame}). A Terra e a Lua. */
        EARTH_SYSTEM,
        /** H: origem no sol, eixos que não giram ({@link Heliocentric}). O sol e Marte. */
        HELIOCENTRIC
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
        private final Frame frame;
        /** Posição do centro em H (heliocêntrico, relativa ao sol); null nos corpos do sistema Terra-Lua que não são a Terra. */
        private final DoubleFunction<Pose> heliocentric;
        /** Movimento no referencial de estado do corpo (I para a Terra e a Lua, H para o sol e Marte). */
        private final DoubleFunction<Pose> inertialMotion;
        /** Movimento em E (preso à Terra). */
        private final DoubleFunction<Pose> motion;
        private final Pose reference;
        /** Um instante em cache: o mesmo relógio é pedido dezenas de vezes por frame. Imutável e volátil: seguro entre threads. */
        private volatile Cached cached;
        private volatile Cached cachedInertial;

        private Body(PlanetDefinition planet, DoubleFunction<Pose> inertialMotion, DoubleFunction<Pose> motion, Pose reference) {
            this(planet, Frame.EARTH_SYSTEM, null, inertialMotion, motion, reference);
        }

        private Body(PlanetDefinition planet, Frame frame, DoubleFunction<Pose> heliocentric, DoubleFunction<Pose> inertialMotion,
                DoubleFunction<Pose> motion, Pose reference) {
            this.planet = planet;
            this.frame = frame;
            this.heliocentric = heliocentric;
            this.inertialMotion = inertialMotion;
            this.motion = motion;
            this.reference = reference;
        }

        /**
         * O referencial em que o estado de voo (posição e velocidade relativas a este corpo) e a pose inercial dele vivem: I (o
         * sistema Terra-Lua, sol fixo) para a Terra e a Lua; H (heliocêntrico, eixos que não giram) para o sol e Marte.
         */
        public Frame frame() {
            return frame;
        }

        /** O sol é a mesma direção para todos os pontos deste corpo (Terra e Lua: a aproximação de sempre, o sol no infinito)? */
        public boolean parallelSun() {
            return frame == Frame.EARTH_SYSTEM;
        }

        /**
         * Centro do corpo em H (relativo ao sol) em {@code clock}. Para a Lua é o da Terra mais o da Lua em I girado a H; para o sol é
         * a origem. out = {x, y, z}.
         */
        public void heliocentricPositionAt(double clock, double[] out) {
            if (heliocentric != null) {
                Pose p = heliocentric.apply(clock);
                out[0] = p.x();
                out[1] = p.y();
                out[2] = p.z();
                return;
            }
            double[] earth = new double[3];
            Heliocentric.earthPosition(clock, earth);
            Pose p = inertialMotion.apply(clock);
            double[] h = new double[3];
            Heliocentric.fromEarthSystem(clock, p.x(), p.y(), p.z(), h);
            out[0] = earth[0] + h[0];
            out[1] = earth[1] + h[1];
            out[2] = earth[2] + h[2];
        }

        /** Velocidade do centro em H (blocos por tick de RELÓGIO), por diferença central. out = {vx, vy, vz}. */
        public void heliocentricVelocityAt(double clock, double[] out) {
            double h = 0.5;
            double[] a = new double[3];
            double[] b = new double[3];
            heliocentricPositionAt(clock - h, a);
            heliocentricPositionAt(clock + h, b);
            out[0] = (b[0] - a[0]) / (2 * h);
            out[1] = (b[1] - a[1]) / (2 * h);
            out[2] = (b[2] - a[2]) / (2 * h);
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
    public static final Body EARTH = new Body(Planets.EARTH, Frame.EARTH_SYSTEM, clock -> helioPose(Heliocentric::earthPosition, clock),
            clock -> new Pose(0, 0, 0, InertialFrame.rotationY(-InertialFrame.angle(clock))),
            clock -> new Pose(0, 0, 0, IDENTITY), new Pose(0, 0, 0, IDENTITY));
    public static final Body MOON = new Body(Planets.MOON, SolarSystem::moonInertialPose, SolarSystem::moonPose,
            new Pose(EARTH_MOON_DISTANCE, 0, 0, IDENTITY));

    /** Distância Terra-Marte na configuração de REFERÊNCIA (Marte em oposição, em +X): só para contas estáticas, nunca para o movimento. */
    private static final double MARS_REFERENCE_DISTANCE = Heliocentric.MARS_ORBIT_RADIUS - Heliocentric.EARTH_ORBIT_RADIUS;

    /**
     * Marte em órbita do sol: o estado de voo dele (corpo dominante) vive em H, com a pose inercial {@link #marsHelioPose}; a pose
     * em E (a que o céu e as zonas usam) é {@link #marsPose}, a posição real relativa à Terra.
     */
    public static final Body MARS = new Body(Planets.MARS, Frame.HELIOCENTRIC, clock -> helioPose(Heliocentric::marsPosition, clock),
            SolarSystem::marsHelioPose, SolarSystem::marsPose, new Pose(MARS_REFERENCE_DISTANCE, 0, 0, IDENTITY));

    /** O "planeta" do sol: só dá raio e gravidade à física (a esfera central de H); não tem dimensão, mapa nem parâmetros em JSON. */
    private static final PlanetDefinition SUN_PLANET = sunPlanet();

    /**
     * O sol como corpo central de H (corpo dominante do espaço interplanetário). Não está em {@link #BODIES}: não tem dimensão,
     * zona nem céu próprio; só existe como dono do estado de voo fora das esferas dos planetas.
     */
    public static final Body SUN = new Body(SUN_PLANET, Frame.HELIOCENTRIC, clock -> new Pose(0, 0, 0, IDENTITY),
            clock -> new Pose(0, 0, 0, IDENTITY), SolarSystem::sunPose, new Pose(0, 0, 0, IDENTITY));

    /** Os corpos de superfície do sistema (os que têm dimensão, zona e céu): Terra, Lua e Marte. O sol fica de fora ({@link #SUN}). */
    public static final List<Body> BODIES = List.of(EARTH, MOON, MARS);

    private SolarSystem() {
    }

    public static Optional<Body> bodyOf(PlanetDefinition planet) {
        return BODIES.stream().filter(b -> b.planet().equals(planet)).findFirst();
    }

    /** Corpos "de céu" dos planetas fora do sistema (por id do planeta), criados sob demanda. */
    private static final Map<PlanetDefinition, Body> DETACHED = new ConcurrentHashMap<>();

    /**
     * Corpo que dá o céu (sol, dia e noite, estrelas) de um planeta: o do sistema quando ele está em {@link #BODIES} (a Terra, a Lua e
     * Marte, que desde o sistema centrado no sol tem posição e fase reais); senão um corpo DESTACADO, que não entra em nenhuma
     * física, órbita, zona ou travessia (não está em BODIES) e só gira, para que o sol do lugar dê uma volta a cada
     * {@code day_length_ticks} dos parâmetros do planeta. É o caso de um planeta que tem parâmetros mas ainda não tem órbita
     * (planetas distantes do Tier 2); a posição é a origem, porque só as direções importam para o céu.
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
        return detached(planet, com.spacehorizons.planet.Planets.spec(planet).dayLengthTicks());
    }

    /** Corpo destacado de {@code planet} com um dia de {@code day} ticks (a conta de {@link #detached(PlanetDefinition)}, também para os testes). */
    static Body detached(PlanetDefinition planet, double day) {
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

    private static PlanetDefinition sunPlanet() {
        // circunferência múltipla de 16 (exigência da definição); a gravidade sai do mu do sol: mu = G0 · g · R²
        int circumference = 2_513_280;
        double radius = circumference / (2.0 * Math.PI);
        double gravity = Heliocentric.MU_SUN / (FlightConstants.G0 * radius * radius);
        return new PlanetDefinition(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "sun"), 0L, circumference, 0, 60.0, 0,
                TerrainStyle.PARAMETRIC, gravity);
    }

    /** Pose com o centro dado por uma função de H e a orientação identidade. */
    private static Pose helioPose(java.util.function.BiConsumer<Double, double[]> position, double clock) {
        double[] p = new double[3];
        position.accept(clock, p);
        return new Pose(p[0], p[1], p[2], IDENTITY);
    }

    /**
     * Pose de Marte em H: o centro na órbita e os eixos do corpo girados por {@code Ry(π − θ_M − β(t))}, onde θ_M é o ângulo de Marte e β
     * o ângulo do sol local do ponto (0, 0) ({@link #localSunAngle}, com o dia de Marte e a curva do vanilla). Como o sol visto de Marte
     * é a direção −centro, o sol local do ponto (0, 0) fica EXATAMENTE em β: o céu do chão de Marte segue o dia de
     * {@code day_length_ticks}, do mesmo jeito que o do corpo destacado de antes, mas com o sol na direção verdadeira.
     */
    private static Pose marsHelioPose(double clock) {
        double[] p = new double[3];
        Heliocentric.marsPosition(clock, p);
        return new Pose(p[0], p[1], p[2], InertialFrame.rotationY(marsSpin(clock)));
    }

    /** O ângulo {@code π − θ_M − β} da rotação de Marte em H. */
    private static double marsSpin(double clock) {
        double day = Planets.spec(Planets.MARS).dayLengthTicks();
        return Math.PI - Heliocentric.marsAngle(clock) - localSunAngle(clock, day);
    }

    /** Pose de Marte em E: a posição real relativa à Terra (Terra parada) e os eixos girados por {@code Ry(φ + θ_T)} sobre os de H. */
    private static Pose marsPose(double clock) {
        double[] i = new double[3];
        Heliocentric.marsInEarthSystem(clock, i);
        double[] e = new double[3];
        InertialFrame.toEarthFixed(clock, i[0], i[1], i[2], e);
        double total = InertialFrame.angle(clock) + Heliocentric.earthAngle(clock) + marsSpin(clock);
        return new Pose(e[0], e[1], e[2], InertialFrame.rotationY(total));
    }

    /** Pose do sol em E: a posição dele (a distância da órbita da Terra, na direção do sol do vanilla), eixos identidade. */
    private static Pose sunPose(double clock) {
        double[] s = new double[3];
        SunModel.directionInSpace(clock, s);
        return new Pose(s[0] * Heliocentric.EARTH_ORBIT_RADIUS, s[1] * Heliocentric.EARTH_ORBIT_RADIUS,
                s[2] * Heliocentric.EARTH_ORBIT_RADIUS, IDENTITY);
    }

    /**
     * Direção (unitária, nos eixos do espaço E) do centro do corpo ao sol. Terra e Lua usam a aproximação de sempre (a direção do
     * {@link SunModel}, igual para os dois: o sol no infinito); Marte usa a verdadeira, do centro dele ao sol.
     */
    public static void sunDirectionFrom(Body body, double clock, double[] out) {
        if (body.parallelSun()) {
            SunModel.directionInSpace(clock, out);
            return;
        }
        Pose sun = SUN.poseAt(clock);
        Pose at = body.poseAt(clock);
        double dx = sun.x() - at.x();
        double dy = sun.y() - at.y();
        double dz = sun.z() - at.z();
        double n = Math.sqrt(dx * dx + dy * dy + dz * dz);
        out[0] = dx / n;
        out[1] = dy / n;
        out[2] = dz / n;
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
