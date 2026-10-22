package com.spacehorizons.planet;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.planet.spec.PlanetSpec;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

/**
 * Catálogo de planetas. Cada um é descrito por parâmetros em {@code data/spacehorizons/planet/<id>.json} (ver
 * {@link PlanetSpec}), lidos do jar na primeira vez que esta classe é usada; aqui ficam só os ids e os atalhos.
 */
public final class Planets {
    private static final Map<Identifier, PlanetSpec> SPECS = new LinkedHashMap<>();

    /**
     * Terra de teste (10x a escala original, R ≈ 104.303) para a curvatura ficar evidente: a 40.960 blocos o chão já caiu
     * ~8.000 blocos abaixo do horizonte plano. Mundo do gerador vanilla.
     */
    public static final PlanetDefinition EARTH = register("earth");

    /**
     * Lua pequena (R ≈ 26.076, um quarto da Terra): curvatura forte, sem água nem atmosfera, gravidade de ~1/6. seaLevel 63
     * é só a referência de altitude.
     */
    public static final PlanetDefinition MOON = register("moon");

    /**
     * Marte (R ≈ 55.412, 0,53 da Terra do mod, a mesma razão da real): gravidade 0,38, atmosfera fina avermelhada sem
     * oxigênio, crateras, vulcões em escudo e calotas de gelo. Fora da física orbital por enquanto (etapa 3 dos tiers).
     */
    public static final PlanetDefinition MARS = register("mars");

    public static final List<PlanetDefinition> ALL = List.of(EARTH, MOON, MARS);

    private Planets() {
    }

    private static PlanetDefinition register(String path) {
        Identifier id = Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, path);
        PlanetSpec spec = PlanetSpec.load(id);
        SPECS.put(id, spec);
        return spec.definition();
    }

    public static Optional<PlanetDefinition> byId(Identifier id) {
        return Optional.ofNullable(SPECS.get(id)).map(PlanetSpec::definition);
    }

    /**
     * Parâmetros completos de um planeta do catálogo. Pelo id: uma definição com a mesma identidade e outros números (testes
     * com outra semente) usa os parâmetros do planeta de mesmo id.
     */
    public static PlanetSpec spec(PlanetDefinition planet) {
        PlanetSpec spec = SPECS.get(planet.id());
        if (spec == null) {
            throw new IllegalArgumentException("planeta fora do catálogo: " + planet.id());
        }
        return spec;
    }

    /**
     * Planeta de um level, pelo TIPO de dimensão (não pelo id da dimensão): com o tipo de mundo
     * "Space Horizons" o Overworld usa o tipo spacehorizons:earth. O cliente também conhece o tipo,
     * então servidor e cliente chegam à mesma resposta sem pacote extra.
     */
    public static Optional<PlanetDefinition> forLevel(Level level) {
        return level.dimensionTypeRegistration().unwrapKey().flatMap(key -> byId(key.identifier()));
    }
}
