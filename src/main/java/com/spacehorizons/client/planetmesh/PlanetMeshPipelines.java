package com.spacehorizons.client.planetmesh;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.spacehorizons.SpaceHorizons;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/** Pipeline da malha orbital e o bloco de uniforms que o shader dela lê. */
final class PlanetMeshPipelines {
    static final String INFO_UNIFORM = "PlanetMeshInfo";

    private static final BindGroupLayout INFO_LAYOUT = BindGroupLayout.builder()
            .withUniform(INFO_UNIFORM, UniformType.UNIFORM_BUFFER)
            .build();

    /**
     * Triângulos opacos, posição + cor por vértice, sem culling. Testa e escreve profundidade para que o
     * lado do planeta virado para longe fique escondido pelo lado próximo; o alvo de profundidade é
     * próprio (ver PlanetMeshRenderer), então nada disso vaza para o terreno desenhado depois.
     * Profundidade invertida (GREATER_THAN_OR_EQUAL), como no resto do vanilla 26.x.
     */
    static final RenderPipeline PLANET_MESH = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "pipeline/planet_mesh"))
            .withVertexShader(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "planetmesh/planet_mesh"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "planetmesh/planet_mesh"))
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(INFO_LAYOUT)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withCull(false)
            .build();

    private PlanetMeshPipelines() {
    }

    /**
     * Registrada como opcional: se o shader não compilar o jogo segue sem a malha em vez de falhar no carregamento.
     * O renderer checa getCompiledPipelineNullable antes de desenhar.
     */
    static void register(RegisterRenderPipelinesEvent event) {
        event.registerOptionalPipeline(PLANET_MESH);
    }
}
