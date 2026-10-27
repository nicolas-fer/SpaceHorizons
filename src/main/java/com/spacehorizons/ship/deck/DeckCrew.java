package com.spacehorizons.ship.deck;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.FlightMode;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketPortrait;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A tripulação a pé no convés de uma nave em movimento (spike S5; plano em {@code docs/PLANO_NAVE_ATMOSFERA.md}). Cada tripulante tem o estado
 * guardado e simulado NO REFERENCIAL DA NAVE ({@link DeckState}): o cliente prevê o próprio movimento em coordenadas locais e manda o estado
 * local ({@link DeckMovePayload}) no lugar do pacote de posição do vanilla; o servidor confere ({@link DeckMoveCheck}), aceita e, depois de
 * cada tick da nave, DERIVA a posição do jogador no mundo pela pose da nave naquele tick ({@link DeckFrame}). O movimento da nave entra como
 * transformação, nunca como um deslocamento que o jogador precise alcançar: a 10 a 100 blocos por tick a colisão é a mesma que a 0.
 *
 * <p>Embarcar e desembarcar são decisões do servidor ({@link #board}, {@link #leave}); o cliente é avisado por pacote. Quem senta numa nave
 * (passageiro), muda de dimensão ou sai da caixa do retrato deixa o convés.
 *
 * <p>Embarque automático (fase 2e, frente 2): o jogador entra no convés de uma nave que voa quando a caixa dele fica apoiada num bloco do retrato
 * ({@link DeckBoarding}), ao levantar do assento da nave em voo ({@link #requestBoardAfterSeat}) e ao montar a nave pela ponte
 * ({@link #boardStandingInside}); sai ao deixar a caixa com folga, ao pousar e desmontar ({@link #detachAll}) ou ao subir ao estaleiro.
 */
public final class DeckCrew {
    /** Quanto o tripulante pode passar da caixa do retrato (blocos) antes de deixar o convés. */
    public static final double LEAVE_MARGIN = 3.0;

    private static final Map<UUID, Member> MEMBERS = new ConcurrentHashMap<>();
    /** Quem levantou do assento de uma nave em voo e deve embarcar no próximo tick dela: jogador -> pedido (id da nave e ticks de espera que restam). */
    private static final Map<UUID, SeatRequest> SEAT_REQUESTS = new ConcurrentHashMap<>();
    /** Ticks que o pedido de embarque do assento espera pelo tick da nave antes de ser descartado. */
    private static final int SEAT_REQUEST_TICKS = 10;

    /** Contagens para o autoteste e o diagnóstico: estados aceitos, recusados (correções enviadas) e saídas pelo limite da caixa. */
    public static final AtomicLong ACCEPTED = new AtomicLong();
    public static final AtomicLong REJECTED = new AtomicLong();
    public static final AtomicLong LEFT_BOX = new AtomicLong();
    /** Correções de posição que o servidor mandou a jogadores (o pacote de teletransporte do vanilla, que é o "moved wrongly" visto de fora). */
    public static final AtomicLong TELEPORTS = new AtomicLong();
    /** Embarques automáticos (pisar no convés, levantar do assento, montagem) e danos de queda aplicados dentro do convés. */
    public static final AtomicLong AUTO_BOARDED = new AtomicLong();
    public static final AtomicLong FALL_DAMAGES = new AtomicLong();

    private DeckCrew() {
    }

    /** O tripulante no servidor: o último estado local aceito e o que chegou e espera conferência. */
    public static final class Member {
        final UUID player;
        final UUID rocket;
        final int rocketId;
        final DeckState good = new DeckState();
        @Nullable DeckState pending;
        long lastAcceptTick;
        /** A distância de queda acumulada em coordenadas locais ({@link DeckFall}). */
        double fall;

        Member(UUID player, UUID rocket, int rocketId) {
            this.player = player;
            this.rocket = rocket;
            this.rocketId = rocketId;
        }

        /** O último estado local aceito (cópia). */
        public DeckState state() {
            return new DeckState().set(this.good);
        }

        public UUID rocketUuid() {
            return this.rocket;
        }
    }

    private static final class SeatRequest {
        final int rocketId;
        int ticksLeft = SEAT_REQUEST_TICKS;

        SeatRequest(int rocketId) {
            this.rocketId = rocketId;
        }
    }

    /** Um tripulante que saiu do convés sem teletransporte: o jogador e o último estado local aceito. */
    public record Crewed(ServerPlayer player, DeckState state) {
    }

    /** Os últimos estados locais aceitos dos tripulantes a pé da nave (para não fechar uma porta sobre um deles). */
    public static List<DeckState> statesOn(UUID rocket) {
        List<DeckState> states = new ArrayList<>();
        for (Member m : MEMBERS.values()) {
            if (m.rocket.equals(rocket)) {
                states.add(m.state());
            }
        }
        return states;
    }

    public static boolean isCrew(Entity entity) {
        return entity instanceof ServerPlayer && MEMBERS.containsKey(entity.getUUID());
    }

    public static @Nullable Member member(UUID player) {
        return MEMBERS.get(player);
    }

    /** A pose atual da nave no servidor. */
    public static DeckFrame frameOf(RocketEntity rocket) {
        return rocket.deckFrame();
    }

    // ---------------------------------------------------------------- embarcar e desembarcar

    /**
     * Põe o jogador no convés da nave começando em {@code start} (referencial local). Recusa (false) se o jogador for passageiro, estiver em
     * outra dimensão que a da nave, a nave não tiver blocos ou o jogador já for tripulante.
     */
    public static boolean board(ServerPlayer player, RocketEntity rocket, DeckState start) {
        if (player.isPassenger() || rocket.portrait().isEmpty() || rocket.isRemoved() || player.level() != rocket.level() || MEMBERS.containsKey(player.getUUID())) {
            return false;
        }
        Member m = new Member(player.getUUID(), rocket.getUUID(), rocket.getId());
        m.good.set(start);
        m.lastAcceptTick = player.level().getGameTime();
        MEMBERS.put(player.getUUID(), m);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        applyToPlayer(player, rocket, m);
        PacketDistributor.sendToPlayer(player, new DeckBoardPayload(rocket.getId(), DeckMovePayload.of(rocket.getId(), m.good)));
        SpaceHorizons.LOGGER.info("[deck] {} embarcou no convés da nave {} em ({}, {}, {}) locais", player.getPlainTextName(), rocket.getId(),
                round(start.pos.x), round(start.pos.y), round(start.pos.z));
        return true;
    }

    /** Embarca na posição em que o jogador está agora no mundo, vista pela pose atual da nave (para o comando e o uso manual). */
    public static boolean boardAtWorld(ServerPlayer player, RocketEntity rocket) {
        DeckFrame frame = frameOf(rocket);
        Vector3d local = frame.toLocal(player.getX(), player.getY(), player.getZ());
        float[] view = frame.viewToLocal(player.getYRot(), player.getXRot());
        DeckState s = new DeckState();
        s.pos.set(local);
        s.yaw = view[0];
        s.pitch = view[1];
        return board(player, rocket, s);
    }

    /**
     * Tira o jogador do convés: avisa o cliente e o teletransporta ao ponto do mundo onde ele estava (o protocolo normal de posição do vanilla
     * retoma o controle). Ao sair a velocidade é zero: a herdada da nave faria o jogador voar a dezenas de blocos por tick.
     */
    public static void leave(ServerPlayer player, String reason) {
        Member m = MEMBERS.remove(player.getUUID());
        if (m == null) {
            return;
        }
        RocketEntity rocket = rocketOf(player, m);
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        if (rocket != null) {
            DeckFrame frame = frameOf(rocket);
            Vector3d w = frame.toWorld(m.good.pos.x, m.good.pos.y, m.good.pos.z);
            float[] view = frame.viewToWorld(m.good.yaw, m.good.pitch);
            x = w.x;
            y = w.y;
            z = w.z;
            yaw = view[0];
            pitch = view[1];
        }
        PacketDistributor.sendToPlayer(player, new DeckLeavePayload());
        player.connection.teleport(x, y, z, yaw, pitch);
        releaseObservers(player, m);
        SpaceHorizons.LOGGER.info("[deck] {} deixou o convés ({})", player.getPlainTextName(), reason);
    }

    /**
     * Tira a tripulação da nave do convés SEM teletransportar: avisa os clientes e devolve cada tripulante com o último estado local aceito, para
     * quem chamou levá-lo a outro lugar (a subida ao estaleiro e o pouso levam pela posição relativa à nave).
     */
    public static List<Crewed> detachAll(RocketEntity rocket) {
        List<Crewed> out = new ArrayList<>();
        if (MEMBERS.isEmpty() || !(rocket.level() instanceof ServerLevel level)) {
            return out;
        }
        for (Member m : new ArrayList<>(MEMBERS.values())) {
            if (!m.rocket.equals(rocket.getUUID())) {
                continue;
            }
            MEMBERS.remove(m.player);
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(m.player);
            if (player != null && !player.isRemoved()) {
                PacketDistributor.sendToPlayer(player, new DeckLeavePayload());
                // os observadores voltam ao vanilla já (sem isto esperariam 10 ticks sem estado, desenhando o tripulante na pose velha)
                releaseObservers(player, m);
                out.add(new Crewed(player, m.state()));
            }
        }
        return out;
    }

    /**
     * Tira o jogador do convés SEM teletransportá-lo (ele vai sentar num assento da nave: a posição de quem manda agora é a do assento). Avisa o
     * cliente e devolve o último estado local aceito, ou null se ele não era tripulante.
     */
    public static @Nullable DeckState release(ServerPlayer player) {
        Member m = MEMBERS.remove(player.getUUID());
        if (m == null) {
            return null;
        }
        PacketDistributor.sendToPlayer(player, new DeckLeavePayload());
        releaseObservers(player, m);
        return m.state();
    }

    private static @Nullable RocketEntity rocketOf(ServerPlayer player, Member m) {
        return player.level() instanceof ServerLevel level && level.getEntity(m.rocket) instanceof RocketEntity r && !r.isRemoved() ? r : null;
    }

    // ---------------------------------------------------------------- rede e tick

    /** Chegou o estado local do cliente (thread do servidor): guarda para a conferência do próximo tick da nave. */
    public static void onMove(ServerPlayer player, DeckMovePayload payload) {
        Member m = MEMBERS.get(player.getUUID());
        if (m != null && m.rocketId == payload.rocketId()) {
            m.pending = payload.toState();
        }
    }

    /**
     * Depois do tick da nave no servidor: confere o último estado local de cada tripulante, aceita ou recusa, e põe o jogador no mundo pela pose
     * que a nave tem agora. A conferência e a derivação ficam juntas porque a posição do mundo só vale para esta pose.
     */
    public static void afterRocketTick(RocketEntity rocket) {
        if (rocket.level() instanceof ServerLevel here && !rocket.isRemoved()) {
            autoBoard(here, rocket);
        }
        if (MEMBERS.isEmpty()) {
            return;
        }
        List<Member> mine = null;
        for (Member m : MEMBERS.values()) {
            if (m.rocket.equals(rocket.getUUID())) {
                if (mine == null) {
                    mine = new ArrayList<>();
                }
                mine.add(m);
            }
        }
        if (mine == null || !(rocket.level() instanceof ServerLevel level)) {
            return;
        }
        // a pose deste tick vai marcada com o tick do servidor (o cliente dos tripulantes desenha a nave nessa linha do tempo)
        rocket.stampPose((int) level.getGameTime());
        for (Member m : mine) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(m.player);
            if (player == null || player.isRemoved()) {
                MEMBERS.remove(m.player);
                continue;
            }
            if (player.isPassenger() || player.level() != rocket.level()) {
                // sentou (piloto) ou mudou de dimensão: a posição do mundo é a de quem manda agora
                MEMBERS.remove(m.player);
                PacketDistributor.sendToPlayer(player, new DeckLeavePayload());
                releaseObservers(player, m);
                continue;
            }
            processPending(player, rocket, m, level.getGameTime());
            RocketPortrait p = rocket.portrait();
            if (outsideBox(m.good.pos, p)) {
                LEFT_BOX.incrementAndGet();
                leave(player, "saiu da caixa da nave");
                continue;
            }
            applyToPlayer(player, rocket, m);
            // os outros clientes que veem a nave recebem o estado local na mesma pose (o pacote de posição do vanilla do tripulante é suprimido)
            PacketDistributor.sendToPlayersTrackingEntity(rocket, DeckObservePayload.of(player.getId(), rocket.getId(), (int) level.getGameTime(), m.good));
        }
    }

    /**
     * O tripulante saiu do convés: avisa os observadores (voltam ao pacote de posição do vanilla) e manda a eles a posição exata de agora. A posição
     * que o servidor lembra ter mandado (a base dos pacotes relativos) vem de antes da saída, então um delta pequeno cairia no lugar errado do
     * observador; a posição absoluta o acerta.
     */
    private static void releaseObservers(ServerPlayer player, Member m) {
        PacketDistributor.sendToPlayersTrackingEntity(player, DeckObservePayload.left(player.getId(), m.rocketId, (int) player.level().getGameTime()));
        if (player.level() instanceof ServerLevel level) {
            level.getChunkSource().sendToTrackingPlayers(player, ClientboundEntityPositionSyncPacket.of(player));
        }
    }

    /**
     * Só autoteste: põe o tripulante num ponto local (sem velocidade) e avisa o cliente pelo pacote de correção, que reposiciona a simulação local.
     * Devolve false se o jogador não está no convés.
     */
    public static boolean devtestRelocate(ServerPlayer player, double x, double y, double z, boolean onGround) {
        Member m = MEMBERS.get(player.getUUID());
        if (m == null) {
            return false;
        }
        m.good.pos.set(x, y, z);
        m.good.vel.zero();
        m.good.onGround = onGround;
        m.pending = null;
        m.fall = 0.0;
        m.lastAcceptTick = player.level().getGameTime();
        PacketDistributor.sendToPlayer(player, new DeckCorrectionPayload(m.rocketId, DeckMovePayload.of(m.rocketId, m.good)));
        return true;
    }

    // ---------------------------------------------------------------- embarque automático (fase 2e, frente 2)

    /** O jogador levantou do assento de uma nave em voo: embarca no próximo tick dela, ao lado ou em cima do ponto onde o assento o deixou. */
    public static void requestBoardAfterSeat(ServerPlayer player, RocketEntity rocket) {
        if (rocket.flight().mode() != FlightMode.LANDED) {
            SEAT_REQUESTS.put(player.getUUID(), new SeatRequest(rocket.getId()));
        }
    }

    private static boolean canBoard(ServerPlayer player) {
        return !player.isPassenger() && !player.isSpectator() && player.isAlive() && !MEMBERS.containsKey(player.getUUID());
    }

    /** O raio (blocos) em volta da nave dentro do qual um jogador pode estar no convés: a meia diagonal da caixa mais uma folga. */
    private static double reach(RocketPortrait p) {
        return 0.5 * Math.sqrt((double) p.sizeX * p.sizeX + (double) p.sizeY * p.sizeY + (double) p.sizeZ * p.sizeZ) + 2.0;
    }

    /** O estado de embarque de um jogador no ponto local {@code feet}, com o olhar na direção em que ele olha. */
    private static DeckState startState(ServerPlayer player, DeckFrame frame, Vector3d feet) {
        float[] view = frame.viewToLocal(player.getYRot(), player.getXRot());
        DeckState s = new DeckState();
        s.pos.set(feet);
        s.yaw = view[0];
        s.pitch = view[1];
        s.onGround = true;
        return s;
    }

    /** Antes da conferência do tick: quem pisou no convés de uma nave em voo e quem levantou do assento dela embarca. */
    private static void autoBoard(ServerLevel level, RocketEntity rocket) {
        RocketPortrait portrait = rocket.portrait();
        if (portrait.isEmpty()) {
            return;
        }
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }
        boolean flying = rocket.flight().mode() != FlightMode.LANDED;
        boolean auto = flying && DeckBoarding.slowEnough(rocket.getDeltaMovement().length());
        if (!auto && SEAT_REQUESTS.isEmpty()) {
            return;
        }
        double radiusSqr = reach(portrait) * reach(portrait);
        DeckFrame frame = null;
        for (ServerPlayer player : players) {
            SeatRequest request = SEAT_REQUESTS.get(player.getUUID());
            boolean seatRequest = request != null && request.rocketId == rocket.getId();
            if (!canBoard(player) || !(seatRequest || auto) || player.distanceToSqr(rocket) > radiusSqr) {
                continue;
            }
            if (frame == null) {
                frame = frameOf(rocket);
            }
            EntityDimensions dims = player.getDimensions(player.getPose());
            Vector3d local = frame.toLocal(player.getX(), player.getY(), player.getZ());
            Vector3d feet = seatRequest
                    ? DeckBoarding.settleNear(local, dims.width(), dims.height(), portrait.deckColliders(), DeckBoarding.SEAT_REACH, DeckBoarding.SEAT_ABOVE, 2)
                    : DeckBoarding.settle(local, dims.width(), dims.height(), portrait.deckColliders(), DeckBoarding.REACH, DeckBoarding.ABOVE);
            if (feet != null && board(player, rocket, startState(player, frame, feet))) {
                AUTO_BOARDED.incrementAndGet();
                SEAT_REQUESTS.remove(player.getUUID());
            }
        }
        // os pedidos do assento que não acharam lugar em alguns ticks são descartados (o jogador fica no mundo)
        for (var e : SEAT_REQUESTS.entrySet()) {
            if (e.getValue().rocketId == rocket.getId() && --e.getValue().ticksLeft <= 0) {
                SEAT_REQUESTS.remove(e.getKey());
            }
        }
    }

    /**
     * Montou a nave: quem está em pé na caixa dela, fora {@code except} (que vai sentar), entra no convés na mesma posição relativa. Devolve quantos.
     * Quem não está apoiado num bloco do retrato (no ar, ao lado) fica no mundo.
     */
    public static int boardStandingInside(ServerLevel level, RocketEntity rocket, @Nullable ServerPlayer except) {
        RocketPortrait portrait = rocket.portrait();
        if (portrait.isEmpty()) {
            return 0;
        }
        DeckFrame frame = frameOf(rocket);
        int boarded = 0;
        double radiusSqr = reach(portrait) * reach(portrait);
        for (ServerPlayer player : level.players()) {
            if (player == except || !canBoard(player) || player.distanceToSqr(rocket) > radiusSqr) {
                continue;
            }
            EntityDimensions dims = player.getDimensions(player.getPose());
            Vector3d local = frame.toLocal(player.getX(), player.getY(), player.getZ());
            Vector3d feet = DeckBoarding.settle(local, dims.width(), dims.height(), portrait.deckColliders(), DeckBoarding.REACH, DeckBoarding.ABOVE);
            if (feet != null && board(player, rocket, startState(player, frame, feet))) {
                AUTO_BOARDED.incrementAndGet();
                boarded++;
            }
        }
        return boarded;
    }

    private static void processPending(ServerPlayer player, RocketEntity rocket, Member m, long now) {
        DeckState incoming = m.pending;
        if (incoming == null) {
            return;
        }
        m.pending = null;
        EntityDimensions dims = player.getDimensions(player.getPose());
        DeckMoveCheck.Verdict verdict = DeckMoveCheck.check(m.good.pos, incoming, (int) (now - m.lastAcceptTick), dims.width(), dims.height(),
                rocket.portrait().deckColliders());
        if (verdict == DeckMoveCheck.Verdict.OK) {
            DeckFall.Step fall = DeckFall.next(m.fall, incoming.pos.y - m.good.pos.y, incoming.onGround);
            m.fall = fall.fall();
            if (fall.landed() > 0.0) {
                landed(player, rocket, incoming, fall.landed());
            }
            m.good.set(incoming);
            m.lastAcceptTick = now;
            ACCEPTED.incrementAndGet();
            return;
        }
        REJECTED.incrementAndGet();
        SpaceHorizons.LOGGER.warn("[deck] {} estado local recusado ({}): pedido ({}, {}, {}), último bom ({}, {}, {})", player.getPlainTextName(), verdict,
                round(incoming.pos.x), round(incoming.pos.y), round(incoming.pos.z), round(m.good.pos.x), round(m.good.pos.y), round(m.good.pos.z));
        DeckState back = new DeckState().set(m.good);
        back.vel.zero();
        PacketDistributor.sendToPlayer(player, new DeckCorrectionPayload(rocket.getId(), DeckMovePayload.of(rocket.getId(), back)));
    }

    /**
     * Tocou o piso do convés depois de uma queda: o dano segue o vanilla ({@code causeFallDamage}: distância segura, efeitos, armadura), com o
     * multiplicador do bloco do RETRATO sob os pés (slime, feno, mel, cama), não o do ar do mundo.
     */
    private static void landed(ServerPlayer player, RocketEntity rocket, DeckState at, double distance) {
        RocketPortrait p = rocket.portrait();
        int x = (int) Math.floor(at.pos.x);
        int y = (int) Math.floor(at.pos.y - 0.2);
        int z = (int) Math.floor(at.pos.z);
        float multiplier = 1.0F;
        if (x >= 0 && y >= 0 && z >= 0 && x < p.sizeX && y < p.sizeY && z < p.sizeZ) {
            BlockState below = p.get(x, y, z);
            multiplier = DeckFall.multiplier(BuiltInRegistries.BLOCK.getKey(below.getBlock()).getPath());
        }
        if (multiplier > 0.0F && player.causeFallDamage(distance, multiplier, player.damageSources().fall())) {
            FALL_DAMAGES.incrementAndGet();
            SpaceHorizons.LOGGER.info("[deck] {} caiu {} blocos no convés: dano de queda (x{})", player.getPlainTextName(), round(distance), multiplier);
        }
    }

    /** Se o ponto (pés) passou da caixa do retrato mais a margem. */
    static boolean outsideBox(Vector3d pos, RocketPortrait p) {
        return pos.x < -LEAVE_MARGIN || pos.y < -LEAVE_MARGIN || pos.z < -LEAVE_MARGIN || pos.x > p.sizeX + LEAVE_MARGIN || pos.y > p.sizeY + LEAVE_MARGIN
                || pos.z > p.sizeZ + LEAVE_MARGIN;
    }

    /** Põe o jogador no mundo: a posição local pela pose da nave, o olhar pela mesma rotação, sem velocidade nem queda acumulada. */
    private static void applyToPlayer(ServerPlayer player, RocketEntity rocket, Member m) {
        DeckFrame frame = frameOf(rocket);
        Vector3d w = frame.toWorld(m.good.pos.x, m.good.pos.y, m.good.pos.z);
        float[] view = frame.viewToWorld(m.good.yaw, m.good.pitch);
        player.setPos(w.x, w.y, w.z);
        player.setYRot(view[0]);
        player.setXRot(view[1]);
        player.setYHeadRot(view[0]);
        if (player.onGround() != m.good.onGround) {
            player.setOnGround(m.good.onGround);
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.level().getChunkSource().move(player);
    }

    /** A nave saiu do mundo: toda a tripulação dela deixa o convés. */
    public static void onRocketRemoved(RocketEntity rocket) {
        SEAT_REQUESTS.entrySet().removeIf(e -> e.getValue().rocketId == rocket.getId());
        if (MEMBERS.isEmpty() || !(rocket.level() instanceof ServerLevel level)) {
            return;
        }
        for (Member m : new ArrayList<>(MEMBERS.values())) {
            if (m.rocket.equals(rocket.getUUID())) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(m.player);
                if (player == null) {
                    MEMBERS.remove(m.player);
                } else {
                    leave(player, "a nave saiu do mundo");
                }
            }
        }
    }

    /** O jogador saiu do servidor. */
    public static void onLogout(ServerPlayer player) {
        MEMBERS.remove(player.getUUID());
        SEAT_REQUESTS.remove(player.getUUID());
    }

    /** O servidor parou: esquece tudo. */
    public static void reset() {
        MEMBERS.clear();
        SEAT_REQUESTS.clear();
        AUTO_BOARDED.set(0);
        FALL_DAMAGES.set(0);
        ACCEPTED.set(0);
        REJECTED.set(0);
        LEFT_BOX.set(0);
        TELEPORTS.set(0);
    }

    private static String round(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
