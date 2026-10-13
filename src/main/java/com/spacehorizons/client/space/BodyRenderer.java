package com.spacehorizons.client.space;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.atmosphere.AtmosphereClient;
import com.spacehorizons.client.atmosphere.AtmosphereModel;
import com.spacehorizons.client.planetmesh.PlanetMeshProjection;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceFrame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.resources.Identifier;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Desenha os outros corpos do SolarSystem como esferas reais no céu de qualquer planeta (a Lua vista da Terra, a
 * Terra com halo vista da Lua), como "fundo" do frame: depois do céu e ANTES da malha orbital, do terreno e do DH
 * (o listener roda com prioridade alta), então o chão do planeta atual cobre o que estiver atrás dele.
 *
 * Posição e orientação: os corpos se movem (a Lua orbita e gira sincronizada), então tudo é calculado no instante do
 * relógio do Overworld com a parte fracionária do frame (SpaceSun.clock), que dá movimento suave entre ticks. O vetor do
 * centro do outro corpo à câmera, no espaço (SpaceFrame.toSpace), vira os eixos locais da câmera com
 * SpaceFrame.spaceToLocal. A esfera é fixa no corpo (BodyMeshCache) e uma matriz de modelo faz o resto: rotação dos
 * eixos do corpo para os locais da câmera (inclui a orientação dos dois corpos naquele instante, SpaceFrame.meshToLocal),
 * escala do scaled space e translação até o centro. O shader é o do terreno orbital (ver BodySky, "por que não há salto").
 *
 * Roda no render thread; a geração das esferas é assíncrona (BodyMeshCache). Qualquer falha de render desliga o
 * módulo em vez de derrubar o jogo.
 */
final class BodyRenderer {
    /** Planos próprios: os corpos estão a milhares de blocos. */
    static final float NEAR_PLANE = 16.0F;
    static final float FAR_PLANE = 1_000_000.0F;
    /** Luz ambiente do lado noturno (a mesma da malha orbital). */
    static final float NIGHT_AMBIENT = 0.07F;
    /** Disco menor que isto (em pixels de raio) não vale um draw. */
    static final double MIN_PIXEL_RADIUS = 0.35;
    /** No máximo isto de corpos por frame (um anel de buffers e um halo para cada). */
    static final int MAX_BODIES = 3;
    /** Intensidade do halo de um corpo visto de fora (só o limbo; o disco fica sem véu). */
    private static final double FAR_HALO_VISIBILITY = 1.0;

    private static final int UBO_SIZE = new Std140SizeCalculator().putMat4f().putMat4f().putVec4().putVec4().get();

    static final BodyRenderer INSTANCE = new BodyRenderer();

    /** Um corpo a desenhar neste frame. */
    private record Candidate(SolarSystem.Body body, BodySky.Apparent apparent, boolean self) {
    }

    private final BodyMeshCache cache = new BodyMeshCache();
    private final MappableRingBuffer[] ubos = new MappableRingBuffer[MAX_BODIES];
    private final Map<Identifier, Integer> lastSegments = new HashMap<>();
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f model = new Matrix4f();
    private final Matrix4f rotation = new Matrix4f();
    private final Matrix4f mvp = new Matrix4f();
    private final double[] meshBasis = new double[9];

    private @Nullable TextureTarget depthTarget;
    private boolean disabled;
    private boolean warnedPipeline;

    private BodyRenderer() {
    }

    /** Sai do mundo: libera tudo (o cache de esferas e o alvo de profundidade). */
    void reset() {
        cache.reset();
        lastSegments.clear();
        if (depthTarget != null) {
            depthTarget.destroyBuffers();
            depthTarget = null;
        }
        for (int i = 0; i < ubos.length; i++) {
            if (ubos[i] != null) {
                ubos[i].close();
                ubos[i] = null;
            }
        }
    }

    void render(LevelRenderState state, Matrix4fc viewMatrix) {
        if (disabled) {
            return;
        }
        try {
            renderInternal(state, viewMatrix);
        } catch (Throwable t) {
            disabled = true;
            SpaceHorizons.LOGGER.error("Corpos no céu desligados por erro de renderização", t);
        }
    }

    private void renderInternal(LevelRenderState state, Matrix4fc viewMatrix) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Optional<PlanetDefinition> found = Planets.forLevel(level);
        if (found.isEmpty()) {
            return;
        }
        PlanetDefinition planet = found.get();
        Optional<SolarSystem.Body> currentBody = SolarSystem.bodyOf(planet);
        if (currentBody.isEmpty()) {
            return;
        }
        SolarSystem.Body current = currentBody.get();
        CameraRenderState camera = state.cameraRenderState;
        float m00 = camera.projectionMatrix.m00();
        float m11 = camera.projectionMatrix.m11();
        RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
        if (m00 == 0.0F || m11 == 0.0F || main.getColorTextureView() == null) {
            return;
        }
        double camX = camera.pos.x;
        double camY = camera.pos.y;
        double camZ = camera.pos.z;
        double pixelAngle = 2.0 / m11 / Math.max(1, main.height);

        // os corpos se movem: tudo neste frame é do mesmo instante (relógio do Overworld + parcial do frame)
        double clock = SpaceSun.clock(level);
        cache.uploadReady();
        prefetch(current, clock, pixelAngle);

        Vector3f forward = camera.viewRotationMatrix.getRow(2, new Vector3f()).negate();
        double halfDiagonal = Math.atan(Math.hypot(1.0 / m00, 1.0 / m11));
        List<Candidate> visible = new ArrayList<>();
        for (SolarSystem.Body body : SolarSystem.BODIES) {
            // o próprio planeta só entra de longe: no primeiro quadro depois de uma troca de dimensão a malha orbital
            // do nível novo ainda está sendo construída, e a esfera (mesma geometria, cor e luz) cobre esse vão
            if (body == current && camY - current.planet().seaLevel() < PlanetMeshProjection.altitudeStart(current.planet().radius())) {
                continue;
            }
            BodySky.Apparent apparent = BodySky.fromDimension(current, clock, camX, camY, camZ, body);
            if (apparent.distance() <= body.planet().radius()) {
                continue; // câmera dentro do corpo
            }
            if (apparent.angularRadius() < MIN_PIXEL_RADIUS * pixelAngle) {
                continue;
            }
            if (!BodySky.inFrustum(apparent.x(), apparent.y(), apparent.z(), forward.x, forward.y, forward.z,
                    halfDiagonal, apparent.angularRadius())) {
                continue;
            }
            visible.add(new Candidate(body, apparent, body == current));
        }
        if (visible.isEmpty()) {
            return;
        }
        // de longe para perto: o mais próximo fica por cima
        visible.sort(Comparator.comparingDouble((Candidate c) -> c.apparent().distance()).reversed());

        CompiledRenderPipeline pipeline = RenderSystem.getCompiledPipelineNullable(BodyPipelines.BODY);
        if (pipeline == null) {
            if (!warnedPipeline) {
                warnedPipeline = true;
                SpaceHorizons.LOGGER.warn("Pipeline dos corpos no céu não compilou; eles não serão desenhados");
            }
            return;
        }

        // projeção com o mesmo FOV e proporção do vanilla, mas near/far próprios; invertida como a do vanilla
        float fovY = 2.0F * (float) Math.atan(1.0F / m11);
        boolean zZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        projection.setPerspective(fovY, m11 / m00, FAR_PLANE, NEAR_PLANE, zZeroToOne);

        // sol único (SunModel) no ponto da câmera, nos eixos do mundo: a mesma direção do disco, do céu e da luz do chão
        float[] sun = new float[3];
        SpaceSun.localAt(minecraft.level, planet, camX, camZ, sun);
        float sunX = sun[0];
        float sunY = sun[1];
        float sunZ = sun[2];
        AtmosphereModel here = AtmosphereModel.of(planet);
        double fade = BodySky.daylightFade(here.skyBrightness(camY - planet.seaLevel()), sunY);
        Matrix4f invView = new Matrix4f(viewMatrix).invert();
        TextureTarget depth = ensureDepthTarget(main.width, main.height);

        int slot = 0;
        for (Candidate candidate : visible) {
            if (slot >= MAX_BODIES) {
                break;
            }
            if (draw(candidate, slot, pipeline, main, depth, camera, invView, sunX, sunY, sunZ, (float) fade, pixelAngle, current,
                    clock, camX, camZ)) {
                slot++;
            }
        }
    }

    /**
     * Antecipa a esfera do corpo ATUAL: quando o jogador trocar de dimensão, o planeta de onde veio passa a ser
     * "outro corpo" e a esfera dele precisa estar pronta no mesmo frame. A resolução é a que ele terá visto do
     * corpo mais próximo.
     */
    private void prefetch(SolarSystem.Body current, double clock, double pixelAngle) {
        double nearest = Double.MAX_VALUE;
        SolarSystem.Pose here = current.poseAt(clock);
        for (SolarSystem.Body other : SolarSystem.BODIES) {
            if (other != current) {
                SolarSystem.Pose there = other.poseAt(clock);
                nearest = Math.min(nearest, Math.hypot(Math.hypot(there.x() - here.x(), there.y() - here.y()), there.z() - here.z()));
            }
        }
        if (nearest == Double.MAX_VALUE) {
            return;
        }
        double required = BodySky.requiredSegments(current.planet().radius(), nearest, pixelAngle);
        cache.ensure(current.planet(), BodySky.chooseSegments(0, required));
    }

    private boolean draw(Candidate candidate, int slot, CompiledRenderPipeline pipeline, RenderTarget main, TextureTarget depth,
            CameraRenderState camera, Matrix4fc invView, float sunX, float sunY, float sunZ, float fade, double pixelAngle,
            SolarSystem.Body observer, double clock, double camX, double camZ) {
        PlanetDefinition planet = candidate.body().planet();
        BodySky.Apparent a = candidate.apparent();

        double required = BodySky.requiredSegments(planet.radius(), a.distance(), pixelAngle);
        int segments = BodySky.chooseSegments(lastSegments.getOrDefault(planet.id(), 0), required);
        lastSegments.put(planet.id(), segments);
        cache.ensure(planet, segments);
        BodyMeshCache.Mesh mesh = cache.best(planet, segments);
        if (mesh == null) {
            return false;
        }

        // scaled space: além de SCALED_SPACE_DISTANCE desenha-se a essa distância, com o raio escalado igual
        double scale = BodySky.scaleFactor(a.distance(), BodySky.SCALED_SPACE_DISTANCE);
        double centerX = a.x() * a.distance() * scale;
        double centerY = a.y() * a.distance() * scale;
        double centerZ = a.z() * a.distance() * scale;
        // eixos do corpo (a malha é desenhada nos eixos fixos no corpo) -> eixos locais da câmera, no instante do frame
        SpaceFrame.meshToLocal(observer, clock, camX, camZ, candidate.body(), meshBasis);
        rotation.set((float) meshBasis[0], (float) meshBasis[3], (float) meshBasis[6], 0.0F,
                (float) meshBasis[1], (float) meshBasis[4], (float) meshBasis[7], 0.0F,
                (float) meshBasis[2], (float) meshBasis[5], (float) meshBasis[8], 0.0F,
                0.0F, 0.0F, 0.0F, 1.0F);
        model.translation((float) centerX, (float) centerY, (float) centerZ).scale((float) scale).mul(rotation);
        mvp.set(projection).mul(camera.viewRotationMatrix).mul(model);

        MappableRingBuffer ubo = ubos[slot];
        if (ubo == null) {
            ubo = new MappableRingBuffer(() -> "SpaceHorizons body UBO " + slot, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UBO_SIZE);
            ubos[slot] = ubo;
        }
        try (GpuBufferSlice.MappedView view = ubo.currentBuffer().map(false, true)) {
            Std140Builder.intoBuffer(view.data())
                    .putMat4f(mvp)
                    .putMat4f(model)
                    .putVec4(sunX, sunY, sunZ, NIGHT_AMBIENT)
                    .putVec4(fade, 0.0F, 0.0F, 0.0F);
        }

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "SpaceHorizons body " + planet.id(), main.getColorTextureView(), Optional.empty(),
                depth.getDepthTextureView(), OptionalDouble.of(0.0))) {
            pass.pushDebugGroup(() -> "SpaceHorizons body");
            pass.setPipeline(pipeline);
            pass.setUniform(BodyPipelines.INFO_UNIFORM, ubo.currentBuffer());
            pass.setVertexBuffer(0, mesh.vertices().slice());
            pass.setIndexBuffer(mesh.indices(), IndexType.INT);
            pass.drawIndexed(mesh.indexCount(), 1, 0, 0, 0);
            pass.popDebugGroup();
        }
        ubo.rotate();

        // halo de atmosfera visto de fora: o mesmo shader do limbo, com centro, raio e espessura na escala do desenho
        AtmosphereModel atmosphere = AtmosphereModel.of(planet);
        if (atmosphere.present() && slot < AtmosphereClient.MAX_HALOS && !candidate.self()) {
            AtmosphereClient.drawHalo(slot, invView, centerX, centerY, centerZ, planet.radius() * scale,
                    atmosphere.thickness() * scale, atmosphere.baseAltitude() * scale, sunX, sunY, sunZ, FAR_HALO_VISIBILITY);
        }
        return true;
    }

    private TextureTarget ensureDepthTarget(int width, int height) {
        TextureTarget target = depthTarget;
        if (target == null) {
            target = new TextureTarget("SpaceHorizons bodies", width, height, null, GpuFormat.D32_FLOAT);
            depthTarget = target;
        } else if (target.width != width || target.height != height) {
            target.resize(width, height);
        }
        return target;
    }
}
