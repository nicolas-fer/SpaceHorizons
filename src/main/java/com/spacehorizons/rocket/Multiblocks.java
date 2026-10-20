package com.spacehorizons.rocket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;

/**
 * Detecção das estruturas multibloco do foguete (docs/PLANO_TIERS.md, seção 3), por REGRA e não por lista fixa. Lógica pura sobre
 * conjuntos de posições (codificadas como {@link RocketAssembly#pack}): o mesmo cálculo serve ao mundo (o motor se forma ao ser posto),
 * à montagem, ao retrato em voo, ao censo da nave e ao painel de projeto, e por isso todos concordam.
 *
 * <ul>
 *   <li><b>Tanque</b>: coluna retangular CHEIA de tanques, largura e profundidade de 1 a {@value #TANK_MAX_SIDE}, altura de
 *       {@value #TANK_MIN_HEIGHT} a {@value #TANK_MAX_HEIGHT}. Tanques que não cabem numa dessas colunas (altura menor que
 *       {@value #TANK_MIN_HEIGHT}, buracos, formas em L) continuam valendo, um a um, como tanques avulsos.</li>
 *   <li><b>Motor</b>: cubo cheio de motores 2x2x2 ou 3x3x3 (a base e a altura). Motores que não formam um cubo valem um a um.</li>
 * </ul>
 *
 * A partição é gulosa e determinística: percorre as células de baixo para cima (y, depois z, depois x) e, em cada célula ainda livre
 * (o canto mínimo de uma caixa), toma a maior estrutura válida que cabe ali (a de maior volume; empate, a de maior base). Percorrer de
 * baixo para cima faz o motor (embaixo) e o tanque (empilhado) se formarem como o jogador espera, e dá o mesmo resultado para o mesmo
 * conjunto de células, onde quer que o foguete esteja.
 */
public final class Multiblocks {
    public static final int TANK_MIN_SIDE = 1;
    public static final int TANK_MAX_SIDE = 3;
    public static final int TANK_MIN_HEIGHT = 4;
    public static final int TANK_MAX_HEIGHT = 12;
    /** Lados (base = altura) dos motores formados, do maior para o menor (a partição tenta o maior primeiro). */
    public static final int ENGINE_LARGE = 3;
    public static final int ENGINE_SMALL = 2;

    private Multiblocks() {
    }

    /** Uma caixa de blocos: canto mínimo e tamanho (largura em x, altura em y, profundidade em z). */
    public record Box(int x, int y, int z, int w, int h, int d) {
        public int volume() {
            return this.w * this.h * this.d;
        }

        /** Faces expostas da caixa, em blocos (a "casca" de um tanque). */
        public int surface() {
            return 2 * (this.w * this.d + this.w * this.h + this.d * this.h);
        }

        public boolean contains(int px, int py, int pz) {
            return px >= this.x && px < this.x + this.w && py >= this.y && py < this.y + this.h && pz >= this.z && pz < this.z + this.d;
        }

        /** Lado do cubo de um motor formado (largura = altura = profundidade). */
        public int side() {
            return this.w;
        }
    }

    /** O resultado de uma partição: as caixas formadas e as células avulsas (packed). */
    public record Tiling(List<Box> boxes, long[] loose) {
        public int looseCount() {
            return this.loose.length;
        }
    }

    /** O papel de um bloco de motor dentro da estrutura formada. */
    public enum EngineRole {
        /** Motor 1x1 avulso: desenha o modelo normal. */
        SOLO,
        /** Mestre de um motor 2x2x2: o canto mínimo; desenha o modelo do conjunto. */
        MASTER_2,
        /** Mestre de um motor 3x3x3: o bloco central; desenha o modelo do conjunto. */
        MASTER_3,
        /** Qualquer outro bloco de uma estrutura formada: invisível. */
        PART
    }

    /** As estruturas de um foguete: tanques e motores formados e o que sobrou avulso. */
    public record Analysis(Tiling tanks, Tiling engines) {
        public static final Analysis NONE = new Analysis(new Tiling(List.of(), new long[0]), new Tiling(List.of(), new long[0]));
    }

    public static Analysis analyze(long[] tankCells, long[] engineCells) {
        return new Analysis(tileTanks(tankCells), tileEngines(engineCells));
    }

    // ---------------------------------------------------------------- partição

    /** Parte os tanques em colunas formadas e avulsos. */
    public static Tiling tileTanks(long[] cells) {
        return tile(cells, false);
    }

    /** Parte os motores em cubos formados (3x3x3, depois 2x2x2) e avulsos. */
    public static Tiling tileEngines(long[] cells) {
        return tile(cells, true);
    }

    private static Tiling tile(long[] cells, boolean engines) {
        if (cells.length == 0) {
            return new Tiling(List.of(), new long[0]);
        }
        long[] sorted = sortedBottomUp(cells);
        LongOpenHashSet present = new LongOpenHashSet(cells);
        LongOpenHashSet claimed = new LongOpenHashSet();
        List<Box> boxes = new ArrayList<>();
        List<Long> loose = new ArrayList<>();
        for (long cell : sorted) {
            if (claimed.contains(cell)) {
                continue;
            }
            int x = BlockPos.getX(cell);
            int y = BlockPos.getY(cell);
            int z = BlockPos.getZ(cell);
            Box best = engines ? bestEngine(present, claimed, x, y, z) : bestTank(present, claimed, x, y, z);
            if (best == null) {
                claimed.add(cell);
                loose.add(cell);
                continue;
            }
            for (int yy = best.y; yy < best.y + best.h; yy++) {
                for (int zz = best.z; zz < best.z + best.d; zz++) {
                    for (int xx = best.x; xx < best.x + best.w; xx++) {
                        claimed.add(RocketAssembly.pack(xx, yy, zz));
                    }
                }
            }
            boxes.add(best);
        }
        return new Tiling(List.copyOf(boxes), loose.stream().mapToLong(Long::longValue).toArray());
    }

    private static Box bestEngine(LongOpenHashSet present, LongOpenHashSet claimed, int x, int y, int z) {
        for (int side : new int[] {ENGINE_LARGE, ENGINE_SMALL}) {
            if (fullBox(present, claimed, x, y, z, side, side, side)) {
                return new Box(x, y, z, side, side, side);
            }
        }
        return null;
    }

    private static Box bestTank(LongOpenHashSet present, LongOpenHashSet claimed, int x, int y, int z) {
        Box best = null;
        for (int w = TANK_MAX_SIDE; w >= TANK_MIN_SIDE; w--) {
            for (int d = TANK_MAX_SIDE; d >= TANK_MIN_SIDE; d--) {
                int h = 0;
                while (h < TANK_MAX_HEIGHT && fullBox(present, claimed, x, y + h, z, w, 1, d)) {
                    h++;
                }
                if (h < TANK_MIN_HEIGHT) {
                    continue;
                }
                // maior volume; empate, a maior base (as voltas vão de w e d maiores para menores, então só troca se for estritamente melhor)
                if (best == null || w * d * h > best.volume() || (w * d * h == best.volume() && w * d > best.w * best.d)) {
                    best = new Box(x, y, z, w, h, d);
                }
            }
        }
        return best;
    }

    private static boolean fullBox(LongOpenHashSet present, LongOpenHashSet claimed, int x, int y, int z, int w, int h, int d) {
        for (int yy = y; yy < y + h; yy++) {
            for (int zz = z; zz < z + d; zz++) {
                for (int xx = x; xx < x + w; xx++) {
                    long key = RocketAssembly.pack(xx, yy, zz);
                    if (!present.contains(key) || claimed.contains(key)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Ordena as células de baixo para cima: y, depois z, depois x. */
    private static long[] sortedBottomUp(long[] cells) {
        Long[] boxed = new Long[cells.length];
        for (int i = 0; i < cells.length; i++) {
            boxed[i] = cells[i];
        }
        Arrays.sort(boxed, Comparator.<Long>comparingInt(c -> BlockPos.getY(c)).thenComparingInt(c -> BlockPos.getZ(c))
                .thenComparingInt(c -> BlockPos.getX(c)));
        long[] out = new long[cells.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = boxed[i];
        }
        return out;
    }

    // ---------------------------------------------------------------- papéis dos motores

    /** O papel de cada bloco de motor (célula packed) conforme a partição; a posição do mestre depende do tamanho. */
    public static Map<Long, EngineRole> engineRoles(Tiling engines) {
        Map<Long, EngineRole> roles = new HashMap<>();
        for (long cell : engines.loose()) {
            roles.put(cell, EngineRole.SOLO);
        }
        for (Box box : engines.boxes()) {
            for (int y = box.y; y < box.y + box.h; y++) {
                for (int z = box.z; z < box.z + box.d; z++) {
                    for (int x = box.x; x < box.x + box.w; x++) {
                        roles.put(RocketAssembly.pack(x, y, z), EngineRole.PART);
                    }
                }
            }
            roles.put(masterOf(box), box.side() == ENGINE_LARGE ? EngineRole.MASTER_3 : EngineRole.MASTER_2);
        }
        return roles;
    }

    /** Onde fica o mestre de um motor formado: o canto mínimo (2x2x2) ou o bloco central (3x3x3: o modelo é maior que 32 pixels). */
    public static long masterOf(Box box) {
        if (box.side() == ENGINE_LARGE) {
            return RocketAssembly.pack(box.x + 1, box.y + 1, box.z + 1);
        }
        return RocketAssembly.pack(box.x, box.y, box.z);
    }

    // ---------------------------------------------------------------- a caixa de um bloco

    /** A caixa formada que contém a célula, ou null se ela é avulsa. */
    public static Box boxAt(Tiling tiling, int x, int y, int z) {
        for (Box box : tiling.boxes()) {
            if (box.contains(x, y, z)) {
                return box;
            }
        }
        return null;
    }
}
