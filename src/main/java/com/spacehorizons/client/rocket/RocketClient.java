package com.spacehorizons.client.rocket;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModEntities;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Ligação do render do foguete no cliente (registrada sozinha pelo {@link EventBusSubscriber}, só no cliente): o
 * renderer da entidade e a limpeza do cache de malhas ao sair do mundo. A troca de dimensão NÃO limpa o cache: o retrato é
 * o mesmo (mesmo id) e a malha continua valendo.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class RocketClient {
    private RocketClient() {
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ROCKET.get(), RocketRenderer::new);
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        RocketRenderer.MESHES.clear();
    }

    /** Quantas malhas de foguete o cache tem (assadas ou assando); para o autoteste. */
    public static int meshCount() {
        return RocketRenderer.MESHES.size();
    }
}
