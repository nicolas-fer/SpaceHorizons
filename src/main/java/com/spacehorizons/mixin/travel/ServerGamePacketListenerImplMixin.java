package com.spacehorizons.mixin.travel;

import com.spacehorizons.planet.Planets;
import com.spacehorizons.ship.deck.DeckCrew;
import com.spacehorizons.space.SpaceDimension;
import com.spacehorizons.space.travel.TravelMath;

import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * O voo acelerado e o piloto automático andam dezenas de blocos por tick; o "moved too quickly" do vanilla (que no
 * singleplayer já é ignorado) devolveria o jogador para trás num servidor dedicado. Acima de uma altitude baixa,
 * num level-planeta, e em qualquer ponto do espaço aberto, a checagem de velocidade fica desligada.
 *
 * <p>Convés andável (S5): o tripulante a pé numa nave em movimento tem a posição no referencial da nave; o pacote de posição do mundo que o
 * cliente ainda possa mandar é descartado (a posição do mundo é derivada da local, por {@link DeckCrew}), e a regra "voando" do vanilla
 * (expulsa quem fica no ar sem chão de bloco do mundo por mais de 80 ticks) não vale para ele.
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Shadow
    private boolean clientIsFloating;

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

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void spacehorizons$deckCrewIgnoresWorldMoves(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl) (Object) this, this.player.level());
        if (DeckCrew.isCrew(this.player)) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void spacehorizons$deckCrewNotFloating(CallbackInfo ci) {
        if (DeckCrew.isCrew(this.player)) {
            this.clientIsFloating = false;
        }
    }

    /** Conta as correções de posição (teletransportes) que o servidor manda, para o autoteste do convés. */
    @Inject(method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V", at = @At("HEAD"))
    private void spacehorizons$countTeleports(PositionMoveRotation destination, Set<Relative> relatives, CallbackInfo ci) {
        DeckCrew.TELEPORTS.incrementAndGet();
    }
}
