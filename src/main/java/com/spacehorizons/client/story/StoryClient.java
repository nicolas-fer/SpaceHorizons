package com.spacehorizons.client.story;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.story.DataTerminalBlock;
import com.spacehorizons.story.JournalEntryItem;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** O lado do cliente da trama: liga a abertura da tela de leitura no diário e no terminal (os dois só chamam um gancho; um servidor dedicado nunca o liga). */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class StoryClient {
    private StoryClient() {
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            JournalEntryItem.CLIENT_OPEN = id -> Minecraft.getInstance().gui.setScreen(new StoryScreen(id));
            DataTerminalBlock.CLIENT_OPEN = id -> Minecraft.getInstance().gui.setScreen(new StoryScreen(id));
        });
    }
}
