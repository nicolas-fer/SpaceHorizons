package com.spacehorizons.client.devtest;

import java.util.Locale;
import java.util.UUID;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.space.SpaceDimension;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Só em dev (autoteste): voa o jogador em linha reta, a velocidade constante, pela dimensão de espaço aberto, empurrado
 * pelo SERVIDOR a cada tick (como a nave e o piloto automático), e mede o custo dos chunks da dimensão vazia
 * (pesquisa 3c, seção 4.5): tempo de trabalho de cada tick do servidor, ticks acima de 50 e 100 ms, passos segurados
 * esperando chunk, e quantos chunks ficaram carregados.
 *
 * Dois modos, para comparar:
 * <ul>
 *   <li>com tickets: o desenho da etapa 3 (tickets ENDER_PEARL à frente, em 1, 2, 4, 8, 16 e 32 ticks, raio 2 nos dois primeiros e
 *       0 nos outros; passo segurado se faltar chunk até 4 ticks à frente, no máximo 200 ticks; {@code chunkSource.move} depois
 *       de mover);</li>
 *   <li>sem tickets: só empurra (o {@code Entity.setPosRaw} do NeoForge pede cada chunk de destino e o servidor espera por ele).</li>
 * </ul>
 * Opcionalmente reduz as distâncias de visão e de simulação do servidor durante o voo (e as restaura no fim).
 * Estado só na thread do servidor; o fim é sinalizado por um volatile para o autoteste do cliente.
 */
final class SpaceFlightProbe {
    private static final int[] AHEAD = {1, 2, 4, 8, 16, 32};
    /** Faltar chunk até este passo à frente segura o passo da simulação. */
    private static final int GATE_UP_TO = 4;
    private static final int MAX_HELD = 200;
    private static final String TAG = "[AUTOTEST] ";

    private static boolean installed;
    private static volatile boolean done = true;
    /** O pedido de início já saiu do cliente mas ainda não rodou na thread do servidor (o fim do voo anterior não vale). */
    private static volatile boolean starting;

    // ---- estado do voo em andamento (thread do servidor)
    private static String label;
    private static UUID playerId;
    private static Vec3 perTick;
    private static int remaining;
    private static boolean tickets;
    private static int savedView = -1;
    private static int savedSimulation = -1;
    private static int moved;
    private static int held;
    private static int heldInARow;
    private static int total;
    private static double sumMs;
    private static double maxMs;
    private static int over50;
    private static int over100;
    private static long preStart;
    private static int startChunks;
    private static int maxChunks;

    private SpaceFlightProbe() {
    }

    /** O voo terminou (ou nunca começou)? Pode ser lido de qualquer thread. */
    static boolean isDone() {
        return done && !starting;
    }

    /**
     * Começa um voo. Pode ser chamado de qualquer thread: o início roda na thread do servidor.
     *
     * @param perTick deslocamento por tick (blocos); 600 blocos/s = 30 por tick
     * @param ticks quantos ticks de voo (passos segurados não contam)
     * @param withTickets tickets à frente e passo segurado; false = só empurra
     * @param distance distância de visão e simulação do servidor durante o voo; 0 = não mexe
     */
    static void start(MinecraftServer server, UUID player, String name, Vec3 perTick, int ticks, boolean withTickets, int distance) {
        starting = true;
        server.execute(() -> begin(server, player, name, perTick, ticks, withTickets, distance));
    }

    private static void begin(MinecraftServer server, UUID player, String name, Vec3 velocity, int ticks, boolean withTickets,
            int distance) {
        if (!installed) {
            installed = true;
            NeoForge.EVENT_BUS.addListener(ServerTickEvent.Pre.class, event -> preStart = System.nanoTime());
            NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> onPost(event.getServer()));
        }
        done = false;
        starting = false;
        label = name;
        playerId = player;
        perTick = velocity;
        remaining = ticks;
        tickets = withTickets;
        moved = 0;
        held = 0;
        heldInARow = 0;
        total = 0;
        sumMs = 0.0;
        maxMs = 0.0;
        over50 = 0;
        over100 = 0;
        maxChunks = 0;
        ServerPlayer p = server.getPlayerList().getPlayer(player);
        startChunks = p == null ? 0 : p.level().getChunkSource().getLoadedChunksCount();
        if (distance > 0) {
            savedView = server.getPlayerList().getViewDistance();
            savedSimulation = server.getPlayerList().getSimulationDistance();
            server.getPlayerList().setViewDistance(distance);
            server.getPlayerList().setSimulationDistance(distance);
        }
        SpaceHorizons.LOGGER.info(TAG + "VOO {} inicio: {} blocos/s ({} por tick), {} ticks, tickets={}, distancia={}/{} chunks no inicio={}",
                label, f(velocity.length() * 20.0), f(velocity.length()), ticks, withTickets,
                server.getPlayerList().getViewDistance(), server.getPlayerList().getSimulationDistance(), startChunks);
    }

    private static void onPost(MinecraftServer server) {
        if (done) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null || !SpaceDimension.is(player.level())) {
            finish(server, "jogador fora da dimensao de espaco");
            return;
        }
        ServerLevel level = player.level();
        boolean hold = false;
        if (tickets) {
            hold = warmAhead(level, player.position(), perTick);
            if (hold && ++heldInARow > MAX_HELD) {
                if (heldInARow == MAX_HELD + 1) {
                    SpaceHorizons.LOGGER.warn(TAG + "VOO {}: chunks a frente nao carregaram em {} ticks; seguindo assim mesmo", label, MAX_HELD);
                }
                hold = false;
            }
            if (!hold) {
                heldInARow = 0;
            }
        }
        if (hold) {
            held++;
        } else {
            Vec3 next = player.position().add(perTick);
            // velocidade zero e rotacao relativa a zero: o mesmo que o piloto automatico manda a cada tick
            player.connection.teleport(new PositionMoveRotation(next, Vec3.ZERO, 0.0F, 0.0F), Relative.ROTATION);
            moved++;
            remaining--;
        }
        if (tickets) {
            level.getChunkSource().move(player);
        }
        double ms = (System.nanoTime() - preStart) / 1.0e6;
        total++;
        sumMs += ms;
        maxMs = Math.max(maxMs, ms);
        if (ms > 50.0) {
            over50++;
        }
        if (ms > 100.0) {
            over100++;
        }
        maxChunks = Math.max(maxChunks, level.getChunkSource().getLoadedChunksCount());
        if (remaining <= 0) {
            finish(server, "concluido");
        }
    }

    /** Pede os chunks a frente da nave (tickets que expiram sozinhos). true = falta chunk dos primeiros passos: segurar. */
    private static boolean warmAhead(ServerLevel level, Vec3 pos, Vec3 velocity) {
        boolean missing = false;
        for (int k : AHEAD) {
            int cx = Mth.floor(pos.x + velocity.x * k) >> 4;
            int cz = Mth.floor(pos.z + velocity.z * k) >> 4;
            if (!level.hasChunk(cx, cz)) {
                level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, new ChunkPos(cx, cz), k <= 2 ? 2 : 0);
                missing |= k <= GATE_UP_TO;
            }
        }
        return missing;
    }

    private static void finish(MinecraftServer server, String reason) {
        done = true;
        if (savedView >= 0) {
            server.getPlayerList().setViewDistance(savedView);
            server.getPlayerList().setSimulationDistance(savedSimulation);
            savedView = -1;
            savedSimulation = -1;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        int loaded = player == null ? -1 : player.level().getChunkSource().getLoadedChunksCount();
        SpaceHorizons.LOGGER.info(TAG + "VOO {} RESULTADO ({}): ticks do servidor={} passos dados={} segurados={} tempo de trabalho do tick: "
                + "medio={} ms maximo={} ms, acima de 50 ms={} acima de 100 ms={}; chunks carregados no inicio={} maximo={} no fim={}",
                label, reason, total, moved, held, f(total == 0 ? 0.0 : sumMs / total), f(maxMs), over50, over100, startChunks, maxChunks,
                loaded);
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
