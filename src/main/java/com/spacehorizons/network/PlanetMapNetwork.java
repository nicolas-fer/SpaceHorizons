package com.spacehorizons.network;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Registro do payload do mapa do planeta (servidor para cliente) e o envio em pedaços. O handler do cliente fica em
 * {@link PlanetMapClient}. Registrada sozinha pelo {@link EventBusSubscriber}; não precisa de chamada no construtor do mod.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID)
public final class PlanetMapNetwork {
    private PlanetMapNetwork() {
    }

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(PlanetMapPayload.TYPE, PlanetMapPayload.CODEC);
    }

    /** Envia o mapa serializado do planeta (id do planeta + bytes de PlanetMap.serialize) ao jogador. */
    public static void send(ServerPlayer player, String planetId, byte[] data) {
        for (PlanetMapPayload piece : PlanetMapChunks.split(planetId, data)) {
            PacketDistributor.sendToPlayer(player, piece);
        }
    }
}
