package com.spacehorizons.client.planetmesh;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.OptionalDouble;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.space.SpaceSun;
import com.spacehorizons.client.planetmesh.dh.DhRange;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrains;
import com.spacehorizons.planet.Planets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.neoforged.fml.ModList;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

/**
 * Desenha a malha orbital do planeta como "fundo" do frame: depois do céu e antes de terreno, DH e entidades.
 *
 * Tudo aqui roda no render thread, exceto a geração (PlanetMeshBuilder). A malha vive numa passada própria,
 * com projeção de far plane grande e um alvo de profundidade só dela (limpo a cada frame). O depth do alvo
 * principal fica intocado em 0 (profundidade invertida: 0 = infinito), então qualquer coisa que o vanilla ou o DH
 * desenhem depois passa no teste e cobre a malha. Assim a malha só aparece onde não há chunk nem LOD.
 */
final class PlanetMeshRenderer {
    // ---- parâmetros ----
    /**
     * Orçamento de vértices da grade adaptativa (ver PlanetMeshGeometry.Layout.adaptive). Cada vértice custa
     * ~2 amostras de terreno (altura + tipo) mais 3 de tipo para o supersampling; o tempo de reconstrução vai no
     * log (PlanetMeshBuilder.logTiming). Mais vértices = bordas mais finas, e reconstrução mais lenta.
     * Medido (pool de 3 threads, primeira reconstrução): 70.000 vértices levaram 75 a 94 ms (~1,1 a 1,3 µs por
     * vértice, mais em CPU fraca). 50.000 ficam em ~55 a 65 ms, fora do render thread de qualquer forma.
     */
    static final int MAX_VERTICES = 50_000;
    /** Menor raio interno aceito (blocos), mesmo com render distance minúscula. */
    static final double MIN_INNER_RADIUS = 128.0;
    /** Raio interno sem DH quando os chunks já não alcançam o chão: a malha cobre o disco quase todo. */
    static final double NO_DH_INNER_RADIUS = 16.0;
    // Acima disso a malha usa o raio interno mínimo e cobre tudo. O DH e os chunks desenham depois e sobrescrevem
    // a malha, então o único custo é mais triângulos; assim não há altitude em que reapareça o buraco no centro.
    static final double DH_BLIND_ALTITUDE = 2000.0;
    /** Sobreposição com o alcance do DH (blocos): a malha começa um pouco antes para não abrir fresta. */
    static final double MAX_OVERLAP = 256.0;
    /** Luz ambiente do lado noturno (fração da cor). */
    static final float NIGHT_AMBIENT = 0.07F;
    static final float NEAR_PLANE = 16.0F;
    static final float FAR_PLANE = 400_000.0F;
    /** Reconstrução: deslocamento horizontal mínimo como fração do espaçamento do primeiro anel, e limites em blocos. */
    static final double MOVE_FRACTION = 0.25;
    static final double MOVE_MIN = 16.0;
    static final double MOVE_MAX = 64.0;
    /** Reconstrução: variação relativa de altitude (e piso em blocos). */
    static final double ALTITUDE_FRACTION = 0.10;
    static final double ALTITUDE_MIN = 50.0;
    /** Intervalo mínimo entre inícios de reconstrução. */
    static final long MIN_REBUILD_NANOS = 150_000_000L;
    /** Frames até fechar um buffer substituído (a GPU pode ainda estar lendo dele). */
    static final int RETIRE_FRAMES = 8;
    static final int DH_POLL_FRAMES = 20;

    private static final int UBO_SIZE = new Std140SizeCalculator().putMat4f().putVec4().putVec4().putVec4().putVec4().get();

    /** Malha já na GPU, com a câmera de construção para compensar o movimento desde então. */
    private record Mesh(PlanetDefinition planet, GpuBuffer vertices, int indexCount,
            double camX, double camY, double camZ, double innerRadius, double outerRadius, double nearRadius, int firstRingSegments) {
    }

    private record Retired(GpuBuffer buffer, long frame) {
    }

    static final PlanetMeshRenderer INSTANCE = new PlanetMeshRenderer();

    private final PlanetMeshBuilder builder = new PlanetMeshBuilder();
    private final ArrayDeque<Retired> retired = new ArrayDeque<>();
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f mvp = new Matrix4f();

    private PlanetMeshGeometry.@Nullable Palette palette;
    private @Nullable TextureTarget depthTarget;
    private @Nullable GpuBuffer ubo;
    private @Nullable GpuBuffer indexBuffer;
    private PlanetMeshGeometry.@Nullable Layout indexLayout;
    private @Nullable Mesh mesh;
    private boolean disabled;
    private boolean warnedPipeline;
    private long frame;
    private long lastBuildStart;
    private double nearRadius = MIN_INNER_RADIUS;
    private int dhRangeCache = -1;
    private int dhPollCountdown;
    /** Versão do mapa do planeta (PlanetTerrains.version) com que a última malha foi pedida. */
    private int builtMapVersion;

    private PlanetMeshRenderer() {
    }

    /**
     * Sai do mundo ou do planeta: libera tudo na hora. Sem frame de planeta não há mais quem leia esses buffers,
     * e closeRetired() só roda em frames de planeta.
     */
    void reset() {
        Mesh old = mesh;
        if (old != null) {
            old.vertices().close();
            mesh = null;
        }
        for (Retired r : retired) {
            r.buffer().close();
        }
        retired.clear();
        if (indexBuffer != null) {
            indexBuffer.close();
            indexBuffer = null;
        }
        if (ubo != null) {
            ubo.close();
            ubo = null;
        }
        if (depthTarget != null) {
            depthTarget.destroyBuffers();
            depthTarget = null;
        }
    }

    void render(LevelRenderState state) {
        if (disabled) {
            return;
        }
        try {
            renderInternal(state);
        } catch (Throwable t) {
            // falha de render não pode derrubar o jogo: desliga a malha e avisa uma vez
            disabled = true;
            SpaceHorizons.LOGGER.error("Malha orbital desligada por erro de renderização", t);
        }
    }

    private void renderInternal(LevelRenderState state) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Optional<PlanetDefinition> found = Planets.forLevel(level);
        if (found.isEmpty()) {
            reset();
            return;
        }
        PlanetDefinition planet = found.get();
        frame++;
        closeRetired();

        CameraRenderState camera = state.cameraRenderState;
        double camX = camera.pos.x;
        double camY = camera.pos.y;
        double camZ = camera.pos.z;
        double altitude = camY - planet.seaLevel();

        // só o que pode aparecer sobre o horizonte (com folga para a subida até a próxima reconstrução)
        double outer = outerRadius(planet, altitude);
        double inner = innerRadius(minecraft, altitude);
        if (inner >= outer * 0.9) {
            return;
        }
        // Com a câmera baixa tudo além do alcance está atrás do horizonte: nem constrói nem desenha
        if (PlanetMeshGeometry.horizonDistance(planet.radius(), altitude) <= inner) {
            return;
        }

        uploadReady();
        maybeRebuild(planet, camera, camX, camY, camZ, inner, outer);

        Mesh current = mesh;
        if (current == null || current.planet() != planet || indexBuffer == null) {
            return;
        }
        double stale = Math.hypot(camX - current.camX(), camZ - current.camZ());
        if (stale > Math.max(256.0, current.innerRadius() * 0.2)) {
            return; // teletransporte: melhor não desenhar do que mostrar o planeta no lugar errado até a nova malha chegar
        }
        draw(current, planet, camera, state, camX, camY, camZ);
    }

    // ---- raio interno e visibilidade ----

    /**
     * Raio interno da malha. Também atualiza {@link #nearRadius}, o alcance de chunks/DH usado para decidir onde a
     * malha segue a mesma carta deles (PlanetMeshProjection.weight).
     */
    private double innerRadius(Minecraft minecraft, double altitude) {
        int range = -1;
        if (ModList.get().isLoaded("distanthorizons")) {
            if (--dhPollCountdown <= 0) {
                dhPollCountdown = DH_POLL_FRAMES;
                dhRangeCache = DhRange.renderDistanceBlocks();
            }
            range = dhRangeCache;
        }
        if (range <= 0) {
            range = minecraft.options.renderDistance().get() * 16;
        }
        nearRadius = range;
        boolean hasDh = ModList.get().isLoaded("distanthorizons") && dhRangeCache > 0;
        if (!hasDh && altitude > DH_BLIND_ALTITUDE) {
            // Sem DH nada além dos chunks 3D desenha o disco central: o buraco do vanilla tem de ficar coberto pela
            // malha (3 blocos abaixo do terreno, então a sobreposição com os chunks é inofensiva).
            return NO_DH_INNER_RADIUS;
        }
        if (altitude > DH_BLIND_ALTITUDE) {
            return MIN_INNER_RADIUS;
        }
        if (!hasDh) {
            // Sem DH, o vanilla só desenha seções dentro de render distance em 3D (visto no autoteste: a 600 blocos
            // o chão não aparece). O alcance horizontal efetivo encolhe com a altura sobre o terreno (~60 blocos).
            double height = Math.max(0.0, altitude - 60.0);
            double effective = Math.sqrt(Math.max(0.0, (double) range * range - height * height));
            if (effective <= MIN_INNER_RADIUS) {
                return NO_DH_INNER_RADIUS;
            }
            range = (int) effective;
        }
        double overlap = Math.min(MAX_OVERLAP, range * 0.25);
        // múltiplo de 16 para que o raio interno não oscile à toa e dispare reconstruções
        return Math.max(MIN_INNER_RADIUS, Math.floor((range - overlap) / 16.0) * 16.0);
    }

    /** Raio externo da malha: horizonte da altitude (com folga), limitado a 0,98·π·R. */
    static double outerRadius(PlanetDefinition planet, double altitude) {
        return PlanetMeshGeometry.outerRadius(planet, altitude);
    }

    // ---- reconstrução ----

    private void uploadReady() {
        PlanetMeshBuilder.Built built = builder.poll();
        if (built == null) {
            return;
        }
        try {
            GpuDevice device = RenderSystem.getDevice();
            PlanetMeshBuilder.Request request = built.request();
            PlanetMeshGeometry.Layout layout = request.layout();
            GpuBuffer vertices = device.createBuffer(() -> "SpaceHorizons planet mesh vertices", GpuBuffer.USAGE_VERTEX, built.vertices());
            GpuBuffer oldIndices = indexBuffer;
            PlanetMeshGeometry.Layout previous = indexLayout;
            if (oldIndices == null || previous == null || !previous.sameTopology(layout)
                    || oldIndices.size() != built.indices().remaining()) {
                if (oldIndices != null) {
                    retire(oldIndices);
                }
                indexBuffer = device.createBuffer(() -> "SpaceHorizons planet mesh indices", GpuBuffer.USAGE_INDEX, built.indices());
                indexLayout = layout;
            }
            Mesh old = mesh;
            if (old != null) {
                retire(old.vertices());
            }
            mesh = new Mesh(request.planet(), vertices, layout.indexCount(),
                    request.camX(), request.camY(), request.camZ(), layout.rMin(), layout.rMax(), request.nearRadius(), layout.segments(0));
        } finally {
            builder.release();
        }
    }

    private void maybeRebuild(PlanetDefinition planet, CameraRenderState camera,
            double camX, double camY, double camZ, double inner, double outer) {
        if (!builder.isIdle() || System.nanoTime() - lastBuildStart < MIN_REBUILD_NANOS) {
            return;
        }
        Mesh current = mesh;
        // mapa do mundo novo (ou removido): reconstrói já, sem esperar a câmera andar
        boolean mapChanged = PlanetTerrains.version(planet) != builtMapVersion;
        if (current != null && current.planet() == planet && !mapChanged) {
            double spacing = current.innerRadius() * (2.0 * Math.PI / current.firstRingSegments());
            double moveThreshold = Math.max(MOVE_MIN, Math.min(MOVE_MAX, spacing * MOVE_FRACTION));
            boolean moved = Math.hypot(camX - current.camX(), camZ - current.camZ()) > moveThreshold;
            double builtAlt = current.camY() - planet.seaLevel();
            double alt = camY - planet.seaLevel();
            boolean altitudeChanged = Math.abs(alt - builtAlt) > Math.max(ALTITUDE_MIN, ALTITUDE_FRACTION * Math.abs(builtAlt));
            boolean innerChanged = Math.abs(inner - current.innerRadius()) > 0.05 * inner + 32.0;
            // a margem de altitude da malha construída acabou: o horizonte já passou do raio externo
            boolean outerShort = current.outerRadius() < PlanetMeshGeometry.horizonDistance(planet.radius(), Math.max(0.0, alt)) * 1.01
                    && current.outerRadius() < PlanetMeshGeometry.outerRadius(planet) - 1.0;
            boolean nearChanged = Math.abs(nearRadius - current.nearRadius()) > 0.1 * nearRadius + 32.0;
            if (!moved && !altitudeChanged && !innerChanged && !outerShort && !nearChanged) {
                return;
            }
        }
        PlanetMeshGeometry.Palette colors = palette;
        if (colors == null) {
            colors = PlanetMeshPalette.create();
            palette = colors;
        }
        PlanetMeshGeometry.Layout layout = PlanetMeshGeometry.Layout.adaptive(planet.radius(),
                camY - planet.seaLevel(), inner, outer, pixelAngle(camera), MAX_VERTICES);
        lastBuildStart = System.nanoTime();
        builtMapVersion = PlanetTerrains.version(planet);
        builder.submit(new PlanetMeshBuilder.Request(planet, camX, camY, camZ, nearRadius, layout, colors));
    }

    /** Ângulo (rad) de um pixel no centro da tela: 2·tan(fovY/2) / altura. Zero se ainda não dá para saber. */
    private static double pixelAngle(CameraRenderState camera) {
        float m11 = camera.projectionMatrix.m11();
        int height = Minecraft.getInstance().gameRenderer.mainRenderTarget().height;
        if (m11 == 0.0F || height <= 0) {
            return 0.0;
        }
        return 2.0 / m11 / height;
    }

    // ---- desenho ----

    private void draw(Mesh current, PlanetDefinition planet, CameraRenderState camera, LevelRenderState state,
            double camX, double camY, double camZ) {
        Minecraft minecraft = Minecraft.getInstance();
        CompiledRenderPipeline pipeline = RenderSystem.getCompiledPipelineNullable(PlanetMeshPipelines.PLANET_MESH);
        if (pipeline == null) {
            if (!warnedPipeline) {
                warnedPipeline = true;
                SpaceHorizons.LOGGER.warn("Pipeline da malha orbital não compilou; a malha não será desenhada");
            }
            return;
        }
        GpuBufferSlice fog = RenderSystem.getShaderFog();
        float m00 = camera.projectionMatrix.m00();
        float m11 = camera.projectionMatrix.m11();
        if (fog == null || m00 == 0.0F || m11 == 0.0F) {
            return;
        }
        RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
        if (main.getColorTextureView() == null) {
            return;
        }
        TextureTarget depth = ensureDepthTarget(main.width, main.height);

        // projeção com o mesmo FOV e proporção do vanilla, mas near/far próprios; invertida como a do vanilla (near e far trocados)
        float fovY = 2.0F * (float) Math.atan(1.0F / m11);
        boolean zZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        projection.setPerspective(fovY, m11 / m00, FAR_PLANE, NEAR_PLANE, zZeroToOne);
        mvp.set(projection).mul(camera.viewRotationMatrix);

        // a malha foi gerada relativa à câmera de construção; o deslocamento a mantém colada no mundo entre reconstruções
        float offX = (float) (current.camX() - camX);
        float offY = (float) (current.camY() - camY);
        float offZ = (float) (current.camZ() - camZ);
        double radius = planet.radius();
        double builtAltitude = current.camY() - planet.seaLevel();
        float centerX = offX;
        float centerY = (float) (-(radius + builtAltitude)) + offY;
        float centerZ = offZ;

        // sol único (SunModel) no ponto da câmera, nos eixos do mundo: a mesma direção do disco, do céu e da luz do chão
        float[] sun = new float[3];
        SpaceSun.localAt(minecraft.level, planet, camX, camZ, sun);
        float sunX = sun[0];
        float sunY = sun[1];
        float sunZ = sun[2];

        // ponto sob a câmera de construção (latitude e longitude do mundo): o shader leva a direção de cada vértice ao referencial fixo
        // do corpo para o detalhe procedural não nadar quando a malha é reconstruída (ver planetmesh/planet_mesh.vsh)
        double lat0 = current.camZ() / radius;
        double lon0 = current.camX() / radius;
        float[] frame = {(float) Math.sin(lat0), (float) Math.cos(lat0), (float) Math.sin(lon0), (float) Math.cos(lon0)};

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuBuffer uniforms = ensureUbo();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, UBO_SIZE)
                    .putMat4f(mvp)
                    .putVec4(offX, offY, offZ, 0.0F)
                    .putVec4(sunX, sunY, sunZ, NIGHT_AMBIENT)
                    .putVec4(centerX, centerY, centerZ, (float) radius)
                    .putVec4(frame[0], frame[1], frame[2], frame[3])
                    .get();
            encoder.writeToBuffer(uniforms.slice(0L, UBO_SIZE), data);
        }

        try (RenderPass pass = encoder.createRenderPass(() -> "SpaceHorizons planet mesh",
                main.getColorTextureView(), Optional.empty(), depth.getDepthTextureView(), OptionalDouble.of(0.0))) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("Fog", fog);
            pass.setPipeline(pipeline);
            pass.setUniform(PlanetMeshPipelines.INFO_UNIFORM, uniforms.slice(0L, UBO_SIZE));
            pass.setVertexBuffer(0, current.vertices().slice());
            pass.setIndexBuffer(indexBuffer, IndexType.INT);
            pass.drawIndexed(current.indexCount(), 1, 0, 0, 0);
        }
    }

    private TextureTarget ensureDepthTarget(int width, int height) {
        TextureTarget target = depthTarget;
        if (target == null) {
            target = new TextureTarget("SpaceHorizons planet mesh", width, height, null, GpuFormat.D32_FLOAT);
            depthTarget = target;
        } else if (target.width != width || target.height != height) {
            target.resize(width, height);
        }
        return target;
    }

    private GpuBuffer ensureUbo() {
        GpuBuffer buffer = ubo;
        if (buffer == null) {
            buffer = RenderSystem.getDevice().createBuffer(() -> "SpaceHorizons planet mesh UBO",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UBO_SIZE);
            ubo = buffer;
        }
        return buffer;
    }

    // ---- buffers substituídos ----

    private void retire(GpuBuffer buffer) {
        retired.add(new Retired(buffer, frame));
    }

    private void closeRetired() {
        while (!retired.isEmpty() && frame - retired.peek().frame() >= RETIRE_FRAMES) {
            retired.poll().buffer().close();
        }
    }
}
