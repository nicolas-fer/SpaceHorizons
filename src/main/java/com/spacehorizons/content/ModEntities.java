package com.spacehorizons.content;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.RocketEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Tipos de entidade do mod. */
public final class ModEntities {
    public static final DeferredRegister.Entities TYPES = DeferredRegister.createEntities(SpaceHorizons.MODID);

    /**
     * Foguete montado. {@code clientTrackingRange(16)} e {@code updateInterval(1)} porque se move rápido e é grande (o
     * vanilla usa 5 chunks e 3 ticks). {@code noSummon}: um foguete sem retrato (do /summon) não serve para nada.
     * O tamanho é só o inicial: a caixa real vem do retrato ({@link RocketEntity#getDimensions}).
     */
    public static final DeferredHolder<EntityType<?>, EntityType<RocketEntity>> ROCKET = TYPES.registerEntityType("rocket",
            RocketEntity::new, MobCategory.MISC,
            builder -> builder.sized(1.0F, 1.0F).clientTrackingRange(16).updateInterval(1).noSummon());

    private ModEntities() {
    }
}
