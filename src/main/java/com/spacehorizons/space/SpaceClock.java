package com.spacehorizons.space;

import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.world.clock.ClockInstance;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.Level;

/**
 * O tempo do espaço: o relógio do Overworld em ticks (com a fração do relógio), o mesmo que move o sol e as fases da Lua
 * ({@code /time set} muda os dois). Vale para o servidor e para o cliente, e para qualquer dimensão: o gerenciador de
 * relógios é do mundo, não do nível.
 */
public final class SpaceClock {
    private SpaceClock() {
    }

    /** Ticks do relógio do Overworld agora, com a parte fracionária do relógio (sem o parcial do frame do cliente). */
    public static double ticks(Level level) {
        Optional<Holder.Reference<WorldClock>> holder = level.registryAccess().get(WorldClocks.OVERWORLD);
        if (holder.isEmpty()) {
            return 0.0;
        }
        ClockInstance instance = level.clockManager().getInstance(holder.get());
        return instance.totalTicks() + instance.partialTick();
    }
}
