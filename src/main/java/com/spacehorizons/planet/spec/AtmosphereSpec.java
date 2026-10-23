package com.spacehorizons.planet.spec;

import static com.spacehorizons.planet.spec.SpecCodecs.require;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spacehorizons.planet.atmosphere.AtmosphereModel;

/**
 * Atmosfera ({@code "atmosphere"} no JSON). Sem ar: só {@code "present": false}. Com ar: espessura e altitude base da curva
 * de densidade (ver {@link AtmosphereModel}), se tem oxigênio, o espalhamento do limbo por canal e a profundidade óptica,
 * as cores do céu e da neblina e se há nuvens. Opcional ({@code surface_density}, padrão 1): a densidade do ar na superfície relativa
 * à da Terra, só para o arrasto e o calor do voo (Marte tem ar visível no céu, mas fino para o paraquedas). As cores e as nuvens são aplicadas pelo tipo de dimensão (atributos
 * {@code minecraft:visual/*} do JSON do tipo e do bioma); aqui ficam como a fonte declarada, e um teste confere que batem.
 */
public record AtmosphereSpec(boolean present, Optional<Air> air) {
    public static final Codec<AtmosphereSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("present").forGetter(AtmosphereSpec::present),
            Codec.DOUBLE.optionalFieldOf("thickness").forGetter(s -> s.air.map(Air::thickness)),
            Codec.DOUBLE.optionalFieldOf("base_altitude").forGetter(s -> s.air.map(Air::baseAltitude)),
            Codec.BOOL.optionalFieldOf("breathable").forGetter(s -> s.air.map(Air::breathable)),
            Codec.DOUBLE.listOf().optionalFieldOf("scatter").forGetter(s -> s.air.map(Air::scatter)),
            Codec.DOUBLE.optionalFieldOf("optical_depth").forGetter(s -> s.air.map(Air::opticalDepth)),
            SpecCodecs.COLOR.optionalFieldOf("sky_color").forGetter(s -> s.air.map(Air::skyColor)),
            SpecCodecs.COLOR.optionalFieldOf("fog_color").forGetter(s -> s.air.map(Air::fogColor)),
            Codec.BOOL.optionalFieldOf("clouds").forGetter(s -> s.air.map(Air::clouds)),
            Codec.DOUBLE.optionalFieldOf("surface_density").forGetter(s -> s.air.map(Air::surfaceDensity)))
            .apply(i, AtmosphereSpec::create));

    public AtmosphereSpec {
        require(present == air.isPresent(), "atmosphere: present não bate com os campos do ar");
    }

    private static AtmosphereSpec create(boolean present, Optional<Double> thickness, Optional<Double> base,
            Optional<Boolean> breathable, Optional<List<Double>> scatter, Optional<Double> opticalDepth, Optional<Integer> sky,
            Optional<Integer> fog, Optional<Boolean> clouds, Optional<Double> surfaceDensity) {
        boolean any = thickness.isPresent() || base.isPresent() || breathable.isPresent() || scatter.isPresent()
                || opticalDepth.isPresent() || sky.isPresent() || fog.isPresent() || clouds.isPresent() || surfaceDensity.isPresent();
        if (!present) {
            require(!any, "atmosphere sem ar (present = false) não leva outros campos");
            return new AtmosphereSpec(false, Optional.empty());
        }
        boolean all = thickness.isPresent() && base.isPresent() && breathable.isPresent() && scatter.isPresent()
                && opticalDepth.isPresent() && sky.isPresent() && fog.isPresent() && clouds.isPresent();
        require(all, "atmosphere com present = true precisa de thickness, base_altitude, breathable, scatter, optical_depth, "
                + "sky_color, fog_color e clouds");
        return new AtmosphereSpec(true, Optional.of(new Air(thickness.get(), base.get(), breathable.get(), scatter.get(),
                opticalDepth.get(), sky.get(), fog.get(), clouds.get(), surfaceDensity.orElse(1.0))));
    }

    /** Densidade do ar na superfície relativa à da Terra (1 sem o campo, e onde não há ar): vale só para o arrasto e o calor do voo. */
    public double surfaceDensity() {
        return air.map(Air::surfaceDensity).orElse(1.0);
    }

    public AtmosphereModel model() {
        return air.map(Air::model).orElse(AtmosphereModel.NONE);
    }

    /** Campos de uma atmosfera presente. */
    public record Air(double thickness, double baseAltitude, boolean breathable, List<Double> scatter, double opticalDepth,
            int skyColor, int fogColor, boolean clouds, double surfaceDensity) {
        /** Sem densidade declarada: a da Terra (1). */
        public Air(double thickness, double baseAltitude, boolean breathable, List<Double> scatter, double opticalDepth, int skyColor,
                int fogColor, boolean clouds) {
            this(thickness, baseAltitude, breathable, scatter, opticalDepth, skyColor, fogColor, clouds, 1.0);
        }

        public Air {
            require(surfaceDensity > 0.0 && surfaceDensity <= 2.0, "atmosphere.surface_density fora de (0, 2]: " + surfaceDensity);
            scatter = List.copyOf(scatter);
            require(scatter.size() == 3, "atmosphere.scatter precisa de 3 valores (r, g, b): " + scatter);
            // espessura maior que a base e valores não negativos: validados pelo próprio modelo
            new AtmosphereModel(thickness, baseAltitude, true, breathable, scatter.get(0), scatter.get(1), scatter.get(2),
                    opticalDepth);
        }

        public AtmosphereModel model() {
            return new AtmosphereModel(thickness, baseAltitude, true, breathable, scatter.get(0), scatter.get(1), scatter.get(2),
                    opticalDepth);
        }
    }
}
