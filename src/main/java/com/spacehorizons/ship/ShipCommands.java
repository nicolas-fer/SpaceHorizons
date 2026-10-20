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
import net.minecraft.world.phys.Vec3;

import com.spacehorizons.rocket.RocketEntity;

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
                                .then(Commands.literal("flight").executes(ctx -> createFlight(ctx.getSource())))
                                .then(Commands.argument("tamanho", IntegerArgumentType.integer(ShipBuilder.MIN_SIZE, ShipYard.MAX_X))
                                        .executes(ctx -> create(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "tamanho")))))
                        .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                        .then(Commands.literal("goto")
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(ctx -> go(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "id")))))
                        .then(Commands.literal("leave").executes(ctx -> leave(ctx.getSource())))
                        .then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
                        .then(Commands.literal("fromrocket").executes(ctx -> fromRocket(ctx.getSource())))
                        .then(Commands.literal("torocket")
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(ctx -> toRocket(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "id")))))
                        .then(Commands.literal("genrocket")
                                .then(Commands.argument("blocos", IntegerArgumentType.integer(100, 12_000))
                                        .executes(ctx -> genRocket(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "blocos")))))
                        .then(Commands.literal("budget")
                                .executes(ctx -> budget(ctx.getSource(), -1, -1))
                                .then(Commands.argument("unidades", IntegerArgumentType.integer(1, 1_000_000))
                                        .executes(ctx -> budget(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "unidades"), -1))
                                        .then(Commands.argument("ms", IntegerArgumentType.integer(0, 1000))
                                                .executes(ctx -> budget(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "unidades"),
                                                        IntegerArgumentType.getInteger(ctx, "ms"))))))
                        .then(Commands.literal("census")
                                .executes(ctx -> census(ctx.getSource(), -1))
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(ctx -> census(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "id")))))
                        .then(Commands.literal("convstatus").executes(ctx -> convStatus(ctx.getSource())))));
    }

    /** Conta os blocos da nave (a do id, ou a que cerca o operador) e mostra a especificação: massa, empuxo, Δv e combustível. */
    private static int census(CommandSourceStack source, int id) {
        if (requireYard(source) == null) {
            return 0;
        }
        ShipRegistry registry = ShipRegistry.get(source.getServer());
        ShipRecord ship = id > 0 ? registry.byId(id) : null;
        if (id <= 0 && source.getPlayer() != null && ShipYard.is(source.getPlayer().level())) {
            ship = registry.atPosition(source.getPlayer().getX(), source.getPlayer().getZ());
        }
        if (ship == null) {
            source.sendFailure(Component.literal(id > 0 ? "Não existe a nave " + id + " (veja /spacehorizons ship list)."
                    : "Informe o id da nave ou rode o comando dentro de uma."));
            return 0;
        }
        long start = System.nanoTime();
        ShipCensusResult result = ShipCensusService.scanNow(source.getServer(), ship);
        double ms = (System.nanoTime() - start) / 1.0e6;
        String line = ShipInfo.line(ship, result, result.tankFuelMb()) + String.format(Locale.ROOT, " [censo em %.1f ms]", ms);
        SpaceHorizons.LOGGER.info("[ship] {}", line);
        source.sendSuccess(() -> Component.literal(line), false);
        return ship.id();
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

    /**
     * Cria a nave de voo de teste (criativo, sem montagem no chão): ponte, três assentos, quatro motores, oito tanques cheios, janelas e
     * anteparo com eclusa, já em órbita circular da Terra a 30.000 blocos com a Terra à direita. Mede o censo e confere com o que foi colocado.
     */
    private static int createFlight(CommandSourceStack source) {
        ServerLevel yard = requireYard(source);
        if (yard == null) {
            return 0;
        }
        ShipRegistry registry = ShipRegistry.get(source.getServer());
        ServerPlayer owner = source.getPlayer();
        long start = System.nanoTime();
        ShipRecord ship = registry.create("Nave de voo", ShipBuilder.FLIGHT_SX, ShipBuilder.FLIGHT_SY, ShipBuilder.FLIGHT_SZ,
                owner != null ? owner.getUUID() : null, yard.getGameTime());
        ShipChunkTickets.hold(yard, ship, yard.getGameTime());
        ShipBuilder.FlightLayout layout = ShipBuilder.buildFlightShip(yard, ship.box());
        ShipCensusResult census = ShipCensusService.scanNow(source.getServer(), ship);
        ShipFlightTicker.start(source.getServer(), ship, census);
        double ms = (System.nanoTime() - start) / 1.0e6;
        boolean same = census.census().equals(layout.expected());
        String line = ShipInfo.line(ship, census, census.tankFuelMb());
        SpaceHorizons.LOGGER.info("[ship] nave de voo {} criada em {} ms; censo {} o esperado ({}); {}", ship.id(), String.format(Locale.ROOT, "%.0f", ms),
                same ? "igual a" : "DIFERENTE do", layout.expected(), line);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Nave de voo %d criada em %.0f ms. %s. Entre com /spacehorizons ship goto %d e sente no assento da ponte.",
                ship.id(), ms, line, ship.id())), true);
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

    // ---------------------------------------------------------------- conversão entidade <-> blocos (spike S3)

    /** O foguete que o jogador olha (o raio de visão até 128 blocos), ou o mais perto dentro de 64 blocos. */
    private static RocketEntity findRocket(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(128.0));
        RocketEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (RocketEntity rocket : player.level().getEntitiesOfClass(RocketEntity.class, player.getBoundingBox().inflate(160.0))) {
            java.util.Optional<Vec3> hit = rocket.getBoundingBox().clip(eye, end);
            if (hit.isPresent() && eye.distanceToSqr(hit.get()) < bestDist) {
                bestDist = eye.distanceToSqr(hit.get());
                best = rocket;
            }
        }
        if (best == null) {
            for (RocketEntity rocket : player.level().getEntitiesOfClass(RocketEntity.class, player.getBoundingBox().inflate(64.0))) {
                double d = rocket.distanceToSqr(player);
                if (d < bestDist) {
                    bestDist = d;
                    best = rocket;
                }
            }
        }
        return best;
    }

    private static int fromRocket(CommandSourceStack source) throws CommandSyntaxException {
        if (requireYard(source) == null) {
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        RocketEntity rocket = findRocket(player);
        if (rocket == null) {
            source.sendFailure(Component.literal("Nenhum foguete à vista (olhe para um) nem a menos de 64 blocos."));
            return 0;
        }
        try {
            int id = ShipConversions.startRocketToYard((ServerLevel) player.level(), rocket);
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Convertendo o foguete (%d blocos) na nave %d, em lotes de %d blocos/tick (teto %d ms/tick).",
                    rocket.portrait().blockCount(), id, ConversionBudget.unitsPerTick(), ConversionBudget.millisPerTick())), true);
            return id;
        } catch (ShipConversionException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int toRocket(CommandSourceStack source, int id) {
        if (requireYard(source) == null) {
            return 0;
        }
        try {
            ShipConversions.startYardToRocket(source.getServer(), id);
            source.sendSuccess(() -> Component.literal("Devolvendo a nave " + id + " ao foguete, em lotes."), true);
            return id;
        } catch (ShipConversionException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int genRocket(CommandSourceStack source, int blocks) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = (ServerLevel) player.level();
        ShipTestRocket.Spec spec = ShipTestRocket.preset(blocks);
        long t0 = System.nanoTime();
        ShipTestRocket.Built built = ShipTestRocket.build(level.registryAccess(), spec);
        Vec3 center = new Vec3(player.getX() + 10 + spec.sx() / 2.0, player.getY() + spec.sy() / 2.0, player.getZ());
        ShipTestRocket.spawn(level, center, built);
        double ms = (System.nanoTime() - t0) / 1.0e6;
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Foguete de teste: %d blocos (%dx%dx%d), %d baús, %d itens, %d mB nos tanques, gerado em %.0f ms.",
                built.portrait().blockCount(), spec.sx(), spec.sy(), spec.sz(), built.chests(), built.expectedTotalItems(), built.tankFuelMb(), ms)), true);
        return built.portrait().blockCount();
    }

    private static int budget(CommandSourceStack source, int units, int ms) {
        if (units > 0) {
            ConversionBudget.configure(units, ms >= 0 ? ms : ConversionBudget.millisPerTick());
        }
        source.sendSuccess(() -> Component.literal("Orçamento da conversão: " + ConversionBudget.unitsPerTick() + " blocos por tick, teto de "
                + ConversionBudget.millisPerTick() + " ms por tick (0 = sem teto)."), false);
        return ConversionBudget.unitsPerTick();
    }

    private static int convStatus(CommandSourceStack source) {
        var reports = ShipConversions.reports();
        source.sendSuccess(() -> Component.literal("Conversão em andamento: " + ShipConversions.busy() + "; relatórios nesta sessão: " + reports.size()), false);
        if (!reports.isEmpty()) {
            source.sendSuccess(() -> Component.literal(reports.get(reports.size() - 1).line()), false);
        }
        return reports.size();
    }
}
