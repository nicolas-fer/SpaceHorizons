package com.spacehorizons.client.space;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * O disco do sol do espaço e do estaleiro: um disco redondo com halo suave no lugar do quadrado de textura do vanilla.
 *
 * O quadrado do vanilla é um quadro no plano tangente à esfera do céu, preso aos eixos leste-oeste e cima-baixo do mundo. Com a
 * câmera de uma nave em qualquer atitude (guinada, arfagem, rolagem) o céu gira e o quadrado vira losango, o que denuncia que o
 * sol é uma figura plana. Aqui o sol é um disco de frente para a câmera: o contorno é um círculo na tela em qualquer atitude e em qualquer ponto do campo de visão. O desenho é feito
 * na pose que o vanilla já monta para o sol (altura, azimute e giro do {@link SpaceSun}), então a direção é a mesma de antes.
 *
 * O disco (a {@link #SKY_RADIUS} do olho, como o sol do vanilla), de frente para o eixo da câmera, tem anéis de brilho decrescente:
 * núcleo branco-quente opaco, uma borda de ~1 grau para o contorno não serrilhar e um halo que cai a zero. Fica só no espaço e no
 * estaleiro; os céus dos planetas seguem com o sol do vanilla (ali o céu não gira em torno da câmera: o quadrado aparece alinhado
 * ao horizonte e combina com o estilo do jogo).
 */
public final class SunDisc {
    /** Distância (blocos) do sol à câmera: a mesma esfera do céu do vanilla. */
    static final float SKY_RADIUS = 100.0F;
    /** Segmentos do círculo: o bastante para o contorno ficar redondo mesmo com zoom. */
    static final int SEGMENTS = 96;
    /** Raio angular (graus) de cada anel, do centro para fora, e o alfa e a cor (0xRRGGBB) dele. O primeiro anel é o centro. */
    static final float[] RING_DEGREES = {0.0F, 5.4F, 6.4F, 9.0F, 13.0F, 19.0F, 27.0F};
    static final float[] RING_ALPHA = {1.0F, 1.0F, 0.50F, 0.26F, 0.11F, 0.035F, 0.0F};
    static final int[] RING_RGB = {0xFFFBE8, 0xFFFBE8, 0xFFE9B8, 0xFFDDA0, 0xFFD290, 0xFFC880, 0xFFC070};

    /** A malha do disco: triângulos soltos, posição (3 floats por vértice) e cor ARGB (1 int por vértice). */
    record Mesh(float[] positions, int[] colors) {
        int vertexCount() {
            return colors.length;
        }
    }

    private static @Nullable GpuBuffer buffer;
    private static int vertexCount;

    private SunDisc() {
    }

    /**
     * Desenha o disco na pose atual do {@code poseStack} (a do sol do vanilla: o sol fica em +Y, a {@link #SKY_RADIUS} da origem).
     *
     * @return false se não deu para desenhar (pipeline não compilou): o chamador deixa o sol do vanilla aparecer
     */
    public static boolean draw(RenderPass renderPass, PoseStack poseStack, float rainBrightness) {
        CompiledRenderPipeline pipeline = RenderSystem.getCompiledPipelineNullable(BodyPipelines.SUN_DISC);
        if (pipeline == null) {
            return false;
        }
        GpuBuffer vertices = vertices();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        Matrix4f skyToView = new Matrix4f(modelViewStack);
        modelViewStack.popMatrix();
        // centro do sol no espaço da câmera; o disco é desenhado de frente para o EIXO da câmera (plano perpendicular a ele, na
        // profundidade do centro) e não na tangente da esfera do céu: assim o contorno é redondo na tela em qualquer ponto do FOV
        // (um círculo tangente à esfera sai oval nas bordas de um FOV largo, esticado na direção radial)
        Vector3f center = skyToView.transformPosition(0.0F, SKY_RADIUS, 0.0F, new Vector3f());
        if (center.z() >= 0.0F) {
            return true; // atrás da câmera: nada a desenhar (e o quadrado do vanilla também não deve aparecer)
        }
        Matrix4f modelView = new Matrix4f().translation(center).scale(center.length());
        GpuBufferSlice dynamicTransforms =
                RenderSystem.getDynamicUniforms().writeTransform(modelView, new Vector4f(1.0F, 1.0F, 1.0F, rainBrightness));
        renderPass.pushDebugGroup(() -> "Sun disc");
        renderPass.setPipeline(pipeline);
        RenderSystem.bindDefaultUniforms(renderPass);
        renderPass.setUniform("DynamicTransforms", dynamicTransforms);
        renderPass.setVertexBuffer(0, vertices.slice());
        renderPass.draw(vertexCount, 1, 0, 0);
        renderPass.popDebugGroup();
        return true;
    }

    /** Libera o buffer (saída do mundo); o próximo desenho o recria. */
    public static void reset() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
    }

    private static GpuBuffer vertices() {
        GpuBuffer existing = buffer;
        if (existing != null) {
            return existing;
        }
        Mesh mesh = mesh();
        vertexCount = mesh.vertexCount();
        int size = vertexCount * DefaultVertexFormat.POSITION_COLOR.getVertexSize();
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(size)) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i < vertexCount; i++) {
                builder.addVertex(mesh.positions()[i * 3], mesh.positions()[i * 3 + 1], mesh.positions()[i * 3 + 2]).setColor(mesh.colors()[i]);
            }
            try (MeshData data = builder.buildOrThrow()) {
                buffer = RenderSystem.getDevice().createBuffer(() -> "Sun disc", GpuBuffer.USAGE_VERTEX, data.vertexBuffer());
            }
        }
        return buffer;
    }

    /**
     * A malha do disco: triângulos de um disco no plano XY, a distância 1 da origem (um disco de raio tan(alfa) cobre o ângulo alfa
     * visto do centro), anel a anel; {@link #draw} a escala e a leva ao centro do sol. Separada do desenho para ser testada sem GPU.
     */
    static Mesh mesh() {
        int rings = RING_DEGREES.length;
        // centro: um triângulo por segmento; cada par de anéis seguintes: dois triângulos por segmento
        int triangles = SEGMENTS + (rings - 2) * SEGMENTS * 2;
        float[] positions = new float[triangles * 3 * 3];
        int[] colors = new int[triangles * 3];
        int[] n = {0};
        for (int j = 0; j < SEGMENTS; j++) {
            double a0 = 2.0 * Math.PI * j / SEGMENTS;
            double a1 = 2.0 * Math.PI * (j + 1) / SEGMENTS;
            put(positions, colors, n, 0, 0.0);
            put(positions, colors, n, 1, a0);
            put(positions, colors, n, 1, a1);
            for (int r = 1; r < rings - 1; r++) {
                put(positions, colors, n, r, a0);
                put(positions, colors, n, r + 1, a0);
                put(positions, colors, n, r, a1);
                put(positions, colors, n, r, a1);
                put(positions, colors, n, r + 1, a0);
                put(positions, colors, n, r + 1, a1);
            }
        }
        return new Mesh(positions, colors);
    }

    private static void put(float[] positions, int[] colors, int[] n, int ring, double azimuth) {
        double t = Math.tan(Math.toRadians(RING_DEGREES[ring]));
        int v = n[0]++;
        positions[v * 3] = (float) (t * Math.cos(azimuth));
        positions[v * 3 + 1] = (float) (t * Math.sin(azimuth));
        positions[v * 3 + 2] = 0.0F;
        colors[v] = Math.round(RING_ALPHA[ring] * 255.0F) << 24 | RING_RGB[ring];
    }
}
