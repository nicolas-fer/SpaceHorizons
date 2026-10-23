package com.spacehorizons.ship;

import java.util.Locale;
import java.util.UUID;

import org.joml.Quaterniond;
import org.jspecify.annotations.Nullable;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.FlightMode;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.space.SpaceDimension;
import com.spacehorizons.space.flight.FrameBridge;
import com.spacehorizons.space.flight.V3;
import com.spacehorizons.space.travel.TravelTransferPayload;
import com.spacehorizons.space.travel.TravelTransfers;
import com.spacehorizons.space.vessel.VesselEntry;
import com.spacehorizons.space.vessel.VesselHull;
import com.spacehorizons.space.vessel.VesselPose;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.portal.TeleportTransition;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Só dev (comando {@code /spacehorizons vessel} e autoteste {@code shipsee}): põe um foguete estacionado no espaço a uma posição dada em eixos da nave,
 * gira a nave e lê as posições de que o servidor tem certeza. Nada daqui é caminho de jogo.
 */
public final class VesselDev {
    private VesselDev() {
    }

    /** O estado virtual (posição em E, atitude local...) do veículo {@code id} neste instante, ou null. */
    public static @Nullable VesselEntry entry(MinecraftServer server, UUID id) {
        return VesselSync.collect(server, server.overworld().getGameTime()).get(id);
    }

    /** Quantos veículos o último pacote do jogador tinha (-1 se nenhum pacote foi mandado a ele). */
    public static int lastCount(UUID player) {
        return VesselSync.lastCount(player);
    }

    public static long packetsSent() {
        return VesselSync.packets();
    }

    /** A duração (ms), as células e os bytes da última varredura de casco de nave. */
    public static double lastShipHullMs() {
        return VesselHulls.lastShipBuildMs();
    }

    public static int lastShipHullCells() {
        return VesselHulls.lastShipHullCells();
    }

    public static int lastShipHullBytes() {
        return VesselHulls.lastShipHullBytes();
    }

    private static boolean frozen;
    private static final java.util.Map<Integer, V3> FROZEN_SHIPS = new java.util.HashMap<>();
    private static final java.util.Map<UUID, Vec3> FROZEN_ROCKETS = new java.util.HashMap<>();

    /** Liga ou desliga o congelamento de teste: as naves e os foguetes estacionados ficam parados em E (velocidade zero a cada tick), para a geometria dos prints não andar. */
    public static void freeze(MinecraftServer server, boolean on) {
        FROZEN_SHIPS.clear();
        FROZEN_ROCKETS.clear();
        frozen = on;
        if (on) {
            for (ShipFlightTicker.Live live : ShipFlightTicker.liveAll()) {
                FROZEN_SHIPS.put(live.shipId(), live.state().orbit().r());
            }
            ServerLevel space = TravelTransfers.findSpace(server);
            if (space != null) {
                for (RocketEntity r : space.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(RocketEntity.class), RocketEntity::isVirtual)) {
                    if (r.flight().spacePosition() != null) {
                        FROZEN_ROCKETS.put(r.getUUID(), r.flight().spacePosition());
                    }
                }
            }
        }
    }

    /** A cada tick do servidor: repõe a posição congelada. */
    static void tickFreeze(MinecraftServer server) {
        if (!frozen) {
            return;
        }
        for (ShipFlightTicker.Live live : ShipFlightTicker.liveAll()) {
            V3 r = FROZEN_SHIPS.get(live.shipId());
            if (r != null) {
                ShipFlight.State s = live.state();
                ShipFlightTicker.devtestSetState(live.shipId(), new ShipFlight.State(s.orbit().withRV(r, V3.ZERO), s.attitude(), s.spin(), s.sas(), s.throttle()));
            }
        }
        ServerLevel space = TravelTransfers.findSpace(server);
        if (space != null) {
            double clock = com.spacehorizons.space.SpaceClock.ticks(server.overworld());
            for (RocketEntity r : space.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(RocketEntity.class), RocketEntity::isVirtual)) {
                Vec3 at = FROZEN_ROCKETS.get(r.getUUID());
                if (at != null) {
                    r.flight().devtestSetSpaceState(new FrameBridge.Local(at.x, at.y, at.z, 0.0, 0.0, 0.0), clock, 0.0);
                }
            }
        }
    }

    /** O casco da nave (lido agora dos blocos do estaleiro; null se a caixa não está carregada). */
    public static @Nullable VesselHull shipHull(MinecraftServer server, int shipId) {
        ShipRecord ship = ShipRegistry.get(server).byId(shipId);
        ServerLevel yard = ShipYard.level(server);
        return ship == null || yard == null ? null : VesselHulls.buildShip(yard, ship);
    }

    /** O casco do foguete estacionado {@code id} (do retrato), ou null. */
    public static @Nullable VesselHull rocketHull(MinecraftServer server, UUID id) {
        ServerLevel space = TravelTransfers.findSpace(server);
        return space != null && space.getEntity(id) instanceof RocketEntity rocket ? VesselHulls.rocketHull(rocket) : null;
    }

    /** O casco do veículo {@code id} (nave do estaleiro ou foguete estacionado), ou null. */
    public static @Nullable VesselHull hull(MinecraftServer server, UUID id) {
        return VesselHulls.isShip(id) ? shipHull(server, (int) id.getLeastSignificantBits()) : rocketHull(server, id);
    }

    /** Os eixos do estaleiro (x leste, y cima, z sul) → os do espaço, com a atitude da nave agora: {@code M_V · d}. */
    public static double[] yardToSpace(MinecraftServer server, int shipId, double dx, double dy, double dz) {
        VesselEntry ship = entry(server, VesselEntry.shipUuid(shipId));
        if (ship == null) {
            throw new IllegalStateException("a nave " + shipId + " não voa");
        }
        double[] m = VesselPose.modelToSpace(ship.attitude());
        return new double[] {m[0] * dx + m[1] * dy + m[2] * dz, m[3] * dx + m[4] * dy + m[5] * dz, m[6] * dx + m[7] * dy + m[8] * dz};
    }

    /** Gira a nave de {@code degrees} em torno do CIMA dela (eixo Y do estaleiro): o céu dela gira ao contrário. SAS e giro ficam como estão (desligados no teste). */
    public static void yawShip(MinecraftServer server, int shipId, double degrees) {
        ShipFlightTicker.Live live = ShipFlightTicker.live(shipId);
        if (live == null) {
            throw new IllegalStateException("a nave " + shipId + " não voa");
        }
        int bow = live.arrangement().bow();
        Quaterniond yard = ShipFlight.yardAttitude(live.state().attitude(), bow);
        yard.mul(new Quaterniond().rotationY(Math.toRadians(degrees)));
        Quaterniond p = ShipFlight.rocketAttitude(yard.mul(ShipArrangement.yardToCanonical(bow).conjugate(new Quaterniond())));
        ShipFlight.State s = live.state();
        ShipFlightTicker.devtestSetState(shipId, new ShipFlight.State(s.orbit(), p, s.spin(), s.sas(), s.throttle()));
    }

    /**
     * Cria um foguete de teste de {@code blocks} blocos na posição do jogador (na dimensão dele, a Terra), senta o jogador nele e o leva ao espaço, ESTACIONADO a
     * {@code (dx, dy, dz)} blocos da nave {@code shipId}, em eixos do estaleiro (x leste, y cima, z sul; a proa da nave de teste é o norte, -z), na mesma órbita.
     * Pelo mesmo caminho da travessia real do foguete (estado de voo no espaço, aviso ao piloto, teleporte com o passageiro).
     *
     * @return o foguete
     */
    public static RocketEntity boardRocket(ServerPlayer player, int shipId, double dx, double dy, double dz, int blocks) {
        MinecraftServer server = player.level().getServer();
        RocketEntity rocket = spawnRocket(player, shipId, dx, dy, dz, blocks);
        if (!player.startRiding(rocket, true, true)) {
            throw new IllegalStateException("o jogador não sentou no foguete");
        }
        ServerLevel space = TravelTransfers.findSpace(server);
        Vec3 park = com.spacehorizons.rocket.SpaceParking.pointFor(rocket.getUUID());
        Vec3 seat = rocket.passengerPositionAt(park, rocket.attitude(), player);
        PacketDistributor.sendToPlayer(player, new TravelTransferPayload(seat.x, seat.y, seat.z, player.getYRot(), player.getXRot()));
        var moved = rocket.teleport(new TeleportTransition(space, park, Vec3.ZERO, rocket.getYRot(), rocket.getXRot(), TeleportTransition.DO_NOTHING));
        SpaceHorizons.LOGGER.info("[vessel] foguete {} estacionado no espaço a ({}, {}, {}) da nave {}; jogador a bordo, teleporte={}", rocket.getUUID(),
                dx, dy, dz, shipId, moved != null);
        return rocket;
    }

    /** Um foguete de teste na dimensão do jogador, com o estado de voo já no espaço (mesma órbita da nave, deslocado), ainda não teleportado. */
    private static RocketEntity spawnRocket(ServerPlayer player, int shipId, double dx, double dy, double dz, int blocks) {
        MinecraftServer server = player.level().getServer();
        ShipFlightTicker.Live live = ShipFlightTicker.live(shipId);
        if (live == null || Double.isNaN(live.clockAfterStep())) {
            throw new IllegalStateException("a nave " + shipId + " não voa");
        }
        ServerLevel level = (ServerLevel) player.level();
        ShipTestRocket.Spec spec = ShipTestRocket.preset(blocks);
        ShipTestRocket.Built built = ShipTestRocket.build(level.registryAccess(), spec);
        Vec3 center = new Vec3(player.getX(), player.getY() + spec.sy() / 2.0, player.getZ());
        RocketEntity rocket = ShipTestRocket.spawn(level, center, built);
        // a posição inercial: a da nave mais o deslocamento (eixos do estaleiro → inerciais pela atitude do estaleiro)
        ShipFlight.State s = live.state();
        Quaterniond yard = ShipFlight.yardAttitude(s.attitude(), live.arrangement().bow());
        org.joml.Vector3d offset = yard.transform(new org.joml.Vector3d(dx, dy, dz));
        V3 r = s.orbit().r().plus(new V3(offset.x, offset.y, offset.z));
        double clock = live.clockAfterStep();
        FrameBridge.Local local = FrameBridge.toSpaceDimension(s.orbit().body(), clock, live.rate(), r, s.orbit().v());
        rocket.flight().devtestSetSpaceState(local, clock, live.rate());
        rocket.setFlightMode(FlightMode.FLIGHT);
        rocket.setVirtualSpacePosition(new Vec3(local.x(), local.y(), local.z()));
        SpaceHorizons.LOGGER.info("[vessel] foguete de teste {}: {} blocos, posição no espaço ({}, {}, {}) = nave + ({}, {}, {}) em eixos da nave", rocket.getUUID(),
                built.portrait().blockCount(), String.format(Locale.ROOT, "%.1f", local.x()), String.format(Locale.ROOT, "%.1f", local.y()),
                String.format(Locale.ROOT, "%.1f", local.z()), dx, dy, dz);
        return rocket;
    }

    /** Onde, na dimensão de espaço, fica um ponto a {@code (dx, dy, dz)} (eixos do espaço) da posição de {@code id}: para pôr um jogador livre. */
    public static double[] spaceDimensionAt(MinecraftServer server, UUID id, double dx, double dy, double dz) {
        VesselEntry e = entry(server, id);
        if (e == null) {
            throw new IllegalStateException("veículo " + id + " desconhecido");
        }
        double[] out = new double[3];
        SpaceDimension.fromSpace(e.pos()[0] + dx, e.pos()[1] + dy, e.pos()[2] + dz, out);
        return out;
    }
}
