package com.spacehorizons.ship;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.PortraitParts;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketPortrait;
import com.spacehorizons.space.travel.TravelTransfers;
import com.spacehorizons.space.vessel.VesselEntry;
import com.spacehorizons.space.vessel.VesselHull;
import com.spacehorizons.space.vessel.VesselHullBuilder;
import com.spacehorizons.space.vessel.VesselHullPartPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Os cascos externos que o servidor manda aos clientes sob demanda (docs/PLANO_NAVES.md, 4.3; fase 2d): para cada nave do estaleiro e cada foguete
 * estacionado no espaço, o casco esparso ({@link VesselHull}) codificado, guardado, e enviado em partes ({@link PortraitParts}) a quem o pediu
 * ({@link VesselHullPartPayload}). Um casco por tick de servidor, no máximo (varrer a caixa de uma nave grande custa alguns ms).
 *
 * <p>O casco da nave é lido dos blocos do estaleiro (só com todos os chunks da caixa carregados) e refeito, quando alguém o pede, se tem mais de
 * {@link #SHIP_REFRESH_TICKS}; a versão sobe e o estado dos veículos passa a anunciá-la. O do foguete vem do retrato (que não muda).
 */
final class VesselHulls {
    /** Tempo (ticks) depois do qual o casco de uma nave é refeito quando alguém o pede de novo. */
    static final long SHIP_REFRESH_TICKS = 6_000L;

    private record Cached(int version, byte[] bytes, long builtTick) {
    }

    private record Request(UUID player, UUID vessel, int version) {
    }

    private static final Map<UUID, Cached> CACHE = new HashMap<>();
    private static final ArrayDeque<Request> QUEUE = new ArrayDeque<>();
    /** A duração (ms) e o tamanho da última varredura de casco de nave (para o autoteste). */
    private static volatile double lastShipBuildMs;
    private static volatile int lastShipHullCells;
    private static volatile int lastShipHullBytes;

    private VesselHulls() {
    }

    /** A versão do casco da nave anunciada no estado (1 até o casco ser feito; sobe a cada refazer). */
    static int shipVersion(int shipId) {
        Cached c = CACHE.get(VesselEntry.shipUuid(shipId));
        return c == null ? 1 : c.version();
    }

    /** A versão do casco do foguete: do retrato, que não muda. */
    static int rocketVersion(RocketEntity rocket) {
        return rocket.portrait().id().hashCode() | 1;
    }

    static double lastShipBuildMs() {
        return lastShipBuildMs;
    }

    static int lastShipHullCells() {
        return lastShipHullCells;
    }

    static int lastShipHullBytes() {
        return lastShipHullBytes;
    }

    /** O jogador pediu o casco {@code vessel} (versão que viu): entra na fila se o servidor o mandou a ele. */
    static void request(ServerPlayer player, UUID vessel, int version) {
        if (!VesselSync.wasSentTo(player.getUUID(), vessel)) {
            return;
        }
        Request r = new Request(player.getUUID(), vessel, version);
        if (!QUEUE.contains(r) && QUEUE.size() < 64) {
            QUEUE.add(r);
        }
    }

    /** A cada tick do servidor: atende um pedido. */
    static void tick(MinecraftServer server) {
        Request r = QUEUE.poll();
        if (r == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(r.player());
        if (player == null) {
            return;
        }
        long now = server.overworld().getGameTime();
        Cached cached = hullOf(server, r.vessel(), now);
        if (cached == null) {
            return;      // sem chunks (ou o veículo sumiu): o cliente pede de novo
        }
        List<byte[]> parts = PortraitParts.split(cached.bytes(), PortraitParts.partBytes());
        for (int i = 0; i < parts.size(); i++) {
            PacketDistributor.sendToPlayer(player, new VesselHullPartPayload(r.vessel(), cached.version(), i, parts.size(), parts.get(i)));
        }
        SpaceHorizons.LOGGER.info("[vessel] casco {} v{} enviado a {}: {} bytes em {} parte(s)", r.vessel(), cached.version(), player.getName().getString(),
                cached.bytes().length, parts.size());
    }

    private static @Nullable Cached hullOf(MinecraftServer server, UUID vessel, long now) {
        Cached cached = CACHE.get(vessel);
        if (isShip(vessel)) {
            int shipId = (int) vessel.getLeastSignificantBits();
            if (cached != null && now - cached.builtTick() <= SHIP_REFRESH_TICKS) {
                return cached;
            }
            ShipRegistry registry = ShipRegistry.get(server);
            ShipRecord ship = registry.byId(shipId);
            ServerLevel yard = ShipYard.level(server);
            if (ship == null || yard == null) {
                return cached;
            }
            long t0 = System.nanoTime();
            VesselHull hull = buildShip(yard, ship);
            if (hull == null) {
                return cached;
            }
            byte[] bytes = hull.encode();
            lastShipBuildMs = (System.nanoTime() - t0) / 1.0e6;
            lastShipHullCells = hull.cellCount();
            lastShipHullBytes = bytes.length;
            SpaceHorizons.LOGGER.info("[vessel] casco da nave {}: {} células de casco (escala {}) de uma caixa {}x{}x{}, {} bytes, {} ms", shipId, hull.cellCount(),
                    hull.scale, ship.sizeX(), ship.sizeY(), ship.sizeZ(), bytes.length, String.format(Locale.ROOT, "%.1f", lastShipBuildMs));
            Cached built = new Cached(cached == null ? 1 : cached.version() + 1, bytes, now);
            CACHE.put(vessel, built);
            return built;
        }
        net.minecraft.world.entity.Entity entity = null;
        ServerLevel space = TravelTransfers.findSpace(server);
        if (space != null) {
            entity = space.getEntity(vessel);
        }
        if (!(entity instanceof RocketEntity rocket)) {
            return cached;
        }
        int version = rocketVersion(rocket);
        if (cached != null && cached.version() == version) {
            return cached;
        }
        Cached built = new Cached(version, rocketHull(rocket).encode(), now);
        CACHE.put(vessel, built);
        return built;
    }

    /** O casco do foguete, do retrato dele (pivô no centro da caixa). */
    static VesselHull rocketHull(RocketEntity rocket) {
        RocketPortrait p = rocket.portrait();
        return VesselHullBuilder.build(p.sizeX, p.sizeY, p.sizeZ, (x, y, z) -> rgb(p.get(x, y, z)), p.sizeX / 2.0F, p.sizeY / 2.0F, p.sizeZ / 2.0F);
    }

    static boolean isShip(UUID vessel) {
        return vessel.getMostSignificantBits() == VesselEntry.shipUuid(0).getMostSignificantBits();
    }

    /** O casco da nave lida dos blocos do estaleiro; null se algum chunk da caixa não está carregado. */
    static @Nullable VesselHull buildShip(ServerLevel yard, ShipRecord ship) {
        ShipYard.Box box = ship.box();
        for (int cx = box.chunkMinX(); cx <= box.chunkMaxX(); cx++) {
            for (int cz = box.chunkMinZ(); cz <= box.chunkMaxZ(); cz++) {
                if (yard.getChunkSource().getChunkNow(cx, cz) == null) {
                    return null;
                }
            }
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return VesselHullBuilder.build(box.sizeX(), box.sizeY(), box.sizeZ(),
                (x, y, z) -> rgb(yard.getBlockState(pos.set(box.minX() + x, box.minY() + y, box.minZ() + z))),
                box.sizeX() / 2.0F, box.sizeY() / 2.0F, box.sizeZ() / 2.0F);
    }

    /** A cor RGB (não zero) de um bloco para o casco, ou 0 para ar e líquido. */
    static int rgb(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty() && state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
            return 0;
        }
        int col = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col & 0xFFFFFF;
        return col == 0 ? 0x8A8A92 : col;
    }

    static void reset() {
        CACHE.clear();
        QUEUE.clear();
    }
}
