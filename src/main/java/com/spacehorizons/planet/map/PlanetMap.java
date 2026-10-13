package com.spacehorizons.planet.map;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.SurfaceType;

/**
 * Mapa do planeta pré-calculado a partir do gerador real do mundo: grade equirretangular com a altura da superfície,
 * a cor média vista de longe, a flag de água e o tipo de superfície aproximado. É a fonte da vista de longe da Terra
 * (malha orbital, corpos no céu, pouso), igual no servidor e no cliente.
 *
 * Geometria: {@code width} colunas cobrem a circunferência inteira (periódicas em X; o nó i fica em
 * x = -C/2 + i·célula) e as linhas cobrem Z de -maxLatitudeBlocks até +maxLatitudeBlocks (o nó j fica em
 * z = -maxLat + j·célula; a última linha pode passar um pouco do limite). Fora disso as consultas repetem a borda.
 * As consultas interpolam bilinearmente (altura e cor) entre os quatro nós vizinhos, com wrap exato em X, então não
 * há costura. Java puro; depois de pronto (fim do bake) só é lido, então pode ser consultado de várias threads.
 *
 * Formato serializado ({@link #serialize()}): cabeçalho fixo (magia, formato, semente, tamanho, impressão do gerador)
 * seguido do corpo (altura, cor, flags) comprimido com deflate. O mesmo byte[] serve para o arquivo de cache e para
 * a rede.
 */
public final class PlanetMap {
    public static final int MAGIC = 0x53484D50; // "SHMP"
    public static final int FORMAT = 1;
    /** Largura (colunas) escolhida para a Terra: célula de 64 blocos na circunferência de 65.536, ~410 mil nós. */
    public static final int DEFAULT_WIDTH = 1024;

    private static final int FLAG_WATER = 0x01;
    private static final int TYPE_SHIFT = 1;
    private static final int TYPE_MASK = 0x0F;

    private final PlanetDefinition planet;
    private final long seed;
    private final int width;
    private final int height;
    private final int circumference;
    private final int maxLat;
    private final double cell;
    private final short[] heights;
    private final int[] colors;
    private final byte[] flags;
    private final String fingerprint;

    public PlanetMap(PlanetDefinition planet, long seed, int width, String fingerprint) {
        this(planet, seed, width, fingerprint, null, null, null);
    }

    private PlanetMap(PlanetDefinition planet, long seed, int width, String fingerprint,
            short[] heights, int[] colors, byte[] flags) {
        if (width < 8 || planet.circumferenceBlocks() % width != 0) {
            throw new IllegalArgumentException("largura deve dividir a circunferência: " + width);
        }
        this.planet = planet;
        this.seed = seed;
        this.width = width;
        this.fingerprint = fingerprint;
        this.circumference = planet.circumferenceBlocks();
        this.maxLat = planet.maxLatitudeBlocks();
        this.cell = (double) circumference / width;
        this.height = rowsFor(planet, width);
        int count = width * height;
        if (heights != null && (heights.length != count || colors.length != count || flags.length != count)) {
            throw new IllegalArgumentException("arrays do mapa com tamanho errado");
        }
        this.heights = heights != null ? heights : new short[count];
        this.colors = colors != null ? colors : new int[count];
        this.flags = flags != null ? flags : new byte[count];
    }

    /** Número de linhas para a largura dada: cobre [-maxLat, +maxLat] com folga de um nó. */
    public static int rowsFor(PlanetDefinition planet, int width) {
        double cell = (double) planet.circumferenceBlocks() / width;
        return (int) Math.ceil(2.0 * planet.maxLatitudeBlocks() / cell) + 1;
    }

    public PlanetDefinition planet() {
        return planet;
    }

    public long seed() {
        return seed;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Lado da célula em blocos. */
    public double cellSize() {
        return cell;
    }

    public String fingerprint() {
        return fingerprint;
    }

    /** X do mundo do nó da coluna i. */
    public double nodeX(int column) {
        return -circumference / 2.0 + column * cell;
    }

    /** Z do mundo do nó da linha j. */
    public double nodeZ(int row) {
        return -maxLat + row * cell;
    }

    // ---- escrita (só durante o bake; linhas distintas podem ser escritas de threads distintas) ----

    public void set(int column, int row, int surfaceY, int rgb, boolean water, SurfaceType type) {
        int index = row * width + column;
        heights[index] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, surfaceY));
        colors[index] = rgb & 0xFFFFFF;
        flags[index] = (byte) ((water ? FLAG_WATER : 0) | (type.ordinal() & TYPE_MASK) << TYPE_SHIFT);
    }

    // ---- leitura ----

    public int heightAtNode(int column, int row) {
        return heights[row * width + column];
    }

    public int colorAtNode(int column, int row) {
        return colors[row * width + column];
    }

    public boolean waterAtNode(int column, int row) {
        return (flags[row * width + column] & FLAG_WATER) != 0;
    }

    public SurfaceType typeAtNode(int column, int row) {
        return SurfaceType.values()[flags[row * width + column] >> TYPE_SHIFT & TYPE_MASK];
    }

    /** Altura (Y do bloco sólido mais alto) interpolada bilinearmente. */
    public double heightAt(double x, double z) {
        Cursor c = cursor(x, z);
        return lerp(lerp(heights[c.i00], heights[c.i10], c.fx), lerp(heights[c.i01], heights[c.i11], c.fx), c.fz);
    }

    /** Cor 0xRRGGBB interpolada bilinearmente por canal. */
    public int colorAt(double x, double z) {
        Cursor c = cursor(x, z);
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            double v = lerp(
                    lerp(colors[c.i00] >> shift & 0xFF, colors[c.i10] >> shift & 0xFF, c.fx),
                    lerp(colors[c.i01] >> shift & 0xFF, colors[c.i11] >> shift & 0xFF, c.fx), c.fz);
            rgb |= (int) Math.round(Math.max(0.0, Math.min(255.0, v))) << shift;
        }
        return rgb;
    }

    /** Água se a fração interpolada de nós com água passa de metade. */
    public boolean waterAt(double x, double z) {
        Cursor c = cursor(x, z);
        double w = lerp(
                lerp(flags[c.i00] & FLAG_WATER, flags[c.i10] & FLAG_WATER, c.fx),
                lerp(flags[c.i01] & FLAG_WATER, flags[c.i11] & FLAG_WATER, c.fx), c.fz);
        return w >= 0.5;
    }

    /** Tipo do nó mais próximo. */
    public SurfaceType typeAt(double x, double z) {
        Cursor c = cursor(x, z);
        int index;
        if (c.fx >= 0.5) {
            index = c.fz < 0.5 ? c.i10 : c.i11;
        } else {
            index = c.fz < 0.5 ? c.i00 : c.i01;
        }
        return SurfaceType.values()[flags[index] >> TYPE_SHIFT & TYPE_MASK];
    }

    /** Mesmos dados (mesmo planeta, semente, tamanho e conteúdo). */
    public boolean sameContent(PlanetMap other) {
        return other != null && planet.equals(other.planet) && seed == other.seed && width == other.width
                && height == other.height && Arrays.equals(heights, other.heights)
                && Arrays.equals(colors, other.colors) && Arrays.equals(flags, other.flags);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Os quatro nós vizinhos e as frações (o JIT elimina a alocação por consulta). */
    private record Cursor(int i00, int i10, int i01, int i11, double fx, double fz) {
    }

    private Cursor cursor(double x, double z) {
        double wrapped = x + circumference / 2.0;
        wrapped -= Math.floor(wrapped / circumference) * circumference;
        double u = wrapped / cell;
        int i0 = (int) Math.floor(u);
        double fx = u - i0;
        if (i0 >= width) { // arredondamento extremo em ponto flutuante
            i0 = 0;
            fx = 0.0;
        }
        int i1 = i0 + 1 == width ? 0 : i0 + 1;

        double v = (Math.max(-maxLat, Math.min(maxLat, z)) + maxLat) / cell;
        v = Math.max(0.0, Math.min(height - 1.0, v));
        int j0 = Math.min((int) Math.floor(v), height - 2);
        double fz = v - j0;
        int j1 = j0 + 1;
        return new Cursor(j0 * width + i0, j0 * width + i1, j1 * width + i0, j1 * width + i1, fx, fz);
    }

    // ---- serialização ----

    /** Bytes (cabeçalho + corpo deflate), prontos para arquivo e rede. */
    public byte[] serialize() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(width * height * 3);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeInt(FORMAT);
            out.writeLong(seed);
            out.writeInt(width);
            out.writeInt(height);
            out.writeUTF(fingerprint);
            out.flush();
            Deflater deflater = new Deflater(6);
            try (DataOutputStream body = new DataOutputStream(new DeflaterOutputStream(bytes, deflater, 1 << 16))) {
                for (short h : heights) {
                    body.writeShort(h);
                }
                for (int rgb : colors) {
                    body.writeByte(rgb >> 16);
                    body.writeByte(rgb >> 8);
                    body.writeByte(rgb);
                }
                body.write(flags);
            } finally {
                deflater.end();
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("falha ao serializar o mapa do planeta", e);
        }
    }

    /** Cabeçalho lido sem inflar o corpo: serve para validar o cache antes de pagar pela descompressão. */
    public record Header(long seed, int width, int height, String fingerprint) {
        public boolean matches(PlanetDefinition planet, long expectedSeed, int expectedWidth, String expectedFingerprint) {
            return seed == expectedSeed && width == expectedWidth && height == rowsFor(planet, expectedWidth)
                    && fingerprint.equals(expectedFingerprint);
        }
    }

    /** @return o cabeçalho, ou null se os bytes não são um mapa deste formato. */
    public static Header readHeader(byte[] data) {
        try {
            return readHeader(new DataInputStream(new ByteArrayInputStream(data)));
        } catch (IOException e) {
            return null;
        }
    }

    private static Header readHeader(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC || in.readInt() != FORMAT) {
            return null;
        }
        long seed = in.readLong();
        int width = in.readInt();
        int height = in.readInt();
        String fingerprint = in.readUTF();
        return new Header(seed, width, height, fingerprint);
    }

    /** @throws IOException se os bytes forem inválidos, de outro formato ou não couberem no planeta */
    public static PlanetMap deserialize(PlanetDefinition planet, byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        Header header = readHeader(in);
        if (header == null) {
            throw new IOException("mapa do planeta inválido ou de outro formato");
        }
        if (header.width() < 8 || planet.circumferenceBlocks() % header.width() != 0
                || header.height() != rowsFor(planet, header.width())) {
            throw new IOException("tamanho do mapa incompatível com o planeta: " + header.width() + "x" + header.height());
        }
        int count = header.width() * header.height();
        short[] heights = new short[count];
        int[] colors = new int[count];
        byte[] flags = new byte[count];
        try (DataInputStream body = new DataInputStream(new InflaterInputStream(in))) {
            for (int i = 0; i < count; i++) {
                heights[i] = body.readShort();
            }
            for (int i = 0; i < count; i++) {
                colors[i] = body.readUnsignedByte() << 16 | body.readUnsignedByte() << 8 | body.readUnsignedByte();
            }
            body.readFully(flags);
        }
        return new PlanetMap(planet, header.seed(), header.width(), header.fingerprint(), heights, colors, flags);
    }
}
