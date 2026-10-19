package com.spacehorizons.client.space;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.space.SkyTestPayload;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Cliente do comando {@code /spacehorizons skytest} (spike S1): aplica o {@link SkyTestPayload} ao {@link SkyAttitude}. O handler
 * roda no thread principal, o mesmo do render. Registrada sozinha pelo {@link EventBusSubscriber} (só no cliente).
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class SkyTestClient {
    private SkyTestClient() {
    }

    @SubscribeEvent
    static void onRegisterHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(SkyTestPayload.TYPE, (payload, context) -> {
            switch (payload.mode()) {
                case 1 -> SkyAttitude.set(payload.a(), payload.b(), payload.c());
                case 2 -> SkyAttitude.spin(payload.a(), payload.axis(), System.nanoTime());
                default -> SkyAttitude.off();
            }
            SpaceHorizons.LOGGER.info("[skytest] modo={} a={} b={} c={} eixo={}", payload.mode(), payload.a(), payload.b(), payload.c(), payload.axis());
        });
    }
}
