package com.spacehorizons.rocket;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.space.flight.LandingRules;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A batida do foguete: sem explosão de terreno, os blocos do retrato viram itens no ponto do impacto e o piloto toma dano
 * proporcional à velocidade ({@link LandingRules#impactDamage}), como uma queda. A entidade some.
 */
final class RocketCrash {
    private RocketCrash() {
    }

    /** Resultado da batida, para o log e os testes. */
    record Outcome(int itemStacks, int items, double damage, @Nullable ServerPlayer pilot) {
    }

    /**
     * Desfaz o foguete em {@code at} (o centro da caixa já assentado no ponto de impacto). {@code speed} é a velocidade do
     * impacto (blocos/tick). O piloto sai em pé ao lado do foguete antes do dano.
     */
    static Outcome crash(ServerLevel level, RocketEntity rocket, @Nullable ServerPlayer pilot, Vec3 at, double speed) {
        double damage = LandingRules.impactDamage(speed);
        if (pilot != null) {
            // o assento sai dos blocos antes de eles virarem itens; "pousado" faz o foguete devolver o ponto de saída no chão
            pilot.stopRiding();
            pilot.resetFallDistance();
            if (damage > 0.0) {
                pilot.hurtServer(level, level.damageSources().fall(), (float) damage);
            }
        }
        Map<Item, Integer> drops = new LinkedHashMap<>();
        RocketPortrait portrait = rocket.portrait();
        for (int y = 0; y < portrait.sizeY; y++) {
            for (int z = 0; z < portrait.sizeZ; z++) {
                for (int x = 0; x < portrait.sizeX; x++) {
                    BlockState state = portrait.get(x, y, z);
                    if (state.isAir()) {
                        continue;
                    }
                    Item item = state.getBlock().asItem();
                    if (item != net.minecraft.world.item.Items.AIR) {
                        drops.merge(item, 1, Integer::sum);
                    }
                }
            }
        }
        int stacks = 0;
        int total = 0;
        for (Map.Entry<Item, Integer> entry : drops.entrySet()) {
            int left = entry.getValue();
            total += left;
            while (left > 0) {
                int count = Math.min(left, new ItemStack(entry.getKey()).getMaxStackSize());
                left -= count;
                ItemEntity drop = new ItemEntity(level, at.x + (level.getRandom().nextDouble() - 0.5) * 2.0, at.y + 0.5,
                        at.z + (level.getRandom().nextDouble() - 0.5) * 2.0, new ItemStack(entry.getKey(), count),
                        (level.getRandom().nextDouble() - 0.5) * 0.2, 0.2, (level.getRandom().nextDouble() - 0.5) * 0.2);
                level.addFreshEntity(drop);
                stacks++;
            }
        }
        rocket.discard();
        SpaceHorizons.LOGGER.info("Foguete bateu a {} blocos/tick em {}: {} blocos viraram {} pilhas de itens, dano ao piloto={} PV",
                String.format(java.util.Locale.ROOT, "%.2f", speed), at, total, stacks,
                String.format(java.util.Locale.ROOT, "%.1f", damage));
        return new Outcome(stacks, total, damage, pilot);
    }
}
