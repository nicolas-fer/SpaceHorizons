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

    /** Raio e altitude da câmera sobre o nível do mar. Raio 0 desliga a curvatura no shader. */
    public record Frame(double radius, double cameraAltitude) {
        public static final Frame OFF = new Frame(0.0, 0.0);
    }

    private CurvatureState() {
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
