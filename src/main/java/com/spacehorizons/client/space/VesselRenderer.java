package com.spacehorizons.client.space;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.ship.ShipYard;
import com.spacehorizons.space.SpaceDimension;
import com.spacehorizons.space.vessel.VesselEntry;
import com.spacehorizons.space.vessel.VesselPose;
import com.spacehorizons.space.vessel.VesselSelector;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Desenha as OUTRAS naves e foguetes no céu de quem está no espaço, num foguete estacionado ou dentro de uma nave do estaleiro
 * (docs/PLANO_NAVES.md, 4.3; fase 2d), depois dos corpos ({@link BodyRenderer}) e antes do terreno: o casco da nave de quem olha (os blocos do estaleiro) cobre o
 * que estiver atrás, como a Terra e a Lua.
 *
 * <p>Cada veículo vem do {@link VesselClient} com posição absoluta em E e atitude local extrapoladas para o quadro; o {@link VesselPose} leva isso aos eixos
 * locais do observador ({@link SkyObserver}: com a atitude da nave de quem olha) em double, relativo ao observador, e só então vira float na matriz de
 * modelo: coordenadas grandes nunca chegam à GPU (sem tremor). O casco é o esparso do servidor (cor por bloco, luz simples do sol do shader
 * {@code space/vessel}); sem ele, ou longe demais, é uma caixa do tamanho do veículo, ampliada se preciso para nunca ficar menor que
 * {@link #MIN_PIXELS} pixels (o impostor). A câmera dentro da nave não tem paralaxe: o céu inteiro é visto do ponto virtual da nave.
 *
 * <p>Roda no render thread. Qualquer falha de render desliga o módulo em vez de derrubar o jogo.
 */
public final class VesselRenderer {
    static final VesselRenderer INSTANCE = new VesselRenderer();

    /** Planos próprios: de poucos blocos a 1 milhão. */
    static final float NEAR_PLANE = 0.5F;
    static final float FAR_PLANE = 1_000_000.0F;
    /** Luz do lado que o sol não ilumina. */
    static final float AMBIENT = 0.22F;
    /** Um veículo nunca é desenhado menor que isto (em pixels de diâmetro): o impostor. */
    static final double MIN_PIXELS = 2.0;
    /** No máximo isto de veículos por frame (um buffer de uniformes por um). */
    static final int MAX_VESSELS = VesselSelector.MAX_VESSELS;

    private static final int UBO_SIZE = new Std140SizeCalculator().putMat4f().putMat4f().putVec4().putVec4().get();

    /** Desenhar os veículos (o autoteste desliga para tirar o quadro de referência sem eles). */
    private static volatile boolean enabled = true;
    private static volatile int drawnLastFrame;
    private static volatile float lastM00;
    private static volatile float lastM11;
    private static volatile List<Seen> lastSeen = List.of();

    /** O que o último quadro viu de cada veículo (para o autoteste): distância ao pivô e vetor ao pivô nos eixos locais do observador. */
    public record Seen(java.util.UUID id, double distance, double[] center, boolean hull, boolean drawn) {
    }

    /** Um veículo a desenhar neste quadro. */
    private record Candidate(VesselClient.Posed posed, double[] center, double[] rotation, double distance, double angularRadius) {
    }

    private final MappableRingBuffer[] ubos = new MappableRingBuffer[MAX_VESSELS];
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f model = new Matrix4f();
    private final Matrix4f rotation = new Matrix4f();
    private final Matrix4f mvp = new Matrix4f();
    private @Nullable TextureTarget depthTarget;
    private boolean disabled;
    private boolean warnedPipeline;

    private VesselRenderer() {
    }

    /** Liga e desliga o desenho dos veículos (só o autoteste usa: o quadro de referência sem eles). */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /** Os termos m00 e m11 da projeção do último quadro com veículos (2D: x e y da projeção em perspectiva). */
    public static float[] lastProjection() {
        return new float[] {lastM00, lastM11};
    }

    public static List<Seen> lastSeen() {
        return lastSeen;
    }

    /** Quantos veículos foram desenhados no último quadro (para o autoteste). */
    public static int drawnLastFrame() {
        return drawnLastFrame;
    }

    void reset() {
        VesselClient.reset();
        if (this.depthTarget != null) {
            this.depthTarget.destroyBuffers();
            this.depthTarget = null;
        }
        for (int i = 0; i < this.ubos.length; i++) {
            if (this.ubos[i] != null) {
                this.ubos[i].close();
                this.ubos[i] = null;
            }
        }
    }

    void render(LevelRenderState state, Matrix4fc viewMatrix) {
        drawnLastFrame = 0;
        if (this.disabled || !enabled) {
            return;
        }
        try {
            renderInternal(state);
        } catch (Throwable t) {
            this.disabled = true;
            SpaceHorizons.LOGGER.error("Outras naves e foguetes no céu desligados por erro de renderização", t);
        }
    }

    private void renderInternal(LevelRenderState state) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !(SpaceDimension.is(level) || ShipYard.is(level)) || !VesselClient.active()) {
            return;
        }
        CameraRenderState camera = state.cameraRenderState;
        float m00 = camera.projectionMatrix.m00();
        float m11 = camera.projectionMatrix.m11();
        RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
        if (m00 == 0.0F || m11 == 0.0F || main.getColorTextureView() == null) {
            return;
        }
        lastM00 = m00;
        lastM11 = m11;
        double clock = SpaceSun.clock(level);
        SkyObserver observer = SkyObserver.of(level, camera.pos.x, camera.pos.y, camera.pos.z, clock).orElse(null);
        if (observer == null) {
            return;
        }
        double pixelAngle = 2.0 / m11 / Math.max(1, main.height);
        Vector3f forward = camera.viewRotationMatrix.getRow(2, new Vector3f()).negate();
        double halfDiagonal = Math.atan(Math.hypot(1.0 / m00, 1.0 / m11));

        List<Candidate> visible = new ArrayList<>();
        List<Seen> seen = new ArrayList<>();
        double[] center = new double[3];
        for (VesselClient.Posed posed : VesselClient.posed(System.nanoTime())) {
            double[] rotation = new double[9];
            VesselPose.relative(observer.pos(), observer.localToSpace(), posed.pos(), VesselPose.modelToSpace(posed.attitude()), center, rotation);
            double distance = Math.sqrt(center[0] * center[0] + center[1] * center[1] + center[2] * center[2]);
            double radius = posed.entry().radius();
            if (VesselSelector.wantsHull(distance, radius)) {
                VesselClient.wantHull(posed.entry().id(), posed.entry().hullVersion());
            }
            seen.add(new Seen(posed.entry().id(), distance, center.clone(), VesselClient.hullFor(posed.entry().id(), posed.entry().hullVersion()) != null, false));
            if (distance <= 1.0) {
                continue; // dentro do veículo
            }
            double angularRadius = VesselPose.angularRadius(radius, distance);
            if (!BodySky.inFrustum(center[0] / distance, center[1] / distance, center[2] / distance, forward.x, forward.y, forward.z, halfDiagonal, angularRadius)) {
                continue;
            }
            visible.add(new Candidate(posed, center.clone(), rotation, distance, angularRadius));
        }
        lastSeen = seen;
        if (visible.isEmpty()) {
            return;
        }
        // de longe para perto: o mais próximo fica por cima (a profundidade é do veículo, entre veículos também)
        visible.sort(Comparator.comparingDouble((Candidate c) -> c.distance()).reversed());

        CompiledRenderPipeline pipeline = RenderSystem.getCompiledPipelineNullable(BodyPipelines.VESSEL);
        if (pipeline == null) {
            if (!this.warnedPipeline) {
                this.warnedPipeline = true;
                SpaceHorizons.LOGGER.warn("Pipeline dos veículos no céu não compilou; eles não serão desenhados");
            }
            return;
        }
        float fovY = 2.0F * (float) Math.atan(1.0F / m11);
        boolean zZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        this.projection.setPerspective(fovY, m11 / m00, FAR_PLANE, NEAR_PLANE, zZeroToOne);
        double[] sun = new double[3];
        observer.sun(sun);
        TextureTarget depth = ensureDepthTarget(main.width, main.height);

        int slot = 0;
        for (Candidate candidate : visible) {
            if (slot >= MAX_VESSELS) {
                break;
            }
            if (draw(candidate, slot, slot == 0, pipeline, main, depth, camera, sun, pixelAngle)) {
                slot++;
            }
        }
        drawnLastFrame = slot;
    }

    private boolean draw(Candidate c, int slot, boolean first, CompiledRenderPipeline pipeline, RenderTarget main, TextureTarget depth, CameraRenderState camera,
            double[] sun, double pixelAngle) {
        VesselEntry entry = c.posed().entry();
        GpuBuffer vertices;
        int vertexCount;
        float scale = 1.0F;
        VesselClient.Hull hull = VesselClient.hullFor(entry.id(), entry.hullVersion());
        if (hull != null && VesselClient.upload(hull)) {
            vertices = hull.gpu;
            vertexCount = hull.vertexCount;
        } else {
            vertices = VesselClient.impostor(entry.kind());
            vertexCount = VesselClient.impostorVertices();
            scale = (float) (2.0 * entry.radius() / Math.sqrt(3.0));      // o cubo cabe na esfera do veículo
        }
        // o impostor: nunca menor que MIN_PIXELS de diâmetro
        double angularDiameter = 2.0 * c.angularRadius();
        double minimum = MIN_PIXELS * pixelAngle;
        if (angularDiameter < minimum) {
            scale *= (float) Math.min(200.0, minimum / angularDiameter);
        }
        double[] r = c.rotation();
        this.rotation.set((float) r[0], (float) r[3], (float) r[6], 0.0F, (float) r[1], (float) r[4], (float) r[7], 0.0F, (float) r[2], (float) r[5], (float) r[8],
                0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
        this.model.translation((float) c.center()[0], (float) c.center()[1], (float) c.center()[2]).mul(this.rotation).scale(scale);
        this.mvp.set(this.projection).mul(camera.viewRotationMatrix).mul(this.model);

        MappableRingBuffer ubo = this.ubos[slot];
        if (ubo == null) {
            ubo = new MappableRingBuffer(() -> "SpaceHorizons vessel UBO " + slot, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UBO_SIZE);
            this.ubos[slot] = ubo;
        }
        try (GpuBufferSlice.MappedView view = ubo.currentBuffer().map(false, true)) {
            Std140Builder.intoBuffer(view.data())
                    .putMat4f(this.mvp)
                    .putMat4f(this.model)
                    .putVec4((float) sun[0], (float) sun[1], (float) sun[2], AMBIENT)
                    .putVec4(1.0F, 0.0F, 0.0F, 0.0F);
        }
        // o primeiro veículo limpa a profundidade; os outros a carregam (todos os veículos se ocultam entre si)
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "SpaceHorizons vessel " + entry.id(), main.getColorTextureView(), Optional.empty(), depth.getDepthTextureView(),
                first ? OptionalDouble.of(0.0) : OptionalDouble.empty())) {
            pass.pushDebugGroup(() -> "SpaceHorizons vessel");
            pass.setPipeline(pipeline);
            pass.setUniform(BodyPipelines.INFO_UNIFORM, ubo.currentBuffer());
            pass.setVertexBuffer(0, vertices.slice());
            pass.draw(vertexCount, 1, 0, 0);
            pass.popDebugGroup();
        }
        ubo.rotate();
        return true;
    }

    private TextureTarget ensureDepthTarget(int width, int height) {
        TextureTarget target = this.depthTarget;
        if (target == null) {
            target = new TextureTarget("SpaceHorizons vessels", width, height, null, GpuFormat.D32_FLOAT);
            this.depthTarget = target;
        } else if (target.width != width || target.height != height) {
            target.resize(width, height);
        }
        return target;
    }
}
