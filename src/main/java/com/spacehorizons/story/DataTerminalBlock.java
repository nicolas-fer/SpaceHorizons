package com.spacehorizons.story;

import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
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
 * O terminal de dados da trama (docs/PLANO_SOBREVIVENCIA.md, "Etapa 5: a trama"). Clique direito abre a tela com o registro do terminal; no servidor, a
 * PRIMEIRA vez de cada jogador entrega a recompensa ({@link StoryLogic#useTerminal}), nas seguintes só mostra o registro de novo. Qual registro é vem do estado
 * {@code record} ({@code aurora}, {@code selene}, {@code vigia}), não de um block entity: assim o terminal pode ser desenhado direto pelo gerador do mundo e
 * quebrado/recolocado sem perder o registro (o loot table copia o estado).
 */
public class DataTerminalBlock extends Block {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<TerminalRecord> RECORD = EnumProperty.create("record", TerminalRecord.class);

    /** Abre a tela do registro (só o cliente liga isto; num servidor dedicado o clique só entrega a recompensa). Recebe o id do registro. */
    public static volatile Consumer<String> CLIENT_OPEN = id -> {
    };

    /** Qual registro o terminal guarda: o valor é o id do registro sem o sufixo {@code _terminal}. */
    public enum TerminalRecord implements StringRepresentable {
        AURORA, SELENE, VIGIA;

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public DataTerminalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(RECORD, TerminalRecord.AURORA));
    }

    /** O registro deste terminal. */
    public static StoryEntry entryOf(BlockState state) {
        return StoryEntries.terminalForRecord(state.getValue(RECORD).getSerializedName());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        StoryEntry entry = entryOf(state);
        if (entry == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            CLIENT_OPEN.accept(entry.id());
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            StoryLogic.useTerminal(serverPlayer, entry);
        }
        return InteractionResult.SUCCESS_SERVER;
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
        builder.add(FACING, RECORD);
    }
}
