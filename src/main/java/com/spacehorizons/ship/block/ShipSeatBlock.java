package com.spacehorizons.ship.block;

import com.spacehorizons.ship.ShipSeatEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Assento do piloto (docs/PLANO_NAVES.md, seção 5). O modelo é uma poltrona baixa; a forma é a do assento (9/16 de altura), não o cubo
 * cheio, para dar para passar por cima e para o piloto sentar na altura certa. Sentar (clique direito) e pilotar são da etapa 1e.
 */
public class ShipSeatBlock extends Block {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    /** Altura do assento em blocos (o piloto senta nesta altura). */
    public static final double SEAT_HEIGHT = 8.0 / 16.0;
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 8.0, 15.0);

    public ShipSeatBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Clique com a mão vazia: senta (a entidade do assento carrega o jogador; sentado, ele pilota a nave se ela voa). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty() || player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            com.spacehorizons.SpaceHorizons.LOGGER.info("[ship] clique no assento {} por {}", pos, player.getName().getString());
            ShipSeatEntity.sit(serverLevel, pos, player);
        }
        return InteractionResult.SUCCESS;
    }

    /** O assento olha para onde o jogador olha (as costas ficam para trás dele). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
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
