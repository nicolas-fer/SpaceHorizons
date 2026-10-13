package com.spacehorizons;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

// Não carrega em servidor dedicado: código de cliente é seguro aqui
@Mod(value = SpaceHorizons.MODID, dist = Dist.CLIENT)
public class SpaceHorizonsClient {
    public SpaceHorizonsClient(ModContainer container) {
    }
}
