package com.spacehorizons.client.ship;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModEntities;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Ligação das naves no cliente (registrada sozinha pelo {@link EventBusSubscriber}): o assento é uma entidade invisível, sem desenho. */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class ShipClient {
    private ShipClient() {
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SHIP_SEAT.get(), NoopRenderer::new);
    }
}
