package com.spacehorizons.client.rocket.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.spacehorizons.rocket.blueprint.Blueprint;
import com.spacehorizons.rocket.blueprint.LaunchPadBlockEntity;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * O holograma da planta ativa numa plataforma de lançamento (docs/PLANO_TIERS.md, seção 4): fantasmas translúcidos onde falta bloco,
 * vermelho onde o bloco está errado, nada onde está certo. A malha é de toda a planta, assada uma vez ({@link GhostMesh}); por quadro só se
 * reproduzem os intervalos dos blocos que faltam ou estão errados, segundo a comparação do {@link BlueprintHologram} (refeita a cada 10 ticks).
 * Fica no pipeline vanilla (translúcido de bloco, neblina, curvatura do mod), pelo mesmo {@code submitCustomGeometry} do retrato do foguete.
 *
 * <p>Plataformas sem planta (a imensa maioria) saem no {@link #shouldRender}, sem trabalho. Alcance: {@link #RANGE} blocos da caixa da planta.
 */
public class LaunchPadRenderer implements BlockEntityRenderer<LaunchPadBlockEntity, LaunchPadRenderer.RenderState> {
    /** Distância máxima (blocos) da câmera à caixa da planta para desenhar o holograma. */
    public static final int RANGE = 96;

    public static final class RenderState extends BlockEntityRenderState {
        @Nullable GhostMesh mesh;
        byte @Nullable [] status;
    }

    public LaunchPadRenderer(BlockEntityRendererProvider.Context context) {
        BlueprintHologram.clearMeshes(); // o dispatcher recria os renderers a cada recarga de recursos: UVs do atlas antigo não valem mais
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(LaunchPadBlockEntity pad, RenderState state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(pad, state, partialTicks, cameraPosition, breakProgress);
        Blueprint blueprint = pad.blueprint();
        state.mesh = null;
        state.status = null;
        if (blueprint == null || pad.getLevel() == null) {
            return;
        }
        GhostMesh mesh = BlueprintHologram.mesh(blueprint);
        if (mesh == null) {
            return;
        }
        var progress = BlueprintHologram.state(pad.getLevel(), pad.getBlockPos(), blueprint).progress();
        if (progress != null && progress.toDo() > 0) {
            state.mesh = mesh;
            state.status = progress.status();
        }
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        GhostMesh mesh = state.mesh;
        byte[] status = state.status;
        if (mesh == null || status == null) {
            return;
        }
        // poseStack já está na plataforma-âncora (posição do block entity - câmera); a malha está em coordenadas relativas a ela
        collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, buffer) -> mesh.replay(pose, buffer, status));
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return RANGE;
    }

    @Override
    public boolean shouldRender(LaunchPadBlockEntity pad, Vec3 cameraPosition) {
        Blueprint blueprint = pad.blueprint();
        if (blueprint == null) {
            return false;
        }
        AABB box = this.getRenderBoundingBox(pad);
        double dx = Math.max(0.0, Math.max(box.minX - cameraPosition.x, cameraPosition.x - box.maxX));
        double dy = Math.max(0.0, Math.max(box.minY - cameraPosition.y, cameraPosition.y - box.maxY));
        double dz = Math.max(0.0, Math.max(box.minZ - cameraPosition.z, cameraPosition.z - box.maxZ));
        return dx * dx + dy * dy + dz * dz <= (double) RANGE * RANGE;
    }

    /** A caixa da planta (para o recorte pelo campo de visão: o holograma aparece mesmo com a plataforma fora da tela). */
    @Override
    public AABB getRenderBoundingBox(LaunchPadBlockEntity pad) {
        Blueprint blueprint = pad.blueprint();
        if (blueprint == null) {
            return new AABB(pad.getBlockPos());
        }
        var p = pad.getBlockPos();
        return new AABB(p.getX() + blueprint.minX(), p.getY() + blueprint.minY(), p.getZ() + blueprint.minZ(), p.getX() + blueprint.maxX() + 1.0,
                p.getY() + blueprint.maxY() + 1.0, p.getZ() + blueprint.maxZ() + 1.0);
    }
}
