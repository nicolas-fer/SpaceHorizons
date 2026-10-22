package com.spacehorizons.client.atmosphere;

import com.spacehorizons.client.space.BodyClient;
import com.spacehorizons.dh.DhFogConfig;
import com.spacehorizons.planet.atmosphere.AtmosphereModel;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.joml.Matrix4fc;

/**
 * Entrada da atmosfera por altitude: neblina vanilla e do DH, cor do céu, estrelas e o brilho do limbo.
 * Chamar {@link #init} uma vez a partir do construtor do mod de cliente.
 */
public final class AtmosphereClient {
    /** Quantos halos de outros corpos cabem num frame (um anel de buffers cada; o slot 0 é do planeta atual). */
    public static final int MAX_HALOS = AtmosphereLimbRenderer.MAX_SHELLS - 1;

    private AtmosphereClient() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(AtmosphereLimbRenderer::onRegisterPipelines);

        NeoForge.EVENT_BUS.addListener(AtmosphereFog::onRenderFog);
        NeoForge.EVENT_BUS.addListener(AtmosphereFog::onComputeFogColor);
        NeoForge.EVENT_BUS.addListener(AtmosphereFog::onExtractRenderState);
        NeoForge.EVENT_BUS.addListener(AtmosphereLimbRenderer::onAfterSky);

        // corpos no céu (Lua, Terra vista de fora) e a lua vanilla escondida: o mesmo ponto de entrada do cliente
        BodyClient.init(modEventBus);

        if (ModList.get().isLoaded("distanthorizons")) {
            NeoForge.EVENT_BUS.addListener(AtmosphereClient::onClientTick);
            NeoForge.EVENT_BUS.addListener(AtmosphereClient::onLoggingOut);
        }
    }

    /**
     * Halo de atmosfera de outro corpo visto de fora, para o renderer de corpos (slot 0 a 2). Centro, raio e espessura na mesma
     * escala, relativos à câmera nos eixos do mundo (x leste, y cima, z norte); o sol é a direção no mesmo referencial.
     */
    public static void drawHalo(int slot, Matrix4fc invView, double centerX, double centerY, double centerZ,
            double radius, double thickness, double baseAltitude, AtmosphereModel model, float sunX, float sunY, float sunZ,
            double visibility) {
        AtmosphereLimbRenderer.drawShell(slot + 1, invView, centerX, centerY, centerZ, radius, thickness, baseAltitude, model,
                sunX, sunY, sunZ, visibility, true);
    }

    /** Com um mundo aberto a neblina do DH fica desligada; sem mundo, volta a config do usuário. */
    private static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) {
            DhFogConfig.restore();
            return;
        }
        DhFogConfig.disable();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        DhFogConfig.restore();
    }
}
