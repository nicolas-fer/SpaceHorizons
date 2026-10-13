package com.spacehorizons.space.travel;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.space.SolarSystem;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Cola dos eventos da viagem (servidor): registro do aviso de transferência, tick por jogador (piloto automático,
 * velocidade de voo e esfera de influência) e limpeza do estado. Registrada sozinha pelo {@link EventBusSubscriber};
 * não precisa de chamada no construtor do mod.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID)
public final class TravelServer {
    private TravelServer() {
    }

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        // sem handler aqui: o handler do cliente é registrado em client/travel (RegisterClientPayloadHandlersEvent)
        event.registrar("1").playToClient(TravelTransferPayload.TYPE, TravelTransferPayload.CODEC);
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlanetDefinition planet = Planets.forLevel(player.level()).orElse(null);
        SolarSystem.Body body = planet == null ? null : SolarSystem.bodyOf(planet).orElse(null);
        if (body == null) {
            Autopilot.forget(player.getUUID());
            FlightBoost.restore(player);
            return;
        }
        if (Autopilot.tick(player, body)) {
            return;
        }
        FlightBoost.update(player, planet);
        TravelTransfers.checkSphereOfInfluence(player, body);
    }

    @SubscribeEvent
    static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // ao sair, grava o jogador com a velocidade de voo padrão
            FlightBoost.restore(player);
            FlightBoost.forget(player.getUUID());
            Autopilot.forget(player.getUUID());
            TravelTransfers.forget(player.getUUID());
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        FlightBoost.clear();
        Autopilot.clear();
        TravelTransfers.clear();
    }
}
