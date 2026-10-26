package com.spacehorizons.rocket;

import java.util.Arrays;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.VarInt;

/**
 * Grade densa do retrato do foguete: um índice de paleta por célula (0 = vazio), nas coordenadas locais
 * 0..tamanho-1 (a caixa mínima do foguete vira a origem). Lógica pura, sem BlockState, para ser testada sem o jogo:
 * a paleta de estados vive em {@link RocketPortrait}.
 *
 * Índice: x + sizeX * (z + sizeZ * y). Giros no plano horizontal seguem {@code net.minecraft.world.level.block.Rotation}:
 * um quarto de volta é horário vista de cima (o norte vira leste), o mesmo sentido em que o yaw do Minecraft cresce.
 */
public final class PortraitGrid {
    /**
     * Teto de células de um retrato: a caixa da nave da D2 (128 x 128 x 256 = 4.194.304 células). A grade é densa mas guarda cada
     * célula em 16 bits (a paleta tem no máximo {@link RocketAssembly#MAX_BLOCKS} + 1 estados), então a caixa cheia custa 8 MiB.
     */
    public static final int MAX_VOLUME = RocketAssembly.MAX_EXTENT_XZ * RocketAssembly.MAX_EXTENT_XZ * RocketAssembly.MAX_EXTENT_Y;

    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;
    private final char[] cells;

    private PortraitGrid(int sizeX, int sizeY, int sizeZ, char[] cells) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.cells = cells;
    }

    public static PortraitGrid empty(int sizeX, int sizeY, int sizeZ) {
        if (sizeX < 0 || sizeY < 0 || sizeZ < 0 || (long) sizeX * sizeY * sizeZ > MAX_VOLUME) {
            throw new IllegalArgumentException("tamanho de retrato inválido: " + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        return new PortraitGrid(sizeX, sizeY, sizeZ, new char[sizeX * sizeY * sizeZ]);
    }

    public int volume() {
        return this.cells.length;
    }

    public boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < this.sizeX && y < this.sizeY && z < this.sizeZ;
    }

    private int index(int x, int y, int z) {
        return x + this.sizeX * (z + this.sizeZ * y);
    }

    /** Índice de paleta da célula; 0 fora da caixa. */
    public int get(int x, int y, int z) {
        return this.inside(x, y, z) ? this.cells[this.index(x, y, z)] : 0;
    }

    public void set(int x, int y, int z, int paletteIndex) {
        if (!this.inside(x, y, z)) {
            throw new IndexOutOfBoundsException("célula fora do retrato: " + x + ", " + y + ", " + z);
        }
        if (paletteIndex < 0 || paletteIndex > Character.MAX_VALUE) {
            throw new IllegalArgumentException("índice de paleta fora de 16 bits: " + paletteIndex);
        }
        this.cells[this.index(x, y, z)] = (char) paletteIndex;
    }

    /** Cópia da grade (o retrato é imutável: uma mudança gera outro retrato). */
    public PortraitGrid copy() {
        return new PortraitGrid(this.sizeX, this.sizeY, this.sizeZ, this.cells.clone());
    }

    /** Cópia da grade com uma célula trocada. */
    public PortraitGrid with(int x, int y, int z, int paletteIndex) {
        PortraitGrid copy = this.copy();
        copy.set(x, y, z, paletteIndex);
        return copy;
    }

    /** Quantas células não estão vazias. */
    public int nonEmptyCount() {
        int n = 0;
        for (char cell : this.cells) {
            if (cell != 0) {
                n++;
            }
        }
        return n;
    }

    /** Maior índice de paleta em uso (0 se vazio). */
    public int maxIndex() {
        int max = 0;
        for (char cell : this.cells) {
            max = Math.max(max, cell);
        }
        return max;
    }

    // ---------------------------------------------------------------- giro horizontal

    /** O número de quartos de volta horários (0 a 3) mais próximo de um yaw em graus. */
    public static int quarterTurnsFromYaw(double yawDegrees) {
        return Math.floorMod((int) Math.round(yawDegrees / 90.0), 4);
    }

    /** Coluna (x, z) de uma caixa sizeX x sizeZ depois de {@code quarterTurns} quartos de volta horários. */
    public static int rotatedX(int quarterTurns, int x, int z, int sizeX, int sizeZ) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 1 -> sizeZ - 1 - z;
            case 2 -> sizeX - 1 - x;
            case 3 -> z;
            default -> x;
        };
    }

    public static int rotatedZ(int quarterTurns, int x, int z, int sizeX, int sizeZ) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 1 -> x;
            case 2 -> sizeZ - 1 - z;
            case 3 -> sizeX - 1 - x;
            default -> z;
        };
    }

    /** Tamanho em x da caixa depois do giro (troca com z nos quartos de volta ímpares). */
    public static int rotatedSizeX(int quarterTurns, int sizeX, int sizeZ) {
        return (quarterTurns & 1) == 0 ? sizeX : sizeZ;
    }

    public static int rotatedSizeZ(int quarterTurns, int sizeX, int sizeZ) {
        return (quarterTurns & 1) == 0 ? sizeZ : sizeX;
    }

    /** A grade girada em torno do eixo vertical; os índices de paleta não mudam. */
    public PortraitGrid rotated(int quarterTurns) {
        int turns = Math.floorMod(quarterTurns, 4);
        if (turns == 0) {
            return new PortraitGrid(this.sizeX, this.sizeY, this.sizeZ, this.cells.clone());
        }
        PortraitGrid out = new PortraitGrid(rotatedSizeX(turns, this.sizeX, this.sizeZ), this.sizeY,
                rotatedSizeZ(turns, this.sizeX, this.sizeZ), new char[this.cells.length]);
        for (int y = 0; y < this.sizeY; y++) {
            for (int z = 0; z < this.sizeZ; z++) {
                for (int x = 0; x < this.sizeX; x++) {
                    out.cells[out.index(rotatedX(turns, x, z, this.sizeX, this.sizeZ), y,
                            rotatedZ(turns, x, z, this.sizeX, this.sizeZ))] = this.cells[this.index(x, y, z)];
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- NBT esparso: [{Pos: long, State: int}, ...]

    /** As células não vazias como lista NBT: {@code Pos} é a posição local em long (BlockPos.asLong), {@code State} o índice de paleta. */
    public ListTag toSparseTag() {
        ListTag list = new ListTag();
        for (int y = 0; y < this.sizeY; y++) {
            for (int z = 0; z < this.sizeZ; z++) {
                for (int x = 0; x < this.sizeX; x++) {
                    int state = this.cells[this.index(x, y, z)];
                    if (state != 0) {
                        CompoundTag entry = new CompoundTag();
                        entry.putLong("Pos", BlockPos.asLong(x, y, z));
                        entry.putInt("State", state);
                        list.add(entry);
                    }
                }
            }
        }
        return list;
    }

    /** Inverso de {@link #toSparseTag}; posições fora da caixa e índices negativos são recusados. */
    public static PortraitGrid fromSparseTag(int sizeX, int sizeY, int sizeZ, ListTag list) {
        PortraitGrid grid = empty(sizeX, sizeY, sizeZ);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompoundOrEmpty(i);
            long pos = entry.getLongOr("Pos", Long.MIN_VALUE);
            int state = entry.getIntOr("State", -1);
            int x = BlockPos.getX(pos);
            int y = BlockPos.getY(pos);
            int z = BlockPos.getZ(pos);
            if (pos == Long.MIN_VALUE || state <= 0 || !grid.inside(x, y, z)) {
                throw new IllegalArgumentException("entrada inválida no retrato: " + entry);
            }
            grid.cells[grid.index(x, y, z)] = (char) state;
        }
        return grid;
    }

    // ---------------------------------------------------------------- rede: repetições (índice, quantidade)

    /**
     * Grava as células como repetições (índice de paleta, quantidade), em VarInt. Foguetes têm grandes trechos vazios e
     * repetidos, então isto reduz o pacote de spawn a poucas centenas de bytes por centenas de blocos.
     */
    public void writeRuns(ByteBuf out) {
        int i = 0;
        while (i < this.cells.length) {
            int value = this.cells[i];
            int run = 1;
            while (i + run < this.cells.length && this.cells[i + run] == value) {
                run++;
            }
            VarInt.write(out, value);
            VarInt.write(out, run);
            i += run;
        }
    }

    /**
     * Lê as repetições escritas por {@link #writeRuns}. Recusa dados que não somam exatamente o volume ou que usam um
     * índice acima de {@code maxIndex} (a paleta lida junto), para um pacote malformado não corromper o cliente.
     */
    public static PortraitGrid readRuns(ByteBuf in, int sizeX, int sizeY, int sizeZ, int maxIndex) {
        PortraitGrid grid = empty(sizeX, sizeY, sizeZ);
        int at = 0;
        while (at < grid.cells.length) {
            int value = VarInt.read(in);
            int run = VarInt.read(in);
            if (value < 0 || value > maxIndex || value > Character.MAX_VALUE || run <= 0 || run > grid.cells.length - at) {
                throw new IllegalArgumentException("repetição inválida no retrato: índice " + value + ", quantidade " + run);
            }
            Arrays.fill(grid.cells, at, at + run, (char) value);
            at += run;
        }
        return grid;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PortraitGrid other && this.sizeX == other.sizeX && this.sizeY == other.sizeY && this.sizeZ == other.sizeZ
                && Arrays.equals(this.cells, other.cells);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(this.cells) + this.sizeX * 961 + this.sizeY * 31 + this.sizeZ;
    }
}
