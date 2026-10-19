package com.spacehorizons.planet;

import java.util.Map;
import java.util.Optional;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

/** Catálogo de planetas. Por enquanto fixo em código; vira data-driven depois. */
public final class Planets {
    /**
     * Planeta de teste (10x a escala original) (R ≈ 104.303) para a curvatura ficar evidente:
     * a 40.960 blocos o chão já caiu ~8.000 blocos abaixo do horizonte plano.
     */
    public static final PlanetDefinition EARTH = new PlanetDefinition(
            Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "earth"),
            0x5EAC0FFEEL,
            655_360,
            63,
            70.0,
            96,
            TerrainStyle.EARTH,
            1.0);

    /**
     * Lua pequena (R ≈ 26.076, um quarto da Terra): curvatura forte, sem água nem atmosfera,
     * gravidade de ~1/6. seaLevel 63 é só a referência de altitude.
     */
    public static final PlanetDefinition MOON = new PlanetDefinition(
            Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "moon"),
            0x10A4L,
            163_840,
            63,
            75.0,
            48,
            TerrainStyle.MOON,
            0.165);

    private static final Map<Identifier, PlanetDefinition> BY_ID = Map.of(EARTH.id(), EARTH, MOON.id(), MOON);

    private Planets() {
    }

    public static Optional<PlanetDefinition> byId(Identifier id) {
        return Optional.ofNullable(BY_ID.get(id));
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
