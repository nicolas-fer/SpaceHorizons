package com.spacehorizons.survival.suit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.spacehorizons.survival.oxygen.OxygenMath;
import com.spacehorizons.survival.oxygen.OxygenSource;

import net.minecraft.server.level.ServerPlayer;

/**
 * Fonte de oxigênio do traje: no vácuo, com capacete e peitoral do traje vestidos, puxa o consumo de cada tick dos
 * recipientes de oxigênio do inventário ({@link SuitGear#drawOxygen}) e o devolve como abastecimento. Sem as duas
 * peças, ou sem oxigênio nos recipientes, devolve 0 e a reserva do corpo cai como sempre.
 *
 * Só o servidor chama isto (o {@link OxygenSource} só é consultado pelo tick do servidor, e só no vácuo, com o
 * jogador fora de criativo/espectador e com a regra de jogo ligada): criativo e quem está no ar nunca gastam tanque.
 * É registrada depois das fontes gratuitas (ver {@code OxygenSources}), então só gasta tanque quando nada mais
 * cobre a respiração.
 *
 * Também conta quantos segundos seguidos o jogador respira só do traje e avisa o gatilho de conquista
 * {@link SuitTriggers#SUIT_BREATHING}.
 */
public final class SuitOxygenSource implements OxygenSource {
    /** Quantos ticks seguidos o jogador respira do traje, e o tick do jogador da última contagem. */
    private record Streak(int lastTick, int ticks) {
    }

    private static final Map<UUID, Streak> STREAKS = new ConcurrentHashMap<>();

    @Override
    public int supply(ServerPlayer player) {
        if (!SuitGear.piecesOf(player).canBreathe()) {
            STREAKS.remove(player.getUUID());
            return 0;
        }
        int taken = SuitGear.drawOxygen(player, OxygenMath.CONSUMPTION_PER_TICK);
        countBreath(player, taken >= OxygenMath.CONSUMPTION_PER_TICK);
        return taken;
    }

    /** Conta ticks seguidos respirando do traje (um tick sem respirar, ou sem consulta, zera) e dispara o gatilho a cada segundo. */
    private static void countBreath(ServerPlayer player, boolean breathed) {
        UUID id = player.getUUID();
        if (!breathed) {
            STREAKS.remove(id);
            return;
        }
        Streak previous = STREAKS.get(id);
        int ticks = previous != null && previous.lastTick() == player.tickCount - 1 ? previous.ticks() + 1 : 1;
        STREAKS.put(id, new Streak(player.tickCount, ticks));
        if (ticks % 20 == 0) {
            SuitTriggers.SUIT_BREATHING.get().trigger(player, ticks / 20);
        }
    }

    /** Esquece o jogador (logout). */
    static void forget(UUID player) {
        STREAKS.remove(player);
    }

    /** Esquece todos (o servidor parou). */
    static void clear() {
        STREAKS.clear();
    }
}
