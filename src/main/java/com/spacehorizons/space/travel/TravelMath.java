package com.spacehorizons.space.travel;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceFrame;

/**
 * Contas puras da viagem (sem classes de render nem de rede): direção da câmera, velocidade de voo por altitude e
 * o ponto de referência de cada corpo. Tudo em double, no estilo do SpaceFrame.
 */
public final class TravelMath {
    /** Velocidade de voo padrão do criativo (Abilities.flyingSpeed). */
    public static final float BASE_FLYING_SPEED = 0.05F;
    /** Abaixo desta altitude (sobre o nível do mar) o voo é o normal do jogo. */
    public static final double BOOST_START_ALTITUDE = 800.0;
    /** Teto do multiplicador de velocidade (atingido a BOOST_MAX * BOOST_START_ALTITUDE blocos). */
    public static final double BOOST_MAX = 100.0;
    /** Passo de quantização do multiplicador: evita um pacote de habilidades a cada tick durante a subida. */
    public static final double BOOST_STEP = 0.25;
    /**
     * Acima desta altitude o servidor deixa de aplicar o "moved too quickly" do vanilla (passos grandes por tick são
     * esperados no voo rápido e no piloto automático).
     */
    public static final double MOVEMENT_CHECK_ALTITUDE = 128.0;

    private TravelMath() {
    }

    /**
     * Multiplicador da velocidade de voo pela altitude: 1 até {@link #BOOST_START_ALTITUDE}, depois proporcional à
     * altitude, com teto. Quantizado em {@link #BOOST_STEP}.
     */
    public static double flightSpeedMultiplier(double altitude) {
        if (!(altitude > BOOST_START_ALTITUDE)) {
            return 1.0;
        }
        double raw = Math.min(BOOST_MAX, altitude / BOOST_START_ALTITUDE);
        return Math.max(1.0, Math.round(raw / BOOST_STEP) * BOOST_STEP);
    }

    /** Vetor unitário de olhar (eixos do Minecraft: x=east, y=up, z=north) para yaw/pitch em graus. */
    public static void lookVector(float yRot, float xRot, double[] out) {
        double yaw = Math.toRadians(yRot);
        double pitch = Math.toRadians(xRot);
        double cosPitch = Math.cos(pitch);
        out[0] = -Math.sin(yaw) * cosPitch;
        out[1] = -Math.sin(pitch);
        out[2] = Math.cos(yaw) * cosPitch;
    }

    /** Yaw em graus (-180, 180] de um vetor com componentes horizontais (x, z). */
    public static float yawOf(double x, double z) {
        return (float) Math.toDegrees(Math.atan2(-x, z));
    }

    /** Pitch em graus de um vetor unitário com componente vertical y. */
    public static float pitchOf(double y) {
        return (float) Math.toDegrees(-Math.asin(Math.max(-1.0, Math.min(1.0, y))));
    }

    /** Leva x para [-C/2, C/2): o intervalo que o wrap em X do planeta considera válido. */
    public static double normalizeX(SolarSystem.Body body, double x) {
        double c = body.planet().circumferenceBlocks();
        double half = c / 2.0;
        double r = (x + half) % c;
        if (r < 0) {
            r += c;
        }
        return r - half;
    }

    /**
     * Ponto (x, z) da superfície do corpo que fica voltado para o corpo mais próximo (a face da Lua para a Terra, o
     * zênite do spawn na Terra), já em [-C/2, C/2) e no centro do bloco. out = {x, z}.
     */
    public static void facingPoint(SolarSystem.Body body, double[] out) {
        SolarSystem.Body nearest = null;
        double best = Double.MAX_VALUE;
        for (SolarSystem.Body other : SolarSystem.BODIES) {
            if (other == body) {
                continue;
            }
            double dx = other.x() - body.x();
            double dy = other.y() - body.y();
            double dz = other.z() - body.z();
            double d = dx * dx + dy * dy + dz * dz;
            if (d < best) {
                best = d;
                nearest = other;
            }
        }
        double[] local = new double[3];
        if (nearest != null) {
            SpaceFrame.fromSpace(body, nearest.x(), nearest.y(), nearest.z(), local);
        }
        out[0] = Math.floor(normalizeX(body, local[0])) + 0.5;
        out[1] = Math.floor(local[2]) + 0.5;
    }
}
