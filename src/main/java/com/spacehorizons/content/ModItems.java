package com.spacehorizons.content;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.RocketFuelTankItem;
import com.spacehorizons.rocket.blueprint.RocketBlueprintItem;
import com.spacehorizons.survival.fuel.FuelCanisterItem;
import com.spacehorizons.survival.oxygen.OxygenAmounts;
import com.spacehorizons.survival.oxygen.OxygenTankItem;
import com.spacehorizons.survival.suit.SpaceSuit;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorType;
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

    /**
     * Galão de combustível: 1000 mB num data component. Não empilha: cada galão tem a sua quantidade, e empilhar
     * exigiria que só galões iguais (todos vazios ou todos com a mesma quantidade) se juntassem, o que atrapalha
     * mais do que ajuda (a barra de preenchimento também só faz sentido para um item por slot).
     */
    public static final DeferredItem<Item> FUEL_CANISTER = ITEMS.registerItem(ContentIds.FUEL_CANISTER,
            FuelCanisterItem::new, props -> props.stacksTo(1));

    /**
     * Tanques de oxigênio: recipientes pelo contrato de {@code OxygenStorage}, com a capacidade como componente padrão
     * (2.400 ticks = 2 min; 7.200 = 6 min). Não empilham: cada tanque tem a sua carga (o mesmo motivo do galão).
     */
    public static final DeferredItem<Item> OXYGEN_TANK_SMALL = ITEMS.registerItem(ContentIds.OXYGEN_TANK_SMALL,
            OxygenTankItem::new, props -> props.stacksTo(1)
                    .component(ModDataComponents.OXYGEN_CAPACITY.get(), OxygenAmounts.SMALL_TANK_CAPACITY));
    public static final DeferredItem<Item> OXYGEN_TANK_LARGE = ITEMS.registerItem(ContentIds.OXYGEN_TANK_LARGE,
            OxygenTankItem::new, props -> props.stacksTo(1)
                    .component(ModDataComponents.OXYGEN_CAPACITY.get(), OxygenAmounts.LARGE_TANK_CAPACITY));

    // Traje espacial: material próprio (SpaceSuit); a lógica de respirar e do frio está em survival.suit.SuitSetup
    public static final DeferredItem<Item> SPACE_HELMET = ITEMS.registerItem(ContentIds.SPACE_HELMET,
            Item::new, props -> props.humanoidArmor(SpaceSuit.material(), ArmorType.HELMET));
    public static final DeferredItem<Item> SPACE_CHESTPLATE = ITEMS.registerItem(ContentIds.SPACE_CHESTPLATE,
            Item::new, props -> props.humanoidArmor(SpaceSuit.material(), ArmorType.CHESTPLATE));
    public static final DeferredItem<Item> SPACE_LEGGINGS = ITEMS.registerItem(ContentIds.SPACE_LEGGINGS,
            Item::new, props -> props.humanoidArmor(SpaceSuit.material(), ArmorType.LEGGINGS));
    public static final DeferredItem<Item> SPACE_BOOTS = ITEMS.registerItem(ContentIds.SPACE_BOOTS,
            Item::new, props -> props.humanoidArmor(SpaceSuit.material(), ArmorType.BOOTS));

    /**
     * BlockItem do tanque de foguete, com tooltip da carga. Registrado aqui (e não em ModBlocks, que registra os
     * BlockItems simples): carregar ModBlocks, que vem antes na ordem de registro, não depende deste campo.
     */
    public static final DeferredItem<RocketFuelTankItem> ROCKET_FUEL_TANK = ITEMS.registerItem(ContentIds.ROCKET_FUEL_TANK,
            props -> new RocketFuelTankItem(ModBlocks.ROCKET_FUEL_TANK.get(), props), () -> new Item.Properties().useBlockDescriptionPrefix());

    /** Planta de montagem (em branco ou com um projeto no componente {@code rocket_blueprint}); não empilha: cada uma tem o seu projeto. */
    public static final DeferredItem<RocketBlueprintItem> ROCKET_BLUEPRINT = ITEMS.registerItem(ContentIds.ROCKET_BLUEPRINT,
            RocketBlueprintItem::new, props -> props.stacksTo(1));

    private ModItems() {
    }
}
