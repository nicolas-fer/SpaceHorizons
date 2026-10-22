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
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketPortrait;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
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
 */
public final class DeckCrew {
    /** Quanto o tripulante pode passar da caixa do retrato (blocos) antes de deixar o convés. */
    public static final double LEAVE_MARGIN = 3.0;

    private static final Map<UUID, Member> MEMBERS = new ConcurrentHashMap<>();

    /** Contagens para o autoteste e o diagnóstico: estados aceitos, recusados (correções enviadas) e saídas pelo limite da caixa. */
    public static final AtomicLong ACCEPTED = new AtomicLong();
    public static final AtomicLong REJECTED = new AtomicLong();
    public static final AtomicLong LEFT_BOX = new AtomicLong();
    /** Correções de posição que o servidor mandou a jogadores (o pacote de teletransporte do vanilla, que é o "moved wrongly" visto de fora). */
    public static final AtomicLong TELEPORTS = new AtomicLong();

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
        SpaceHorizons.LOGGER.info("[deck] {} deixou o convés ({})", player.getPlainTextName(), reason);
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
        }
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
    }

    /** O servidor parou: esquece tudo. */
    public static void reset() {
        MEMBERS.clear();
        ACCEPTED.set(0);
        REJECTED.set(0);
        LEFT_BOX.set(0);
        TELEPORTS.set(0);
    }

    private static String round(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
