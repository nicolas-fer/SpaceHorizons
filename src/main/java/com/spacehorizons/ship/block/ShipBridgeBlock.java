package com.spacehorizons.ship.block;

import com.spacehorizons.ship.ShipAssembler;
import com.spacehorizons.ship.ShipInfo;
import com.spacehorizons.ship.ShipYard;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Ponte de comando: o núcleo que define a nave (docs/PLANO_NAVES.md, seção 1). Clicar com a mão vazia, dentro de uma nave do
 * estaleiro, conta os blocos da nave (censo em lotes) e mostra a especificação: massa, empuxo, Δv e combustível ({@link ShipInfo}).
 * O {@code facing} da ponte define a PROA da nave: a ponte olha para quem pilota, então a proa é o lado oposto ao facing
 * ({@code ShipArrangement}).
 * A montagem no chão (a ponte como ponto de partida da busca, como o cockpit do foguete) é da fase 2a.
 */
public class ShipBridgeBlock extends Block {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    public ShipBridgeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            // agachado, fora do estaleiro (onde a ponte só comanda a nave registrada): monta a nave no chão
            if (player.isShiftKeyDown() && !ShipYard.is(serverLevel)) {
                ShipAssembler.assembleFor(serverLevel, pos, serverPlayer);
            } else {
                ShipInfo.reportFor(serverLevel, pos, serverPlayer);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
