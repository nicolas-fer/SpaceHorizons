package com.spacehorizons.client.space;

import java.util.Optional;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceFrame;
import com.spacehorizons.space.SunModel;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.ClockInstance;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.Entity;

/**
 * Ponte do cliente para o {@link SunModel}: o sol único do sistema, nos eixos do Minecraft de quem olha. O tempo é o
 * relógio do Overworld ({@link #clock}), o mesmo que move as fases e a órbita da Lua (SolarSystem). Tudo o que
 * mostra o sol ou depende dele (disco, brilho do nascer/pôr, estrelas, céu, luz do chão, malha orbital, corpos vistos
 * de fora, limbo da atmosfera) parte da mesma direção, a do SunModel no ponto da câmera com o relógio do Overworld.
 *
 * <ul>
 *   <li>{@link #localAt}: direção do sol (unitária, x leste, y cima, z norte) num ponto, para os renderers;</li>
 *   <li>{@link #update}: uma vez por frame (extração do estado do céu), guarda o ângulo e o giro do disco do vanilla e a
 *       rotação das estrelas, que {@code SkyRendererMixin} aplica no desenho do céu;</li>
 *   <li>a cor do céu e a luz do chão vêm dos atributos de ambiente do vanilla, avaliados no tick efetivo do ponto
 *       (ver {@code AttributeTrackSamplerMixin} e {@link #effectiveTicks}).</li>
 * </ul>
 */
public final class SpaceSun {
    /** Ângulo do sol do vanilla (rad, 0 = meio-dia) com a mesma altura do sol local; vai para {@code SkyRenderState.sunAngle}. */
    private static float skyAngle;
    /** Giro em torno da vertical (rad) do disco e do brilho do nascer/pôr do sol, para o azimute real. */
    private static float sunTilt;
    /** Ângulo do campo de estrelas do vanilla no spawn da Terra (rad); vai para {@code SkyRenderState.starAngle}. */
    private static float starAngle;
    /** Rotação do campo de estrelas (eixos do Minecraft da câmera); null = nenhuma (vale o vanilla). */
    private static Matrix4f starRotation;
    /** A câmera está na dimensão de espaço aberto (ligado por SpaceSkyClient a cada frame; lido por SkyRendererMixin). */
    private static boolean inSpace;

    /** A câmera está no céu de um planeta do mod (Terra ou Lua; ligado por BodyClient a cada frame). */
    private static boolean onPlanet;

    private SpaceSun() {
    }

    /** O sol deste céu é o disco redondo do {@link SunDisc}? Sim no espaço, no estaleiro e nos planetas do mod; fora deles vale o vanilla. */
    public static boolean roundSun() {
        return inSpace || onPlanet;
    }

    static void setOnPlanet(boolean value) {
        onPlanet = value;
    }

    /** Relógio do Overworld agora, em ticks com a parte fracionária (clock + frame), para movimentos suaves entre ticks. */
    public static double clock(ClientLevel level) {
        Optional<Holder.Reference<WorldClock>> holder = level.registryAccess().get(WorldClocks.OVERWORLD);
        if (holder.isEmpty()) {
            return 0.0;
        }
        ClockInstance instance = level.clockManager().getInstance(holder.get());
        double fraction = instance.partialTick();
        if (!instance.isPaused()) {
            fraction += Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false) * instance.rate();
        }
        return instance.totalTicks() + fraction;
    }

    /**
     * Direção do sol nos eixos do Minecraft do ponto (camX, camZ) do planeta, unitária. Planeta fora do SolarSystem (Marte):
     * o corpo destacado dele ({@link SolarSystem#skyBodyOf}), que só gira com o dia do planeta.
     */
    public static void localAt(ClientLevel level, PlanetDefinition planet, double camX, double camZ, float[] out) {
        double[] sun = new double[3];
        if (!local(level, planet, camX, camZ, sun)) {
            out[0] = 0.0F;
            out[1] = 1.0F;
            out[2] = 0.0F;
            return;
        }
        out[0] = (float) sun[0];
        out[1] = (float) sun[1];
        out[2] = (float) sun[2];
    }

    private static boolean local(ClientLevel level, PlanetDefinition planet, double x, double z, double[] out) {
        SunModel.localDirection(SolarSystem.skyBodyOf(planet), x, z, clock(level), out);
        return true;
    }

    /**
     * Tick do relógio do Overworld que os atributos de ambiente (timelines) devem usar no ponto de vista atual: o horário
     * efetivo do SunModel, que dá ao vanilla o céu e a luz do chão do sol real do lugar. Só o relógio do Overworld de
     * um nível do mod muda; qualquer outro relógio ou nível passa direto.
     */
    public static long effectiveTicks(ClockInstance instance, long ticks) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Entity viewer = minecraft.getCameraEntity();
        if (level == null || viewer == null) {
            return ticks;
        }
        Optional<Holder.Reference<WorldClock>> overworld = level.registryAccess().get(WorldClocks.OVERWORLD);
        if (overworld.isEmpty() || level.clockManager().getInstance(overworld.get()) != instance) {
            return ticks;
        }
        Optional<PlanetDefinition> planet = Planets.forLevel(level);
        if (planet.isEmpty()) {
            return ticks;
        }
        return Math.round(SunModel.effectiveClock(SolarSystem.skyBodyOf(planet.get()), viewer.getX(), viewer.getZ(), ticks));
    }

    /**
     * Calcula o estado do céu deste frame para o ponto (x, z) do planeta: ângulo e giro do sol e rotação das estrelas.
     * As estrelas ficam fixas no espaço: o campo do vanilla (Ry(-90) Rx(ângulo), nos eixos do Minecraft do spawn da Terra)
     * é levado ao espaço e dali aos eixos locais do ponto, em qualquer altitude e planeta; no spawn da Terra a rotação é
     * a identidade. Assim as mesmas estrelas aparecem no mesmo lugar dos dois lados de uma troca de dimensão. Na Lua, que
     * gira com a órbita, a rotação inclui a orientação dela no instante (o céu estrelado gira devagar sobre o chão lunar).
     */
    public static void update(ClientLevel level, PlanetDefinition planet, double x, double z) {
        SolarSystem.Body body = SolarSystem.skyBodyOf(planet);
        double clock = clock(level);
        double[] sun = new double[3];
        SunModel.localDirection(body, x, z, clock, sun);
        skyAngle = (float) SunModel.vanillaSkyAngle(sun);
        sunTilt = (float) SunModel.azimuthTilt(sun);
        starAngle = (float) SunModel.angle(clock);

        Matrix3f m = new Matrix3f();
        double[] space = new double[3];
        double[] local = new double[3];
        for (int i = 0; i < 3; i++) {
            SpaceFrame.localToSpace(SolarSystem.EARTH, clock, 0.0, 0.0, i == 0 ? 1.0 : 0.0, i == 1 ? 1.0 : 0.0, i == 2 ? 1.0 : 0.0, space);
            SpaceFrame.spaceToLocal(body, clock, x, z, space[0], space[1], space[2], local);
            m.setColumn(i, (float) local[0], (float) local[1], (float) local[2]);
        }
        starRotation = new Matrix4f().rotation(new Quaternionf().setFromNormalized(m));
    }

    /**
     * Céu do espaço visto por um observador de atitude qualquer ({@link SkyObserver#forShip}): o sol do vanilla recebe a altura
     * e o giro em torno da vertical que levam o disco à direção local do SunModel (a mesma da luz das esferas), e o campo de
     * estrelas recebe a rotação COMPLETA do referencial do spawn ({@code SkyObserver#vanillaStarsToLocal}, quaternion, não só um
     * ângulo), aplicada por {@code SkyRendererMixin}. Com a atitude identidade sai o mesmo de {@link #update} no spawn da Terra.
     */
    public static void updateForObserver(SkyObserver observer) {
        double[] sun = new double[3];
        observer.sun(sun);
        skyAngle = (float) SunModel.vanillaSkyAngle(sun);
        sunTilt = (float) SunModel.azimuthTilt(sun);
        starAngle = (float) SunModel.angle(observer.clock());
        double[] r = new double[9];
        observer.vanillaStarsToLocal(r);
        Matrix3f m = new Matrix3f();
        for (int i = 0; i < 3; i++) {
            m.setColumn(i, (float) r[i], (float) r[3 + i], (float) r[6 + i]);   // coluna i da matriz = (linhas 0..2, coluna i)
        }
        starRotation = new Matrix4f().rotation(new Quaternionf().setFromNormalized(m));
    }

    /** Ângulo do sol do vanilla (rad) deste frame, para o disco e o brilho do nascer/pôr do sol. */
    public static float skyAngle() {
        return skyAngle;
    }

    /** Giro (rad) em torno da vertical a aplicar ao disco do sol e ao brilho do nascer/pôr deste frame; 0 fora do mod. */
    public static float sunTilt() {
        return sunTilt;
    }

    /** Ângulo do campo de estrelas do vanilla (rad) deste frame. */
    public static float starAngle() {
        return starAngle;
    }

    /** Rotação a aplicar ao campo de estrelas do vanilla neste frame, ou null. Só a thread de render lê e escreve. */
    public static Matrix4f starRotation() {
        return starRotation;
    }

    /**
     * A câmera está no espaço aberto? Nele a lua do vanilla (um quadrado de textura) não pode aparecer: a Lua de verdade é a
     * esfera do BodyRenderer, e sem chão para escondê-la o quadrado ficaria visível olhando para baixo.
     */
    public static boolean inSpace() {
        return inSpace;
    }

    static void setInSpace(boolean value) {
        inSpace = value;
    }

    /** Esquece o estado do céu (saída do mundo). */
    public static void reset() {
        clearView();
        inSpace = false;
    }

    /** Apaga giro e rotação, para que um céu que não é do mod fique como o vanilla. */
    public static void clearView() {
        sunTilt = 0.0F;
        starRotation = null;
    }
}
