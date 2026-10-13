package com.spacehorizons.client.travel;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.space.travel.TravelTransferPayload;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Cliente da viagem: recebe o aviso de transferência e o guarda para o mixin do respawn. Registrada sozinha pelo
 * {@link EventBusSubscriber} (só no cliente); não precisa de chamada no construtor do mod.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class TravelClient {
    private TravelClient() {
    }

    @SubscribeEvent
    static void onRegisterHandlers(RegisterClientPayloadHandlersEvent event) {
        // handler roda na thread principal; o respawn que vem logo depois é processado na mesma fila
        event.register(TravelTransferPayload.TYPE, (payload, context) -> TravelClientState.markPending(payload.x(), payload.y(), payload.z(),
                payload.yRot(), payload.xRot()));
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        TravelClientState.reset();
    }
}
