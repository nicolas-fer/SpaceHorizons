package com.spacehorizons.space.vessel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * O casco externo ESPARSO de uma nave ou foguete (docs/PLANO_NAVES.md, 4.3; fase 2d), o que o cliente precisa para desenhar OUTRO veículo no céu: só as
 * células da superfície (o que toca o espaço aberto), uma cor RGB por célula (paleta) e nada do interior. Lógica pura (sem Minecraft): o servidor
 * monta o casco ({@link VesselHullBuilder}) e o codifica ({@link #encode}); o cliente o decodifica ({@link #decode}) e faz a malha ({@link VesselMesh}).
 *
 * <p>Uma célula cobre {@code scale}x{@code scale}x{@code scale} blocos (1 num casco pequeno; mais num casco enorme, para o pacote e a malha ficarem
 * limitados: ver {@link VesselHullBuilder#MAX_BLOCKS}). O índice linear de uma célula é {@code x + sizeX * (z + sizeZ * y)} e a lista de células é
 * ordenada por ele. O pivô (em blocos, nas coordenadas do casco) é o ponto que fica na posição virtual do veículo e em torno do qual ele gira.
 *
 * <p>Formato ({@link #encode}): um byte de versão, o resto comprimido (Deflate): escala, tamanho, pivô, paleta, e as células como diferenças de
 * índice (varint) com o índice da cor (varint). A leitura recusa (IllegalArgumentException) entrada incoerente ou grande demais.
 */
public final class VesselHull {
    private static final int FORMAT = 1;
    /** Teto do volume (células) de um casco lido da rede: o maior casco do plano é 128 x 256 x 128 = 4,2 milhões. */
    public static final int MAX_VOLUME = 16_000_000;
    /** Teto das células de um casco lido da rede. */
    public static final int MAX_CELLS = 400_000;
    public static final int MAX_PALETTE = 4096;
    /** Teto dos bytes depois de descomprimir (proteção contra pacote que se expande demais). */
    private static final int MAX_INFLATED = 8 * 1024 * 1024;

    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;
    /** Blocos por lado de uma célula. */
    public final int scale;
    /** O pivô, em blocos, nas coordenadas do casco. */
    public final float pivotX;
    public final float pivotY;
    public final float pivotZ;
    private final int[] palette;
    private final int[] cells;
    private final short[] colors;

    public VesselHull(int sizeX, int sizeY, int sizeZ, int scale, float pivotX, float pivotY, float pivotZ, int[] palette, int[] cells, short[] colors) {
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1 || (long) sizeX * sizeY * sizeZ > MAX_VOLUME) {
            throw new IllegalArgumentException("tamanho de casco inválido: " + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        if (scale < 1 || scale > 64) {
            throw new IllegalArgumentException("escala de casco inválida: " + scale);
        }
        if (cells.length != colors.length || cells.length > MAX_CELLS || palette.length > MAX_PALETTE) {
            throw new IllegalArgumentException("casco com células demais ou incoerente: " + cells.length + " células, " + palette.length + " cores");
        }
        long volume = (long) sizeX * sizeY * sizeZ;
        int previous = -1;
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] <= previous || cells[i] >= volume) {
                throw new IllegalArgumentException("célula fora de ordem ou do volume: " + cells[i]);
            }
            if (colors[i] < 0 || colors[i] >= palette.length) {
                throw new IllegalArgumentException("cor fora da paleta: " + colors[i]);
            }
            previous = cells[i];
        }
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.scale = scale;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;
        this.palette = palette.clone();
        this.cells = cells.clone();
        this.colors = colors.clone();
    }

    public int cellCount() {
        return this.cells.length;
    }

    /** O índice linear da célula {@code i} (ordenado). */
    public int cellIndex(int i) {
        return this.cells[i];
    }

    /** A cor RGB da célula {@code i}. */
    public int cellRgb(int i) {
        return this.palette[this.colors[i]];
    }

    public int paletteSize() {
        return this.palette.length;
    }

    /** Índice linear de (x, y, z) em células. */
    public int index(int x, int y, int z) {
        return x + this.sizeX * (z + this.sizeZ * y);
    }

    /** Metade da diagonal da caixa do casco, em blocos: o raio da esfera que o cobre. */
    public double radius() {
        double dx = (double) this.sizeX * this.scale;
        double dy = (double) this.sizeY * this.scale;
        double dz = (double) this.sizeZ * this.scale;
        return 0.5 * Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ---------------------------------------------------------------- rede

    public byte[] encode() {
        ByteArrayOutputStream raw = new ByteArrayOutputStream(64 + this.cells.length * 3);
        try (DataOutputStream out = new DataOutputStream(raw)) {
            writeVarInt(out, this.scale);
            writeVarInt(out, this.sizeX);
            writeVarInt(out, this.sizeY);
            writeVarInt(out, this.sizeZ);
            out.writeFloat(this.pivotX);
            out.writeFloat(this.pivotY);
            out.writeFloat(this.pivotZ);
            writeVarInt(out, this.palette.length);
            for (int rgb : this.palette) {
                writeVarInt(out, rgb & 0xFFFFFF);
            }
            writeVarInt(out, this.cells.length);
            int previous = -1;
            for (int i = 0; i < this.cells.length; i++) {
                writeVarInt(out, this.cells[i] - previous - 1);
                writeVarInt(out, this.colors[i]);
                previous = this.cells[i];
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        ByteArrayOutputStream packed = new ByteArrayOutputStream(raw.size() / 2 + 16);
        packed.write(FORMAT);
        try (DeflaterOutputStream deflate = new DeflaterOutputStream(packed)) {
            deflate.write(raw.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return packed.toByteArray();
    }

    public static VesselHull decode(byte[] bytes) {
        if (bytes.length < 2 || bytes[0] != FORMAT) {
            throw new IllegalArgumentException("formato de casco desconhecido");
        }
        byte[] inflated;
        try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(bytes, 1, bytes.length - 1))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
                if (out.size() > MAX_INFLATED) {
                    throw new IllegalArgumentException("casco descomprimido grande demais");
                }
            }
            inflated = out.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("casco corrompido: " + e.getMessage(), e);
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(inflated))) {
            int scale = readVarInt(in);
            int sx = readVarInt(in);
            int sy = readVarInt(in);
            int sz = readVarInt(in);
            float px = in.readFloat();
            float py = in.readFloat();
            float pz = in.readFloat();
            int paletteSize = readVarInt(in);
            if (paletteSize < 0 || paletteSize > MAX_PALETTE) {
                throw new IllegalArgumentException("paleta de casco grande demais: " + paletteSize);
            }
            int[] palette = new int[paletteSize];
            for (int i = 0; i < paletteSize; i++) {
                palette[i] = readVarInt(in) & 0xFFFFFF;
            }
            int count = readVarInt(in);
            if (count < 0 || count > MAX_CELLS) {
                throw new IllegalArgumentException("casco com células demais: " + count);
            }
            int[] cells = new int[count];
            short[] colors = new short[count];
            long previous = -1;
            for (int i = 0; i < count; i++) {
                previous += (long) readVarInt(in) + 1L;
                if (previous > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("índice de célula fora do limite");
                }
                cells[i] = (int) previous;
                int color = readVarInt(in);
                if (color < 0 || color > Short.MAX_VALUE) {
                    throw new IllegalArgumentException("índice de cor inválido: " + color);
                }
                colors[i] = (short) color;
            }
            return new VesselHull(sx, sy, sz, scale, px, py, pz, palette, cells, colors);
        } catch (IOException e) {
            throw new IllegalArgumentException("casco truncado: " + e.getMessage(), e);
        }
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.writeByte(v);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int result = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = in.readUnsignedByte();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new IllegalArgumentException("varint grande demais");
    }
}
