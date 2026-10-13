package com.spacehorizons.mixin;

import com.spacehorizons.client.curvature.CurvatureState;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A névoa das nuvens (cloudEnd) usa o alcance das nuvens; num planeta, o alcance ampliado. */
@Mixin(AtmosphericFogEnvironment.class)
abstract class AtmosphericFogEnvironmentMixin {
    @Redirect(
            method = "setupFog",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0))
    private Object spacehorizons$cloudRange(OptionInstance<?> option) {
        Object value = option.get();
        if (value instanceof Integer range) {
            return CurvatureState.cloudRangeChunks(range);
        }
        return value;
    }
}
