package com.spacehorizons.client.space;

import com.spacehorizons.planet.Planets;

import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Entrada dos corpos no céu (BodyRenderer) e da supressão da lua vanilla. Ligada por AtmosphereClient.init, então
 * não precisa de chamada própria em SpaceHorizonsClient.
 */
public final class BodyClient {
    private BodyClient() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(BodyPipelines::register);
        // prioridade alta: os corpos vão ao fundo, ANTES do limbo e da malha orbital do planeta atual, que cobrem o que está atrás
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, BodyClient::onAfterSky);
        NeoForge.EVENT_BUS.addListener(BodyClient::onExtractRenderState);
        NeoForge.EVENT_BUS.addListener(BodyClient::onLoggingOut);
        // o céu de dentro da dimensão de espaço aberto (sol, estrelas, sem lua do vanilla, sem névoa)
        SpaceSkyClient.init();
    }

    private static void onAfterSky(RenderLevelStageEvent.AfterSky event) {
        BodyRenderer.INSTANCE.render(event.getLevelRenderState(), event.getModelViewMatrix());
    }

    /**
     * Esconde a lua vanilla (textura PNG) nos planetas do mod: a Lua de verdade é a esfera do BodyRenderer. O céu
     * vanilla desenha a lua a moonAngle graus da órbita do sol; π a põe direto no nadir (0, -1, 0), onde o chão do
     * planeta sempre a cobre. O sol fica como está.
     */
    private static void onExtractRenderState(ExtractLevelRenderStateEvent event) {
        SkyRenderState sky = event.getRenderState().skyRenderState;
        if (sky.skybox != DimensionType.Skybox.OVERWORLD || Planets.forLevel(event.getLevel()).isEmpty()) {
            return;
        }
        sky.moonAngle = (float) Math.PI;
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BodyRenderer.INSTANCE.reset();
        SpaceSun.reset();
    }
}
