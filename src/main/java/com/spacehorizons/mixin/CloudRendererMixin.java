package com.spacehorizons.mixin;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.spacehorizons.client.curvature.CurvatureState;

import net.minecraft.client.renderer.CloudRenderer;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * O CloudInfo das nuvens ganha raio e altitude da câmera depois do vec3 CellSize (std140 empacota
 * floats nos 4 bytes sobrando do vec3, então ficam nos offsets 44 e 48), na ordem do bloco do shader.
 */
@Mixin(CloudRenderer.class)
abstract class CloudRendererMixin {
    @Unique
    private int spacehorizons$lastRange = Integer.MIN_VALUE;

    @Shadow
    public abstract void markForRebuild();

    /**
     * Num planeta o alcance das nuvens sobe para o mínimo do planeta (as nuvens planas do DH ficam desligadas, então
     * são as vanilla que cobrem o céu). Mudar o alcance recria o buffer de faces vazio: força a reconstrução da malha
     * antes de o prepare decidir se precisa dela (o vanilla só reconstrói ao mudar de célula).
     * ordinal 1 entre os int dos argumentos = range (ordinal 0 é color).
     */
    @ModifyVariable(
            method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1)
    private int spacehorizons$cloudRange(int range) {
        int effective = CurvatureState.cloudRangeChunks(range);
        if (effective != this.spacehorizons$lastRange) {
            this.spacehorizons$lastRange = effective;
            this.markForRebuild();
        }
        return effective;
    }

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
