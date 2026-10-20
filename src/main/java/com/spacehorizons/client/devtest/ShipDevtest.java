package com.spacehorizons.client.devtest;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.ship.ShipSkyClient;
import com.spacehorizons.client.space.SkyObserver;
import com.spacehorizons.ship.ShipBuilder;
import com.spacehorizons.ship.ShipChunkTickets;
import com.spacehorizons.ship.ShipRecord;
import com.spacehorizons.ship.ShipRegistry;
import com.spacehorizons.ship.ShipYard;
import com.spacehorizons.space.SolarSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.joml.Quaterniond;

/**
 * Só em dev (autoteste, cenário {@code shipyard}, spike S2 das naves): leituras do servidor integrado (chunks, tickets, tempo de
 * tick, luz, blocos e inventários das naves), as verificações com PASSOU/FALHOU no log e o ciclo de fechar e reabrir o mundo.
 * O roteiro em si (passos, prints) fica em {@code AutoTest.shipYardScenario}.
 */
final class ShipDevtest {
    private static final String TAG = "[AUTOTEST] ";
    private static final Map<Integer, ShipBuilder.Layout> LAYOUTS = new HashMap<>();
    private static int passed;
    private static int failed;

    private ShipDevtest() {
    }

    // ---- verificações ----

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        SpaceHorizons.LOGGER.info(TAG + "SHIPCHECK {} {}: {}", ok ? "PASSOU" : "FALHOU", name, detail);
    }

    static void summary() {
        SpaceHorizons.LOGGER.info(TAG + "SHIPCHECK RESUMO: {} passaram, {} falharam", passed, failed);
    }

    // ---- acesso ao servidor integrado ----

    static <T> T onServer(Minecraft mc, Function<MinecraftServer, T> body) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            throw new IllegalStateException("sem servidor integrado");
        }
        try {
            return server.submit(() -> body.apply(server)).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao consultar o servidor", e);
        }
    }

    /** O layout da nave de teste {@code id} (posições dos elementos no estaleiro). */
    static ShipBuilder.Layout layout(Minecraft mc, int id) {
        return LAYOUTS.computeIfAbsent(id, k -> onServer(mc, server -> {
            ShipRecord ship = ShipRegistry.get(server).byId(k);
            if (ship == null) {
                throw new IllegalStateException("nave " + k + " não existe");
            }
            return ShipBuilder.layout(ship.box());
        }));
    }

    static ServerLevel yard(MinecraftServer server) {
        return ShipYard.level(server);
    }

    // ---- estado do mundo ----

    static int shipCount(Minecraft mc) {
        return onServer(mc, server -> ShipRegistry.get(server).all().size());
    }

    static boolean hasYard(Minecraft mc) {
        return onServer(mc, server -> ShipYard.level(server) != null);
    }

    /** Id da nave cuja célula contém o jogador (0 se nenhuma). */
    static int shipAroundPlayer(Minecraft mc) {
        return onServer(mc, server -> {
            var players = server.getPlayerList().getPlayers();
            if (players.isEmpty() || !ShipYard.is(players.get(0).level())) {
                return 0;
            }
            ShipRecord ship = ShipRegistry.get(server).atPosition(players.get(0).getX(), players.get(0).getZ());
            return ship == null ? 0 : ship.id();
        });
    }

    /** Uma linha com o estado das naves e do servidor: tickets, chunks carregados, tempo de tick, memória. */
    static String stats(Minecraft mc, boolean gc) {
        if (gc) {
            System.gc();
            System.gc();
        }
        Runtime rt = Runtime.getRuntime();
        double heapMb = (rt.totalMemory() - rt.freeMemory()) / 1048576.0;
        String server = onServer(mc, srv -> {
            StringBuilder sb = new StringBuilder();
            ServerLevel yard = ShipYard.level(srv);
            long[] all = srv.getTickTimesNanos().clone();
            sb.append(String.format(Locale.ROOT, "tick(servidor, 100 ticks)=%.2f ms (máx %.2f)", avg(all), max(all)));
            long[] yt = srv.getTickTime(ShipYard.DIMENSION);
            if (yt != null) {
                sb.append(String.format(Locale.ROOT, " | tick(estaleiro)=%.3f ms (máx %.3f)", avg(yt), max(yt)));
            }
            long[] ot = srv.getTickTime(Level.OVERWORLD);
            if (ot != null) {
                sb.append(String.format(Locale.ROOT, " | tick(overworld)=%.2f ms", avg(ot)));
            }
            sb.append(" | chunks: ");
            for (ServerLevel level : srv.getAllLevels()) {
                sb.append(level.dimension().identifier().getPath()).append('=').append(level.getChunkSource().getLoadedChunksCount()).append(' ');
            }
            if (yard != null) {
                sb.append("| seguradas=").append(ShipChunkTickets.held());
                for (ShipRecord ship : ShipRegistry.get(srv).all()) {
                    ShipYard.Box box = ship.box();
                    int loaded = 0;
                    int blockTicking = 0;
                    int total = 0;
                    for (int cx = box.chunkMinX(); cx <= box.chunkMaxX(); cx++) {
                        for (int cz = box.chunkMinZ(); cz <= box.chunkMaxZ(); cz++) {
                            total++;
                            if (yard.getChunkSource().getChunkNow(cx, cz) != null) {
                                loaded++;
                            }
                            if (yard.shouldTickBlocksAt(new ChunkPos(cx, cz).pack())) {
                                blockTicking++;
                            }
                        }
                    }
                    sb.append(String.format(Locale.ROOT, " | nave %d: colunas carregadas %d/%d, com tick de bloco %d", ship.id(), loaded, total, blockTicking));
                }
            }
            return sb.toString();
        });
        return String.format(Locale.ROOT, "%s | heap=%.0f MB%s", server, heapMb, gc ? " (após GC)" : "");
    }

    private static double avg(long[] nanos) {
        double sum = 0.0;
        for (long n : nanos) {
            sum += n;
        }
        return sum / nanos.length / 1.0e6;
    }

    private static double max(long[] nanos) {
        long m = 0L;
        for (long n : nanos) {
            m = Math.max(m, n);
        }
        return m / 1.0e6;
    }

    static void logStats(Minecraft mc, String label, boolean gc) {
        SpaceHorizons.LOGGER.info(TAG + "SHIPSTATS {}: {}", label, stats(mc, gc));
        for (int id = 1; id <= shipCount(mc); id++) {
            logIntegrity(mc, id, label);
        }
    }

    /**
     * Diagnóstico: quantos blocos não-ar a caixa da nave tem nos chunks carregados e se os marcadores (vidro do piso, baú, fornalha,
     * lâmpada, alavanca, porta) estão no lugar. Não carrega chunks.
     */
    static void logIntegrity(Minecraft mc, int shipId, String label) {
        ShipBuilder.Layout l = layout(mc, shipId);
        String s = onServer(mc, server -> {
            ServerLevel yard = ShipYard.level(server);
            ShipYard.Box box = l.box();
            long solid = 0;
            int missingColumns = 0;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int cx = box.chunkMinX(); cx <= box.chunkMaxX(); cx++) {
                for (int cz = box.chunkMinZ(); cz <= box.chunkMaxZ(); cz++) {
                    net.minecraft.world.level.chunk.LevelChunk chunk = yard.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) {
                        missingColumns++;
                        continue;
                    }
                    for (int x = cx << 4; x < (cx << 4) + 16; x++) {
                        for (int z = cz << 4; z < (cz << 4) + 16; z++) {
                            for (int y = box.minY(); y <= box.maxY(); y++) {
                                if (!chunk.getBlockState(p.set(x, y, z)).isAir()) {
                                    solid++;
                                }
                            }
                        }
                    }
                }
            }
            net.minecraft.world.level.chunk.LevelChunk fw = yard.getChunkSource().getChunkNow(l.floorWindowCenter().getX() >> 4, l.floorWindowCenter().getZ() >> 4);
            String marks = fw == null ? "chunks da cabine/vidro descarregados" : String.format("vidro=%s bau=%s fornalha=%s lampada=%s alavanca=%s porta=%s",
                    fw.getBlockState(l.floorWindowCenter()).is(Blocks.GLASS), yard.getBlockEntity(l.chest()) != null, yard.getBlockState(l.furnace()).is(Blocks.FURNACE),
                    yard.getBlockState(l.lamp()).is(Blocks.REDSTONE_LAMP), yard.getBlockState(l.lever()).is(Blocks.LEVER),
                    yard.getBlockState(l.doorLower()).is(Blocks.OAK_DOOR));
            return solid + " blocos nao-ar, colunas descarregadas=" + missingColumns + ", " + marks;
        });
        SpaceHorizons.LOGGER.info(TAG + "SHIPINTEGRIDADE {} nave {}: {}", label, shipId, s);
    }

    static void setReleaseTicks(Minecraft mc, long ticks) {
        onServer(mc, server -> {
            ShipChunkTickets.setReleaseTicks(ticks);
            return null;
        });
    }

    static boolean isHeld(Minecraft mc, int shipId) {
        return onServer(mc, server -> ShipChunkTickets.isHeld(shipId));
    }

    static int heldCount(Minecraft mc) {
        return onServer(mc, server -> ShipChunkTickets.held().size());
    }

    static int yardChunks(Minecraft mc) {
        return onServer(mc, server -> ShipYard.level(server).getChunkSource().getLoadedChunksCount());
    }

    // ---- blocos e inventários ----

    static BlockState serverState(Minecraft mc, BlockPos pos) {
        return onServer(mc, server -> ShipYard.level(server).getBlockState(pos));
    }

    /** Soma dos itens do baú da nave. */
    static int chestItems(Minecraft mc, int shipId) {
        BlockPos pos = layout(mc, shipId).chest();
        return onServer(mc, server -> {
            ServerLevel yard = ShipYard.level(server);
            yard.getChunk(pos);
            return yard.getBlockEntity(pos) instanceof Container chest ? count(chest, null) : -1;
        });
    }

    static int chestDiamonds(Minecraft mc, int shipId) {
        BlockPos pos = layout(mc, shipId).chest();
        return onServer(mc, server -> {
            ServerLevel yard = ShipYard.level(server);
            return yard.getBlockEntity(pos) instanceof Container chest ? count(chest, Items.DIAMOND) : -1;
        });
    }

    /** Tira {@code n} diamantes do baú (o servidor, como se o jogador os pegasse). */
    static void takeDiamonds(Minecraft mc, int shipId, int n) {
        BlockPos pos = layout(mc, shipId).chest();
        onServer(mc, server -> {
            if (ShipYard.level(server).getBlockEntity(pos) instanceof Container chest) {
                chest.removeItem(0, n);
                chest.setChanged();
            }
            return null;
        });
    }

    private static int count(Container container, net.minecraft.world.item.Item only) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (only == null || stack.is(only)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Fornalha da nave: aceso? e quantos minérios ainda não fundidos e lingotes prontos. */
    static String furnace(Minecraft mc, int shipId) {
        BlockPos pos = layout(mc, shipId).furnace();
        return onServer(mc, server -> {
            ServerLevel yard = ShipYard.level(server);
            BlockState state = yard.getBlockState(pos);
            if (!state.is(Blocks.FURNACE) || !(yard.getBlockEntity(pos) instanceof Container furnace)) {
                return "ausente (" + state + ")";
            }
            return String.format(Locale.ROOT, "aceso=%s minério=%d carvão=%d lingotes=%d", state.getValue(AbstractFurnaceBlock.LIT),
                    furnace.getItem(0).getCount(), furnace.getItem(1).getCount(), furnace.getItem(2).getCount());
        });
    }

    static boolean furnaceLit(Minecraft mc, int shipId) {
        BlockPos pos = layout(mc, shipId).furnace();
        return onServer(mc, server -> {
            BlockState state = ShipYard.level(server).getBlockState(pos);
            return state.is(Blocks.FURNACE) && state.getValue(AbstractFurnaceBlock.LIT);
        });
    }

    static boolean lampLit(Minecraft mc, int shipId) {
        BlockPos pos = layout(mc, shipId).lamp();
        return onServer(mc, server -> {
            BlockState state = ShipYard.level(server).getBlockState(pos);
            return state.is(Blocks.REDSTONE_LAMP) && state.getValue(RedstoneLampBlock.LIT);
        });
    }

    static boolean leverPowered(Minecraft mc, int shipId) {
        BlockPos pos = layout(mc, shipId).lever();
        return onServer(mc, server -> {
            BlockState state = ShipYard.level(server).getBlockState(pos);
            return state.is(Blocks.LEVER) && state.getValue(LeverBlock.POWERED);
        });
    }

    static boolean doorOpen(Minecraft mc, int shipId) {
        BlockPos pos = layout(mc, shipId).doorLower();
        return onServer(mc, server -> {
            BlockState state = ShipYard.level(server).getBlockState(pos);
            return state.is(Blocks.OAK_DOOR) && state.getValue(DoorBlock.OPEN);
        });
    }

    /** Luz de bloco e de céu no ponto de medida da nave, no servidor e no cliente: {servidorBloco, servidorCéu, clienteBloco, clienteCéu}. */
    static int[] light(Minecraft mc, int shipId, BlockPos pos) {
        int[] server = onServer(mc, srv -> {
            ServerLevel yard = ShipYard.level(srv);
            return new int[] {yard.getBrightness(LightLayer.BLOCK, pos), yard.getBrightness(LightLayer.SKY, pos)};
        });
        return new int[] {server[0], server[1], mc.level.getBrightness(LightLayer.BLOCK, pos), mc.level.getBrightness(LightLayer.SKY, pos)};
    }

    // ---- olhar e céu ----

    /** Yaw (graus) de uma direção em eixos do mundo. */
    static float yawOf(Vec3 dir) {
        return (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
    }

    static float pitchOf(Vec3 dir) {
        return (float) -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, dir.normalize().y))));
    }

    /**
     * O que há no caminho de um raio de visão: "janela" se passa por pelo menos um vidro e depois não encontra mais nada num raio de
     * 400 blocos, "aberto" se não há nada (nem vidro), ou "bloqueado por X". Anda pelos blocos do cliente em passos de 0,2.
     */
    static String sightLine(Level level, Vec3 eye, Vec3 dir) {
        Vec3 d = dir.normalize();
        int glass = 0;
        boolean inGlass = false;
        BlockPos last = null;
        for (double t = 0.3; t < 400.0; t += 0.2) {
            Vec3 p = eye.add(d.scale(t));
            BlockPos pos = BlockPos.containing(p);
            if (pos.equals(last)) {
                continue;
            }
            last = pos;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                inGlass = false;
            } else if (state.is(Blocks.GLASS)) {
                if (!inGlass) {
                    glass++;
                }
                inGlass = true;
            } else {
                return "bloqueado por " + state.getBlock().getName().getString() + " a " + String.format(Locale.ROOT, "%.0f", t) + " blocos";
            }
        }
        return glass > 0 ? "janela (" + glass + " vidro(s))" : "aberto";
    }

    /** Um quadro de céu: o relógio, de onde olhar e a direção do astro. */
    record SkyShot(long clock, Vec3 stand, Vec3 dir, double elevation) {
    }

    /**
     * Procura (relógio do dia, ponto de pé no salão) em que o astro {@code target} (null = o sol) aparece por uma janela da nave, com
     * pelo menos {@code minElevation} graus de altura. Varre o dia de 250 em 250 ticks e alguns pontos de pé; null se não houver.
     */
    static SkyShot findSkyShot(Minecraft mc, ShipBuilder.Layout layout, SolarSystem.Body target, double minElevation, long fromClock, long toClock) {
        double[] pos = ShipSkyClient.testPosition();
        Vec3 base = layout.spawnPoint();
        int cx = layout.spawn().getX();
        int cz = layout.spawn().getZ();
        int[][] offsets = {{0, 0}, {-12, 12}, {12, 12}, {-12, -12}, {0, 12}, {0, -12}, {-20, 0}, {-20, 12}, {-20, -12}};
        for (long clock = fromClock; clock <= toClock; clock += 250) {
            SkyObserver observer = SkyObserver.forShip(clock, pos, new Quaterniond());
            double[] dir = new double[3];
            if (target == null) {
                observer.sun(dir);
            } else {
                var a = observer.apparent(target);
                dir[0] = a.x();
                dir[1] = a.y();
                dir[2] = a.z();
            }
            double elevation = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, dir[1]))));
            if (elevation < minElevation) {
                continue;
            }
            for (int[] o : offsets) {
                Vec3 stand = new Vec3(cx + o[0] + 0.5, base.y, cz + o[1] + 0.5);
                Vec3 eye = stand.add(0.0, 1.62, 0.0);
                String sight = sightLine(mc.level, eye, new Vec3(dir[0], dir[1], dir[2]));
                if (sight.startsWith("janela")) {
                    return new SkyShot(clock, stand, new Vec3(dir[0], dir[1], dir[2]), elevation);
                }
            }
        }
        return null;
    }

    // ---- fechar e reabrir o mundo ----

    private static int reloadPhase;
    private static int reloadTicks;
    private static String reloadWorld;
    private static boolean reloadRegistered;
    private static boolean backupAnswered;
    private static long reloadStartedNanos;

    /**
     * Fecha o mundo (salvando) e o abre de novo, tudo pelo menu, como o jogador. O roteiro continua sozinho quando o jogador
     * volta (o AutoTest só avança com jogador e nível). {@link #reloadDone} diz quando terminou.
     */
    static void startReload(Minecraft mc, String worldName) {
        reloadWorld = worldName;
        reloadPhase = 1;
        reloadTicks = 0;
        reloadStartedNanos = System.nanoTime();
        if (!reloadRegistered) {
            reloadRegistered = true;
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> reloadTick(Minecraft.getInstance()));
        }
    }

    static boolean reloadDone() {
        return reloadPhase == 0;
    }

    private static void reloadTick(Minecraft mc) {
        if (reloadPhase == 0) {
            return;
        }
        reloadTicks++;
        switch (reloadPhase) {
            case 1 -> {
                SpaceHorizons.LOGGER.info(TAG + "RELOAD: fechando o mundo (salvando)");
                mc.disconnectWithSavingScreen();
                mc.setScreenAndShow(new TitleScreen());
                reloadPhase = 2;
                reloadTicks = 0;
            }
            case 2 -> {
                if (mc.level == null && mc.getSingleplayerServer() == null && mc.gui.screen() instanceof TitleScreen && reloadTicks > 20) {
                    SpaceHorizons.LOGGER.info(TAG + "RELOAD: mundo fechado em {} ms, reabrindo '{}'", (System.nanoTime() - reloadStartedNanos) / 1_000_000L, reloadWorld);
                    mc.createWorldOpenFlows().openWorld(reloadWorld, () -> SpaceHorizons.LOGGER.warn(TAG + "RELOAD: abertura cancelada"));
                    reloadPhase = 3;
                    reloadTicks = 0;
                } else if (reloadTicks % 100 == 0) {
                    SpaceHorizons.LOGGER.info(TAG + "RELOAD: esperando o mundo fechar (nível={}, servidor={}, tela={})", mc.level, mc.getSingleplayerServer(),
                            mc.gui.screen() == null ? "nenhuma" : mc.gui.screen().getClass().getSimpleName());
                }
            }
            case 3 -> {
                if (mc.player != null && mc.level != null && mc.getSingleplayerServer() != null) {
                    SpaceHorizons.LOGGER.info(TAG + "RELOAD: jogador de volta em {} ms (dim={})", (System.nanoTime() - reloadStartedNanos) / 1_000_000L,
                            mc.level.dimension().identifier());
                    reloadPhase = 0;
                } else if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.BackupConfirmScreen backup && reloadTicks > 20 && !backupAnswered) {
                    // aviso do vanilla ao abrir um mundo (cópia de segurança antes de carregar): o jogador clicaria "carregar sem cópia" (2º botão)
                    backupAnswered = true;
                    java.util.List<net.minecraft.client.gui.components.Button> buttons = new java.util.ArrayList<>();
                    for (var child : backup.children()) {
                        if (child instanceof net.minecraft.client.gui.components.Button b) {
                            buttons.add(b);
                        }
                    }
                    SpaceHorizons.LOGGER.info(TAG + "RELOAD: aviso '{}' com os botões {}", backup.getTitle().getString(),
                            buttons.stream().map(b -> b.getMessage().getString()).toList());
                    if (buttons.size() >= 2) {
                        buttons.get(1).onPress(new net.minecraft.client.input.MouseButtonInfo(0, 0));
                    }
                } else if (reloadTicks % 100 == 0) {
                    SpaceHorizons.LOGGER.info(TAG + "RELOAD: esperando o jogador (tela={})",
                            mc.gui.screen() == null ? "nenhuma" : mc.gui.screen().getClass().getSimpleName());
                }
            }
            default -> reloadPhase = 0;
        }
    }
}
