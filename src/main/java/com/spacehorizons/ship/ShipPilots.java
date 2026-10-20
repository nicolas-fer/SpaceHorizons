package com.spacehorizons.ship;

import com.spacehorizons.rocket.RocketControlPayload;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

/**
 * Quem pilota uma nave (docs/PLANO_NAVES.md, seção 5): o jogador sentado num assento dela, um piloto por nave (o primeiro sentado, na ordem da
 * lista de jogadores); os demais andam livres pelo interior. O comando só vale se vier do piloto, e o servidor decide a nave pelo assento (não
 * confia no cliente para isso).
 */
final class ShipPilots {
    private ShipPilots() {
    }

    /** O piloto da nave (sentado num assento dentro da célula dela), ou null. */
    static @Nullable ServerPlayer pilotOf(MinecraftServer server, ShipRecord ship) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getVehicle() instanceof ShipSeatEntity && ShipYard.is(player.level())
                    && ShipYard.cellContains(ship.cell(), player.getX(), player.getZ())) {
                return player;
            }
        }
        return null;
    }

    /** O piloto, se o jogador está sentado no assento de uma nave; a nave dele sai do registro pela posição. */
    static @Nullable ShipRecord shipOf(MinecraftServer server, ServerPlayer player) {
        if (!(player.getVehicle() instanceof ShipSeatEntity) || !ShipYard.is(player.level())) {
            return null;
        }
        return ShipRegistry.get(server).atPosition(player.getX(), player.getZ());
    }

    /**
     * Um comando do piloto chegou (pacote do cliente). Vale só do piloto sentado; o bit "levantar" ({@link RocketControlPayload#FLAG_DISASSEMBLE})
     * tira o jogador do assento, e o resto vai ao voo da nave.
     */
    static void onControl(ServerPlayer player, RocketControlPayload control) {
        MinecraftServer server = player.level().getServer();
        ShipRecord ship = shipOf(server, player);
        if (ship == null || pilotOf(server, ship) != player) {
            return;
        }
        if (control.has(RocketControlPayload.FLAG_DISASSEMBLE)) {
            player.stopRiding();
            return;
        }
        ShipFlightTicker.applyControl(ship.id(), control, server.overworld().getGameTime(), false);
    }
}
