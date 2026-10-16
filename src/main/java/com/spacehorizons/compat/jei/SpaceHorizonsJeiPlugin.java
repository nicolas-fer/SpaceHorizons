package com.spacehorizons.compat.jei;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.refinery.ClientRefiningRecipes;
import com.spacehorizons.client.refinery.RefineryScreen;
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.content.ModDataComponents;
import com.spacehorizons.content.ModItems;
import com.spacehorizons.content.ModMenus;
import com.spacehorizons.survival.fuel.RefineryBlockEntity;
import com.spacehorizons.survival.fuel.RefineryMenu;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Integração opcional com o JEI. Só o JEI carrega esta classe (pela anotação {@link JeiPlugin}); o resto do mod não
 * referencia nada deste pacote (a não ser o autoteste de desenvolvimento, só quando o JEI está presente), então sem o
 * JEI instalado nada daqui é tocado.
 *
 * Registra a categoria "Refinaria" (uma entrada por receita {@code spacehorizons:refining}), a refinaria como
 * catalisador, o "+" do JEI preenchendo o slot de entrada, a seta da tela da refinaria como atalho para a categoria e
 * as informações do galão de combustível e do martelo de forja.
 */
@JeiPlugin
public class SpaceHorizonsJeiPlugin implements IModPlugin {
    /** Último runtime do JEI, para o autoteste abrir telas do JEI sem depender de teclas. */
    private static @Nullable IJeiRuntime runtime;

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "jei_plugin");
    }

    /** Galão vazio e galão cheio (aba criativa) são o mesmo item com {@code fuel_mb} diferente: o JEI os lista separados. */
    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerFromDataComponentTypes(ModItems.FUEL_CANISTER.get(), ModDataComponents.FUEL_MB.get());
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new RefiningCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    /**
     * Receitas de refino: vêm do mapa que o cliente recebeu do servidor ({@link ClientRefiningRecipes}); não existe
     * RecipeManager com receitas no cliente do 26.3, e é assim que o próprio JEI lê as receitas dos tipos de mod.
     */
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(RefiningCategory.RECIPE_TYPE, ClientRefiningRecipes.all());

        registration.addIngredientInfo(ModItems.FUEL_CANISTER.get(),
                Component.translatable("jei.spacehorizons.info.fuel_canister"));
        registration.addIngredientInfo(ModItems.FORGE_HAMMER.get(),
                Component.translatable("jei.spacehorizons.info.forge_hammer"));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(RefiningCategory.RECIPE_TYPE, ModBlocks.REFINERY.get());
    }

    /**
     * "+" do JEI: leva o item da receita para o slot de entrada da refinaria (slot 0); o inventário do jogador são
     * os 36 slots depois dos três da refinaria.
     */
    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(RefineryMenu.class, ModMenus.REFINERY.get(), RefiningCategory.RECIPE_TYPE,
                RefineryBlockEntity.SLOT_INPUT, 1, RefineryBlockEntity.SLOT_COUNT, 36);
    }

    /** Clicar na seta de progresso da tela da refinaria abre a categoria. */
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addRecipeClickArea(RefineryScreen.class, RefineryScreen.ARROW_X, RefineryScreen.ARROW_Y,
                RefineryScreen.ARROW_W, RefineryScreen.ARROW_H, RefiningCategory.RECIPE_TYPE);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    /** Runtime atual do JEI, ou {@code null} se ainda não iniciou. Usado pelo autoteste. */
    public static @Nullable IJeiRuntime runtime() {
        return runtime;
    }
}
