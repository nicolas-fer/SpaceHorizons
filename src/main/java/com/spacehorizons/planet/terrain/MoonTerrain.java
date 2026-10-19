package com.spacehorizons.planet.terrain;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetMath;
import com.spacehorizons.planet.PlanetTerrain;
import com.spacehorizons.planet.SurfaceType;

/**
 * Relevo da Lua: planícies de regolito, mares baixos e escuros, terras altas e crateras.
 *
 * Tudo é função do ponto na esfera unitária (como no SphericalTerrain), então é periódico em X por
 * construção, sem costura. As crateras vêm de uma grade 3D de células no espaço da esfera, em quatro
 * escalas: cada célula tem no máximo uma cratera, com centro, raio e presença sorteados por hash da
 * (seed, escala, célula). O centro fica em 3D (não projetado na superfície), então a distância até ele
 * dá círculos de raios variados quando o centro está um pouco fora da casca: bacias rasas e profundas
 * aparecem naturalmente, sem nenhuma costura entre células.
 *
 * Java puro, sem estado mutável: seguro entre threads. Custo por coluna: ~10 avaliações de ruído e
 * 4 x 27 hashes de célula, quando surfaceHeight e surfaceType são chamados juntos.
 *
 * Invariante usada pela malha orbital: a altura nunca fica abaixo de seaLevel + 4, então nada é lido como
 * "água" nela (a Lua não tem oceano, mas a malha compara a altura com seaLevel).
 */
public final class MoonTerrain implements PlanetTerrain {
    /** Frequência das células de cratera na esfera unitária (célula = 1/F de raio): 8.692, 2.897, 966 e 322 blocos na Lua (R ≈ 26.076). */
    private static final double[] CRATER_FREQ = {3.0, 9.0, 27.0, 81.0};
    /** Chance de uma célula ter cratera. */
    private static final double[] CRATER_PROB = {0.45, 0.60, 0.70, 0.70};
    /** Profundidade e altura da borda, em fração do raio da cratera. */
    private static final double[] CRATER_DEPTH = {0.09, 0.11, 0.13, 0.16};
    private static final double[] CRATER_RIM = {0.05, 0.04, 0.035, 0.03};
    /** Raio mínimo (blocos) de uma cratera que pinta CRATER_FLOOR; as pequenas só mexem na altura. */
    private static final double FLOOR_MIN_RADIUS = 40.0;
    /** t = distância/raio até o qual a cratera afeta o relevo (a borda é um pico em t = 1). */
    private static final double INFLUENCE = 2.0;
    /** Os deltas somados de crateras sobrepostas ficam nesta faixa (blocos). */
    private static final double MAX_CRATER_DEPTH = 38.0;
    private static final double MAX_CRATER_RISE = 30.0;

    private static final double MARE_FREQ = 1.2;
    private static final double HIGHLAND_FREQ = 1.8;
    private static final double DETAIL_FREQ = 14.0;

    /** Altura de referência das planícies acima de seaLevel. */
    private static final double BASE_ABOVE_SEA = 62.0;
    /** Piso absoluto acima de seaLevel (ver invariante acima). */
    private static final int MIN_ABOVE_SEA = 4;
    private static final int MAX_HEIGHT = 300;

    private final PlanetDefinition planet;
    private final GradientNoise3D mareNoise;
    private final GradientNoise3D highlandNoise;
    private final GradientNoise3D detail;
    private final long craterSeed;
    private final double radius;
    private final int maxLatBlocks;
    private final double amplitude;

    public MoonTerrain(PlanetDefinition planet) {
        this.planet = planet;
        long seed = planet.seed();
        this.mareNoise = new GradientNoise3D(seed);
        this.highlandNoise = new GradientNoise3D(seed ^ 0x5151_5151L);
        this.detail = new GradientNoise3D(seed ^ 0x7272_7272L);
        this.craterSeed = seed ^ 0x0C4A_7E45_1234_5678L;
        this.radius = planet.radius();
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

    /** Só a contribuição das crateras à altura (blocos, positivo = borda), para testes. */
    double craterDelta(int blockX, int blockZ) {
        double[] p = unit(blockX, blockZ);
        double[] out = new double[2];
        craters(p[0], p[1], p[2], out);
        return out[0];
    }

    private double[] unit(int blockX, int blockZ) {
        // wrap antes de tudo: x e x + C caem no mesmo ponto exato, sem erro de ponto flutuante
        long wx = PlanetMath.wrapX(planet, blockX);
        double[] p = new double[3];
        PlanetMath.unitSphere(planet, wx, blockZ, p);
        return p;
    }

    private Sample sample(int blockX, int blockZ) {
        double[] p = unit(blockX, blockZ);
        double px = p[0];
        double py = p[1];
        double pz = p[2];

        double low = mareNoise.fbm(px * MARE_FREQ, py * MARE_FREQ, pz * MARE_FREQ, 3);
        double mare = smoothstep(0.06, 0.26, -low);
        double high = highlandNoise.fbm(px * HIGHLAND_FREQ, py * HIGHLAND_FREQ, pz * HIGHLAND_FREQ, 3);
        double highland = smoothstep(0.05, 0.28, high) * (1.0 - mare);

        double fine = detail.fbm(px * DETAIL_FREQ, py * DETAIL_FREQ, pz * DETAIL_FREQ, 4);
        // mares são quase lisos; as terras altas são mais ásperas
        double roughness = amplitude * 0.10 * (1.0 - 0.8 * mare) * (1.0 + 0.5 * highland);

        double[] crater = new double[2];
        craters(px, py, pz, crater);

        double height = planet.seaLevel() + BASE_ABOVE_SEA
                + highland * amplitude * 0.45
                - mare * amplitude * 0.30
                + fine * roughness
                + crater[0];

        // Parede polar: platô alto de terra alta que fecha a calota antes do limite de |Z|
        double lat = Math.min(1.0, Math.abs((double) blockZ) / maxLatBlocks);
        height += smoothstep(0.90, 1.0, lat) * amplitude * 2.0;

        height = Math.max(planet.seaLevel() + MIN_ABOVE_SEA, Math.min(MAX_HEIGHT, height));

        SurfaceType type;
        if (lat >= 0.93) {
            type = SurfaceType.HIGHLAND;
        } else if (crater[1] > 0) {
            type = SurfaceType.CRATER_FLOOR;
        } else if (mare > 0.5) {
            type = SurfaceType.MARE;
        } else if (highland > 0.5) {
            type = SurfaceType.HIGHLAND;
        } else {
            type = SurfaceType.REGOLITH;
        }
        return new Sample(height, type);
    }

    /**
     * Soma das crateras no ponto da esfera unitária. out[0] = delta de altura (blocos);
     * out[1] > 0 se o ponto está no fundo de uma cratera grande.
     */
    private void craters(double px, double py, double pz, double[] out) {
        double delta = 0;
        boolean floor = false;
        for (int s = 0; s < CRATER_FREQ.length; s++) {
            double f = CRATER_FREQ[s];
            double x = px * f;
            double y = py * f;
            double z = pz * f;
            long cx = (long) Math.floor(x);
            long cy = (long) Math.floor(y);
            long cz = (long) Math.floor(z);
            for (long i = cx - 1; i <= cx + 1; i++) {
                for (long j = cy - 1; j <= cy + 1; j++) {
                    for (long k = cz - 1; k <= cz + 1; k++) {
                        long h = cellHash(s, i, j, k);
                        if (unit(h, 0) >= CRATER_PROB[s]) {
                            continue;
                        }
                        // raio em células: no máximo 0.5, então a influência (2 raios) cabe nos vizinhos 3x3x3
                        double rc = 0.20 + 0.30 * unit(h, 12);
                        double dx = x - (i + 0.1 + 0.8 * unit(h, 24));
                        double dy = y - (j + 0.1 + 0.8 * unit(h, 36));
                        double dz = z - (k + 0.1 + 0.8 * unit(h, 48));
                        double t = Math.sqrt(dx * dx + dy * dy + dz * dz) / rc;
                        if (t >= INFLUENCE) {
                            continue;
                        }
                        double rBlocks = rc / f * radius;
                        // bacia: fundo plano que sobe suavemente até a borda; borda: pico gaussiano em t = 1
                        double bowl = -CRATER_DEPTH[s] * rBlocks * (1.0 - smoothstep(0.5, 0.95, t));
                        double rim = CRATER_RIM[s] * rBlocks * Math.exp(-sq((t - 1.0) / 0.22));
                        delta += bowl + rim;
                        if (t < 0.55 && rBlocks >= FLOOR_MIN_RADIUS) {
                            floor = true;
                        }
                    }
                }
            }
        }
        out[0] = Math.max(-MAX_CRATER_DEPTH, Math.min(MAX_CRATER_RISE, delta));
        out[1] = floor ? 1 : 0;
    }

    private long cellHash(int scale, long i, long j, long k) {
        long h = mix(craterSeed + scale * 0x632B_E59B_D9B4_E019L);
        h = mix(h ^ (i * 0x9E37_79B9_7F4A_7C15L));
        h = mix(h ^ (j * 0xC2B2_AE3D_27D4_EB4FL));
        return mix(h ^ (k * 0x1656_67B1_9E37_79F9L));
    }

    /** 12 bits do hash (campos disjuntos em 0, 12, 24, 36 e 48), como fração em [0, 1). */
    private static double unit(long hash, int shift) {
        return ((hash >>> shift) & 0xFFF) / 4096.0;
    }

    private static long mix(long z) {
        z += 0x9E37_79B9_7F4A_7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58_476D_1CE4_E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D0_49BB_1331_11EBL;
        return z ^ (z >>> 31);
    }

    private static double sq(double v) {
        return v * v;
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = Math.max(0.0, Math.min(1.0, (x - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }

    private record Sample(double height, SurfaceType type) {
    }
}
