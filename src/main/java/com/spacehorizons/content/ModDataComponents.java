package com.spacehorizons.content;

import com.spacehorizons.SpaceHorizons;
import com.mojang.serialization.Codec;
import com.spacehorizons.rocket.RocketSpecs;
import com.spacehorizons.rocket.blueprint.Blueprint;
import com.spacehorizons.survival.fuel.CanisterFuel;
import com.spacehorizons.survival.fuel.RefineryLogic;
import com.spacehorizons.survival.oxygen.OxygenStorage;

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

    /**
     * Combustível que estava no tanque de uma refinaria quebrada, em mB (0 a {@link RefineryLogic#TANK_CAPACITY}); fica no
     * item do bloco e volta ao tanque quando ele é colocado.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> REFINERY_TANK_MB =
            COMPONENTS.registerComponentType("refinery_tank_mb", builder -> builder
                    .persistent(Codec.intRange(0, RefineryLogic.TANK_CAPACITY))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * Oxigênio guardado num item (tanque de oxigênio etc.), em ticks de respiração (a unidade de
     * {@link com.spacehorizons.survival.oxygen.OxygenMath#CONSUMPTION_PER_TICK}). Use {@link OxygenStorage}.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> OXYGEN =
            COMPONENTS.registerComponentType("oxygen", builder -> builder
                    .persistent(Codec.intRange(0, Integer.MAX_VALUE))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * Capacidade de oxigênio de um item, em ticks de respiração. Um item com este componente é um recipiente de
     * oxigênio; o valor padrão vai nas Item.Properties do item (ex.: tanque pequeno e grande).
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> OXYGEN_CAPACITY =
            COMPONENTS.registerComponentType("oxygen_capacity", builder -> builder
                    .persistent(Codec.intRange(1, Integer.MAX_VALUE))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * Combustível que estava num tanque de foguete quebrado, em mB (0 a {@link RocketSpecs#TANK_CAPACITY_MB}); fica no
     * item do bloco e volta ao tanque quando ele é colocado.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ROCKET_TANK_MB =
            COMPONENTS.registerComponentType("rocket_tank_mb", builder -> builder
                    .persistent(Codec.intRange(0, RocketSpecs.TANK_CAPACITY_MB))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * A planta de montagem guardada no item {@code rocket_blueprint} (docs/PLANO_TIERS.md, seção 4). Sem o componente = planta em branco (que
     * salva o foguete de uma plataforma).
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Blueprint>> ROCKET_BLUEPRINT =
            COMPONENTS.registerComponentType("rocket_blueprint", builder -> builder
                    .persistent(Blueprint.CODEC)
                    .networkSynchronized(Blueprint.STREAM_CODEC));

    /**
     * Carga de um motor do Tier 2 quebrado (núcleo do nuclear ou hélio-3 do de fusão), em mB; fica no item do bloco e volta ao motor quando ele é
     * colocado.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENGINE_CHARGE_MB =
            COMPONENTS.registerComponentType("engine_charge_mb", builder -> builder
                    .persistent(Codec.intRange(0, Integer.MAX_VALUE))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    private ModDataComponents() {
    }
}
