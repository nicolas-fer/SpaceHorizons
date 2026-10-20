package com.spacehorizons.ship;

import java.util.List;

import net.minecraft.core.BlockPos;

/**
 * O resultado de um censo da caixa de uma nave: a contagem por papel, os tanques de combustível (posições no estaleiro) e o combustível
 * somado deles, em mB, no instante {@code gameTime}.
 */
public record ShipCensusResult(ShipCensus census, List<BlockPos> tanks, long tankFuelMb, long gameTime) {
}
