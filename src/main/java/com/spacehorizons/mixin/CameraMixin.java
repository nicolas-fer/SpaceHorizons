package com.spacehorizons.mixin;

import com.spacehorizons.client.curvature.CurvatureState;

import com.spacehorizons.client.rocket.RocketCamera;
import com.spacehorizons.client.ship.CameraSeam;
import com.spacehorizons.client.ship.DeckClient;
import com.spacehorizons.client.ship.DeckObserver;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

    /** Troca sem costura (fase 2g): no fim do quadro a posição final da câmera recebe a mistura da última troca de fonte (ver {@link CameraSeam}). */
    @Inject(method = "update", at = @At("RETURN"))
    private void spacehorizons$seam(DeltaTracker deltaTracker, CallbackInfo ci) {
        Vec3 adjusted = CameraSeam.adjustPosition(((Camera) (Object) this).position());
        if (adjusted != null) {
            this.setPosition(adjusted);
        }
    }

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void move(float forwards, float up, float right);

    @Shadow
    private boolean detached;

    @Shadow
    protected abstract float getMaxZoom(float cameraDist);

    /**
     * Piloto do foguete em primeira pessoa: o olho vai para a janela do cockpit (ver {@link RocketCamera#firstPersonEye}). Roda logo
     * depois de alinhar a câmera à entidade e antes do frustum e da perspectiva, que já leem a posição nova.
     *
     * <p>Tripulante a pé no convés (S5): o olho é o do referencial da nave, na pose interpolada do quadro ({@link DeckClient#eyeWorld}); em
     * terceira pessoa a câmera recua dali pelo olhar já composto com a atitude, como o vanilla faz a partir da posição da entidade.
     */
    @Inject(
            method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void spacehorizons$rocketEye(DeltaTracker deltaTracker, CallbackInfo ci) {
        Camera self = (Camera) (Object) this;
        float partialTick = self.getCameraEntityPartialTicks(deltaTracker);
        Vec3 eye = RocketCamera.firstPersonEye(partialTick);
        if (eye != null) {
            this.setPosition(eye);
            return;
        }
        Vec3 deckEye = DeckClient.eyeWorld(partialTick);
        if (deckEye != null) {
            this.setPosition(deckEye);
            if (this.detached) {
                var player = Minecraft.getInstance().player;
                float distance = player == null ? 4.0F : (float) player.getAttributeValue(Attributes.CAMERA_DISTANCE);
                this.move(-this.getMaxZoom(distance * (player == null ? 1.0F : player.getScale())), 0.0F, 0.0F);
            }
            return;
        }
        // só no autoteste de dois clientes: o observador parado num ponto fixo do referencial da nave
        Vec3 observerEye = DeckObserver.observerEye(partialTick);
        if (observerEye != null) {
            this.setPosition(observerEye);
        }
    }
}
