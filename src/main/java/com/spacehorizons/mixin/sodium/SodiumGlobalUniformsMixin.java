package com.spacehorizons.mixin.sodium;

import java.nio.ByteBuffer;

import com.spacehorizons.client.compat.SodiumCurvature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registro do u_Globals do Sodium (UniformBufferManager.GlobalUniforms, package-private, por isso alvo por nome).
 * Os dois floats entram antes do Std140Builder.get(), que vira o buffer; o bloco do Sodium usa 184 dos 256
 * bytes reservados por entrada, então há espaço.
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager$GlobalUniforms")
abstract class SodiumGlobalUniformsMixin {
    @Inject(
            method = "write",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/buffers/Std140Builder;get()Ljava/nio/ByteBuffer;"))
    private void spacehorizons$writeCurvature(ByteBuffer buffer, CallbackInfo ci) {
        SodiumCurvature.writeUniforms(buffer);
    }
}
