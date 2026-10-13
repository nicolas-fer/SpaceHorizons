package com.spacehorizons.worldgen;

import java.util.Set;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Espaço contínuo em X: quem atravessa a costura x = ±C/2 reaparece do outro lado, e o terreno é
 * periódico, então nada muda visualmente. Também segura Z dentro da calota polar.
 *
 * Limitação restante: o salto é um teleporte no servidor. O cliente ainda precisa receber os chunks
 * do lado novo; os tickets abaixo tentam garantir que eles já estejam gerados no servidor.
 * Só jogadores são movidos. Entidades soltas ainda não atravessam a costura.
 */
final class PlanetWrap {
    /** Quantos ticks esperar entre renovações dos tickets espelhados. */
    private static final int MIRROR_INTERVAL = 20;
    /** Duração do ticket espelhado; renovado a cada MIRROR_INTERVAL, então expira logo se o jogador sair. */
    private static final TicketType MIRROR = new TicketType(100L, TicketType.FLAG_LOADING);
    private static final int CHUNK_BLOCKS = 16;

    private PlanetWrap() {
    }

    static void onLevelTick(ServerLevel level) {
        PlanetDefinition planet = Planets.byId(level.dimension().identifier()).orElse(null);
        if (planet == null) {
            return;
        }
        boolean mirror = level.getGameTime() % MIRROR_INTERVAL == 0;
        int viewChunks = level.getServer().getPlayerList().getViewDistance() + 2;
        for (ServerPlayer player : level.players()) {
            enforcePole(player, planet);
            wrapX(player, planet);
            if (mirror) {
                mirrorSeamChunks(level, player, planet, viewChunks);
            }
        }
    }

    /** Teleporte relativo na costura: mesma rotação e velocidade, só X muda por C. */
    private static void wrapX(ServerPlayer player, PlanetDefinition planet) {
        double half = planet.halfCircumference();
        double x = player.getX();
        double shift;
        if (x >= half) {
            shift = -planet.circumferenceBlocks();
        } else if (x < -half) {
            shift = planet.circumferenceBlocks();
        } else {
            return;
        }
        Vec3 velocity = player.getDeltaMovement();
        // Set vazio = posição absoluta. O último argumento não é verificado como "dismount": o veículo
        // NÃO é movido com o jogador (limitação da fase 1, ver README). Em veículo, o jogador pode ser desmontado.
        player.teleportTo((ServerLevel) player.level(), x + shift, player.getY(), player.getZ(),
                Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
        player.setDeltaMovement(velocity);
    }

    /** Barreira suave: empurra de volta para dentro da calota e zera a velocidade para fora. */
    private static void enforcePole(ServerPlayer player, PlanetDefinition planet) {
        double limit = planet.maxLatitudeBlocks() - 2;
        double z = player.getZ();
        if (Math.abs(z) <= limit) {
            return;
        }
        double clampedZ = Math.copySign(limit, z);
        Vec3 velocity = player.getDeltaMovement();
        double vz = Math.signum(z) == Math.signum(velocity.z) ? 0 : velocity.z;
        player.teleportTo((ServerLevel) player.level(), player.getX(), player.getY(), clampedZ,
                Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
        player.setDeltaMovement(velocity.x, velocity.y, vz);
    }

    /**
     * Quando o jogador está perto da costura, carrega o outro lado da costura (chunks espelhados).
     * O terreno é periódico, então eles já são o que o jogador vai ver após o salto.
     */
    private static void mirrorSeamChunks(ServerLevel level, ServerPlayer player, PlanetDefinition planet, int viewChunks) {
        int chunkX = player.blockPosition().getX() >> 4;
        int chunkZ = player.blockPosition().getZ() >> 4;
        int fullChunks = planet.circumferenceBlocks() / CHUNK_BLOCKS;
        int halfChunks = fullChunks / 2;
        int mirrorX;
        if (chunkX >= halfChunks - viewChunks) {
            mirrorX = chunkX - fullChunks;
        } else if (chunkX < -halfChunks + viewChunks) {
            mirrorX = chunkX + fullChunks;
        } else {
            return;
        }
        level.getChunkSource().addTicketWithRadius(MIRROR, new ChunkPos(mirrorX, chunkZ), viewChunks);
    }
}
