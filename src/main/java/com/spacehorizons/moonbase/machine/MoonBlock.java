package com.spacehorizons.moonbase.machine;

import com.spacehorizons.content.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Bloco de energia ou máquina da base lunar. Um só tipo de bloco para os sete {@link MachineKind}, com o estado aceso ({@code lit}).
 * Clicar numa máquina abre o menu; num painel, bateria ou cabo mostra a energia na barra de ação.
 */
public class MoonBlock extends BaseEntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private final MachineKind kind;

    public MoonBlock(BlockBehaviour.Properties properties, MachineKind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    public MachineKind kind() {
        return kind;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MoonBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntities.MOON.get(),
                        (innerLevel, pos, innerState, entity) -> MoonBlockEntity.serverTick(serverLevel, pos, innerState, entity))
                : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MoonBlockEntity be) {
            if (kind.hasMenu()) {
                player.openMenu(be, pos);
            } else {
                player.sendOverlayMessage(be.describe());
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(LIT);
    }

    /** Fumaça fina saindo da máquina que trabalha. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) || !kind.hasMenu() || random.nextInt(6) != 0) {
            return;
        }
        level.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5, pos.getY() + 1.05,
                pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5, 0.0, 0.03, 0.0);
    }
}
