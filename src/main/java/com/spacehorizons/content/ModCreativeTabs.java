package com.spacehorizons.content;

import java.util.function.Supplier;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.RocketFuelTankItem;
import com.spacehorizons.rocket.blueprint.Blueprints;
import com.spacehorizons.rocket.blueprint.RocketBlueprintItem;
import com.spacehorizons.survival.fuel.FuelCanisterItem;
import com.spacehorizons.survival.oxygen.OxygenTankItem;

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
                // idem para os tanques de oxigênio: o vazio vem da lista acima, o cheio daqui
                output.accept(OxygenTankItem.fullStack(ModItems.OXYGEN_TANK_SMALL.get()));
                output.accept(OxygenTankItem.fullStack(ModItems.OXYGEN_TANK_LARGE.get()));
                // e o tanque de foguete: o vazio vem da lista acima, o cheio (4.000 mB) daqui
                output.accept(RocketFuelTankItem.fullStack());
                // as plantas embutidas (a em branco vem da lista acima); a lunar só aqui e no menu do criativo até a história existir
                for (Blueprints.Entry entry : Blueprints.ALL) {
                    output.accept(RocketBlueprintItem.stackOf(entry.blueprint()));
                }
                // Tier 2: os galões e os tanques cheios
                output.accept(FuelCanisterItem.fullStack(ModItems.HYDROGEN_CANISTER.get()));
                output.accept(FuelCanisterItem.fullStack(ModItems.XENON_CANISTER.get()));
                output.accept(FuelCanisterItem.fullStack(ModItems.HELIUM3_CANISTER.get()));
                output.accept(RocketFuelTankItem.fullStack(ModItems.HYDROGEN_TANK.get()));
                output.accept(RocketFuelTankItem.fullStack(ModItems.XENON_TANK.get()));
            })
            .build());

    private ModCreativeTabs() {
    }
}
