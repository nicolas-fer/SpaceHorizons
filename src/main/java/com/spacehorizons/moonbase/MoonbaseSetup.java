package com.spacehorizons.moonbase;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModBlockEntities;
import com.spacehorizons.moonbase.room.PressurizedRooms;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

/**
 * Ligações da base lunar com o NeoForge (por evento, registradas sozinhas pelo {@link EventBusSubscriber}): a capability de item
 * (funil e automação enxergam os slots por face) e a limpeza das salas pressurizadas quando o servidor para.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID)
public final class MoonbaseSetup {
    private MoonbaseSetup() {
    }

    @SubscribeEvent
    static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.MOON.get(), WorldlyContainerWrapper::new);
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        PressurizedRooms.clear();
    }
}
