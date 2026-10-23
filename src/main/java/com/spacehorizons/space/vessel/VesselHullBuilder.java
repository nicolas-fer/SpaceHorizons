package com.spacehorizons.space.vessel;

import java.util.Arrays;

import it.unimi.dsi.fastutil.ints.Int2ShortOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * Monta o {@link VesselHull} (casco externo esparso) a partir de uma grade de células sólidas (lógica pura, sem Minecraft). O interior é
 * descartado: o ar EXTERIOR é achado por enchente a partir da borda da caixa (o ar fechado dentro do casco não conta) e só ficam as células sólidas
 * que tocam esse ar (ou a borda da caixa). Um casco com blocos demais vira uma grade mais grossa (células de 2, 4 ou 8 blocos), para o pacote e a
 * malha do cliente ficarem limitados.
 */
public final class VesselHullBuilder {
    /** Teto de células do casco enviado (depois disto a grade engrossa). */
    public static final int MAX_BLOCKS = 60_000;
    private static final int MAX_SCALE = 8;

    private VesselHullBuilder() {
    }

    /** A grade de entrada: a cor ARGB de cada bloco (0 = ar; qualquer outro valor = sólido, com os 24 bits baixos como RGB). */
    @FunctionalInterface
    public interface Cells {
        int colorAt(int x, int y, int z);
    }

    /** Monta o casco de uma caixa de {@code sx x sy x sz} blocos com o pivô dado (em blocos), no limite {@link #MAX_BLOCKS}. */
    public static VesselHull build(int sx, int sy, int sz, Cells cells, float pivotX, float pivotY, float pivotZ) {
        return build(sx, sy, sz, cells, pivotX, pivotY, pivotZ, MAX_BLOCKS);
    }

    public static VesselHull build(int sx, int sy, int sz, Cells cells, float pivotX, float pivotY, float pivotZ, int maxCells) {
        int[] grid = new int[sx * sy * sz];
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int c = cells.colorAt(x, y, z);
                    grid[x + sx * (z + sz * y)] = c == 0 ? 0 : 0xFF000000 | c;
                }
            }
        }
        int scale = 1;
        int gx = sx;
        int gy = sy;
        int gz = sz;
        int[] current = grid;
        while (true) {
            VesselHull hull = shell(gx, gy, gz, current, scale, pivotX, pivotY, pivotZ, maxCells);
            if (hull != null || scale >= MAX_SCALE) {
                return hull != null ? hull : shell(gx, gy, gz, current, scale, pivotX, pivotY, pivotZ, Integer.MAX_VALUE);
            }
            int nx = (gx + 1) / 2;
            int ny = (gy + 1) / 2;
            int nz = (gz + 1) / 2;
            current = coarser(current, gx, gy, gz, nx, ny, nz);
            gx = nx;
            gy = ny;
            gz = nz;
            scale *= 2;
        }
    }

    /** A grade com células de 2x2x2: sólida se qualquer uma das oito é; a cor é a da primeira sólida. */
    private static int[] coarser(int[] grid, int gx, int gy, int gz, int nx, int ny, int nz) {
        int[] out = new int[nx * ny * nz];
        for (int y = 0; y < gy; y++) {
            for (int z = 0; z < gz; z++) {
                for (int x = 0; x < gx; x++) {
                    int c = grid[x + gx * (z + gz * y)];
                    if (c != 0) {
                        int at = (x >> 1) + nx * ((z >> 1) + nz * (y >> 1));
                        if (out[at] == 0) {
                            out[at] = c;
                        }
                    }
                }
            }
        }
        return out;
    }

    /** O casco da grade (null se tiver mais de {@code maxCells} células). */
    private static VesselHull shell(int sx, int sy, int sz, int[] grid, int scale, float px, float py, float pz, int maxCells) {
        int nx = sx + 2;
        int ny = sy + 2;
        int nz = sz + 2;
        // ar exterior: enchente (6 vizinhos) por células sem bloco, a partir do canto da borda de uma célula de largura em volta da caixa
        boolean[] outside = new boolean[nx * ny * nz];
        int[] stack = new int[1024];
        int top = 0;
        stack[top++] = 0;
        outside[0] = true;
        int[] dx = {1, -1, 0, 0, 0, 0};
        int[] dy = {0, 0, 1, -1, 0, 0};
        int[] dz = {0, 0, 0, 0, 1, -1};
        while (top > 0) {
            int at = stack[--top];
            int x = at % nx;
            int z = (at / nx) % nz;
            int y = at / (nx * nz);
            for (int d = 0; d < 6; d++) {
                int ax = x + dx[d];
                int ay = y + dy[d];
                int az = z + dz[d];
                if (ax < 0 || ay < 0 || az < 0 || ax >= nx || ay >= ny || az >= nz) {
                    continue;
                }
                int idx = ax + nx * (az + nz * ay);
                if (outside[idx]) {
                    continue;
                }
                boolean inside = ax >= 1 && ax <= sx && ay >= 1 && ay <= sy && az >= 1 && az <= sz;
                if (inside && grid[(ax - 1) + sx * ((az - 1) + sz * (ay - 1))] != 0) {
                    continue;      // bloco sólido
                }
                outside[idx] = true;
                if (top == stack.length) {
                    stack = Arrays.copyOf(stack, top * 2);
                }
                stack[top++] = idx;
            }
        }
        IntArrayList cells = new IntArrayList();
        IntArrayList rgbs = new IntArrayList();
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int c = grid[x + sx * (z + sz * y)];
                    if (c == 0) {
                        continue;
                    }
                    int px1 = x + 1;
                    int py1 = y + 1;
                    int pz1 = z + 1;
                    boolean exposed = outside[(px1 + 1) + nx * (pz1 + nz * py1)] || outside[(px1 - 1) + nx * (pz1 + nz * py1)]
                            || outside[px1 + nx * (pz1 + nz * (py1 + 1))] || outside[px1 + nx * (pz1 + nz * (py1 - 1))]
                            || outside[px1 + nx * ((pz1 + 1) + nz * py1)] || outside[px1 + nx * ((pz1 - 1) + nz * py1)];
                    if (exposed) {
                        if (cells.size() >= maxCells) {
                            return null;
                        }
                        cells.add(x + sx * (z + sz * y));
                        rgbs.add(c & 0xFFFFFF);
                    }
                }
            }
        }
        // paleta (as cores exatas; se passar do limite, as cores são quantizadas a 4 bits por canal)
        short[] colors = new short[cells.size()];
        int[] palette = paletteOf(rgbs, colors, false);
        if (palette == null) {
            palette = paletteOf(rgbs, colors, true);
        }
        return new VesselHull(sx, sy, sz, scale, px, py, pz, palette, cells.toIntArray(), colors);
    }

    private static int[] paletteOf(IntArrayList rgbs, short[] colors, boolean quantize) {
        Int2ShortOpenHashMap index = new Int2ShortOpenHashMap();
        index.defaultReturnValue((short) -1);
        IntArrayList palette = new IntArrayList();
        for (int i = 0; i < rgbs.size(); i++) {
            int rgb = quantize ? (rgbs.getInt(i) & 0xF0F0F0) | 0x080808 : rgbs.getInt(i);
            short at = index.get(rgb);
            if (at < 0) {
                if (palette.size() >= VesselHull.MAX_PALETTE) {
                    return null;
                }
                at = (short) palette.size();
                index.put(rgb, at);
                palette.add(rgb);
            }
            colors[i] = at;
        }
        return palette.toIntArray();
    }
}
