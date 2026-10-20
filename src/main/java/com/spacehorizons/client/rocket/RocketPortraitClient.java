package com.spacehorizons.client.rocket;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.PortraitParts;
import com.spacehorizons.rocket.RocketPortraitPartPayload;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Recebe as partes do retrato de um foguete grande demais para o pacote de spawn e as junta ({@link PortraitParts}); a entidade
 * ({@code RocketEntity.tick}, no cliente) pega o retrato quando ele fica completo.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class RocketPortraitClient {
    private RocketPortraitClient() {
    }

    @SubscribeEvent
    static void onRegisterHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(RocketPortraitPartPayload.TYPE, (payload, context) -> {
            try {
                if (PortraitParts.CLIENT.accept(payload.portraitId(), payload.index(), payload.count(), payload.bytes())) {
                    SpaceHorizons.LOGGER.info("Retrato do foguete {} completo no cliente ({} parte(s))", payload.portraitId(), payload.count());
                }
            } catch (IllegalArgumentException e) {
                SpaceHorizons.LOGGER.warn("Parte de retrato recusada: {}", e.getMessage());
            }
        });
    }
}
