package com.spacehorizons.content;

import java.util.function.Supplier;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.survival.fuel.FuelCanisterItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Abas do modo criativo do mod. */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SpaceHorizons.MODID);

    /** Aba "Space Horizons": lista automaticamente todos os itens registrados em {@link ModItems}. */
    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + SpaceHorizons.MODID + ".main"))
            .icon(() -> new ItemStack(ModItems.STEEL_INGOT.get()))
            .displayItems((parameters, output) -> {
                for (DeferredHolder<Item, ? extends Item> item : ModItems.ITEMS.getEntries()) {
                    output.accept(item.get());
                }
                // além do galão vazio (acima), um galão cheio para testes
                output.accept(FuelCanisterItem.fullStack());
            })
            .build());

    private ModCreativeTabs() {
    }
}
