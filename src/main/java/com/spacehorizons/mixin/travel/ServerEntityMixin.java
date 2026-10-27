package com.spacehorizons.mixin.travel;

import com.spacehorizons.ship.deck.DeckCrew;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Convés andável (fase 2e): a posição do tripulante no mundo anda com a nave (dezenas de blocos por tick), e o cliente de um observador interpola o
 * jogador remoto por conta própria (3 ticks), então ele o veria muito atrás do casco. Para quem está no convés o servidor NÃO manda aos outros clientes
 * os pacotes de posição, rotação e cabeça do vanilla: o estado local vai em {@code DeckObservePayload} e o observador põe o jogador pela pose da nave.
 * Os outros pacotes da entidade (dados, movimento, passageiros) seguem iguais.
 */
@Mixin(ServerEntity.class)
abstract class ServerEntityMixin {
    @Shadow
    @Final
    private Entity entity;

    @Redirect(method = "sendChanges", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerEntity$Synchronizer;sendToTrackingPlayers(Lnet/minecraft/network/protocol/Packet;)V"))
    private void spacehorizons$deckCrewNoVanillaPosition(ServerEntity.Synchronizer synchronizer, Packet<? super ClientGamePacketListener> packet) {
        if (DeckCrew.isCrew(this.entity) && (packet instanceof ClientboundMoveEntityPacket || packet instanceof ClientboundEntityPositionSyncPacket
                || packet instanceof ClientboundRotateHeadPacket)) {
            return;
        }
        synchronizer.sendToTrackingPlayers(packet);
    }
}
