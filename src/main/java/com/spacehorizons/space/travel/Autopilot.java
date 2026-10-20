package com.spacehorizons.space.travel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.SpaceParking;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrains;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceClock;
import com.spacehorizons.space.SpaceDimension;
import com.spacehorizons.space.flight.Zones;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

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
        /**
         * O jogador está na dimensão de espaço aberto, com a posição calculada no referencial do corpo e convertida a cada tick.
         * O jogador fica ESTACIONADO num ponto fixo da dimensão ({@link SpaceParking}, como o foguete) e a posição real do voo vai ao
         * cliente à parte ({@link AutopilotSkyPayload}): um jogador que atravessa a dimensão de espaço a 80 blocos/tick faz o servidor
         * carregar chunks sem parar (medido: ticks de até 69 s), e o estacionamento não gera chunk nenhum. Entre a saída do corpo e a
         * volta a ele não há terreno a esperar.
         */
        boolean inSpace;
        /** O mundo não tem a dimensão de espaço (criado antes dela): o voo inteiro fica nas dimensões dos corpos, como antes. */
        boolean noSpace;
        /** Posição REAL do voo na dimensão de espaço (coordenadas da dimensão), enquanto o jogador está estacionado nela. */
        Vec3 spacePosition = Vec3.ZERO;
        /** Ponto de chegada (x, z) na dimensão do destino, para pré-carregar o terreno antes de voltar ao corpo. */
        double facingX;
        double facingZ;
        int preloadCountdown;

        Session(FlightGuide guide, SolarSystem.Body target) {
            this.guide = guide;
            this.target = target;
        }
    }

    /** Raio (chunks) do terreno do ponto de chegada pedido antes de o piloto automático voltar à dimensão do corpo. */
    private static final int PRELOAD_RADIUS = 2;
    /** Ticks entre dois pedidos do terreno de chegada (os tickets expiram sozinhos). */
    private static final int PRELOAD_INTERVAL = 100;

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
        if (player.getVehicle() instanceof com.spacehorizons.rocket.RocketEntity) {
            return Component.literal("O piloto automático não funciona dentro de um foguete (o foguete é pilotado de verdade)");
        }
        ServerLevel level = player.level();
        PlanetDefinition planet = Planets.forLevel(level).orElse(null);
        SolarSystem.Body current = planet == null ? null : SolarSystem.bodyOf(planet).orElse(null);
        if (current == null) {
            return Component.literal(SpaceDimension.is(level) ? "O piloto automático não funciona no espaço aberto (a nave de verdade é que vai pilotar ali)"
                    : "Este mundo não é um planeta do Space Horizons");
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
        Session session = new Session(guide, target);
        session.facingX = facing[0];
        session.facingZ = facing[1];
        SESSIONS.put(player.getUUID(), session);
        return null;
    }

    /** Para o piloto automático; devolve false se não estava ativo. */
    public static boolean stop(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return false;
        }
        finish(player, session);
        return true;
    }

    /**
     * Encerra o voo do jogador por qualquer motivo: tira a sessão, devolve as distâncias de chunk e, se ele estava estacionado na dimensão
     * de espaço, o leva ao ponto REAL onde o voo estava (e avisa o cliente de que o céu volta a ser o da posição dele).
     */
    private static void finish(ServerPlayer player, Session session) {
        SESSIONS.remove(player.getUUID());
        restoreDistances(player.level().getServer());
        if (session.inSpace) {
            session.inSpace = false;
            sendSky(player, false, session.spacePosition);
            if (SpaceDimension.is(player.level())) {
                Vec3 at = session.spacePosition;
                player.connection.teleport(new PositionMoveRotation(at, Vec3.ZERO, 0.0F, 0.0F), Relative.ROTATION);
            }
        }
    }

    /** Avisa o cliente da posição real do voo (ou de que ela deixou de valer). */
    private static void sendSky(ServerPlayer player, boolean active, Vec3 at) {
        PacketDistributor.sendToPlayer(player, new AutopilotSkyPayload(active, at.x, at.y, at.z));
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
     * Um tick do piloto automático do jogador. {@code body} é o corpo da dimensão em que ele está, ou null na dimensão de espaço
     * aberto. Devolve true se há um voo ativo (o tick foi consumido por ele, mesmo esperando o cliente carregar), false se o jogador
     * voa livre.
     *
     * O voo anda em dois "modos" que se alternam sem tela de carregamento: na dimensão do corpo (do chão até a altitude de saída
     * antecipada, {@link #leaveAltitude}) e na de espaço aberto (o resto da viagem, até voltar a menos de {@link Zones#enterAltitude}
     * de um corpo). O plano é sempre o mesmo; só muda onde a posição dele é entregue ao jogador.
     */
    static boolean tick(ServerPlayer player, SolarSystem.@Nullable Body body) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return false;
        }
        if (session.inSpace != (body == null) || (body != null && session.guide.body() != body)) {
            // trocou de dimensão por outro meio (portal, comando): o plano deixou de valer
            finish(player, session);
            player.sendSystemMessage(Component.literal("Piloto automático cancelado: dimensão alterada"));
            return false;
        }
        if (!TravelTransfers.isReady(player)) {
            return true;
        }
        MinecraftServer server = player.level().getServer();

        // nunca põe o jogador num chunk que ainda não existe: o servidor trava esperando-o (o jogador voa a centenas de
        // blocos por segundo sobre terreno que o gerador vanilla ainda não produziu). Pede os chunks à frente (a geração roda
        // em threads paralelos) e segura o voo até os próximos estarem prontos. No espaço aberto não há terreno: nada a esperar.
        if (!session.inSpace && session.last != null && holdForChunks(player.level(), session, body)) {
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
        SolarSystem.Body where = state.target();
        session.last = step.transferred() || session.inSpace ? null : state;
        player.resetFallDistance();
        boolean spaceAvailable = !session.noSpace && TravelTransfers.findSpace(server) != null;
        session.noSpace = !spaceAvailable;

        if (session.inSpace) {
            if (!step.arrived() && !wantSpace(true, where, session.target, state.y() - where.planet().seaLevel())) {
                if (!returnToBody(player, session, state, where)) {
                    return true;
                }
            } else {
                preloadArrival(player, session, where);
                deliverInSpace(player, session, state, where, clock);
            }
        } else {
            adjustDistances(server, state.y());
            if (step.transferred()) {
                // esfera de influência cruzada ainda dentro da dimensão do planeta (voo lento): vai direto à dimensão do outro corpo
                ServerLevel toLevel = TravelTransfers.findLevel(server, where.planet());
                if (toLevel == null || !TravelTransfers.transfer(player, state, toLevel)) {
                    finish(player, session);
                    player.sendSystemMessage(Component.literal("Piloto automático cancelado: a transferência foi recusada"));
                    return true;
                }
            } else if (spaceAvailable && !step.arrived() && wantSpace(false, where, session.target, state.y() - where.planet().seaLevel())) {
                if (!leaveToSpace(player, session, state, where, clock)) {
                    session.noSpace = true;
                    deliverInPlanet(player, state);
                }
            } else {
                deliverInPlanet(player, state);
            }
        }

        if (step.arrived()) {
            finish(player, session);
            player.sendSystemMessage(Component.literal("Piloto automático: chegada a " + session.target.planet().id().getPath()));
        }
        return true;
    }

    /**
     * O voo, no corpo {@code where} a {@code altitude} sobre o nível do mar, deve estar na dimensão de espaço? {@code inSpace} é onde ele
     * está agora (histerese). Do corpo de ORIGEM (ou de um intermediário) sai ao passar de {@link #leaveAltitude} e não volta; ao do
     * DESTINO só volta abaixo de {@link Zones#enterAltitude} e dele não sai mais. Sem essa assimetria o jogador iria e viria entre as
     * dimensões na saída (que fica abaixo da altitude de entrada) ou na chegada (que fica abaixo da altitude de saída).
     */
    static boolean wantSpace(boolean inSpace, SolarSystem.Body where, SolarSystem.Body target, double altitude) {
        if (where == target) {
            return inSpace && altitude > Zones.enterAltitude(where.planet());
        }
        return inSpace || altitude >= leaveAltitude(where);
    }

    /**
     * Altitude (sobre o nível do mar) em que o piloto automático passa da dimensão do corpo para a de espaço: a mesma saída antecipada
     * do foguete rápido ({@code ZoneSwitch.LEAVE_MIN_ALTITUDE_FRACTION} da altitude de saída, 1.000 na Terra e 250 na Lua). O piloto
     * automático sempre voa rápido, e é acima disso que o gerador de terreno não acompanha.
     */
    static double leaveAltitude(SolarSystem.Body body) {
        return com.spacehorizons.rocket.ZoneSwitch.LEAVE_MIN_ALTITUDE_FRACTION * Zones.exitAltitude(body.planet());
    }

    /** Entrega a posição na dimensão do corpo: teleporte absoluto, velocidade zero, olhar do jogador mantido. */
    private static void deliverInPlanet(ServerPlayer player, BodyTransfer state) {
        // velocidade zero: o cliente não extrapola entre dois teleportes (com o servidor lento isso o fazia ir e voltar);
        // rotação relativa a zero = o cliente mantém para onde está olhando
        player.connection.teleport(new PositionMoveRotation(new Vec3(state.x(), state.y(), state.z()), Vec3.ZERO, 0.0F, 0.0F),
                Relative.ROTATION);
    }

    /**
     * Entrega a posição na dimensão de espaço: a posição REAL (o ponto do referencial do corpo convertido para as coordenadas da
     * dimensão) vai ao cliente, que desenha o céu a partir dela; o jogador em si fica no ponto de estacionamento.
     */
    private static void deliverInSpace(ServerPlayer player, Session session, BodyTransfer state, SolarSystem.Body where, double clock) {
        BodyTransfer inSpace = BodyTransfer.toSpace(where, clock, state.x(), state.y(), state.z(), 0.0, 0.0, 0.0, player.getYRot(),
                player.getXRot());
        session.spacePosition = new Vec3(inSpace.x(), inSpace.y(), inSpace.z());
        sendSky(player, true, session.spacePosition);
        Vec3 park = SpaceParking.pointFor(player.getUUID());
        // o jogador pode se mexer sozinho (voo criativo): volta ao ponto fixo, para o chunk dele ser sempre o mesmo
        if (player.position().distanceToSqr(park) > 0.01) {
            player.connection.teleport(new PositionMoveRotation(park, Vec3.ZERO, 0.0F, 0.0F), Relative.ROTATION);
        }
    }

    /** Dimensão do corpo → dimensão de espaço, no mesmo ponto. Devolve false se o mundo não tem a dimensão ou a troca foi recusada. */
    private static boolean leaveToSpace(ServerPlayer player, Session session, BodyTransfer state, SolarSystem.Body where, double clock) {
        ServerLevel space = TravelTransfers.findSpace(player.level().getServer());
        if (space == null) {
            return false;
        }
        BodyTransfer inSpace = BodyTransfer.toSpace(where, clock, state.x(), state.y(), state.z(), state.vx(), state.vy(), state.vz(),
                state.yRot(), state.xRot());
        // o jogador chega ao ponto de estacionamento (com o olhar convertido) e a posição real vai ao cliente à parte
        Vec3 park = SpaceParking.pointFor(player.getUUID());
        BodyTransfer parked = new BodyTransfer(null, park.x, park.y, park.z, 0.0, 0.0, 0.0, inSpace.yRot(), inSpace.xRot());
        session.spacePosition = new Vec3(inSpace.x(), inSpace.y(), inSpace.z());
        sendSky(player, true, session.spacePosition);
        if (!TravelTransfers.transfer(player, parked, space)) {
            sendSky(player, false, session.spacePosition);
            return false;
        }
        session.inSpace = true;
        session.last = null;
        session.preloadCountdown = 0;
        return true;
    }

    /** Dimensão de espaço → dimensão do corpo, no ponto do plano. Devolve false (e cancela o voo) se a troca foi recusada. */
    private static boolean returnToBody(ServerPlayer player, Session session, BodyTransfer state, SolarSystem.Body where) {
        MinecraftServer server = player.level().getServer();
        ServerLevel level = TravelTransfers.findLevel(server, where.planet());
        if (level == null) {
            finish(player, session);
            player.sendSystemMessage(Component.literal("Piloto automático cancelado: a transferência foi recusada"));
            return false;
        }
        // o aviso de que o céu volta a ser o da posição do jogador segue na mesma conexão, antes do respawn da troca
        sendSky(player, false, session.spacePosition);
        if (!TravelTransfers.transfer(player, state, level)) {
            session.inSpace = false;
            SESSIONS.remove(player.getUUID());
            restoreDistances(server);
            player.sendSystemMessage(Component.literal("Piloto automático cancelado: a transferência foi recusada"));
            return false;
        }
        session.inSpace = false;
        session.last = null;
        return true;
    }

    /**
     * No espaço, já na vizinhança do corpo de destino, pede o terreno em volta do ponto de chegada (tickets que expiram sozinhos): a
     * geração roda em paralelo enquanto o piloto automático ainda voa sobre o vazio, e a volta ao corpo não espera por chunks.
     */
    private static void preloadArrival(ServerPlayer player, Session session, SolarSystem.Body where) {
        if (where != session.target || session.preloadCountdown-- > 0) {
            return;
        }
        session.preloadCountdown = PRELOAD_INTERVAL;
        ServerLevel level = TravelTransfers.findLevel(player.level().getServer(), where.planet());
        if (level == null) {
            return;
        }
        double x = TravelMath.normalizeX(where, session.facingX);
        level.getChunkSource().addTicketWithRadius(TicketType.PORTAL,
                new ChunkPos((int) Math.floor(x) >> 4, (int) Math.floor(session.facingZ) >> 4), PRELOAD_RADIUS);
    }
}
