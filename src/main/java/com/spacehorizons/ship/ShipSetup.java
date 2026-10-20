package com.spacehorizons.ship;

import com.spacehorizons.SpaceHorizons;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Ligações das naves com o NeoForge (por evento, registradas sozinhas pelo {@link EventBusSubscriber}): os comandos
 * {@code /spacehorizons ship ...} e a manutenção dos tickets de chunk (a cada tick do servidor, ver {@link ShipChunkTickets#tick}).
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID)
public final class ShipSetup {
    private ShipSetup() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        ShipCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        ShipChunkTickets.tick(event.getServer());
        ShipConversions.tick(event.getServer());
    }

    /** Os tickets morrem com o servidor: esquece o estado (o mundo seguinte, no mesmo processo, começa limpo). */
    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ShipChunkTickets.reset();
        ShipConversions.reset();
    }
}
