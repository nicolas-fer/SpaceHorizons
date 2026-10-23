package com.spacehorizons.ship;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Ligações das naves com o NeoForge (por evento, registradas sozinhas pelo {@link EventBusSubscriber}): os comandos
 * {@code /spacehorizons ship ...}, a manutenção dos tickets de chunk (a cada tick do servidor, ver {@link ShipChunkTickets#tick}), o voo
 * das naves ({@link ShipFlightTicker}) e os pacotes do piloto e do estado virtual.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID)
public final class ShipSetup {
    private ShipSetup() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        ShipCommands.register(event.getDispatcher());
    }

    /** O comando do piloto (cliente para servidor) e o estado virtual da nave (servidor para os clientes dentro dela). */
    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(ShipControlPayload.TYPE, ShipControlPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                ShipPilots.onControl(player, payload.control());
            }
        });
        // o handler do cliente fica em client/ship (RegisterClientPayloadHandlersEvent)
        event.registrar("1").playToClient(ShipStatePayload.TYPE, ShipStatePayload.CODEC);
        // a âncora no ar: a tecla do piloto (cliente para servidor) e o estado para o painel (o handler do cliente fica em client/ship: AnchorClient)
        event.registrar("1").playToServer(AnchorRequestPayload.TYPE, AnchorRequestPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                ShipAnchors.onPilotRequest(player);
            }
        });
        event.registrar("1").playToClient(AnchorStatusPayload.TYPE, AnchorStatusPayload.CODEC);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        ShipChunkTickets.tick(event.getServer());
        ShipAnchors.tick(event.getServer());
        ShipConversions.tick(event.getServer());
        ShipCensusService.tick(event.getServer());
        ShipFlightTicker.tick(event.getServer());
    }

    /** O mundo abriu: carrega as naves ancoradas no ar (o índice dos ticks de bloco precisa delas desde o primeiro tick). */
    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        ShipAnchors.onServerStarted(event.getServer());
    }

    /** Mobs não nascem numa nave ancorada no ar nem no teto dela (spike S6). */
    @SubscribeEvent
    static void onSpawnCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        ShipAnchors.onSpawnCheck(event);
    }

    /** O servidor vai parar: grava o estado de voo das naves antes de salvar o mundo. */
    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        ShipFlightTicker.flush(event.getServer());
    }

    /** Os tickets morrem com o servidor: esquece o estado (o mundo seguinte, no mesmo processo, começa limpo). */
    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ShipChunkTickets.reset();
        ShipConversions.reset();
        ShipAnchors.reset();
        ShipCensusService.reset();
        ShipFlightTicker.reset();
    }
}
