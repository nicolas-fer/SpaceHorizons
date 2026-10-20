package com.spacehorizons.ship;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Entrar e sair das naves (spike S2): leva o jogador ao salão da nave no estaleiro e de volta ao ponto de onde ele veio. A nave é
 * segurada antes (tickets) e o chunk do destino é carregado de forma síncrona, para o jogador nunca aparecer num chunk vazio.
 */
public final class ShipTransit {
    private ShipTransit() {
    }

    /** Leva o jogador ao ponto de entrada da nave. Devolve false (e nenhum efeito) se o mundo não tem o estaleiro. */
    public static boolean enter(ServerPlayer player, ShipRecord ship) {
        MinecraftServer server = player.level().getServer();
        ServerLevel yard = ShipYard.level(server);
        if (yard == null) {
            return false;
        }
        // nave vinda de um foguete (S3): perto do cockpit; nave de teste (S2): o salão do layout
        Vec3 at = ship.snapshot().isPresent() ? ShipSpots.nearCockpit(yard, ship) : ShipBuilder.layout(ship.box()).spawnPoint();
        return enterAt(player, ship, at, -90.0F, 0.0F);
    }

    /**
     * Leva o jogador a um ponto da nave (o da conversão: perto do cockpit, com a vista no referencial local). Guarda o ponto de
     * retorno, segura a nave e carrega de forma síncrona o chunk de destino. Devolve false se o mundo não tem o estaleiro.
     */
    public static boolean enterAt(ServerPlayer player, ShipRecord ship, Vec3 at, float yaw, float pitch) {
        MinecraftServer server = player.level().getServer();
        ServerLevel yard = ShipYard.level(server);
        if (yard == null) {
            return false;
        }
        ShipRegistry registry = ShipRegistry.get(server);
        if (!ShipYard.is(player.level())) {
            registry.setReturn(player.getUUID(), ShipRegistry.ReturnPoint.of(player.level().dimension(), player.getX(), player.getY(),
                    player.getZ(), player.getYRot(), player.getXRot()));
        }
        ShipChunkTickets.hold(yard, ship, yard.getGameTime());
        yard.getChunk(BlockPos.containing(at));      // carrega (síncrono) o chunk onde o jogador vai aparecer
        SpaceHorizons.LOGGER.info("[ship] {} entra na nave {} em {}", player.getGameProfile().name(), ship.id(), at);
        player.teleport(new TeleportTransition(yard, at, Vec3.ZERO, yaw, pitch, TeleportTransition.DO_NOTHING));
        return true;
    }

    /** Leva o jogador de volta ao ponto de onde entrou (ou ao spawn do Overworld, se o ponto se perdeu). */
    public static void leave(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ShipRegistry registry = ShipRegistry.get(server);
        ShipRegistry.ReturnPoint back = registry.returnPoint(player.getUUID());
        ServerLevel level = back != null ? server.getLevel(back.levelKey()) : null;
        TeleportTransition transition;
        if (back != null && level != null) {
            transition = new TeleportTransition(level, new Vec3(back.x(), back.y(), back.z()), Vec3.ZERO, back.yRot(), back.xRot(),
                    TeleportTransition.DO_NOTHING);
        } else {
            ServerLevel overworld = server.overworld();
            BlockPos top = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.ZERO);
            transition = new TeleportTransition(overworld, Vec3.atBottomCenterOf(top.above()), Vec3.ZERO, 0.0F, 0.0F,
                    TeleportTransition.DO_NOTHING);
        }
        registry.clearReturn(player.getUUID());
        SpaceHorizons.LOGGER.info("[ship] {} sai da nave para {}", player.getGameProfile().name(), transition.newLevel().dimension().identifier());
        player.teleport(transition);
    }
}
