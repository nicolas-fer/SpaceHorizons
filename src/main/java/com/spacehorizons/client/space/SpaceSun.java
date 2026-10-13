package com.spacehorizons.client.space;

import com.spacehorizons.client.atmosphere.AtmosphereModel;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceFrame;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Direção do sol única para todo o sistema, no referencial do espaço, para que dois lados de uma troca de dimensão
 * (e um corpo visto de outro) sejam iluminados igual. A referência é o relógio do Overworld: no spawn da Terra o sol
 * local é o do vanilla ((-sen a, cos a, 0) em leste/cima/norte); esse vetor, levado ao espaço, vale para todos os corpos.
 *
 * Na Terra, dentro da atmosfera, vale o sol local do vanilla (o céu e a luz do chão dependem dele); só o vácuo usa o
 * sol do espaço (mistura por {@code vacuum}). Corpos sem relógio próprio (a Lua) usam sempre o do espaço.
 */
public final class SpaceSun {
    private static final double DAY_TICKS = 24000.0;
    /** Ângulo do sol (rad, 0 = meio-dia) visto na última vez em um planeta com atmosfera, e o relógio nesse instante. */
    private static float referenceAngle = Float.NaN;
    private static long referenceClock;

    private SpaceSun() {
    }

    /** Ângulo do sol de referência (rad) agora; no meio-dia vale 0. */
    private static float referenceAngle(ClientLevel level, boolean hasOwnSky, float vanillaAngle) {
        long clock = level.getOverworldClockTime();
        if (hasOwnSky) {
            referenceAngle = vanillaAngle;
            referenceClock = clock;
            return vanillaAngle;
        }
        if (Float.isNaN(referenceAngle)) {
            return (float) (2.0 * Math.PI * ((clock - 6000L) / DAY_TICKS));
        }
        return referenceAngle + (float) (2.0 * Math.PI * ((clock - referenceClock) / DAY_TICKS));
    }

    /** Como {@link #local}, deduzindo atmosfera e vácuo do planeta e da altitude da câmera. */
    public static void localAt(ClientLevel level, PlanetDefinition planet, double camX, double camY, double camZ,
            float vanillaAngle, float[] out) {
        AtmosphereModel model = AtmosphereModel.of(planet);
        double vacuum = model.present() ? 1.0 - model.skyBrightness(camY - planet.seaLevel()) : 1.0;
        local(level, planet, camX, camZ, model.present(), vanillaAngle, vacuum, out);
    }

    // ---- estrelas ----

    /** Ângulo das estrelas (rad) do relógio da Terra na última vez no planeta com atmosfera, e o relógio nesse instante. */
    private static float starReference = Float.NaN;
    private static long starReferenceClock;
    private static float starAngleForSky;
    /** Rotação extra do campo de estrelas (eixos do Minecraft da câmera); null = nenhuma (vale o do vanilla). */
    private static Matrix4f starRotation;

    /** Ângulo de estrelas que o céu do mundo atual deve usar quando não tem relógio próprio coerente (a Lua). */
    public static float starAngle() {
        return starAngleForSky;
    }

    /** Rotação a aplicar ao campo de estrelas do vanilla neste frame, ou null. Só a thread de render lê e escreve. */
    public static Matrix4f starRotation() {
        return starRotation;
    }

    /**
     * Fixa as estrelas no espaço: o campo do vanilla (Ry(-90) Rx(ângulo), nos eixos do Minecraft do spawn da Terra) é levado
     * ao espaço e dali aos eixos da câmera. Na Terra mistura com o do vanilla pela falta de atmosfera ({@code vacuum});
     * na Lua vale sempre. Assim as mesmas estrelas aparecem no mesmo lugar dos dois lados de uma troca de dimensão.
     */
    public static void updateStars(ClientLevel level, PlanetDefinition planet, double camX, double camZ, float vanillaStarAngle,
            double vacuum) {
        boolean hasOwnSky = AtmosphereModel.of(planet).present();
        long clock = level.getOverworldClockTime();
        if (hasOwnSky) {
            starReference = vanillaStarAngle;
            starReferenceClock = clock;
            starAngleForSky = vanillaStarAngle;
        } else if (Float.isNaN(starReference)) {
            starAngleForSky = (float) (2.0 * Math.PI * ((clock - 6000L) / DAY_TICKS));
        } else {
            starAngleForSky = starReference + (float) (2.0 * Math.PI * ((clock - starReferenceClock) / DAY_TICKS));
        }
        SolarSystem.Body body = SolarSystem.bodyOf(planet).orElse(null);
        double t = hasOwnSky ? Math.max(0.0, Math.min(1.0, vacuum)) : 1.0;
        if (body == null || t <= 0.0) {
            starRotation = null;
            return;
        }
        Matrix3f m = new Matrix3f();
        double[] space = new double[3];
        double[] local = new double[3];
        for (int i = 0; i < 3; i++) {
            SpaceFrame.localToSpace(SolarSystem.EARTH, 0.0, 0.0, i == 0 ? 1.0 : 0.0, i == 1 ? 1.0 : 0.0, i == 2 ? 1.0 : 0.0, space);
            SpaceFrame.spaceToLocal(body, camX, camZ, space[0], space[1], space[2], local);
            m.setColumn(i, (float) local[0], (float) local[1], (float) local[2]);
        }
        Quaternionf q = new Quaternionf().setFromNormalized(m);
        if (t < 1.0) {
            q = new Quaternionf().nlerp(q, (float) t);
        }
        starRotation = new Matrix4f().rotation(q);
    }

    /** Esquece as referências e a rotação das estrelas (saída do mundo ou mundo sem planeta do mod). */
    public static void reset() {
        referenceAngle = Float.NaN;
        starReference = Float.NaN;
        starRotation = null;
    }

    /** Só apaga a rotação das estrelas, para que um céu que não é do mod fique como o vanilla. */
    public static void clearStarRotation() {
        starRotation = null;
    }

    /**
     * Direção do sol nos eixos do Minecraft da câmera (x leste, y cima, z norte), unitária.
     *
     * @param hasOwnSky {@code true} se o planeta atual tem o céu do vanilla coerente com o relógio do Overworld
     * @param vanillaAngle ângulo do sol do vanilla no planeta atual (só usado se {@code hasOwnSky})
     * @param vacuum 0 = dentro da atmosfera (sol local do vanilla), 1 = vácuo (sol do espaço)
     */
    public static void local(ClientLevel level, PlanetDefinition planet, double camX, double camZ, boolean hasOwnSky,
            float vanillaAngle, double vacuum, float[] out) {
        float ref = referenceAngle(level, hasOwnSky, vanillaAngle);
        float vx = -(float) Math.sin(vanillaAngle);
        float vy = (float) Math.cos(vanillaAngle);
        float vz = 0.0F;
        SolarSystem.Body body = SolarSystem.bodyOf(planet).orElse(null);
        if (body == null || (hasOwnSky && vacuum <= 0.0)) {
            out[0] = vx;
            out[1] = vy;
            out[2] = vz;
            return;
        }
        // sol do referencial do spawn da Terra, levado ao espaço e de lá aos eixos da câmera
        double[] space = new double[3];
        SpaceFrame.localToSpace(SolarSystem.EARTH, 0.0, 0.0, -Math.sin(ref), Math.cos(ref), 0.0, space);
        double[] local = new double[3];
        SpaceFrame.spaceToLocal(body, camX, camZ, space[0], space[1], space[2], local);
        double t = hasOwnSky ? Math.max(0.0, Math.min(1.0, vacuum)) : 1.0;
        double x = (hasOwnSky ? vx : 0.0) * (1.0 - t) + local[0] * t;
        double y = (hasOwnSky ? vy : 0.0) * (1.0 - t) + local[1] * t;
        double z = (hasOwnSky ? vz : 0.0) * (1.0 - t) + local[2] * t;
        double len = Math.sqrt(x * x + y * y + z * z);
        if (len < 1.0e-6) {
            out[0] = (float) local[0];
            out[1] = (float) local[1];
            out[2] = (float) local[2];
            return;
        }
        out[0] = (float) (x / len);
        out[1] = (float) (y / len);
        out[2] = (float) (z / len);
    }
}
