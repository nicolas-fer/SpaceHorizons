package com.spacehorizons.compat.jei;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.content.ModItems;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * Ajudas do autoteste ({@code -PshOnly=jei}) que abrem telas do JEI pela API em tempo de execução. Só é carregada
 * pelo autoteste quando o JEI está presente; nunca pelo jogo normal.
 */
public final class JeiDevtest {
    private JeiDevtest() {
    }

    /** O JEI já iniciou (runtime disponível)? */
    public static boolean ready() {
        return SpaceHorizonsJeiPlugin.runtime() != null;
    }

    /** Abre a tela de receitas do JEI com foco no item: papel de estação (clique na refinaria), entrada ou saída. */
    public static void show(String papel, ItemLike item) {
        IJeiRuntime runtime = SpaceHorizonsJeiPlugin.runtime();
        if (runtime == null) {
            SpaceHorizons.LOGGER.warn("[AUTOTEST] JEI sem runtime ao abrir {} de {}", papel, item);
            return;
        }
        RecipeIngredientRole role = switch (papel) {
            case "station" -> RecipeIngredientRole.CRAFTING_STATION;
            case "input" -> RecipeIngredientRole.INPUT;
            default -> RecipeIngredientRole.OUTPUT;
        };
        IFocus<ItemStack> focus = runtime.getJeiHelpers().getFocusFactory()
                .createFocus(role, VanillaTypes.ITEM_STACK, new ItemStack(item));
        runtime.getRecipesGui().show(focus);
        SpaceHorizons.LOGGER.info("[AUTOTEST] JEI aberto: {} de {}", papel, item.asItem());
    }

    /** Itens usados pelo cenário: evita que o AutoTest importe classes do mod só para isso. */
    public static ItemLike refinery() {
        return ModBlocks.REFINERY.get();
    }

    public static ItemLike coal() {
        return Items.COAL;
    }

    public static ItemLike steelIngot() {
        return ModItems.STEEL_INGOT.get();
    }

    public static ItemLike steelBlend() {
        return ModItems.STEEL_BLEND.get();
    }

    public static ItemLike canister() {
        return ModItems.FUEL_CANISTER.get();
    }

    public static ItemLike hammer() {
        return ModItems.FORGE_HAMMER.get();
    }

    public static ItemLike compressor() {
        return ModBlocks.OXYGEN_COMPRESSOR.get();
    }

    public static ItemLike smallTank() {
        return ModItems.OXYGEN_TANK_SMALL.get();
    }
}
