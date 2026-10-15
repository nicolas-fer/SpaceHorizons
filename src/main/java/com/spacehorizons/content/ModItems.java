package com.spacehorizons.content;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registro de todos os itens do mod (inclui os BlockItems dos blocos de {@link ModBlocks}).
 * A aba do criativo lista tudo o que estiver aqui automaticamente.
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SpaceHorizons.MODID);

    /**
     * PROVISÓRIO: item de teste da fundação de conteúdo (etapa 0).
     * Será removido/substituído pelos itens reais na etapa 1.
     */
    public static final DeferredItem<Item> PLACEHOLDER_ITEM = ITEMS.registerSimpleItem(ContentIds.PLACEHOLDER_ITEM);

    private ModItems() {
    }
}
