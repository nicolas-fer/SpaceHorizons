package com.spacehorizons.survival.oxygen;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntSupplier;

import net.minecraft.server.level.ServerPlayer;

/** Registro das {@link OxygenSource}; registre durante a inicialização do mod. */
public final class OxygenSources {
    private static final List<OxygenSource> SOURCES = new CopyOnWriteArrayList<>();

    private OxygenSources() {
    }

    public static void register(OxygenSource source) {
        SOURCES.add(source);
    }

    /** Abastecimento total do jogador neste tick, somando todas as fontes. */
    public static int total(ServerPlayer player) {
        List<IntSupplier> suppliers = SOURCES.stream().<IntSupplier>map(source -> () -> source.supply(player)).toList();
        return OxygenMath.totalSupply(suppliers);
    }
}
