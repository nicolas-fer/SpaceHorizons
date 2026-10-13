package com.spacehorizons.client.atmosphere;

import java.util.Optional;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.spacehorizons.SpaceHorizons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import org.joml.Matrix4f;

/**
 * Brilho da atmosfera (limbo): um triângulo de tela cheia, depois do céu, que para cada pixel calcula
 * a interseção do raio da câmera com a casca da atmosfera (centro em (0, -(R + altitude), 0) relativo
 * à câmera, o mesmo mapeamento da curvatura) e soma um espalhamento de Rayleigh aproximado.
 *
 * Desenha logo depois do céu e antes do terreno: o planeta (chunks, LODs do DH, malha orbital)
 * cobre o que está na frente, e o halo fica visível em volta da silhueta.
 */
final class AtmosphereLimbRenderer {
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "atmosphere/limb");
    /** Abaixo disso o brilho é invisível e o pass inteiro é pulado. */
    private static final double MIN_VISIBILITY = 0.003;
    private static final int UBO_SIZE = new Std140SizeCalculator().putVec4().putVec4().putMat4f().get();
    /** Mesma cor do vanilla (OVERLAY) no destino, mas sem alterar o alpha do alvo. */
    private static final BlendFunction ADD_KEEP_ALPHA =
            new BlendFunction(BlendFactor.ONE, BlendFactor.ONE, BlendFactor.ZERO, BlendFactor.ONE);

    /** Criado só quando o evento de registro dispara (depois dos pipelines do vanilla). */
    private static RenderPipeline pipeline;
    private static MappableRingBuffer ubo;

    private AtmosphereLimbRenderer() {
    }

    static void onRegisterPipelines(RegisterRenderPipelinesEvent event) {
        pipeline = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "pipeline/atmosphere_limb"))
                .withVertexShader(SHADER)
                .withFragmentShader(SHADER)
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("Projection", UniformType.UNIFORM_BUFFER).build())
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("AtmosphereInfo", UniformType.UNIFORM_BUFFER).build())
                .withColorTargetState(new ColorTargetState(ADD_KEEP_ALPHA))
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .build();
        event.registerPipeline(pipeline);
    }

    static void onAfterSky(RenderLevelStageEvent.AfterSky event) {
        if (pipeline == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LevelRenderState levelState = event.getLevelRenderState();
        CameraRenderState camera = levelState.cameraRenderState;
        if (level == null || (camera.fogType != FogType.NONE && camera.fogType != FogType.ATMOSPHERIC)) {
            return;
        }
        Optional<AtmosphereSample> sample = AtmosphereSample.of(level, camera.pos.y);
        if (sample.isEmpty()) {
            return;
        }
        AtmosphereModel model = sample.get().model();
        double altitude = sample.get().altitude();
        double visibility = model.limbVisibility(altitude);
        GpuBufferSlice projection = RenderSystem.getProjectionMatrixBuffer();
        GpuTextureView target = minecraft.gameRenderer.mainRenderTarget().getColorTextureView();
        if (visibility < MIN_VISIBILITY || projection == null || target == null) {
            return;
        }

        if (ubo == null) {
            ubo = new MappableRingBuffer(() -> "Atmosphere limb UBO", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UBO_SIZE);
        }
        // mesma convenção do SkyRenderer: sol em Ry(-90°) * Rx(sunAngle) * (0, 1, 0) = (-sin, cos, 0)
        float sunAngle = levelState.skyRenderState.sunAngle;
        Matrix4f invView = new Matrix4f(event.getModelViewMatrix()).invert();
        try (GpuBufferSlice.MappedView view = ubo.currentBuffer().map(false, true)) {
            Std140Builder.intoBuffer(view.data())
                    .putVec4((float) sample.get().planet().radius(), (float) altitude, (float) model.thickness(), (float) model.baseAltitude())
                    .putVec4(-(float) Math.sin(sunAngle), (float) Math.cos(sunAngle), 0.0F, (float) visibility)
                    .putMat4f(invView);
        }

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "Atmosphere limb", target, Optional.empty())) {
            pass.pushDebugGroup(() -> "Atmosphere limb");
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            pass.setUniform("Projection", projection);
            pass.setUniform("AtmosphereInfo", ubo.currentBuffer());
            pass.draw(3, 1, 0, 0);
            pass.popDebugGroup();
        }
        ubo.rotate();
    }
}
