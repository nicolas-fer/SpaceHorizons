package com.spacehorizons.mixin.travel;

import com.spacehorizons.planet.Planets;
import com.spacehorizons.space.SpaceDimension;
import com.spacehorizons.space.travel.TravelMath;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * O voo acelerado e o piloto automático andam dezenas de blocos por tick; o "moved too quickly" do vanilla (que no
 * singleplayer já é ignorado) devolveria o jogador para trás num servidor dedicado. Acima de uma altitude baixa,
 * num level-planeta, e em qualquer ponto do espaço aberto, a checagem de velocidade fica desligada.
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "shouldCheckPlayerMovement", at = @At("HEAD"), cancellable = true)
    private void spacehorizons$allowSpaceSpeeds(boolean isFallFlying, CallbackInfoReturnable<Boolean> cir) {
        Planets.forLevel(this.player.level()).ifPresent(planet -> {
            if (this.player.getY() - planet.seaLevel() > TravelMath.MOVEMENT_CHECK_ALTITUDE) {
                cir.setReturnValue(false);
            }
        });
        if (SpaceDimension.is(this.player.level())) {
            cir.setReturnValue(false);
        }
    }
}
