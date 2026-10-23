package com.spacehorizons.story;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.spacehorizons.content.ModDataComponents;
import com.spacehorizons.content.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * O diário da trama (docs/PLANO_SOBREVIVENCIA.md, "Etapa 5: a trama"): um livro por registro, com o id do registro no componente {@code journal_entry}.
 * Clique direito abre a tela de leitura ({@code StoryScreen}, no cliente); no servidor marca o registro como lido e dispara a conquista. O texto vem
 * do lang pelo id, então não vai dentro do item.
 */
public class JournalEntryItem extends Item {
    /** Abre a tela de leitura (só o cliente liga isto; num servidor dedicado o clique só marca como lido). Recebe o id do registro. */
    public static volatile Consumer<String> CLIENT_OPEN = id -> {
    };

    public JournalEntryItem(Item.Properties properties) {
        super(properties);
    }

    /** Um diário do registro {@code entryId}. */
    public static ItemStack stackOf(String entryId) {
        ItemStack stack = new ItemStack(ModItems.JOURNAL_ENTRY.get());
        stack.set(ModDataComponents.JOURNAL_ENTRY.get(), entryId);
        return stack;
    }

    /** O registro do diário, ou null se o item não tem registro (ou o id é desconhecido). */
    public static @Nullable StoryEntry entryOf(ItemStack stack) {
        String id = stack.get(ModDataComponents.JOURNAL_ENTRY.get());
        return id == null ? null : StoryEntries.byId(id);
    }

    @Override
    public Component getName(ItemStack stack) {
        StoryEntry entry = entryOf(stack);
        return entry == null ? super.getName(stack) : Component.translatable("item.spacehorizons.journal_entry.named", Component.translatable(entry.textKey("title")));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        StoryEntry entry = entryOf(stack);
        if (entry != null) {
            builder.accept(Component.translatable(entry.textKey("author")).withStyle(ChatFormatting.GRAY));
        }
        builder.accept(Component.translatable("tooltip.spacehorizons.journal_entry.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        StoryEntry entry = entryOf(player.getItemInHand(hand));
        if (entry == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            CLIENT_OPEN.accept(entry.id());
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            StoryLogic.markRead(serverPlayer, entry);
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
