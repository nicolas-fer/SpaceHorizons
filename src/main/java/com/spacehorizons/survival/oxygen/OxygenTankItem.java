package com.spacehorizons.survival.oxygen;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Tanque de oxigênio (pequeno e grande são o mesmo item com capacidades diferentes). É recipiente pelo contrato de
 * {@link OxygenStorage}: a capacidade vem do componente {@code spacehorizons:oxygen_capacity}, posto como padrão nas
 * Item.Properties (veja {@code ModItems}), e a carga fica em {@code spacehorizons:oxygen}.
 *
 * Barra de carga na hotbar (escala da durabilidade, azul claro) e tooltip "m:ss / m:ss" (tempo de respiração).
 * Não empilha: cada tanque tem a sua carga.
 */
public class OxygenTankItem extends Item {
    /** Azul claro do ar comprimido. */
    private static final int BAR_COLOR = 0xFF58B8F0;

    public OxygenTankItem(Properties properties) {
        super(properties);
    }

    /** Tanque cheio do item dado (para a aba do criativo e para testes). */
    public static ItemStack fullStack(Item tank) {
        ItemStack stack = new ItemStack(tank);
        OxygenStorage.set(stack, OxygenStorage.capacity(stack));
        return stack;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return OxygenStorage.get(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return OxygenAmounts.barWidth(OxygenStorage.get(stack), OxygenStorage.capacity(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
            TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.spacehorizons.oxygen_tank",
                OxygenAmounts.formatTime(OxygenStorage.get(stack)), OxygenAmounts.formatTime(OxygenStorage.capacity(stack)))
                .withStyle(ChatFormatting.GRAY));
    }
}
