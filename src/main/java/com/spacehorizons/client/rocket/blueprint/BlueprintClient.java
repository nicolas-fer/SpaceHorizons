package com.spacehorizons.client.rocket.blueprint;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModBlockEntities;
import com.spacehorizons.rocket.blueprint.RocketBlueprintItem;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** O lado do cliente das plantas de montagem: o renderer do holograma na plataforma, o menu do item e a limpeza do cache. */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class BlueprintClient {
    private BlueprintClient() {
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.LAUNCH_PAD.get(), LaunchPadRenderer::new);
        // o clique no ar com a planta abre o menu (o item só chama este gancho; um servidor dedicado nunca o liga)
        RocketBlueprintItem.CLIENT_OPEN = hand -> Minecraft.getInstance().gui.setScreen(new BlueprintScreen(hand));
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getGameTime() % 20 == 0) {
            BlueprintHologram.prune(mc.level);
        }
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BlueprintHologram.clearAll();
    }
}
