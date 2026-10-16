package com.spacehorizons.survival.suit;

import com.spacehorizons.survival.oxygen.OxygenSetup;
import com.spacehorizons.survival.oxygen.OxygenSources;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Traje espacial (etapa 2A), lado servidor: a fonte de oxigênio do traje e o frio do vácuo.
 *
 * <ul>
 *   <li>Respirar: {@link SuitOxygenSource} entra no sistema de oxigênio como mais uma {@code OxygenSource}.</li>
 *   <li>Frio: no vácuo, sem as quatro peças, o jogador congela com o congelamento do vanilla (ver {@link SuitCold}).
 *       Vale a mesma regra do oxigênio: criativo, espectador e a regra de jogo {@code spacehorizons:vacuum_damage}
 *       desligada isentam.</li>
 * </ul>
 *
 * As peças do traje <b>não</b> entram na tag {@code minecraft:freeze_immune_wearables}: ali basta UMA peça para o
 * jogo considerar o jogador imune a congelar, o que contradiz a regra de "as quatro peças" no vácuo (e, por reduzir
 * {@code canFreeze()}, também desligaria o dano de congelar do vanilla com uma peça só).
 */
public final class SuitSetup {
    private SuitSetup() {
    }

    public static void init(IEventBus modEventBus) {
        SuitTriggers.register(modEventBus);
        // registrada depois da fonte de teste (fontes gratuitas primeiro): o traje só gasta tanque se nada mais cobre
        OxygenSources.register(new SuitOxygenSource());
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class,
                event -> SuitOxygenSource.forget(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> SuitOxygenSource.clear());
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                coldTick(player);
            }
        });
    }

    /** Frio do vácuo, depois do tick do jogador (o vanilla já descontou o que descontou neste tick). */
    private static void coldTick(ServerPlayer player) {
        ServerLevel level = player.level();
        SuitPieces suit = SuitGear.piecesOf(player);
        boolean enabled = OxygenSetup.isEnabled(level);
        boolean exempt = OxygenSetup.isExempt(player);
        // o vácuo (mais caro) só é calculado para quem ainda pode sofrer o frio
        boolean vacuum = enabled && !exempt && !suit.protectsFromCold() && OxygenSetup.isInVacuum(player);
        if (!SuitCold.exposed(enabled, exempt, vacuum, suit)) {
            return;
        }
        int required = player.getTicksRequiredToFreeze();
        int frozen = SuitCold.nextFrozenTicks(player.getTicksFrozen(), required);
        player.setTicksFrozen(frozen);
        float damage = SuitCold.damageAt(frozen, required, player.tickCount);
        if (damage > 0.0F) {
            player.hurtServer(level, player.damageSources().freeze(), damage);
        }
    }
}
