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
            }
        });
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        RocketCommands.register(event.getDispatcher());
    }
}
