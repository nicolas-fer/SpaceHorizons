package com.spacehorizons.rocket.blueprint;

import java.util.Locale;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.content.ModDataComponents;
import com.spacehorizons.content.ModItems;
import com.spacehorizons.rocket.DesignReport;
import com.spacehorizons.rocket.RocketBlockKind;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * A planta de montagem (docs/PLANO_TIERS.md, seção 4). O item guarda um projeto ({@link Blueprint}, componente {@code rocket_blueprint}) ou
 * está em branco.
 *
 * <ul>
 *   <li>Clique direito no ar: abre o menu das plantas (o "livro"), que troca o projeto do item por uma planta embutida ou o deixa em branco.</li>
 *   <li>Clique numa plataforma de lançamento com uma planta: a planta fica ATIVA nela (a plataforma clicada é a âncora) e aparece o holograma.</li>
 *   <li>Clique numa plataforma com a planta em branco: salva o foguete que está sobre ela (a mesma busca do painel e da montagem).</li>
 *   <li>Agachado + clique numa plataforma: tira a planta ativa dela.</li>
 * </ul>
 *
 * Tudo que muda o mundo ou o item acontece no servidor; o menu é do cliente e só pede a troca ({@link BlueprintChoicePayload}).
 */
public class RocketBlueprintItem extends Item {
    /** O menu das plantas (só o cliente liga isto; num servidor dedicado o clique no ar não faz nada). */
    public static volatile Consumer<InteractionHand> CLIENT_OPEN = hand -> {
    };

    public RocketBlueprintItem(Item.Properties properties) {
        super(properties);
    }

    /** Um item com a planta dada (as embutidas na aba do criativo, a lunar entregue pela história no futuro). */
    public static ItemStack stackOf(Blueprint blueprint) {
        ItemStack stack = new ItemStack(ModItems.ROCKET_BLUEPRINT.get());
        stack.set(ModDataComponents.ROCKET_BLUEPRINT.get(), blueprint);
        return stack;
    }

    public static @Nullable Blueprint blueprintOf(ItemStack stack) {
        return stack.get(ModDataComponents.ROCKET_BLUEPRINT.get());
    }

    /** O nome da planta: o dado pelo jogador, o da embutida, ou "Foguete de &lt;autor&gt;". */
    public static Component displayName(Blueprint blueprint) {
        if (!blueprint.name().isEmpty()) {
            return Component.literal(blueprint.name());
        }
        if (!blueprint.id().isEmpty()) {
            return Component.translatable("blueprint.spacehorizons." + blueprint.id());
        }
        return Component.translatable("blueprint.spacehorizons.saved_name", blueprint.author().isEmpty() ? "?" : blueprint.author());
    }

    /** A chave do destino do projeto da planta (a mesma frase do painel). */
    public static String destinationKey(Blueprint blueprint) {
        return "design.spacehorizons.destination." + blueprint.design().destinationValue().name().toLowerCase(Locale.ROOT);
    }

    @Override
    public Component getName(ItemStack stack) {
        Blueprint blueprint = blueprintOf(stack);
        return blueprint == null ? Component.translatable("item.spacehorizons.rocket_blueprint.blank")
                : Component.translatable("item.spacehorizons.rocket_blueprint.named", displayName(blueprint));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        Blueprint blueprint = blueprintOf(stack);
        if (blueprint == null) {
            builder.accept(Component.translatable("tooltip.spacehorizons.rocket_blueprint.blank").withStyle(ChatFormatting.GRAY));
        } else {
            DesignReport design = blueprint.design();
            int parts = blueprint.size() - blueprint.count(RocketBlockKind.PAD);
            builder.accept(Component.translatable("tooltip.spacehorizons.rocket_blueprint.blocks", parts, blueprint.count(RocketBlockKind.PAD),
                    blueprint.maxX() - blueprint.minX() + 1, blueprint.maxY() - blueprint.minY() + 1, blueprint.maxZ() - blueprint.minZ() + 1)
                    .withStyle(ChatFormatting.GRAY));
            builder.accept(Component.translatable("tooltip.spacehorizons.rocket_blueprint.destination", Component.translatable(destinationKey(blueprint)))
                    .withStyle(design.assemblable() ? ChatFormatting.AQUA : ChatFormatting.RED));
            if (!blueprint.author().isEmpty()) {
                builder.accept(Component.translatable("tooltip.spacehorizons.rocket_blueprint.author", blueprint.author()).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        builder.accept(Component.translatable("tooltip.spacehorizons.rocket_blueprint.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            CLIENT_OPEN.accept(hand);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos).is(ModBlocks.LAUNCH_PAD.get())) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player == null || !(level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad)) {
            return InteractionResult.PASS;
        }
        if (!level.mayInteract(player, pos)) {
            player.sendOverlayMessage(Component.translatable("message.spacehorizons.blueprint.not_allowed"));
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        Blueprint blueprint = blueprintOf(stack);
        if (player.isSecondaryUseActive()) {
            boolean had = pad.blueprint() != null;
            pad.setBlueprint(null);
            player.sendOverlayMessage(Component.translatable(had ? "message.spacehorizons.blueprint.cleared" : "message.spacehorizons.blueprint.none_active"));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (blueprint != null) {
            pad.setBlueprint(blueprint);
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.4F, 1.6F);
            player.sendOverlayMessage(Component.translatable("message.spacehorizons.blueprint.activated", displayName(blueprint), blueprint.size()));
            return InteractionResult.SUCCESS_SERVER;
        }
        // em branco: salva o foguete que está sobre a plataforma
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        Blueprint saved = BlueprintWorld.capture(level, pos, customName == null ? "" : customName.getString(), player.getName().getString());
        if (saved == null) {
            player.sendOverlayMessage(Component.translatable("message.spacehorizons.blueprint.nothing_to_save"));
            return InteractionResult.FAIL;
        }
        stack.set(ModDataComponents.ROCKET_BLUEPRINT.get(), saved);
        level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.sendSystemMessage(Component.translatable("message.spacehorizons.blueprint.saved", displayName(saved), saved.size(),
                Component.translatable(destinationKey(saved))));
        return InteractionResult.SUCCESS_SERVER;
    }
}
