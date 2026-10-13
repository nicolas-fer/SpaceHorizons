package com.spacehorizons.client.curvature;

import java.util.Optional;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Valores de curvatura do frame atual, lidos pelos mixins de render. Fora de um planeta o raio é 0
 * e o shader devolve a posição intacta.
 */
public final class CurvatureState {
    /**
     * Tamanho do TerrainUniform com os dois floats extras (raio e altitude) depois do mat4 + ivec2.
     * O vanilla usa 72 bytes; 80 é o próximo múltiplo de 16 e cobre os dois floats.
     */
    public static final int TERRAIN_UBO_SIZE = 80;

    /**
     * Tamanho do CloudInfo (vec4 + vec3 + vec3 = 44 bytes) com os dois floats extras nos offsets 44 e 48.
     * 64 cobre os 52 bytes usados e mantém o alinhamento em 16.
     */
    public static final int CLOUD_UBO_SIZE = 64;

    /**
     * Tamanho do DynamicTransforms (mat4 + mat4 + vec4 + vec3 = 156 bytes) com os dois floats extras nos
     * offsets 156 e 160 (std140 empacota o primeiro float nos 4 bytes sobrando do vec3). 176 é o próximo
     * múltiplo de 16 acima dos 164 usados.
     */
    public static final int TRANSFORM_UBO_SIZE = 176;

    /**
     * Alcance mínimo das nuvens vanilla num planeta, em chunks (128 = 2.048 blocos, o máximo da opção do vanilla
     * e o valor padrão do atributo cloud_fog_end_distance). Sem as nuvens planas do DH, é o que cobre o céu.
     */
    public static final int PLANET_CLOUD_RANGE_CHUNKS = 128;

    /** Raio e altitude da câmera sobre o nível do mar. Raio 0 desliga a curvatura no shader. */
    public record Frame(double radius, double cameraAltitude) {
        public static final Frame OFF = new Frame(0.0, 0.0);
    }

    private CurvatureState() {
    }

    /** true quando o level atual é um planeta (a curvatura está ligada). */
    public static boolean inPlanet() {
        return current().radius() > 0.0;
    }

    /** Alcance das nuvens em chunks: o da opção do usuário, ou o mínimo do planeta se estiver num. */
    public static int cloudRangeChunks(int userRange) {
        return inPlanet() ? Math.max(userRange, PLANET_CLOUD_RANGE_CHUNKS) : userRange;
    }

    public static Frame current() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return Frame.OFF;
        }
        Optional<PlanetDefinition> planet = Planets.forLevel(level);
        if (planet.isEmpty()) {
            return Frame.OFF;
        }
        double cameraY = minecraft.gameRenderer.mainCamera().position().y;
        return new Frame(planet.get().radius(), cameraY - planet.get().seaLevel());
    }
}
