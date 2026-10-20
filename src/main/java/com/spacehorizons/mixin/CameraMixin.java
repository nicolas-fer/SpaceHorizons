package com.spacehorizons.mixin;

import com.spacehorizons.client.curvature.CurvatureState;

import com.spacehorizons.client.rocket.RocketCamera;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    @Shadow
    protected abstract void setPosition(Vec3 position);

    /**
     * Piloto do foguete em primeira pessoa: o olho vai para a janela do cockpit (ver {@link RocketCamera#firstPersonEye}). Roda logo
     * depois de alinhar a câmera à entidade e antes do frustum e da perspectiva, que já leem a posição nova.
     */
    @Inject(
            method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void spacehorizons$rocketEye(DeltaTracker deltaTracker, CallbackInfo ci) {
        Camera self = (Camera) (Object) this;
        Vec3 eye = RocketCamera.firstPersonEye(self.getCameraEntityPartialTicks(deltaTracker));
        if (eye != null) {
            this.setPosition(eye);
        }
    }
}
