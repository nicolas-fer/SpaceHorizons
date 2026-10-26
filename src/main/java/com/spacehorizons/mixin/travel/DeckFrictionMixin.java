package com.spacehorizons.mixin.travel;

import com.spacehorizons.client.ship.DeckClient;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Atrito do convés (fase 2e, frente 2): o atrito do jogador no chão é o do bloco sob o pé, lido no MUNDO pelo vanilla ({@code travelInAir}); no
 * convés o mundo é ar, então gelo, slime e afins não faziam efeito. Aqui o bloco lido é o do RETRATO ({@link DeckClient#groundState}), só para o
 * jogador local a bordo; para todo o resto vale o mundo. O fator de velocidade e o de pulo estão em {@link DeckMoveMixin}.
 */
@Mixin(LivingEntity.class)
abstract class DeckFrictionMixin {
    @Redirect(method = "travelInAir", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState spacehorizons$deckGround(Level level, BlockPos pos) {
        return DeckClient.groundState((LivingEntity) (Object) this, level.getBlockState(pos));
    }
}
