package com.spacehorizons.client.space;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.planetmesh.PlanetMeshGeometry;
import com.spacehorizons.client.planetmesh.PlanetMeshPalette;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrains;

import org.jspecify.annotations.Nullable;

/**
 * Esferas dos corpos já na GPU, uma por (planeta, resolução). A geometria é fixa no corpo (ver BodyMeshGeometry),
 * então cada nível é construído uma vez, numa thread de background, e vale para qualquer posição da câmera.
 * Os níveis ficam guardados (poucos MB): voltar a um corpo já visitado não reconstrói nada.
 *
 * Tudo exceto a geração roda no render thread. Para nunca mostrar nada enquanto o nível desejado não chega,
 * o primeiro pedido de um planeta constrói antes um nível pequeno.
 */
final class BodyMeshCache {
    /** Primeiro nível construído de cada planeta: rápido, para o corpo aparecer logo. */
    static final int FIRST_SEGMENTS = 64;
    private static final int VERTEX_BYTES = 16;

    record Key(PlanetDefinition planet, int segments) {
    }

    /** Esfera na GPU. */
    record Mesh(int segments, GpuBuffer vertices, GpuBuffer indices, int indexCount) {
    }

    private record Built(Key key, ByteBuffer vertices, ByteBuffer indices, int indexCount) {
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SpaceHorizons-BodyMesh");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private final ForkJoinPool pool = new ForkJoinPool(Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() / 4)));
    private final ConcurrentLinkedQueue<Built> ready = new ConcurrentLinkedQueue<>();
    // só o render thread mexe nestes dois
    private final Set<Key> pending = new HashSet<>();
    private final Map<Key, Mesh> meshes = new HashMap<>();
    private PlanetMeshGeometry.@Nullable Palette palette;

    /** Pede (se faltar) o nível {@code segments} do planeta, e antes dele o nível pequeno se ainda não há nada. */
    void ensure(PlanetDefinition planet, int segments) {
        if (!hasAny(planet)) {
            request(new Key(planet, Math.min(segments, FIRST_SEGMENTS)));
        }
        request(new Key(planet, segments));
    }

    private boolean hasAny(PlanetDefinition planet) {
        for (Key key : meshes.keySet()) {
            if (key.planet().equals(planet)) {
                return true;
            }
        }
        for (Key key : pending) {
            if (key.planet().equals(planet)) {
                return true;
            }
        }
        return false;
    }

    private void request(Key key) {
        if (meshes.containsKey(key) || !pending.add(key)) {
            return;
        }
        PlanetMeshGeometry.Palette colors = palette;
        if (colors == null) {
            colors = PlanetMeshPalette.create();
            palette = colors;
        }
        PlanetMeshGeometry.Palette usePalette = colors;
        worker.execute(() -> {
            try {
                ready.add(build(key, usePalette));
            } catch (Throwable t) {
                SpaceHorizons.LOGGER.error("Falha ao gerar a esfera de {} ({} segmentos)", key.planet().id(), key.segments(), t);
            }
        });
    }

    private Built build(Key key, PlanetMeshGeometry.Palette colors) {
        int segments = key.segments();
        int vertexCount = BodyMeshGeometry.vertexCount(segments);
        int indexCount = BodyMeshGeometry.indexCount(segments);
        float[] positions = new float[vertexCount * 3];
        int[] argb = new int[vertexCount];
        long start = System.nanoTime();
        BodyMeshGeometry.fill(PlanetTerrains.get(key.planet()), colors, segments, positions, argb, pool);
        int[] indices = new int[indexCount];
        BodyMeshGeometry.fillIndices(segments, indices);

        ByteBuffer vertexBytes = ByteBuffer.allocateDirect(vertexCount * VERTEX_BYTES).order(ByteOrder.nativeOrder());
        for (int v = 0; v < vertexCount; v++) {
            vertexBytes.putFloat(positions[v * 3]).putFloat(positions[v * 3 + 1]).putFloat(positions[v * 3 + 2]);
            int color = argb[v];
            vertexBytes.put((byte) (color >> 16)).put((byte) (color >> 8)).put((byte) color).put((byte) (color >>> 24));
        }
        vertexBytes.flip();
        ByteBuffer indexBytes = ByteBuffer.allocateDirect(indexCount * Integer.BYTES).order(ByteOrder.nativeOrder());
        indexBytes.asIntBuffer().put(indices);
        indexBytes.limit(indexCount * Integer.BYTES);
        SpaceHorizons.LOGGER.info("Esfera de {}: {} segmentos, {} vértices, {} ms", key.planet().id(), segments, vertexCount,
                String.format("%.0f", (System.nanoTime() - start) / 1.0e6));
        return new Built(key, vertexBytes, indexBytes, indexCount);
    }

    /** Sobe para a GPU, no render thread, no máximo uma esfera por frame. */
    void uploadReady() {
        Built built = ready.poll();
        if (built == null) {
            return;
        }
        pending.remove(built.key());
        GpuBuffer vertices = RenderSystem.getDevice().createBuffer(
                () -> "SpaceHorizons body vertices " + built.key().planet().id(), GpuBuffer.USAGE_VERTEX, built.vertices());
        GpuBuffer indices = RenderSystem.getDevice().createBuffer(
                () -> "SpaceHorizons body indices " + built.key().planet().id(), GpuBuffer.USAGE_INDEX, built.indices());
        Mesh old = meshes.put(built.key(), new Mesh(built.key().segments(), vertices, indices, built.indexCount()));
        if (old != null) {
            old.vertices().close();
            old.indices().close();
        }
    }

    /**
     * Melhor esfera pronta para o planeta: a de {@code wanted} segmentos, senão a mais fina abaixo dela, senão a mais
     * grossa acima (qualquer uma serve de aproximação enquanto a certa não chega).
     */
    @Nullable Mesh best(PlanetDefinition planet, int wanted) {
        Mesh exact = meshes.get(new Key(planet, wanted));
        if (exact != null) {
            return exact;
        }
        Mesh below = null;
        Mesh above = null;
        for (Mesh mesh : lookup(planet)) {
            if (mesh.segments() < wanted) {
                if (below == null || mesh.segments() > below.segments()) {
                    below = mesh;
                }
            } else if (above == null || mesh.segments() < above.segments()) {
                above = mesh;
            }
        }
        return below != null ? below : above;
    }

    private List<Mesh> lookup(PlanetDefinition planet) {
        List<Mesh> found = new ArrayList<>();
        for (Map.Entry<Key, Mesh> entry : meshes.entrySet()) {
            if (entry.getKey().planet().equals(planet)) {
                found.add(entry.getValue());
            }
        }
        return found;
    }

    /** Sai do mundo: libera as esferas da GPU. Construções em andamento terminam e entram no próximo upload. */
    void reset() {
        for (Mesh mesh : meshes.values()) {
            mesh.vertices().close();
            mesh.indices().close();
        }
        meshes.clear();
        ready.clear();
        pending.clear();
    }
}
