package com.spacehorizons.content;

import net.neoforged.bus.api.IEventBus;

/** Ponto único de registro do conteúdo de jogo (itens, blocos, abas do criativo). */
public final class Content {
    private Content() {
    }

    public static void init(IEventBus modEventBus) {
        // Blocos primeiro: carregar ModBlocks registra os BlockItems em ModItems
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModBlockEntities.TYPES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModRecipes.TYPES.register(modEventBus);
        ModRecipes.SERIALIZERS.register(modEventBus);
        ModRecipes.CATEGORIES.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
    }
}
