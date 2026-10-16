package com.spacehorizons.mixin.travel;

import com.spacehorizons.space.SpaceDimension;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * O vácuo do mundo não existe no espaço aberto. A dimensão tem 16 blocos de altura (min_y 0) e as posições do espaço
 * ({@link SpaceDimension}: y = x do espaço + viés) passam de 80.000 e chegam a -40.000 perto da Lua, em metade do mês
 * lunar; abaixo de {@code min_y - 64} o vanilla mata a entidade ({@code Entity.checkBelowWorld}). Ali não há "baixo".
 */
@Mixin(Entity.class)
abstract class EntityMixin {
    @Inject(method = "checkBelowWorld", at = @At("HEAD"), cancellable = true)
    private void spacehorizons$noVoidInSpace(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        Level level = self.level();
        if (self.getY() < level.getMinY() - 64 && SpaceDimension.is(level)) {
            ci.cancel();
        }
    }
}
