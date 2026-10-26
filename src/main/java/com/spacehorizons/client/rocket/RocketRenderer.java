package com.spacehorizons.client.rocket;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketPortrait;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Renderer do foguete (Tier 1): malha assada uma vez ({@link PortraitMesh}), reproduzida por frame na pose da entidade
 * via submitCustomGeometry. Fica dentro do pipeline vanilla: translúcido, OIT, ordenação, neblina e a curvatura
 * do mod (shader core/block já remendado) funcionam sem código extra.
 *
 * Ciclo no 26.3 (tudo no render thread):
 *   extract : EntityRenderer.createRenderState(entity, partial) = createRenderState() + extractRenderState(...)
 *   submit  : EntityRenderDispatcher.submit translada para (entidade - câmera) em double e chama submit(...)
 *   prepare : FeatureRenderDispatcher.prepareFrame -> CustomFeatureRenderer.buildGroup chama o nosso replay()
 *   execute : desenho (sólido no passe "Main", translúcido depois)
 */
public class RocketRenderer extends EntityRenderer<RocketEntity, RocketRenderState> {
    static final RocketMeshCache MESHES = new RocketMeshCache();

    public RocketRenderer(EntityRendererProvider.Context context) {
        super(context);
        RocketEntity.portraitReplaced = MESHES::replaced; // um delta do retrato (porta, baú) troca o retrato: a malha do antigo não serve mais
        MESHES.clear(); // o dispatcher recria os renderers a cada recarga de recursos: UVs do atlas antigo não valem mais
        this.shadowRadius = 0.0F; // sombra circular de entidade não faz sentido num foguete de blocos
    }

    @Override
    public RocketRenderState createRenderState() {
        return new RocketRenderState();
    }

    @Override
    public void extractRenderState(RocketEntity rocket, RocketRenderState state, float partialTick) {
        super.extractRenderState(rocket, state, partialTick); // posição interpolada, lightCoords (sonda em getLightProbePosition), nomes
        RocketPortrait portrait = rocket.portrait();
        state.mesh = portrait.isEmpty() ? null : MESHES.get(portrait);
        rocket.orientation(partialTick, state.orientation);
        state.pivot.set(rocket.pivotLocal());
        // com tripulação a pé a bordo (S5) o casco é desenhado na linha do tempo de ticks do servidor, a mesma da câmera e do tripulante
        com.spacehorizons.ship.deck.DeckFrame smooth = rocket.smoothFrame(rocket.clientTickAt(partialTick));
        if (smooth != null) {
            state.x = smooth.cx();
            state.y = smooth.cy();
            state.z = smooth.cz();
        }

        com.spacehorizons.client.ship.DeckAim.Target aim = com.spacehorizons.client.ship.DeckAim.targetOf(rocket);
        state.highlightPos = aim == null ? null : aim.pos();
        state.highlightBoxes = aim == null ? null : portrait.shapes().at(aim.pos().getX(), aim.pos().getY(), aim.pos().getZ());

        state.blockEntities.clear();
        if (rocket.level() instanceof ClientLevel level && state.mesh != null) {
            BlockEntityRenderDispatcher dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
            Vec3 cameraPos = this.entityRenderDispatcher.camera != null ? this.entityRenderDispatcher.camera.position() : Vec3.ZERO;
            for (RocketPortrait.LoadedBlockEntity loaded : portrait.loadBlockEntities(level.registryAccess())) {
                BlockEntity blockEntity = loaded.blockEntity();
                // tryExtractRenderState recusa BE sem level; o renderer em si aceita (ChestRenderer tem o ramo "sem level")
                BlockEntityRenderer<BlockEntity, BlockEntityRenderState> renderer = dispatcher.getRenderer(blockEntity);
                if (renderer != null) {
                    BlockEntityRenderState blockState = renderer.createRenderState();
                    renderer.extractRenderState(blockEntity, blockState, partialTick, cameraPos, null);
                    blockState.lightCoords = state.lightCoords;
                    state.blockEntities.add(new RocketRenderState.BlockEntityDraw(blockState, loaded.localPos()));
                }
            }
        }
    }

    @Override
    public void submit(RocketRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.mesh != null) {
            poseStack.pushPose();
            // poseStack já está em (posição da entidade - câmera); rotação em torno do pivô e recentra o retrato
            poseStack.rotate(state.orientation);
            poseStack.translate(-state.pivot.x, -state.pivot.y, -state.pivot.z);
            for (PortraitMesh.Layer layer : state.mesh.layers()) {
                // Pose é copiada agora; o lambda roda no prepareFrame deste mesmo frame
                collector.submitCustomGeometry(poseStack, layer.renderType(), (pose, buffer) -> layer.replay(pose, buffer, state.lightCoords));
            }
            BlockEntityRenderDispatcher dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
            for (RocketRenderState.BlockEntityDraw draw : state.blockEntities) {
                poseStack.pushPose();
                poseStack.translate(draw.localPos().getX(), draw.localPos().getY(), draw.localPos().getZ());
                dispatcher.submit(draw.state(), poseStack, collector, camera);
                poseStack.popPose();
            }
            if (state.highlightPos != null && state.highlightBoxes != null && state.highlightBoxes.length > 0) {
                // o contorno de seleção do bloco do retrato que a mira acerta (o vanilla só contorna blocos do mundo)
                poseStack.pushPose();
                poseStack.translate(state.highlightPos.getX(), state.highlightPos.getY(), state.highlightPos.getZ());
                AABB[] boxes = state.highlightBoxes;
                collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
                    for (AABB box : boxes) {
                        drawBox(pose, buffer, box.inflate(0.002));
                    }
                });
                poseStack.popPose();
            }
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera); // nome, corda
    }

    /** As 12 arestas da caixa em linhas pretas (a cor do contorno do vanilla), com a normal de cada aresta para a espessura da linha. */
    private static void drawBox(PoseStack.Pose pose, VertexConsumer buffer, AABB b) {
        float x0 = (float) b.minX;
        float y0 = (float) b.minY;
        float z0 = (float) b.minZ;
        float x1 = (float) b.maxX;
        float y1 = (float) b.maxY;
        float z1 = (float) b.maxZ;
        float[][] edges = {
            {x0, y0, z0, x1, y0, z0}, {x0, y1, z0, x1, y1, z0}, {x0, y0, z1, x1, y0, z1}, {x0, y1, z1, x1, y1, z1},
            {x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0}, {x0, y0, z1, x0, y1, z1}, {x1, y0, z1, x1, y1, z1},
            {x0, y0, z0, x0, y0, z1}, {x1, y0, z0, x1, y0, z1}, {x0, y1, z0, x0, y1, z1}, {x1, y1, z0, x1, y1, z1}};
        for (float[] e : edges) {
            float nx = e[3] - e[0];
            float ny = e[4] - e[1];
            float nz = e[5] - e[2];
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1.0E-6F) {
                continue;
            }
            nx /= len;
            ny /= len;
            nz /= len;
            buffer.addVertex(pose, e[0], e[1], e[2]).setColor(0xFF000000).setNormal(pose, nx, ny, nz).setLineWidth(3.0F);
            buffer.addVertex(pose, e[3], e[4], e[5]).setColor(0xFF000000).setNormal(pose, nx, ny, nz).setLineWidth(3.0F);
        }
    }

    /** A hitbox é alinhada aos eixos e não gira com o foguete: culling por esfera que cobre o retrato em qualquer pose. */
    @Override
    protected AABB getBoundingBoxForCulling(RocketEntity rocket, float partialTick) {
        RocketPortrait p = rocket.portrait();
        double r = 0.5 * Math.sqrt((double) p.sizeX * p.sizeX + (double) p.sizeY * p.sizeY + (double) p.sizeZ * p.sizeZ) + 1.0;
        com.spacehorizons.ship.deck.DeckFrame smooth = rocket.smoothFrame(rocket.clientTickAt(partialTick));
        Vec3 c = smooth != null ? new Vec3(smooth.cx(), smooth.cy(), smooth.cz()) : rocket.getPosition(partialTick);
        return new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
    }
}
