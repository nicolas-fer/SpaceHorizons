package com.spacehorizons.space.travel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrains;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceClock;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Piloto automático de /spacehorizons fly: leva o jogador pelo espaço (trajetória do {@link FlightGuide}) até a
 * {@link #TARGET_ALTITUDE} sobre a face do corpo de destino, passando pelas transferências de esfera de influência.
 * Os corpos se movem (a Lua orbita): o guia mira onde o destino estará e, ao entrar na esfera de influência dele, segue no
 * referencial do corpo, então o jogador chega com a velocidade relativa à superfície e não a do movimento orbital.
 *
 * Em cada tick o servidor manda um teleporte absoluto com a velocidade do passo (a rotação fica livre, relativa a
 * zero): o cliente aplica a posição e segue sem trancos visíveis. Só roda na thread principal do servidor.
 */
public final class Autopilot {
    /** Altitude final sobre a superfície do destino. */
    public static final double TARGET_ALTITUDE = 300.0;

    private static final class Session {
        final FlightGuide guide;
        final SolarSystem.Body target;

        Session(FlightGuide guide, SolarSystem.Body target) {
            this.guide = guide;
            this.target = target;
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
        MinecraftServer server = level.getServer();
        FlightGuide guide = new FlightGuide(current, player.getX(), player.getY(), player.getZ(), SpaceClock.ticks(level), target,
                facing[0], surface + TARGET_ALTITUDE, facing[1], body -> TravelTransfers.findLevel(server, body.planet()) != null);

        if (!player.getAbilities().flying) {
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }
        SESSIONS.put(player.getUUID(), new Session(guide, target));
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
        if (session.guide.body() != body) {
            // trocou de dimensão por outro meio (portal, comando): o plano deixou de valer
            SESSIONS.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Piloto automático cancelado: dimensão alterada"));
            return false;
        }
        if (!TravelTransfers.isReady(player)) {
            return true;
        }

        FlightGuide.Step step = session.guide.tick(SpaceClock.ticks(player.level()), player.getYRot(), player.getXRot());
        BodyTransfer state = step.state();
        player.resetFallDistance();
        if (step.transferred()) {
            ServerLevel toLevel = TravelTransfers.findLevel(player.level().getServer(), state.target().planet());
            if (toLevel == null || !TravelTransfers.transfer(player, state, toLevel)) {
                SESSIONS.remove(player.getUUID());
                player.sendSystemMessage(Component.literal("Piloto automático cancelado: a transferência foi recusada"));
                return true;
            }
        } else {
            // rotação relativa a zero = o cliente mantém para onde está olhando
            player.connection.teleport(new PositionMoveRotation(new Vec3(state.x(), state.y(), state.z()),
                    new Vec3(state.vx(), state.vy(), state.vz()), 0.0F, 0.0F), Relative.ROTATION);
        }

        if (step.arrived()) {
            SESSIONS.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Piloto automático: chegada a " + session.target.planet().id().getPath()));
        }
        return true;
    }
}
