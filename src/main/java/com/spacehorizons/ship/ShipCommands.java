package com.spacehorizons.ship;

import java.util.Collection;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.spacehorizons.SpaceHorizons;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Comandos de desenvolvimento das naves (operadores), spike S2: {@code /spacehorizons ship create [tamanho]} (nave de teste
 * cúbica, de 40 a 128, padrão 64, numa célula nova do estaleiro), {@code ship list}, {@code ship goto <id>}, {@code ship leave} e
 * {@code ship info} (a nave onde o operador está). Num mundo criado sem a dimensão do estaleiro, recusam com uma mensagem.
 */
final class ShipCommands {
    private static final int DEFAULT_SIZE = 64;

    private ShipCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spacehorizons")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("ship")
                        .then(Commands.literal("create")
                                .executes(ctx -> create(ctx.getSource(), DEFAULT_SIZE))
                                .then(Commands.argument("tamanho", IntegerArgumentType.integer(ShipBuilder.MIN_SIZE, ShipYard.MAX_X))
                                        .executes(ctx -> create(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "tamanho")))))
                        .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                        .then(Commands.literal("goto")
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(ctx -> go(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "id")))))
                        .then(Commands.literal("leave").executes(ctx -> leave(ctx.getSource())))
                        .then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))));
    }

    private static ServerLevel requireYard(CommandSourceStack source) {
        ServerLevel yard = ShipYard.level(source.getServer());
        if (yard == null) {
            source.sendFailure(Component.literal("Este mundo não tem a dimensão spacehorizons:shipyard (foi criado antes dela existir): "
                    + "crie um mundo novo com o tipo Space Horizons para usar as naves."));
        }
        return yard;
    }

    private static int create(CommandSourceStack source, int size) {
        ServerLevel yard = requireYard(source);
        if (yard == null) {
            return 0;
        }
        ShipRegistry registry = ShipRegistry.get(source.getServer());
        ServerPlayer owner = source.getPlayer();
        long start = System.nanoTime();
        ShipRecord ship = registry.create("Nave de teste", size, size, size, owner != null ? owner.getUUID() : null, yard.getGameTime());
        ShipChunkTickets.hold(yard, ship, yard.getGameTime());
        ShipBuilder.build(yard, ship.box());
        double ms = (System.nanoTime() - start) / 1.0e6;
        ShipYard.Box box = ship.box();
        SpaceHorizons.LOGGER.info("[ship] nave {} criada: {}x{}x{} na célula {} em ({}, {}, {}), {} colunas de chunk, {} ms", ship.id(), size, size, size,
                ship.cell(), box.minX(), box.minY(), box.minZ(), box.chunkColumns(), String.format(Locale.ROOT, "%.0f", ms));
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Nave %d criada (%dx%dx%d) na célula %d, canto (%d, %d, %d), em %.0f ms. Entre com /spacehorizons ship goto %d",
                ship.id(), size, size, size, ship.cell(), box.minX(), box.minY(), box.minZ(), ms, ship.id())), true);
        return ship.id();
    }

    private static int list(CommandSourceStack source) {
        ShipRegistry registry = ShipRegistry.get(source.getServer());
        Collection<ShipRecord> ships = registry.all();
        if (ships.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Nenhuma nave no estaleiro."), false);
            return 0;
        }
        for (ShipRecord ship : ships) {
            ShipYard.Box box = ship.box();
            int[] cell = ShipYardAllocator.cellCoords(ship.cell());
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "#%d %s %dx%dx%d, célula %d (%d, %d), canto (%d, %d, %d), %s",
                    ship.id(), ship.name(), ship.sizeX(), ship.sizeY(), ship.sizeZ(), ship.cell(), cell[0], cell[1], box.minX(), box.minY(),
                    box.minZ(), ShipChunkTickets.isHeld(ship.id()) ? "carregada" : "congelada")), false);
        }
        return ships.size();
    }

    private static int go(CommandSourceStack source, int id) throws CommandSyntaxException {
        if (requireYard(source) == null) {
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        ShipRecord ship = ShipRegistry.get(source.getServer()).byId(id);
        if (ship == null) {
            source.sendFailure(Component.literal("Não existe a nave " + id + " (veja /spacehorizons ship list)."));
            return 0;
        }
        ShipTransit.enter(player, ship);
        source.sendSuccess(() -> Component.literal("Entrando na nave " + id + "."), false);
        return id;
    }

    private static int leave(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!ShipYard.is(player.level())) {
            source.sendFailure(Component.literal("Você não está no estaleiro."));
            return 0;
        }
        ShipTransit.leave(player);
        return 1;
    }

    private static int info(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!ShipYard.is(player.level())) {
            source.sendFailure(Component.literal("Você não está no estaleiro."));
            return 0;
        }
        ShipRecord ship = ShipRegistry.get(source.getServer()).atPosition(player.getX(), player.getZ());
        if (ship == null) {
            source.sendSuccess(() -> Component.literal("Você está no estaleiro, fora de qualquer célula com nave."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Você está na nave %d (%s), %s, tickets: %d.", ship.id(), ship.name(),
                ShipChunkTickets.isHeld(ship.id()) ? "carregada" : "congelada", ShipChunkTickets.held().size())), false);
        return ship.id();
    }
}
