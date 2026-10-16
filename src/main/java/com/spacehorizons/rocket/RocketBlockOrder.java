package com.spacehorizons.rocket;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ordem de remoção e de reposição dos blocos: os "presos" (que dependem de um apoio: tochas, botões, placas, trilhos,
 * escadas de mão, tapetes, avisos...) saem antes e voltam depois dos sólidos, como na montagem do Create.
 */
public final class RocketBlockOrder {
    private RocketBlockOrder() {
    }

    /** O bloco precisa de apoio para ficar (heurística: sem colisão ou numa das tags de blocos presos do vanilla). */
    public static boolean isAttached(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        return state.is(BlockTags.BUTTONS) || state.is(BlockTags.PRESSURE_PLATES) || state.is(BlockTags.RAILS)
                || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.ALL_SIGNS) || state.is(BlockTags.BANNERS)
                || state.is(BlockTags.CLIMBABLE) || state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
    }
}
