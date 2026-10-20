package com.spacehorizons.client.devtest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.RocketEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Só em dev (autoteste {@code jump}): mede o "vai e volta" do foguete em voo rápido na dimensão de espaço. Grava um CSV
 * ({@code run/jump_<rótulo>.csv}) com linhas de quatro tipos, todas com o tempo em ms desde o início (relógio único do processo:
 * o servidor integrado e o cliente estão na mesma JVM):
 *
 * <ul>
 *   <li>{@code F} (cliente, por QUADRO): tick, partialTick, distância câmera-foguete e jogador-foguete (posições renderizadas, com o
 *       partialTick do quadro), se o chunk do jogador / do foguete / da câmera está carregado no {@code ClientLevel}, o estado
 *       "carregado" do cliente ({@code ClientPacketListener.hasClientLoaded}, que congela o jogador quando falso), a tela aberta
 *       (ex.: LevelLoadingScreen), o dado HELD do foguete, se há interpolação ativa, a posição da câmera e o {@code xo}/{@code x} do
 *       foguete e do jogador (o desenho interpola {@code xo} até {@code x}: se os dois passos diferem, o foguete se afasta do jogador);</li>
 *   <li>{@code K} (cliente, por tick): posição do tick do foguete e do jogador, alvo da interpolação, chunks carregados no cliente;</li>
 *   <li>{@code P} (cliente): chegada de pacote (posição do foguete, do jogador, dados, chunk, centro do cache), com o tick;</li>
 *   <li>{@code S} (servidor, por tick): posição do foguete, se o passo foi segurado (HELD), chunks carregados (total e novos) na
 *       dimensão de espaço, se o chunk do jogador já foi enviado a ele, a duração do tick em ms, a dimensão e quantos dos chunks que o
 *       {@code ChunkGate} pede em cada passo à frente ainda não estavam carregados.</li>
 * </ul>
 * A análise (saltos, correlação) é do {@code tools/analyze_jump.py}.
 */
public final class JumpProbe {
    private static final String TAG = "[JUMP] ";
    private static final ConcurrentLinkedQueue<String> ROWS = new ConcurrentLinkedQueue<>();

    private static volatile boolean active;
    private static boolean installed;
    private static long t0;
    private static String label = "x";

    // servidor (só na thread do servidor)
    private static long serverTickStart;
    private static int loadsThisTick;
    private static int newThisTick;
    private static volatile int loadsAccum;
    private static volatile int newAccum;
    /** Posição e velocidade (último passo em que andou) do foguete no tick anterior, para refazer os chunks que o portão de chunks pede. */
    private static double lastX = Double.NaN;
    private static double lastZ = Double.NaN;
    private static double lastVx;
    private static double lastVz;

    private JumpProbe() {
    }

    public static boolean active() {
        return active;
    }

    /** Liga a medição (todas as linhas já gravadas somem). */
    static void start(String name) {
        label = name;
        ROWS.clear();
        t0 = System.nanoTime();
        if (!installed) {
            installed = true;
            NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Post.class, e -> onFrame(Minecraft.getInstance(), e.getPartialTick().getGameTimeDeltaPartialTick(false)));
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> onClientTick(Minecraft.getInstance()));
            NeoForge.EVENT_BUS.addListener(ServerTickEvent.Pre.class, e -> serverTickStart = System.nanoTime());
            NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> onServerTick(e.getServer()));
            NeoForge.EVENT_BUS.addListener(ChunkEvent.Load.class, e -> onChunkLoad(e));
        }
        active = true;
        SpaceHorizons.LOGGER.info("{}medição '{}' ligada", TAG, label);
    }

    /** Desliga e grava o CSV em {@code run/jump_<rótulo>.csv}. */
    static void stop(Minecraft mc) {
        if (!active) {
            return;
        }
        active = false;
        Path file = mc.gameDirectory.toPath().resolve("jump_" + label + ".csv");
        try {
            Files.write(file, ROWS, StandardCharsets.UTF_8);
            SpaceHorizons.LOGGER.info("{}medição '{}' gravada: {} linhas em {}", TAG, label, ROWS.size(), file);
        } catch (IOException e) {
            SpaceHorizons.LOGGER.error(TAG + "não consegui gravar o CSV", e);
        }
    }

    private static String t() {
        return String.format(Locale.ROOT, "%.2f", (System.nanoTime() - t0) / 1.0e6);
    }

    private static void row(String line) {
        ROWS.add(line);
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    // ---------------------------------------------------------------- cliente

    private static RocketEntity rocketOf(Minecraft mc) {
        return mc.player != null && mc.player.getVehicle() instanceof RocketEntity r ? r : null;
    }

    private static void onFrame(Minecraft mc, float partial) {
        if (!active || mc.level == null || mc.player == null) {
            return;
        }
        RocketEntity rocket = rocketOf(mc);
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        Vec3 pp = mc.player.getPosition(partial);
        double camRocket = Double.NaN;
        double playerRocket = Double.NaN;
        int rocketChunk = -1;
        int held = -1;
        int interp = -1;
        String olds = "NaN,NaN,NaN,NaN";
        if (rocket != null) {
            Vec3 rp = rocket.getPosition(partial);
            camRocket = cam.distanceTo(rp);
            playerRocket = pp.distanceTo(rp);
            rocketChunk = mc.level.hasChunk(Mth.floor(rp.x) >> 4, Mth.floor(rp.z) >> 4) ? 1 : 0;
            held = rocket.isHeld() ? 1 : 0;
            interp = rocket.isInterpolating() ? 1 : 0;
            olds = f(rocket.xo) + "," + f(rocket.getX()) + "," + f(mc.player.xo) + "," + f(mc.player.getX());
        }
        int playerChunk = mc.level.hasChunk(Mth.floor(pp.x) >> 4, Mth.floor(pp.z) >> 4) ? 1 : 0;
        int camChunk = mc.level.hasChunk(Mth.floor(cam.x) >> 4, Mth.floor(cam.z) >> 4) ? 1 : 0;
        boolean loaded = mc.player.connection.hasClientLoaded();
        String screen = mc.gui.screen() == null ? "-" : mc.gui.screen().getClass().getSimpleName();
        String overlay = mc.gui.overlay() == null ? "-" : mc.gui.overlay().getClass().getSimpleName();
        row("F," + t() + "," + mc.level.getGameTime() + "," + f(partial) + "," + f(camRocket) + "," + f(playerRocket) + "," + playerChunk + ","
                + rocketChunk + "," + camChunk + "," + (loaded ? 1 : 0) + "," + screen + "," + overlay + "," + held + "," + interp + ","
                + f(cam.x) + "," + f(cam.z) + "," + olds);
    }

    private static void onClientTick(Minecraft mc) {
        if (!active || mc.level == null || mc.player == null) {
            return;
        }
        RocketEntity rocket = rocketOf(mc);
        String target = "NaN,NaN";
        String rp = "NaN,NaN";
        if (rocket != null) {
            var tg = rocket.getInterpolation().target();
            if (tg != null) {
                target = f(tg.position().x) + "," + f(tg.position().z);
            }
            rp = f(rocket.getX()) + "," + f(rocket.getZ());
        }
        row("K," + t() + "," + mc.level.getGameTime() + "," + rp + "," + target + "," + f(mc.player.getX()) + "," + f(mc.player.getZ()) + ","
                + mc.level.getChunkSource().getLoadedChunksCount() + "," + mc.options.renderDistance().get());
    }

    /** Chamado pelo mixin de dev a cada pacote de interesse que chega ao cliente (thread principal do cliente). */
    public static void packet(String kind, int entityId, String extra) {
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String who = "-";
        if (entityId >= 0 && mc.level != null) {
            Entity e = mc.level.getEntity(entityId);
            who = e instanceof RocketEntity ? "rocket" : e == mc.player ? "player" : "other";
        }
        row("P," + t() + "," + (mc.level == null ? -1 : mc.level.getGameTime()) + "," + kind + "," + who + "," + extra);
    }

    // ---------------------------------------------------------------- servidor

    private static void onChunkLoad(ChunkEvent.Load e) {
        if (!active || e.getLevel().isClientSide()) {
            return;
        }
        if (e.getLevel() instanceof ServerLevel level && com.spacehorizons.space.SpaceDimension.is(level)) {
            loadsThisTick++;
            if (e.isNewChunk()) {
                newThisTick++;
            }
        }
    }

    private static void onServerTick(MinecraftServer server) {
        if (!active || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        double ms = (System.nanoTime() - serverTickStart) / 1.0e6;
        int loads = loadsThisTick;
        int fresh = newThisTick;
        loadsThisTick = 0;
        newThisTick = 0;
        loadsAccum += loads;
        newAccum += fresh;
        ServerLevel level = player.level();
        int pcx = Mth.floor(player.getX()) >> 4;
        int pcz = Mth.floor(player.getZ()) >> 4;
        boolean tracked = level.getChunkSource().chunkMap.isChunkTracked(player, pcx, pcz);
        String rocket = "NaN,NaN,NaN,-1";
        String miss = "-";
        if (player.getVehicle() instanceof RocketEntity r) {
            // no espaço o foguete fica estacionado: a posição que vale (a do voo) é a real
            net.minecraft.world.phys.Vec3 at = r.flight().spacePosition() != null && r.isVirtual() ? r.flight().spacePosition() : r.position();
            if (lastX == lastX && Math.hypot(at.x - lastX, at.z - lastZ) > 0.5) {
                lastVx = at.x - lastX;
                lastVz = at.z - lastZ;
            }
            lastX = at.x;
            lastZ = at.z;
            miss = r.isVirtual() ? "-" : missingAhead(level, r);
            rocket = f(at.x) + "," + f(at.y) + "," + f(at.z) + "," + (r.isHeld() ? 1 : 0);
        }
        row("S," + t() + "," + level.getGameTime() + "," + rocket + "," + f(player.getX()) + "," + f(player.getZ()) + "," + loads + "," + fresh + ","
                + level.getChunkSource().getLoadedChunksCount() + "," + (tracked ? 1 : 0) + "," + f(ms) + "," + level.dimension().identifier().getPath() + "," + miss);
    }

    /**
     * Refaz o que o {@code ChunkGate} pede ao foguete (mesma função pura, com a velocidade do último passo em que andou) e conta, por passo
     * de segurar (ticks à frente), quantos dos chunks pedidos NÃO estão carregados agora: "passo:faltando/pedidos" separados por barra.
     */
    private static String missingAhead(ServerLevel level, RocketEntity r) {
        double half = Math.max(r.portrait().sizeX, r.portrait().sizeZ) / 2.0 + 2.0;
        java.util.TreeMap<Integer, int[]> bySteps = new java.util.TreeMap<>();
        double circumference = com.spacehorizons.planet.Planets.forLevel(level).map(p -> (double) p.circumferenceBlocks()).orElse(0.0);
        for (var cell : com.spacehorizons.rocket.ChunkGate.corridor(r.getX(), r.getZ(), lastVx, lastVz, circumference, half, 32)) {
            // baldes de ticks à frente (0, 1, 2, 4, 8, 16, 32): o que o analisador espera
            int bucket = 0;
            while (bucket < cell.ticksAhead()) {
                bucket = bucket == 0 ? 1 : bucket * 2;
            }
            int[] c = bySteps.computeIfAbsent(bucket, k -> new int[2]);
            c[1]++;
            if (!level.hasChunk(cell.chunkX(), cell.chunkZ())) {
                c[0]++;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (var e : bySteps.entrySet()) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(e.getKey()).append(':').append(e.getValue()[0]).append('x').append(e.getValue()[1]);
        }
        return sb.toString();
    }
}
