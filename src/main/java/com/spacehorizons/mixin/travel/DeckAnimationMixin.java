package com.spacehorizons.mixin.travel;

import com.spacehorizons.client.ship.DeckClient;
import com.spacehorizons.client.ship.DeckObserver;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Convés andável (S5): o vanilla mede a caminhada pelo deslocamento da posição no MUNDO, que a bordo é o da nave (a 100 blocos por tick o jogador
 * parado balançaria os braços no máximo). A bordo, a animação usa o passo no referencial local (também para o tripulante visto por outro cliente).
 */
@Mixin(LivingEntity.class)
abstract class DeckAnimationMixin {
    @Shadow
    protected abstract void updateWalkAnimation(float distance);

    @Shadow
    @org.spongepowered.asm.mixin.Final
    public net.minecraft.world.entity.WalkAnimationState walkAnimation;

    @Inject(method = "calculateEntityAnimation", at = @At("HEAD"), cancellable = true)
    private void spacehorizons$deckAnimation(boolean useY, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof LocalPlayer player && DeckClient.isAboard(player)) {
            if (!self.isPassenger() && self.isAlive()) {
                this.updateWalkAnimation((float) DeckClient.localStep());
            } else {
                this.walkAnimation.stop();
            }
            ci.cancel();
        } else if (self instanceof RemotePlayer remote && DeckObserver.isObserved(remote)) {
            // o tripulante visto por outro cliente: o mesmo, com o passo local que o estado recebido dá
            this.updateWalkAnimation((float) DeckObserver.localStep(remote));
            ci.cancel();
        }
    }
}
