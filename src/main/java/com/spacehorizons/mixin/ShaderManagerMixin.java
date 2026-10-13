package com.spacehorizons.mixin;

import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.spacehorizons.client.curvature.CurvatureShaders;

import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ShaderManager.class)
abstract class ShaderManagerMixin {
    /**
     * Os includes são lidos uma vez por recarga e compartilhados por todos os shaders. O dynamictransforms.glsl
     * ganha os dois floats da curvatura aqui, para vsh e fsh (que o incluem) continuarem com o bloco idêntico.
     */
    @Redirect(
            method = "loadInclude",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/renderpearl/api/pipeline/ShaderSource$CachedIncludeSource;create(Lnet/minecraft/resources/Identifier;Ljava/lang/String;)Lcom/mojang/renderpearl/api/pipeline/ShaderSource$CachedIncludeSource;"))
    private static ShaderSource.CachedIncludeSource spacehorizons$patchInclude(Identifier id, String source) {
        return ShaderSource.CachedIncludeSource.create(id, CurvatureShaders.patchInclude(id, source));
    }
}
