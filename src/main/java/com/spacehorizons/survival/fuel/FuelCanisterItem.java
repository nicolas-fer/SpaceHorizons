package com.spacehorizons.survival.fuel;

import java.util.function.Consumer;

import com.spacehorizons.content.ModDataComponents;
import com.spacehorizons.content.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Galão de combustível de foguete: guarda a quantidade (mB) no data component {@code spacehorizons:fuel_mb}.
 * Barra de preenchimento na hotbar (escala da durabilidade, cor âmbar) e tooltip "N / 1000 mB".
 * Não empilha (veja o registro em {@link ModItems}).
 */
public class FuelCanisterItem extends Item {
    /** Âmbar do combustível. */
    private static final int BAR_COLOR = 0xFFE0A030;

    public FuelCanisterItem(Properties properties) {
        super(properties);
    }

    /** Quantidade guardada em mB (0 se o componente não existe). */
    public static int getFuel(ItemStack stack) {
        return CanisterFuel.clamp(stack.getOrDefault(ModDataComponents.FUEL_MB.get(), 0));
    }

    /** Guarda a quantidade (ajustada aos limites); zero remove o componente, para o galão vazio ser igual a qualquer outro. */
    public static void setFuel(ItemStack stack, int mb) {
        int value = CanisterFuel.clamp(mb);
        if (value == 0) {
            stack.remove(ModDataComponents.FUEL_MB.get());
        } else {
            stack.set(ModDataComponents.FUEL_MB.get(), value);
        }
    }

    /** Galão cheio (para a aba do criativo). */
    public static ItemStack fullStack() {
        ItemStack stack = new ItemStack(ModItems.FUEL_CANISTER.get());
        setFuel(stack, CanisterFuel.CAPACITY);
        return stack;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getFuel(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return CanisterFuel.barWidth(getFuel(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
            TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.spacehorizons.fuel_canister", getFuel(stack), CanisterFuel.CAPACITY)
                .withStyle(ChatFormatting.GRAY));
    }
}
