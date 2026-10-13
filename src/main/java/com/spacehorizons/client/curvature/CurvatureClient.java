package com.spacehorizons.client.curvature;

import net.neoforged.bus.api.IEventBus;

/**
 * Entrada do lado cliente. A curvatura não precisa de eventos: os mixins em
 * com.spacehorizons.mixin leem CurvatureState no momento de escrever o TerrainUniform de cada frame.
 */
public final class CurvatureClient {
    private CurvatureClient() {
    }

    public static void init(IEventBus modEventBus) {
    }
}
