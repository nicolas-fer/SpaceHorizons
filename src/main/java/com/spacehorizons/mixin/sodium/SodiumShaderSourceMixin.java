package com.spacehorizons.mixin.sodium;

import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.spacehorizons.client.compat.SodiumCurvature;

import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * O Sodium entrega o shader de chunks pelo mesmo ShaderManager do vanilla (id sodium:blocks/block_layer_opaque),
 * então a reescrita acontece aqui, independente do mixin do terrain.vsh do vanilla.
 */
@Mixin(ShaderManager.Configs.class)
abstract class SodiumShaderSourceMixin {
    @Inject(method = "getShader", at = @At("RETURN"), cancellable = true)
    private void spacehorizons$curveSodiumTerrain(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
        String source = cir.getReturnValue();
        if (source == null || !SodiumCurvature.isChunkShader(id)) {
            return;
        }
        if (type == ShaderType.VERTEX) {
            cir.setReturnValue(SodiumCurvature.patchVertex(source));
        } else if (type == ShaderType.FRAGMENT) {
            cir.setReturnValue(SodiumCurvature.patchFragment(source));
        }
    }
}
