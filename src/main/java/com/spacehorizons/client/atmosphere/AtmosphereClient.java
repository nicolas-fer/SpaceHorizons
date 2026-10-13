package com.spacehorizons.client.atmosphere;

import java.util.Optional;

import com.spacehorizons.dh.DhFogConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Entrada da atmosfera por altitude: neblina vanilla e do DH, cor do céu, estrelas e o brilho do limbo.
 * Chamar {@link #init} uma vez a partir do construtor do mod de cliente.
 */
public final class AtmosphereClient {
    private AtmosphereClient() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(AtmosphereLimbRenderer::onRegisterPipelines);

        NeoForge.EVENT_BUS.addListener(AtmosphereFog::onRenderFog);
        NeoForge.EVENT_BUS.addListener(AtmosphereFog::onComputeFogColor);
        NeoForge.EVENT_BUS.addListener(AtmosphereFog::onExtractRenderState);
        NeoForge.EVENT_BUS.addListener(AtmosphereLimbRenderer::onAfterSky);

        if (ModList.get().isLoaded("distanthorizons")) {
            NeoForge.EVENT_BUS.addListener(AtmosphereClient::onClientTick);
            NeoForge.EVENT_BUS.addListener(AtmosphereClient::onLoggingOut);
        }
    }

    /** Ajusta a neblina do DH pela densidade na câmera; fora de planeta devolve a config do usuário. */
    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            DhFogConfig.restore();
            return;
        }
        Optional<AtmosphereSample> sample = AtmosphereSample.of(level, minecraft.gameRenderer.mainCamera().position().y);
        if (sample.isEmpty()) {
            DhFogConfig.restore();
            return;
        }
        double density = sample.get().model().density(sample.get().altitude());
        DhFogConfig.update(density);
        DhFogConfig.updateClouds(density >= 0.999);
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        DhFogConfig.restore();
    }
}
