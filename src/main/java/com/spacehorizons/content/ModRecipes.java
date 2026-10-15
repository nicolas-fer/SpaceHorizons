package com.spacehorizons.content;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.survival.fuel.RefiningRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Tipos e serializadores de receita próprios do mod. */
public final class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, SpaceHorizons.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, SpaceHorizons.MODID);
    public static final DeferredRegister<RecipeBookCategory> CATEGORIES =
            DeferredRegister.create(Registries.RECIPE_BOOK_CATEGORY, SpaceHorizons.MODID);

    /** Refino de combustível ({@code spacehorizons:refining}), usado pela refinaria. */
    public static final DeferredHolder<RecipeType<?>, RecipeType<RefiningRecipe>> REFINING = TYPES.register("refining",
            () -> RecipeType.simple(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "refining")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<RefiningRecipe>> REFINING_SERIALIZER =
            SERIALIZERS.register("refining", () -> RefiningRecipe.SERIALIZER);
    /** Categoria exigida por toda receita; as de refino são "especiais" e não aparecem em nenhum livro. */
    public static final DeferredHolder<RecipeBookCategory, RecipeBookCategory> REFINING_CATEGORY =
            CATEGORIES.register("refining", RecipeBookCategory::new);

    private ModRecipes() {
    }
}
