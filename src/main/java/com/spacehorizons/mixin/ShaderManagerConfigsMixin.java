package com.spacehorizons.mixin;

import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.spacehorizons.client.curvature.CurvatureShaders;

import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShaderManager.Configs.class)
abstract class ShaderManagerConfigsMixin {
    @Inject(method = "getShader", at = @At("RETURN"), cancellable = true)
    private void spacehorizons$curveTerrain(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
        String source = cir.getReturnValue();
        if (source == null) {
            return;
        }
        if (CurvatureShaders.isCloudsVertex(id)) {
            // O fsh das nuvens não declara CloudInfo, então só o vsh muda.
            if (type == ShaderType.VERTEX) {
                cir.setReturnValue(CurvatureShaders.patchCloudsVertex(source));
            }
            return;
        }
        if (CurvatureShaders.isWorldVertex(id)) {
            // fsh dessas passagens não projeta nada; o bloco DynamicTransforms vem do include já ajustado
            if (type == ShaderType.VERTEX) {
                cir.setReturnValue(CurvatureShaders.patchWorldVertex(source));
            }
            return;
        }
        if (!CurvatureShaders.isTerrainVertex(id)) {
            return;
        }
        if (type == ShaderType.VERTEX) {
            cir.setReturnValue(CurvatureShaders.patchTerrainVertex(source));
        } else if (type == ShaderType.FRAGMENT) {
            cir.setReturnValue(CurvatureShaders.patchTerrainFragment(source));
        }
    }
}
