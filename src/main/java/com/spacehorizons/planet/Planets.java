package com.spacehorizons.planet;

import java.util.Map;
import java.util.Optional;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.resources.Identifier;

/** Catálogo de planetas. Por enquanto fixo em código; vira data-driven depois. */
public final class Planets {
    /**
     * Planeta de teste pequeno (R ≈ 10.430) para a curvatura ficar evidente:
     * a 4.096 blocos o chão já caiu ~800 blocos abaixo do horizonte plano.
     */
    public static final PlanetDefinition EARTH = new PlanetDefinition(
            Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "earth"),
            0x5EAC0FFEEL,
            65_536,
            63,
            70.0,
            96);

    private static final Map<Identifier, PlanetDefinition> BY_ID = Map.of(EARTH.id(), EARTH);

    private Planets() {
    }

    public static Optional<PlanetDefinition> byId(Identifier id) {
        return Optional.ofNullable(BY_ID.get(id));
    }
}
