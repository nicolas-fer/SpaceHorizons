package com.spacehorizons.compat.jei;

import java.util.Locale;

import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.content.ModRecipes;
import com.spacehorizons.survival.fuel.RefineryLogic;
import com.spacehorizons.survival.fuel.RefiningRecipe;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Categoria "Refinaria" do JEI: item de entrada, seta animada com a duração da receita, uma barra de tanque
 * proporcional ao combustível produzido e os números (mB e segundos) escritos ao lado.
 */
public class RefiningCategory extends AbstractRecipeCategory<RecipeHolder<RefiningRecipe>> {
    /** Tipo de receita do JEI para o tipo {@code spacehorizons:refining} do jogo. */
    public static final IRecipeHolderType<RefiningRecipe> RECIPE_TYPE = IRecipeHolderType.create(ModRecipes.REFINING.get());

    private static final int WIDTH = 130;
    private static final int HEIGHT = 40;
    private static final int INPUT_X = 6;
    private static final int INPUT_Y = 12;
    private static final int ARROW_X = 30;
    private static final int ARROW_Y = 12;
    /** Barra de tanque, no mesmo estilo da tela da refinaria. */
    private static final int TANK_X = 62;
    private static final int TANK_Y = 4;
    private static final int TANK_W = 12;
    private static final int TANK_H = 32;
    private static final int TEXT_X = 80;

    private static final int COLOR_FRAME = 0xFF373737;
    private static final int COLOR_BACK = 0xFF8B8B8B;
    private static final int COLOR_FUEL = 0xFFE0A030;
    private static final int COLOR_FUEL_TOP = 0xFFF5CB6A;
    private static final int COLOR_TEXT = 0xFF404040;

    public RefiningCategory(IGuiHelper guiHelper) {
        super(RECIPE_TYPE, Component.translatable("jei.spacehorizons.category.refining"),
                guiHelper.createDrawableItemLike(ModBlocks.REFINERY.get()), WIDTH, HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<RefiningRecipe> holder, IFocusGroup focuses) {
        builder.addInputSlot(INPUT_X, INPUT_Y).setStandardSlotBackground().add(holder.value().ingredient());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder extras, RecipeHolder<RefiningRecipe> holder, IFocusGroup focuses) {
        RefiningRecipe recipe = holder.value();
        extras.addAnimatedRecipeArrowWidget(recipe.time()).setPosition(ARROW_X, ARROW_Y);
        extras.addDrawableWidget(tank(recipe.fuelMb())).setPosition(TANK_X, TANK_Y)
                .setTooltip(Component.translatable("gui.spacehorizons.refinery.tank"));
        extras.addText(Component.translatable("jei.spacehorizons.refining.fuel", recipe.fuelMb()), WIDTH - TEXT_X, 10)
                .setPosition(TEXT_X, 9).setColor(COLOR_TEXT).setShadow(false);
        extras.addText(Component.translatable("jei.spacehorizons.refining.time", seconds(recipe.time())), WIDTH - TEXT_X, 10)
                .setPosition(TEXT_X, 21).setColor(COLOR_TEXT).setShadow(false);
    }

    /** Segundos (ticks / 20), com uma casa decimal quando não são inteiros. */
    static String seconds(int ticks) {
        return ticks % 20 == 0 ? Integer.toString(ticks / 20) : String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }

    /** Barra do tanque: cheia na proporção do combustível produzido em relação à capacidade da refinaria. */
    private static IDrawable tank(int fuelMb) {
        int filled = Math.max(1, Math.round(TANK_H * fuelMb / (float) RefineryLogic.TANK_CAPACITY));
        return new IDrawable() {
            @Override
            public int getWidth() {
                return TANK_W;
            }

            @Override
            public int getHeight() {
                return TANK_H;
            }

            @Override
            public void draw(GuiGraphicsExtractor graphics, int x, int y) {
                graphics.fill(x - 1, y - 1, x + TANK_W + 1, y + TANK_H + 1, COLOR_FRAME);
                graphics.fill(x, y, x + TANK_W, y + TANK_H, COLOR_BACK);
                graphics.fill(x, y + TANK_H - filled, x + TANK_W, y + TANK_H, COLOR_FUEL);
                graphics.fill(x, y + TANK_H - filled, x + TANK_W, y + TANK_H - filled + 1, COLOR_FUEL_TOP);
            }
        };
    }
}
