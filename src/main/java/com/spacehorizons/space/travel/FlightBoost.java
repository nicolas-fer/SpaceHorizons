package com.spacehorizons.space.travel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceDimension;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;

/**
 * Velocidade de voo criativo proporcional à altitude: sem isto os ~47.000 blocos Terra–Lua são impraticáveis.
 * Quem manda é o servidor (Abilities.flyingSpeed, enviado ao cliente), então funciona em servidor dedicado e persiste
 * na troca de dimensão. Perto do chão a velocidade volta ao padrão.
 *
 * Limitação: a velocidade de voo sem boost é sempre {@link TravelMath#BASE_FLYING_SPEED} (ou menos, se alguém reduziu);
 * uma velocidade de voo maior definida por outro meio é rebaixada. Só roda na thread principal do servidor.
 */
final class FlightBoost {
    private record Applied(float base, float applied) {
    }

    private static final Map<UUID, Applied> APPLIED = new HashMap<>();

    private FlightBoost() {
    }

    static void update(ServerPlayer player, PlanetDefinition planet) {
        apply(player, TravelMath.flightSpeedMultiplier(player.getY() - planet.seaLevel()));
    }

    /**
     * No espaço aberto: a mesma regra, pela altitude sobre a superfície do corpo MAIS PRÓXIMO. Na saída de um planeta a
     * altitude é a mesma dos dois lados da troca, então a velocidade de voo não dá salto.
     */
    static void updateInSpace(ServerPlayer player, double clock) {
        double[] space = new double[3];
        SpaceDimension.toSpace(player.getX(), player.getY(), player.getZ(), space);
        SolarSystem.Body nearest = TravelMath.nearestBody(clock, space[0], space[1], space[2]);
        apply(player, TravelMath.flightSpeedMultiplier(TravelMath.altitudeOver(nearest, clock, space[0], space[1], space[2])));
    }

    /** Devolve a velocidade ao padrão se foi este código que a mudou (fora de planeta ou ao sair do jogo). */
    static void restore(ServerPlayer player) {
        if (APPLIED.containsKey(player.getUUID())) {
            apply(player, 1.0);
        }
    }

    private static void apply(ServerPlayer player, double multiplier) {
        Abilities abilities = player.getAbilities();
        float current = abilities.getFlyingSpeed();
        Applied applied = APPLIED.get(player.getUUID());
        float base;
        if (applied != null && Math.abs(current - applied.applied()) < 1.0e-6F) {
            base = applied.base();
        } else {
            // alguém mexeu na velocidade (ou valor persistido de uma sessão anterior): recomeça do padrão
            APPLIED.remove(player.getUUID());
            base = Math.min(current, TravelMath.BASE_FLYING_SPEED);
        }
        float desired = (float) (base * multiplier);
        if (Math.abs(desired - current) > 1.0e-6F) {
            abilities.setFlyingSpeed(desired);
            player.onUpdateAbilities();
        }
        if (multiplier == 1.0) {
            APPLIED.remove(player.getUUID());
        } else {
            APPLIED.put(player.getUUID(), new Applied(base, desired));
        }
    }

    static void forget(UUID id) {
        APPLIED.remove(id);
    }

    static void clear() {
        APPLIED.clear();
    }
}
