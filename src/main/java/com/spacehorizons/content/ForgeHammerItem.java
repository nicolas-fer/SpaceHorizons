package com.spacehorizons.content;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * Martelo de forja: nas receitas de crafting ele não é consumido; volta à grade com 1 de dano e some
 * quando esse dano o quebraria.
 *
 * <p>Por que sobrescrever {@code getCraftingRemainder}: o {@code craftRemainder} das propriedades é um molde fixo
 * (sempre o item novo, sem dano), então o martelo nunca gastaria. O gancho do NeoForge recebe a instância
 * (com os componentes, inclusive o dano) e permite devolver um molde com dano+1, sem receita customizada:
 * qualquer receita comum (shaped/shapeless) que use o martelo como ingrediente já o devolve gasto.
 */
public class ForgeHammerItem extends Item {
    public ForgeHammerItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable ItemStackTemplate getCraftingRemainder(ItemInstance instance) {
        int dano = instance.getOrDefault(DataComponents.DAMAGE, 0);
        int maximo = instance.getOrDefault(DataComponents.MAX_DAMAGE, 0);
        if (maximo <= 0 || dano + 1 >= maximo) {
            return null; // último uso: o martelo quebra
        }
        // Copia preservando os demais componentes (encantamentos, nome etc.) e aplica o dano
        ItemStack copia = instance instanceof ItemStack pilha
                ? pilha.copyWithCount(1)
                : ((ItemStackTemplate) instance).create();
        copia.setDamageValue(dano + 1);
        return ItemStackTemplate.fromNonEmptyStack(copia);
    }
}
