package com.spacehorizons.client.ship;

import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.ship.deck.DeckBoardPayload;
import com.spacehorizons.ship.deck.DeckCollision;
import com.spacehorizons.ship.deck.DeckCorrectionPayload;
import com.spacehorizons.ship.deck.DeckFrame;
import com.spacehorizons.ship.deck.DeckLeavePayload;
import com.spacehorizons.ship.deck.DeckMovePayload;
import com.spacehorizons.ship.deck.DeckState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * O jogador local no convés de uma nave em movimento (spike S5): o estado dele vive NO REFERENCIAL DA NAVE ({@link DeckState}) e é simulado aqui,
 * no cliente, em coordenadas do retrato. O movimento vanilla do jogador ({@code Entity.move}, chamado pela física de sempre: gravidade, atrito,
 * pulo, sprint) é redirecionado por um mixin para a colisão local ({@link #moveLocal}), sem tocar no mundo. O mundo só entra na ponta:
 * <ul>
 *   <li>no fim de cada tick, a posição do jogador no mundo é DERIVADA da local pela pose da nave daquele tick (a anterior e a atual, para o
 *       desenho interpolar o jogador como interpola a nave);</li>
 *   <li>no desenho, a câmera usa a pose interpolada do quadro ({@link DeckCamera}), a mesma do desenho da nave;</li>
 *   <li>o estado local segue ao servidor em {@link DeckMovePayload} (no lugar do pacote de posição do vanilla).</li>
 * </ul>
 * Quem decide embarcar e desembarcar é o servidor ({@code DeckCrew}); aqui só se obedece aos pacotes.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class DeckClient {
    /** Ticks de espera pela entidade da nave (o pacote de embarque pode chegar antes dela) antes de desistir. */
    private static final int BOARD_PATIENCE = 100;

    /** Ticks do cliente desde o começo: o relógio da linha do tempo da nave (sem os saltos do tempo de jogo, que o servidor corrige). */
    private static long tickCounter;
    private static boolean active;
    private static int rocketId = -1;
    private static final DeckState STATE = new DeckState();
    private static final Vector3d PREV = new Vector3d();
    private static @Nullable DeckBoardPayload pendingBoard;
    /** A pose e o tempo do cliente que a última derivação da posição do jogador usou (diagnóstico do autoteste). */
    private static @Nullable DeckFrame lastGlueFrame;
    private static double lastGlueClock;
    private static int pendingTicks;
    private static int missingTicks;
    private static int sent;
    private static int corrections;
    private static int boards;

    private DeckClient() {
    }

    // ---------------------------------------------------------------- consulta

    /** O jogador local está no referencial de uma nave? */
    public static boolean isAboard() {
        return active;
    }

    public static boolean isAboard(Object player) {
        return active && player == Minecraft.getInstance().player;
    }

    /** O estado local atual (cópia). */
    public static DeckState state() {
        return new DeckState().set(STATE);
    }

    /** A posição local dos pés no começo do tick atual (a anterior). */
    public static Vector3d previousLocal() {
        return new Vector3d(PREV);
    }

    public static @Nullable DeckFrame lastGlueFrame() {
        return lastGlueFrame;
    }

    public static double lastGlueClock() {
        return lastGlueClock;
    }

    public static int sentCount() {
        return sent;
    }

    public static int correctionCount() {
        return corrections;
    }

    public static int boardCount() {
        return boards;
    }

    /** A entidade da nave em que o jogador está, ou null. */
    public static @Nullable RocketEntity rocket() {
        Minecraft mc = Minecraft.getInstance();
        return active && mc.level != null && mc.level.getEntity(rocketId) instanceof RocketEntity r && !r.isRemoved() ? r : null;
    }

    /** O quanto o jogador andou na horizontal neste tick, no referencial local (para a animação de caminhada, que o vanilla mediria no mundo). */
    public static double localStep() {
        return Math.hypot(STATE.pos.x - PREV.x, STATE.pos.z - PREV.z);
    }

    /** Onde está o olho do jogador no mundo neste quadro: a pose interpolada da nave aplicada ao olho local interpolado. */
    public static @Nullable Vec3 eyeWorld(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        RocketEntity rocket = rocket();
        if (rocket == null || mc.player == null) {
            return null;
        }
        return worldAt(rocket, partialTick, mc.player.getEyeHeight());
    }

    /** Os pés do jogador no mundo neste quadro, pela mesma conta do olho. */
    public static @Nullable Vec3 feetWorld(float partialTick) {
        RocketEntity rocket = rocket();
        return rocket == null ? null : worldAt(rocket, partialTick, 0.0);
    }

    /**
     * O quanto o modelo do jogador (desenhado pelo vanilla na interpolação linear entre a posição antiga e a nova do tick) está fora da posição exata
     * deste quadro (a pose da nave do quadro aplicada ao pé local interpolado). Num tick em que a nave muda de velocidade a interpolação linear
     * corta o canto da trajetória (a 100 blocos por tick, dezenas de blocos); o desenho do modelo soma esta diferença e fica sempre no convés.
     */
    public static Vec3 modelCorrection(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 exact = feetWorld(partialTick);
        if (exact == null || mc.player == null) {
            return Vec3.ZERO;
        }
        return exact.subtract(mc.player.getPosition(partialTick));
    }

    private static Vec3 worldAt(RocketEntity rocket, float partialTick, double up) {
        double t = partialTick;
        double lx = PREV.x + (STATE.pos.x - PREV.x) * t;
        double ly = PREV.y + (STATE.pos.y - PREV.y) * t + up;
        double lz = PREV.z + (STATE.pos.z - PREV.z) * t;
        Vector3d w = rocket.deckFrameAt(partialTick).toWorld(lx, ly, lz);
        return new Vec3(w.x, w.y, w.z);
    }

    // ---------------------------------------------------------------- pacotes

    @SubscribeEvent
    static void onRegisterHandlers(RegisterClientPayloadHandlersEvent event) {
        RocketEntity.clientTicks = () -> tickCounter;
        RocketEntity.clientClock = () -> tickCounter + (double) Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        event.register(DeckBoardPayload.TYPE, (payload, context) -> {
            pendingBoard = payload;
            pendingTicks = 0;
            tryStart(Minecraft.getInstance());
        });
        event.register(DeckLeavePayload.TYPE, (payload, context) -> deactivate("pacote do servidor"));
        event.register(DeckCorrectionPayload.TYPE, (payload, context) -> {
            if (active && payload.rocketId() == rocketId) {
                corrections++;
                DeckState s = payload.state().toState();
                STATE.pos.set(s.pos);
                STATE.vel.zero();
                PREV.set(s.pos);
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.setDeltaMovement(Vec3.ZERO);
                }
                SpaceHorizons.LOGGER.warn("[deck] correção do servidor: de volta a ({}, {}, {}) locais", s.pos.x, s.pos.y, s.pos.z);
            }
        });
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        deactivate("saiu do mundo");
        pendingBoard = null;
    }

    private static void tryStart(Minecraft mc) {
        DeckBoardPayload board = pendingBoard;
        if (board == null || mc.level == null || mc.player == null) {
            return;
        }
        if (!(mc.level.getEntity(board.rocketId()) instanceof RocketEntity rocket) || rocket.portrait().isEmpty()) {
            if (++pendingTicks > BOARD_PATIENCE) {
                SpaceHorizons.LOGGER.warn("[deck] a nave {} não apareceu no cliente; embarque descartado", board.rocketId());
                pendingBoard = null;
            }
            return;
        }
        pendingBoard = null;
        DeckState s = board.state().toState();
        STATE.set(s);
        PREV.set(s.pos);
        rocketId = board.rocketId();
        active = true;
        missingTicks = 0;
        boards++;
        LocalPlayer p = mc.player;
        p.setYRot(s.yaw);
        p.setXRot(s.pitch);
        // o olhar anterior é o novo: sem isto o vanilla interpola o olhar do mundo para o local ao longo do primeiro tick e a câmera gira (fase 2g)
        p.yRotO = s.yaw;
        p.xRotO = s.pitch;
        p.setDeltaMovement(Vec3.ZERO);
        glue(p, rocket);
        SpaceHorizons.LOGGER.info("[deck] no convés da nave {} em ({}, {}, {}) locais", rocketId, s.pos.x, s.pos.y, s.pos.z);
    }

    private static void deactivate(String reason) {
        if (active) {
            SpaceHorizons.LOGGER.info("[deck] fora do convés ({})", reason);
        }
        active = false;
        rocketId = -1;
    }

    // ---------------------------------------------------------------- tick

    @SubscribeEvent
    static void onTickPre(ClientTickEvent.Pre event) {
        tickCounter++;
        if (active) {
            PREV.set(STATE.pos);
        }
    }

    @SubscribeEvent
    static void onTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            return;
        }
        if (pendingBoard != null) {
            tryStart(mc);
        }
        if (!active) {
            return;
        }
        RocketEntity rocket = rocket();
        if (rocket == null) {
            // a nave ainda não está (ou deixou de estar) no cliente: espera um pouco, depois solta o jogador
            if (++missingTicks > BOARD_PATIENCE) {
                deactivate("a nave sumiu do cliente");
            }
            return;
        }
        missingTicks = 0;
        if (p.isPassenger()) {
            deactivate("sentou");
            return;
        }
        STATE.yaw = p.getYRot();
        STATE.pitch = p.getXRot();
        STATE.vel.set(p.getDeltaMovement().x, p.getDeltaMovement().y, p.getDeltaMovement().z);
        STATE.onGround = p.onGround();
        glue(p, rocket);
        ClientPacketDistributor.sendToServer(DeckMovePayload.of(rocketId, STATE));
        sent++;
    }

    /**
     * Deriva a posição do jogador no mundo: a antiga pela pose do tick anterior e a nova pela do tick atual (ambas já são do fim do tick, porque isto
     * roda depois de todas as entidades), de modo que o desenho do jogador interpolado entre elas acompanha o da nave.
     */
    private static void glue(LocalPlayer p, RocketEntity rocket) {
        // o desenho do jogador interpola entre estas duas posições com a parcial do quadro; a nave é desenhada no mesmo tempo (g + parcial), então
        // as duas poses são as da linha do tempo suavizada em g e g + 1 (ou, sem ela, as do tick anterior e atual do vanilla)
        double clientTick = rocket.clientTickAt(0.0F);
        DeckFrame previousFrame = rocket.smoothFrame(clientTick);
        DeckFrame frame = rocket.smoothFrame(clientTick + 1.0);
        if (previousFrame == null || frame == null) {
            previousFrame = rocket.deckFramePrevious();
            frame = rocket.deckFrame();
        }
        lastGlueFrame = frame;
        lastGlueClock = clientTick;
        Vector3d before = previousFrame.toWorld(PREV.x, PREV.y, PREV.z);
        Vector3d now = frame.toWorld(STATE.pos.x, STATE.pos.y, STATE.pos.z);
        p.setOldPosAndRot(new Vec3(before.x, before.y, before.z), p.yRotO, p.xRotO);
        p.setPos(now.x, now.y, now.z);
    }

    // ---------------------------------------------------------------- blocos do retrato sob os pés (atrito, velocidade, pulo)

    /** O bloco do retrato na célula local (ar fora do retrato ou sem a nave). */
    private static BlockState portraitState(int x, int y, int z) {
        RocketEntity rocket = rocket();
        if (rocket == null) {
            return Blocks.AIR.defaultBlockState();
        }
        var p = rocket.portrait();
        if (x < 0 || y < 0 || z < 0 || x >= p.sizeX || y >= p.sizeY || z >= p.sizeZ) {
            return Blocks.AIR.defaultBlockState();
        }
        return p.get(x, y, z);
    }

    /** O bloco do retrato onde estão os pés (a célula local da posição), para o fator de velocidade e de pulo. */
    private static BlockState stateHere() {
        return portraitState((int) Math.floor(STATE.pos.x), (int) Math.floor(STATE.pos.y), (int) Math.floor(STATE.pos.z));
    }

    /** O bloco do retrato que sustenta os pés, como o vanilla escolhe (meio bloco e um fio abaixo): o que dá o atrito (gelo escorrega). */
    private static BlockState stateBelow() {
        return portraitState((int) Math.floor(STATE.pos.x), (int) Math.floor(STATE.pos.y - 0.500001), (int) Math.floor(STATE.pos.z));
    }

    /**
     * O bloco sob o pé que a física do vanilla deve ler no lugar do ar do mundo: o do RETRATO, se {@code entity} é o jogador local no convés; senão
     * {@code vanilla}. Chamado pelo mixin do atrito ({@code LivingEntity.travelInAir}).
     */
    public static BlockState groundState(Entity entity, BlockState vanilla) {
        return isAboard(entity) ? stateBelow() : vanilla;
    }

    /** O fator de velocidade dos blocos do retrato (alma de areia, mel) para o jogador local no convés, ou null fora dele (vale o vanilla). */
    public static @Nullable Float speedFactor(Entity entity) {
        if (!isAboard(entity)) {
            return null;
        }
        float here = stateHere().getBlock().getSpeedFactor();
        return here == 1.0F ? stateBelow().getBlock().getSpeedFactor() : here;
    }

    /** O fator de pulo dos blocos do retrato (mel) para o jogador local no convés, ou null fora dele. */
    public static @Nullable Float jumpFactor(Entity entity) {
        if (!isAboard(entity)) {
            return null;
        }
        float here = stateHere().getBlock().getJumpFactor();
        return here == 1.0F ? stateBelow().getBlock().getJumpFactor() : here;
    }

    // ---------------------------------------------------------------- física local

    /**
     * Chamado pelo mixin no lugar de {@code Entity.move} do jogador local: move o jogador no referencial da nave, com a colisão dos sólidos do
     * retrato. Devolve false (e o vanilla segue) se o jogador não está a bordo ou a nave ainda não chegou.
     *
     * @param delta o movimento que a física do vanilla quer, já NO REFERENCIAL LOCAL (o yaw do jogador é o local e a gravidade é para baixo local)
     */
    public static boolean moveLocal(LocalPlayer p, Vec3 delta) {
        if (!active || p.noPhysics) {
            return false;
        }
        RocketEntity rocket = rocket();
        if (rocket == null) {
            return false;
        }
        EntityDimensions dims = p.getDimensions(p.getPose());
        double half = dims.width() / 2.0;
        AABB box = new AABB(STATE.pos.x - half, STATE.pos.y, STATE.pos.z - half, STATE.pos.x + half, STATE.pos.y + dims.height(), STATE.pos.z + half);
        DeckCollision.Result r = DeckCollision.move(box, delta, rocket.portrait().deckColliders(), p.maxUpStep(), p.onGround());
        STATE.pos.add(r.moved().x, r.moved().y, r.moved().z);
        p.horizontalCollision = r.horizontalCollision();
        p.verticalCollision = r.collidedY();
        p.verticalCollisionBelow = r.collidedY() && delta.y < 0.0;
        p.setOnGroundWithMovement(p.verticalCollisionBelow, p.horizontalCollision, r.moved());
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(r.collidedX() ? 0.0 : v.x, r.collidedY() ? 0.0 : v.y, r.collidedZ() ? 0.0 : v.z);
        p.resetFallDistance();
        return true;
    }
}
