package com.spacehorizons.space.travel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrains;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceClock;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
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
        /** Instante (System.nanoTime) do último avanço, para o plano andar pelo tempo real mesmo com o servidor atrasado. */
        long lastNanos = System.nanoTime();
        /** Último estado enviado ao jogador (null antes do primeiro tick e após uma transferência). */
        BodyTransfer last;
        /** Ticks seguidos segurando o voo à espera de chunks. */
        int heldTicks;

        Session(FlightGuide guide, SolarSystem.Body target) {
            this.guide = guide;
            this.target = target;
        }
    }

    /** Passos à frente (em ticks) cujos chunks precisam estar carregados antes de o voo andar. */
    private static final int[] GATE_STEPS = {1, 2, 4};
    /** Passos à frente cujos chunks só são pedidos, para a geração (em paralelo) ficar na frente do jogador. */
    private static final int[] REQUEST_STEPS = {8, 16, 32, 64};
    /** Depois de tantos ticks segurado, o voo segue assim mesmo (chunk que nunca carrega não pode prender o jogador). */
    private static final int MAX_HELD_TICKS = 200;
    private static final long TICK_NANOS = 50_000_000L;
    /** No máximo tantos passos por tick do servidor, para um engasgo longo não virar um salto enorme. */
    private static final int MAX_SUBSTEPS = 8;

    /**
     * Acima desta altura o chão não importa para quem voa a centenas de blocos por segundo, mas o servidor gera os
     * chunks de cada ponto novo (e trava esperando-os). Durante o voo, nessa faixa, as distâncias de visão e simulação
     * caem para {@link #HIGH_DISTANCE}, e voltam ao normal abaixo de {@link #LOW_Y}.
     */
    private static final double HIGH_Y = 800.0;
    private static final double LOW_Y = 500.0;
    private static final int HIGH_DISTANCE = 2;
    private static int savedView = -1;
    private static int savedSimulation = -1;

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
        boolean was = SESSIONS.remove(player.getUUID()) != null;
        restoreDistances(player.level().getServer());
        return was;
    }

    /**
     * Pede os chunks à frente do voo (extrapolando a última velocidade) e devolve true se algum dos próximos passos cai
     * num chunk ainda não carregado. Os tickets PORTAL expiram sozinhos.
     */
    private static boolean holdForChunks(ServerLevel level, Session session, SolarSystem.Body body) {
        BodyTransfer last = session.last;
        boolean missing = false;
        for (int k : GATE_STEPS) {
            missing |= requestIfMissing(level, body, last.x() + last.vx() * k, last.z() + last.vz() * k);
        }
        for (int k : REQUEST_STEPS) {
            requestIfMissing(level, body, last.x() + last.vx() * k, last.z() + last.vz() * k);
        }
        if (!missing) {
            session.heldTicks = 0;
            return false;
        }
        if (++session.heldTicks > MAX_HELD_TICKS) {
            if (session.heldTicks == MAX_HELD_TICKS + 1) {
                SpaceHorizons.LOGGER.warn("Piloto automático: chunks à frente não carregaram em {} ticks; seguindo mesmo assim",
                        MAX_HELD_TICKS);
            }
            return false;
        }
        return true;
    }

    /** true se o chunk de (x, z) não está carregado (e pede o carregamento). */
    private static boolean requestIfMissing(ServerLevel level, SolarSystem.Body body, double x, double z) {
        int cx = (int) Math.floor(TravelMath.normalizeX(body, x)) >> 4;
        int cz = (int) Math.floor(z) >> 4;
        if (level.hasChunk(cx, cz)) {
            return false;
        }
        level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, new ChunkPos(cx, cz), 0);
        return true;
    }

    /** Reduz ou restaura as distâncias de chunk conforme a altura do voo (com histerese). */
    private static void adjustDistances(MinecraftServer server, double y) {
        if (savedView < 0 && y > HIGH_Y) {
            savedView = server.getPlayerList().getViewDistance();
            savedSimulation = server.getPlayerList().getSimulationDistance();
            server.getPlayerList().setViewDistance(Math.min(savedView, HIGH_DISTANCE));
            server.getPlayerList().setSimulationDistance(Math.min(savedSimulation, HIGH_DISTANCE));
        } else if (savedView >= 0 && y < LOW_Y) {
            restoreDistances(server);
        }
    }

    private static void restoreDistances(MinecraftServer server) {
        if (savedView >= 0 && server != null) {
            server.getPlayerList().setViewDistance(savedView);
            server.getPlayerList().setSimulationDistance(savedSimulation);
        }
        savedView = -1;
        savedSimulation = -1;
    }

    static void forget(UUID id) {
        SESSIONS.remove(id);
    }

    static void clear() {
        SESSIONS.clear();
        savedView = -1;
        savedSimulation = -1;
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
            restoreDistances(player.level().getServer());
            player.sendSystemMessage(Component.literal("Piloto automático cancelado: dimensão alterada"));
            return false;
        }
        if (!TravelTransfers.isReady(player)) {
            return true;
        }

        // nunca põe o jogador num chunk que ainda não existe: o servidor trava esperando-o (o jogador voa a centenas de
        // blocos por segundo sobre terreno que o gerador vanilla ainda não produziu). Pede os chunks à frente (a geração roda
        // em threads paralelos) e segura o voo até os próximos estarem prontos.
        if (session.last != null && holdForChunks(player.level(), session, body)) {
            session.lastNanos = System.nanoTime();
            return true;
        }

        // o servidor pode estar atrasado (tick > 50 ms): avança um passo por 50 ms de tempo real decorrido, para a velocidade
        // não depender do tick e o cliente não ficar puxado para trás
        long now = System.nanoTime();
        int substeps = (int) Math.max(1, Math.min(MAX_SUBSTEPS, (now - session.lastNanos) / TICK_NANOS));
        session.lastNanos = now;
        double clock = SpaceClock.ticks(player.level());
        FlightGuide.Step step = null;
        for (int i = 0; i < substeps; i++) {
            step = session.guide.tick(clock + i, player.getYRot(), player.getXRot());
            if (step.transferred() || step.arrived()) {
                break;
            }
        }
        BodyTransfer state = step.state();
        session.last = step.transferred() ? null : state;
        adjustDistances(player.level().getServer(), state.y());
        player.resetFallDistance();
        if (step.transferred()) {
            ServerLevel toLevel = TravelTransfers.findLevel(player.level().getServer(), state.target().planet());
            if (toLevel == null || !TravelTransfers.transfer(player, state, toLevel)) {
                SESSIONS.remove(player.getUUID());
                restoreDistances(player.level().getServer());
                player.sendSystemMessage(Component.literal("Piloto automático cancelado: a transferência foi recusada"));
                return true;
            }
        } else {
            // velocidade zero: o cliente não extrapola entre dois teleportes (com o servidor lento isso o fazia ir e voltar);
            // rotação relativa a zero = o cliente mantém para onde está olhando
            player.connection.teleport(new PositionMoveRotation(new Vec3(state.x(), state.y(), state.z()),
                    Vec3.ZERO, 0.0F, 0.0F), Relative.ROTATION);
        }

        if (step.arrived()) {
            SESSIONS.remove(player.getUUID());
            restoreDistances(player.level().getServer());
            player.sendSystemMessage(Component.literal("Piloto automático: chegada a " + session.target.planet().id().getPath()));
        }
        return true;
    }
}
