package com.spacehorizons.ship;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Alocação de células do estaleiro (lógica pura, sem Minecraft). O estaleiro é uma grade de células quadradas
 * ({@link ShipYard#CELL} blocos de lado) numeradas em ESPIRAL a partir da origem: 0 = (0,0), depois (1,0), (1,1), (0,1),
 * (-1,1), ... Cada nave ocupa uma célula, por índice. Células de naves destruídas voltam para a lista de livres, mas só na
 * sessão SEGUINTE ({@link #startSession}): é o "tombstone", que evita reutilizar uma célula enquanto ainda pode haver
 * chunks, entidades ou NBT residual dela em memória ou numa região salva pela metade.
 *
 * <p>Persistido dentro do {@code ShipRegistry} ({@link #CODEC}).
 */
public final class ShipYardAllocator {
    public static final Codec<ShipYardAllocator> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("next").forGetter(a -> a.next),
            Codec.INT.listOf().fieldOf("free").forGetter(a -> new ArrayList<>(a.free)),
            Codec.INT.listOf().fieldOf("pending").forGetter(a -> new ArrayList<>(a.pending))
    ).apply(i, ShipYardAllocator::new));

    /** Próximo índice nunca usado. */
    private int next;
    /** Células liberadas em sessões anteriores: reaproveitáveis (a menor primeiro, para a grade não crescer à toa). */
    private final TreeSet<Integer> free = new TreeSet<>();
    /** Células liberadas nesta sessão (tombstones): só viram livres na próxima sessão. */
    private final TreeSet<Integer> pending = new TreeSet<>();

    public ShipYardAllocator() {
    }

    private ShipYardAllocator(int next, List<Integer> free, List<Integer> pending) {
        this.next = next;
        this.free.addAll(free);
        this.pending.addAll(pending);
    }

    /** Reserva uma célula: a menor livre ou, não havendo, uma nunca usada. */
    public int allocate() {
        Integer reused = free.pollFirst();
        return reused != null ? reused : next++;
    }

    /** Devolve a célula (a nave foi destruída). Fica em quarentena até a próxima sessão. */
    public void release(int index) {
        if (index < 0 || index >= next) {
            throw new IllegalArgumentException("célula desconhecida: " + index);
        }
        if (free.contains(index) || !pending.add(index)) {
            throw new IllegalStateException("célula já liberada: " + index);
        }
    }

    /** Começa uma sessão (carregamento do mundo): as células em quarentena passam a ser reaproveitáveis. */
    public void startSession() {
        free.addAll(pending);
        pending.clear();
    }

    /** Quantas células já foram entregues alguma vez (a grade usada tem este tamanho). */
    public int highWater() {
        return next;
    }

    public int freeCount() {
        return free.size();
    }

    public int pendingCount() {
        return pending.size();
    }

    /**
     * Coordenadas (cx, cz) da célula de índice {@code index} na espiral quadrada: 0 → (0,0); depois um passo para +X, um para +Z,
     * dois para -X, dois para -Z, três para +X... Vizinhos consecutivos na espiral são vizinhos na grade.
     */
    public static int[] cellCoords(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("índice negativo: " + index);
        }
        int x = 0;
        int z = 0;
        int dx = 1;
        int dz = 0;
        int segment = 1;
        int left = 1;
        int turns = 0;
        for (int n = 0; n < index; n++) {
            x += dx;
            z += dz;
            if (--left == 0) {
                int t = dx;                     // gira 90 graus: (1,0) → (0,1) → (-1,0) → (0,-1)
                dx = -dz;
                dz = t;
                if (++turns % 2 == 0) {
                    segment++;
                }
                left = segment;
            }
        }
        return new int[] {x, z};
    }
}
