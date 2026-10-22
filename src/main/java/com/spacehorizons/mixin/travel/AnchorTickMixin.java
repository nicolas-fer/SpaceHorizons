package com.spacehorizons.mixin.travel;

import com.spacehorizons.ship.ShipAnchors;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.material.Fluid;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nave ancorada no ar (spike S6, {@link ShipAnchors}): dentro da caixa dela os fluidos não escorrem e os blocos que caem (areia, cascalho, pó de
 * concreto, bigorna) não caem. Os dois são ticks agendados do mundo; o tick é descartado (um vizinho que mudar agenda outro, que também é descartado
 * enquanto a nave estiver ancorada). Os outros ticks agendados (redstone, por exemplo) seguem normais.
 */
@Mixin(ServerLevel.class)
abstract class AnchorTickMixin {
    @Inject(method = "tickFluid", at = @At("HEAD"), cancellable = true)
    private void spacehorizons$anchoredFluid(BlockPos pos, Fluid type, CallbackInfo ci) {
        if (ShipAnchors.holds((ServerLevel) (Object) this, pos)) {
            ci.cancel();
        }
    }

    @Inject(method = "tickBlock", at = @At("HEAD"), cancellable = true)
    private void spacehorizons$anchoredFalling(BlockPos pos, Block type, CallbackInfo ci) {
        if (type instanceof FallingBlock && ShipAnchors.holds((ServerLevel) (Object) this, pos)) {
            ci.cancel();
        }
    }
}
