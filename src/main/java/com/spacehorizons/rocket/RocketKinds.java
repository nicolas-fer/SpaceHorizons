package com.spacehorizons.rocket;

import com.spacehorizons.content.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Converte BlockStates em {@link RocketBlockKind} (a parte da montagem que depende dos registros do jogo). */
public final class RocketKinds {
    private RocketKinds() {
    }

    /** O papel de uma peça do mod pelo bloco, ou null se o bloco não é uma peça. A plataforma conta como {@link RocketBlockKind#PAD}. */
    public static RocketBlockKind partKind(BlockState state) {
        if (state.is(ModBlocks.ROCKET_COCKPIT.get())) {
            return RocketBlockKind.COCKPIT;
        } else if (state.is(ModBlocks.ROCKET_ENGINE.get())) {
            return RocketBlockKind.ENGINE;
        } else if (state.is(ModBlocks.ROCKET_FUEL_TANK.get())) {
            return RocketBlockKind.FUEL_TANK;
        } else if (state.is(ModBlocks.ROCKET_PARACHUTE.get())) {
            return RocketBlockKind.PARACHUTE;
        } else if (state.is(ModBlocks.ROCKET_LANDING_LEG.get())) {
            return RocketBlockKind.LEG;
        } else if (state.is(ModBlocks.ROCKET_HULL.get())) {
            return RocketBlockKind.HULL;
        } else if (state.is(ModBlocks.LAUNCH_PAD.get())) {
            return RocketBlockKind.PAD;
        } else if (state.is(ModBlocks.NUCLEAR_ENGINE.get())) {
            return RocketBlockKind.NUCLEAR_ENGINE;
        } else if (state.is(ModBlocks.ION_ENGINE.get())) {
            return RocketBlockKind.ION_ENGINE;
        } else if (state.is(ModBlocks.FUSION_ENGINE.get())) {
            return RocketBlockKind.FUSION_ENGINE;
        } else if (state.is(ModBlocks.HYDROGEN_TANK.get())) {
            return RocketBlockKind.HYDROGEN_TANK;
        } else if (state.is(ModBlocks.XENON_TANK.get())) {
            return RocketBlockKind.XENON_TANK;
        } else if (state.is(ModBlocks.RTG_GENERATOR.get())) {
            return RocketBlockKind.RTG;
        }
        return null;
    }

    /** O papel de um bloco que JÁ está no retrato do foguete: as peças do mod, e qualquer outro bloco é estrutura. */
    public static RocketBlockKind ofPortraitState(BlockState state) {
        if (state.isAir()) {
            return RocketBlockKind.AIR;
        }
        RocketBlockKind part = partKind(state);
        return part == null || part == RocketBlockKind.PAD ? RocketBlockKind.STRUCTURE : part;
    }

    /**
     * O papel de um bloco do mundo para a busca da montagem. A ordem importa: peças do mod primeiro (o tanque é um block
     * entity, o cockpit é um bloco de interação), depois o que recusa a montagem (proibido, terreno), depois o que é
     * ignorado (substituível) e, por fim, estrutura.
     */
    public static RocketBlockKind classify(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return RocketBlockKind.AIR;
        }
        RocketBlockKind part = partKind(state);
        if (part != null) {
            return part;
        }
        if (!state.getFluidState().isEmpty() || state.is(RocketTags.ROCKET_FORBIDDEN) || state.getDestroySpeed(level, pos) < 0.0F) {
            return RocketBlockKind.FORBIDDEN;
        }
        if (state.is(RocketTags.ROCKET_TERRAIN)) {
            return RocketBlockKind.TERRAIN;
        }
        if (state.canBeReplaced()) {
            return RocketBlockKind.AIR;
        }
        return RocketBlockKind.STRUCTURE;
    }
}
