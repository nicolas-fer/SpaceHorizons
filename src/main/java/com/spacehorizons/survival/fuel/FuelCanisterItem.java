package com.spacehorizons.survival.fuel;

import java.util.function.Consumer;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModDataComponents;
import com.spacehorizons.content.ModItems;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.space.flight.Propellant;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Galão de combustível de foguete: guarda a quantidade (mB) no data component {@code spacehorizons:fuel_mb}.
 * Barra de preenchimento na hotbar (escala da durabilidade, cor âmbar) e tooltip "N / 1000 mB".
 * Não empilha (veja o registro em {@link ModItems}).
 */
public class FuelCanisterItem extends Item {
    /** Âmbar do combustível. */
    private static final int BAR_COLOR = 0xFFE0A030;

    private final Propellant propellant;

    public FuelCanisterItem(Properties properties) {
        this(properties, Propellant.CHEMICAL);
    }

    /**
     * Galão de outro combustível (Tier 2: hidrogênio, xenônio, hélio-3). Mesma capacidade e mesmo componente {@code fuel_mb}; o
     * combustível é o do item, e um tanque só aceita o galão do combustível dele.
     */
    public FuelCanisterItem(Properties properties, Propellant propellant) {
        super(properties);
        this.propellant = propellant;
    }

    /** O combustível que este galão guarda. */
    public Propellant propellant() {
        return this.propellant;
    }

    /** O combustível de um galão qualquer (null se o item não é galão). */
    public static Propellant propellantOf(ItemStack stack) {
        return stack.getItem() instanceof FuelCanisterItem canister ? canister.propellant : null;
    }

    /** Galão cheio do item dado (para a aba do criativo e testes). */
    public static ItemStack fullStack(Item item) {
        ItemStack stack = new ItemStack(item);
        setFuel(stack, CanisterFuel.CAPACITY);
        return stack;
    }

    /** Quantidade guardada em mB (0 se o componente não existe). */
    public static int getFuel(ItemStack stack) {
        return CanisterFuel.clamp(stack.getOrDefault(ModDataComponents.FUEL_MB.get(), 0));
    }

    /** Guarda a quantidade (ajustada aos limites); zero remove o componente, para o galão vazio ser igual a qualquer outro. */
    public static void setFuel(ItemStack stack, int mb) {
        int value = CanisterFuel.clamp(mb);
        if (value == 0) {
            stack.remove(ModDataComponents.FUEL_MB.get());
        } else {
            stack.set(ModDataComponents.FUEL_MB.get(), value);
        }
    }

    /** Galão cheio (para a aba do criativo). */
    public static ItemStack fullStack() {
        ItemStack stack = new ItemStack(ModItems.FUEL_CANISTER.get());
        setFuel(stack, CanisterFuel.CAPACITY);
        return stack;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getFuel(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return CanisterFuel.barWidth(getFuel(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return switch (this.propellant) {
            case HYDROGEN -> 0xFF9FD8F0;
            case XENON -> 0xFFB080F0;
            case HELIUM3 -> 0xFFE8F0A0;
            default -> BAR_COLOR;
        };
    }

    /** Hélio-3 que um bloco de regolito lunar rende ao ser processado pelo galão (8 blocos enchem um galão). */
    public static final int HELIUM3_PER_BLOCK_MB = 125;
    /** Blocos de onde se extrai hélio-3, e só na Lua (hoje o solo da Lua é pedra; o regolito lunar do gerador de planetas entra na tag). */
    public static final TagKey<Block> HELIUM3_SOURCES = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "helium3_sources"));

    /**
     * Galão de hélio-3 com clique direito no solo da Lua: processa o bloco (ele some) e o galão ganha {@value #HELIUM3_PER_BLOCK_MB} mB. É a
     * "máquina simples" de extrair o He-3 que o vento solar deixou no regolito (docs/PLANO_TIERS.md, "Tier 2: conteúdo").
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (this.propellant != Propellant.HELIUM3) {
            return super.useOn(context);
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        if (!level.getBlockState(pos).is(HELIUM3_SOURCES) || !isMoon(level) || CanisterFuel.space(getFuel(stack)) < HELIUM3_PER_BLOCK_MB) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            level.destroyBlock(pos, false);
            setFuel(stack, getFuel(stack) + HELIUM3_PER_BLOCK_MB);
            level.playSound(null, pos, SoundEvents.BUCKET_FILL_POWDER_SNOW, SoundSource.BLOCKS, 0.8F, 1.4F);
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean isMoon(Level level) {
        return Planets.forLevel(level).map(p -> p.id().equals(Planets.MOON.id())).orElse(false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
            TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.spacehorizons.fuel_canister", getFuel(stack), CanisterFuel.CAPACITY)
                .withStyle(ChatFormatting.GRAY));
    }
}
