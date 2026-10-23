package com.spacehorizons.moonbase.room;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;

/**
 * Detecção de sala selada por preenchimento (flood fill) de células de ar com teto de volume. Java puro (só a codificação
 * de posição do {@link BlockPos}), testável sem o jogo: o mundo entra por {@link Space}.
 *
 * A partir de uma célula de ar, espalha pelas 6 faces. Termina em um de quatro resultados ({@link Status}): sala fechada
 * (acabou o ar sem tocar o vazio nem passar do teto), vazamento (alcançou uma célula "vazio": chunk não carregado ou fora do
 * mundo), grande demais (passou de {@code maxVolume} células: o espaço aberto da Lua sempre cai aqui) ou partida bloqueada
 * (a célula inicial é parede). O teto limita o custo: nunca se visita mais que {@code maxVolume + 1} células.
 */
public final class RoomScan {
    /** Teto de volume de uma sala selada, em células de ar (blocos). */
    public static final int MAX_VOLUME = 1_000;

    private RoomScan() {
    }

    /** O que há numa célula. */
    public enum Cell {
        /** Bloco que segura o ar (cubo cheio, porta fechada). */
        WALL,
        /** Ar (ou bloco que não segura: vidraça, laje, tocha...). */
        AIR,
        /** Fora do que se pode ver: chunk não carregado ou acima/abaixo do mundo. Conta como vazamento. */
        VOID
    }

    /** O mundo visto pelo scanner. */
    @FunctionalInterface
    public interface Space {
        Cell at(int x, int y, int z);
    }

    public enum Status {
        /** Todo o ar alcançável cabe no teto e está cercado: pressurizável. */
        SEALED,
        /** O ar alcança o vazio (porta aberta, parede quebrada, chunk descarregado). */
        LEAK,
        /** O ar alcançável passa do teto de volume (espaço aberto ou sala enorme). */
        TOO_BIG,
        /** A célula inicial é parede ou vazio: não há sala. */
        BLOCKED
    }

    /**
     * Resultado do scan. {@code cells} (posições {@link BlockPos#asLong}) é o ar alcançado: completo só em {@link Status#SEALED}
     * (nos outros casos é parcial e não deve ser usado como sala).
     */
    public record Result(Status status, int volume, LongOpenHashSet cells) {
        public boolean sealed() {
            return status == Status.SEALED;
        }
    }

    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /** Varre a partir de (sx, sy, sz) com o teto padrão {@link #MAX_VOLUME}. */
    public static Result scan(Space space, int sx, int sy, int sz) {
        return scan(space, sx, sy, sz, MAX_VOLUME);
    }

    public static Result scan(Space space, int sx, int sy, int sz, int maxVolume) {
        Cell start = space.at(sx, sy, sz);
        if (start != Cell.AIR) {
            return new Result(Status.BLOCKED, 0, new LongOpenHashSet());
        }
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        long first = BlockPos.asLong(sx, sy, sz);
        seen.add(first);
        queue.enqueue(first);
        while (!queue.isEmpty()) {
            long packed = queue.dequeueLong();
            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);
            for (int[] f : FACES) {
                int nx = x + f[0];
                int ny = y + f[1];
                int nz = z + f[2];
                Cell cell = space.at(nx, ny, nz);
                if (cell == Cell.WALL) {
                    continue;
                }
                if (cell == Cell.VOID) {
                    return new Result(Status.LEAK, seen.size(), seen);
                }
                long next = BlockPos.asLong(nx, ny, nz);
                if (seen.add(next)) {
                    if (seen.size() > maxVolume) {
                        return new Result(Status.TOO_BIG, seen.size(), seen);
                    }
                    queue.enqueue(next);
                }
            }
        }
        return new Result(Status.SEALED, seen.size(), seen);
    }
}
