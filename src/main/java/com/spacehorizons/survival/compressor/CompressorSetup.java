package com.spacehorizons.survival.compressor;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModBlockEntities;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

/**
 * Ligações do compressor de oxigênio com o NeoForge (por evento, registradas sozinhas pelo {@link EventBusSubscriber}):
 * a capability de item, para funil e automação enxergarem os slots por face.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID)
public final class CompressorSetup {
    private CompressorSetup() {
    }

    /** Funil e automação enxergam os slots por lado como numa fornalha (o block entity é um WorldlyContainer). */
    @SubscribeEvent
    static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.OXYGEN_COMPRESSOR.get(), WorldlyContainerWrapper::new);
    }
}
