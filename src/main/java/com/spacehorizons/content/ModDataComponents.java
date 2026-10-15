package com.spacehorizons.content;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.survival.fuel.CanisterFuel;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Data components próprios do mod. */
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, SpaceHorizons.MODID);

    /** Combustível de foguete guardado num galão, em mB (0 a {@link CanisterFuel#CAPACITY}). Sem o componente = vazio. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> FUEL_MB =
            COMPONENTS.registerComponentType("fuel_mb", builder -> builder
                    .persistent(CanisterFuel.CODEC)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    private ModDataComponents() {
    }
}
