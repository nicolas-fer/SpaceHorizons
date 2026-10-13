package com.spacehorizons.dh;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * A curvatura do DH é uma config global (um único earthCurveRatio), mas o servidor integrado carrega
 * vários planetas ao mesmo tempo. Por isso ela segue o planeta do level que o CLIENTE está vendo,
 * e não o último level carregado (o que deixava a Terra com a curvatura da Lua).
 */
public final class DhCurvatureClient {
    private static PlanetDefinition current;

    private DhCurvatureClient() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        PlanetDefinition planet = mc.level == null ? null : Planets.forLevel(mc.level).orElse(null);
        if (planet == current) {
            return;
        }
        current = planet;
        if (planet != null) {
            DhCurvatureConfig.apply(planet);
        } else {
            DhCurvatureConfig.restore();
        }
    }
}
