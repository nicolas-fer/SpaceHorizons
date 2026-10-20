package com.spacehorizons.ship;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/**
 * Mantém a nave carregada no estaleiro (plano 2.4). Cada nave "segurada" tem um ticket por coluna de chunk da caixa dela, de raio
 * {@link #RADIUS} (nível de chunk com ticks de bloco: baú, fornalha e redstone continuam funcionando; entidades só ticam onde há
 * jogador por perto). Uma nave é segurada enquanto houver jogador na célula dela e por {@link #releaseTicks} (5 min) depois do
 * último; então os tickets caem e a nave "congela" (os chunks descarregam e salvam).
 *
 * <p>O ticket é de um tipo próprio, SEM expiração e não persistido: some com o servidor e é recolocado quando alguém entra (ou
 * {@link #hold} é chamado). Só roda no thread do servidor.
 */
public final class ShipChunkTickets {
    /** Carregamento + simulação + mantém a dimensão ativa, sem expirar (nós soltamos). */
    private static final TicketType TYPE = new TicketType(TicketType.NO_TIMEOUT,
            TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
    /** Raio do ticket: 1 = nível de bloco ticando na caixa e um anel de chunks carregados em volta. */
    public static final int RADIUS = 1;
    /** 5 minutos de jogo. */
    public static final long DEFAULT_RELEASE_TICKS = 6_000L;
    /** De quantos em quantos ticks o servidor confere as naves. */
    private static final int CHECK_INTERVAL = 20;

    private static long releaseTicks = DEFAULT_RELEASE_TICKS;
    /** Naves seguradas agora (id → último tempo de jogo com alguém dentro). */
    private static final Map<Integer, Long> HELD = new HashMap<>();

    private ShipChunkTickets() {
    }

    /** Tempo sem ninguém até soltar (para testes e desenvolvimento). */
    public static void setReleaseTicks(long ticks) {
        releaseTicks = Math.max(0L, ticks);
    }

    public static long releaseTicks() {
        return releaseTicks;
    }

    /** Ids das naves seguradas agora. */
    public static Set<Integer> held() {
        return new HashSet<>(HELD.keySet());
    }

    public static boolean isHeld(int shipId) {
        return HELD.containsKey(shipId);
    }

    /** Esquece o estado (o servidor parou: os tickets morrem com ele). */
    public static void reset() {
        HELD.clear();
        releaseTicks = DEFAULT_RELEASE_TICKS;
    }

    /** Segura a nave agora e zera o relógio de espera (alguém entrou ou ela acabou de ser criada). */
    public static void hold(ServerLevel yard, ShipRecord ship, long now) {
        if (HELD.put(ship.id(), now) == null) {
            ShipYard.Box box = ship.box();
            for (int cx = box.chunkMinX(); cx <= box.chunkMaxX(); cx++) {
                for (int cz = box.chunkMinZ(); cz <= box.chunkMaxZ(); cz++) {
                    yard.getChunkSource().addTicketWithRadius(TYPE, new ChunkPos(cx, cz), RADIUS);
                }
            }
            SpaceHorizons.LOGGER.info("[ship] nave {} segura: {} colunas de chunk (raio {})", ship.id(), box.chunkColumns(), RADIUS);
        }
    }

    /** Solta a nave: os chunks dela voltam à regra normal de carregamento. */
    public static void release(ServerLevel yard, ShipRecord ship) {
        if (HELD.remove(ship.id()) != null) {
            ShipYard.Box box = ship.box();
            for (int cx = box.chunkMinX(); cx <= box.chunkMaxX(); cx++) {
                for (int cz = box.chunkMinZ(); cz <= box.chunkMaxZ(); cz++) {
                    yard.getChunkSource().removeTicketWithRadius(TYPE, new ChunkPos(cx, cz), RADIUS);
                }
            }
            SpaceHorizons.LOGGER.info("[ship] nave {} solta (sem ninguém há {} ticks)", ship.id(), releaseTicks);
        }
    }

    /** A cada tick do servidor: segura as naves com gente dentro, solta as que ficaram vazias por tempo demais. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_INTERVAL != 0) {
            return;
        }
        ServerLevel yard = ShipYard.level(server);
        if (yard == null) {
            return;
        }
        ShipRegistry registry = ShipRegistry.get(server);
        long now = yard.getGameTime();
        for (ShipRecord ship : registry.all()) {
            boolean occupied = false;
            for (ServerPlayer player : yard.players()) {
                if (ShipYard.cellContains(ship.cell(), player.getX(), player.getZ())) {
                    occupied = true;
                    break;
                }
            }
            if (occupied) {
                if (HELD.containsKey(ship.id())) {
                    HELD.put(ship.id(), now);
                } else {
                    hold(yard, ship, now);
                }
            } else if (HELD.containsKey(ship.id()) && now - HELD.get(ship.id()) >= releaseTicks) {
                release(yard, ship);
            }
        }
    }
}
