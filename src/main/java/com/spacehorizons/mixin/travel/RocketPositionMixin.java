package com.spacehorizons.mixin.travel;

import com.spacehorizons.rocket.RocketEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.PositionPath;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A posição do foguete no cliente nunca dá "snap": os três pacotes de posição (sincronia, teleporte e delta) viram sempre um alvo de
 * interpolação do foguete ({@link RocketEntity}, um passo por tick). O vanilla, quando o alvo está a mais de 64 blocos da posição
 * atual (a 54 blocos por tick com a interpolação atrasada, ou numa rajada de pacotes depois de um tick lento), faz {@code snapTo} no
 * foguete, redefinindo a posição ANTERIOR dele, mas não a do piloto, que só é reposicionado no tick: o desenho interpolava um passo
 * para o foguete e outro (de 2 passos) para o piloto, e o foguete aparecia até 80 blocos adiantado, atrasado ou andando para trás em
 * relação à câmera. Com tudo passando pelo mesmo caminho, foguete e passageiros andam o mesmo passo no mesmo tick.
 */
@Mixin(ClientPacketListener.class)
abstract class RocketPositionMixin {
    @Shadow
    private ClientLevel level;

    @Inject(method = "handleEntityPositionSync", at = @At("HEAD"), cancellable = true)
    private void spacehorizons$rocketPositionSync(ClientboundEntityPositionSyncPacket packet, CallbackInfo ci) {
        PacketUtils.ensureRunningOnSameThread(packet, (ClientPacketListener) (Object) this, Minecraft.getInstance().packetProcessor());
        if (this.level.getEntity(packet.id()) instanceof RocketEntity rocket) {
            PositionPath path = packet.position();
            rocket.getPositionCodec().setBase(path.endPosition());
            rocket.moveOrInterpolateTo(path, packet.yRot(), packet.xRot());
            rocket.setOnGround(packet.onGround());
            ci.cancel();
        }
    }

    @Inject(method = "handleTeleportEntity", at = @At("HEAD"), cancellable = true)
    private void spacehorizons$rocketTeleport(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
        PacketUtils.ensureRunningOnSameThread(packet, (ClientPacketListener) (Object) this, Minecraft.getInstance().packetProcessor());
        if (this.level.getEntity(packet.id()) instanceof RocketEntity rocket) {
            PositionMoveRotation now = PositionMoveRotation.of(rocket);
            PositionMoveRotation target = PositionMoveRotation.calculateAbsolute(now, packet.change(), packet.relatives());
            rocket.getPositionCodec().setBase(target.position());
            rocket.moveOrInterpolateTo(target.position(), target.yRot(), target.xRot());
            rocket.setDeltaMovement(target.deltaMovement());
            rocket.setOnGround(packet.onGround());
            ci.cancel();
        }
    }

    @Inject(method = "handleMoveEntity", at = @At("HEAD"), cancellable = true)
    private void spacehorizons$rocketMove(ClientboundMoveEntityPacket packet, CallbackInfo ci) {
        PacketUtils.ensureRunningOnSameThread(packet, (ClientPacketListener) (Object) this, Minecraft.getInstance().packetProcessor());
        if (packet.getEntity(this.level) instanceof RocketEntity rocket) {
            if (packet.hasPosition()) {
                var codec = rocket.getPositionCodec();
                PositionPath path = packet.getPositionDelta().decode(codec);
                codec.setBase(path.endPosition());
                rocket.moveOrInterpolateTo(path);
            }
            rocket.setOnGround(packet.isOnGround());
            ci.cancel();
        }
    }
}
