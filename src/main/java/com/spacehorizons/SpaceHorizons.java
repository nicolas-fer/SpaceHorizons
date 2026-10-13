package com.spacehorizons;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(SpaceHorizons.MODID)
public class SpaceHorizons {
    public static final String MODID = "spacehorizons";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SpaceHorizons(IEventBus modEventBus, ModContainer modContainer) {
    }
}
