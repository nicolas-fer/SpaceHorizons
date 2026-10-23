package com.spacehorizons.ship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.joml.Quaterniond;
import org.jspecify.annotations.Nullable;

import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketPortrait;
import com.spacehorizons.space.SpaceDimension;
import com.spacehorizons.space.flight.AttitudeMath;
import com.spacehorizons.space.flight.FrameBridge;
import com.spacehorizons.space.travel.TravelTransfers;
import com.spacehorizons.space.vessel.VesselEntry;
import com.spacehorizons.space.vessel.VesselPose;
import com.spacehorizons.space.vessel.VesselSelector;
import com.spacehorizons.space.vessel.VesselStatePayload;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Manda a cada jogador que está no espaço, num foguete estacionado nele ou numa nave do estaleiro o estado virtual dos OUTROS veículos próximos
 * (docs/PLANO_NAVES.md, 4.3; fase 2d): as naves em voo ({@link ShipFlightTicker}) e os foguetes estacionados na dimensão de espaço
 * ({@code RocketEntity.isVirtual}), cada um com posição absoluta em E, atitude local, velocidade e giro (extrapolados no cliente). Taxa:
 * {@link #INTERVAL} ticks; só os mais próximos ({@link VesselSelector}) dentro do raio de estado; o próprio veículo do observador não vai.
 *
 * <p>De onde cada jogador "olha": dentro de uma nave, a posição virtual dela; num foguete estacionado, a posição real do voo
 * ({@code RocketFlight.spacePosition}); livre na dimensão de espaço, a posição real do jogador. (O piloto automático do jogador, comando
 * {@code fly}, fica estacionado e não recebe os outros veículos.)
 */
final class VesselSync {
    /** De quantos em quantos ticks o estado vai aos clientes. */
    static final int INTERVAL = 4;

    private record Sample(long tick, double[] pos, double[] attitude) {
    }

    private static final Map<UUID, Sample> PREVIOUS = new HashMap<>();
    /** Por jogador, os veículos mandados no último pacote (só esses podem ter o casco pedido). */
    private static final Map<UUID, Set<UUID>> SENT = new HashMap<>();
    /** Quantos jogadores receberam estado neste último ciclo e quantos veículos o último pacote de cada um tinha (para o autoteste). */
    private static final Map<UUID, Integer> LAST_COUNT = new HashMap<>();
    private static volatile long packets;

    private VesselSync() {
    }

    static boolean wasSentTo(UUID player, UUID vessel) {
        Set<UUID> sent = SENT.get(player);
        return sent != null && sent.contains(vessel);
    }

    static long packets() {
        return packets;
    }

    /** O último pacote do jogador tinha quantos veículos? (para o autoteste; -1 se nenhum foi mandado) */
    static int lastCount(UUID player) {
        return LAST_COUNT.getOrDefault(player, -1);
    }

    /** A cada tick do servidor: a cada {@link #INTERVAL} ticks, junta os veículos e manda os próximos a cada jogador que precisa. */
    static void tick(MinecraftServer server) {
        VesselHulls.tick(server);
        long now = server.overworld().getGameTime();
        if (now % INTERVAL != 0) {
            return;
        }
        List<ServerPlayer> observers = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ShipYard.is(player.level()) || SpaceDimension.is(player.level())) {
                if (observers == null) {
                    observers = new ArrayList<>(2);
                }
                observers.add(player);
            }
        }
        if (observers == null) {
            SENT.clear();
            LAST_COUNT.clear();
            return;
        }
        Map<UUID, VesselEntry> all = collect(server, now);
        for (ServerPlayer player : observers) {
            send(server, player, all, now);
        }
        SENT.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    private static void send(MinecraftServer server, ServerPlayer player, Map<UUID, VesselEntry> all, long now) {
        double[] pos = new double[3];
        UUID self = observerPosition(server, player, all, pos);
        if (Double.isNaN(pos[0])) {
            return;
        }
        List<VesselEntry> selected = VesselSelector.select(pos, new ArrayList<>(all.values()), self, VesselSelector.STATE_RADIUS, VesselSelector.MAX_VESSELS);
        int previous = LAST_COUNT.getOrDefault(player.getUUID(), 0);
        if (selected.isEmpty() && previous == 0) {
            return;
        }
        Set<UUID> sent = new HashSet<>();
        for (VesselEntry e : selected) {
            sent.add(e.id());
        }
        SENT.put(player.getUUID(), sent);
        LAST_COUNT.put(player.getUUID(), selected.size());
        packets++;
        PacketDistributor.sendToPlayer(player, new VesselStatePayload(now, selected));
    }

    /**
     * Onde o jogador olha, em E, escrito em {@code out} (NaN se não está em lugar nenhum): a nave que o cerca, o foguete estacionado em que
     * viaja ou a própria posição na dimensão de espaço. Devolve o id do veículo dele (a excluir), ou null.
     */
    private static @Nullable UUID observerPosition(MinecraftServer server, ServerPlayer player, Map<UUID, VesselEntry> all, double[] out) {
        out[0] = Double.NaN;
        if (ShipYard.is(player.level())) {
            ShipRecord ship = ShipRegistry.get(server).atPosition(player.getX(), player.getZ());
            VesselEntry mine = ship == null ? null : all.get(VesselEntry.shipUuid(ship.id()));
            if (mine == null) {
                return null;
            }
            System.arraycopy(mine.pos(), 0, out, 0, 3);
            return mine.id();
        }
        if (player.getVehicle() instanceof RocketEntity rocket && rocket.isVirtual()) {
            Vec3 real = rocket.flight().spacePosition();
            if (real != null) {
                SpaceDimension.toSpace(real.x, real.y, real.z, out);
                return rocket.getUUID();
            }
            return null;
        }
        SpaceDimension.toSpace(player.getX(), player.getY(), player.getZ(), out);
        return null;
    }

    /** Todos os veículos que voam: as naves com voo vivo no estaleiro e os foguetes estacionados na dimensão de espaço, por id. */
    static Map<UUID, VesselEntry> collect(MinecraftServer server, long now) {
        Map<UUID, VesselEntry> out = new LinkedHashMap<>();
        ServerLevel overworld = server.overworld();
        ServerLevel yard = ShipYard.level(server);
        if (yard != null) {
            ShipRegistry registry = ShipRegistry.get(server);
            for (ShipFlightTicker.Live live : ShipFlightTicker.liveAll()) {
                ShipRecord ship = registry.byId(live.shipId());
                double clock = live.clockAfterStep();
                if (ship == null || live.descending() || Double.isNaN(clock)) {
                    continue;
                }
                VesselEntry e = shipEntry(live, ship, clock, now);
                out.put(e.id(), e);
            }
        }
        ServerLevel space = TravelTransfers.findSpace(server);
        if (space != null) {
            for (RocketEntity rocket : space.getEntities(EntityTypeTest.forClass(RocketEntity.class), RocketEntity::isVirtual)) {
                VesselEntry e = rocketEntry(rocket, now);
                if (e != null) {
                    out.put(e.id(), e);
                }
            }
        }
        PREVIOUS.keySet().removeIf(id -> !out.containsKey(id));
        return out;
    }

    /** A posição em E e a atitude local de uma nave com voo vivo no relógio {@code clock} (o do fim do último passo). */
    static VesselEntry shipEntry(ShipFlightTicker.Live live, ShipRecord ship, double clock, long now) {
        ShipFlight.State s = live.state();
        var v = FrameBridge.absoluteInEarthFixed(s.orbit().body(), clock, s.orbit().r());
        double[] pos = {v.x(), v.y(), v.z()};
        Quaterniond yard = ShipFlight.yardAttitude(s.attitude(), live.arrangement().bow());
        Quaterniond local = AttitudeMath.inertialToSpaceLocal(yard, clock);
        double[] q = {local.x, local.y, local.z, local.w};
        UUID id = VesselEntry.shipUuid(ship.id());
        float radius = (float) (0.5 * Math.sqrt((double) ship.sizeX() * ship.sizeX() + (double) ship.sizeY() * ship.sizeY() + (double) ship.sizeZ() * ship.sizeZ()));
        return withRates(id, VesselEntry.SHIP, VesselHulls.shipVersion(ship.id()), radius, pos, q, now);
    }

    static @Nullable VesselEntry rocketEntry(RocketEntity rocket, long now) {
        Vec3 real = rocket.flight().spacePosition();
        RocketPortrait p = rocket.portrait();
        if (real == null || p.isEmpty()) {
            return null;
        }
        double[] pos = new double[3];
        SpaceDimension.toSpace(real.x, real.y, real.z, pos);
        var a = rocket.attitude();
        double[] q = {a.x(), a.y(), a.z(), a.w()};
        float radius = (float) (0.5 * Math.sqrt((double) p.sizeX * p.sizeX + (double) p.sizeY * p.sizeY + (double) p.sizeZ * p.sizeZ));
        return withRates(rocket.getUUID(), VesselEntry.ROCKET, VesselHulls.rocketVersion(rocket), radius, pos, q, now);
    }

    /** Completa a velocidade e o giro pela diferença para a amostra anterior do mesmo veículo (zero na primeira). */
    private static VesselEntry withRates(UUID id, byte kind, int version, float radius, double[] pos, double[] q, long now) {
        Sample before = PREVIOUS.get(id);
        double[] velocity = {0.0, 0.0, 0.0};
        double[] omega = {0.0, 0.0, 0.0};
        if (before != null && now > before.tick()) {
            double dt = now - before.tick();
            for (int i = 0; i < 3; i++) {
                velocity[i] = (pos[i] - before.pos()[i]) / dt;
            }
            double[] w = VesselPose.rotationBetween(before.attitude(), q);
            for (int i = 0; i < 3; i++) {
                omega[i] = w[i] / dt;
            }
        }
        if (before == null || now > before.tick()) {
            PREVIOUS.put(id, new Sample(now, pos.clone(), q.clone()));
        }
        return new VesselEntry(id, kind, version, radius, pos, q, velocity, omega);
    }

    static void reset() {
        PREVIOUS.clear();
        SENT.clear();
        LAST_COUNT.clear();
    }
}
