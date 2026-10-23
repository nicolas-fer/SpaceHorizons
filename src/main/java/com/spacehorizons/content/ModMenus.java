package com.spacehorizons.content;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.moonbase.machine.MachineKind;
import com.spacehorizons.moonbase.machine.MoonBlock;
import com.spacehorizons.moonbase.machine.MoonMachineMenu;
import com.spacehorizons.survival.compressor.CompressorMenu;
import com.spacehorizons.survival.fuel.RefineryMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Tipos de menu (container) do mod. */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, SpaceHorizons.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<RefineryMenu>> REFINERY = MENUS.register(ContentIds.REFINERY,
            () -> IMenuTypeExtension.create((windowId, inventory, extraData) -> new RefineryMenu(windowId, inventory)));

    public static final DeferredHolder<MenuType<?>, MenuType<CompressorMenu>> OXYGEN_COMPRESSOR = MENUS.register(
            ContentIds.OXYGEN_COMPRESSOR,
            () -> IMenuTypeExtension.create((windowId, inventory, extraData) -> new CompressorMenu(windowId, inventory)));

    /** Menu das máquinas da base lunar; o tipo vem da posição do bloco que o servidor manda ao abrir. */
    public static final DeferredHolder<MenuType<?>, MenuType<MoonMachineMenu>> MOON_MACHINE = MENUS.register("moon_machine",
            () -> IMenuTypeExtension.create((windowId, inventory, extraData) -> {
                MachineKind kind = inventory.player.level().getBlockState(extraData.readBlockPos()).getBlock() instanceof MoonBlock block
                        ? block.kind() : MachineKind.OXYGEN_GENERATOR;
                return new MoonMachineMenu(windowId, inventory, kind);
            }));

    private ModMenus() {
    }
}
