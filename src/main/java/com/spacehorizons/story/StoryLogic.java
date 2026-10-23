package com.spacehorizons.story;

import java.util.Optional;

import com.spacehorizons.planet.Planets;
import com.spacehorizons.rocket.blueprint.Blueprints;
import com.spacehorizons.rocket.blueprint.RocketBlueprintItem;
import com.spacehorizons.story.StoryEntry.Reward;
import com.spacehorizons.story.StoryEntry.Site;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;

/**
 * A lógica da trama no servidor: ler um diário, usar um terminal (a recompensa vem UMA vez por jogador, pela regra pura de {@link StoryProgress#claim}) e
 * entregar o que o terminal promete (plantas e bússolas). O progresso mora no attachment {@link StorySetup#PROGRESS}.
 */
public final class StoryLogic {
    private StoryLogic() {
    }

    /** O progresso do jogador (no cliente, o sincronizado). */
    public static StoryProgress progress(Player player) {
        return player.getData(StorySetup.PROGRESS.get());
    }

    /** A planta embutida {@code blueprintId} pode ser escolhida em sobrevivência? (a lunar abre quando o jogador levou a do terminal da base) */
    public static boolean unlocksBlueprint(Player player, String blueprintId) {
        return progress(player).unlocksBlueprint(blueprintId);
    }

    /** O jogador leu o registro (abriu o diário): marca como lido e dispara a conquista. */
    public static void markRead(ServerPlayer player, StoryEntry entry) {
        StoryProgress before = progress(player);
        if (!before.hasRead(entry.id())) {
            player.setData(StorySetup.PROGRESS.get(), before.withRead(entry.id()));
        }
        StorySetup.ENTRY_TRIGGER.get().trigger(player, entry.id());
    }

    /**
     * O jogador usou o terminal: na primeira vez entrega as recompensas (e avisa no chat), nas seguintes só avisa que o registro já foi copiado. Sempre
     * marca como lido e dispara a conquista. Devolve o que foi entregue agora.
     */
    public static StoryProgress.Claim useTerminal(ServerPlayer player, StoryEntry entry) {
        StoryProgress.Claim claim = progress(player).claim(entry);
        player.setData(StorySetup.PROGRESS.get(), claim.progress());
        if (claim.first()) {
            player.sendSystemMessage(Component.translatable("message.spacehorizons.story.terminal_copied", Component.translatable(entry.textKey("title"))));
            for (Reward reward : claim.granted()) {
                give(player, reward);
            }
        } else {
            player.sendOverlayMessage(Component.translatable("message.spacehorizons.story.terminal_again"));
        }
        StorySetup.ENTRY_TRIGGER.get().trigger(player, entry.id());
        return claim;
    }

    /** Entrega uma recompensa (no inventário; se não couber, no chão) e conta o que foi no chat. */
    static void give(ServerPlayer player, Reward reward) {
        ItemStack stack = stackOf(reward);
        if (!player.getInventory().add(stack)) {
            player.spawnAtLocation(player.level(), stack);
        }
        int[] where = coordinates(reward);
        if (where == null) {
            player.sendSystemMessage(Component.translatable("message.spacehorizons.story.reward." + reward.id()));
        } else {
            player.sendSystemMessage(Component.translatable("message.spacehorizons.story.reward." + reward.id(), where[0], where[1]));
        }
    }

    /** O item de uma recompensa. */
    public static ItemStack stackOf(Reward reward) {
        return switch (reward) {
            case BLUEPRINT_ORBITAL -> RocketBlueprintItem.stackOf(Blueprints.ORBITAL);
            case BLUEPRINT_LUNAR -> RocketBlueprintItem.stackOf(Blueprints.LUNAR);
            case COMPASS_MOON_BASE -> compass(reward, ResourceKey.create(Registries.DIMENSION, Planets.MOON.id()), coordinates(reward));
            case COMPASS_MOON_PROBE -> compass(reward, ResourceKey.create(Registries.DIMENSION, Planets.MOON.id()), coordinates(reward));
            case COMPASS_MARS -> compass(reward, ResourceKey.create(Registries.DIMENSION, Planets.MARS.id()), coordinates(reward));
        };
    }

    /** As coordenadas (x, z) que a recompensa aponta, ou null se ela não aponta para lugar nenhum. */
    public static int[] coordinates(Reward reward) {
        return switch (reward) {
            case COMPASS_MOON_BASE -> new int[] {StorySites.moon(Site.MOON_BASE).centerX(), StorySites.moon(Site.MOON_BASE).centerZ()};
            case COMPASS_MOON_PROBE -> new int[] {StorySites.moon(Site.MOON_PROBE).centerX(), StorySites.moon(Site.MOON_PROBE).centerZ()};
            case COMPASS_MARS -> new int[] {StoryEntries.MARS_SIGNAL_X, StoryEntries.MARS_SIGNAL_Z};
            default -> null;
        };
    }

    private static ItemStack compass(Reward reward, ResourceKey<Level> dimension, int[] where) {
        ItemStack stack = new ItemStack(Items.COMPASS);
        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(dimension, new BlockPos(where[0], 64, where[1]))), false));
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("story.spacehorizons." + reward.id()).withStyle(style -> style.withItalic(false)));
        return stack;
    }
}
