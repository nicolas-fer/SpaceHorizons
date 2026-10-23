package com.spacehorizons.moonbase;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.moonbase.room.SealedRoomTrigger;

import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registro dos gatilhos de conquista da base lunar. */
public final class MoonTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, SpaceHorizons.MODID);

    /** {@code spacehorizons:sealed_room}: respirando o ar de uma sala selada. */
    public static final DeferredHolder<CriterionTrigger<?>, SealedRoomTrigger> SEALED_ROOM =
            TRIGGERS.register("sealed_room", SealedRoomTrigger::new);

    private MoonTriggers() {
    }
}
