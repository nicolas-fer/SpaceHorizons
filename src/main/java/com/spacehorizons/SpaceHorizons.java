package com.spacehorizons;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.spacehorizons.content.Content;
import com.spacehorizons.dh.DhIntegration;
import com.spacehorizons.worldgen.WorldgenSetup;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

@Mod(SpaceHorizons.MODID)
public class SpaceHorizons {
    public static final String MODID = "spacehorizons";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SpaceHorizons(IEventBus modEventBus, ModContainer modContainer) {
        Content.init(modEventBus);
        WorldgenSetup.init(modEventBus);
        com.spacehorizons.survival.oxygen.OxygenSetup.init(modEventBus);
        // DH é opcional: sem ele o mod funciona, só sem LODs
        if (ModList.get().isLoaded("distanthorizons")) {
            DhIntegration.init();
        }
    }
}
