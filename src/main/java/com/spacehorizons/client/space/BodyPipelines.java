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

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
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

    /**
     * O casco de outra nave ou foguete visto de longe (VesselRenderer): triângulos com posição + cor, o mesmo bloco de uniforms dos corpos, sem
     * mistura e sem culling, com profundidade própria (invertida, como o resto). O alvo de profundidade é do VesselRenderer.
     */
    static final RenderPipeline VESSEL = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "pipeline/space_vessel"))
            .withVertexShader(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "space/vessel"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "space/vessel"))
            .withBindGroupLayout(INFO_LAYOUT)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withCull(false)
            .build();

    /**
     * O disco do sol (SunDisc): triângulos com posição + cor (alfa por vértice) e os shaders de posição e cor do vanilla, a mesma mistura
     * translúcida do brilho do nascer do sol e sem culling. Sem profundidade: é só o céu, desenhado antes de tudo.
     */
    static final RenderPipeline SUN_DISC = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withLocation(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "pipeline/sun_disc"))
            .withVertexShader(Identifier.withDefaultNamespace("core/position_color"))
            .withFragmentShader(Identifier.withDefaultNamespace("core/position_color"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    private BodyPipelines() {
    }

    /** Opcional: se o shader não compilar o jogo segue sem os corpos (o renderer checa getCompiledPipelineNullable). */
    static void register(RegisterRenderPipelinesEvent event) {
        event.registerOptionalPipeline(BODY);
        event.registerOptionalPipeline(SUN_DISC);
        event.registerOptionalPipeline(VESSEL);
    }
}
