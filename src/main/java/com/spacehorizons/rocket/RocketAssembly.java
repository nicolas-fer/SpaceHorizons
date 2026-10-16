package com.spacehorizons.rocket;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Busca em largura que descobre quais blocos formam o foguete, a partir do cockpit (inspirada na montagem do Create).
 * Lógica pura sobre uma {@link Grid} (o adaptador do mundo está em {@link RocketAssembler}), para ser testada sem o jogo.
 *
 * Regra única: entra tudo o que está ligado por FACE ao cockpit. A busca para (sem levar o bloco) em
 * {@link RocketBlockKind#PAD} e em {@link RocketBlockKind#AIR}; recusa a montagem ao tocar {@link RocketBlockKind#TERRAIN},
 * {@link RocketBlockKind#FORBIDDEN} ou {@link RocketBlockKind#UNLOADED}. Tudo é validado ANTES de qualquer bloco ser
 * removido: o resultado só diz o que remover.
 */
public final class RocketAssembly {
    /** Teto de blocos do foguete (o Create usa 2.048). */
    public static final int MAX_BLOCKS = 1024;
    /** Teto de consultas ao mundo (blocos visitados, inclusive os vizinhos vazios que não entram). */
    public static final int MAX_ITERATIONS = 100_000;
    /** Teto de cada dimensão da caixa que envolve o foguete, em blocos. */
    public static final int MAX_EXTENT = 64;

    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private RocketAssembly() {
    }

    /** O mundo visto pela busca: o papel do bloco em cada posição. */
    @FunctionalInterface
    public interface Grid {
        RocketBlockKind kindAt(int x, int y, int z);
    }

    /** Um bloco do foguete na posição do mundo em que foi achado. */
    public record Cell(int x, int y, int z, RocketBlockKind kind) {
    }

    /**
     * O que a busca achou. {@code cells} na ordem da busca (o cockpit primeiro); a caixa é inclusiva.
     *
     * @param padContacts quantas faces de plataforma de lançamento os blocos do foguete tocam
     */
    public record Result(List<Cell> cells, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int cockpitX, int cockpitY,
            int cockpitZ, RocketCensus census, int padContacts) {
        public int sizeX() {
            return this.maxX - this.minX + 1;
        }

        public int sizeY() {
            return this.maxY - this.minY + 1;
        }

        public int sizeZ() {
            return this.maxZ - this.minZ + 1;
        }
    }

    /** Procura o foguete a partir do cockpit em (x, y, z); lança {@link RocketException} se a montagem deve ser recusada. */
    public static Result scan(Grid grid, int x, int y, int z) throws RocketException {
        return scan(grid, x, y, z, MAX_BLOCKS, MAX_ITERATIONS, MAX_EXTENT);
    }

    public static Result scan(Grid grid, int startX, int startY, int startZ, int maxBlocks, int maxIterations, int maxExtent)
            throws RocketException {
        if (grid.kindAt(startX, startY, startZ) != RocketBlockKind.COCKPIT) {
            throw new RocketException(RocketException.Reason.NO_COCKPIT);
        }
        List<Cell> cells = new ArrayList<>();
        LongOpenHashSet visited = new LongOpenHashSet();
        List<int[]> queue = new ArrayList<>();
        visited.add(pack(startX, startY, startZ));
        cells.add(new Cell(startX, startY, startZ, RocketBlockKind.COCKPIT));
        queue.add(new int[] {startX, startY, startZ});
        int minX = startX;
        int minY = startY;
        int minZ = startZ;
        int maxX = startX;
        int maxY = startY;
        int maxZ = startZ;
        int padContacts = 0;
        int iterations = 0;
        for (int head = 0; head < queue.size(); head++) {
            int[] at = queue.get(head);
            for (int[] face : FACES) {
                int nx = at[0] + face[0];
                int ny = at[1] + face[1];
                int nz = at[2] + face[2];
                if (!visited.add(pack(nx, ny, nz))) {
                    continue;
                }
                if (++iterations > maxIterations) {
                    throw new RocketException(RocketException.Reason.TOO_LARGE, maxBlocks);
                }
                RocketBlockKind kind = grid.kindAt(nx, ny, nz);
                switch (kind) {
                    case AIR -> {
                    }
                    case PAD -> padContacts++;
                    case UNLOADED -> throw new RocketException(RocketException.Reason.UNLOADED_CHUNK, nx, ny, nz);
                    case FORBIDDEN -> throw new RocketException(RocketException.Reason.FORBIDDEN_BLOCK, nx, ny, nz);
                    case TERRAIN -> throw new RocketException(RocketException.Reason.TOUCHES_TERRAIN, nx, ny, nz);
                    default -> {
                        cells.add(new Cell(nx, ny, nz, kind));
                        if (cells.size() > maxBlocks) {
                            throw new RocketException(RocketException.Reason.TOO_LARGE, maxBlocks);
                        }
                        minX = Math.min(minX, nx);
                        minY = Math.min(minY, ny);
                        minZ = Math.min(minZ, nz);
                        maxX = Math.max(maxX, nx);
                        maxY = Math.max(maxY, ny);
                        maxZ = Math.max(maxZ, nz);
                        if (maxX - minX + 1 > maxExtent || maxY - minY + 1 > maxExtent || maxZ - minZ + 1 > maxExtent) {
                            throw new RocketException(RocketException.Reason.TOO_LARGE, maxBlocks);
                        }
                        queue.add(new int[] {nx, ny, nz});
                    }
                }
            }
        }
        RocketCensus census = RocketCensus.of(cells.stream().map(Cell::kind).toList());
        if (census.cockpits() > 1) {
            throw new RocketException(RocketException.Reason.MULTIPLE_COCKPITS, census.cockpits());
        }
        if (census.engines() < 1) {
            throw new RocketException(RocketException.Reason.NO_ENGINE);
        }
        if (census.tanks() < 1) {
            throw new RocketException(RocketException.Reason.NO_TANK);
        }
        if (padContacts < 1) {
            throw new RocketException(RocketException.Reason.NO_PAD);
        }
        return new Result(List.copyOf(cells), minX, minY, minZ, maxX, maxY, maxZ, startX, startY, startZ, census, padContacts);
    }

    /** Mesma codificação de BlockPos.asLong (26 bits em x e z, 12 em y), sem depender da classe. */
    static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFFL;
    }
}
