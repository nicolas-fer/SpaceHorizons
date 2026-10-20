package com.spacehorizons.rocket;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Plataforma de lançamento. Com a mão vazia, o clique direito nela FIXA (ou solta) o painel de projeto ({@code docs/PLANO_TIERS.md}, seção 4):
 * o painel também aparece sozinho enquanto se olha para a plataforma de mão vazia, e fixado ele fica aberto mesmo andando em volta ou com
 * blocos na mão. O painel é do cliente ({@code RocketDesignClient}); o bloco só avisa o gancho {@link #CLIENT_PRESS}, que o cliente liga no
 * começo (um servidor dedicado nunca o liga e o gancho não faz nada).
 */
public class LaunchPadBlock extends Block {
    /** Quem quer saber de um clique de mão vazia numa plataforma (só o cliente liga isto). */
    public static volatile Consumer<BlockPos> CLIENT_PRESS = pos -> {
    };

    public LaunchPadBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            CLIENT_PRESS.accept(pos.immutable());
        }
        return InteractionResult.SUCCESS;
    }
}
