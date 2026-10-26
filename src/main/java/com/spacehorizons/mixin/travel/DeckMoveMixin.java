package com.spacehorizons.mixin.travel;

import com.spacehorizons.client.ship.DeckClient;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Convés andável (S5): o jogador local a bordo de uma nave é simulado no referencial da nave. A física do vanilla (gravidade, atrito, pulo,
 * sprint) segue igual, mas o movimento que ela pede é aplicado à posição LOCAL contra os sólidos do retrato ({@link DeckClient#moveLocal}), e
 * não ao mundo: a posição do mundo é derivada da local no fim do tick. Só no cliente: o servidor nunca move o tripulante por {@code move}.
 */
@Mixin(Entity.class)
abstract class DeckMoveMixin {
    @Shadow
    protected abstract float getBlockSpeedFactor();

    @Shadow
    public abstract Vec3 getDeltaMovement();

    @Shadow
    public abstract void setDeltaMovement(Vec3 movement);

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void spacehorizons$deckMove(MoverType moverType, Vec3 delta, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player && DeckClient.moveLocal(player, delta)) {
            // o fim do Entity.move do vanilla: o fator de velocidade do bloco (alma de areia, mel), agora o do retrato
            float factor = this.getBlockSpeedFactor();
            Vec3 v = this.getDeltaMovement();
            this.setDeltaMovement(new Vec3(v.x * factor, v.y, v.z * factor));
            ci.cancel();
        }
    }

    /** Os fatores de velocidade e de pulo dos blocos são os do retrato sob os pés, não os do mundo (que é ar). */
    @Inject(method = "getBlockSpeedFactor", at = @At("HEAD"), cancellable = true)
    private void spacehorizons$deckSpeedFactor(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof LocalPlayer player) {
            Float factor = DeckClient.speedFactor(player);
            if (factor != null) {
                cir.setReturnValue(factor);
            }
        }
    }

    @Inject(method = "getBlockJumpFactor", at = @At("HEAD"), cancellable = true)
    private void spacehorizons$deckJumpFactor(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof LocalPlayer player) {
            Float factor = DeckClient.jumpFactor(player);
            if (factor != null) {
                cir.setReturnValue(factor);
            }
        }
    }
}
