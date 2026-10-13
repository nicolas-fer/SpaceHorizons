package com.spacehorizons.planet.terrain;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetMath;
import com.spacehorizons.planet.PlanetTerrain;
import com.spacehorizons.planet.SurfaceType;

/**
 * Relevo de um planeta esférico. O ruído é amostrado na esfera unitária, não em (x, z) plano,
 * então o terreno é periódico em X por construção e não há costura nem distorção nos polos.
 *
 * Java puro e sem estado mutável: pode ser chamado de várias threads (o DH faz isso).
 * Custo: ~15 avaliações de ruído por coluna quando surfaceHeight e surfaceType são chamados juntos.
 */
public final class SphericalTerrain implements PlanetTerrain {
    private static final int CONTINENT_OCTAVES = 5;
    private static final int DETAIL_OCTAVES = 4;

    // Frequências na esfera unitária: a circunferência inteira é 2π, então ~1.6 dá uns 3 continentes
    private static final double CONTINENT_FREQ = 1.6;
    private static final double DETAIL_FREQ = 9.0;
    private static final double RIDGE_FREQ = 5.0;
    private static final double CLIMATE_FREQ = 2.5;

    /** Abaixo disso a coluna é oceano. */
    private static final double SHORE = -0.02;

    private final PlanetDefinition planet;
    private final GradientNoise3D continent;
    private final GradientNoise3D detail;
    private final GradientNoise3D ridge;
    private final GradientNoise3D climate;
    private final GradientNoise3D moisture;
    private final int maxLatBlocks;
    private final double amplitude;

    public SphericalTerrain(PlanetDefinition planet) {
        this.planet = planet;
        long seed = planet.seed();
        this.continent = new GradientNoise3D(seed);
        this.detail = new GradientNoise3D(seed ^ 0x1111_1111L);
        this.ridge = new GradientNoise3D(seed ^ 0x2222_2222L);
        this.climate = new GradientNoise3D(seed ^ 0x3333_3333L);
        this.moisture = new GradientNoise3D(seed ^ 0x4444_4444L);
        this.maxLatBlocks = planet.maxLatitudeBlocks();
        this.amplitude = planet.terrainAmplitude();
    }

    @Override
    public PlanetDefinition planet() {
        return planet;
    }

    @Override
    public int surfaceHeight(int blockX, int blockZ) {
        return (int) Math.round(sample(blockX, blockZ).height);
    }

    @Override
    public SurfaceType surfaceType(int blockX, int blockZ) {
        return sample(blockX, blockZ).type;
    }

    /** Uma amostra completa; chunks chamam altura e tipo juntos, então isto evita repetir ruído. */
    private Sample sample(int blockX, int blockZ) {
        // wrap antes de tudo: x e x + C caem no mesmo ponto exato, sem erro de ponto flutuante
        long wx = PlanetMath.wrapX(planet, blockX);
        double[] p = new double[3];
        PlanetMath.unitSphere(planet, wx, blockZ, p);
        double px = p[0];
        double py = p[1];
        double pz = p[2];

        double c = continent.fbm(px * CONTINENT_FREQ, py * CONTINENT_FREQ, pz * CONTINENT_FREQ, CONTINENT_OCTAVES);
        double sea = planet.seaLevel();

        // Altura base: oceano desce, terra sobe suavemente a partir da costa
        double height;
        if (c < SHORE) {
            height = sea + (c - SHORE) * amplitude * 0.45;
        } else {
            double land = smoothstep(SHORE, 0.10, c);
            double fine = detail.fbm(px * DETAIL_FREQ, py * DETAIL_FREQ, pz * DETAIL_FREQ, DETAIL_OCTAVES);
            // cordilheiras: 1 - |ruído| dá vales agudos e cristas; só aparecem onde o continente é alto
            double ridged = 1.0 - Math.abs(ridge.noise(px * RIDGE_FREQ, py * RIDGE_FREQ, pz * RIDGE_FREQ));
            double mountain = ridged * ridged * smoothstep(0.30, 0.65, c) * amplitude * 0.6;
            height = sea + 2 + (c - SHORE) * amplitude * 0.5 + land * (fine * amplitude * 0.08 + mountain);
        }

        // Parede polar: platô de gelo alto que fecha a calota antes do limite de |Z|
        double lat = Math.min(1.0, Math.abs((double) blockZ) / maxLatBlocks);
        double wall = smoothstep(0.90, 1.0, lat);
        height += wall * amplitude * 1.5;

        // mínimo do mundo (-64) e máximo (319) com folga para não furar o bedrock nem o limite
        height = Math.max(-60, Math.min(300, height));

        SurfaceType type = classify(c, height, lat, px, py, pz);
        return new Sample(height, type);
    }

    private SurfaceType classify(double c, double height, double lat, double px, double py, double pz) {
        if (lat >= 0.93) {
            return SurfaceType.POLAR_ICE;
        }
        double sea = planet.seaLevel();
        if (c < SHORE) {
            return SurfaceType.OCEAN;
        }
        if (height <= sea + 2) {
            return SurfaceType.BEACH;
        }
        if (height > sea + amplitude * 0.7) {
            return SurfaceType.MOUNTAIN;
        }
        // Temperatura cai com a latitude e com a altitude
        double temp = 1.0 - lat * 1.1
                + 0.15 * climate.fbm(px * CLIMATE_FREQ, py * CLIMATE_FREQ, pz * CLIMATE_FREQ, 3)
                - (height - sea) / (amplitude * 2.0);
        if (temp < 0.25 || lat > 0.75) {
            return SurfaceType.SNOW;
        }
        double wet = moisture.fbm(px * CLIMATE_FREQ, py * CLIMATE_FREQ, pz * CLIMATE_FREQ, 3);
        if (temp > 0.6 && wet < -0.05) {
            return SurfaceType.DESERT;
        }
        if (wet > 0.10) {
            return SurfaceType.FOREST;
        }
        return SurfaceType.PLAINS;
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = Math.max(0.0, Math.min(1.0, (x - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }

    private record Sample(double height, SurfaceType type) {
    }
}
