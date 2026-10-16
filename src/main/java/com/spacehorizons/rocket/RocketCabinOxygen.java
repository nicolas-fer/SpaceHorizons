package com.spacehorizons.rocket;

import com.spacehorizons.survival.oxygen.OxygenMath;
import com.spacehorizons.survival.oxygen.OxygenSource;

import net.minecraft.server.level.ServerPlayer;

/**
 * Ar da cabine: quem está sentado num foguete respira o ar pressurizado do cockpit (não gasta os tanques do traje). Fica
 * registrada antes da fonte do traje, para o traje só puxar dos tanques fora do foguete.
 */
public final class RocketCabinOxygen implements OxygenSource {
    @Override
    public int supply(ServerPlayer player) {
        return player.getVehicle() instanceof RocketEntity ? OxygenMath.CONSUMPTION_PER_TICK : 0;
    }
}
