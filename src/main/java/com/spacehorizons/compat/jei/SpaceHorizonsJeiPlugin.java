package com.spacehorizons.compat.jei;

import java.util.List;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.compressor.CompressorScreen;
import com.spacehorizons.client.refinery.ClientRefiningRecipes;
import com.spacehorizons.client.refinery.RefineryScreen;
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.content.ModDataComponents;
import com.spacehorizons.content.ModItems;
import com.spacehorizons.content.ModMenus;
import com.spacehorizons.survival.compressor.CompressorMenu;
import com.spacehorizons.survival.compressor.OxygenCompressorBlockEntity;
import com.spacehorizons.survival.fuel.RefineryBlockEntity;
import com.spacehorizons.survival.fuel.RefineryMenu;
import com.spacehorizons.survival.oxygen.OxygenTankItem;

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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Integração opcional com o JEI. Só o JEI carrega esta classe (pela anotação {@link JeiPlugin}); o resto do mod não
 * referencia nada deste pacote (a não ser o autoteste de desenvolvimento, só quando o JEI está presente), então sem o
 * JEI instalado nada daqui é tocado.
 *
 * Registra a categoria "Refinaria" (uma entrada por receita {@code spacehorizons:refining}), a refinaria como
 * catalisador, o "+" do JEI preenchendo o slot de entrada, a seta da tela da refinaria como atalho para a categoria e
 * as informações do galão de combustível e do martelo de forja. Idem para o compressor de oxigênio: categoria
 * "tanque vazio para tanque cheio" (uma entrada por tamanho de tanque), catalisador, "+" para o slot do tanque, seta
 * da tela e a informação dos tanques.
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
        // idem para os tanques de oxigênio: vazio e cheio (aba criativa) são subtipos diferentes
        registration.registerFromDataComponentTypes(ModItems.OXYGEN_TANK_SMALL.get(), ModDataComponents.OXYGEN.get());
        registration.registerFromDataComponentTypes(ModItems.OXYGEN_TANK_LARGE.get(), ModDataComponents.OXYGEN.get());
        // a planta em branco e as embutidas (aba criativa) também
        registration.registerFromDataComponentTypes(ModItems.ROCKET_BLUEPRINT.get(), ModDataComponents.ROCKET_BLUEPRINT.get());
        // Tier 2: os galões dos outros combustíveis (vazio e cheio)
        registration.registerFromDataComponentTypes(ModItems.HYDROGEN_CANISTER.get(), ModDataComponents.FUEL_MB.get());
        registration.registerFromDataComponentTypes(ModItems.XENON_CANISTER.get(), ModDataComponents.FUEL_MB.get());
        registration.registerFromDataComponentTypes(ModItems.HELIUM3_CANISTER.get(), ModDataComponents.FUEL_MB.get());
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new RefiningCategory(registration.getJeiHelpers().getGuiHelper()),
                new CompressionCategory(registration.getJeiHelpers().getGuiHelper()));
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
        // blocos das naves (fase 1b do plano das naves)
        registration.addIngredientInfo(ModBlocks.SHIP_BRIDGE.get(), Component.translatable("jei.spacehorizons.info.ship_bridge"));
        registration.addIngredientInfo(ModBlocks.SHIP_IMPULSE_ENGINE.get(), Component.translatable("jei.spacehorizons.info.ship_impulse_engine"));
        registration.addIngredientInfo(ModBlocks.SHIP_WINDOW.get(), Component.translatable("jei.spacehorizons.info.ship_window"));
        registration.addIngredientInfo(ModBlocks.SHIP_SEAT.get(), Component.translatable("jei.spacehorizons.info.ship_seat"));
        registration.addIngredientInfo(ModBlocks.SHIP_AIRLOCK.get(), Component.translatable("jei.spacehorizons.info.ship_airlock"));
        registration.addIngredientInfo(ModBlocks.ANTIGRAVITY_GENERATOR.get(), Component.translatable("jei.spacehorizons.info.antigravity_generator"));

        // Tier 2: de onde vêm os combustíveis, o que cada motor queima e como se carregam (docs/PLANO_TIERS.md, "Tier 2: conteúdo")
        for (Item item : List.of(ModBlocks.NUCLEAR_ENGINE.get().asItem(), ModBlocks.ION_ENGINE.get().asItem(), ModBlocks.FUSION_ENGINE.get().asItem(),
                ModBlocks.HYDROGEN_TANK.get().asItem(), ModBlocks.XENON_TANK.get().asItem(), ModBlocks.RTG_GENERATOR.get().asItem(),
                ModBlocks.MARS_REGOLITH.get().asItem(), ModItems.NUCLEAR_FUEL_ROD.get(), ModItems.RARE_EARTH_DUST.get())) {
            registration.addIngredientInfo(item, Component.translatable("jei.spacehorizons.info."
                    + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath()));
        }
        for (Item canister : List.of(ModItems.HYDROGEN_CANISTER.get(), ModItems.XENON_CANISTER.get(), ModItems.HELIUM3_CANISTER.get())) {
            registration.addItemStackInfo(List.of(new ItemStack(canister), com.spacehorizons.survival.fuel.FuelCanisterItem.fullStack(canister)),
                    Component.translatable("jei.spacehorizons.info." + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(canister).getPath()));
        }

        // compressor: um tanque vazio virando um cheio, por tamanho de tanque; e a informação curta dos tanques
        Item small = ModItems.OXYGEN_TANK_SMALL.get();
        Item large = ModItems.OXYGEN_TANK_LARGE.get();
        registration.addRecipes(CompressionCategory.RECIPE_TYPE,
                List.of(CompressionCategory.Entry.of(small), CompressionCategory.Entry.of(large)));
        // a informação vale para o tanque vazio e para o cheio (são subtipos diferentes para o JEI)
        registration.addItemStackInfo(List.of(new ItemStack(small), OxygenTankItem.fullStack(small)),
                Component.translatable("jei.spacehorizons.info.oxygen_tank_small"));
        registration.addItemStackInfo(List.of(new ItemStack(large), OxygenTankItem.fullStack(large)),
                Component.translatable("jei.spacehorizons.info.oxygen_tank_large"));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(RefiningCategory.RECIPE_TYPE, ModBlocks.REFINERY.get());
        registration.addCraftingStation(CompressionCategory.RECIPE_TYPE, ModBlocks.OXYGEN_COMPRESSOR.get());
    }

    /**
     * "+" do JEI: leva o item da receita para o slot de entrada da refinaria (slot 0); o inventário do jogador são
     * os 36 slots depois dos três da refinaria.
     */
    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(RefineryMenu.class, ModMenus.REFINERY.get(), RefiningCategory.RECIPE_TYPE,
                RefineryBlockEntity.SLOT_INPUT, 1, RefineryBlockEntity.SLOT_COUNT, 36);
        // "+" na categoria do compressor: leva um tanque vazio do inventário para o slot do tanque
        registration.addRecipeTransferHandler(CompressorMenu.class, ModMenus.OXYGEN_COMPRESSOR.get(), CompressionCategory.RECIPE_TYPE,
                OxygenCompressorBlockEntity.SLOT_TANK, 1, OxygenCompressorBlockEntity.SLOT_COUNT, 36);
    }

    /** Clicar na seta de progresso da tela da refinaria (ou do compressor) abre a categoria. */
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addRecipeClickArea(RefineryScreen.class, RefineryScreen.ARROW_X, RefineryScreen.ARROW_Y,
                RefineryScreen.ARROW_W, RefineryScreen.ARROW_H, RefiningCategory.RECIPE_TYPE);
        registration.addRecipeClickArea(CompressorScreen.class, CompressorScreen.ARROW_X, CompressorScreen.ARROW_Y,
                CompressorScreen.ARROW_W, CompressorScreen.ARROW_H, CompressionCategory.RECIPE_TYPE);
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
