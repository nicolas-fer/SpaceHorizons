package com.spacehorizons.planet.terrain;

import java.util.List;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetMath;
import com.spacehorizons.planet.PlanetTerrain;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.SurfaceColors;
import com.spacehorizons.planet.SurfaceType;
import com.spacehorizons.planet.spec.SurfaceSpec;
import com.spacehorizons.planet.spec.TerrainParameters;
import com.spacehorizons.planet.spec.TerrainParameters.CraterScale;
import com.spacehorizons.planet.spec.TerrainParameters.Relief;

/**
 * Relevo de um planeta descrito por parâmetros ({@code terrain.relief} do JSON do planeta): planícies de regolito,
 * planícies baixas e escuras, terras altas, crateras em várias escalas, cordilheiras e vulcões em escudo opcionais e uma
 * calota polar. É a generalização do relevo da Lua: com os parâmetros dela o resultado é idêntico, bloco a bloco, ao do
 * MoonTerrain original (o teste de referência guarda a soma de controle).
 *
 * Tudo é função do ponto na esfera unitária, então é periódico em X por construção, sem costura. As crateras vêm de uma
 * grade 3D de células no espaço da esfera, uma grade por escala: cada célula tem no máximo uma cratera, com centro, raio e
 * presença sorteados por hash da (semente, escala, célula). O centro fica em 3D (não projetado na superfície), então a
 * distância até ele dá círculos de raios variados quando o centro está um pouco fora da casca: bacias rasas e profundas
 * aparecem naturalmente, sem costura entre células. Os vulcões usam o mesmo esquema numa grade própria.
 *
 * Java puro, sem estado mutável: seguro entre threads. Custo por coluna (Lua): ~10 avaliações de ruído e 4 x 27 hashes de
 * célula; cordilheiras somam 2 ruídos e vulcões 27 hashes.
 *
 * Invariante usada pela malha orbital: a altura nunca fica abaixo de seaLevel + minAboveSea (> 0), então nada é lido como
 * "água" nela (estes planetas não têm oceano, mas a malha compara a altura com seaLevel).
 */
public final class ParametricTerrain implements PlanetTerrain {
    /** Sementes dos ruídos: as quatro primeiras são as da Lua original (não mudar, ou a Lua muda). */
    private static final long HIGHLAND_SALT = 0x5151_5151L;
    private static final long DETAIL_SALT = 0x7272_7272L;
    private static final long CRATER_SALT = 0x0C4A_7E45_1234_5678L;
    private static final long RIDGE_SALT = 0x3939_3939L;
    private static final long RIDGE_MASK_SALT = 0x4848_4848L;
    private static final long VOLCANO_SALT = 0x7A1C_A40E_5EED_0001L;
    /** Índice de "escala" do hash dos vulcões, fora das escalas de cratera. */
    private static final int VOLCANO_SCALE = 31;

    private final PlanetDefinition planet;
    private final Relief relief;
    private final SurfaceSpec surface;
    private final GradientNoise3D mareNoise;
    private final GradientNoise3D highlandNoise;
    private final GradientNoise3D detail;
    private final GradientNoise3D ridgeNoise;
    private final GradientNoise3D ridgeMask;
    private final long craterSeed;
    private final long volcanoSeed;
    private final double radius;
    private final int maxLatBlocks;
    private final double amplitude;
    private final CraterScale[] craterScales;

    /** Terreno do planeta do catálogo (os parâmetros saem do JSON do planeta de mesmo id). */
    public ParametricTerrain(PlanetDefinition planet) {
        this(planet, Planets.spec(planet).terrain(), Planets.spec(planet).surface());
    }

    public ParametricTerrain(PlanetDefinition planet, TerrainParameters terrain, SurfaceSpec surface) {
        this.planet = planet;
        this.relief = terrain.relief().orElseThrow(() -> new IllegalArgumentException(
                "o planeta " + planet.id() + " não tem terrain.relief"));
        this.surface = surface;
        long seed = planet.seed();
        this.mareNoise = new GradientNoise3D(seed);
        this.highlandNoise = new GradientNoise3D(seed ^ HIGHLAND_SALT);
        this.detail = new GradientNoise3D(seed ^ DETAIL_SALT);
        this.ridgeNoise = new GradientNoise3D(seed ^ RIDGE_SALT);
        this.ridgeMask = new GradientNoise3D(seed ^ RIDGE_MASK_SALT);
        this.craterSeed = seed ^ CRATER_SALT;
        this.volcanoSeed = seed ^ VOLCANO_SALT;
        this.radius = planet.radius();
        this.maxLatBlocks = planet.maxLatitudeBlocks();
        this.amplitude = planet.terrainAmplitude();
        List<CraterScale> scales = relief.craters().scales();
        this.craterScales = scales.toArray(new CraterScale[0]);
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

    /** Cor de longe: a declarada na paleta do planeta, senão a padrão do papel. */
    @Override
    public int surfaceColor(int blockX, int blockZ) {
        SurfaceType type = surfaceType(blockX, blockZ);
        SurfaceSpec.Layer layer = surface.types().get(type);
        return layer != null && layer.color().isPresent() ? layer.color().get() : SurfaceColors.of(type);
    }

    /** Só a contribuição das crateras à altura (blocos, positivo = borda), para testes. */
    double craterDelta(int blockX, int blockZ) {
        double[] p = unit(blockX, blockZ);
        double[] out = new double[2];
        craters(p[0], p[1], p[2], out);
        return out[0];
    }

    /** Só a contribuição dos vulcões à altura (blocos), para testes. */
    double volcanoDelta(int blockX, int blockZ) {
        double[] p = unit(blockX, blockZ);
        double[] out = new double[2];
        volcanoes(p[0], p[1], p[2], out);
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

        TerrainParameters.NoiseBand lowlands = relief.lowlands();
        TerrainParameters.NoiseBand highlands = relief.highlands();
        TerrainParameters.Detail d = relief.detail();
        double lf = lowlands.frequency();
        double low = mareNoise.fbm(px * lf, py * lf, pz * lf, lowlands.octaves());
        double mare = smoothstep(lowlands.edge0(), lowlands.edge1(), -low);
        double hf = highlands.frequency();
        double high = highlandNoise.fbm(px * hf, py * hf, pz * hf, highlands.octaves());
        double highland = smoothstep(highlands.edge0(), highlands.edge1(), high) * (1.0 - mare);

        double df = d.frequency();
        double fine = detail.fbm(px * df, py * df, pz * df, d.octaves());
        // planícies baixas são quase lisas; as terras altas são mais ásperas
        double roughness = amplitude * d.roughness() * (1.0 - d.lowlandSmoothing() * mare) * (1.0 + d.highlandRoughening() * highland);

        double[] crater = new double[2];
        craters(px, py, pz, crater);

        double height = planet.seaLevel() + relief.baseHeight()
                + highland * amplitude * highlands.strength()
                - mare * amplitude * lowlands.strength()
                + fine * roughness
                + crater[0];

        double mountain = 0.0;
        if (relief.mountains().isPresent()) {
            mountain = mountains(relief.mountains().get(), px, py, pz, mare);
            height += mountain;
        }
        double[] volcano = null;
        if (relief.volcanoes().isPresent()) {
            volcano = new double[2];
            volcanoes(px, py, pz, volcano);
            height += volcano[0];
        }

        // Parede polar: platô alto que fecha a calota antes do limite de |Z|
        TerrainParameters.Polar polar = relief.polar();
        double lat = Math.min(1.0, Math.abs((double) blockZ) / maxLatBlocks);
        height += smoothstep(polar.wallStart(), 1.0, lat) * amplitude * polar.wallHeight();

        height = Math.max(planet.seaLevel() + relief.minAboveSea(), Math.min(relief.maxHeight(), height));

        SurfaceType type;
        if (lat >= polar.capStart()) {
            type = polar.capType();
        } else if (crater[1] > 0) {
            type = SurfaceType.CRATER_FLOOR;
        } else if (volcano != null && volcano[1] > 0) {
            type = SurfaceType.VOLCANIC;
        } else if (mare > 0.5) {
            type = SurfaceType.MARE;
        } else if (highland > 0.5 || (relief.mountains().isPresent()
                && mountain > amplitude * relief.mountains().get().highlandAbove())) {
            type = SurfaceType.HIGHLAND;
        } else {
            type = SurfaceType.REGOLITH;
        }
        return new Sample(height, type);
    }

    /** Cordilheira: (1 − |ruído|)² sob uma máscara de fBm; some nas planícies baixas. */
    private double mountains(TerrainParameters.Mountains m, double px, double py, double pz, double mare) {
        double mf = m.maskFrequency();
        double mask = smoothstep(m.maskEdge0(), m.maskEdge1(), ridgeMask.fbm(px * mf, py * mf, pz * mf, 3));
        if (mask <= 0.0) {
            return 0.0;
        }
        double f = m.frequency();
        double ridged = 1.0 - Math.abs(ridgeNoise.fbm(px * f, py * f, pz * f, 3));
        return ridged * ridged * mask * (1.0 - mare) * amplitude * m.strength();
    }

    /**
     * Soma das crateras no ponto da esfera unitária. out[0] = delta de altura (blocos);
     * out[1] > 0 se o ponto está no fundo de uma cratera grande.
     */
    private void craters(double px, double py, double pz, double[] out) {
        TerrainParameters.Craters c = relief.craters();
        double radiusMin = c.radiusMin();
        double radiusSpan = c.radiusSpan();
        double influence = c.influence();
        double rimWidth = c.rimWidth();
        double floorMinRadius = c.floorMinRadius();
        double delta = 0;
        boolean floor = false;
        for (int s = 0; s < craterScales.length; s++) {
            CraterScale scale = craterScales[s];
            double f = scale.frequency();
            double x = px * f;
            double y = py * f;
            double z = pz * f;
            long cx = (long) Math.floor(x);
            long cy = (long) Math.floor(y);
            long cz = (long) Math.floor(z);
            for (long i = cx - 1; i <= cx + 1; i++) {
                for (long j = cy - 1; j <= cy + 1; j++) {
                    for (long k = cz - 1; k <= cz + 1; k++) {
                        long h = cellHash(craterSeed, s, i, j, k);
                        if (unit(h, 0) >= scale.probability()) {
                            continue;
                        }
                        // raio em células: influence * raio máximo <= 1, então a influência cabe nos vizinhos 3x3x3
                        double rc = radiusMin + radiusSpan * unit(h, 12);
                        double dx = x - (i + 0.1 + 0.8 * unit(h, 24));
                        double dy = y - (j + 0.1 + 0.8 * unit(h, 36));
                        double dz = z - (k + 0.1 + 0.8 * unit(h, 48));
                        double t = Math.sqrt(dx * dx + dy * dy + dz * dz) / rc;
                        if (t >= influence) {
                            continue;
                        }
                        double rBlocks = rc / f * radius;
                        // bacia: fundo plano que sobe suavemente até a borda; borda: pico gaussiano em t = 1
                        double bowl = -scale.depth() * rBlocks * (1.0 - smoothstep(0.5, 0.95, t));
                        double rim = scale.rim() * rBlocks * Math.exp(-sq((t - 1.0) / rimWidth));
                        delta += bowl + rim;
                        if (t < 0.55 && rBlocks >= floorMinRadius) {
                            floor = true;
                        }
                    }
                }
            }
        }
        out[0] = Math.max(-c.maxDepth(), Math.min(c.maxRise(), delta));
        out[1] = floor ? 1 : 0;
    }

    /**
     * Vulcões em escudo no ponto da esfera unitária: cone largo e suave, (1 − t)^1,6 da altura, com a caldeira afundada no
     * topo. out[0] = delta de altura (blocos); out[1] > 0 se o ponto está na encosta pintada de vulcânico.
     */
    private void volcanoes(double px, double py, double pz, double[] out) {
        TerrainParameters.Volcanoes v = relief.volcanoes().orElseThrow();
        double f = v.frequency();
        double x = px * f;
        double y = py * f;
        double z = pz * f;
        long cx = (long) Math.floor(x);
        long cy = (long) Math.floor(y);
        long cz = (long) Math.floor(z);
        double delta = 0;
        boolean slope = false;
        for (long i = cx - 1; i <= cx + 1; i++) {
            for (long j = cy - 1; j <= cy + 1; j++) {
                for (long k = cz - 1; k <= cz + 1; k++) {
                    long h = cellHash(volcanoSeed, VOLCANO_SCALE, i, j, k);
                    if (unit(h, 0) >= v.probability()) {
                        continue;
                    }
                    double rc = v.radiusMin() + v.radiusSpan() * unit(h, 12);
                    double dx = x - (i + 0.1 + 0.8 * unit(h, 24));
                    double dy = y - (j + 0.1 + 0.8 * unit(h, 36));
                    double dz = z - (k + 0.1 + 0.8 * unit(h, 48));
                    double t = Math.sqrt(dx * dx + dy * dy + dz * dz) / rc;
                    if (t >= 1.0) {
                        continue;
                    }
                    double cone = v.height() * Math.pow(1.0 - t, 1.6);
                    double caldera = v.calderaRadius();
                    if (caldera > 0.0 && t < caldera) {
                        double u = t / caldera;
                        // fundo plano e parede íngreme, sem degrau na borda (u = 1 → 0)
                        cone -= v.calderaDepth() * v.height() * (1.0 - smoothstep(0.6, 1.0, u));
                    }
                    delta += cone;
                    if (t < v.typeRadius()) {
                        slope = true;
                    }
                }
            }
        }
        out[0] = delta;
        out[1] = slope ? 1 : 0;
    }

    private static long cellHash(long seed, int scale, long i, long j, long k) {
        long h = mix(seed + scale * 0x632B_E59B_D9B4_E019L);
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
