package com.spacehorizons.rocket;

import java.util.function.Consumer;

import com.spacehorizons.content.ModDataComponents;
import com.spacehorizons.content.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/** Item do tanque de foguete: o tooltip mostra o combustível que ele guarda (componente {@code rocket_tank_mb}). */
public class RocketFuelTankItem extends BlockItem {
    public RocketFuelTankItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** Combustível guardado no item, em mB (zero se o componente não existe). */
    public static int getFuel(ItemStack stack) {
        return RocketFuel.clamp(stack.getOrDefault(ModDataComponents.ROCKET_TANK_MB.get(), 0));
    }

    /** Tanque cheio (para a aba do criativo e para testes). */
    public static ItemStack fullStack() {
        ItemStack stack = new ItemStack(ModItems.ROCKET_FUEL_TANK.get());
        stack.set(ModDataComponents.ROCKET_TANK_MB.get(), RocketSpecs.TANK_CAPACITY_MB);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable("tooltip.spacehorizons.rocket_fuel_tank", getFuel(stack), RocketSpecs.TANK_CAPACITY_MB)
                .withStyle(ChatFormatting.GRAY));
    }
}
