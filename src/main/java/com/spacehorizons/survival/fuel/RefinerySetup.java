package com.spacehorizons.survival.fuel;

import java.util.Optional;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModBlockEntities;
import com.spacehorizons.content.ModRecipes;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterRecipePropertiesEvent;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

/**
 * Ligações da refinaria com o NeoForge (todas por evento, registradas sozinhas pelo {@link EventBusSubscriber}):
 * a capability de item (funil e automação), o conjunto de entradas válidas do slot de entrada e o envio das receitas
 * de refino ao cliente (preciso para um mod de receitas como o JEI mostrá-las).
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID)
public final class RefinerySetup {
    private RefinerySetup() {
    }

    /** Funil e automação enxergam os slots por lado como numa fornalha (o block entity é um WorldlyContainer). */
    @SubscribeEvent
    static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.REFINERY.get(), WorldlyContainerWrapper::new);
    }

    /** Ingredientes de todas as receitas de refino: valida o slot de entrada no cliente e no servidor. */
    @SubscribeEvent
    static void onRegisterRecipeProperties(RegisterRecipePropertiesEvent event) {
        event.register(RefineryMenu.INPUT_SET,
                recipe -> recipe instanceof RefiningRecipe refining ? Optional.of(refining.ingredient()) : Optional.empty());
    }

    /** Manda as receitas de refino completas ao cliente (para o JEI e para telas que as mostrem). */
    @SubscribeEvent
    static void onDatapackSync(OnDatapackSyncEvent event) {
        event.sendRecipes(ModRecipes.REFINING.get());
    }
}
