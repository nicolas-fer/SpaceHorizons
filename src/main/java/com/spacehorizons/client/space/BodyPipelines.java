package com.spacehorizons.client.space;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.spacehorizons.SpaceHorizons;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/** Pipeline da esfera de um corpo visto de fora e o bloco de uniforms que o shader dela lê. */
final class BodyPipelines {
    static final String INFO_UNIFORM = "BodyInfo";

    private static final BindGroupLayout INFO_LAYOUT = BindGroupLayout.builder()
            .withUniform(INFO_UNIFORM, UniformType.UNIFORM_BUFFER)
            .build();

    /** Mistura por opacidade (o céu de dia apaga o corpo) sem alterar o alfa do alvo. */
    private static final BlendFunction ALPHA_KEEP_ALPHA =
            new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA, BlendFactor.ZERO, BlendFactor.ONE);

    /**
     * Triângulos com posição + cor por vértice, sem culling, com profundidade própria (GREATER_THAN_OR_EQUAL:
     * profundidade invertida, como o resto do vanilla 26.x): o lado do corpo virado para longe fica atrás do próximo.
     * O alvo de profundidade é do BodyRenderer; o depth do alvo principal fica intocado.
     */
    static final RenderPipeline BODY = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "pipeline/space_body"))
            .withVertexShader(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "space/body"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "space/body"))
            .withBindGroupLayout(INFO_LAYOUT)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(new ColorTargetState(ALPHA_KEEP_ALPHA))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withCull(false)
            .build();

    private BodyPipelines() {
    }

    /** Opcional: se o shader não compilar o jogo segue sem os corpos (o renderer checa getCompiledPipelineNullable). */
    static void register(RegisterRenderPipelinesEvent event) {
        event.registerOptionalPipeline(BODY);
    }
}
