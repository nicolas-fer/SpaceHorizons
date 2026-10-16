package com.spacehorizons.survival.oxygen;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntSupplier;

import net.minecraft.server.level.ServerPlayer;

/**
 * Registro das {@link OxygenSource}; registre durante a inicialização do mod. A ordem de registro é a prioridade:
 * registre as fontes gratuitas (sala selada, fonte de teste) antes das que gastam estoque (o tanque do traje).
 */
public final class OxygenSources {
    private static final List<OxygenSource> SOURCES = new CopyOnWriteArrayList<>();

    private OxygenSources() {
    }

    public static void register(OxygenSource source) {
        SOURCES.add(source);
    }

    /**
     * Abastecimento total do jogador neste tick. Consulta as fontes na ordem de registro e para quando o consumo já
     * está coberto (as fontes seguintes não são chamadas, então não gastam estoque à toa).
     */
    public static int total(ServerPlayer player) {
        List<IntSupplier> suppliers = SOURCES.stream().<IntSupplier>map(source -> () -> source.supply(player)).toList();
        return OxygenMath.totalSupplyUntilCovered(suppliers);
    }
}
