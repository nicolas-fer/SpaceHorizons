package com.spacehorizons.space.travel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrains;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceFrame;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Piloto automático de /spacehorizons fly: leva o jogador pelo espaço (trajetória do {@link FlightPlan}) até a
 * {@link #TARGET_ALTITUDE} sobre a face do corpo de destino, passando pelas transferências de esfera de influência.
 *
 * Em cada tick o servidor manda um teleporte absoluto com a velocidade do passo (a rotação fica livre, relativa a
 * zero): o cliente aplica a posição e segue sem trancos visíveis. Só roda na thread principal do servidor.
 */
public final class Autopilot {
    /** Altitude final sobre a superfície do destino. */
    public static final double TARGET_ALTITUDE = 300.0;

    private static final class Session {
        final FlightPlan plan;
        final SolarSystem.Body target;
        SolarSystem.Body current;

        Session(FlightPlan plan, SolarSystem.Body target, SolarSystem.Body current) {
            this.plan = plan;
            this.target = target;
            this.current = current;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private Autopilot() {
    }

    public static boolean isActive(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /** Inicia o voo até o corpo. Devolve a mensagem de erro, ou null se começou. */
    public static @Nullable Component start(ServerPlayer player, SolarSystem.Body target) {
        ServerLevel level = player.level();
        PlanetDefinition planet = Planets.forLevel(level).orElse(null);
        SolarSystem.Body current = planet == null ? null : SolarSystem.bodyOf(planet).orElse(null);
        if (current == null) {
            return Component.literal("Este mundo não é um planeta do Space Horizons");
        }
        if (!player.mayFly()) {
            return Component.literal("O piloto automático só funciona com voo (criativo ou espectador)");
        }
        if (TravelTransfers.findLevel(level.getServer(), target.planet()) == null) {
            return Component.literal("Este mundo não tem a dimensão de " + target.planet().id().getPath());
        }

        double[] facing = new double[2];
        TravelMath.facingPoint(target, facing);
        int surface = PlanetTerrains.get(target.planet()).surfaceHeight((int) Math.floor(facing[0]), (int) Math.floor(facing[1]));
        double[] targetSpace = new double[3];
        SpaceFrame.toSpace(target, facing[0], surface + TARGET_ALTITUDE, facing[1], targetSpace);
        double[] startSpace = new double[3];
        SpaceFrame.toSpace(current, player.getX(), player.getY(), player.getZ(), startSpace);

        if (!player.getAbilities().flying) {
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }
        SESSIONS.put(player.getUUID(), new Session(FlightPlan.create(SolarSystem.BODIES, startSpace, targetSpace), target, current));
        return null;
    }

    /** Para o piloto automático; devolve false se não estava ativo. */
    public static boolean stop(ServerPlayer player) {
        return SESSIONS.remove(player.getUUID()) != null;
    }

    static void forget(UUID id) {
        SESSIONS.remove(id);
    }

    static void clear() {
        SESSIONS.clear();
    }

    /**
     * Um tick do piloto automático do jogador. Devolve true se há um voo ativo (o tick foi consumido por ele, mesmo
     * esperando o cliente carregar), false se o jogador voa livre.
     */
    static boolean tick(ServerPlayer player, SolarSystem.Body body) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return false;
        }
        if (session.current != body) {
            // trocou de dimensão por outro meio (portal, comando): o plano deixou de valer
            SESSIONS.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Piloto automático cancelado: dimensão alterada"));
            return false;
        }
        if (!TravelTransfers.isReady(player)) {
            return true;
        }

        boolean arrived = session.plan.advance();
        double[] space = session.plan.position();
        double[] step = session.plan.velocity();
        SolarSystem.Body dominant = SphereOfInfluence.dominant(session.current, space[0], space[1], space[2]);
        ServerLevel toLevel = dominant == session.current
                ? null
                : TravelTransfers.findLevel(player.level().getServer(), dominant.planet());

        double[] local = new double[3];
        SpaceFrame.fromSpace(session.current, space[0], space[1], space[2], local);
        double x = TravelMath.normalizeX(session.current, local[0]);
        double[] v = new double[3];
        SpaceFrame.spaceToLocal(session.current, x, local[2], step[0], step[1], step[2], v);

        player.resetFallDistance();
        if (toLevel != null) {
            BodyTransfer transfer = BodyTransfer.compute(session.current, dominant, x, local[1], local[2], v[0], v[1], v[2],
                    player.getYRot(), player.getXRot());
            if (TravelTransfers.transfer(player, transfer, toLevel)) {
                session.current = dominant;
            } else {
                SESSIONS.remove(player.getUUID());
                player.sendSystemMessage(Component.literal("Piloto automático cancelado: a transferência foi recusada"));
                return true;
            }
        } else {
            // rotação relativa a zero = o cliente mantém para onde está olhando
            player.connection.teleport(new PositionMoveRotation(new Vec3(x, local[1], local[2]), new Vec3(v[0], v[1], v[2]), 0.0F, 0.0F),
                    Relative.ROTATION);
        }

        if (arrived) {
            SESSIONS.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Piloto automático: chegada a " + session.target.planet().id().getPath()));
        }
        return true;
    }
}
