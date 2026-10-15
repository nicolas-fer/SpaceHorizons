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

    public static final DeferredItem<Item> STEEL_BLEND = ITEMS.registerSimpleItem(ContentIds.STEEL_BLEND);
    public static final DeferredItem<Item> STEEL_INGOT = ITEMS.registerSimpleItem(ContentIds.STEEL_INGOT);
    public static final DeferredItem<Item> STEEL_NUGGET = ITEMS.registerSimpleItem(ContentIds.STEEL_NUGGET);
    public static final DeferredItem<Item> STEEL_PLATE = ITEMS.registerSimpleItem(ContentIds.STEEL_PLATE);
    public static final DeferredItem<Item> BASIC_CIRCUIT = ITEMS.registerSimpleItem(ContentIds.BASIC_CIRCUIT);

    /** Martelo de forja: 64 usos (cada receita que o usa gasta 1). */
    public static final DeferredItem<ForgeHammerItem> FORGE_HAMMER = ITEMS.registerItem(ContentIds.FORGE_HAMMER,
            ForgeHammerItem::new, props -> props.durability(64));

    private ModItems() {
    }
}
