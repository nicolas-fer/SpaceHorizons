package com.spacehorizons.rocket;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Motor do foguete. Sozinho é o motor 1x1; vários, na forma certa (cubo cheio 2x2x2 ou 3x3x3, {@link Multiblocks}), formam um motor
 * maior: o bloco MESTRE desenha o modelo do conjunto e os outros ficam invisíveis ({@link #FORM}). O estado é mantido por
 * {@link EngineForming} quando um motor é posto ou quebrado, e refeito na montagem do foguete (assim o retrato em voo, o estaleiro e o
 * pouso levam a estrutura formada).
 *
 * {@link #FACING} só importa para o mestre 2x2x2 (o canto mínimo, cujo modelo se estende para +x e +z): ao girar a estrutura no pouso, o
 * mestre vai para outro canto e o modelo gira com ele.
 */
public class RocketEngineBlock extends Block {
    public static final EnumProperty<EngineForm> FORM = EnumProperty.create("form", EngineForm.class);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    public RocketEngineBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORM, EngineForm.SOLO).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORM, FACING);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !oldState.is(this)) {
            EngineForming.refresh(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        EngineForming.refreshAround(level, pos);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
