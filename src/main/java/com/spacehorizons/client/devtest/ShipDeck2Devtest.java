package com.spacehorizons.client.devtest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.joml.Vector3d;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.ship.DeckObserver;
import com.spacehorizons.network.NetLatency;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.ship.deck.DeckCrew;
import com.spacehorizons.ship.deck.DeckFrame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Autoteste de DOIS CLIENTES do convés andável (fase 2e, frente 1): {@code ./gradlew shipdeck2} sobe duas instâncias do cliente.
 *
 * <ul>
 *   <li><b>A (anfitrião, {@code -PshOnly=shipdeck2})</b> cria o mundo, abre para a LAN numa porta fixa e roda o roteiro do {@code shipdeck} (a nave
 *       roteirizada de ~4.000 blocos a 1, 10 e 100 b/t, mais 100 b/t com 100 ms de latência) com o robô andando no convés. O servidor integrado de A
 *       grava, a cada tick, a posição EXATA de A no mundo (a verdade) e teletransporta o observador B, a cada tick, para um ponto fixo no referencial da
 *       nave (a posição só serve para B ver a nave e o jogador).</li>
 *   <li><b>B (observador, {@code shipdeck2b})</b> entra pela LAN, fica num ponto fixo da nave (a 30 blocos do casco, depois dentro dele, olhando o
 *       convés; o olho é fixo no referencial da nave, não no mundo) e mede, a cada quadro, onde desenha A: {@link DeckObserver#modelCorrection} sobre a
 *       posição do vanilla. Ao fim de cada cenário compara com a verdade que A gravou, interpolada no mesmo tempo do servidor.</li>
 * </ul>
 * Os dois conversam por arquivos na pasta {@code run-test/shipdeck2-shared} (estado do cenário, verdade, confirmação). Critério: p99 do erro da
 * posição vista < 0,1 bloco e máximo < 0,3; o modelo de A não some (quadros desenhados) e fica dentro da caixa da nave. As linhas
 * {@code [AUTOTEST] SHIPDECK2 CHECK ...} são lidas pela tarefa {@code shipdeck2} do Gradle.
 */
final class ShipDeck2Devtest {
    private static final String TAG = "[AUTOTEST] SHIPDECK2 ";
    static final int PORT = Integer.getInteger("spacehorizons.autotest.port", 25_599);
    /** Do tick da corrida em diante o observador entra no casco; as medidas começam depois de a linha do tempo da nave encher. */
    private static final int INSIDE_FROM = 1_000;
    private static final int MEASURE_FROM = 60;
    /** O olho do observador e o ponto que ele olha, no referencial da nave (retrato 26x28x26): fora, a 30 blocos do casco; dentro, num canto do convés. */
    private static final double[] OUTSIDE_EYE = {13.0, 14.0, -30.0};
    private static final double[] OUTSIDE_LOOK = {13.0, 8.0, 13.0};
    private static final double[] INSIDE_EYE = {3.0, 7.0, 3.0};
    private static final double[] INSIDE_LOOK = {13.0, 2.0, 13.0};
    private static final double BOX_MARGIN = 1.0;

    // ---- anfitrião (A) ----
    private static volatile boolean following;
    private static volatile int hostRunTick;
    private static final AtomicInteger OBSERVER_TELEPORTS = new AtomicInteger();
    private static final List<double[]> TRUTH = new ArrayList<>();
    private static boolean hostRegistered;
    private static int lastWrittenTick = -100;

    // ---- observador (B) ----
    private static boolean observerRegistered;
    private static int cfgIndex = -1;
    private static int cfgSpeed;
    private static int cfgLatency;
    private static int cfgRunTick;
    private static String cfgPhase = "";
    private static int handledDone = -1;
    private static boolean finished;
    private static int shotsTaken;
    private static final List<double[]> SEEN = new ArrayList<>();
    private static int framesWithA;
    private static int framesObserved;
    private static int framesInsideBox;
    private static int framesOutsideBox;
    private static int drawnAtStart;
    private static int passed;
    private static int failed;
    private static final List<String> TABLE = new ArrayList<>();

    private ShipDeck2Devtest() {
    }

    static int observerTeleports() {
        return OBSERVER_TELEPORTS.get();
    }

    private static Path shared(Minecraft mc) {
        Path dir = mc.gameDirectory.toPath().toAbsolutePath().normalize().resolveSibling("shipdeck2-shared");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("sem a pasta compartilhada " + dir, e);
        }
        return dir;
    }

    private static void write(Path file, String text) {
        // no Windows o leitor do outro cliente pode estar com o arquivo aberto no instante da troca: tenta de novo
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                Files.writeString(tmp, text, StandardCharsets.UTF_8);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (IOException e) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        SpaceHorizons.LOGGER.warn(TAG + "não consegui gravar {}", file);
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        SpaceHorizons.LOGGER.info(TAG + "CHECK {} {}: {}", ok ? "PASSOU" : "FALHOU", name, detail);
    }

    private static double percentile(double[] sorted, double p) {
        return sorted.length == 0 ? 0.0 : sorted[Math.min(sorted.length - 1, (int) Math.ceil(p * sorted.length) - 1)];
    }

    private static Vector3d at(DeckFrame frame, double[] local) {
        return frame.toWorld(local[0], local[1], local[2]);
    }

    // ============================================================================================================ anfitrião (A)

    /** Abre o mundo para a LAN na porta fixa e avisa o observador. */
    static void hostOpen(Minecraft mc) {
        Path dir = shared(mc);
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            // o cliente de dev do observador não tem sessão da Mojang: sem autenticação o login pela LAN (só em localhost, só no autoteste) passa
            server.setUsesAuthentication(false);
        }
        boolean ok = server != null && server.publishServer(MinecraftServer.MultiplayerScope.LAN, true, PORT);
        SpaceHorizons.LOGGER.info(TAG + "mundo aberto para a LAN na porta {}: {}", PORT, ok);
        if (!hostRegistered) {
            hostRegistered = true;
            NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> serverTick(e.getServer()));
        }
        write(dir.resolve("lan.txt"), PORT + "\n");
        write(dir.resolve("state.txt"), "-1 0 0 0 wait\n");
    }

    /** O observador entrou no servidor: dois jogadores (e ele pode voar parado onde o servidor o puser). */
    static boolean observerJoined(Minecraft mc) {
        return ShipDevtest.onServer(mc, server -> {
            if (server.getPlayerList().getPlayers().size() < 2) {
                return false;
            }
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p != server.getPlayerList().getPlayers().get(0)) {
                    p.getAbilities().mayfly = true;
                    p.getAbilities().flying = true;
                    p.onUpdateAbilities();
                }
            }
            return true;
        });
    }

    /** Começa um cenário: zera a verdade e o contador de teletransportes do observador, gera a nave e embarca A, e põe o observador a seguir a nave. */
    static void hostBegin(Minecraft mc, int index, int speed, int latency) {
        Path dir = shared(mc);
        synchronized (TRUTH) {
            TRUTH.clear();
        }
        OBSERVER_TELEPORTS.set(0);
        hostRunTick = 0;
        lastWrittenTick = -100;
        write(dir.resolve("state.txt"), String.format(Locale.ROOT, "%d %d %d 0 prepare%n", index, speed, latency));
        ShipDeckDevtest.begin(mc, speed, latency);
        following = true;
        cfgIndex = index;
        cfgSpeed = speed;
        cfgLatency = latency;
    }

    /** A cada tick do cliente de A durante a corrida: publica o tick da corrida (a cada 10) para o observador. */
    static void hostTick(Minecraft mc, int runTick) {
        hostRunTick = runTick;
        if (runTick - lastWrittenTick >= 10) {
            lastWrittenTick = runTick;
            write(shared(mc).resolve("state.txt"), String.format(Locale.ROOT, "%d %d %d %d run%n", cfgIndex, cfgSpeed, cfgLatency, runTick));
        }
    }

    /** Antes de A encerrar o cenário: o observador para de ser teletransportado (rodando no tick do servidor, depois dele nada mais conta). */
    static void hostStopFollow(Minecraft mc) {
        ShipDevtest.onServer(mc, server -> {
            following = false;
            return null;
        });
    }

    /** Fim do cenário: grava a verdade e avisa o observador para medir. */
    static void hostEnd(Minecraft mc, int index) {
        Path dir = shared(mc);
        StringBuilder sb = new StringBuilder();
        int rows;
        synchronized (TRUTH) {
            rows = TRUTH.size();
            for (double[] t : TRUTH) {
                sb.append((long) t[0]).append(' ').append(t[1]).append(' ').append(t[2]).append(' ').append(t[3]).append(' ').append(t[4]).append(' ').append(t[5]).append(' ').append(t[6]).append('\n');
            }
        }
        write(dir.resolve("truth_" + index + ".txt"), sb.toString());
        write(dir.resolve("state.txt"), String.format(Locale.ROOT, "%d %d %d %d cfgdone%n", index, cfgSpeed, cfgLatency, hostRunTick));
        SpaceHorizons.LOGGER.info(TAG + "cenário {} encerrado em A: {} posições exatas gravadas, {} teletransportes do observador", index, rows,
                OBSERVER_TELEPORTS.get());
    }

    /** O observador mediu o cenário {@code index} e confirmou. */
    static boolean acked(Minecraft mc, int index) {
        return Files.isRegularFile(shared(mc).resolve("ack_" + index + ".txt"));
    }

    /** Fim do roteiro: avisa o observador para fechar. */
    static void hostFinish(Minecraft mc) {
        write(shared(mc).resolve("state.txt"), "-1 0 0 0 finished\n");
    }

    /** No tick do servidor (depois da nave e do convés): grava a posição exata de A no mundo e leva o observador ao ponto dele. */
    private static void serverTick(MinecraftServer server) {
        if (!following || server.getPlayerList().getPlayers().size() < 2) {
            return;
        }
        ServerPlayer a = server.getPlayerList().getPlayers().get(0);
        ServerPlayer b = null;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p != a) {
                b = p;
            }
        }
        RocketEntity rocket = ShipDeckDevtest.currentRocket(server);
        if (b == null || rocket == null || !(a.level() instanceof ServerLevel level)) {
            return;
        }
        if (DeckCrew.member(a.getUUID()) != null) {
            synchronized (TRUTH) {
                TRUTH.add(new double[] {level.getGameTime(), a.getX(), a.getY(), a.getZ(), rocket.getX(), rocket.getY(), rocket.getZ()});
            }
        }
        double[] eye = hostRunTick >= INSIDE_FROM ? INSIDE_EYE : OUTSIDE_EYE;
        Vector3d p = at(rocket.deckFrame(), eye);
        if (b.level() == level) {
            b.connection.teleport(p.x, p.y, p.z, 0.0F, 0.0F);
            OBSERVER_TELEPORTS.incrementAndGet();
        }
    }

    // ============================================================================================================ observador (B)

    /** O anfitrião já abriu o mundo? */
    static boolean hostReady(Minecraft mc) {
        return Files.isRegularFile(shared(mc).resolve("lan.txt"));
    }

    /** Entra no mundo do anfitrião pela LAN. */
    static void join(Minecraft mc) {
        String address = "localhost:" + PORT;
        SpaceHorizons.LOGGER.info(TAG + "observador entrando em {}", address);
        ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address), new ServerData("shipdeck2", address, ServerData.Type.OTHER), false,
                null);
    }

    /** Começa a observar: liga o olho fixo e a medida por quadro. */
    static void observerStart(Minecraft mc) {
        if (!observerRegistered) {
            observerRegistered = true;
            NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Pre.class, e -> onFrame(Minecraft.getInstance()));
        }
        DeckObserver.eyeOverride = pt -> {
            RocketEntity rocket = findRocket(Minecraft.getInstance());
            if (rocket == null) {
                return null;
            }
            Vector3d eye = at(rocket.deckFrameAt(pt), insideNow() ? INSIDE_EYE : OUTSIDE_EYE);
            return new Vec3(eye.x, eye.y, eye.z);
        };
        SpaceHorizons.LOGGER.info(TAG + "observador pronto (jogador {})", mc.player == null ? "?" : mc.player.getName().getString());
    }

    private static boolean insideNow() {
        return cfgRunTick >= INSIDE_FROM;
    }

    private static RocketEntity findRocket(Minecraft mc) {
        if (mc.level == null) {
            return null;
        }
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof RocketEntity r && !r.isRemoved() && !r.portrait().isEmpty()) {
                return r;
            }
        }
        return null;
    }

    private static RemotePlayer findOther(Minecraft mc) {
        if (mc.level == null) {
            return null;
        }
        for (var p : mc.level.players()) {
            if (p instanceof RemotePlayer r) {
                return r;
            }
        }
        return null;
    }

    /** A cada quadro: aponta o olhar para o convés e, na janela de medida, mede onde A é desenhado. */
    private static void onFrame(Minecraft mc) {
        if (mc.player == null || mc.level == null || cfgIndex < 0 || !("run".equals(cfgPhase) || "prepare".equals(cfgPhase))) {
            return;
        }
        RocketEntity rocket = findRocket(mc);
        if (rocket == null) {
            return;
        }
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        DeckFrame frame = rocket.deckFrameAt(pt);
        boolean inside = insideNow();
        Vector3d eye = at(frame, inside ? INSIDE_EYE : OUTSIDE_EYE);
        Vector3d look = at(frame, inside ? INSIDE_LOOK : OUTSIDE_LOOK);
        double dx = look.x - eye.x;
        double dy = look.y - eye.y;
        double dz = look.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
        if (!"run".equals(cfgPhase) || cfgRunTick < MEASURE_FROM) {
            return;
        }
        RemotePlayer a = findOther(mc);
        if (a == null) {
            return;
        }
        framesWithA++;
        if (!DeckObserver.isObserved(a)) {
            return;
        }
        framesObserved++;
        Vec3 vanilla = a.getPosition(pt);
        Vec3 drawn = vanilla.add(DeckObserver.modelCorrection(a, pt));
        double serverTime = rocket.smoothServerTimeAt(rocket.clientTickAt(pt));
        SEEN.add(new double[] {serverTime, drawn.x, drawn.y, drawn.z, vanilla.x, vanilla.y, vanilla.z, frame.cx(), frame.cy(), frame.cz(), rocket.smoothStarved(rocket.clientTickAt(pt)) ? 1.0 : 0.0});
        var portrait = rocket.portrait();
        Vector3d local = frame.toLocal(drawn.x, drawn.y, drawn.z);
        boolean in = local.x >= -BOX_MARGIN && local.y >= -BOX_MARGIN && local.z >= -BOX_MARGIN && local.x <= portrait.sizeX + BOX_MARGIN
                && local.y <= portrait.sizeY + BOX_MARGIN && local.z <= portrait.sizeZ + BOX_MARGIN;
        if (in) {
            framesInsideBox++;
        } else {
            framesOutsideBox++;
        }
    }

    /**
     * Um tick do cliente do observador: lê o estado que A publicou, tira os prints nos instantes fixos e, no fim do cenário, mede. Devolve true quando A
     * avisa que acabou.
     */
    static boolean observerTick(Minecraft mc, Consumer<String> shot) {
        Path dir = shared(mc);
        String line;
        try {
            line = Files.readString(dir.resolve("state.txt"), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return false;
        }
        String[] f = line.split(" ");
        if (f.length < 5) {
            return false;
        }
        int index = Integer.parseInt(f[0]);
        String phase = f[4];
        if ("finished".equals(phase)) {
            if (!finished) {
                finished = true;
                summary();
            }
            return true;
        }
        if (index >= 0 && index != cfgIndex && ("prepare".equals(phase) || "run".equals(phase))) {
            // cenário novo: zera as medidas e copia a latência de A
            cfgIndex = index;
            cfgSpeed = Integer.parseInt(f[1]);
            cfgLatency = Integer.parseInt(f[2]);
            SEEN.clear();
            framesWithA = 0;
            framesObserved = 0;
            framesInsideBox = 0;
            framesOutsideBox = 0;
            shotsTaken = 0;
            drawnAtStart = DeckObserver.drawnFrames();
            NetLatency.setDelayMs(cfgLatency);
            SpaceHorizons.LOGGER.info(TAG + "observador: cenário {} ({} b/t, latência {} ms)", index, cfgSpeed, cfgLatency);
        }
        if (index == cfgIndex) {
            cfgPhase = phase;
            cfgRunTick = Integer.parseInt(f[3]);
        }
        if ("run".equals(phase) && index == cfgIndex) {
            int[] at = {300, 650, 1_100, 1_500};
            if (shotsTaken < at.length && cfgRunTick >= at[shotsTaken]) {
                String where = cfgRunTick >= INSIDE_FROM ? "dentro" : "fora";
                shot.accept(String.format(Locale.ROOT, "sdk2_v%d_l%d_%s_t%d", cfgSpeed, cfgLatency, where, at[shotsTaken]));
                shotsTaken++;
            }
        }
        if ("cfgdone".equals(phase) && index == cfgIndex && handledDone != index) {
            handledDone = index;
            measure(mc, dir, index);
            NetLatency.setDelayMs(0);
            write(dir.resolve("ack_" + index + ".txt"), "ok\n");
        }
        return false;
    }

    /** Compara as posições desenhadas com a verdade que A gravou e registra a linha da tabela e as verificações do cenário. */
    private static void measure(Minecraft mc, Path dir, int index) {
        double[][] truth;
        try {
            List<String> lines = Files.readAllLines(dir.resolve("truth_" + index + ".txt"), StandardCharsets.UTF_8);
            truth = new double[lines.size()][];
            for (int i = 0; i < truth.length; i++) {
                String[] p = lines.get(i).split(" ");
                truth[i] = new double[] {Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]), Double.parseDouble(p[4]), Double.parseDouble(p[5]), Double.parseDouble(p[6])};
            }
        } catch (IOException | RuntimeException e) {
            check("DECK2 " + label() + ": a verdade de A foi lida", false, e.toString());
            return;
        }
        // a verdade por tick do servidor (pode ter buracos): busca pelo carimbo, não pela posição na lista
        java.util.Map<Long, double[]> byStamp = new java.util.HashMap<>();
        for (double[] t : truth) {
            byStamp.put((long) t[0], t);
        }
        double[] errors = new double[SEEN.size()];
        double[] vanillaErrors = new double[SEEN.size()];
        double[] poseErrors = new double[SEEN.size()];
        int n = 0;
        int starved = 0;
        for (double[] s : SEEN) {
            double ts = s[0];
            if (Double.isNaN(ts)) {
                continue;
            }
            if (s[10] > 0.0) {
                // o tempo mostrado passou da pose mais nova (o cliente de B atrasou os pacotes): sem dado novo, não há o que comparar
                starved++;
                continue;
            }
            long k = (long) Math.floor(ts);
            double[] t0 = byStamp.get(k);
            double[] t1 = byStamp.get(k + 1);
            if (t0 == null || t1 == null) {
                continue;
            }
            double t = ts - k;
            double x = t0[1] + (t1[1] - t0[1]) * t;
            double y = t0[2] + (t1[2] - t0[2]) * t;
            double z = t0[3] + (t1[3] - t0[3]) * t;
            double rx = t0[4] + (t1[4] - t0[4]) * t;
            double ry = t0[5] + (t1[5] - t0[5]) * t;
            double rz = t0[6] + (t1[6] - t0[6]) * t;
            errors[n] = Math.sqrt((s[1] - x) * (s[1] - x) + (s[2] - y) * (s[2] - y) + (s[3] - z) * (s[3] - z));
            vanillaErrors[n] = Math.sqrt((s[4] - x) * (s[4] - x) + (s[5] - y) * (s[5] - y) + (s[6] - z) * (s[6] - z));
            poseErrors[n] = Math.sqrt((s[7] - rx) * (s[7] - rx) + (s[8] - ry) * (s[8] - ry) + (s[9] - rz) * (s[9] - rz));
            n++;
            if (n == SEEN.size() / 2) {
                SpaceHorizons.LOGGER.info(TAG + "amostra do meio: ts {}, A desenhado ({}, {}, {}) contra a exata ({}, {}, {}); pose da nave vista ({}, {}, {}) contra a do servidor ({}, {}, {})", ts,
                        f(s[1]), f(s[2]), f(s[3]), f(x), f(y), f(z), f(s[7]), f(s[8]), f(s[9]), f(rx), f(ry), f(rz));
            }
        }
        double[] pe = Arrays.copyOf(poseErrors, n);
        Arrays.sort(pe);
        SpaceHorizons.LOGGER.info(TAG + "pose da nave vista contra a do servidor no mesmo tick: p99 {}, máx {}", f(percentile(pe, 0.99)), f(n == 0 ? 0.0 : pe[n - 1]));
        double[] e = Arrays.copyOf(errors, n);
        double[] v = Arrays.copyOf(vanillaErrors, n);
        Arrays.sort(e);
        Arrays.sort(v);
        double p99 = percentile(e, 0.99);
        double max = n == 0 ? 0.0 : e[n - 1];
        double vanillaP99 = percentile(v, 0.99);
        double vanillaMax = n == 0 ? 0.0 : v[n - 1];
        int drawn = DeckObserver.drawnFrames() - drawnAtStart;
        String c = "DECK2 " + label() + ": ";
        String row = String.format(Locale.ROOT, "%-24s | quadros com A %d, A a bordo visto %d, modelo desenhado %d (dentro da caixa %d, fora %d) | posição vista contra a exata: %d quadros (fora %d sem dado novo), p99 %.5f máx %.5f "
                + "(sem a correção do desenho: p99 %.3f máx %.3f)", label(), framesWithA, framesObserved, drawn, framesInsideBox, framesOutsideBox, n, starved, p99, max, vanillaP99, vanillaMax);
        TABLE.add(row);
        SpaceHorizons.LOGGER.info(TAG + "LINHA {}", row);
        check(c + "posição vista de A: p99 < 0,1 e máximo < 0,3 bloco (contra a pose da nave x estado local exato)", n >= 500 && p99 < 0.1 && max < 0.3,
                String.format(Locale.ROOT, "p99 %.5f, máx %.5f, %d quadros", p99, max, n));
        check(c + "a linha do tempo da nave teve dado novo em >= 95% dos quadros (B sob carga atrasa pacotes)", framesObserved > 500 && starved <= framesObserved * 0.05,
                starved + " quadros sem dado novo de " + framesObserved);
        check(c + "A foi visto a bordo em >= 99% dos quadros (sem voltar ao vanilla)", framesWithA > 500 && framesObserved >= framesWithA * 0.99,
                framesObserved + " de " + framesWithA + " quadros");
        check(c + "o modelo de A não sumiu (culling): desenhado em >= 99% dos quadros", framesObserved > 500 && drawn >= framesObserved * 0.99,
                drawn + " desenhos em " + framesObserved + " quadros");
        check(c + "o modelo de A fica dentro da caixa da nave em todos os quadros", framesObserved > 500 && framesOutsideBox == 0,
                "dentro " + framesInsideBox + ", fora " + framesOutsideBox);
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String label() {
        return String.format(Locale.ROOT, "%d b/t, latência %d ms", cfgSpeed, cfgLatency);
    }

    private static void summary() {
        SpaceHorizons.LOGGER.info(TAG + "TABELA ({} verificações passaram, {} falharam):", passed, failed);
        for (String row : TABLE) {
            SpaceHorizons.LOGGER.info(TAG + "TABELA {}", row);
        }
        SpaceHorizons.LOGGER.info(TAG + "CHECK RESUMO: {} passaram, {} falharam", passed, failed);
    }
}
