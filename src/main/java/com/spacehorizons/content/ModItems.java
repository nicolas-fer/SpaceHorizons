package com.spacehorizons.content;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.survival.fuel.FuelCanisterItem;

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

    /**
     * Galão de combustível: 1000 mB num data component. Não empilha: cada galão tem a sua quantidade, e empilhar
     * exigiria que só galões iguais (todos vazios ou todos com a mesma quantidade) se juntassem, o que atrapalha
     * mais do que ajuda (a barra de preenchimento também só faz sentido para um item por slot).
     */
    public static final DeferredItem<Item> FUEL_CANISTER = ITEMS.registerItem(ContentIds.FUEL_CANISTER,
            FuelCanisterItem::new, props -> props.stacksTo(1));

    private ModItems() {
    }
}
