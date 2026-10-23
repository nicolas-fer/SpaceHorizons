package com.spacehorizons.client.space;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.PortraitParts;
import com.spacehorizons.space.vessel.VesselEntry;
import com.spacehorizons.space.vessel.VesselHull;
import com.spacehorizons.space.vessel.VesselHullPartPayload;
import com.spacehorizons.space.vessel.VesselHullRequestPayload;
import com.spacehorizons.space.vessel.VesselMesh;
import com.spacehorizons.space.vessel.VesselPose;
import com.spacehorizons.space.vessel.VesselStatePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import org.jspecify.annotations.Nullable;

/**
 * Os outros veículos no cliente (docs/PLANO_NAVES.md, 4.3; fase 2d): guarda o último estado virtual de cada nave ou foguete próximo que o servidor mandou
 * ({@link VesselStatePayload}), o extrapola para cada quadro (posição pela velocidade e atitude pelo giro, num relógio de servidor estimado como em
 * {@code ShipRenderClient}: o instante de render não depende do atraso de cada pacote, então não há tremor) e junta os cascos que o servidor manda em
 * partes ({@link VesselHullPartPayload}) numa malha pronta para a GPU. O {@link VesselRenderer} lê daqui.
 *
 * <p>O casco só é pedido ao servidor para o veículo dentro do raio do casco; fora dele (ou enquanto o casco não chegou) o veículo é um impostor
 * (uma caixa com o raio dele, nunca menor que ~2 pixels). Só o thread principal do cliente mexe nisto.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class VesselClient {
    private static final double TICK_NANOS = 50_000_000.0;
    /** Sem pacote por mais disto (ms), o estado é velho e nada é desenhado. */
    private static final long STALE_MILLIS = 3_000L;
    private static final double OFFSET_RELAX_NANOS = 20_000.0;
    private static final double MAX_EXTRAPOLATION_TICKS = 24.0;
    /** De quantos em quantos ms o cliente repete o pedido de um casco que não chegou. */
    private static final long REQUEST_RETRY_MILLIS = 3_000L;
    private static final int MAX_HULLS = 16;

    /** Um veículo com o estado extrapolado para o instante do quadro. */
    public record Posed(VesselEntry entry, double[] pos, double[] attitude) {
    }

    /** O casco de um veículo: a malha (assando) e o buffer da GPU (criado no thread de render). */
    static final class Hull {
        final int version;
        final CompletableFuture<VesselMesh.Mesh> mesh;
        @Nullable GpuBuffer gpu;
        int vertexCount;

        Hull(int version, CompletableFuture<VesselMesh.Mesh> mesh) {
            this.version = version;
            this.mesh = mesh;
        }

        void close() {
            if (this.gpu != null) {
                this.gpu.close();
                this.gpu = null;
            }
        }
    }

    /** As três últimas posições (e seus ticks de servidor) de um veículo: o ajuste quadrático dá a posição entre pacotes sem o dente de serra da curvatura da órbita. */
    private static final class History {
        final long[] tick = new long[3];
        final double[][] pos = new double[3][];
        int n;

        void add(long t, double[] p) {
            if (this.n > 0 && t <= this.tick[this.n - 1]) {
                return;
            }
            if (this.n == 3) {
                this.tick[0] = this.tick[1];
                this.tick[1] = this.tick[2];
                this.pos[0] = this.pos[1];
                this.pos[1] = this.pos[2];
                this.n = 2;
            }
            this.tick[this.n] = t;
            this.pos[this.n] = p;
            this.n++;
        }

        /** A posição no tick de servidor {@code t} (fração) pelo polinômio de segundo grau pelos três pontos; null se não há três pontos próximos. */
        double @Nullable [] quadratic(double t) {
            if (this.n < 3 || this.tick[2] - this.tick[1] > 8 || this.tick[1] - this.tick[0] > 8) {
                return null;
            }
            double h0 = this.tick[1] - this.tick[0];
            double h1 = this.tick[2] - this.tick[1];
            double[] out = new double[3];
            for (int i = 0; i < 3; i++) {
                double d0 = (this.pos[1][i] - this.pos[0][i]) / h0;
                double d1 = (this.pos[2][i] - this.pos[1][i]) / h1;
                double c = (d1 - d0) / (h0 + h1);
                out[i] = this.pos[0][i] + d0 * (t - this.tick[0]) + c * (t - this.tick[0]) * (t - this.tick[1]);
            }
            return out;
        }
    }

    private static final Map<UUID, History> HISTORY = new HashMap<>();
    private static final Map<UUID, VesselEntry> ENTRIES = new LinkedHashMap<>();
    private static long lastTick;
    private static volatile long lastArrivalNanos;
    private static double clockOffsetNanos = Double.NaN;
    private static volatile long packets;

    private static final Map<UUID, Hull> HULLS = new LinkedHashMap<>();
    private static final PortraitParts.Receiver PARTS = new PortraitParts.Receiver();
    private static final Map<UUID, Long> REQUESTED = new HashMap<>();
    private static final List<VesselHullRequestPayload> TO_REQUEST = new ArrayList<>();
    private static volatile int hullsReceived;
    private static final Map<Byte, GpuBuffer> IMPOSTORS = new HashMap<>();

    private VesselClient() {
    }

    @SubscribeEvent
    static void onRegisterHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(VesselStatePayload.TYPE, (payload, context) -> onState(payload));
        event.register(VesselHullPartPayload.TYPE, (payload, context) -> onHullPart(payload));
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!TO_REQUEST.isEmpty() && Minecraft.getInstance().getConnection() != null) {
            for (VesselHullRequestPayload request : TO_REQUEST) {
                ClientPacketDistributor.sendToServer(request);
            }
        }
        TO_REQUEST.clear();
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static void onState(VesselStatePayload payload) {
        long now = System.nanoTime();
        double raw = now - payload.tick() * TICK_NANOS;
        clockOffsetNanos = Double.isNaN(clockOffsetNanos) || raw < clockOffsetNanos ? raw : Math.min(raw, clockOffsetNanos + OFFSET_RELAX_NANOS);
        ENTRIES.clear();
        for (VesselEntry e : payload.entries()) {
            ENTRIES.put(e.id(), e);
            HISTORY.computeIfAbsent(e.id(), k -> new History()).add(payload.tick(), e.pos());
        }
        HISTORY.keySet().removeIf(id -> !ENTRIES.containsKey(id));
        lastTick = payload.tick();
        lastArrivalNanos = now;
        packets++;
    }

    private static void onHullPart(VesselHullPartPayload payload) {
        try {
            UUID key = partKey(payload.id(), payload.version());
            if (PARTS.accept(key, payload.index(), payload.count(), payload.bytes())) {
                byte[] bytes = PARTS.take(key);
                if (bytes != null) {
                    int version = payload.version();
                    CompletableFuture<VesselMesh.Mesh> mesh = CompletableFuture.supplyAsync(() -> VesselMesh.build(VesselHull.decode(bytes)), Util.backgroundExecutor());
                    mesh.exceptionally(t -> {
                        SpaceHorizons.LOGGER.error("Casco do veículo {} recusado", payload.id(), t);
                        return null;
                    });
                    Hull old = HULLS.put(payload.id(), new Hull(version, mesh));
                    if (old != null) {
                        old.close();
                    }
                    while (HULLS.size() > MAX_HULLS) {
                        UUID first = HULLS.keySet().iterator().next();
                        HULLS.remove(first).close();
                    }
                    hullsReceived++;
                    SpaceHorizons.LOGGER.info("Casco do veículo {} v{} completo no cliente ({} bytes)", payload.id(), version, bytes.length);
                }
            }
        } catch (IllegalArgumentException e) {
            SpaceHorizons.LOGGER.warn("Parte de casco recusada: {}", e.getMessage());
        }
    }

    /** A chave que junta as partes de um casco: o id do veículo misturado com a versão (versões diferentes nunca se misturam). */
    static UUID partKey(UUID id, int version) {
        return new UUID(id.getMostSignificantBits() ^ ((long) version * 0x9E3779B97F4A7C15L), id.getLeastSignificantBits());
    }

    // ---------------------------------------------------------------- consulta

    /** Há estado recente de outros veículos? */
    static boolean active() {
        return !ENTRIES.isEmpty() && (System.nanoTime() - lastArrivalNanos) / 1_000_000L <= STALE_MILLIS;
    }

    /** Os veículos com a posição e a atitude extrapoladas para {@code nowNanos}; vazio sem estado recente. */
    public static List<Posed> posed(long nowNanos) {
        if (!active()) {
            return List.of();
        }
        double dt = ticksSince(nowNanos);
        List<Posed> out = new ArrayList<>(ENTRIES.size());
        for (VesselEntry e : ENTRIES.values()) {
            History h = HISTORY.get(e.id());
            double[] fitted = h == null ? null : h.quadratic(lastTick + dt);
            out.add(new Posed(e, fitted != null ? fitted : VesselPose.extrapolatePosition(e.pos(), e.velocity(), dt),
                    VesselPose.extrapolateAttitude(e.attitude(), e.omega(), dt)));
        }
        return out;
    }

    private static double ticksSince(long nowNanos) {
        if (Double.isNaN(clockOffsetNanos)) {
            return 0.0;
        }
        double serverTicks = (nowNanos - clockOffsetNanos) / TICK_NANOS;
        return Math.max(-1.0, Math.min(MAX_EXTRAPOLATION_TICKS, serverTicks - lastTick));
    }

    public static long packetCount() {
        return packets;
    }

    /** Quantos veículos o último pacote tinha (para o autoteste). */
    public static int count() {
        return active() ? ENTRIES.size() : 0;
    }

    public static int hullsReceived() {
        return hullsReceived;
    }

    /** O casco do veículo está pronto (malha assada) na versão que o servidor anuncia? */
    public static boolean hullReady(UUID id) {
        VesselEntry e = ENTRIES.get(id);
        Hull h = HULLS.get(id);
        return e != null && h != null && h.version == e.hullVersion() && h.mesh.isDone() && !h.mesh.isCompletedExceptionally();
    }

    /** O casco de {@code id} para desenhar (versão em vigor, malha pronta), ou null. */
    static @Nullable Hull hullFor(UUID id, int version) {
        Hull h = HULLS.get(id);
        if (h != null && h.version == version && h.mesh.isDone() && !h.mesh.isCompletedExceptionally() && h.mesh.join() != null) {
            return h;
        }
        return null;
    }

    /** O veículo está perto o bastante para ter casco: pede ao servidor (uma vez por {@link #REQUEST_RETRY_MILLIS}) se ainda não tem a versão. */
    static void wantHull(UUID id, int version) {
        Hull h = HULLS.get(id);
        if (h != null && h.version == version) {
            return;
        }
        long now = System.nanoTime() / 1_000_000L;
        Long last = REQUESTED.get(id);
        if (last == null || now - last >= REQUEST_RETRY_MILLIS) {
            REQUESTED.put(id, now);
            TO_REQUEST.add(new VesselHullRequestPayload(id, version));
        }
    }

    /** Sobe a malha do casco para a GPU (no thread de render), uma vez. */
    static boolean upload(Hull hull) {
        if (hull.gpu != null) {
            return true;
        }
        VesselMesh.Mesh mesh = hull.mesh.join();
        if (mesh == null || mesh.vertexCount() == 0) {
            return false;
        }
        hull.gpu = buffer(mesh, "Vessel hull");
        hull.vertexCount = mesh.vertexCount();
        return true;
    }

    /** O buffer da caixa do impostor (cubo de lado 1 centrado na origem) de um tipo de veículo. */
    static GpuBuffer impostor(byte kind) {
        return IMPOSTORS.computeIfAbsent(kind, k -> buffer(VesselMesh.box(0.5F, 0.5F, 0.5F, VesselEntry.impostorRgb(k)), "Vessel impostor"));
    }

    static int impostorVertices() {
        return 36;
    }

    private static GpuBuffer buffer(VesselMesh.Mesh mesh, String label) {
        int size = mesh.vertexCount() * DefaultVertexFormat.POSITION_COLOR.getVertexSize();
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(size)) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i < mesh.vertexCount(); i++) {
                builder.addVertex(mesh.positions()[i * 3], mesh.positions()[i * 3 + 1], mesh.positions()[i * 3 + 2]).setColor(mesh.colors()[i]);
            }
            try (MeshData data = builder.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_VERTEX, data.vertexBuffer());
            }
        }
    }

    /** Esquece tudo (saída do mundo) e solta os buffers da GPU. */
    static void reset() {
        ENTRIES.clear();
        HISTORY.clear();
        clockOffsetNanos = Double.NaN;
        PARTS.clear();
        REQUESTED.clear();
        TO_REQUEST.clear();
        for (Hull h : HULLS.values()) {
            h.close();
        }
        HULLS.clear();
        for (GpuBuffer b : IMPOSTORS.values()) {
            b.close();
        }
        IMPOSTORS.clear();
    }
}
