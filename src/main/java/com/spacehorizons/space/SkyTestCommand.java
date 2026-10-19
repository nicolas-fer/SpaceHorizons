package com.spacehorizons.space;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.spacehorizons.SpaceHorizons;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Comando de desenvolvimento do spike S1 das naves (céu virtual com atitude), só para operadores:
 * <ul>
 *   <li>{@code /spacehorizons skytest <yaw> <pitch> <roll>} (graus): o céu do espaço aberto sai desenhado como se o jogador
 *       estivesse numa nave com essa atitude (eixos locais x leste, y cima, z norte);</li>
 *   <li>{@code /spacehorizons skytest spin <graus/s> [x|y|z]}: gira continuamente em torno do eixo local (padrão y);</li>
 *   <li>{@code /spacehorizons skytest off}: volta à orientação fixa da dimensão.</li>
 * </ul>
 * O estado fica só no cliente (SkyAttitude); aqui o comando apenas envia o {@link SkyTestPayload}. Registrada sozinha pelo
 * {@link EventBusSubscriber}; o handler do cliente está em client/space (SkyTestClient). Ver docs/PLANO_NAVES.md, seção 4.2.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID)
public final class SkyTestCommand {
    private SkyTestCommand() {
    }

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(SkyTestPayload.TYPE, SkyTestPayload.CODEC);
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> spin = Commands.literal("spin");
        spin.then(Commands.argument("degPerSecond", DoubleArgumentType.doubleArg())
                .executes(ctx -> spin(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "degPerSecond"), 1))
                .then(Commands.literal("x").executes(ctx -> spin(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "degPerSecond"), 0)))
                .then(Commands.literal("y").executes(ctx -> spin(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "degPerSecond"), 1)))
                .then(Commands.literal("z").executes(ctx -> spin(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "degPerSecond"), 2))));
        event.getDispatcher().register(Commands.literal("spacehorizons")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("skytest")
                        .then(Commands.literal("off").executes(ctx -> send(ctx.getSource(), new SkyTestPayload(0, 0, 0, 0, 0), "desligado")))
                        .then(spin)
                        .then(Commands.argument("yaw", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("pitch", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("roll", DoubleArgumentType.doubleArg())
                                                .executes(ctx -> {
                                                    double yaw = DoubleArgumentType.getDouble(ctx, "yaw");
                                                    double pitch = DoubleArgumentType.getDouble(ctx, "pitch");
                                                    double roll = DoubleArgumentType.getDouble(ctx, "roll");
                                                    return send(ctx.getSource(), new SkyTestPayload(1, yaw, pitch, roll, 0),
                                                            "atitude yaw=" + yaw + " pitch=" + pitch + " roll=" + roll);
                                                }))))));
    }

    private static int spin(CommandSourceStack source, double degPerSecond, int axis) throws CommandSyntaxException {
        return send(source, new SkyTestPayload(2, degPerSecond, 0, 0, axis), "giro " + degPerSecond + " graus/s no eixo " + "xyz".charAt(axis));
    }

    private static int send(CommandSourceStack source, SkyTestPayload payload, String what) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PacketDistributor.sendToPlayer(player, payload);
        source.sendSuccess(() -> Component.literal("Céu de teste (só visível no espaço aberto): " + what), true);
        return 1;
    }
}
