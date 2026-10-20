package com.spacehorizons.ship;

import java.util.Locale;

import com.spacehorizons.space.flight.RocketStats;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** O que a ponte e os comandos dizem da nave: a especificação calculada do censo ({@link ShipSpecs}). */
public final class ShipInfo {
    /** Ticks por segundo: o Δv sai em blocos por segundo para o jogador (a física é em blocos por tick). */
    private static final double TICKS_PER_SECOND = 20.0;

    private ShipInfo() {
    }

    /** Clique na ponte: conta a nave onde a ponte está e mostra a especificação ao jogador. */
    public static void reportFor(ServerLevel level, BlockPos bridge, ServerPlayer player) {
        if (!ShipYard.is(level)) {
            player.sendSystemMessage(Component.translatable("message.spacehorizons.ship.bridge_outside_yard"));
            return;
        }
        MinecraftServer server = level.getServer();
        ShipRecord ship = ShipRegistry.get(server).atPosition(bridge.getX(), bridge.getZ());
        if (ship == null) {
            player.sendSystemMessage(Component.translatable("message.spacehorizons.ship.bridge_no_ship"));
            return;
        }
        ShipCensusResult result = ShipCensusService.scanNow(server, ship);
        player.sendSystemMessage(describe(ship, result, result.tankFuelMb()));
    }

    /** A especificação em uma frase: blocos, massa seca, empuxo, motores, assentos, combustível e Δv. */
    public static Component describe(ShipRecord ship, ShipCensusResult result, double fuelMb) {
        RocketStats stats = ShipSpecs.statsFor(result.census());
        return Component.translatable("message.spacehorizons.ship.specs", ship.name(), ship.id(), result.census().total(),
                String.format(Locale.ROOT, "%.1f", stats.dryMassT()), String.format(Locale.ROOT, "%.1f", stats.thrust()), result.census().engines(),
                result.census().seats(), String.format(Locale.ROOT, "%.0f", fuelMb), String.format(Locale.ROOT, "%.0f", stats.fuelCapacityMb()),
                String.format(Locale.ROOT, "%.0f", stats.deltaV(fuelMb) * TICKS_PER_SECOND));
    }

    /** O mesmo em texto puro (log e autoteste). */
    public static String line(ShipRecord ship, ShipCensusResult result, double fuelMb) {
        RocketStats stats = ShipSpecs.statsFor(result.census());
        ShipCensus c = result.census();
        return String.format(Locale.ROOT, "nave %d: %d blocos (ponte %d, motores %d, tanques %d, janelas %d, assentos %d, eclusas %d, estrutura %d), massa seca %.2f t, "
                + "empuxo %.1f, Isp %.0f ticks, combustível %.0f/%.0f mB, Δv %.2f b/tick (%.1f b/s)", ship.id(), c.total(), c.bridges(), c.engines(),
                c.tanks(), c.windows(), c.seats(), c.airlocks(), c.structures(), stats.dryMassT(), stats.thrust(), stats.ispTicks(), fuelMb,
                stats.fuelCapacityMb(), stats.deltaV(fuelMb), stats.deltaV(fuelMb) * TICKS_PER_SECOND);
    }
}
