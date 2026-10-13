package com.spacehorizons.mixin;

import java.nio.ByteBuffer;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.spacehorizons.client.curvature.CurvatureState;

import net.minecraft.client.renderer.DynamicGpuData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Escreve raio e altitude da câmera logo depois de mat4 + ivec2, na ordem do bloco do shader. */
@Mixin(DynamicGpuData.TerrainTransform.class)
abstract class TerrainTransformMixin {
    @Inject(method = "write", at = @At("TAIL"))
    private void spacehorizons$writeCurvature(ByteBuffer buffer, CallbackInfo ci) {
        CurvatureState.Frame frame = CurvatureState.current();
        Std140Builder.intoBuffer(buffer)
                .putFloat((float) frame.radius())
                .putFloat((float) frame.cameraAltitude());
    }
}
