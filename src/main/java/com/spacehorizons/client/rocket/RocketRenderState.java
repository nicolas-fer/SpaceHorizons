package com.spacehorizons.client.rocket;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Estado por frame (o vanilla cria um novo por entidade visível a cada frame: nada pesado aqui, só referências
 * a objetos imutáveis e valores interpolados). lightCoords vem da EntityRenderState.
 */
public class RocketRenderState extends EntityRenderState {
    public @Nullable PortraitMesh mesh;
    public final Quaternionf orientation = new Quaternionf();
    public final Vector3f pivot = new Vector3f();
    public final List<BlockEntityDraw> blockEntities = new ArrayList<>();
    /** O bloco do retrato que a mira do jogador local acerta (o contorno de seleção, em coordenadas locais) e as caixas dele; null se não é desta nave. */
    public @Nullable BlockPos highlightPos;
    public net.minecraft.world.phys.AABB @Nullable [] highlightBoxes;

    public record BlockEntityDraw(BlockEntityRenderState state, BlockPos localPos) {
    }
}
