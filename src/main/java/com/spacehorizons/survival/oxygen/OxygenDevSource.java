package com.spacehorizons.survival.oxygen;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;

/**
 * Fonte de teste, ligada por jogador pelo comando /spacehorizons oxygen supply on|off. Abastece o consumo todo
 * enquanto estiver ligada (simula um tanque infinito). Não é salva: desliga ao reiniciar o servidor.
 */
final class OxygenDevSource implements OxygenSource {
    private static final Set<UUID> ENABLED = ConcurrentHashMap.newKeySet();

    static void set(ServerPlayer player, boolean on) {
        if (on) {
            ENABLED.add(player.getUUID());
        } else {
            ENABLED.remove(player.getUUID());
        }
    }

    @Override
    public int supply(ServerPlayer player) {
        return ENABLED.contains(player.getUUID()) ? OxygenMath.CONSUMPTION_PER_TICK : 0;
    }
}
