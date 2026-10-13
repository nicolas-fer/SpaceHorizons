package com.spacehorizons.worldgen;

import java.util.Set;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.spacehorizons.planet.PlanetTerrains;
import com.spacehorizons.planet.Planets;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;

/** /spacehorizons goto earth: teste rápido, leva o operador à superfície do planeta em (0, 0). */
final class PlanetCommands {
    private PlanetCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spacehorizons")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("goto")
                        .then(Commands.literal("earth")
                                .executes(ctx -> gotoEarth(ctx.getSource())))));
    }

    private static int gotoEarth(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Planets.EARTH.id());
        ServerLevel level = source.getServer().getLevel(key);
        if (level == null) {
            source.sendFailure(Component.literal("Dimensão spacehorizons:earth não carregada (datapack?)"));
            return 0;
        }
        // superfície + 2 para não nascer dentro do bloco de topo
        int y = PlanetTerrains.get(Planets.EARTH).surfaceHeight(0, 0) + 2;
        player.teleportTo(level, 0.5, y, 0.5, Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
        source.sendSuccess(() -> Component.literal("Teleportado para a superfície de spacehorizons:earth (y=" + y + ")"), true);
        return 1;
    }
}
