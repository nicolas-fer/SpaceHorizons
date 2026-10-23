package com.spacehorizons.client.devtest;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.content.ModDataComponents;
import com.spacehorizons.content.ModItems;
import com.spacehorizons.rocket.blueprint.Blueprint;
import com.spacehorizons.rocket.blueprint.RocketBlueprintItem;
import com.spacehorizons.story.DataTerminalBlock;
import com.spacehorizons.story.StoryLogic;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Só em dev: as buscas e conferências do autoteste {@code -PshOnly=story} (o roteiro fica no fim do AutoTest). */
final class StoryDevtest {
    private static final String TAG = "[AUTOTEST] STORY ";
    private static int passed;
    private static int failed;

    private StoryDevtest() {
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        SpaceHorizons.LOGGER.info(TAG + "CHECK {} {}: {}", ok ? "PASSOU" : "[REDACTED]", name, detail);
    }

    static void summary() {
        SpaceHorizons.LOGGER.info(TAG + "RESUMO: {} verificações passaram, {} falharam", passed, failed);
    }

    static <T> T onServer(Minecraft mc, Function<MinecraftServer, T> body) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            throw new IllegalStateException("sem servidor integrado");
        }
        try {
            return server.submit(() -> body.apply(server)).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao consultar o servidor", e);
        }
    }

    /** O primeiro bloco do tipo no cubo (cliente) em volta do jogador, ou null. */
    static @Nullable BlockPos findBlock(Minecraft mc, Predicate<BlockState> match, int radiusXZ, int radiusY) {
        BlockPos center = mc.player.blockPosition();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -radiusXZ; dx <= radiusXZ; dx++) {
            for (int dz = -radiusXZ; dz <= radiusXZ; dz++) {
                for (int dy = -radiusY; dy <= radiusY; dy++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (match.test(mc.level.getBlockState(pos))) {
                        double d = dx * dx + dz * dz + dy * dy;
                        if (d < bestDist) {
                            bestDist = d;
                            best = pos.immutable();
                        }
                    }
                }
            }
        }
        return best;
    }

    static @Nullable BlockPos findTerminal(Minecraft mc, String record) {
        return findBlock(mc, s -> s.is(ModBlocks.DATA_TERMINAL.get()) && s.getValue(DataTerminalBlock.RECORD).getSerializedName().equals(record), 40, 14);
    }

    static @Nullable BlockPos findBlockOf(Minecraft mc, Block block) {
        return findBlock(mc, s -> s.is(block), 40, 14);
    }

    /** Quantos itens do tipo o jogador tem (inventário do cliente). */
    static int count(Minecraft mc, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** Põe o primeiro item do tipo achado no inventário na mão principal (barra 0) e devolve se achou. */
    static boolean holdFirst(Minecraft mc, java.util.function.Predicate<ItemStack> match) {
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (match.test(inv.getItem(i))) {
                int slot = i < 9 ? i : 0;
                if (i >= 9) {
                    ItemStack tmp = inv.getItem(0).copy();
                    inv.setItem(0, inv.getItem(i).copy());
                    inv.setItem(i, tmp);
                }
                inv.setSelectedSlot(slot);
                return true;
            }
        }
        return false;
    }

    static boolean isJournal(ItemStack stack, String entry) {
        return stack.is(ModItems.JOURNAL_ENTRY.get()) && entry.equals(stack.get(ModDataComponents.JOURNAL_ENTRY.get()));
    }

    static boolean isBlueprint(ItemStack stack, String id) {
        if (!stack.is(ModItems.ROCKET_BLUEPRINT.get())) {
            return false;
        }
        Blueprint b = RocketBlueprintItem.blueprintOf(stack);
        return b != null && id.equals(b.id());
    }

    static boolean hasBlueprint(Minecraft mc, String id) {
        for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
            if (isBlueprint(stack, id)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasCompassFor(Minecraft mc, String dimensionPath) {
        for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
            var tracker = stack.get(DataComponents.LODESTONE_TRACKER);
            if (stack.is(Items.COMPASS) && tracker != null && tracker.target().isPresent()
                    && tracker.target().get().dimension().identifier().getPath().equals(dimensionPath)) {
                return true;
            }
        }
        return false;
    }

    static boolean advancementDone(Minecraft mc, String name) {
        return onServer(mc, server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            AdvancementHolder holder = server.getAdvancements().get(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, name));
            return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
        });
    }

    /** O servidor aceita a planta {@code id} do menu para o jogador (em sobrevivência: só se a história a destravou)? */
    static boolean serverAcceptsBlueprint(Minecraft mc, String id) {
        return onServer(mc, server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROCKET_BLUEPRINT.get()));
            boolean ok = com.spacehorizons.rocket.blueprint.BlueprintChoicePayload.apply(player,
                    new com.spacehorizons.rocket.blueprint.BlueprintChoicePayload(id, true));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            return ok;
        });
    }

    static String progress(Minecraft mc) {
        var p = StoryLogic.progress(mc.player);
        return "lidos=" + p.read() + " levados=" + p.claimed();
    }
}
