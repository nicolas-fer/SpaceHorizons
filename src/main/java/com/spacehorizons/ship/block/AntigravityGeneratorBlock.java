package com.spacehorizons.ship.block;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.rocket.RocketPortrait;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Gerador de antigravidade (decisão A3 de {@code docs/PLANO_NAVE_ATMOSFERA.md}): a nave precisa de pelo menos um, CARREGADO, para ancorar no ar. A
 * energia é uma carga guardada no próprio bloco ({@link #CHARGED}), sem combustível: ancorar gasta a carga de todos os geradores da nave
 * ({@code AnchorJob}) e cada um se recarrega sozinho depois de {@link #rechargeTicks} ticks, com o bloco no mundo (nave ancorada ou pousada). Em voo
 * (a nave é uma entidade, o retrato não tem tick) não recarrega: depois de desancorar é preciso esperar a carga terminar antes de ancorar de novo.
 */
public class AntigravityGeneratorBlock extends Block {
    public static final BooleanProperty CHARGED = BooleanProperty.create("charged");
    /** Padrão da recarga: 20 s. Variável só para o autoteste encurtar. */
    public static final int DEFAULT_RECHARGE_TICKS = 400;
    public static volatile int rechargeTicks = DEFAULT_RECHARGE_TICKS;

    public AntigravityGeneratorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(CHARGED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGED);
    }

    /** Descarregado: agenda a recarga (também quando a nave volta a blocos com o gerador sem carga). */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.getValue(CHARGED) && !state.is(oldState.getBlock())) {
            level.scheduleTick(pos, this, rechargeTicks);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(CHARGED)) {
            level.setBlock(pos, state.setValue(CHARGED, true), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.4F, 1.6F);
        }
    }

    // ---------------------------------------------------------------- contagem no retrato

    private static final Map<RocketPortrait, int[]> COUNTS = Collections.synchronizedMap(new WeakHashMap<>());

    /** Quantos geradores a nave tem e quantos estão carregados: {total, carregados}. Guardado por retrato (a contagem varre a grade uma vez). */
    public static int[] count(RocketPortrait portrait) {
        int[] cached = COUNTS.get(portrait);
        if (cached != null) {
            return cached;
        }
        int total = 0;
        int charged = 0;
        Block block = ModBlocks.ANTIGRAVITY_GENERATOR.get();
        boolean any = false;
        for (BlockState state : portrait.palette()) {
            any |= state.is(block);
        }
        if (any) {
            for (int y = 0; y < portrait.sizeY; y++) {
                for (int z = 0; z < portrait.sizeZ; z++) {
                    for (int x = 0; x < portrait.sizeX; x++) {
                        BlockState state = portrait.get(x, y, z);
                        if (state.is(block)) {
                            total++;
                            if (state.getValue(CHARGED)) {
                                charged++;
                            }
                        }
                    }
                }
            }
        }
        int[] result = {total, charged};
        COUNTS.put(portrait, result);
        return result;
    }
}
