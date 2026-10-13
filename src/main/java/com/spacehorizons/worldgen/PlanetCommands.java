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
import net.minecraft.resources.Identifier;
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
        // com o tipo de mundo "Space Horizons" a Terra é o próprio Overworld; procura pelo tipo de dimensão
        ServerLevel level = null;
        for (ServerLevel candidate : source.getServer().getAllLevels()) {
            if (Planets.forLevel(candidate).filter(Planets.EARTH::equals).isPresent()) {
                level = candidate;
                break;
            }
        }
        if (level == null) {
            source.sendFailure(Component.literal("Este mundo não tem a Terra curva: crie um mundo com o tipo \"Space Horizons\""));
            return 0;
        }
        // superfície + 2 para não nascer dentro do bloco de topo
        int y = PlanetTerrains.get(Planets.EARTH).surfaceHeight(0, 0) + 2;
        player.teleportTo(level, 0.5, y, 0.5, Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
        Identifier dimension = level.dimension().identifier();
        source.sendSuccess(() -> Component.literal("Teleportado para a superfície da Terra em " + dimension + " (y=" + y + ")"), true);
        return 1;
    }
}
