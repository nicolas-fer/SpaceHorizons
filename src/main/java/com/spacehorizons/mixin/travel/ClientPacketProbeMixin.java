package com.spacehorizons.mixin.travel;

import com.spacehorizons.client.devtest.JumpProbe;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Só dev (autoteste "jump"): avisa a {@link JumpProbe} de cada pacote de posição e de chunk que chega ao cliente. Sem a medição
 * ligada ({@link JumpProbe#active}) cada gancho custa a leitura de uma flag.
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketProbeMixin {
    @Shadow
    private ClientLevel level;

    @Inject(method = "handleTeleportEntity", at = @At("HEAD"))
    private void spacehorizons$probeTeleport(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
        if (JumpProbe.active()) {
            Vec3 p = packet.change().position();
            JumpProbe.packet("teleport", packet.id(), p.x + "," + p.z);
        }
    }

    @Inject(method = "handleEntityPositionSync", at = @At("HEAD"))
    private void spacehorizons$probePositionSync(ClientboundEntityPositionSyncPacket packet, CallbackInfo ci) {
        if (JumpProbe.active()) {
            Vec3 p = packet.position().endPosition();
            JumpProbe.packet("possync", packet.id(), p.x + "," + p.z);
        }
    }

    @Inject(method = "handleMoveEntity", at = @At("HEAD"))
    private void spacehorizons$probeMove(ClientboundMoveEntityPacket packet, CallbackInfo ci) {
        if (JumpProbe.active()) {
            Entity e = packet.getEntity(this.level);
            JumpProbe.packet("move", e == null ? -1 : e.getId(), "");
        }
    }

    @Inject(method = "handleSetEntityMotion", at = @At("HEAD"))
    private void spacehorizons$probeMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        if (JumpProbe.active()) {
            JumpProbe.packet("motion", packet.id(), packet.movement().x + "," + packet.movement().z);
        }
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void spacehorizons$probePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        if (JumpProbe.active()) {
            JumpProbe.packet("playerpos", -1, packet.change().position().x + "," + packet.change().position().z);
        }
    }

    @Inject(method = "handleMoveVehicle", at = @At("HEAD"))
    private void spacehorizons$probeVehicle(ClientboundMoveVehiclePacket packet, CallbackInfo ci) {
        if (JumpProbe.active()) {
            JumpProbe.packet("vehicle", -1, "");
        }
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("HEAD"))
    private void spacehorizons$probeChunk(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        if (JumpProbe.active()) {
            JumpProbe.packet("chunk", -1, packet.x() + "," + packet.z());
        }
    }

    @Inject(method = "handleSetChunkCacheCenter", at = @At("HEAD"))
    private void spacehorizons$probeCenter(ClientboundSetChunkCacheCenterPacket packet, CallbackInfo ci) {
        if (JumpProbe.active()) {
            JumpProbe.packet("center", -1, packet.getX() + "," + packet.getZ());
        }
    }
}
