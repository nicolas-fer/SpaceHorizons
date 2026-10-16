package com.spacehorizons.mixin.travel;

import com.spacehorizons.rocket.RocketEntity;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * O piloto sempre rastreia o próprio foguete. O vanilla só pareia uma entidade com um jogador se o chunk dela já foi enviado
 * a ele; a 30 a 60 blocos por tick o chunk do foguete pode estar na fila de envio, e o vanilla mandaria remover a entidade
 * com o piloto montado (o cliente o desmontaria e ele cairia). Para o chunk do foguete do próprio piloto a resposta é sempre sim.
 */
@Mixin(ChunkMap.class)
abstract class ChunkMapMixin {
    @Inject(method = "isChunkTracked", at = @At("HEAD"), cancellable = true)
    private void spacehorizons$pilotTracksOwnRocket(ServerPlayer player, int chunkX, int chunkZ, CallbackInfoReturnable<Boolean> cir) {
        if (player.getVehicle() instanceof RocketEntity rocket) {
            ChunkPos at = rocket.chunkPosition();
            if (at.x() == chunkX && at.z() == chunkZ) {
                cir.setReturnValue(true);
            }
        }
    }
}
