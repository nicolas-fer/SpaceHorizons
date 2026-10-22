package com.spacehorizons.ship.deck;

import java.util.List;
import java.util.Locale;

import org.joml.Vector3d;

import com.mojang.brigadier.CommandDispatcher;
import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.RocketEntity;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Ligações do convés andável com o NeoForge (por evento, registradas sozinhas pelo {@link EventBusSubscriber}): os pacotes de posição local
 * (cliente para servidor) e de embarque, saída e correção (servidor para cliente; os handlers do cliente ficam em {@code client/ship/DeckClient}),
 * os comandos {@code /spacehorizons deck ...} e a limpeza ao sair do servidor.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID)
public final class DeckSetup {
    private DeckSetup() {
    }

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(DeckMovePayload.TYPE, DeckMovePayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                DeckCrew.onMove(player, payload);
            }
        });
        registrar.playToClient(DeckBoardPayload.TYPE, DeckBoardPayload.CODEC);
        registrar.playToClient(DeckLeavePayload.TYPE, DeckLeavePayload.CODEC);
        registrar.playToClient(DeckCorrectionPayload.TYPE, DeckCorrectionPayload.CODEC);
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DeckCrew.onLogout(player);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        DeckCrew.reset();
    }

    // ---------------------------------------------------------------- comandos

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spacehorizons")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("deck")
                        .then(Commands.literal("board").executes(ctx -> board(ctx.getSource())))
                        .then(Commands.literal("leave").executes(ctx -> leave(ctx.getSource())))
                        .then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))));
    }

    /** A nave mais perto (até 160 blocos) cuja caixa do retrato, mais a margem, contém o jogador. */
    private static RocketEntity shipAround(ServerPlayer player) {
        List<RocketEntity> near = player.level().getEntitiesOfClass(RocketEntity.class, player.getBoundingBox().inflate(160.0));
        RocketEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (RocketEntity rocket : near) {
            if (rocket.portrait().isEmpty()) {
                continue;
            }
            Vector3d local = rocket.deckFrame().toLocal(player.getX(), player.getY(), player.getZ());
            var p = rocket.portrait();
            boolean inside = local.x >= -1.0 && local.y >= -1.0 && local.z >= -1.0 && local.x <= p.sizeX + 1.0 && local.y <= p.sizeY + 1.0
                    && local.z <= p.sizeZ + 1.0;
            double d = rocket.distanceToSqr(player);
            if (inside && d < bestDistance) {
                best = rocket;
                bestDistance = d;
            }
        }
        return best;
    }

    private static int board(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("só um jogador embarca no convés"));
            return 0;
        }
        RocketEntity rocket = shipAround(player);
        if (rocket == null) {
            source.sendFailure(Component.literal("nenhuma nave com você dentro da caixa dela"));
            return 0;
        }
        if (!DeckCrew.boardAtWorld(player, rocket)) {
            source.sendFailure(Component.literal("não foi possível embarcar (já embarcado, sentado ou em outra dimensão)"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("no convés da nave " + rocket.getId()), false);
        return 1;
    }

    private static int leave(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player) || !DeckCrew.isCrew(player)) {
            source.sendFailure(Component.literal("você não está no convés de uma nave"));
            return 0;
        }
        DeckCrew.leave(player, "comando");
        return 1;
    }

    private static int info(CommandSourceStack source) {
        long accepted = DeckCrew.ACCEPTED.get();
        String line = String.format(Locale.ROOT, "convés: estados aceitos %d, recusados %d, saídas pelo limite %d", accepted, DeckCrew.REJECTED.get(),
                DeckCrew.LEFT_BOX.get());
        if (source.getEntity() instanceof ServerPlayer player && DeckCrew.member(player.getUUID()) != null) {
            DeckState s = DeckCrew.member(player.getUUID()).state();
            line += String.format(Locale.ROOT, "; você está em (%.2f, %.2f, %.2f) locais, no chão: %s", s.pos.x, s.pos.y, s.pos.z, s.onGround);
        }
        String text = line;
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
