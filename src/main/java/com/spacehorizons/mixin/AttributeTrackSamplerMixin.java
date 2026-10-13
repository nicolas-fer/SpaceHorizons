package com.spacehorizons.mixin;

import com.spacehorizons.client.space.SpaceSun;

import net.minecraft.client.ClientClockManager;
import net.minecraft.world.clock.ClockInstance;
import net.minecraft.world.timeline.AttributeTrackSampler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * O ponto mais estreito por onde o cliente avalia céu e luz do chão: toda trilha de timeline (cor do céu, brilho do nascer
 * e do pôr do sol, estrelas, luz do céu usada no lightmap...) lê o tick do relógio aqui. Num planeta do mod o relógio do
 * Overworld é trocado pelo horário efetivo do SunModel no ponto da câmera, e o vanilla passa a desenhar o dia, a noite e a
 * luz do chão do sol real do lugar.
 *
 * Só relógios do cliente: o servidor (ServerClockManager) segue no relógio real, e {@link SpaceSun#effectiveTicks} deixa
 * passar qualquer relógio que não seja o do Overworld e qualquer nível que não seja de um planeta do mod.
 */
@Mixin(AttributeTrackSampler.class)
abstract class AttributeTrackSamplerMixin {
    @Redirect(
            method = "applyTimeBased",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/clock/ClockInstance;totalTicks()J"))
    private long spacehorizons$effectiveTicks(ClockInstance instance) {
        long ticks = instance.totalTicks();
        if (!(instance instanceof ClientClockManager.ClientClockInstance)) {
            return ticks;
        }
        return SpaceSun.effectiveTicks(instance, ticks);
    }
}
