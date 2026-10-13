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
import com.spacehorizons.client.planetmesh.dh.DhRange;
import com.spacehorizons.planet.PlanetDefinition;
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
    /** Segmentos de azimute por anel. Mais segmentos = mais detalhe longe, e o custo de reconstrução cresce junto. */
    static final int SEGMENTS = 384;
    static final int MAX_RINGS = 320;
    /** Menor raio interno aceito (blocos), mesmo com render distance minúscula. */
    static final double MIN_INNER_RADIUS = 128.0;
    // Acima disso a malha usa o raio interno mínimo e cobre tudo. O DH e os chunks desenham depois e sobrescrevem
    // a malha, então o único custo é mais triângulos; assim não há altitude em que reapareça o buraco no centro.
    static final double DH_BLIND_ALTITUDE = 2000.0;
    /** Sobreposição com o alcance do DH (blocos): a malha começa um pouco antes para não abrir fresta. */
    static final double MAX_OVERLAP = 256.0;
    /** Altura de montanha usada para decidir se a malha pode aparecer atrás do horizonte do nível do mar. */
    static final double HORIZON_MARGIN_HEIGHT = 300.0;
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

    private static final int UBO_SIZE = new Std140SizeCalculator().putMat4f().putVec4().putVec4().putVec4().get();

    /** Malha já na GPU, com a câmera de construção para compensar o movimento desde então. */
    private record Mesh(PlanetDefinition planet, GpuBuffer vertices, int indexCount,
            double camX, double camY, double camZ, double innerRadius) {
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
    private int indexRings;
    private @Nullable Mesh mesh;
    private boolean disabled;
    private boolean warnedPipeline;
    private long frame;
    private long lastBuildStart;
    private int dhRangeCache = -1;
    private int dhPollCountdown;

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

        double outer = PlanetMeshGeometry.outerRadius(planet);
        double inner = altitude > DH_BLIND_ALTITUDE ? MIN_INNER_RADIUS : innerRadius(minecraft);
        if (inner >= outer * 0.9) {
            return;
        }
        // Com a câmera baixa tudo além do alcance está atrás do horizonte: nem constrói nem desenha
        if (horizonDistance(planet.radius(), altitude) <= inner) {
            return;
        }

        uploadReady();
        maybeRebuild(planet, camX, camY, camZ, inner, outer);

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

    private double innerRadius(Minecraft minecraft) {
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
        double overlap = Math.min(MAX_OVERLAP, range * 0.25);
        // múltiplo de 16 para que o raio interno não oscile à toa e dispare reconstruções
        return Math.max(MIN_INNER_RADIUS, Math.floor((range - overlap) / 16.0) * 16.0);
    }

    /** Distância (arco) até o horizonte de uma câmera na altitude dada, mais o horizonte de uma montanha de referência. */
    static double horizonDistance(double radius, double altitude) {
        double fromCamera = Math.acos(radius / (radius + Math.max(0.0, altitude)));
        double fromPeak = Math.acos(radius / (radius + HORIZON_MARGIN_HEIGHT));
        return radius * (fromCamera + fromPeak);
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
            if (oldIndices == null || indexRings != layout.rings() || oldIndices.size() != built.indices().remaining()) {
                if (oldIndices != null) {
                    retire(oldIndices);
                }
                indexBuffer = device.createBuffer(() -> "SpaceHorizons planet mesh indices", GpuBuffer.USAGE_INDEX, built.indices());
                indexRings = layout.rings();
            }
            Mesh old = mesh;
            if (old != null) {
                retire(old.vertices());
            }
            mesh = new Mesh(request.planet(), vertices, layout.indexCount(),
                    request.camX(), request.camY(), request.camZ(), layout.rMin());
        } finally {
            builder.release();
        }
    }

    private void maybeRebuild(PlanetDefinition planet, double camX, double camY, double camZ, double inner, double outer) {
        if (!builder.isIdle() || System.nanoTime() - lastBuildStart < MIN_REBUILD_NANOS) {
            return;
        }
        Mesh current = mesh;
        if (current != null && current.planet() == planet) {
            double spacing = current.innerRadius() * (2.0 * Math.PI / SEGMENTS);
            double moveThreshold = Math.max(MOVE_MIN, Math.min(MOVE_MAX, spacing * MOVE_FRACTION));
            boolean moved = Math.hypot(camX - current.camX(), camZ - current.camZ()) > moveThreshold;
            double builtAlt = current.camY() - planet.seaLevel();
            double alt = camY - planet.seaLevel();
            boolean altitudeChanged = Math.abs(alt - builtAlt) > Math.max(ALTITUDE_MIN, ALTITUDE_FRACTION * Math.abs(builtAlt));
            boolean innerChanged = Math.abs(inner - current.innerRadius()) > 0.05 * inner + 32.0;
            if (!moved && !altitudeChanged && !innerChanged) {
                return;
            }
        }
        PlanetMeshGeometry.Palette colors = palette;
        if (colors == null) {
            colors = PlanetMeshPalette.create();
            palette = colors;
        }
        PlanetMeshGeometry.Layout layout = PlanetMeshGeometry.Layout.create(inner, outer, SEGMENTS, MAX_RINGS);
        lastBuildStart = System.nanoTime();
        builder.submit(new PlanetMeshBuilder.Request(planet, camX, camY, camZ, layout, colors));
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

        // direção do sol no referencial do mundo: o céu gira (0,1,0) por Y(-90°) e depois X(sunAngle) => (-sin a, cos a, 0)
        float sunAngle = state.skyRenderState.sunAngle;
        float sunX = -(float) Math.sin(sunAngle);
        float sunY = (float) Math.cos(sunAngle);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuBuffer uniforms = ensureUbo();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, UBO_SIZE)
                    .putMat4f(mvp)
                    .putVec4(offX, offY, offZ, 0.0F)
                    .putVec4(sunX, sunY, 0.0F, NIGHT_AMBIENT)
                    .putVec4(centerX, centerY, centerZ, (float) radius)
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
