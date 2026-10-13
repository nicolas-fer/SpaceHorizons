package com.spacehorizons.client.planetmesh;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrains;

import org.jspecify.annotations.Nullable;

/**
 * Reconstrução assíncrona da malha: uma tarefa por vez numa thread de background, com buffers reaproveitados
 * entre reconstruções (nada de alocar por frame nem por reconstrução, só quando a malha cresce).
 *
 * Ciclo: o render thread chama submit; o worker gera vértices e índices já no formato final; o render thread
 * pega com poll(), copia para a GPU e chama release(). Enquanto o resultado não for liberado os buffers
 * pertencem ao render thread e nenhuma nova tarefa começa.
 */
final class PlanetMeshBuilder {
    /** Posição (3 floats) + cor RGBA8 = DefaultVertexFormat.POSITION_COLOR. */
    static final int VERTEX_BYTES = 16;

    /** O que construir: a câmera de construção e o desenho da grade. */
    record Request(PlanetDefinition planet, double camX, double camY, double camZ,
            PlanetMeshGeometry.Layout layout, PlanetMeshGeometry.Palette palette) {
    }

    /** Resultado pronto para subir à GPU; os ByteBuffers estão prontos para leitura (position 0, limit = dados). */
    record Built(Request request, ByteBuffer vertices, ByteBuffer indices) {
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SpaceHorizons-PlanetMesh");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    // poucos threads: a reconstrução acontece de tempos em tempos e não pode competir com o jogo
    private final ForkJoinPool pool = new ForkJoinPool(Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() / 4)));

    private volatile boolean busy;
    private volatile @Nullable Built ready;

    private float[] positions = new float[0];
    private int[] colors = new int[0];
    private int[] indexScratch = new int[0];
    private ByteBuffer vertexBytes = ByteBuffer.allocateDirect(0);
    private ByteBuffer indexBytes = ByteBuffer.allocateDirect(0);

    /** Livre para uma nova tarefa (nenhuma em andamento e nenhum resultado esperando o render thread). */
    boolean isIdle() {
        return !busy;
    }

    void submit(Request request) {
        busy = true;
        worker.execute(() -> {
            try {
                ready = build(request);
            } catch (Throwable t) {
                SpaceHorizons.LOGGER.error("Falha ao gerar a malha orbital do planeta", t);
                busy = false;
            }
        });
    }

    /** Resultado pronto, se houver. Depois de copiar os buffers, chamar release(). */
    @Nullable Built poll() {
        Built built = ready;
        ready = null;
        return built;
    }

    void release() {
        busy = false;
    }

    private Built build(Request request) {
        PlanetMeshGeometry.Layout layout = request.layout();
        int vertexCount = layout.vertexCount();
        int indexCount = layout.indexCount();
        if (positions.length < vertexCount * 3) {
            positions = new float[vertexCount * 3];
            colors = new int[vertexCount];
            vertexBytes = ByteBuffer.allocateDirect(vertexCount * VERTEX_BYTES).order(ByteOrder.nativeOrder());
        }
        if (indexScratch.length < indexCount) {
            indexScratch = new int[indexCount];
            indexBytes = ByteBuffer.allocateDirect(indexCount * Integer.BYTES).order(ByteOrder.nativeOrder());
        }

        PlanetMeshGeometry.fill(PlanetTerrains.get(request.planet()), request.palette(), layout,
                request.camX(), request.camY(), request.camZ(), positions, colors, pool);
        PlanetMeshGeometry.fillIndices(layout, indexScratch);

        vertexBytes.clear();
        for (int v = 0; v < vertexCount; v++) {
            vertexBytes.putFloat(positions[v * 3]).putFloat(positions[v * 3 + 1]).putFloat(positions[v * 3 + 2]);
            int argb = colors[v];
            vertexBytes.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
        }
        vertexBytes.flip();

        indexBytes.clear();
        indexBytes.asIntBuffer().put(indexScratch, 0, indexCount);
        indexBytes.limit(indexCount * Integer.BYTES);
        return new Built(request, vertexBytes, indexBytes);
    }
}
