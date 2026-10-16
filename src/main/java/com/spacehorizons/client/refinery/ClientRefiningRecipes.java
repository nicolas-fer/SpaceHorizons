package com.spacehorizons.client.refinery;

import java.util.List;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModRecipes;
import com.spacehorizons.survival.fuel.RefiningRecipe;

import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;

/**
 * Guarda no cliente as receitas de refino recebidas do servidor. No 26.3 o cliente não tem um RecipeManager com as
 * receitas: só recebe, por {@link RecipesReceivedEvent}, os tipos que o servidor mandou ({@code sendRecipes} em
 * {@link com.spacehorizons.survival.fuel.RefinerySetup}). O JEI faz o mesmo para as receitas dele; aqui o
 * mapa fica à mão para a integração com o JEI e para qualquer tela que precise listar as receitas.
 *
 * Esta classe não toca em nada do JEI: carrega sempre, com ou sem o mod instalado.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class ClientRefiningRecipes {
    private static RecipeMap recipes = RecipeMap.EMPTY;

    private ClientRefiningRecipes() {
    }

    @SubscribeEvent
    static void onRecipesReceived(RecipesReceivedEvent event) {
        recipes = event.getRecipeMap();
    }

    /** Receitas de refino que o servidor mandou (vazio fora de um mundo). */
    public static List<RecipeHolder<RefiningRecipe>> all() {
        return List.copyOf(recipes.byType(ModRecipes.REFINING.get()));
    }
}
