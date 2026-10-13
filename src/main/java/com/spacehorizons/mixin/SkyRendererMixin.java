package com.spacehorizons.mixin;

import org.joml.Matrix4f;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.vertex.PoseStack;
import com.spacehorizons.client.space.SpaceSun;

import net.minecraft.client.renderer.SkyRenderer;

/**
 * Ajustes do céu vanilla para o sol real (ver {@link SpaceSun}):
 * <ul>
 *   <li>estrelas: gira o campo do vanilla pela rotação entre o referencial do spawn da Terra e o da câmera, para as
 *       estrelas ficarem fixas no espaço e no mesmo lugar antes e depois de uma troca de dimensão;</li>
 *   <li>sol e brilho do nascer/pôr: o vanilla só sabe girar o sol no plano leste-cima; o giro em torno da vertical do
 *       SunModel leva o disco (que já tem a altura certa) ao azimute real, o que inclina o caminho em latitudes altas.</li>
 * </ul>
 * Fora dos planetas do mod nada é definido e o vanilla segue como está.
 */
@Mixin(SkyRenderer.class)
abstract class SkyRendererMixin {
    @Inject(method = "renderStars", at = @At("HEAD"))
    private void spacehorizons$rotateStars(RenderPass renderPass, float starBrightness, PoseStack poseStack, CallbackInfo ci) {
        Matrix4f rotation = SpaceSun.starRotation();
        if (rotation != null) {
            poseStack.last().pose().mulLocal(rotation);
        }
    }

    /** A pose do sol é empilhada só para ele (o chamador a desempilha): dá para girar sem desfazer. */
    @Inject(method = "renderSun", at = @At("HEAD"))
    private void spacehorizons$tiltSun(RenderPass renderPass, float rainBrightness, PoseStack poseStack, CallbackInfo ci) {
        float tilt = SpaceSun.sunTilt();
        if (tilt != 0.0F) {
            poseStack.last().pose().mulLocal(new Matrix4f().rotationY(tilt));
        }
    }

    /** O brilho usa a pose-raiz, que seguirá em uso depois: empilha uma cópia no início e desempilha em todo retorno. */
    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"))
    private void spacehorizons$tiltSunriseBegin(RenderPass renderPass, PoseStack poseStack, float sunAngle,
            Vector4fc sunriseAndSunsetColor, CallbackInfo ci) {
        poseStack.pushPose();
        float tilt = SpaceSun.sunTilt();
        if (tilt != 0.0F) {
            poseStack.last().pose().mulLocal(new Matrix4f().rotationY(tilt));
        }
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("RETURN"))
    private void spacehorizons$tiltSunriseEnd(RenderPass renderPass, PoseStack poseStack, float sunAngle,
            Vector4fc sunriseAndSunsetColor, CallbackInfo ci) {
        poseStack.popPose();
    }
}
