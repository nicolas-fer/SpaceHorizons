package com.spacehorizons.rocket;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Ligações do foguete com o NeoForge (por evento, registradas sozinhas pelo {@link EventBusSubscriber}): o pacote de
 * controle do piloto (cliente para servidor) e os comandos de teste {@code /spacehorizons rocket ...}.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID)
public final class RocketSetup {
    /** Quantos comandos de voo foram recusados por não virem do piloto (diagnóstico: o log só fala a cada 100). */
    private static final java.util.concurrent.atomic.AtomicInteger REJECTED = new java.util.concurrent.atomic.AtomicInteger();

    private RocketSetup() {
    }

    /**
     * Registra o comando de voo. O handler só aceita o piloto do foguete (o jogador que é o passageiro de controle) e
     * guarda o comando saneado; a física de voo (etapa 3b) o lê depois.
     */
    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(RocketControlPayload.TYPE, RocketControlPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof RocketEntity rocket
                    && rocket.getControllingPassenger() == player) {
                rocket.applyControl(payload);
            } else if (REJECTED.getAndIncrement() % 100 == 0) {
                SpaceHorizons.LOGGER.warn("Comando de voo recusado ({} até agora): o jogador não é o piloto do foguete (veículo={}, piloto do veículo={})",
                        REJECTED.get(), context.player().getVehicle(),
                        context.player().getVehicle() instanceof RocketEntity r ? r.getControllingPassenger() : null);
            }
        });
        // telemetria do voo para o piloto (o handler do cliente fica em client/rocket: RegisterClientPayloadHandlersEvent)
        event.registrar("1").playToClient(RocketTelemetryPayload.TYPE, RocketTelemetryPayload.CODEC);
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        RocketCommands.register(event.getDispatcher());
    }
}
