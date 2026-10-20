package com.spacehorizons.ship.block;

import com.spacehorizons.content.connected.ConnectedFaces;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Janela da nave: vidro translúcido (o mesmo comportamento do vidro do vanilla, que esconde as faces coladas entre vidros iguais) com
 * TEXTURA CONECTADA: a moldura some nas bordas que encostam em outra janela, então uma parede de janelas vira um vidro contínuo, com
 * moldura só no contorno ({@link ConnectedFaces}, docs/TEXTURAS_CONECTADAS.md). O estaleiro não tem nada do lado de fora, então o que se
 * vê por ela é o céu virtual da nave (docs/PLANO_NAVES.md, 4.1).
 */
public class ShipWindowBlock extends TransparentBlock {
    private static final ConnectedFaces.Rule RULE = ConnectedFaces.sameBlock();

    public ShipWindowBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(ConnectedFaces.disconnected(stateDefinition.any()));
    }

    /** As conexões desta janela, de uma vez, para quem escreve blocos por código (ver {@link ConnectedFaces#connectAround}). */
    public static ConnectedFaces.Rule rule() {
        return RULE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return ConnectedFaces.connect(defaultBlockState(), context.getLevel(), context.getClickedPos(), RULE);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return ConnectedFaces.update(state, directionToNeighbour, neighbourState, RULE);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        ConnectedFaces.addProperties(builder);
    }
}
