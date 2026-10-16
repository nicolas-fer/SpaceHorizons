package com.spacehorizons.rocket;

import java.util.Comparator;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Comandos de teste do foguete (operadores): {@code /spacehorizons rocket assemble <pos>|disassemble|nudge <dx> <dy> <dz>|yaw
 * <graus>|info}. {@code disassemble} é o pouso de volta a blocos; {@code nudge} e {@code yaw} movem e giram a entidade
 * (a física de voo só vem na etapa 3b) para ver o retrato fora do chão e testar o arredondamento do yaw ao pousar.
 */
final class RocketCommands {
    /** Raio de busca do foguete mais perto do operador, em blocos. */
    private static final double SEARCH_RADIUS = 48.0;

    private RocketCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spacehorizons")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("rocket")
                        .then(Commands.literal("assemble")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> assemble(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos")))))
                        .then(Commands.literal("disassemble").executes(ctx -> disassemble(ctx.getSource())))
                        .then(Commands.literal("nudge")
                                .then(Commands.argument("dx", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("dy", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("dz", DoubleArgumentType.doubleArg())
                                                        .executes(ctx -> nudge(ctx.getSource(),
                                                                DoubleArgumentType.getDouble(ctx, "dx"),
                                                                DoubleArgumentType.getDouble(ctx, "dy"),
                                                                DoubleArgumentType.getDouble(ctx, "dz")))))))
                        .then(Commands.literal("yaw")
                                .then(Commands.argument("graus", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> yaw(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "graus")))))
                        .then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))));
    }

    private static int assemble(CommandSourceStack source, BlockPos pos) {
        try {
            RocketEntity rocket = RocketAssembler.assemble(source.getLevel(), pos, null);
            source.sendSuccess(() -> Component.translatable("message.spacehorizons.rocket.assembled", rocket.portrait().blockCount(),
                    String.format(java.util.Locale.ROOT, "%.1f", rocket.portrait().stats().dryMassT()),
                    String.format(java.util.Locale.ROOT, "%.0f", rocket.fuelMb())), true);
            return rocket.portrait().blockCount();
        } catch (RocketException e) {
            source.sendFailure(e.component());
            return 0;
        }
    }

    private static int disassemble(CommandSourceStack source) throws CommandSyntaxException {
        RocketEntity rocket = findRocket(source);
        if (rocket == null) {
            return 0;
        }
        try {
            int blocks = rocket.portrait().blockCount();
            RocketLanding.land(source.getLevel(), rocket);
            source.sendSuccess(() -> Component.translatable("message.spacehorizons.rocket.landed", blocks), true);
            return blocks;
        } catch (RocketException e) {
            source.sendFailure(e.component());
            return 0;
        }
    }

    private static int nudge(CommandSourceStack source, double dx, double dy, double dz) throws CommandSyntaxException {
        RocketEntity rocket = findRocket(source);
        if (rocket == null) {
            return 0;
        }
        rocket.teleportTo(rocket.getX() + dx, rocket.getY() + dy, rocket.getZ() + dz);
        Vec3 now = rocket.position();
        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT, "Foguete movido para %.2f %.2f %.2f", now.x, now.y, now.z)), true);
        return 1;
    }

    private static int yaw(CommandSourceStack source, double degrees) throws CommandSyntaxException {
        RocketEntity rocket = findRocket(source);
        if (rocket == null) {
            return 0;
        }
        // yaw horário visto de cima = giro de -yaw em torno de +Y (o mesmo sentido do yaw do Minecraft e de Rotation.CLOCKWISE_90)
        rocket.setAttitude(RocketAttitude.fromYaw(degrees));
        rocket.teleportTo(rocket.getX(), rocket.getY(), rocket.getZ());
        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT, "Foguete girado para yaw %.1f", rocket.yawDegrees())), true);
        return 1;
    }

    private static int info(CommandSourceStack source) throws CommandSyntaxException {
        RocketEntity rocket = findRocket(source);
        if (rocket == null) {
            return 0;
        }
        RocketPortrait portrait = rocket.portrait();
        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "Foguete: %d blocos, caixa %dx%dx%d, massa seca %.1f t, empuxo %.1f, combustível %.0f/%.0f mB, modo %s, yaw %.1f, inclinação %.1f",
                portrait.blockCount(), portrait.sizeX, portrait.sizeY, portrait.sizeZ, rocket.stats().dryMassT(), rocket.stats().thrust(),
                rocket.fuelMb(), rocket.stats().fuelCapacityMb(), rocket.flightMode(), rocket.yawDegrees(), rocket.tiltDegrees())), false);
        return portrait.blockCount();
    }

    /** O foguete em que o operador está sentado, ou o mais próximo dentro de {@link #SEARCH_RADIUS}; responde com a falha e devolve null se não houver. */
    private static RocketEntity findRocket(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (player.getVehicle() instanceof RocketEntity ridden) {
            return ridden;
        }
        List<RocketEntity> found = player.level().getEntitiesOfClass(RocketEntity.class, player.getBoundingBox().inflate(SEARCH_RADIUS));
        RocketEntity nearest = found.stream().min(Comparator.comparingDouble(r -> r.distanceToSqr(player))).orElse(null);
        if (nearest == null) {
            source.sendFailure(new RocketException(RocketException.Reason.NO_ROCKET).component());
        }
        return nearest;
    }
}
