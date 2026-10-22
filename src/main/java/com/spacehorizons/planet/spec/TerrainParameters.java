package com.spacehorizons.planet.spec;

import static com.spacehorizons.planet.spec.SpecCodecs.require;
import static com.spacehorizons.planet.spec.SpecCodecs.requireRange;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spacehorizons.planet.SurfaceType;

/**
 * Relevo de um planeta ({@code "terrain"} no JSON). {@code amplitude} (blocos) escala quase tudo; o estilo Terra só usa
 * ela (o resto do mundo é vanilla). O estilo por parâmetros exige {@code relief}, lido por
 * {@code planet.terrain.ParametricTerrain}; a documentação de cada campo está em {@code docs/GERADOR_PLANETAS.md}.
 *
 * Frequências são na esfera unitária: a circunferência inteira é 2π, então frequência f dá feições de ~R/f blocos, e o
 * mesmo JSON serve a planetas de tamanhos diferentes (o relevo escala com o raio, as alturas não).
 */
public record TerrainParameters(int amplitude, Optional<Relief> relief) {
    public static final Codec<TerrainParameters> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("amplitude").forGetter(TerrainParameters::amplitude),
            Relief.CODEC.optionalFieldOf("relief").forGetter(TerrainParameters::relief))
            .apply(i, TerrainParameters::new));

    public TerrainParameters {
        require(amplitude >= 1 && amplitude <= 256, "terrain.amplitude fora de [1, 256]: " + amplitude);
    }

    /**
     * Relevo analítico. Altura = nível do mar + baseHeight + terras altas − planícies baixas + detalhe + crateras
     * [+ montanhas] [+ vulcões] + parede polar, limitada a [mar + minAboveSea, maxHeight].
     */
    public record Relief(
            double baseHeight,
            int minAboveSea,
            int maxHeight,
            NoiseBand lowlands,
            NoiseBand highlands,
            Detail detail,
            Polar polar,
            Craters craters,
            Optional<Mountains> mountains,
            Optional<Volcanoes> volcanoes) {
        public static final Codec<Relief> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("base_height").forGetter(Relief::baseHeight),
                Codec.INT.fieldOf("min_above_sea").forGetter(Relief::minAboveSea),
                Codec.INT.fieldOf("max_height").forGetter(Relief::maxHeight),
                NoiseBand.CODEC.fieldOf("lowlands").forGetter(Relief::lowlands),
                NoiseBand.CODEC.fieldOf("highlands").forGetter(Relief::highlands),
                Detail.CODEC.fieldOf("detail").forGetter(Relief::detail),
                Polar.CODEC.fieldOf("polar").forGetter(Relief::polar),
                Craters.CODEC.fieldOf("craters").forGetter(Relief::craters),
                Mountains.CODEC.optionalFieldOf("mountains").forGetter(Relief::mountains),
                Volcanoes.CODEC.optionalFieldOf("volcanoes").forGetter(Relief::volcanoes))
                .apply(i, Relief::new));

        public Relief {
            requireRange(baseHeight, 0, 250, "relief.base_height");
            require(minAboveSea >= 0, "relief.min_above_sea negativo: " + minAboveSea);
            require(maxHeight > minAboveSea && maxHeight <= 315, "relief.max_height fora de (min_above_sea, 315]: " + maxHeight);
        }

        /** Todos os papéis de superfície que este relevo pode produzir (a paleta do planeta precisa ter cada um). */
        public Set<SurfaceType> producedTypes() {
            Set<SurfaceType> types = EnumSet.of(SurfaceType.REGOLITH, SurfaceType.MARE, SurfaceType.HIGHLAND,
                    SurfaceType.CRATER_FLOOR, polar.capType());
            if (volcanoes.isPresent()) {
                types.add(SurfaceType.VOLCANIC);
            }
            return types;
        }
    }

    /**
     * Faixa de ruído fBm que vira máscara 0..1 por smoothstep(edge0, edge1, ruído). Nas planícies baixas o ruído entra com o
     * sinal trocado (as bacias ficam onde o ruído é negativo). {@code strength} multiplica a amplitude.
     */
    public record NoiseBand(double frequency, int octaves, double edge0, double edge1, double strength) {
        public static final Codec<NoiseBand> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("frequency").forGetter(NoiseBand::frequency),
                Codec.INT.fieldOf("octaves").forGetter(NoiseBand::octaves),
                Codec.DOUBLE.fieldOf("edge0").forGetter(NoiseBand::edge0),
                Codec.DOUBLE.fieldOf("edge1").forGetter(NoiseBand::edge1),
                Codec.DOUBLE.fieldOf("strength").forGetter(NoiseBand::strength))
                .apply(i, NoiseBand::new));

        public NoiseBand {
            requireRange(frequency, 0.01, 1000, "frequency");
            require(octaves >= 1 && octaves <= 8, "octaves fora de [1, 8]: " + octaves);
            require(edge1 > edge0, "edge1 deve ser maior que edge0: " + edge0 + " / " + edge1);
            requireRange(strength, 0, 4, "strength");
        }
    }

    /**
     * Rugosidade fina: fBm de {@code frequency} vezes amplitude·roughness, que cai {@code lowlandSmoothing} nas planícies
     * baixas e sobe {@code highlandRoughening} nas terras altas.
     */
    public record Detail(double frequency, int octaves, double roughness, double lowlandSmoothing, double highlandRoughening) {
        public static final Codec<Detail> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("frequency").forGetter(Detail::frequency),
                Codec.INT.fieldOf("octaves").forGetter(Detail::octaves),
                Codec.DOUBLE.fieldOf("roughness").forGetter(Detail::roughness),
                Codec.DOUBLE.fieldOf("lowland_smoothing").forGetter(Detail::lowlandSmoothing),
                Codec.DOUBLE.fieldOf("highland_roughening").forGetter(Detail::highlandRoughening))
                .apply(i, Detail::new));

        public Detail {
            requireRange(frequency, 0.01, 1000, "detail.frequency");
            require(octaves >= 1 && octaves <= 8, "detail.octaves fora de [1, 8]: " + octaves);
            requireRange(roughness, 0, 2, "detail.roughness");
            requireRange(lowlandSmoothing, 0, 1, "detail.lowland_smoothing");
            requireRange(highlandRoughening, 0, 4, "detail.highland_roughening");
        }
    }

    /**
     * Calota polar: um platô de amplitude·wallHeight sobe a partir da fração {@code wallStart} da latitude máxima (fecha
     * a calota antes da barreira) e, de {@code capStart} em diante, a superfície é {@code capType} (gelo, rocha...).
     */
    public record Polar(double wallStart, double wallHeight, double capStart, SurfaceType capType) {
        public static final Codec<Polar> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("wall_start").forGetter(Polar::wallStart),
                Codec.DOUBLE.fieldOf("wall_height").forGetter(Polar::wallHeight),
                Codec.DOUBLE.fieldOf("cap_start").forGetter(Polar::capStart),
                SurfaceType.CODEC.fieldOf("cap_type").forGetter(Polar::capType))
                .apply(i, Polar::new));

        public Polar {
            require(wallStart > 0 && wallStart < 1, "polar.wall_start fora de (0, 1): " + wallStart);
            requireRange(wallHeight, 0, 4, "polar.wall_height");
            require(capStart > 0 && capStart <= 1, "polar.cap_start fora de (0, 1]: " + capStart);
            require(!SurfaceType.EARTH_TYPES.contains(capType) || capType == SurfaceType.POLAR_ICE,
                    "polar.cap_type não pode ser um tipo de Terra além de polar_ice: " + capType);
        }
    }

    /** Uma escala de crateras: células de 1/frequency de raio na esfera; probability = chance de a célula ter cratera. */
    public record CraterScale(double frequency, double probability, double depth, double rim) {
        public static final Codec<CraterScale> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("frequency").forGetter(CraterScale::frequency),
                Codec.DOUBLE.fieldOf("probability").forGetter(CraterScale::probability),
                Codec.DOUBLE.fieldOf("depth").forGetter(CraterScale::depth),
                Codec.DOUBLE.fieldOf("rim").forGetter(CraterScale::rim))
                .apply(i, CraterScale::new));

        public CraterScale {
            requireRange(frequency, 0.5, 2000, "craters.scales.frequency");
            requireRange(probability, 0, 1, "craters.scales.probability");
            requireRange(depth, 0, 1, "craters.scales.depth");
            requireRange(rim, 0, 1, "craters.scales.rim");
        }
    }

    /**
     * Crateras em várias escalas. O raio sorteado (em células) fica em [radiusMin, radiusMin + radiusSpan]; a cratera mexe
     * no relevo até {@code influence} raios (a borda é um pico gaussiano de largura {@code rimWidth} em t = 1); só as de raio
     * ≥ floorMinRadius blocos pintam o fundo; a soma das crateras sobrepostas fica em [-maxDepth, +maxRise] blocos.
     */
    public record Craters(List<CraterScale> scales, double radiusMin, double radiusSpan, double influence, double rimWidth,
            double floorMinRadius, double maxDepth, double maxRise) {
        public static final Codec<Craters> CODEC = RecordCodecBuilder.create(i -> i.group(
                CraterScale.CODEC.listOf().fieldOf("scales").forGetter(Craters::scales),
                Codec.DOUBLE.fieldOf("radius_min").forGetter(Craters::radiusMin),
                Codec.DOUBLE.fieldOf("radius_span").forGetter(Craters::radiusSpan),
                Codec.DOUBLE.fieldOf("influence").forGetter(Craters::influence),
                Codec.DOUBLE.fieldOf("rim_width").forGetter(Craters::rimWidth),
                Codec.DOUBLE.fieldOf("floor_min_radius").forGetter(Craters::floorMinRadius),
                Codec.DOUBLE.fieldOf("max_depth").forGetter(Craters::maxDepth),
                Codec.DOUBLE.fieldOf("max_rise").forGetter(Craters::maxRise))
                .apply(i, Craters::new));

        public Craters {
            scales = List.copyOf(scales);
            require(scales.size() <= 6, "craters.scales: no máximo 6 escalas: " + scales.size());
            require(radiusMin > 0 && radiusSpan >= 0, "craters.radius_min > 0 e radius_span >= 0");
            require(influence >= 1, "craters.influence < 1: " + influence);
            // a influência inteira precisa caber nos vizinhos 3x3x3 da célula, senão a cratera seria cortada na borda da célula
            require(influence * (radiusMin + radiusSpan) <= 1.0 + 1e-9,
                    "craters: influence * (radius_min + radius_span) passa de 1 célula: " + influence * (radiusMin + radiusSpan));
            requireRange(rimWidth, 0.01, 1, "craters.rim_width");
            require(floorMinRadius >= 0 && maxDepth >= 0 && maxRise >= 0, "craters: floor_min_radius, max_depth e max_rise >= 0");
        }
    }

    /**
     * Cordilheiras: ruído "ridged" (1 − |ruído|)² de {@code frequency}, só onde a máscara fBm de {@code maskFrequency} passa de
     * smoothstep(maskEdge0, maskEdge1); altura = amplitude·strength. Acima de amplitude·highlandAbove a superfície vira terra alta.
     */
    public record Mountains(double frequency, double maskFrequency, double maskEdge0, double maskEdge1, double strength,
            double highlandAbove) {
        public static final Codec<Mountains> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("frequency").forGetter(Mountains::frequency),
                Codec.DOUBLE.fieldOf("mask_frequency").forGetter(Mountains::maskFrequency),
                Codec.DOUBLE.fieldOf("mask_edge0").forGetter(Mountains::maskEdge0),
                Codec.DOUBLE.fieldOf("mask_edge1").forGetter(Mountains::maskEdge1),
                Codec.DOUBLE.fieldOf("strength").forGetter(Mountains::strength),
                Codec.DOUBLE.fieldOf("highland_above").forGetter(Mountains::highlandAbove))
                .apply(i, Mountains::new));

        public Mountains {
            requireRange(frequency, 0.01, 1000, "mountains.frequency");
            requireRange(maskFrequency, 0.01, 1000, "mountains.mask_frequency");
            require(maskEdge1 > maskEdge0, "mountains.mask_edge1 deve ser maior que mask_edge0");
            requireRange(strength, 0, 4, "mountains.strength");
            requireRange(highlandAbove, 0, 4, "mountains.highland_above");
        }
    }

    /**
     * Vulcões em escudo: uma grade de células de 1/frequency na esfera, cada uma com chance {@code probability} de um vulcão
     * de raio [radiusMin, radiusMin + radiusSpan] células (no máximo 0,5) e {@code height} blocos de altura; caldeira no
     * topo com {@code calderaRadius} do raio e {@code calderaDepth} da altura. Até {@code typeRadius} do raio a superfície é
     * {@link SurfaceType#VOLCANIC}.
     */
    public record Volcanoes(double frequency, double probability, double radiusMin, double radiusSpan, double height,
            double calderaRadius, double calderaDepth, double typeRadius) {
        public static final Codec<Volcanoes> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("frequency").forGetter(Volcanoes::frequency),
                Codec.DOUBLE.fieldOf("probability").forGetter(Volcanoes::probability),
                Codec.DOUBLE.fieldOf("radius_min").forGetter(Volcanoes::radiusMin),
                Codec.DOUBLE.fieldOf("radius_span").forGetter(Volcanoes::radiusSpan),
                Codec.DOUBLE.fieldOf("height").forGetter(Volcanoes::height),
                Codec.DOUBLE.fieldOf("caldera_radius").forGetter(Volcanoes::calderaRadius),
                Codec.DOUBLE.fieldOf("caldera_depth").forGetter(Volcanoes::calderaDepth),
                Codec.DOUBLE.fieldOf("type_radius").forGetter(Volcanoes::typeRadius))
                .apply(i, Volcanoes::new));

        public Volcanoes {
            requireRange(frequency, 0.5, 100, "volcanoes.frequency");
            requireRange(probability, 0, 1, "volcanoes.probability");
            require(radiusMin > 0 && radiusSpan >= 0 && radiusMin + radiusSpan <= 0.5 + 1e-9,
                    "volcanoes: radius_min > 0, radius_span >= 0 e radius_min + radius_span <= 0,5");
            requireRange(height, 0, 250, "volcanoes.height");
            requireRange(calderaRadius, 0, 0.9, "volcanoes.caldera_radius");
            requireRange(calderaDepth, 0, 1, "volcanoes.caldera_depth");
            requireRange(typeRadius, 0, 1, "volcanoes.type_radius");
        }
    }
}
