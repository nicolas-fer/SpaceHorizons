package com.spacehorizons.mixin.travel;

import com.spacehorizons.client.travel.TravelClientState;

import net.minecraft.client.multiplayer.ClientPacketListener;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Transferência contínua entre corpos: o respawn que vem de uma transferência nossa não abre a tela de carregamento
 * nem cria o LevelLoadTracker. Sem tela, ninguém avisaria o servidor de que o cliente terminou de carregar; então, no
 * fim do tratamento do respawn, o cliente manda o ServerboundPlayerLoadedPacket na hora (o jogador está no espaço,
 * não há chunk a esperar; os chunks chegam normalmente em seguida, como em qualquer entrada em chunks vazios).
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
    /** true durante o tratamento de um respawn que veio de uma transferência nossa. */
    @Unique
    private boolean spacehorizons$seamless;

    @Shadow
    protected abstract void notifyPlayerLoaded();

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void spacehorizons$beginRespawn(CallbackInfo ci) {
        this.spacehorizons$seamless = TravelClientState.consumePending();
    }

    /** Impede a tela e o tracker: sem LevelLoadTracker, nada mais no cliente espera o carregamento. */
    @Inject(
            method = "startWaitingForNewLevel(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/client/multiplayer/ClientLevel;"
                    + "Lnet/minecraft/client/gui/screens/LevelLoadingScreen$Reason;Lnet/minecraft/resources/ResourceKey;"
                    + "Lnet/minecraft/resources/ResourceKey;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void spacehorizons$skipLoadingScreen(CallbackInfo ci) {
        if (this.spacehorizons$seamless) {
            ci.cancel();
        }
    }

    @Inject(method = "handleRespawn", at = @At("TAIL"))
    private void spacehorizons$endRespawn(CallbackInfo ci) {
        if (this.spacehorizons$seamless) {
            this.spacehorizons$seamless = false;
            // o novo jogador nasce na origem; sem isto o primeiro quadro seria desenhado de lá, com o olhar antigo
            net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) {
                TravelClientState.applyTo(player);
            }
            // handleRespawn já pôs clientLoaded=false; isto avisa o servidor e volta a true
            this.notifyPlayerLoaded();
        }
    }
}
