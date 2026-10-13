package com.spacehorizons.client.planetmesh;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Entrada da malha orbital (o que se vê do planeta além do alcance do DH). Ligar em SpaceHorizonsClient:
 * {@code PlanetMeshClient.init(modEventBus);}
 */
public final class PlanetMeshClient {
    private PlanetMeshClient() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(PlanetMeshPipelines::register);
        // AfterSky: depois do céu e antes do terreno, no mesmo frame graph do vanilla
        NeoForge.EVENT_BUS.addListener(PlanetMeshClient::onAfterSky);
        NeoForge.EVENT_BUS.addListener(PlanetMeshClient::onLoggingOut);
    }

    private static void onAfterSky(RenderLevelStageEvent.AfterSky event) {
        PlanetMeshRenderer.INSTANCE.render(event.getLevelRenderState());
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PlanetMeshRenderer.INSTANCE.reset();
    }
}
