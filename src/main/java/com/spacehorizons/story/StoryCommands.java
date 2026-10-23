package com.spacehorizons.story;

import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.map.PlanetMapService;
import com.spacehorizons.space.travel.TravelTransfers;
import com.spacehorizons.story.StoryEntry.Site;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Comandos de desenvolvimento da trama ({@code /spacehorizons story ...}, só para operadores):
 *
 * <ul>
 *   <li>{@code where earth_station|moon_base|moon_probe}: onde fica o lugar (a estação da Terra é procurada em até 150 chunks; a base e a sonda têm posição
 *   fixa);</li>
 *   <li>{@code goto earth_station|moon_base|moon_probe}: leva o operador à porta do lugar;</li>
 *   <li>{@code give <registro>}: entrega o diário do registro;</li>
 *   <li>{@code progress}: lista o progresso do operador; {@code reset}: apaga o progresso (para testar a recompensa de novo).</li>
 * </ul>
 */
final class StoryCommands {
    private static final int SEARCH_CHUNKS = 150;

    private StoryCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spacehorizons").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("story")
                        .then(Commands.literal("where").then(Commands.argument("site", StringArgumentType.word())
                                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(siteNames(), builder))
                                .executes(ctx -> where(ctx.getSource(), StringArgumentType.getString(ctx, "site"), false))))
                        .then(Commands.literal("goto").then(Commands.argument("site", StringArgumentType.word())
                                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(siteNames(), builder))
                                .executes(ctx -> where(ctx.getSource(), StringArgumentType.getString(ctx, "site"), true))))
                        .then(Commands.literal("give").then(Commands.argument("entry", StringArgumentType.word())
                                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider
                                        .suggest(StoryEntries.ALL.stream().filter(e -> !e.isTerminal()).map(StoryEntry::id).toList(), builder))
                                .executes(ctx -> give(ctx.getSource(), StringArgumentType.getString(ctx, "entry")))))
                        .then(Commands.literal("progress").executes(ctx -> progress(ctx.getSource())))
                        .then(Commands.literal("reset").executes(ctx -> reset(ctx.getSource())))));
    }

    private static java.util.List<String> siteNames() {
        return java.util.Arrays.stream(Site.values()).map(Site::id).toList();
    }

    private static @Nullable Site siteOf(String id) {
        for (Site s : Site.values()) {
            if (s.id().equals(id.toLowerCase(Locale.ROOT))) {
                return s;
            }
        }
        return null;
    }

    /** Acha o lugar; com {@code teleport}, leva o operador à porta dele. */
    private static int where(CommandSourceStack source, String siteId, boolean teleport) throws CommandSyntaxException {
        Site site = siteOf(siteId);
        if (site == null) {
            source.sendFailure(Component.literal("Lugar desconhecido: " + siteId + " (earth_station, moon_base, moon_probe)"));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        PlanetDefinition planet = site.onMoon() ? Planets.MOON : Planets.EARTH;
        ServerLevel level = TravelTransfers.findLevel(source.getServer(), planet);
        if (level == null) {
            source.sendFailure(Component.literal("Este mundo não tem a dimensão de " + planet.id().getPath() + ": crie um mundo com o tipo \"Space Horizons\""));
            return 0;
        }
        int x;
        int y;
        int z;
        if (site.onMoon()) {
            StorySites.Placement p = StorySites.moon(site);
            // a porta: o lado sul do desenho (z maior), no eixo da entrada
            x = p.minX() + (site == Site.MOON_BASE ? 4 : 8);
            z = p.maxZ() + 2;
            y = PlanetMapService.landingY(level, x, z);
            source.sendSuccess(() -> Component.literal(site.id() + ": centro (" + p.centerX() + ", " + p.centerZ() + "), laje y=" + p.floorY() + ", pegada "
                    + p.minX() + ".." + p.maxX() + " x " + p.minZ() + ".." + p.maxZ() + ", relevo " + p.roughness() + " blocos de diferença"), false);
        } else {
            BlockPos found = locateEarth(level, player.blockPosition());
            if (found == null) {
                source.sendFailure(Component.literal("Nenhuma estação em " + SEARCH_CHUNKS + " chunks (o mundo precisa de \"gerar estruturas\" ligado)"));
                return 0;
            }
            // o canto do chunk inicial; o desenho fica centrado no chunk (StoryStructure) e a porta do salão (local x 5..7) dá para a laje ao sul (local z 13)
            SiteLayout layout = SiteLayouts.of(site);
            x = found.getX() + 8 - layout.width() / 2 + 6;
            z = found.getZ() + 8 - layout.depth() / 2 + 13;
            y = PlanetMapService.landingY(level, x, z);
            source.sendSuccess(() -> Component.literal("earth_station: perto de (" + found.getX() + ", " + found.getZ() + ")"), false);
        }
        if (teleport) {
            player.teleportTo(level, x + 0.5, y, z + 0.5, Set.<Relative>of(), 0.0F, 0.0F, false);
            int fx = x;
            int fy = y;
            int fz = z;
            source.sendSuccess(() -> Component.literal("Teleportado para a porta de " + site.id() + " (" + fx + ", " + fy + ", " + fz + ")"), true);
        }
        return 1;
    }

    private static @Nullable BlockPos locateEarth(ServerLevel level, BlockPos from) {
        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("spacehorizons", Site.EARTH_STATION.id()));
        var holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(key);
        if (holder.isEmpty()) {
            return null;
        }
        return level.findNearestMapStructure(HolderSet.direct(holder.get()), from, SEARCH_CHUNKS, false);
    }

    private static int give(CommandSourceStack source, String entryId) throws CommandSyntaxException {
        StoryEntry entry = StoryEntries.byId(entryId);
        if (entry == null || entry.isTerminal()) {
            source.sendFailure(Component.literal("Diário desconhecido: " + entryId));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = JournalEntryItem.stackOf(entry.id());
        if (!player.getInventory().add(stack)) {
            player.spawnAtLocation(player.level(), stack);
        }
        return 1;
    }

    private static int progress(CommandSourceStack source) throws CommandSyntaxException {
        StoryProgress progress = StoryLogic.progress(source.getPlayerOrException());
        source.sendSuccess(() -> Component.literal("lidos: " + progress.read() + " | levados: " + progress.claimed()), false);
        return 1;
    }

    private static int reset(CommandSourceStack source) throws CommandSyntaxException {
        source.getPlayerOrException().setData(StorySetup.PROGRESS.get(), StoryProgress.EMPTY);
        source.sendSuccess(() -> Component.literal("Progresso da trama apagado"), true);
        return 1;
    }
}
