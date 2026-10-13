package com.spacehorizons.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.vertex.PoseStack;
import com.spacehorizons.client.space.SpaceSun;

import net.minecraft.client.renderer.SkyRenderer;

/**
 * Fixa o campo de estrelas no espaço (ver {@link SpaceSun#updateStars}): gira o campo do vanilla pela rotação entre o
 * referencial do spawn da Terra e o da câmera, para as estrelas ficarem no mesmo lugar antes e depois de uma troca de
 * dimensão. Sem rotação definida (planeta com atmosfera perto do chão), o vanilla segue como está.
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
}
