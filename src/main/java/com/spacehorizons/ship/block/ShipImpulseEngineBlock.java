package com.spacehorizons.ship.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Motor de impulso da nave. {@code FACING} é para onde o bocal aponta: a nave é empurrada para o lado oposto. Os quatro lados horizontais
 * empurram no plano da nave (a soma vetorial dos motores, {@code ShipArrangement}, sem torque); bocal para CIMA ou para BAIXO é o motor de
 * decolagem: o de baixo empurra a nave para cima, e no espaço não empurra no plano (conta massa e vazão, não empuxo horizontal).
 * O bocal segue o olhar de quem coloca (inclusive para cima e para baixo), como o dispenser.
 */
public class ShipImpulseEngineBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public ShipImpulseEngineBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    /** O bocal aponta para onde o jogador olha (inclusive para baixo, para o motor de decolagem). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
