package com.spacehorizons.mixin.travel;

import com.spacehorizons.client.ship.DeckClient;
import com.spacehorizons.client.ship.DeckObserver;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Convés andável (S5): o modelo do jogador local a bordo é desenhado na posição exata do quadro ({@code DeckCamera}), que numa nave a 100 blocos por tick
 * fica dezenas de blocos da posição interpolada da entidade; o descarte por campo de visão usa a caixa da entidade e o esconderia. A bordo, o jogador
 * local nunca é descartado. Os tripulantes vistos por outros clientes ({@link DeckObserver}) também: a caixa da entidade está até um tick de nave
 * longe de onde o modelo é desenhado.
 */
@Mixin(EntityRenderer.class)
abstract class DeckCullingMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void spacehorizons$deckPlayerAlwaysRenders(Entity entity, Frustum culler, double camX, double camY, double camZ, float partialTicks,
            CallbackInfoReturnable<Boolean> cir) {
        if ((DeckClient.isAboard() && entity == Minecraft.getInstance().player) || DeckObserver.isObserved(entity)) {
            cir.setReturnValue(true);
        }
    }
}
