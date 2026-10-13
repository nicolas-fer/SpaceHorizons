package com.spacehorizons.planet;

import net.minecraft.resources.Identifier;

/**
 * Parâmetros imutáveis de um planeta. A circunferência é a grandeza primária (múltiplo de 16,
 * alinhada a chunks) para que o wrap em X seja exato; o raio é derivado dela.
 *
 * Mapeamento equirretangular: X = longitude, Z = latitude, ambos na mesma escala (1 bloco = 1/R rad).
 * surfaceGravity é a fração da gravidade da Terra (1.0 = normal).
 */
public record PlanetDefinition(
        Identifier id,
        long seed,
        int circumferenceBlocks,
        int seaLevel,
        double maxLatitudeDegrees,
        int terrainAmplitude,
        TerrainStyle style,
        double surfaceGravity) {

    public PlanetDefinition {
        if (circumferenceBlocks <= 0 || circumferenceBlocks % 16 != 0) {
            throw new IllegalArgumentException("circumferenceBlocks deve ser múltiplo positivo de 16: " + circumferenceBlocks);
        }
        if (maxLatitudeDegrees <= 0 || maxLatitudeDegrees >= 90) {
            throw new IllegalArgumentException("maxLatitudeDegrees fora de (0, 90): " + maxLatitudeDegrees);
        }
        if (style == null || surfaceGravity <= 0) {
            throw new IllegalArgumentException("style obrigatório e surfaceGravity > 0");
        }
    }

    /** Compatibilidade: planeta estilo Terra com gravidade normal. */
    public PlanetDefinition(Identifier id, long seed, int circumferenceBlocks, int seaLevel, double maxLatitudeDegrees,
            int terrainAmplitude) {
        this(id, seed, circumferenceBlocks, seaLevel, maxLatitudeDegrees, terrainAmplitude, TerrainStyle.EARTH, 1.0);
    }

    /** Oceano só existe em planetas estilo Terra; nos outros, seaLevel é só a referência de altitude. */
    public boolean hasOcean() {
        return style == TerrainStyle.EARTH;
    }

    public double radius() {
        return circumferenceBlocks / (2.0 * Math.PI);
    }

    /** Metade da circunferência: X válido fica em [-halfCircumference, +halfCircumference). */
    public int halfCircumference() {
        return circumferenceBlocks / 2;
    }

    /** |Z| máximo antes da calota polar intransponível. */
    public int maxLatitudeBlocks() {
        return (int) Math.floor(Math.toRadians(maxLatitudeDegrees) * radius());
    }
}
