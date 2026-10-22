package com.spacehorizons.mixin.travel;

import com.spacehorizons.client.ship.DeckClient;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Convés andável (S5): o jogador local a bordo de uma nave é simulado no referencial da nave. A física do vanilla (gravidade, atrito, pulo,
 * sprint) segue igual, mas o movimento que ela pede é aplicado à posição LOCAL contra os sólidos do retrato ({@link DeckClient#moveLocal}), e
 * não ao mundo: a posição do mundo é derivada da local no fim do tick. Só no cliente: o servidor nunca move o tripulante por {@code move}.
 */
@Mixin(Entity.class)
abstract class DeckMoveMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void spacehorizons$deckMove(MoverType moverType, Vec3 delta, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player && DeckClient.moveLocal(player, delta)) {
            ci.cancel();
        }
    }
}
