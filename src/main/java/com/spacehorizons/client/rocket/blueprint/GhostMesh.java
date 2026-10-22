package com.spacehorizons.client.rocket.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.spacehorizons.rocket.blueprint.Blueprint;
import com.spacehorizons.rocket.blueprint.BlueprintCheck;
import com.spacehorizons.rocket.blueprint.BlueprintStates;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * A malha do holograma de UMA planta, tesselada uma vez (fora do render thread, como a do retrato do foguete) e guardada como vértices
 * prontos, com o intervalo de vértices de cada bloco da planta. Por quadro só se reproduz o intervalo dos blocos que FALTAM (fantasma
 * azulado, um pouco menor que o bloco para não brigar com as faces dos vizinhos de verdade) e dos ERRADOS (o mesmo bloco em vermelho, um pouco
 * maior, envolvendo o bloco errado). Os certos não aparecem. Nada é alocado por quadro além do lambda do envio.
 *
 * <p>Cada bloco é tesselado sozinho (sem esconder faces entre fantasmas vizinhos): quando um vizinho é posto, o fantasma que sobra continua
 * fechado. Estados que não têm modelo de bloco (baús, por exemplo) não ganham fantasma, mas contam no progresso.
 */
public final class GhostMesh {
    /** Teto de blocos desenhados por planta por quadro (o resto continua contando no painel). */
    public static final int MAX_DRAWN = 4096;
    /** Cor do fantasma (multiplica a do modelo): azul-claro translúcido. */
    private static final int GHOST_TINT = ARGB.color(110, 150, 215, 255);
    /** Cor do bloco errado: vermelho translúcido. */
    private static final int WRONG_TINT = ARGB.color(150, 255, 70, 60);
    private static final float GHOST_SCALE = 0.96F;
    private static final float WRONG_SCALE = 1.04F;

    private final float[] pos;
    private final float[] uv;
    private final int[] color;
    /** Vértices do bloco i: de {@code start[i]} a {@code start[i + 1]}. */
    private final int[] start;
    private final float[] centers;
    /** Vetor de trabalho do replay (só o render thread reproduz). */
    private final Vector3f scratch = new Vector3f();

    private GhostMesh(float[] pos, float[] uv, int[] color, int[] start, float[] centers) {
        this.pos = pos;
        this.uv = uv;
        this.color = color;
        this.start = start;
        this.centers = centers;
    }

    public int vertexCount() {
        return this.color.length;
    }

    /** Roda em worker thread: lê só objetos imutáveis (modelos assados, BlockColors) e a planta. */
    public static GhostMesh bake(Blueprint blueprint, BlockStateModelSet models, BlockColors blockColors) {
        BlockState[] palette = BlueprintStates.palette(blueprint);
        ModelBlockRenderer renderer = new ModelBlockRenderer(false, false, blockColors);
        FloatArrayList pos = new FloatArrayList();
        FloatArrayList uv = new FloatArrayList();
        IntArrayList color = new IntArrayList();
        int n = blueprint.size();
        int[] start = new int[n + 1];
        float[] centers = new float[3 * n];
        EmptyLevel level = new EmptyLevel();
        BlockQuadOutput out = (qx, qy, qz, quad, instance) -> add(pos, uv, color, qx, qy, qz, quad, instance);
        BlockModelLighter.enableCaching();
        try {
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            for (int i = 0; i < n; i++) {
                start[i] = color.size();
                int dx = blueprint.dx(i);
                int dy = blueprint.dy(i);
                int dz = blueprint.dz(i);
                centers[3 * i] = dx + 0.5F;
                centers[3 * i + 1] = dy + 0.5F;
                centers[3 * i + 2] = dz + 0.5F;
                BlockState state = palette[blueprint.stateIndex(i)];
                if (state == null || state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
                    continue;
                }
                at.set(dx, dy, dz);
                renderer.tesselateBlock(out, dx, dy, dz, level, at, state, models.get(state), state.getSeed(at));
            }
            start[n] = color.size();
        } finally {
            BlockModelLighter.clearCache();
        }
        return new GhostMesh(pos.toFloatArray(), uv.toFloatArray(), color.toIntArray(), start, centers);
    }

    private static void add(FloatArrayList pos, FloatArrayList uv, IntArrayList color, float x, float y, float z, BakedQuad quad, QuadInstance instance) {
        for (int v = 0; v < 4; v++) {
            Vector3fc p = quad.position(v);
            pos.add(p.x() + x);
            pos.add(p.y() + y);
            pos.add(p.z() + z);
            long packedUv = quad.packedUV(v);
            uv.add(UVPair.unpackU(packedUv));
            uv.add(UVPair.unpackV(packedUv));
            color.add(ARGB.multiply(instance.getColor(v), quad.bakedColors().color(v)));
        }
    }

    /**
     * Reproduz na pose do quadro (já na plataforma-âncora) os blocos que faltam e os errados, segundo {@code status} (códigos de
     * {@link BlueprintCheck}, na ordem da planta). Luz cheia: o holograma brilha por conta própria.
     */
    public void replay(PoseStack.Pose pose, VertexConsumer out, byte[] status) {
        Matrix4f m = pose.pose();
        Vector3f t = this.scratch;
        int drawn = 0;
        int n = Math.min(status.length, this.start.length - 1);
        for (int i = 0; i < n && drawn < MAX_DRAWN; i++) {
            byte s = status[i];
            if (s != BlueprintCheck.MISSING && s != BlueprintCheck.WRONG) {
                continue;
            }
            int from = this.start[i];
            int to = this.start[i + 1];
            if (from == to) {
                continue;
            }
            drawn++;
            boolean wrong = s == BlueprintCheck.WRONG;
            float scale = wrong ? WRONG_SCALE : GHOST_SCALE;
            int tint = wrong ? WRONG_TINT : GHOST_TINT;
            float cx = this.centers[3 * i];
            float cy = this.centers[3 * i + 1];
            float cz = this.centers[3 * i + 2];
            for (int v = from; v < to; v++) {
                float x = cx + (this.pos[3 * v] - cx) * scale;
                float y = cy + (this.pos[3 * v + 1] - cy) * scale;
                float z = cz + (this.pos[3 * v + 2] - cz) * scale;
                m.transformPosition(x, y, z, t);
                int c = wrong ? tint : ARGB.multiply(this.color[v], tint);
                out.addVertex(t.x, t.y, t.z, c, this.uv[2 * v], this.uv[2 * v + 1], OverlayTexture.NO_OVERLAY, LightCoordsUtil.FULL_BRIGHT, 0.0F, 1.0F, 0.0F);
            }
        }
    }

    /** Mundo vazio para a tesselação: cada bloco sozinho, céu cheio, sem tom de bioma. */
    private static final class EmptyLevel implements BlockAndTintGetter {
        @Override
        public BlockState getBlockState(BlockPos pos) {
            return Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Blocks.AIR.defaultBlockState().getFluidState();
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return layer == LightLayer.SKY ? 15 : 0;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return LevelLightEngine.EMPTY;
        }

        @Override
        public CardinalLighting cardinalLighting() {
            return CardinalLighting.DEFAULT;
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver color) {
            return -1;
        }

        @Override
        public int getHeight() {
            return 4096;
        }

        @Override
        public int getMinY() {
            return -2048;
        }
    }
}
