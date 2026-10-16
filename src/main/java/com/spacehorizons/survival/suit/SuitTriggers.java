package com.spacehorizons.survival.suit;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registro dos gatilhos de conquista do traje. */
public final class SuitTriggers {
    private static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, SpaceHorizons.MODID);

    /** {@code spacehorizons:suit_breathing}: segundos seguidos respirando do traje no vácuo. */
    public static final DeferredHolder<CriterionTrigger<?>, SuitBreathingTrigger> SUIT_BREATHING =
            TRIGGERS.register("suit_breathing", SuitBreathingTrigger::new);

    private SuitTriggers() {
    }

    static void register(IEventBus modEventBus) {
        TRIGGERS.register(modEventBus);
    }
}
