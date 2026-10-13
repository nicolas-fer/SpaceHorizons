package com.spacehorizons.mixin;

import com.spacehorizons.client.curvature.CurvatureState;

import net.minecraft.client.Camera;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** O plano distante da câmera limita as nuvens (max entre 4x a distância de render e o alcance das nuvens). */
@Mixin(Camera.class)
abstract class CameraMixin {
    @Redirect(
            method = "update",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F", ordinal = 0))
    private float spacehorizons$depthFar(float renderDepth, float cloudDepth) {
        return Math.max(renderDepth, CurvatureState.cloudRangeChunks(Math.round(cloudDepth / 16.0F)) * 16.0F);
    }
}
