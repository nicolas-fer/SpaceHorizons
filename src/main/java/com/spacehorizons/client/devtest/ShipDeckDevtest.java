package com.spacehorizons.client.devtest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.ship.DeckClient;
import com.spacehorizons.network.NetLatency;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.ship.ShipTestRocket;
import com.spacehorizons.ship.deck.DeckCrew;
import com.spacehorizons.ship.deck.DeckFrame;
import com.spacehorizons.ship.deck.DeckState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Autoteste do convés andável (spike S5): {@code -PshOnly=shipdeck}. Um foguete oco de ~4.000 blocos (o de teste da conversão: casco de aço, piso de
 * tijolo, baús, tanques, forno) é uma entidade ROTEIRIZADA que sobe, paira e desce a 1, 10, 50 e 100 blocos por tick, gira 20 graus por segundo em
 * torno do próprio eixo e fica inclinada 30 graus; o jogador está no referencial dela ({@code DeckCrew}) e um robô (as teclas do jogador, de
 * verdade) anda em quadrado no convés por 2.000 ticks. Mede, por cenário, as quedas pelo piso, a altura do pé em relação ao piso LOCAL (na simulação do
 * cliente, no servidor e, a cada quadro, na posição do jogador no mundo vista pela pose da nave), a deriva do jogador parado, as correções do servidor e
 * o tremor da câmera. Repete com latência simulada.
 */
final class ShipDeckDevtest {
    private static final String TAG = "[AUTOTEST] SHIPDECK ";

    /** Ticks da corrida medida. */
    static final int RUN_TICKS = 2_000;
    /** O topo do piso do foguete de teste no referencial local (o casco do fundo tem 1 bloco). */
    static final double FLOOR_TOP = 1.0;
    /** Faixa de altitude (blocos) em que a nave sobe e desce: longe do terreno e abaixo da altitude de saída da Terra (5.000). */
    static final double Y_LOW = 500.0;
    static final double Y_HIGH = 4_500.0;
    /** Giro em torno do eixo da nave (graus por tick: 20 por segundo) e inclinação final (graus). */
    static final double YAW_DEG_PER_TICK = 1.0;
    static final double TILT_DEG = 30.0;
    /** O quadrado do robô, no referencial local (centro do bloco): fora dos tanques, do cockpit, do forno, do barril e dos baús. */
    private static final double[][] CORNERS = {{5.5, 5.5}, {20.5, 5.5}, {20.5, 20.5}, {5.5, 20.5}};
    /** Ticks da corrida em que o robô para por STAND_TICKS (na subida, no pairar e na descida) para medir a deriva e o tremor. */
    static final int[] STAND_AT = {400, 800, 1_200};
    static final int STAND_TICKS = 60;
    /** Ticks do começo de cada janela de parada que o atrito ainda gasta para frear o jogador (fora da medida da deriva). */
    private static final int SETTLE_TICKS = 10;
    /** Quedas: o pé abaixo do piso por mais que isto (blocos) é cair pelo convés. */
    private static final double FALL_BELOW = 0.5;
    /** Ticks do começo da corrida fora das medidas de pose: a linha do tempo da nave se enche e o desenho troca do vanilla para ela (um pulo único). */
    private static final int WARM_UP_TICKS = 40;

    private static ShipTestRocket.Built built;
    private static UUID rocketUuid;
    private static boolean registered;

    // ---- estado da corrida ----
    private static int speed;
    private static int latencyMs;
    private static volatile boolean running;
    private static int runTick;
    private static int waypoint;
    private static int jumpTicks;

    // ---- medidas (cliente) ----
    private static final Samples CLIENT_FOOT = new Samples();
    private static final Samples SEEN_FOOT = new Samples();
    private static final Samples SEEN_SLIP = new Samples();
    private static final Samples RAW_SLIP = new Samples();
    private static int slipLogs;
    private static int footLogs;
    private static int fellTicks;
    private static int leftTicks;
    private static int airborneTicks;
    private static int groundedTicks;
    private static double standDriftClient;
    private static double standDriftSeen;
    private static boolean lastGrounded;
    private static Vector3d lastSeenLocal;
    private static Vector3d lastStateLocal;
    private static int clientCorrections0;
    // ---- medidas (servidor) ----
    private static final Samples SERVER_FOOT = new Samples();
    private static final Samples SERVER_XFORM = new Samples();
    private static volatile int serverSamples;
    private static volatile int serverLeft;
    private static long accepted0;
    private static long rejected0;
    private static long teleports0;
    // ---- tremor ----
    private static final List<double[]> FRAMES = new ArrayList<>();
    private static final List<double[]> FRAMES_GAME = new ArrayList<>();
    private static final List<double[]> FRAMES_CONTROL = new ArrayList<>();
    private static double[] jitterControl = new double[] {0, 0, 0, 0, 0};
    private static double[] jitterGame = new double[] {0, 0, 0, 0, 0};
    private static volatile boolean sampling;
    private static double[] jitter = new double[] {0, 0, 0, 0, 0};
    private static final List<String> TABLE = new ArrayList<>();
    private static int passedRuns;
    private static int failedRuns;

    private ShipDeckDevtest() {
    }

    // ============================================================================================================ percentis

    /** Amostras de uma medida em blocos. */
    private static final class Samples {
        private double[] data = new double[1024];
        private int n;

        void add(double v) {
            if (this.n == this.data.length) {
                this.data = Arrays.copyOf(this.data, this.n * 2);
            }
            this.data[this.n++] = v;
        }

        void clear() {
            this.n = 0;
        }

        int count() {
            return this.n;
        }

        double max() {
            double m = 0.0;
            for (int i = 0; i < this.n; i++) {
                m = Math.max(m, this.data[i]);
            }
            return m;
        }

        double percentile(double p) {
            if (this.n == 0) {
                return 0.0;
            }
            double[] copy = Arrays.copyOf(this.data, this.n);
            Arrays.sort(copy);
            return copy[Math.min(this.n - 1, (int) Math.ceil(p * this.n) - 1)];
        }
    }

    // ============================================================================================================ servidor: o roteiro da nave

    /** O movimento roteirizado da nave: sobe, paira e desce no ritmo da fase, ricocheteando nos limites da faixa, girando e inclinada. */
    private static final class Script implements Consumer<RocketEntity> {
        private final double speed;
        private final double x;
        private final double z;
        private double y = Y_LOW + 100.0;
        private double direction = 1.0;
        private int tick;

        Script(double speed, double x, double z) {
            this.speed = speed;
            this.x = x;
            this.z = z;
        }

        /** 0 sobe, 1 paira, 2 desce (a fase do tick da corrida). */
        static int phase(int t) {
            int m = t % RUN_TICKS;
            return m < 700 ? 0 : m < 1_000 ? 1 : m < 1_700 ? 2 : 0;
        }

        @Override
        public void accept(RocketEntity rocket) {
            int phase = phase(this.tick);
            double vy = 0.0;
            if (phase != 1) {
                // na subida o sentido começa para cima, na descida para baixo; o ricochete nos limites da faixa inverte
                if (this.tick == 0 || phase != phase(this.tick - 1)) {
                    this.direction = phase == 0 ? 1.0 : -1.0;
                }
                vy = this.direction * this.speed;
                if (this.y + vy > Y_HIGH) {
                    this.direction = -1.0;
                    vy = -this.speed;
                } else if (this.y + vy < Y_LOW) {
                    this.direction = 1.0;
                    vy = this.speed;
                }
            }
            this.y += vy;
            double tilt = Math.toRadians(TILT_DEG * Math.min(1.0, this.tick / 100.0));
            Quaternionf q = new Quaternionf().rotateX((float) tilt).rotateY((float) Math.toRadians(YAW_DEG_PER_TICK * this.tick));
            rocket.setPos(this.x, this.y, this.z);
            rocket.setDeltaMovement(0.0, vy, 0.0);
            rocket.setAttitude(q);
            this.tick++;
        }
    }

    // ============================================================================================================ começo e fim de um cenário

    /** Prepara a corrida: zera as medidas, liga a latência, gera a nave no ar sobre o jogador, dá o roteiro a ela e embarca o jogador no convés. */
    static void begin(Minecraft mc, int shipSpeed, int latency) {
        speed = shipSpeed;
        latencyMs = latency;
        running = false;
        runTick = 0;
        waypoint = 1;
        jumpTicks = 0;
        CLIENT_FOOT.clear();
        SEEN_FOOT.clear();
        SEEN_SLIP.clear();
        RAW_SLIP.clear();
        slipLogs = 0;
        footLogs = 0;
        SERVER_FOOT.clear();
        SERVER_XFORM.clear();
        fellTicks = 0;
        leftTicks = 0;
        airborneTicks = 0;
        groundedTicks = 0;
        standDriftClient = 0.0;
        standDriftSeen = 0.0;
        lastGrounded = false;
        lastSeenLocal = null;
        lastStateLocal = null;
        serverSamples = 0;
        serverLeft = 0;
        FRAMES.clear();
        FRAMES_GAME.clear();
        FRAMES_CONTROL.clear();
        sampling = false;
        jitter = new double[] {0, 0, 0, 0, 0};
        jitterGame = new double[] {0, 0, 0, 0, 0};
        jitterControl = new double[] {0, 0, 0, 0, 0};
        registerListeners();
        NetLatency.setDelayMs(latency);
        ShipDevtest.onServer(mc, server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = (ServerLevel) player.level();
            if (built == null) {
                built = ShipTestRocket.build(level.registryAccess(), ShipTestRocket.preset(4_000));
            }
            var portrait = built.portrait();
            Script script = new Script(shipSpeed, Math.floor(player.getX()) + 0.5, Math.floor(player.getZ()) + 0.5);
            Vec3 center = new Vec3(script.x, script.y, script.z);
            RocketEntity rocket = ShipTestRocket.spawn(level, center, built);
            rocket.setDevtestScript(script);
            rocketUuid = rocket.getUUID();
            DeckState start = new DeckState();
            start.pos.set(CORNERS[0][0], FLOOR_TOP, CORNERS[0][1]);
            start.yaw = 0.0F;
            start.pitch = 10.0F;
            start.onGround = true;
            accepted0 = DeckCrew.ACCEPTED.get();
            rejected0 = DeckCrew.REJECTED.get();
            teleports0 = DeckCrew.TELEPORTS.get();
            boolean boarded = DeckCrew.board(player, rocket, start);
            SpaceHorizons.LOGGER.info(TAG + "cenário: nave {}x{}x{} ({} blocos) a {} b/t, latência {} ms por sentido; embarcou {}", portrait.sizeX, portrait.sizeY,
                    portrait.sizeZ, portrait.blockCount(), shipSpeed, latency, boarded);
            return boarded;
        });
        clientCorrections0 = DeckClient.correctionCount();
    }

    /** A nave apareceu no cliente com os blocos e o jogador entrou no referencial dela (e o ar está limpo para medir). */
    static boolean ready(Minecraft mc) {
        RocketEntity r = DeckClient.rocket();
        if (r == null || !DeckClient.isAboard() || r.portrait().isEmpty()) {
            return false;
        }
        if (!running) {
            running = true;
            // zera o que se acumulou esperando
            lastGrounded = false;
            lastSeenLocal = null;
            lastStateLocal = null;
            CLIENT_FOOT.clear();
            SEEN_FOOT.clear();
            SEEN_SLIP.clear();
            RAW_SLIP.clear();
            clientCorrections0 = DeckClient.correctionCount();
            SpaceHorizons.LOGGER.info(TAG + "pronto: a bordo da nave {} em {}", r.getId(), DeckClient.state().pos);
        }
        return true;
    }

    /** Fim do cenário: mede, confere, registra a linha da tabela e retira a nave (a tripulação sai do convés). */
    static void finish(Minecraft mc) {
        running = false;
        sampling = false;
        String label = String.format(Locale.ROOT, "%d b/t, latência %d ms", speed, latencyMs);
        int clientCorrections = DeckClient.correctionCount() - clientCorrections0;
        long rejected = DeckCrew.REJECTED.get() - rejected0;
        // no autoteste de dois clientes o servidor também teletransporta o observador a cada tick: esses não contam
        long teleports = DeckCrew.TELEPORTS.get() - teleports0 - ShipDeck2Devtest.observerTeleports();
        long accepted = DeckCrew.ACCEPTED.get() - accepted0;
        double seenP99 = SEEN_FOOT.percentile(0.99);
        double seenMax = SEEN_FOOT.max();
        double serverP99 = SERVER_FOOT.percentile(0.99);
        double serverMax = SERVER_FOOT.max();
        double clientP99 = CLIENT_FOOT.percentile(0.99);
        double clientMax = CLIENT_FOOT.max();
        double slipP99 = SEEN_SLIP.percentile(0.99);
        double slipMax = SEEN_SLIP.max();
        double xformMax = SERVER_XFORM.max();
        double driftWorst = Math.max(standDriftClient, standDriftSeen);
        boolean dead = fellTicks > 0 || leftTicks > 0 || seenP99 > 0.1 || serverP99 > 0.1;
        String row = String.format(Locale.ROOT,
                "%-24s | quedas %d (saiu %d) | pé visto p99 %.4f máx %.4f | pé cliente p99 %.4f máx %.4f | pé servidor p99 %.4f máx %.4f | modelo desenhado vs exato p99 %.5f máx %.5f (sem a correção: p99 %.3f máx %.3f) | "
                        + "transformação servidor máx %.5f | deriva parado %.5f/tick (cliente %.5f, visto %.5f) | correções cliente %d, recusas %d, teleportes %d | aceitos %d | "
                        + "tremor %.1f%% no relógio real (controle perfeito: %.1f%%), %.1f%% no do jogo (%d quadros, média %.1f graus/s) | no chão %d, no ar %d ticks",
                label, fellTicks, leftTicks, seenP99, seenMax, clientP99, clientMax, serverP99, serverMax, slipP99, slipMax, RAW_SLIP.percentile(0.99), RAW_SLIP.max(), xformMax, driftWorst, standDriftClient,
                standDriftSeen, clientCorrections, rejected, teleports, accepted, jitter[3] * 100.0, jitterControl[3] * 100.0, jitterGame[3] * 100.0, (int) jitter[4], jitter[2], groundedTicks,
                airborneTicks);
        TABLE.add(row);
        SpaceHorizons.LOGGER.info(TAG + "LINHA {}", row);
        String c = "DECK " + label + ": ";
        ShipDevtest.check(c + "0 quedas pelo piso (nem saída da caixa)", fellTicks == 0 && leftTicks == 0, "quedas " + fellTicks + ", fora do convés " + leftTicks);
        ShipDevtest.check(c + "altura do pé visto no mundo: p99 < 0,05 e máximo < 0,15", seenP99 < 0.05 && seenMax < 0.15 && SEEN_FOOT.count() > 100,
                String.format(Locale.ROOT, "p99 %.5f, máx %.5f, %d quadros", seenP99, seenMax, SEEN_FOOT.count()));
        ShipDevtest.check(c + "altura do pé no servidor: p99 < 0,05 e máximo < 0,15", serverP99 < 0.05 && serverMax < 0.15 && SERVER_FOOT.count() > 100,
                String.format(Locale.ROOT, "p99 %.5f, máx %.5f, %d ticks", serverP99, serverMax, SERVER_FOOT.count()));
        ShipDevtest.check(c + "o modelo do jogador desenhado fica no convés: erro máximo < 0,01 bloco em relação à posição local interpolada", slipMax < 0.01 && SEEN_SLIP.count() > 100,
                String.format(Locale.ROOT, "p99 %.6f, máx %.6f, %d quadros (sem a correção do desenho: p99 %.3f, máx %.3f)", slipP99, slipMax, SEEN_SLIP.count(),
                        RAW_SLIP.percentile(0.99), RAW_SLIP.max()));
        ShipDevtest.check(c + "deriva parado < 0,02 bloco/tick", driftWorst < 0.02, String.format(Locale.ROOT, "cliente %.5f, visto %.5f", standDriftClient, standDriftSeen));
        ShipDevtest.check(c + "0 correções (cliente, recusas do servidor e teleportes)", clientCorrections == 0 && rejected == 0 && teleports == 0,
                "cliente " + clientCorrections + ", recusas " + rejected + ", teleportes " + teleports);
        ShipDevtest.check(c + "o servidor aceitou o estado local do cliente (>= 85% dos ticks do servidor com tripulante)", accepted >= serverSamples * 0.85 && accepted > 500,
                "aceitos " + accepted + " em " + serverSamples + " ticks do servidor");
        ShipDevtest.check(c + "tremor da câmera medido (janela com quadros)", jitter[4] >= 5, String.format(Locale.ROOT, "%.2f%% em %d quadros", jitter[3] * 100.0, (int) jitter[4]));
        if (dead) {
            failedRuns++;
            SpaceHorizons.LOGGER.error(TAG + "MORTE do spike neste cenário ({}): queda {} / saída {} / p99 visto {} / p99 servidor {}", label, fellTicks, leftTicks, seenP99, serverP99);
        } else {
            passedRuns++;
        }
        // retira a nave: a tripulação deixa o convés (teletransporte do protocolo) e o próximo cenário começa limpo
        ShipDevtest.onServer(mc, server -> {
            RocketEntity rocket = findRocket(server);
            if (rocket != null) {
                rocket.setDevtestScript(null);
                rocket.discard();
            }
            return null;
        });
        NetLatency.setDelayMs(0);
    }

    /** A nave do cenário atual no servidor, ou null (para o autoteste de dois clientes, que põe o observador ao lado dela). */
    static RocketEntity currentRocket(MinecraftServer server) {
        return rocketUuid == null ? null : findRocket(server);
    }

    private static RocketEntity findRocket(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(rocketUuid) instanceof RocketEntity r) {
                return r;
            }
        }
        return null;
    }

    static void summary() {
        SpaceHorizons.LOGGER.info(TAG + "TABELA ({} cenários sem morte, {} com):", passedRuns, failedRuns);
        for (String row : TABLE) {
            SpaceHorizons.LOGGER.info(TAG + "TABELA {}", row);
        }
    }

    // ============================================================================================================ o robô

    /** O robô de teste: as teclas do jogador, de verdade. Anda em quadrado no convés; nas janelas de parada solta tudo. Devolve true ao chegar em {@code untilTick}. */
    static boolean walk(Minecraft mc, int untilTick) {
        LocalPlayer p = mc.player;
        if (p == null || !running) {
            return false;
        }
        if (!DeckClient.isAboard()) {
            leftTicks++;
            release(mc);
            return runTick >= untilTick;
        }
        runTick++;
        DeckState s = DeckClient.state();
        boolean standing = isStanding(runTick);
        boolean wasSampling = sampling;
        sampling = runTick >= STAND_AT[2] && runTick < STAND_AT[2] + STAND_TICKS;
        if (wasSampling && !sampling) {
            measureJitter();
        }
        if (standing) {
            release(mc);
        } else {
            double[] goal = CORNERS[waypoint];
            double dx = goal[0] - s.pos.x;
            double dz = goal[1] - s.pos.z;
            if (Math.hypot(dx, dz) < 0.35) {
                waypoint = (waypoint + 1) % CORNERS.length;
                jumpTicks = runTick % 3 == 0 ? 2 : 0;
                goal = CORNERS[waypoint];
                dx = goal[0] - s.pos.x;
                dz = goal[1] - s.pos.z;
            }
            p.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
            p.setXRot(10.0F);
            mc.options.keyUp.setDown(true);
            mc.options.keyJump.setDown(jumpTicks > 0);
            if (jumpTicks > 0) {
                jumpTicks--;
            }
        }
        if (runTick >= untilTick) {
            return true;
        }
        return false;
    }

    private static boolean isStanding(int tick) {
        for (int at : STAND_AT) {
            if (tick >= at && tick < at + STAND_TICKS) {
                return true;
            }
        }
        return false;
    }

    static void release(Minecraft mc) {
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
    }

    /** O tick da corrida em que o robô está. */
    static int runTick() {
        return runTick;
    }

    // ============================================================================================================ medidas

    private static void registerListeners() {
        if (registered) {
            return;
        }
        registered = true;
        // no começo do tick do cliente: o tick anterior terminou (a simulação local, a pose da nave e a posição do jogador no mundo estão todas no fim dele)
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, e -> sampleClientTick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Pre.class, e -> sampleFrame(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ViewportEvent.ComputeCameraAngles.class, e -> {
            if (!sampling) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            RocketEntity rocket = DeckClient.rocket();
            if (mc.level == null || mc.player == null || rocket == null) {
                return;
            }
            // a rotação da câmera deste quadro em DUPLA precisão: o desenho usa quaternions de 32 bits, e o erro deles (1e-7 rad) é invisível, mas ao derivar
            // quadro a quadro (0,003 rad por quadro) viraria +-10% de "tremor" falso. É a mesma conta da câmera: slerp da atitude do tick anterior ao atual
            // pela parcial do quadro, composta com o olhar local do jogador.
            double partial = e.getPartialTick();
            Quaterniond q = rocket.smoothAttitude(rocket.clientTickAt((float) partial));
            if (q == null) {
                var from = rocket.deckFramePrevious().attitude();
                var to = rocket.deckFrame().attitude();
                Quaterniond a = new Quaterniond(from.x(), from.y(), from.z(), from.w()).normalize();
                Quaterniond b = new Quaterniond(to.x(), to.y(), to.z(), to.w()).normalize();
                q = a.slerp(b, partial, new Quaterniond());
            }
            q.mul(new Quaterniond().rotationYXZ(Math.PI - Math.toRadians(mc.player.getYRot()), -Math.toRadians(mc.player.getXRot()), 0.0));
            // o tempo real do quadro (como o cenário ship) e o relógio do jogo (tick + parcial): o do vanilla anda em milissegundos inteiros, e isso sozinho
            // dá +-12% de "tremor" numa medida em tempo real com quadros de ~8 ms
            FRAMES.add(new double[] {System.nanoTime(), q.x, q.y, q.z, q.w});
            // CONTROLE: uma rotação perfeita (20 graus por segundo no relógio do jogo) vista pelo mesmo relógio real e pelos mesmos quadros; mostra o
            // quanto de "tremor" a medida em tempo real tem só pelo relógio de milissegundos inteiros do vanilla, sem nada da nave nem da câmera
            Quaterniond ideal = new Quaterniond().rotationY(Math.toRadians(YAW_DEG_PER_TICK * rocket.clientTickAt((float) partial)));
            FRAMES_CONTROL.add(new double[] {System.nanoTime(), ideal.x, ideal.y, ideal.z, ideal.w});
            FRAMES_GAME.add(new double[] {rocket.clientTickAt((float) partial) * 50_000_000.0, q.x, q.y, q.z, q.w});
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> sampleServer(e.getServer()));
    }

    /** Uma amostra por tick do cliente: o pé na simulação local, a queda, a deriva parado e o pé visto pelo mundo no fim do tick. */
    private static void sampleClientTick(Minecraft mc) {
        if (!running || mc.player == null || mc.level == null || runTick == 0) {
            return;
        }
        RocketEntity rocket = DeckClient.rocket();
        if (rocket == null || !DeckClient.isAboard()) {
            return;
        }
        DeckState s = DeckClient.state();
        if (s.pos.y < FLOOR_TOP - FALL_BELOW) {
            fellTicks++;
        }
        boolean grounded = s.onGround;
        if (grounded) {
            groundedTicks++;
        } else {
            airborneTicks++;
        }
        // o pé na simulação local, só no chão (o pulo é de propósito)
        if (grounded && lastGrounded && runTick > WARM_UP_TICKS) {
            CLIENT_FOOT.add(Math.abs(s.pos.y - FLOOR_TOP));
        }
        // o pé visto pelo mundo: a posição do jogador no mundo (o que o desenho dele usa, no fim do tick) pela pose da nave do mesmo fim de tick
        // a pose que a derivação da posição do jogador no mundo usou neste tick (a suavizada em g + 1, ou a do vanilla sem ela): a conta é
        // mundo -> local por ela, e o local tem de sair igual ao estado simulado
        DeckFrame frame = DeckClient.lastGlueFrame();
        if (frame == null) {
            frame = rocket.deckFrame();
        }
        Vec3 w = mc.player.position();
        Vector3d seen = frame.toLocal(w.x, w.y, w.z);
        boolean warm = runTick > WARM_UP_TICKS;
        if (warm && grounded && lastGrounded && Math.abs(seen.y - FLOOR_TOP) > 0.5 && footLogs++ < 4) {
            DeckFrame glued = DeckClient.lastGlueFrame();
            SpaceHorizons.LOGGER.warn(TAG + "pé visto fora do piso no tick {}: local visto {}; relógio do sampler {} contra o da derivação {}; pose do sampler y={} contra a da derivação y={}; vanilla y={}; "
                    + "a linha do tempo está ativa: {}", runTick, seen, rocket.clientTickAt(1.0F), DeckClient.lastGlueClock() + 1.0, frame.cy(),
                    glued == null ? "?" : glued.cy(), rocket.deckFrame().cy(), rocket.smoothFrame(rocket.clientTickAt(1.0F)) != null);
        }
        if (grounded && lastGrounded && warm) {
            SEEN_FOOT.add(Math.abs(seen.y - FLOOR_TOP));
        }
        // parado de verdade: os 10 primeiros ticks da janela são o atrito amortecendo o último passo (0,216 por tick na caminhada, 0,546 de atrito por tick)
        boolean stand = isStanding(runTick) && isStanding(runTick - SETTLE_TICKS);
        if (stand && lastSeenLocal != null && lastStateLocal != null) {
            standDriftSeen = Math.max(standDriftSeen, seen.distance(lastSeenLocal));
            standDriftClient = Math.max(standDriftClient, s.pos.distance(lastStateLocal));
        }
        lastSeenLocal = seen;
        lastStateLocal = new Vector3d(s.pos);
        lastGrounded = grounded;
    }

    /** Uma amostra por quadro desenhado: a posição do jogador interpolada (o desenho dele) vista pela pose interpolada da nave (o desenho dela). */
    private static void sampleFrame(Minecraft mc) {
        if (!running || mc.player == null || runTick == 0 || !DeckClient.isAboard()) {
            return;
        }
        RocketEntity rocket = DeckClient.rocket();
        if (rocket == null) {
            return;
        }
        if (runTick <= WARM_UP_TICKS) {
            return;
        }
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        // o que o vanilla desenharia sem a correção (a interpolação linear da posição do tick) e o que é desenhado com ela
        Vec3 entity = mc.player.getPosition(pt);
        Vec3 drawn = entity.add(DeckClient.modelCorrection(pt));
        DeckFrame frame = rocket.deckFrameAt(pt);
        Vector3d prev = DeckClient.previousLocal();
        DeckState s = DeckClient.state();
        double t = pt;
        Vector3d expected = new Vector3d(prev.x + (s.pos.x - prev.x) * t, prev.y + (s.pos.y - prev.y) * t, prev.z + (s.pos.z - prev.z) * t);
        double slip = frame.toLocal(drawn.x, drawn.y, drawn.z).distance(expected);
        SEEN_SLIP.add(slip);
        double raw = frame.toLocal(entity.x, entity.y, entity.z).distance(expected);
        RAW_SLIP.add(raw);
        if (slip > 0.05 && slipLogs++ < 3) {
            SpaceHorizons.LOGGER.warn(TAG + "escorregão do modelo desenhado de {} blocos (tick da corrida {}, parcial {})", String.format(Locale.ROOT, "%.4f", slip), runTick, pt);
        }
    }

    /** Uma amostra por tick do servidor: o estado aceito, o pé em relação ao piso e a consistência da posição derivada no mundo. */
    private static void sampleServer(MinecraftServer server) {
        if (!running || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        DeckCrew.Member m = DeckCrew.member(player.getUUID());
        if (m == null) {
            serverLeft++;
            return;
        }
        if (!(player.level() instanceof ServerLevel level) || !(level.getEntity(m.rocketUuid()) instanceof RocketEntity rocket)) {
            return;
        }
        DeckState good = m.state();
        Vector3d seen = rocket.deckFrame().toLocal(player.getX(), player.getY(), player.getZ());
        serverSamples++;
        SERVER_XFORM.add(seen.distance(good.pos));
        if (good.onGround) {
            // o pé no estado aceito, e o pé que o mundo mostraria: a posição do jogador no mundo, trazida à nave pela pose atual
            SERVER_FOOT.add(Math.max(Math.abs(good.pos.y - FLOOR_TOP), Math.abs(seen.y - FLOOR_TOP)));
        }
    }

    /** O tremor da câmera na janela de parada da descida: a velocidade angular quadro a quadro, como no cenário {@code ship}. */
    static void measureJitter() {
        sampling = false;
        List<double[]> copy = new ArrayList<>(FRAMES);
        jitter = ShipPilotDevtest.measureFrames(copy);
        jitterGame = ShipPilotDevtest.measureFrames(new ArrayList<>(FRAMES_GAME));
        jitterControl = ShipPilotDevtest.measureFrames(new ArrayList<>(FRAMES_CONTROL));
        SpaceHorizons.LOGGER.info(TAG + "tremor medido em {} quadros: desvio máximo {}% no relógio real, {}% no relógio do jogo", copy.size(),
                String.format(Locale.ROOT, "%.2f", jitter[3] * 100.0), String.format(Locale.ROOT, "%.2f", jitterGame[3] * 100.0));
    }
}
