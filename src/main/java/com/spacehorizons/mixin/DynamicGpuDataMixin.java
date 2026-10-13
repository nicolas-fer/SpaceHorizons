package com.spacehorizons.mixin;

import com.spacehorizons.client.curvature.CurvatureState;

import net.minecraft.client.renderer.DynamicGpuData;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DynamicGpuData.class)
abstract class DynamicGpuDataMixin {
    /** O buffer do TerrainUniform precisa caber os dois floats extras; troca só o tamanho usado no construtor. */
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/DynamicGpuData;TERRAIN_TRANSFORM_UBO_SIZE:I",
                    opcode = Opcodes.GETSTATIC))
    private int spacehorizons$terrainUboSize() {
        return CurvatureState.TERRAIN_UBO_SIZE;
    }
}
