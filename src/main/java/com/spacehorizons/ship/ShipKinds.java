package com.spacehorizons.ship;

import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.rocket.RocketBlockKind;
import com.spacehorizons.rocket.RocketKinds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Converte BlockStates em {@link ShipBlockKind} (a parte do censo que depende dos registros do jogo), como {@code RocketKinds}. */
public final class ShipKinds {
    private ShipKinds() {
    }

    /**
     * O papel de um bloco da nave: as peças do mod pelo bloco, o ar e os fluidos (que não têm massa de estrutura) como ar, e qualquer outro
     * bloco como estrutura. O tanque é o do foguete.
     */
    public static ShipBlockKind classify(BlockState state) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
            return ShipBlockKind.AIR;
        }
        if (state.is(ModBlocks.SHIP_BRIDGE.get())) {
            return ShipBlockKind.BRIDGE;
        } else if (state.is(ModBlocks.SHIP_IMPULSE_ENGINE.get())) {
            return ShipBlockKind.ENGINE;
        } else if (state.is(ModBlocks.ROCKET_FUEL_TANK.get())) {
            return ShipBlockKind.FUEL_TANK;
        } else if (state.is(ModBlocks.SHIP_WINDOW.get())) {
            return ShipBlockKind.WINDOW;
        } else if (state.is(ModBlocks.SHIP_SEAT.get())) {
            return ShipBlockKind.SEAT;
        } else if (state.is(ModBlocks.SHIP_AIRLOCK.get())) {
            return ShipBlockKind.AIRLOCK;
        }
        return ShipBlockKind.STRUCTURE;
    }

    /**
     * O papel de um bloco do mundo para a busca da montagem da nave ({@link ShipAssembly}): a ponte é o cockpit da nave; o resto é o
     * da montagem do foguete ({@link RocketKinds#classify}), que já recusa fluido, proibido e terreno, e aceita o casco e as peças da nave
     * como estrutura (blocos sólidos não proibidos).
     */
    public static RocketBlockKind assemblyKind(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.is(ModBlocks.SHIP_BRIDGE.get())) {
            return RocketBlockKind.COCKPIT;
        }
        return RocketKinds.classify(level, pos, state);
    }

    /** O lado do estaleiro (0 norte, 1 leste, 2 sul, 3 oeste: {@link ShipArrangement}) de uma direção horizontal; vertical vira o norte. */
    public static int side(Direction direction) {
        return switch (direction) {
            case EAST -> ShipArrangement.EAST;
            case SOUTH -> ShipArrangement.SOUTH;
            case WEST -> ShipArrangement.WEST;
            default -> ShipArrangement.NORTH;
        };
    }

    /** A direção horizontal de um lado do estaleiro ({@link ShipArrangement}). */
    public static Direction direction(int side) {
        return switch (side & 3) {
            case ShipArrangement.EAST -> Direction.EAST;
            case ShipArrangement.SOUTH -> Direction.SOUTH;
            case ShipArrangement.WEST -> Direction.WEST;
            default -> Direction.NORTH;
        };
    }
}
