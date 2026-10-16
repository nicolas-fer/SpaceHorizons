package com.spacehorizons.worldgen;

import java.util.Set;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.map.PlanetMapService;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceClock;
import com.spacehorizons.space.SpaceDimension;
import com.spacehorizons.space.SpaceFrame;
import com.spacehorizons.space.travel.Autopilot;
import com.spacehorizons.space.travel.BodyTransfer;
import com.spacehorizons.space.travel.TravelMath;
import com.spacehorizons.space.travel.TravelTransfers;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Comandos de teste e de viagem:
 * /spacehorizons goto earth|moon (teleporte direto para a superfície, na face voltada para o outro corpo; vindo do espaço
 * aberto, dentro da zona do corpo, é a travessia contínua no mesmo ponto), /spacehorizons goto space [x y z] (para a
 * dimensão de espaço aberto: de um planeta, a travessia contínua no mesmo ponto do espaço; com x y z, para esse ponto da
 * dimensão) e /spacehorizons fly earth|moon|stop (piloto automático pelo espaço, com transferência entre dimensões).
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
                                .executes(ctx -> gotoBody(ctx.getSource(), SolarSystem.MOON)))
                        .then(Commands.literal("space")
                                .executes(ctx -> gotoSpace(ctx.getSource(), null))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(ctx -> gotoSpace(ctx.getSource(), Vec3Argument.getVec3(ctx, "pos"))))))
                .then(Commands.literal("fly")
                        .then(Commands.literal("earth")
                                .executes(ctx -> fly(ctx.getSource(), SolarSystem.EARTH)))
                        .then(Commands.literal("moon")
                                .executes(ctx -> fly(ctx.getSource(), SolarSystem.MOON)))
                        .then(Commands.literal("stop")
                                .executes(ctx -> stopFlying(ctx.getSource())))));
    }

    /**
     * Leva o operador à superfície do corpo, no ponto voltado para o outro corpo (Terra: spawn; Lua: face da Terra). Vindo do
     * espaço aberto e já dentro da zona do corpo (acima da superfície e até a altitude de saída), é a travessia contínua:
     * mesmo ponto do espaço, mesma velocidade e olhar, sem tela de carregamento.
     */
    private static int gotoBody(CommandSourceStack source, SolarSystem.Body body) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlanetDefinition planet = body.planet();
        String name = planet.id().getPath();
        if (SpaceDimension.is(player.level()) && TravelTransfers.isReady(player)) {
            double[] space = new double[3];
            SpaceDimension.toSpace(player.getX(), player.getY(), player.getZ(), space);
            double altitude = TravelMath.altitudeOver(body, SpaceClock.ticks(player.level()), space[0], space[1], space[2]);
            if (altitude > 0.0 && altitude <= TravelMath.exitAltitude(body) && TravelTransfers.transferFromSpace(player, body)) {
                source.sendSuccess(() -> Component.literal("De volta a " + name + " no mesmo ponto do espaço (altitude " + Math.round(altitude) + ")"), true);
                return 1;
            }
        }
        // procura pelo tipo de dimensão: com o tipo de mundo "Space Horizons" a Terra é o próprio Overworld
        ServerLevel level = TravelTransfers.findLevel(source.getServer(), planet);
        if (level == null) {
            source.sendFailure(Component.literal("Este mundo não tem a dimensão de " + name
                    + ": crie um mundo com o tipo \"Space Horizons\""));
            return 0;
        }
        double[] facing = new double[2];
        TravelMath.facingPoint(body, facing);
        // heightmap real do chunk quando carregado, senão o do gerador do nível (nunca o mapa aproximado): fica sobre o chão ou a água
        int y = PlanetMapService.landingY(level, (int) Math.floor(facing[0]), (int) Math.floor(facing[1]));
        player.teleportTo(level, facing[0], y, facing[1], Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
        Identifier dimension = level.dimension().identifier();
        source.sendSuccess(() -> Component.literal("Teleportado para a superfície de " + name + " em " + dimension + " (y=" + y + ")"), true);
        return 1;
    }

    /**
     * Leva o operador à dimensão de espaço aberto. De um planeta e sem coordenadas: a travessia contínua no mesmo ponto do
     * espaço (a posição, a velocidade e o olhar viram os da dimensão de espaço). Com x y z (coordenadas da dimensão de espaço,
     * as do F3 de lá): para esse ponto, parado; do próprio espaço é só um teleporte.
     */
    private static int gotoSpace(CommandSourceStack source, @Nullable Vec3 target) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel space = TravelTransfers.findSpace(source.getServer());
        if (space == null) {
            source.sendFailure(Component.literal("Este mundo não tem a dimensão de espaço: crie um mundo com o tipo \"Space Horizons\""));
            return 0;
        }
        if (SpaceDimension.is(player.level())) {
            if (target == null) {
                source.sendFailure(Component.literal("Você já está no espaço; informe x y z para ir a um ponto dele"));
                return 0;
            }
            player.teleportTo(space, target.x, target.y, target.z, Set.<Relative>of(), player.getYRot(), player.getXRot(), true);
            source.sendSuccess(() -> Component.literal("Teleportado no espaço para " + describeSpace(target.x, target.y, target.z)), true);
            return 1;
        }
        SolarSystem.Body from = Planets.forLevel(player.level()).flatMap(SolarSystem::bodyOf).orElse(null);
        if (from == null) {
            source.sendFailure(Component.literal("Este mundo não é um planeta do Space Horizons"));
            return 0;
        }
        if (!TravelTransfers.isReady(player)) {
            source.sendFailure(Component.literal("O jogador ainda está carregando; tente de novo"));
            return 0;
        }
        boolean ok;
        Vec3 at;
        if (target == null) {
            double[] p = new double[3];
            SpaceFrame.toSpace(from, SpaceClock.ticks(player.level()), player.getX(), player.getY(), player.getZ(), p);
            SpaceDimension.fromSpace(p[0], p[1], p[2], p);
            at = new Vec3(p[0], p[1], p[2]);
            ok = TravelTransfers.transferToSpace(player, from);
        } else {
            at = target;
            ok = TravelTransfers.transfer(player, new BodyTransfer(null, target.x, target.y, target.z, 0.0, 0.0, 0.0,
                    player.getYRot(), player.getXRot()), space);
        }
        if (!ok) {
            source.sendFailure(Component.literal("A transferência para o espaço foi recusada"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("No espaço em " + describeSpace(at.x, at.y, at.z)), true);
        return 1;
    }

    /** Ponto da dimensão de espaço e o corpo mais próximo dele, para a mensagem do comando. */
    private static String describeSpace(double x, double y, double z) {
        double[] s = new double[3];
        SpaceDimension.toSpace(x, y, z, s);
        // só a Lua se move e só a mensagem usa isto: o relógio 0 basta para dizer "perto de quê"
        SolarSystem.Body nearest = TravelMath.nearestBody(0.0, s[0], s[1], s[2]);
        return String.format("(%.1f, %.1f, %.1f), perto de %s", x, y, z, nearest.planet().id().getPath());
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
