package com.spacehorizons.rocket;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Busca em largura que descobre quais blocos formam o foguete, a partir do cockpit (inspirada na montagem do Create).
 * Lógica pura sobre uma {@link Grid} (o adaptador do mundo está em {@link RocketAssembler}), para ser testada sem o jogo.
 *
 * Regra única: entra tudo o que está ligado por FACE ao cockpit. A busca para (sem levar o bloco) em
 * {@link RocketBlockKind#PAD} e em {@link RocketBlockKind#AIR}; recusa a montagem ao tocar {@link RocketBlockKind#TERRAIN},
 * {@link RocketBlockKind#FORBIDDEN} ou {@link RocketBlockKind#UNLOADED}. Tudo é validado ANTES de qualquer bloco ser
 * removido: o resultado só diz o que remover.
 *
 * Há duas portas para a mesma busca: {@link #scan} (a montagem de verdade: recusa com {@link RocketException} no primeiro
 * problema) e {@link #analyze} (o painel de projeto: devolve o relatório inteiro, com TODOS os problemas e a posição de cada
 * um). {@code scan} é {@code analyze} mais a recusa, então o painel e a montagem não podem discordar.
 */
public final class RocketAssembly {
    /**
     * Teto de blocos do foguete: 8.192 (a decisão D2 do docs/PLANO_NAVES.md; o Create usa 2.048 e a versão anterior do foguete
     * 1.024). O custo cresce com o retrato (memória, malha e pacote), não com a busca: ver {@code RocketAssembler.MAX_PORTRAIT_BYTES}.
     */
    public static final int MAX_BLOCKS = 8192;
    /** Teto de consultas ao mundo (blocos visitados, inclusive os vizinhos vazios que não entram): ~6 por bloco em casco oco, com folga. */
    public static final int MAX_ITERATIONS = 400_000;
    /** Teto da largura e da profundidade da caixa que envolve o foguete, em blocos (a nave da D2 tem 128 x 128 x 256). */
    public static final int MAX_EXTENT_XZ = 128;
    /** Teto da altura da caixa. */
    public static final int MAX_EXTENT_Y = 256;

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
     * @param structures  as colunas de tanque e os cubos de motor que os blocos formam (o censo já as conta)
     */
    public record Result(List<Cell> cells, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int cockpitX, int cockpitY,
            int cockpitZ, RocketCensus census, int padContacts, Multiblocks.Analysis structures) {
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

    /** Um problema que impede a montagem: o motivo, o bloco onde ele aparece (o vizinho recusado, o cockpit extra...) e os argumentos da mensagem. */
    public record Blocker(RocketException.Reason reason, int x, int y, int z, Object[] args) {
        public RocketException toException() {
            return new RocketException(this.reason, this.args);
        }
    }

    /**
     * A busca inteira, sem lançar: o {@link Result} do que foi achado, as estruturas multibloco, os problemas que recusariam a
     * montagem (em ordem de descoberta) e as posições dos cockpits. {@code truncated}: a busca parou no teto de blocos ou de consultas.
     */
    public record Report(Result result, Multiblocks.Analysis structures, List<Blocker> blockers, List<Cell> cockpits, boolean truncated) {
        /** A montagem de verdade aceitaria este foguete? */
        public boolean assemblable() {
            return this.blockers.isEmpty();
        }
    }

    /** Procura o foguete a partir do cockpit em (x, y, z); lança {@link RocketException} se a montagem deve ser recusada. */
    public static Result scan(Grid grid, int x, int y, int z) throws RocketException {
        return scan(grid, x, y, z, MAX_BLOCKS, MAX_ITERATIONS, MAX_EXTENT_XZ, MAX_EXTENT_Y);
    }

    /** Como acima, com o mesmo teto de extensão nos três eixos. */
    public static Result scan(Grid grid, int startX, int startY, int startZ, int maxBlocks, int maxIterations, int maxExtent)
            throws RocketException {
        return scan(grid, startX, startY, startZ, maxBlocks, maxIterations, maxExtent, maxExtent);
    }

    public static Result scan(Grid grid, int startX, int startY, int startZ, int maxBlocks, int maxIterations, int maxExtentXz,
            int maxExtentY) throws RocketException {
        if (grid.kindAt(startX, startY, startZ) != RocketBlockKind.COCKPIT) {
            throw new RocketException(RocketException.Reason.NO_COCKPIT);
        }
        Report report = analyze(grid, List.of(new int[] {startX, startY, startZ}), maxBlocks, maxIterations, maxExtentXz, maxExtentY);
        if (!report.blockers().isEmpty()) {
            throw report.blockers().get(0).toException();
        }
        return report.result();
    }

    /** O relatório da busca a partir de {@code seeds} (blocos do foguete), com os tetos padrão. */
    public static Report analyze(Grid grid, List<int[]> seeds) {
        return analyze(grid, seeds, MAX_BLOCKS, MAX_ITERATIONS, MAX_EXTENT_XZ, MAX_EXTENT_Y);
    }

    public static Report analyze(Grid grid, List<int[]> seeds, int maxBlocks, int maxIterations, int maxExtentXz, int maxExtentY) {
        List<Cell> cells = new ArrayList<>();
        List<Blocker> blockers = new ArrayList<>();
        LongOpenHashSet visited = new LongOpenHashSet();
        List<int[]> queue = new ArrayList<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int[] seed : seeds) {
            RocketBlockKind kind = grid.kindAt(seed[0], seed[1], seed[2]);
            if (kind.isPart() && visited.add(pack(seed[0], seed[1], seed[2]))) {
                cells.add(new Cell(seed[0], seed[1], seed[2], kind));
                queue.add(seed.clone());
                minX = Math.min(minX, seed[0]);
                minY = Math.min(minY, seed[1]);
                minZ = Math.min(minZ, seed[2]);
                maxX = Math.max(maxX, seed[0]);
                maxY = Math.max(maxY, seed[1]);
                maxZ = Math.max(maxZ, seed[2]);
            }
        }
        int padContacts = 0;
        int iterations = 0;
        boolean truncated = false;
        search:
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
                    blockers.add(new Blocker(RocketException.Reason.TOO_LARGE, nx, ny, nz, new Object[] {maxBlocks}));
                    truncated = true;
                    break search;
                }
                RocketBlockKind kind = grid.kindAt(nx, ny, nz);
                switch (kind) {
                    case AIR -> {
                    }
                    case PAD -> padContacts++;
                    case UNLOADED -> blockers.add(new Blocker(RocketException.Reason.UNLOADED_CHUNK, nx, ny, nz, new Object[] {nx, ny, nz}));
                    case FORBIDDEN -> blockers.add(new Blocker(RocketException.Reason.FORBIDDEN_BLOCK, nx, ny, nz, new Object[] {nx, ny, nz}));
                    case TERRAIN -> blockers.add(new Blocker(RocketException.Reason.TOUCHES_TERRAIN, nx, ny, nz, new Object[] {nx, ny, nz}));
                    default -> {
                        cells.add(new Cell(nx, ny, nz, kind));
                        minX = Math.min(minX, nx);
                        minY = Math.min(minY, ny);
                        minZ = Math.min(minZ, nz);
                        maxX = Math.max(maxX, nx);
                        maxY = Math.max(maxY, ny);
                        maxZ = Math.max(maxZ, nz);
                        if (cells.size() > maxBlocks || maxX - minX + 1 > maxExtentXz || maxZ - minZ + 1 > maxExtentXz
                                || maxY - minY + 1 > maxExtentY) {
                            blockers.add(new Blocker(RocketException.Reason.TOO_LARGE, nx, ny, nz, new Object[] {maxBlocks}));
                            truncated = true;
                            break search;
                        }
                        queue.add(new int[] {nx, ny, nz});
                    }
                }
            }
        }
        if (cells.isEmpty()) {
            minX = minY = minZ = maxX = maxY = maxZ = 0;
        }

        LongArrayList tanks = new LongArrayList();
        LongArrayList engines = new LongArrayList();
        List<Cell> cockpits = new ArrayList<>();
        for (Cell cell : cells) {
            switch (cell.kind()) {
                case FUEL_TANK -> tanks.add(pack(cell.x(), cell.y(), cell.z()));
                case ENGINE -> engines.add(pack(cell.x(), cell.y(), cell.z()));
                case COCKPIT -> cockpits.add(cell);
                default -> {
                }
            }
        }
        Multiblocks.Analysis structures = Multiblocks.analyze(tanks.toLongArray(), engines.toLongArray());
        RocketCensus census = RocketCensus.of(cells.stream().map(Cell::kind).toList()).withStructures(structures);
        if (census.cockpits() > 1) {
            Cell extra = cockpits.get(1);
            blockers.add(new Blocker(RocketException.Reason.MULTIPLE_COCKPITS, extra.x(), extra.y(), extra.z(), new Object[] {census.cockpits()}));
        }
        Cell first = cells.isEmpty() ? new Cell(0, 0, 0, RocketBlockKind.AIR) : cells.get(0);
        if (census.engines() < 1) {
            blockers.add(new Blocker(RocketException.Reason.NO_ENGINE, first.x(), first.y(), first.z(), new Object[0]));
        }
        if (census.tanks() < 1) {
            blockers.add(new Blocker(RocketException.Reason.NO_TANK, first.x(), first.y(), first.z(), new Object[0]));
        }
        if (padContacts < 1) {
            blockers.add(new Blocker(RocketException.Reason.NO_PAD, first.x(), first.y(), first.z(), new Object[0]));
        }
        if (census.cockpits() < 1) {
            blockers.add(new Blocker(RocketException.Reason.NO_COCKPIT, first.x(), first.y(), first.z(), new Object[0]));
        }
        Cell cockpit = cockpits.isEmpty() ? first : cockpits.get(0);
        Result result = new Result(List.copyOf(cells), minX, minY, minZ, maxX, maxY, maxZ, cockpit.x(), cockpit.y(), cockpit.z(), census,
                padContacts, structures);
        return new Report(result, structures, List.copyOf(blockers), List.copyOf(cockpits), truncated);
    }

    /** Mesma codificação de BlockPos.asLong (26 bits em x e z, 12 em y), sem depender da classe. */
    static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFF;
    }
}
