package com.spacehorizons.planet.spec;

import static com.spacehorizons.planet.spec.SpecCodecs.require;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.SurfaceType;
import com.spacehorizons.planet.TerrainStyle;

import net.minecraft.resources.Identifier;

/**
 * Um planeta inteiro descrito por parâmetros: o arquivo {@code data/<ns>/planet/<id>.json} do mod. Dele saem a
 * {@link PlanetDefinition} (circunferência, gravidade, estilo, semente), o relevo, a paleta de superfície e subsolo, a
 * água, a atmosfera, os veios de minério e a duração do dia. Formato completo em {@code docs/GERADOR_PLANETAS.md}.
 *
 * Os arquivos são lidos do jar (classpath), não do datapack: cliente e servidor precisam dos mesmos valores antes de
 * qualquer mundo carregar (malha orbital, céu, curvatura e gravidade são estáticos por planeta), então um datapack não
 * consegue trocá-los. Mudar um planeta é mudar o JSON e recompilar.
 */
public record PlanetSpec(PlanetDefinition definition, int dayLengthTicks, TerrainParameters terrain, SurfaceSpec surface,
        WaterSpec water, AtmosphereSpec atmosphere, List<OreVeinSpec> ores) {

    /** Dia do jogo (o do Overworld); vale para planetas fora do SolarSystem que não dizem outra duração. */
    public static final int DEFAULT_DAY_TICKS = 24_000;

    public PlanetSpec {
        ores = List.copyOf(ores);
        PlanetDefinition p = definition;
        require(dayLengthTicks >= 1_000 && dayLengthTicks <= 2_400_000, "day_length_ticks fora de [1000, 2400000]: " + dayLengthTicks);
        require(p.seaLevel() >= -60 && p.seaLevel() <= 250, "sea_level fora de [-60, 250]: " + p.seaLevel());
        require(water.ocean() == (p.style() == TerrainStyle.EARTH),
                "water.ocean: por enquanto só o estilo earth tem oceano (e ele sempre tem)");
        Set<SurfaceType> required;
        if (p.style() == TerrainStyle.PARAMETRIC) {
            require(terrain.relief().isPresent(), "estilo parametric precisa de terrain.relief");
            TerrainParameters.Relief relief = terrain.relief().get();
            require(relief.maxHeight() > p.seaLevel() + relief.minAboveSea(), "relief.max_height abaixo do piso (sea_level + min_above_sea)");
            required = relief.producedTypes();
        } else {
            require(terrain.relief().isEmpty(), "estilo earth usa o gerador vanilla: terrain.relief não se aplica");
            require(ores.isEmpty(), "estilo earth usa os minérios do vanilla: ores não se aplica");
            required = SurfaceType.EARTH_TYPES;
        }
        Set<SurfaceType> missing = EnumSet.noneOf(SurfaceType.class);
        for (SurfaceType type : required) {
            if (!surface.types().containsKey(type)) {
                missing.add(type);
            }
        }
        require(missing.isEmpty(), "surface.types sem os papéis que o terreno produz: " + missing);
    }

    public Identifier id() {
        return definition.id();
    }

    /** Codec do arquivo; o id vem do nome do arquivo, não do conteúdo. */
    public static Codec<PlanetSpec> codec(Identifier id) {
        return RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("seed").forGetter(s -> s.definition.seed()),
                Codec.INT.fieldOf("circumference").forGetter(s -> s.definition.circumferenceBlocks()),
                Codec.INT.fieldOf("sea_level").forGetter(s -> s.definition.seaLevel()),
                Codec.DOUBLE.fieldOf("max_latitude").forGetter(s -> s.definition.maxLatitudeDegrees()),
                Codec.DOUBLE.fieldOf("gravity").forGetter(s -> s.definition.surfaceGravity()),
                TerrainStyle.CODEC.fieldOf("style").forGetter(s -> s.definition.style()),
                Codec.INT.optionalFieldOf("day_length_ticks", DEFAULT_DAY_TICKS).forGetter(PlanetSpec::dayLengthTicks),
                TerrainParameters.CODEC.fieldOf("terrain").forGetter(PlanetSpec::terrain),
                SurfaceSpec.CODEC.fieldOf("surface").forGetter(PlanetSpec::surface),
                WaterSpec.CODEC.fieldOf("water").forGetter(PlanetSpec::water),
                AtmosphereSpec.CODEC.fieldOf("atmosphere").forGetter(PlanetSpec::atmosphere),
                OreVeinSpec.CODEC.listOf().optionalFieldOf("ores", List.of()).forGetter(PlanetSpec::ores))
                .apply(i, (seed, circumference, sea, maxLat, gravity, style, day, terrain, surface, water, atmosphere, ores) ->
                        new PlanetSpec(new PlanetDefinition(id, seed, circumference, sea, maxLat, terrain.amplitude(), style, gravity),
                                day, terrain, surface, water, atmosphere, ores)));
    }

    /**
     * Lê e valida um planeta a partir do texto JSON. Qualquer parâmetro inválido (faltando, fora da faixa, incoerente)
     * vira IllegalArgumentException com o id do planeta e o motivo.
     */
    public static PlanetSpec parse(Identifier id, String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            return codec(id).parse(JsonOps.INSTANCE, element)
                    .getOrThrow(message -> new IllegalArgumentException(message));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("parâmetros inválidos do planeta " + id + ": " + e.getMessage(), e);
        }
    }

    /** Caminho do arquivo do planeta no jar: {@code data/<ns>/planet/<path>.json}. */
    public static String resourcePath(Identifier id) {
        return "data/" + id.getNamespace() + "/planet/" + id.getPath() + ".json";
    }

    /** Lê o planeta do jar (classpath). Falha alto: um planeta do catálogo sem arquivo válido é erro de empacotamento. */
    public static PlanetSpec load(Identifier id) {
        String path = resourcePath(id);
        InputStream in = PlanetSpec.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            in = PlanetSpec.class.getResourceAsStream("/" + path);
        }
        if (in == null) {
            throw new IllegalStateException("arquivo do planeta ausente no jar: " + path);
        }
        try (InputStream stream = in) {
            return parse(id, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("não foi possível ler " + path, e);
        }
    }
}
