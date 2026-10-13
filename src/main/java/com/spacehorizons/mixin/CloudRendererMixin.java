package com.spacehorizons.mixin;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.spacehorizons.client.curvature.CurvatureState;

import net.minecraft.client.renderer.CloudRenderer;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * O CloudInfo das nuvens ganha raio e altitude da câmera depois do vec3 CellSize (std140 empacota
 * floats nos 4 bytes sobrando do vec3, então ficam nos offsets 44 e 48), na ordem do bloco do shader.
 */
@Mixin(CloudRenderer.class)
abstract class CloudRendererMixin {
    /** O buffer precisa caber os dois floats extras; troca só o tamanho usado no construtor. */
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/CloudRenderer;UBO_SIZE:I",
                    opcode = Opcodes.GETSTATIC))
    private int spacehorizons$cloudUboSize() {
        return CurvatureState.CLOUD_UBO_SIZE;
    }

    /** Segundo putVec3 do prepare é o CellSize, o último membro do bloco vanilla. */
    @Redirect(
            method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putVec3(FFF)Lcom/mojang/blaze3d/buffers/Std140Builder;",
                    ordinal = 1))
    private Std140Builder spacehorizons$writeCurvature(Std140Builder builder, float x, float y, float z) {
        CurvatureState.Frame frame = CurvatureState.current();
        return builder.putVec3(x, y, z)
                .putFloat((float) frame.radius())
                .putFloat((float) frame.cameraAltitude());
    }
}
