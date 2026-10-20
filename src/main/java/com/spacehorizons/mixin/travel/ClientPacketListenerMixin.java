package com.spacehorizons.mixin.travel;

import com.spacehorizons.client.travel.TravelClientState;

import com.spacehorizons.rocket.RocketEntity;

import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    /**
     * O vanilla mostra "Aperte Shift para desmontar" ao montar (e de novo a cada troca de dimensão, que reenvia os passageiros).
     * No foguete isso é falso: Shift é o acelerador e desmontar é a tecla própria, só pousado. A dica certa vem de RocketPilot.
     */
    @Redirect(
            method = "handleSetEntityPassengersPacket",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void spacehorizons$noMountMessage(Hud hud, Component message, boolean animate) {
        if (!spacehorizons$ridingRocket()) {
            hud.setOverlayMessage(message, animate);
        }
    }

    /** O mesmo aviso, lido em voz alta pelo narrador. */
    @Redirect(
            method = "handleSetEntityPassengersPacket",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/GameNarrator;saySystemNow(Lnet/minecraft/network/chat/Component;)V"))
    private void spacehorizons$noMountNarration(GameNarrator narrator, Component message) {
        if (!spacehorizons$ridingRocket()) {
            narrator.saySystemNow(message);
        }
    }

    @Unique
    private static boolean spacehorizons$ridingRocket() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getVehicle() instanceof RocketEntity;
    }
}
