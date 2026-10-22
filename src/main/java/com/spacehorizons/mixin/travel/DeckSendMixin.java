package com.spacehorizons.mixin.travel;

import com.spacehorizons.client.ship.DeckClient;

import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Convés andável (S5): a bordo, o pacote de posição do vanilla ({@code ServerboundMovePlayerPacket}, que leva a posição do MUNDO) não é
 * enviado; o estado local vai em {@code DeckMovePayload} ({@link DeckClient}). O sprint continua sendo avisado ao servidor.
 */
@Mixin(LocalPlayer.class)
abstract class DeckSendMixin {
    @Shadow
    abstract void sendIsSprintingIfNeeded();

    @Inject(method = "sendPosition", at = @At("HEAD"), cancellable = true)
    private void spacehorizons$deckSendPosition(CallbackInfo ci) {
        if (DeckClient.isAboard()) {
            this.sendIsSprintingIfNeeded();
            ci.cancel();
        }
    }
}
