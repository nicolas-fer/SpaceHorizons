package com.spacehorizons.mixin;

import java.nio.ByteBuffer;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.spacehorizons.client.curvature.CurvatureState;

import net.minecraft.client.renderer.DynamicGpuData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Escreve raio e altitude da câmera logo depois de mat4 + mat4 + vec4 + vec3, na ordem do bloco
 * DynamicTransforms (ver CurvatureShaders.patchDynamicTransformsInclude). É o bloco por desenho que
 * entidades, partículas, itens, texto no mundo, contornos e a rachadura de blocos já usam, e que a
 * GUI também escreve: o shader ignora a curvatura quando a projeção é ortográfica.
 */
@Mixin(DynamicGpuData.Transform.class)
abstract class DynamicTransformMixin {
    @Inject(method = "write", at = @At("TAIL"))
    private void spacehorizons$writeCurvature(ByteBuffer buffer, CallbackInfo ci) {
        CurvatureState.Frame frame = CurvatureState.current();
        Std140Builder.intoBuffer(buffer)
                .putFloat((float) frame.radius())
                .putFloat((float) frame.cameraAltitude());
    }
}
