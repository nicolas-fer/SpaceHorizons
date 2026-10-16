package com.spacehorizons.space.travel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceClock;
import com.spacehorizons.space.SpaceDimension;
import com.spacehorizons.space.SpaceFrame;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jspecify.annotations.Nullable;

/**
 * Transferência do jogador entre as dimensões dos corpos (esfera de influência). Só roda no servidor, na thread
 * principal (estado estático sem sincronização).
 */
public final class TravelTransfers {
    /** Ticks mínimos entre duas transferências naturais do mesmo jogador (rede de segurança além da histerese). */
    private static final int COOLDOWN_TICKS = 40;

    private static final Map<UUID, Integer> LAST_ATTEMPT = new HashMap<>();

    private TravelTransfers() {
    }

    /** ServerLevel do corpo (pelo tipo de dimensão, como Planets.forLevel), ou null se o mundo não o tem. */
    public static @Nullable ServerLevel findLevel(MinecraftServer server, PlanetDefinition planet) {
        for (ServerLevel level : server.getAllLevels()) {
            if (Planets.forLevel(level).filter(planet::equals).isPresent()) {
                return level;
            }
        }
        return null;
    }

    /** ServerLevel da dimensão de espaço aberto (pelo tipo de dimensão), ou null se o mundo não a tem (criado antes dela existir). */
    public static @Nullable ServerLevel findSpace(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (SpaceDimension.is(level)) {
                return level;
            }
        }
        return null;
    }

    /** O jogador pode receber um teleporte agora (cliente carregado e sem outra troca de dimensão em curso). */
    public static boolean isReady(ServerPlayer player) {
        return player.connection != null && player.connection.hasClientLoaded() && !player.isChangingDimension();
    }

    /**
     * Teleporta o jogador para a dimensão do alvo sem tela de carregamento no cliente. Devolve false se o teleporte
     * foi cancelado (evento do NeoForge) ou não pôde ser feito.
     */
    public static boolean transfer(ServerPlayer player, BodyTransfer transfer, ServerLevel toLevel) {
        // o aviso vai na mesma conexão e chega antes do pacote de respawn
        PacketDistributor.sendToPlayer(player, new TravelTransferPayload(transfer.x(), transfer.y(), transfer.z(), transfer.yRot(), transfer.xRot()));
        TeleportTransition transition = new TeleportTransition(
                toLevel,
                new Vec3(transfer.x(), transfer.y(), transfer.z()),
                new Vec3(transfer.vx(), transfer.vy(), transfer.vz()),
                transfer.yRot(),
                transfer.xRot(),
                TeleportTransition.DO_NOTHING);
        return player.teleport(transition) != null;
    }

    /**
     * Leva o jogador do planeta {@code from} para a dimensão de espaço aberto, sem tela de carregamento, no mesmo ponto do
     * espaço e com a mesma velocidade e olhar (ver {@link BodyTransfer#toSpace}). Devolve false se o mundo não tem a
     * dimensão de espaço ou o teleporte foi recusado.
     */
    public static boolean transferToSpace(ServerPlayer player, SolarSystem.Body from) {
        ServerLevel space = findSpace(player.level().getServer());
        if (space == null) {
            return false;
        }
        Vec3 v = player.getKnownMovement();
        return transfer(player, BodyTransfer.toSpace(from, SpaceClock.ticks(player.level()), player.getX(), player.getY(),
                player.getZ(), v.x, v.y, v.z, player.getYRot(), player.getXRot()), space);
    }

    /**
     * Leva o jogador do espaço aberto para a dimensão do corpo {@code to}, sem tela de carregamento, no mesmo ponto do
     * espaço e com a mesma velocidade (relativa ao chão do corpo) e olhar (ver {@link BodyTransfer#fromSpace}). Devolve
     * false se o mundo não tem a dimensão do corpo ou o teleporte foi recusado.
     */
    public static boolean transferFromSpace(ServerPlayer player, SolarSystem.Body to) {
        ServerLevel level = findLevel(player.level().getServer(), to.planet());
        if (level == null) {
            return false;
        }
        Vec3 v = player.getKnownMovement();
        return transfer(player, BodyTransfer.fromSpace(to, SpaceClock.ticks(player.level()), player.getX(), player.getY(),
                player.getZ(), v.x, v.y, v.z, player.getYRot(), player.getXRot()), level);
    }

    /**
     * Esfera de influência no voo livre: se outro corpo passou a dominar a posição do jogador, transfere. A velocidade
     * vem do movimento que o cliente reporta (blocos/tick, relativa ao chão do corpo atual); a transferência a converte para
     * o chão do corpo novo (os corpos se movem, ver BodyTransfer).
     */
    static void checkSphereOfInfluence(ServerPlayer player, SolarSystem.Body body) {
        if (!isReady(player)) {
            return;
        }
        double clock = SpaceClock.ticks(player.level());
        double[] space = new double[3];
        SpaceFrame.toSpace(body, clock, player.getX(), player.getY(), player.getZ(), space);
        SolarSystem.Body dominant = SphereOfInfluence.dominant(body, clock, space[0], space[1], space[2]);
        if (dominant == body) {
            return;
        }
        int now = player.level().getServer().getTickCount();
        Integer last = LAST_ATTEMPT.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_TICKS) {
            return;
        }
        LAST_ATTEMPT.put(player.getUUID(), now);
        ServerLevel toLevel = findLevel(player.level().getServer(), dominant.planet());
        if (toLevel == null) {
            return;
        }
        Vec3 v = player.getKnownMovement();
        BodyTransfer transfer = BodyTransfer.compute(body, dominant, clock, player.getX(), player.getY(), player.getZ(),
                v.x, v.y, v.z, player.getYRot(), player.getXRot());
        transfer(player, transfer, toLevel);
    }

    static void forget(UUID id) {
        LAST_ATTEMPT.remove(id);
    }

    static void clear() {
        LAST_ATTEMPT.clear();
    }
}
