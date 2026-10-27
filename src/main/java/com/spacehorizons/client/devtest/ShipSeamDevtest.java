package com.spacehorizons.client.devtest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.joml.Quaterniond;
import org.joml.Quaternionfc;
import org.joml.Vector3d;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.ship.DeckClient;
import com.spacehorizons.content.ModEntities;
import com.spacehorizons.rocket.FlightMode;
import com.spacehorizons.rocket.RocketAttitude;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketException;
import com.spacehorizons.ship.ShipAssembler;
import com.spacehorizons.ship.deck.DeckCrew;
import com.spacehorizons.ship.deck.DeckFrame;
import com.spacehorizons.ship.deck.SeamJump;
import com.spacehorizons.rocket.RocketLanding;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Autoteste das trocas sem costura (fase 2g): {@code -PshOnly=shipseam}. Grava, a CADA QUADRO desenhado, o olho e a rotação da câmera (a final, depois de
 * todo o desenho: a composição com a atitude da nave no convés, o olho do assento, a mistura das trocas) e a pose do casco da nave que é desenhada.
 * Cada troca (entrar no convés, sair, sentar, levantar, ancorar, desancorar, pousar, decolar) é uma janela: {@link #begin} marca o primeiro quadro e
 * {@link #end} calcula, para cada par de quadros seguidos dentro dela, o SALTO: o quanto a posição andou além do que a nave andaria naquele intervalo
 * ({@link SeamJump}) e o quanto a câmera girou além do giro esperado da nave. O critério do plano é &lt; 0,1 bloco e &lt; 1 grau. O mesmo cálculo
 * sobre os 60 quadros ANTES da troca dá o piso de ruído da medida. O roteiro do cliente fica no {@code AutoTest}.
 */
final class ShipSeamDevtest {
    private static final String TAG = "[AUTOTEST] SHIPSEAM ";
    static final double LIMIT_POSITION = 0.1;
    static final double LIMIT_DEGREES = 1.0;
    /** Quadros antes da troca usados como piso de ruído. */
    private static final int BASELINE_FRAMES = 60;
    private static final net.minecraft.core.BlockPos BASE = ShipGroundDevtest.BASE;

    /** O ponto do convés (local) sobre o piso onde o jogador embarca na nave roteirizada. */
    private static final double BOARD_Y = 1.0;
    private static final double BOARD_Z = 2.5;

    private record Frame(double clock, Vector3d eye, Quaterniond rotation, Vector3d hull, Quaterniond hullRotation, int signature, int timeline, int blends) {
    }

    private static final List<Frame> FRAMES = new ArrayList<>();
    private static final List<String> TABLE = new ArrayList<>();
    private static boolean registered;
    private static long ticks;

    private static String swapLabel;
    private static int swapStart;
    private static Vector3d swapVelocity = new Vector3d();
    private static double swapDegreesPerTick;
    private static RocketEntity ship;
    private static ScriptedShip scripted;
    private static RocketEntity landed;
    private static final double LONG_FRAME_TICKS = 0.4;
    /** Até quantos ticks o par de quadros anterior ainda dá a velocidade própria do ponto (deslize da mistura). */
    private static final double OWN_VELOCITY_MAX_TICKS = 5.0;
    private static int longFrames;
    private static String worstPosition = "";
    private static int worstPositionIndex;
    private static int worstRotationIndex;
    private static String worstRotation = "";
    /** A nave cujo casco é gravado (id da entidade, igual no servidor integrado e no cliente), ou -1. */
    private static volatile int trackedId = -1;

    private ShipSeamDevtest() {
    }

    private static void check(String name, boolean ok, String detail) {
        ShipDevtest.check("SEAM " + name, ok, detail);
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    // ============================================================================================================ gravação

    static void register() {
        if (registered) {
            return;
        }
        registered = true;
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, e -> {
            ticks++;
            watch(Minecraft.getInstance());
        });
        NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Post.class, e -> record(Minecraft.getInstance()));
    }

    private static String lastScreen = "-";
    private static Object lastWorld;

    /** Registra as trocas de tela e de mundo (a troca de dimensão do tripulante: tem tela de carregamento?) com o tick e o olho de antes e depois. */
    private static void watch(Minecraft mc) {
        String screen = mc.gui.screen() == null ? "-" : mc.gui.screen().getClass().getSimpleName();
        if (!screen.equals(lastScreen)) {
            SpaceHorizons.LOGGER.info(TAG + "tela {} -> {} no tick {} (mundo {})", lastScreen, screen, ticks, mc.level == null ? "nenhum" : mc.level.dimension());
            lastScreen = screen;
        }
        Object world = mc.level;
        if (world != lastWorld) {
            SpaceHorizons.LOGGER.info(TAG + "mundo {} -> {} no tick {}; câmera {}", lastWorld == null ? "nenhum" : "anterior", mc.level == null ? "nenhum" : mc.level.dimension(), ticks,
                    mc.level == null ? "-" : mc.gameRenderer.mainCamera().position());
            lastWorld = world;
        }
    }

    private static void record(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double clock = ticks + (double) partial;
        var camera = mc.gameRenderer.mainCamera();
        Vec3 p = camera.position();
        var q = camera.rotation();
        Vector3d hull = new Vector3d(Double.NaN, Double.NaN, Double.NaN);
        Quaterniond hullRotation = new Quaterniond();
        RocketEntity rocket = trackedId < 0 ? null : (mc.level.getEntity(trackedId) instanceof RocketEntity r && !r.isRemoved() ? r : null);
        if (rocket != null && !rocket.portrait().isEmpty()) {
            DeckFrame frame = rocket.deckFrameAt(partial);
            hull.set(frame.cx(), frame.cy(), frame.cz());
            Quaternionfc a = frame.attitude();
            hullRotation.set(a.x(), a.y(), a.z(), a.w());
        }
        int signature = (DeckClient.isAboard() ? 1 : 0) + (mc.player.isPassenger() ? 2 : 0);
        int timeline = 0;
        if (rocket != null) {
            double c = rocket.clientTickAt(partial);
            timeline = rocket.smoothFrame(c) == null ? 0 : (rocket.smoothStarved(c) ? 2 : 1);
        }
        FRAMES.add(new Frame(clock, new Vector3d(p.x, p.y, p.z), new Quaterniond(q.x(), q.y(), q.z(), q.w()), hull, hullRotation, signature, timeline, com.spacehorizons.client.ship.CameraSeam.blendCount()));
    }

    // ============================================================================================================ janelas de medida

    /** Abre a janela de uma troca: a nave anda {@code velocity} blocos por tick e gira {@code degreesPerTick} (o que se desconta do salto). */
    static void begin(String label, Vector3d velocity, double degreesPerTick) {
        swapLabel = label;
        swapStart = FRAMES.size();
        swapVelocity = new Vector3d(velocity);
        swapDegreesPerTick = degreesPerTick;
    }

    /** Fecha a janela e confere o salto da troca (câmera e casco). */
    static void end(Minecraft mc) {
        close(true);
    }

    /** Fecha a janela só registrando os números (sem verificação). */
    static void endInfo(Minecraft mc) {
        close(false);
    }

    private static void close(boolean assertIt) {
        int to = FRAMES.size();
        int from = Math.max(1, swapStart - BASELINE_FRAMES);
        double[] cameraBase = worst(from, swapStart, false);
        double[] camera = worst(swapStart, to, false);
        double[] hullBase = worst(from, swapStart, true);
        double[] hull = worst(swapStart, to, true);
        double t0 = swapStart < FRAMES.size() ? FRAMES.get(swapStart).clock : 0.0;
        double travelled = 0.0;
        for (int i = swapStart; i < to; i++) {
            travelled = Math.max(travelled, FRAMES.get(i).eye.distance(FRAMES.get(swapStart).eye));
        }
        String line = String.format(Locale.ROOT, "%-34s | câmera %.4f bloco (aos +%.2f ticks), %.3f° (aos +%.2f) | casco %.4f bloco, %.3f° | ruído antes: câmera %.4f bloco, %.3f°, casco %.4f, %.3f° | %d quadros, câmera andou até %.2f blocos",
                swapLabel, camera[0], camera[2] - t0, camera[1], camera[3] - t0, hull[0], hull[1], cameraBase[0], cameraBase[1], hullBase[0], hullBase[1], to - swapStart, travelled);
        SpaceHorizons.LOGGER.info(TAG + line);
        if (!worstPosition.isEmpty() && camera[0] >= LIMIT_POSITION) {
            StringBuilder around = new StringBuilder();
            for (int i = Math.max(0, worstPositionIndex - 4); i < Math.min(FRAMES.size(), worstPositionIndex + 4); i++) {
                Frame fr = FRAMES.get(i);
                around.append(String.format(Locale.ROOT, "[%d t+%.2f (%.3f, %.3f, %.3f) as %d] ", i - worstPositionIndex, fr.clock - t0, fr.eye.x, fr.eye.y, fr.eye.z, fr.signature));
            }
            SpaceHorizons.LOGGER.info(TAG + "  quadros em volta do pior salto de posição: " + around);
        }
        if (!worstRotation.isEmpty() && camera[1] >= LIMIT_DEGREES) {
            StringBuilder around = new StringBuilder();
            for (int i = Math.max(0, worstRotationIndex - 4); i < Math.min(FRAMES.size(), worstRotationIndex + 4); i++) {
                Frame fr = FRAMES.get(i);
                around.append(String.format(Locale.ROOT, "[%d t+%.2f %s as %d misturas %d] ", i - worstRotationIndex, fr.clock - t0, euler(fr.rotation), fr.signature, fr.blends));
            }
            SpaceHorizons.LOGGER.info(TAG + "  quadros em volta do pior salto de rotação: " + around);
        }
        SpaceHorizons.LOGGER.info(TAG + "  pior da câmera na janela: posição [{}]; rotação [{}]; quadros longos (> {} ticks) descartados: {}", worstPosition, worstRotation, LONG_FRAME_TICKS, longFrames);
        TABLE.add(line);
        if (!assertIt) {
            return;
        }
        check(swapLabel + ": câmera sem salto (< 0,1 bloco e < 1°)", camera[0] < LIMIT_POSITION && camera[1] < LIMIT_DEGREES,
                "posição " + f(camera[0]) + " bloco, rotação " + f(camera[1]) + "°");
        check(swapLabel + ": casco desenhado sem salto (< 0,1 bloco e < 1°)", hull[0] < LIMIT_POSITION && hull[1] < LIMIT_DEGREES,
                "posição " + f(hull[0]) + " bloco, rotação " + f(hull[1]) + "°");
    }

    /** O pior salto de posição e de rotação entre quadros seguidos de [from, to): {posição, graus, relógio da posição, relógio da rotação}. */
    private static double[] worst(int from, int to, boolean hull) {
        double maxPos = 0.0;
        double maxAng = 0.0;
        double atPos = 0.0;
        double atAng = 0.0;
        if (!hull && from >= swapStart) {
            longFrames = 0;
            worstPosition = "";
            worstRotation = "";
        }
        for (int i = Math.max(1, from); i < to && i < FRAMES.size(); i++) {
            Frame a = FRAMES.get(i - 1);
            Frame b = FRAMES.get(i);
            double dt = b.clock - a.clock;
            if (dt <= 0.0) {
                continue;
            }
            if (dt > LONG_FRAME_TICKS) {
                // um quadro longo (carregando chunk, coleta de lixo) não é salto de troca: a nave andou o dt inteiro e é contado à parte
                if (!hull) {
                    longFrames++;
                }
                continue;
            }
            Vector3d pa = hull ? a.hull : a.eye;
            Vector3d pb = hull ? b.hull : b.eye;
            if (Double.isNaN(pa.x) || Double.isNaN(pb.x)) {
                continue;
            }
            // a velocidade que o ponto trazia do par anterior, para não contar um deslize suave como salto; vale mesmo depois de um quadro longo
            // (a média dele ainda é o deslize), e um teleporte escondido num quadro longo não se repete no quadro seguinte, então continua salto
            Vector3d own = new Vector3d();
            Quaterniond ra = hull ? a.hullRotation : a.rotation;
            Quaterniond rb = hull ? b.hullRotation : b.rotation;
            double ang = SeamJump.angle(ra, rb, swapDegreesPerTick, dt);
            if (i >= 2) {
                Frame z = FRAMES.get(i - 2);
                Vector3d pz = hull ? z.hull : z.eye;
                double dtz = a.clock - z.clock;
                if (dtz > 0.0 && dtz <= OWN_VELOCITY_MAX_TICKS && !Double.isNaN(pz.x)) {
                    own.set(pa).sub(pz).div(dtz);
                    ang = SeamJump.angle(hull ? z.hullRotation : z.rotation, ra, rb, swapDegreesPerTick, dtz, dt);
                }
            }
            double pos = SeamJump.position(pa, pb, swapVelocity, own, dt);
            if (pos > maxPos) {
                maxPos = pos;
                atPos = b.clock;
                if (!hull && from >= swapStart) {
                    worstPositionIndex = i;
                    worstPosition = String.format(Locale.ROOT, "dt %.2f ticks, assinatura %d->%d, linha do tempo %d->%d, deslocamento (%.3f, %.3f, %.3f)", dt, a.signature, b.signature, a.timeline, b.timeline, pb.x - pa.x, pb.y - pa.y, pb.z - pa.z);
                }
            }
            if (ang > maxAng) {
                maxAng = ang;
                atAng = b.clock;
                if (!hull && from >= swapStart) {
                    worstRotationIndex = i;
                    worstRotation = String.format(Locale.ROOT, "dt %.2f ticks, assinatura %d->%d, ângulo %.3f, euler YXZ antes %s depois %s", dt, a.signature, b.signature,
                            SeamJump.angleDegrees(a.rotation, b.rotation), euler(a.rotation), euler(b.rotation));
                }
            }
        }
        return new double[] {maxPos, maxAng, atPos, atAng};
    }

    private static String euler(Quaterniond q) {
        Vector3d e = q.getEulerAnglesYXZ(new Vector3d());
        return String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", Math.toDegrees(e.y), Math.toDegrees(e.x), Math.toDegrees(e.z));
    }

    static void summary() {
        SpaceHorizons.LOGGER.info(TAG + "TABELA ({} trocas; salto = deslocamento e giro além do esperado da nave, entre quadros seguidos):", TABLE.size());
        for (String line : TABLE) {
            SpaceHorizons.LOGGER.info(TAG + "  " + line);
        }
    }

    // ============================================================================================================ ground: decolar e pousar

    private static <T> T onServer(Minecraft mc, java.util.function.Function<ServerPlayer, T> body) {
        return ShipDevtest.onServer(mc, server -> body.apply(server.getPlayerList().getPlayers().get(0)));
    }

    /** Monta a nave de teste no chão (a ponte da ShipGround) com o jogador em pé sobre o piso: decolar. */
    static void assembleStanding(Minecraft mc) {
        String result = onServer(mc, player -> {
            ServerLevel level = (ServerLevel) player.level();
            try {
                landed = ShipAssembler.assemble(level, BASE.offset(0, 1, 0), null);
            } catch (RocketException e) {
                return "recusada: " + e.getMessage();
            }
            return "ok";
        });
        check("montagem no chão com o jogador em pé", "ok".equals(result), result);
    }

    static boolean aboard(Minecraft mc) {
        return DeckClient.isAboard() && DeckClient.rocket() != null;
    }

    /** Desmonta a nave no chão (pousar) com o jogador em pé no convés. */
    static void landNow(Minecraft mc) {
        String result = onServer(mc, player -> {
            ServerLevel level = (ServerLevel) player.level();
            try {
                RocketLanding.land(level, landed);
            } catch (RocketException e) {
                return "recusada: " + e.getMessage();
            }
            return "ok";
        });
        check("desmontagem no chão com o jogador em pé", "ok".equals(result), result);
    }

    static boolean standingOnGround(Minecraft mc) {
        return !DeckClient.isAboard() && mc.player != null && mc.player.onGround() && !mc.player.isPassenger();
    }

    // ============================================================================================================ a nave roteirizada

    /** O roteiro: velocidade e giro constantes; no tick {@code boardTick} o jogador (parado no mundo sobre o piso) embarca pelo comando. */
    private static final class ScriptedShip implements Consumer<RocketEntity> {
        private final Vec3 velocity;
        private final double yawRate;
        private double yaw;
        private Vec3 position;
        private final int boardTick;
        private int tick;

        ScriptedShip(Vec3 start, double yaw0, Vec3 velocity, double yawRate, int boardTick) {
            this.position = start;
            this.yaw = yaw0;
            this.velocity = velocity;
            this.yawRate = yawRate;
            this.boardTick = boardTick;
        }

        @Override
        public void accept(RocketEntity rocket) {
            this.tick++;
            this.position = this.position.add(this.velocity);
            this.yaw += this.yawRate;
            rocket.setPos(this.position);
            rocket.setDeltaMovement(this.velocity);
            rocket.setAttitude(RocketAttitude.fromYaw(this.yaw));
            if (this.tick == this.boardTick && rocket.level() instanceof ServerLevel level) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
                boolean ok = DeckCrew.boardAtWorld(player, rocket);
                SpaceHorizons.LOGGER.info(TAG + "embarque pelo comando no tick {} da nave: {}", this.tick, ok);
            }
        }
    }

    /**
     * Gera a nave roteirizada de modo que, no tick {@code alignTick} dela, o ponto local ({@code localX}, piso, {@code localZ}) esteja {@code lift} blocos
     * acima dos pés do jogador, que fica parado no chão. Com {@code boardByCommand} o jogador embarca nesse tick; sem, quem embarca é o jogador que
     * pula (embarque ao pisar).
     */
    static void spawnScripted(Minecraft mc, Vector3d velocity, double degreesPerTick, int alignTick, boolean boardByCommand, double lift, double localX, double localZ,
            boolean flying) {
        String result = onServer(mc, player -> {
            ServerLevel level = (ServerLevel) player.level();
            RocketEntity rocket = ModEntities.ROCKET.get().create(level, EntitySpawnReason.TRIGGERED);
            if (rocket == null) {
                return "sem entidade";
            }
            rocket.setPortrait(ShipDeckRealDevtest.hoverPortrait());
            rocket.setFuelMb(0.0);
            Vec3 ground = new Vec3(BASE.getX() + 6.5, BASE.getY() + 1.0, BASE.getZ() + 0.5);
            double yaw0 = 20.0;
            rocket.setAttitude(RocketAttitude.fromYaw(yaw0 + degreesPerTick * alignTick));
            rocket.setPos(0.0, 0.0, 0.0);
            Vector3d w0 = rocket.deckFrame().toWorld(localX, BOARD_Y, localZ);
            Vec3 centerAtAlign = new Vec3(ground.x - w0.x, ground.y + lift - w0.y, ground.z - w0.z);
            Vec3 v = new Vec3(velocity.x, velocity.y, velocity.z);
            Vec3 start = centerAtAlign.subtract(v.scale(alignTick));
            rocket.setAttitude(RocketAttitude.fromYaw(yaw0));
            rocket.setPos(start);
            scripted = new ScriptedShip(start, yaw0, v, degreesPerTick, boardByCommand ? alignTick : -1);
            rocket.setDevtestScript(scripted);
            rocket.flight().setMode(flying ? FlightMode.FLIGHT : FlightMode.LANDED);
            level.addFreshEntity(rocket);
            ship = rocket;
            trackedId = rocket.getId();
            player.teleportTo(ground.x, ground.y, ground.z);
            player.setDeltaMovement(Vec3.ZERO);
            return "ok";
        });
        check("nave roteirizada criada", "ok".equals(result), result);
    }

    /** O jogador pula do chão (o vanilla: velocidade vertical de 0,42). */
    static void jump(Minecraft mc) {
        mc.player.setDeltaMovement(0.0, 0.42, 0.0);
        mc.player.setOnGround(false);
    }

    static boolean boardedByStepping(Minecraft mc) {
        return DeckClient.isAboard() && DeckClient.rocket() != null && onServer(mc, player -> DeckCrew.isCrew(player));
    }

    /** Senta o jogador (como o clique no cockpit). */
    static void sit(Minecraft mc) {
        boolean ok = onServer(mc, player -> {
            player.setDeltaMovement(Vec3.ZERO);
            return player.startRiding(ship, true, true);
        });
        check("sentou na nave roteirizada", ok, "sentou " + ok);
    }

    static boolean sat(Minecraft mc) {
        return !DeckClient.isAboard() && mc.player != null && mc.player.isPassenger();
    }

    static void standUp(Minecraft mc) {
        onServer(mc, player -> {
            player.stopRiding();
            return null;
        });
    }

    static boolean standingAboard(Minecraft mc) {
        return DeckClient.isAboard() && onServer(mc, player -> DeckCrew.isCrew(player));
    }

    /** O jogador sai do convés pelo comando (volta ao mundo, em pé, onde estava). */
    static void leaveNow(Minecraft mc) {
        onServer(mc, player -> {
            // voando em criativo: quem sai de uma nave que anda longe da plataforma não despenca durante a janela de medida (a queda livre não é salto)
            if (ship != null) {
                // pousada (sem empuxo): o embarque automático não leva o jogador de volta ao convés logo depois de sair
                ship.flight().setMode(FlightMode.LANDED);
            }
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            DeckCrew.leave(player, "autoteste shipseam");
            return null;
        });
    }

    static boolean notAboard(Minecraft mc) {
        return !DeckClient.isAboard() && mc.player != null && !mc.player.isPassenger();
    }

    static void cleanup(Minecraft mc) {
        ShipDevtest.onServer(mc, server -> {
            if (ship != null && !ship.isRemoved()) {
                ship.setDevtestScript(null);
                ship.discard();
            }
            return null;
        });
        ship = null;
        trackedId = -1;
    }

    /** O pé do jogador voltou ao chão (depois de sair da nave): põe o jogador sobre o ponto do chão de novo para a próxima troca. */
    static void standOnGround(Minecraft mc) {
        onServer(mc, player -> {
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.teleportTo(BASE.getX() + 6.5, BASE.getY() + 1.0, BASE.getZ() + 0.5);
            player.setDeltaMovement(Vec3.ZERO);
            return null;
        });
    }

    static BlockPos base() {
        return BASE;
    }
}
