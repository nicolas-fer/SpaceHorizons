package com.spacehorizons.ship;

import com.spacehorizons.content.ModBlocks;

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
}
