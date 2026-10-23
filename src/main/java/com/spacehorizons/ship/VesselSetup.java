package com.spacehorizons.ship;

import java.util.Locale;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.space.vessel.VesselEntry;
import com.spacehorizons.space.vessel.VesselHullPartPayload;
import com.spacehorizons.space.vessel.VesselHullRequestPayload;
import com.spacehorizons.space.vessel.VesselStatePayload;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Ligações com o NeoForge dos outros veículos vistos no espaço (fase 2d das naves): os pacotes (estado e casco, servidor para cliente; pedido de
 * casco, cliente para servidor), o envio a cada tick ({@link VesselSync}) e o comando de desenvolvimento {@code /spacehorizons vessel}.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID)
public final class VesselSetup {
    private VesselSetup() {
    }

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        // os handlers do cliente ficam em client/space (RegisterClientPayloadHandlersEvent)
        event.registrar("1").playToClient(VesselStatePayload.TYPE, VesselStatePayload.CODEC);
        event.registrar("1").playToClient(VesselHullPartPayload.TYPE, VesselHullPartPayload.CODEC);
        event.registrar("1").playToServer(VesselHullRequestPayload.TYPE, VesselHullRequestPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                VesselHulls.request(player, payload.id(), payload.version());
            }
        });
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        VesselDev.tickFreeze(event.getServer());
        VesselSync.tick(event.getServer());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        VesselSync.reset();
        VesselHulls.reset();
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("spacehorizons")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("vessel")
                        .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                        .then(Commands.literal("freeze")
                                .then(Commands.literal("on").executes(ctx -> freeze(ctx.getSource(), true)))
                                .then(Commands.literal("off").executes(ctx -> freeze(ctx.getSource(), false))))
                        .then(Commands.literal("yaw")
                                .then(Commands.argument("nave", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("graus", DoubleArgumentType.doubleArg(-360.0, 360.0))
                                                .executes(ctx -> yaw(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "nave"),
                                                        DoubleArgumentType.getDouble(ctx, "graus"))))))
                        .then(Commands.literal("rocketboard")
                                .then(Commands.argument("nave", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("dx", DoubleArgumentType.doubleArg(-5000.0, 5000.0))
                                                .then(Commands.argument("dy", DoubleArgumentType.doubleArg(-5000.0, 5000.0))
                                                        .then(Commands.argument("dz", DoubleArgumentType.doubleArg(-5000.0, 5000.0))
                                                                .executes(ctx -> board(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "nave"),
                                                                        DoubleArgumentType.getDouble(ctx, "dx"), DoubleArgumentType.getDouble(ctx, "dy"),
                                                                        DoubleArgumentType.getDouble(ctx, "dz"))))))))));
    }

    private static int list(CommandSourceStack source) {
        var all = VesselSync.collect(source.getServer(), source.getServer().overworld().getGameTime());
        for (VesselEntry e : all.values()) {
            String line = String.format(Locale.ROOT, "%s %s raio=%.1f E=(%.1f, %.1f, %.1f) v=(%.3f, %.3f, %.3f)", e.kind() == VesselEntry.SHIP ? "nave" : "foguete",
                    e.id(), e.radius(), e.pos()[0], e.pos()[1], e.pos()[2], e.velocity()[0], e.velocity()[1], e.velocity()[2]);
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return all.size();
    }

    private static int freeze(CommandSourceStack source, boolean on) {
        VesselDev.freeze(source.getServer(), on);
        source.sendSuccess(() -> Component.literal("Veículos " + (on ? "congelados" : "soltos") + " (só teste)"), true);
        return 1;
    }

    private static int yaw(CommandSourceStack source, int ship, double degrees) {
        try {
            VesselDev.yawShip(source.getServer(), ship, degrees);
        } catch (IllegalStateException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Nave " + ship + " girada " + degrees + " graus em torno do cima dela"), true);
        return 1;
    }

    private static int board(CommandSourceStack source, int ship, double dx, double dy, double dz) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        try {
            VesselDev.boardRocket(player, ship, dx, dy, dz, 4_000);
        } catch (IllegalStateException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        return 1;
    }
}
