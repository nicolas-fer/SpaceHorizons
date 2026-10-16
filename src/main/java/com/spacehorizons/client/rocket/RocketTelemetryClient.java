package com.spacehorizons.client.rocket;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.RocketTelemetryPayload;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

import org.jspecify.annotations.Nullable;

/** Guarda a última telemetria recebida do servidor (o painel do foguete lê daqui). Só no cliente. */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class RocketTelemetryClient {
    private static volatile @Nullable RocketTelemetryPayload last;
    private static volatile long receivedAtMillis;

    private RocketTelemetryClient() {
    }

    @SubscribeEvent
    static void onRegisterHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(RocketTelemetryPayload.TYPE, (payload, context) -> {
            last = payload;
            receivedAtMillis = System.currentTimeMillis();
        });
    }

    /** A telemetria mais recente, ou null se nunca chegou ou está velha (mais de 2 s sem pacote). */
    public static @Nullable RocketTelemetryPayload current() {
        RocketTelemetryPayload p = last;
        return p != null && System.currentTimeMillis() - receivedAtMillis < 2000 ? p : null;
    }
}
