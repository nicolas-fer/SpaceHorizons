package com.spacehorizons.worldgen;

import java.util.Set;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrains;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.travel.Autopilot;
import com.spacehorizons.space.travel.TravelMath;
import com.spacehorizons.space.travel.TravelTransfers;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;

/**
 * Comandos de teste e de viagem:
 * /spacehorizons goto earth|moon (teleporte direto para a superfície, na face voltada para o outro corpo) e
 * /spacehorizons fly earth|moon|stop (piloto automático pelo espaço, com transferência entre dimensões).
 */
final class PlanetCommands {
    private PlanetCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spacehorizons")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("goto")
                        .then(Commands.literal("earth")
                                .executes(ctx -> gotoBody(ctx.getSource(), SolarSystem.EARTH)))
                        .then(Commands.literal("moon")
                                .executes(ctx -> gotoBody(ctx.getSource(), SolarSystem.MOON))))
                .then(Commands.literal("fly")
                        .then(Commands.literal("earth")
                                .executes(ctx -> fly(ctx.getSource(), SolarSystem.EARTH)))
                        .then(Commands.literal("moon")
                                .executes(ctx -> fly(ctx.getSource(), SolarSystem.MOON)))
                        .then(Commands.literal("stop")
                                .executes(ctx -> stopFlying(ctx.getSource())))));
    }

    /** Leva o operador à superfície do corpo, no ponto voltado para o outro corpo (Terra: spawn; Lua: face da Terra). */
    private static int gotoBody(CommandSourceStack source, SolarSystem.Body body) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlanetDefinition planet = body.planet();
        String name = planet.id().getPath();
        // procura pelo tipo de dimensão: com o tipo de mundo "Space Horizons" a Terra é o próprio Overworld
        ServerLevel level = TravelTransfers.findLevel(source.getServer(), planet);
        if (level == null) {
            source.sendFailure(Component.literal("Este mundo não tem a dimensão de " + name
                    + ": crie um mundo com o tipo \"Space Horizons\""));
            return 0;
        }
        double[] facing = new double[2];
        TravelMath.facingPoint(body, facing);
        // superfície + 2 para não nascer dentro do bloco de topo
        int y = PlanetTerrains.get(planet).surfaceHeight((int) Math.floor(facing[0]), (int) Math.floor(facing[1])) + 2;
        player.teleportTo(level, facing[0], y, facing[1], Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
        Identifier dimension = level.dimension().identifier();
        source.sendSuccess(() -> Component.literal("Teleportado para a superfície de " + name + " em " + dimension + " (y=" + y + ")"), true);
        return 1;
    }

    private static int fly(CommandSourceStack source, SolarSystem.Body target) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Component error = Autopilot.start(player, target);
        if (error != null) {
            source.sendFailure(error);
            return 0;
        }
        String name = target.planet().id().getPath();
        source.sendSuccess(() -> Component.literal("Piloto automático a caminho de " + name + " (/spacehorizons fly stop cancela)"), true);
        return 1;
    }

    private static int stopFlying(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!Autopilot.stop(player)) {
            source.sendFailure(Component.literal("O piloto automático não está ativo"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Piloto automático cancelado"), true);
        return 1;
    }
}
