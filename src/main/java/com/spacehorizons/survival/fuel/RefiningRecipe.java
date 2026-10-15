package com.spacehorizons.survival.fuel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spacehorizons.content.ModRecipes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Receita de refino ({@code spacehorizons:refining}): um ingrediente vira {@code fuel_mb} de combustível de foguete
 * em {@code time} ticks. Não produz item, então é "especial" (fora do livro de receitas e do aprendizado).
 *
 * JSON: {@code {"type": "spacehorizons:refining", "ingredient": "minecraft:coal", "fuel_mb": 250, "time": 100}}.
 * O ingrediente aceita item, lista de itens ou tag ({@code "#minecraft:coals"}).
 */
public final class RefiningRecipe implements Recipe<SingleRecipeInput> {
    public static final MapCodec<RefiningRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(RefiningRecipe::ingredient),
            Codec.intRange(1, RefineryLogic.TANK_CAPACITY).fieldOf("fuel_mb").forGetter(RefiningRecipe::fuelMb),
            Codec.intRange(1, 24000).fieldOf("time").forGetter(RefiningRecipe::time))
            .apply(i, RefiningRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, RefiningRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, RefiningRecipe::ingredient,
            ByteBufCodecs.VAR_INT, RefiningRecipe::fuelMb,
            ByteBufCodecs.VAR_INT, RefiningRecipe::time,
            RefiningRecipe::new);
    public static final RecipeSerializer<RefiningRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final Ingredient ingredient;
    private final int fuelMb;
    private final int time;
    private @Nullable PlacementInfo placementInfo;

    public RefiningRecipe(Ingredient ingredient, int fuelMb, int time) {
        this.ingredient = ingredient;
        this.fuelMb = fuelMb;
        this.time = time;
    }

    public Ingredient ingredient() {
        return ingredient;
    }

    /** Combustível produzido, em mB. */
    public int fuelMb() {
        return fuelMb;
    }

    /** Duração do processamento, em ticks. */
    public int time() {
        return time;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<RefiningRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public RecipeType<RefiningRecipe> getType() {
        return ModRecipes.REFINING.get();
    }

    @Override
    public PlacementInfo placementInfo() {
        if (placementInfo == null) {
            placementInfo = PlacementInfo.create(ingredient);
        }
        return placementInfo;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return ModRecipes.REFINING_CATEGORY.get();
    }
}
