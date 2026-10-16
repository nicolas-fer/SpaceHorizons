package com.spacehorizons.client.rocket;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.spacehorizons.rocket.RocketPortrait;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Tesselação ÚNICA do retrato (fora do render thread, igual ao SectionCompiler), guardada como vértices prontos
 * (posição local, cor com AO e luz embutidas, UV, luz). Por frame só se reproduz isso numa matriz: nada de
 * modelo, AO, culling ou luz de novo. Um Layer por camada de render (sólido, recorte, translúcido).
 */
public final class PortraitMesh {
    private final List<Layer> layers;

    private PortraitMesh(List<Layer> layers) {
        this.layers = layers;
    }

    public List<Layer> layers() {
        return this.layers;
    }

    /** Roda em worker thread: lê só objetos imutáveis (modelos assados, BlockColors) e o próprio retrato. */
    public static PortraitMesh bake(RocketPortrait portrait, boolean ambientOcclusion, boolean cutoutLeaves,
            BlockStateModelSet models, BlockColors blockColors) {
        PortraitLevel level = new PortraitLevel(portrait);
        // cull = true: ao contrário do MovingBlockFeatureRenderer (cull = false), aqui as faces coladas somem
        ModelBlockRenderer renderer = new ModelBlockRenderer(ambientOcclusion, true, blockColors);
        Map<ChunkSectionLayer, Builder> builders = new EnumMap<>(ChunkSectionLayer.class);

        BlockModelLighter.enableCaching(); // cache de luz por thread, como no SectionCompiler.compile
        try {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = 0; y < portrait.sizeY; y++) {
                for (int z = 0; z < portrait.sizeZ; z++) {
                    for (int x = 0; x < portrait.sizeX; x++) {
                        BlockState state = portrait.get(x, y, z);
                        if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
                            continue;
                        }
                        pos.set(x, y, z);
                        boolean forceOpaque = ModelBlockRenderer.forceOpaque(cutoutLeaves, state);
                        BlockQuadOutput out = (qx, qy, qz, quad, instance) -> builders
                                .computeIfAbsent(forceOpaque ? ChunkSectionLayer.SOLID : quad.materialInfo().layer(), l -> new Builder())
                                .add(qx, qy, qz, quad, instance);
                        renderer.tesselateBlock(out, x, y, z, level, pos, state, models.get(state), state.getSeed(pos));
                    }
                }
            }
        } finally {
            BlockModelLighter.clearCache();
        }

        List<Layer> layers = new ArrayList<>();
        for (ChunkSectionLayer l : ChunkSectionLayer.values()) { // ordem SOLID, CUTOUT, TRANSLUCENT
            Builder b = builders.get(l);
            if (b != null) {
                layers.add(b.build(renderTypeOf(l)));
            }
        }
        return new PortraitMesh(List.copyOf(layers));
    }

    /** Mesmos RenderTypes do MovingBlockFeatureRenderer: pipeline core/block, atlas de blocos, lightmap. */
    static RenderType renderTypeOf(ChunkSectionLayer layer) {
        return switch (layer) {
            case SOLID -> RenderTypes.solidMovingBlock();
            case CUTOUT -> RenderTypes.cutoutMovingBlock();
            case TRANSLUCENT -> RenderTypes.translucentMovingBlock();
        };
    }

    private static final class Builder {
        private final FloatArrayList pos = new FloatArrayList();
        private final FloatArrayList uv = new FloatArrayList();
        private final IntArrayList color = new IntArrayList();
        private final IntArrayList light = new IntArrayList();

        /** Mesmo que VertexConsumer.putBlockBakedQuad, só que gravando em vez de escrever num buffer de GPU. */
        void add(float x, float y, float z, BakedQuad quad, QuadInstance instance) {
            int emission = quad.materialInfo().lightEmission();
            for (int v = 0; v < 4; v++) {
                Vector3fc p = quad.position(v);
                this.pos.add(p.x() + x);
                this.pos.add(p.y() + y);
                this.pos.add(p.z() + z);
                long packedUv = quad.packedUV(v);
                this.uv.add(UVPair.unpackU(packedUv));
                this.uv.add(UVPair.unpackV(packedUv));
                this.color.add(ARGB.multiply(instance.getColor(v), quad.bakedColors().color(v)));
                this.light.add(instance.getLightCoordsWithEmission(v, emission));
            }
        }

        Layer build(RenderType renderType) {
            return new Layer(renderType, this.pos.toFloatArray(), this.uv.toFloatArray(), this.color.toIntArray(), this.light.toIntArray());
        }
    }

    /** Vértices prontos de uma camada, 4 por quad (GL_QUADS, como o formato BLOCK do vanilla espera). */
    public record Layer(RenderType renderType, float[] pos, float[] uv, int[] color, int[] light) {
        public int vertexCount() {
            return this.color.length;
        }

        /**
         * Reproduz na pose do frame. worldLight = luz do mundo na posição do foguete (EntityRenderState.lightCoords):
         * o céu do retrato é escalado por ela (foguete num hangar fica escuro) e a luz de bloco é o máximo dos dois.
         */
        public void replay(PoseStack.Pose pose, VertexConsumer out, int worldLight) {
            var m = pose.pose();
            Vector3f t = new Vector3f();
            int worldSky = LightCoordsUtil.sky(worldLight);
            int worldBlock = worldLight & 0xFFFF;
            for (int i = 0; i < this.color.length; i++) {
                m.transformPosition(this.pos[3 * i], this.pos[3 * i + 1], this.pos[3 * i + 2], t);
                int baked = this.light[i];
                int blockLight = Math.max(baked & 0xFFFF, worldBlock);
                int skyLight = (baked >>> 16) * worldSky / 15 << 16;
                // overlay e normal só existem no formato ENTITY; o BufferBuilder do BLOCK os ignora no caminho rápido
                out.addVertex(t.x, t.y, t.z, this.color[i], this.uv[2 * i], this.uv[2 * i + 1], OverlayTexture.NO_OVERLAY,
                        blockLight | skyLight, 0.0F, 1.0F, 0.0F);
            }
        }
    }
}
