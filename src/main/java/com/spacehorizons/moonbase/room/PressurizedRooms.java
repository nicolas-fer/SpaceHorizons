package com.spacehorizons.moonbase.room;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Salas pressurizadas ativas, por mundo: cada gerador de oxigênio registra aqui o ar da sala dele e a pressão a cada varredura, e
 * tira o registro ao ser removido ou descarregado. {@link #breathableAt} é o que o sistema de oxigênio consulta para saber se o
 * ponto de respiração de um jogador está num ar respirável (e então ele não está no vácuo: não gasta tanque, não congela).
 *
 * Só guarda o que o servidor sabe agora; nada é salvo (o gerador refaz tudo na primeira varredura depois de carregar).
 */
public final class PressurizedRooms {
    private record Room(LongOpenHashSet cells, int pressure) {
    }

    private static final Map<ResourceKey<Level>, Map<Long, Room>> ROOMS = new ConcurrentHashMap<>();

    private PressurizedRooms() {
    }

    /** Registra (ou atualiza) a sala do gerador em {@code generator}. */
    public static void put(Level level, BlockPos generator, LongOpenHashSet cells, int pressure) {
        ROOMS.computeIfAbsent(level.dimension(), key -> new ConcurrentHashMap<>()).put(generator.asLong(), new Room(cells, pressure));
    }

    public static void remove(Level level, BlockPos generator) {
        Map<Long, Room> rooms = ROOMS.get(level.dimension());
        if (rooms != null) {
            rooms.remove(generator.asLong());
        }
    }

    /** O ponto está dentro de uma sala com pressão de ar respirável? */
    public static boolean breathableAt(Level level, BlockPos pos) {
        Map<Long, Room> rooms = ROOMS.get(level.dimension());
        if (rooms == null || rooms.isEmpty()) {
            return false;
        }
        long packed = pos.asLong();
        for (Room room : rooms.values()) {
            if (room.pressure() >= RoomPressure.BREATHABLE_MIN && room.cells().contains(packed)) {
                return true;
            }
        }
        return false;
    }

    /** Quantas salas estão registradas neste mundo (testes e diagnóstico). */
    public static int count(Level level) {
        Map<Long, Room> rooms = ROOMS.get(level.dimension());
        return rooms == null ? 0 : rooms.size();
    }

    /** Esquece tudo (o servidor parou; no singleplayer o próximo mundo começa limpo). */
    public static void clear() {
        ROOMS.clear();
    }
}
