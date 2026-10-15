package com.spacehorizons.client.devtest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;

import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.content.ModItems;

/**
 * Simula o crafting no servidor do autoteste (sem GUI): procura a receita pela grade e registra no log o
 * resultado e as sobras ("[AutoTest] craft ..."). Serve para ver no jogo de verdade que as receitas do mod
 * carregam e que o martelo volta danificado.
 */
final class ContentCraftCheck {
    private static final String TAG = "[AUTOTEST] ";

    private ContentCraftCheck() {
    }

    static void run(MinecraftServer server) {
        server.execute(() -> {
            ServerLevel level = server.overworld();
            ItemStack ferro = new ItemStack(Items.IRON_INGOT);
            ItemStack carvao = new ItemStack(Items.CHARCOAL);
            ItemStack carvaoMineral = new ItemStack(Items.COAL);
            ItemStack aco = new ItemStack(ModItems.STEEL_INGOT.get());
            ItemStack pepita = new ItemStack(ModItems.STEEL_NUGGET.get());
            ItemStack vazio = ItemStack.EMPTY;
            ItemStack graveto = new ItemStack(Items.STICK);
            ItemStack martelo = new ItemStack(ModItems.FORGE_HAMMER.get());
            ItemStack marteloGasto = new ItemStack(ModItems.FORGE_HAMMER.get());
            marteloGasto.setDamageValue(marteloGasto.getMaxDamage() - 1);
            ItemStack placa = new ItemStack(ModItems.STEEL_PLATE.get());
            ItemStack cobre = new ItemStack(Items.COPPER_INGOT);
            ItemStack redstone = new ItemStack(Items.REDSTONE);
            ItemStack pepitaOuro = new ItemStack(Items.GOLD_NUGGET);

            craft(level, "mistura (ferro + carvão vegetal x2)", 3, 1, ferro, carvao, carvao);
            craft(level, "mistura (ferro + carvão x2)", 3, 1, ferro, carvaoMineral, carvaoMineral);
            craft(level, "mistura invalida (ferro + 1 carvão)", 2, 1, ferro, carvao);
            craft(level, "placa (martelo + 2 aço)", 3, 1, martelo, aco, aco);
            craft(level, "placa (martelo no ultimo uso + 2 aço)", 3, 1, marteloGasto, aco, aco);
            craft(level, "martelo", 3, 3, aco, aco, vazio, aco, aco, graveto, vazio, vazio, graveto);
            craft(level, "circuito", 3, 3, vazio, pepitaOuro, vazio, cobre, placa, cobre, redstone, redstone, redstone);
            craft(level, "bloco de aço", 3, 3, aco, aco, aco, aco, aco, aco, aco, aco, aco);
            craft(level, "9 pepitas", 1, 1, aco);
            craft(level, "lingote das pepitas", 3, 3, pepita, pepita, pepita, pepita, pepita, pepita, pepita, pepita,
                    pepita);
            craft(level, "9 lingotes do bloco", 1, 1, new ItemStack(ModBlocks.STEEL_BLOCK.get()));

            ItemStack mistura = new ItemStack(ModItems.STEEL_BLEND.get());
            var receitas = server.getRecipeManager();
            var alto = receitas.getRecipeFor(RecipeType.BLASTING, new SingleRecipeInput(mistura), level);
            var forno = receitas.getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(mistura), level);
            SpaceHorizons.LOGGER.info("{}craft alto-forno com mistura -> {}", TAG,
                    alto.map(h -> describe(((BlastingRecipe) h.value()).assemble(new SingleRecipeInput(mistura)))
                            + " xp=" + ((BlastingRecipe) h.value()).experience()).orElse("NENHUMA"));
            SpaceHorizons.LOGGER.info("{}craft forno comum com mistura -> {} (esperado NENHUMA)", TAG,
                    forno.map(h -> describe(((SmeltingRecipe) h.value()).assemble(new SingleRecipeInput(mistura))))
                            .orElse("NENHUMA"));
        });
    }

    private static void craft(ServerLevel level, String nome, int largura, int altura, ItemStack... itens) {
        List<ItemStack> grade = new ArrayList<>(Arrays.asList(itens));
        CraftingInput input = CraftingInput.of(largura, altura, grade);
        var holder = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (holder.isEmpty()) {
            SpaceHorizons.LOGGER.info("{}craft {} -> NENHUMA RECEITA", TAG, nome);
            return;
        }
        RecipeHolder<CraftingRecipe> receita = holder.get();
        ItemStack resultado = receita.value().assemble(input);
        List<String> sobras = new ArrayList<>();
        for (ItemStack s : receita.value().getRemainingItems(input)) {
            if (!s.isEmpty()) {
                sobras.add(describe(s));
            }
        }
        SpaceHorizons.LOGGER.info("{}craft {} -> {} (receita {}) sobras={}", TAG, nome, describe(resultado),
                receita.id().identifier(), sobras);
    }

    private static String describe(ItemStack s) {
        String base = s.getCount() + "x " + s.getItem();
        return s.isDamageableItem() ? base + " dano " + s.getDamageValue() + "/" + s.getMaxDamage() : base;
    }
}
