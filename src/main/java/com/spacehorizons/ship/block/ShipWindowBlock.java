package com.spacehorizons.ship.block;

import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Janela da nave: vidro translúcido (o mesmo comportamento do vidro do vanilla, que esconde as faces coladas entre vidros iguais). O
 * estaleiro não tem nada do lado de fora, então o que se vê por ela é o céu virtual da nave (docs/PLANO_NAVES.md, 4.1).
 */
public class ShipWindowBlock extends TransparentBlock {
    public ShipWindowBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
