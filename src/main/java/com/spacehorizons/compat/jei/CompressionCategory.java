package com.spacehorizons.compat.jei;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.survival.compressor.CompressorLogic;
import com.spacehorizons.survival.oxygen.OxygenAmounts;
import com.spacehorizons.survival.oxygen.OxygenStorage;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Categoria "Compressor de oxigênio" do JEI. Não há receita no jogo: o compressor enche qualquer tanque. Cada entrada é
 * um tanque vazio virando um tanque cheio, com a duração (a seta animada), o combustível de fornalha (a chama, com
 * dica) e o aviso de que só funciona em ar respirável.
 */
public class CompressionCategory extends AbstractRecipeCategory<CompressionCategory.Entry> {
    /**
     * Uma linha da categoria: tanque vazio, tanque cheio e quanto o compressor leva para encher (ticks).
     * Montada a partir do item do tanque ({@link #of}); só existe para o JEI.
     */
    public record Entry(ItemStack empty, ItemStack full, int ticks) {
        public static Entry of(Item tank) {
            ItemStack empty = new ItemStack(tank);
            ItemStack full = empty.copy();
            OxygenStorage.set(full, OxygenStorage.capacity(full));
            return new Entry(empty, full, CompressorLogic.ticksToFill(OxygenStorage.capacity(full)));
        }
    }

    /** Tipo próprio do JEI (não corresponde a um tipo de receita do jogo). */
    public static final IRecipeType<Entry> RECIPE_TYPE = IRecipeType.create(SpaceHorizons.MODID, "oxygen_compression", Entry.class);

    /** Tempo de queima do carvão (1.600 ticks): duração do ícone da chama e base da dica "quantos tanques por carvão". */
    private static final int FLAME_TICKS = 1_600;
    private static final int SMALL_TANKS_PER_COAL =
            FLAME_TICKS * CompressorLogic.FILL_PER_TICK / OxygenAmounts.SMALL_TANK_CAPACITY;

    private static final int WIDTH = 160;
    private static final int HEIGHT = 44;
    private static final int INPUT_X = 6;
    private static final int INPUT_Y = 5;
    private static final int ARROW_X = 30;
    private static final int ARROW_Y = 5;
    private static final int OUTPUT_X = 62;
    private static final int OUTPUT_Y = 5;
    private static final int FLAME_X = 9;
    private static final int FLAME_Y = 25;
    private static final int TEXT_X = 88;
    private static final int TEXT_W = WIDTH - TEXT_X - 2;

    private static final int COLOR_TEXT = 0xFF404040;
    private static final int COLOR_WARNING = 0xFF9A2A20;

    public CompressionCategory(IGuiHelper guiHelper) {
        super(RECIPE_TYPE, Component.translatable("jei.spacehorizons.category.oxygen_compression"),
                guiHelper.createDrawableItemLike(ModBlocks.OXYGEN_COMPRESSOR.get()), WIDTH, HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Entry entry, IFocusGroup focuses) {
        builder.addInputSlot(INPUT_X, INPUT_Y).setStandardSlotBackground().add(entry.empty());
        builder.addOutputSlot(OUTPUT_X, OUTPUT_Y).setOutputSlotBackground().add(entry.full());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder extras, Entry entry, IFocusGroup focuses) {
        extras.addAnimatedRecipeArrowWidget(entry.ticks()).setPosition(ARROW_X, ARROW_Y);
        extras.addAnimatedRecipeFlameWidget(FLAME_TICKS).setPosition(FLAME_X, FLAME_Y)
                .setTooltip(Component.translatable("jei.spacehorizons.compression.fuel", SMALL_TANKS_PER_COAL));
        extras.addText(Component.translatable("jei.spacehorizons.compression.time", RefiningCategory.seconds(entry.ticks())),
                TEXT_W, 10).setPosition(TEXT_X, 6).setColor(COLOR_TEXT).setShadow(false);
        extras.addText(Component.translatable("jei.spacehorizons.compression.air"), TEXT_W, 20)
                .setPosition(TEXT_X, 19).setColor(COLOR_WARNING).setShadow(false);
    }
}
