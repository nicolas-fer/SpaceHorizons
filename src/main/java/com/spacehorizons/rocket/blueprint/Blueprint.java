package com.spacehorizons.rocket.blueprint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.IntStream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spacehorizons.rocket.DesignReport;
import com.spacehorizons.rocket.RocketAssembly;
import com.spacehorizons.rocket.RocketBlockKind;
import com.spacehorizons.rocket.RocketDesign;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Uma planta de montagem (docs/PLANO_TIERS.md, seção 4): a lista de blocos de um foguete RELATIVOS à plataforma de lançamento em que a
 * planta é posta (a plataforma-âncora fica em 0, 0, 0), com o estado de cada bloco e o papel dele na montagem ({@link RocketBlockKind}).
 * Inclui as plataformas da base (papel {@link RocketBlockKind#PAD}) e as peças dos multiblocos bloco a bloco: a estrutura formada (mestre e
 * partes do motor, conexões do tanque) é derivada da partição, então a planta guarda o estado NORMALIZADO ({@code BlueprintStates}).
 *
 * <p>Lógica pura (sem registros do jogo): o estado é a string de bloco do Minecraft ({@code spacehorizons:rocket_engine[facing=north]};
 * o cliente e o servidor a resolvem uma vez) e o papel vai junto, então o destino do projeto ({@link #design}) sai do MESMO cálculo do painel
 * ({@link RocketDesign#analyze(RocketAssembly.Grid, int, int, int)}) sem o mundo. Imutável e comparável por valor (é um data component).
 *
 * <p>Formato salvo (NBT/JSON, {@link #CODEC}): {@code id} (planta embutida ou vazio), {@code name}, {@code author}, {@code palette} (estados),
 * {@code kinds} (papéis, pelo nome, um por estado da paleta), {@code positions} (int por bloco: dx, dy e dz em 10 bits cada, deslocados de
 * 512) e {@code states} (índice da paleta por bloco).
 */
public final class Blueprint {
    /** Teto de blocos de uma planta: o foguete inteiro (8.192) mais as plataformas da base, com folga. */
    public static final int MAX_ENTRIES = 16_384;
    /** Deslocamentos de -512 a 511 em cada eixo (o foguete cabe em 128 x 256 x 128). */
    public static final int RANGE = 512;
    private static final int MASK = 1023;

    private final String id;
    private final String name;
    private final String author;
    private final List<String> palette;
    private final List<RocketBlockKind> kinds;
    private final int[] positions;
    private final int[] states;
    private final int hash;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private volatile Map<Integer, RocketBlockKind> kindByPos;
    private volatile DesignReport design;

    private Blueprint(String id, String name, String author, List<String> palette, List<RocketBlockKind> kinds, int[] positions, int[] states) {
        if (palette.size() != kinds.size()) {
            throw new IllegalArgumentException("paleta e papéis com tamanhos diferentes: " + palette.size() + " e " + kinds.size());
        }
        if (positions.length != states.length) {
            throw new IllegalArgumentException("posições e estados com tamanhos diferentes: " + positions.length + " e " + states.length);
        }
        if (positions.length > MAX_ENTRIES || palette.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("planta grande demais: " + positions.length + " blocos (teto " + MAX_ENTRIES + ")");
        }
        this.id = id;
        this.name = name;
        this.author = author;
        this.palette = List.copyOf(palette);
        this.kinds = List.copyOf(kinds);
        this.positions = positions.clone();
        this.states = states.clone();
        int x0 = 0;
        int y0 = 0;
        int z0 = 0;
        int x1 = 0;
        int y1 = 0;
        int z1 = 0;
        for (int i = 0; i < this.states.length; i++) {
            if (this.states[i] < 0 || this.states[i] >= this.palette.size()) {
                throw new IllegalArgumentException("índice de paleta fora da paleta: " + this.states[i]);
            }
            int x = unpackX(this.positions[i]);
            int y = unpackY(this.positions[i]);
            int z = unpackZ(this.positions[i]);
            x0 = Math.min(x0, x);
            y0 = Math.min(y0, y);
            z0 = Math.min(z0, z);
            x1 = Math.max(x1, x);
            y1 = Math.max(y1, y);
            z1 = Math.max(z1, z);
        }
        // a caixa sempre inclui a âncora (0, 0, 0)
        this.minX = x0;
        this.minY = y0;
        this.minZ = z0;
        this.maxX = x1;
        this.maxY = y1;
        this.maxZ = z1;
        this.hash = Objects.hash(id, name, author, this.palette, this.kinds, Arrays.hashCode(this.positions), Arrays.hashCode(this.states));
    }

    // ---------------------------------------------------------------- posições

    /** Empacota um deslocamento (cada eixo de -512 a 511) num int. */
    public static int pack(int dx, int dy, int dz) {
        if (dx < -RANGE || dx >= RANGE || dy < -RANGE || dy >= RANGE || dz < -RANGE || dz >= RANGE) {
            throw new IllegalArgumentException("deslocamento fora do alcance da planta: " + dx + ", " + dy + ", " + dz);
        }
        return (dx + RANGE) << 20 | (dy + RANGE) << 10 | (dz + RANGE);
    }

    public static int unpackX(int packed) {
        return (packed >>> 20 & MASK) - RANGE;
    }

    public static int unpackY(int packed) {
        return (packed >>> 10 & MASK) - RANGE;
    }

    public static int unpackZ(int packed) {
        return (packed & MASK) - RANGE;
    }

    // ---------------------------------------------------------------- acesso

    /** A planta embutida de onde esta veio ({@link Blueprints}), ou vazio para uma planta salva de um foguete. */
    public String id() {
        return this.id;
    }

    /** O nome dado pelo jogador (pode ser vazio: aí o nome mostrado vem do id ou do autor). */
    public String name() {
        return this.name;
    }

    /** Quem salvou a planta (vazio nas embutidas). */
    public String author() {
        return this.author;
    }

    public List<String> palette() {
        return this.palette;
    }

    public List<RocketBlockKind> kinds() {
        return this.kinds;
    }

    public int size() {
        return this.positions.length;
    }

    public int dx(int i) {
        return unpackX(this.positions[i]);
    }

    public int dy(int i) {
        return unpackY(this.positions[i]);
    }

    public int dz(int i) {
        return unpackZ(this.positions[i]);
    }

    /** O índice da paleta do bloco {@code i}. */
    public int stateIndex(int i) {
        return this.states[i];
    }

    /** O estado (string de bloco) do bloco {@code i}. */
    public String state(int i) {
        return this.palette.get(this.states[i]);
    }

    public RocketBlockKind kind(int i) {
        return this.kinds.get(this.states[i]);
    }

    /** Caixa inclusiva dos deslocamentos (sempre contém a âncora). */
    public int minX() {
        return this.minX;
    }

    public int minY() {
        return this.minY;
    }

    public int minZ() {
        return this.minZ;
    }

    public int maxX() {
        return this.maxX;
    }

    public int maxY() {
        return this.maxY;
    }

    public int maxZ() {
        return this.maxZ;
    }

    /** Quantos blocos de cada papel (para o tooltip e o log). */
    public int count(RocketBlockKind kind) {
        int n = 0;
        for (int s : this.states) {
            if (this.kinds.get(s) == kind) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- o projeto

    /**
     * A planta vista como mundo pela busca da montagem: o papel de cada bloco nos deslocamentos e ar no resto. A âncora (0, 0, 0) é sempre
     * plataforma (uma planta salva já a inclui; esta garantia cobre plantas sem base).
     */
    public RocketAssembly.Grid grid() {
        Map<Integer, RocketBlockKind> map = this.kindByPos;
        if (map == null) {
            map = new HashMap<>(this.positions.length * 2);
            for (int i = 0; i < this.positions.length; i++) {
                map.put(this.positions[i], this.kind(i));
            }
            map.putIfAbsent(pack(0, 0, 0), RocketBlockKind.PAD);
            this.kindByPos = map;
        }
        Map<Integer, RocketBlockKind> m = map;
        return (x, y, z) -> {
            if (x < -RANGE || x >= RANGE || y < -RANGE || y >= RANGE || z < -RANGE || z >= RANGE) {
                return RocketBlockKind.AIR;
            }
            return m.getOrDefault(pack(x, y, z), RocketBlockKind.AIR);
        };
    }

    /**
     * O relatório do projeto da planta (massa, empuxo, Δv, DESTINO) com a plataforma-âncora em 0, 0, 0: o mesmo cálculo do painel sobre a
     * planta montada (calculado uma vez).
     */
    public DesignReport design() {
        DesignReport r = this.design;
        if (r == null) {
            r = RocketDesign.analyze(this.grid(), 0, 0, 0);
            this.design = r;
        }
        return r;
    }

    // ---------------------------------------------------------------- construção

    public static Builder builder(String id, String name, String author) {
        return new Builder(id, name, author);
    }

    /** Monta uma planta: blocos em qualquer ordem (a mesma posição duas vezes fica com o último); a ordem final é de baixo para cima. */
    public static final class Builder {
        private final String id;
        private final String name;
        private final String author;
        private final TreeMap<Long, String[]> blocks = new TreeMap<>();

        private Builder(String id, String name, String author) {
            this.id = id;
            this.name = name;
            this.author = author;
        }

        public Builder add(int dx, int dy, int dz, String state, RocketBlockKind kind) {
            pack(dx, dy, dz); // valida o alcance
            // ordem de construção: y, depois z, depois x
            long key = ((long) (dy + RANGE) << 20) | ((long) (dz + RANGE) << 10) | (dx + RANGE);
            this.blocks.put(key, new String[] {state, kind.name()});
            return this;
        }

        public Builder fill(int x0, int y0, int z0, int w, int h, int d, String state, RocketBlockKind kind) {
            for (int y = y0; y < y0 + h; y++) {
                for (int z = z0; z < z0 + d; z++) {
                    for (int x = x0; x < x0 + w; x++) {
                        this.add(x, y, z, state, kind);
                    }
                }
            }
            return this;
        }

        public int size() {
            return this.blocks.size();
        }

        public Blueprint build() {
            List<String> palette = new ArrayList<>();
            List<RocketBlockKind> kinds = new ArrayList<>();
            Map<String, Integer> index = new HashMap<>();
            int[] positions = new int[this.blocks.size()];
            int[] states = new int[this.blocks.size()];
            int i = 0;
            for (Map.Entry<Long, String[]> e : this.blocks.entrySet()) {
                long key = e.getKey();
                int dy = (int) (key >>> 20 & MASK) - RANGE;
                int dz = (int) (key >>> 10 & MASK) - RANGE;
                int dx = (int) (key & MASK) - RANGE;
                String paletteKey = e.getValue()[0] + "\u0000" + e.getValue()[1];
                int p = index.computeIfAbsent(paletteKey, k -> {
                    palette.add(e.getValue()[0]);
                    kinds.add(RocketBlockKind.valueOf(e.getValue()[1]));
                    return palette.size() - 1;
                });
                positions[i] = pack(dx, dy, dz);
                states[i] = p;
                i++;
            }
            return new Blueprint(this.id, this.name, this.author, palette, kinds, positions, states);
        }
    }

    /** Uma cópia com outro nome (o jogador renomeou a planta na bigorna, por exemplo). */
    public Blueprint withName(String newName) {
        return new Blueprint(this.id, newName, this.author, this.palette, this.kinds, this.positions, this.states);
    }

    // ---------------------------------------------------------------- serialização

    /** Papel pelo nome; um nome desconhecido (de uma versão futura) vale como estrutura comum. */
    static RocketBlockKind kindOf(String name) {
        try {
            return RocketBlockKind.valueOf(name);
        } catch (IllegalArgumentException e) {
            return RocketBlockKind.STRUCTURE;
        }
    }

    private static DataResult<Blueprint> create(String id, String name, String author, List<String> palette, List<String> kinds, int[] positions, int[] states) {
        try {
            return DataResult.success(new Blueprint(id, name, author, palette, kinds.stream().map(Blueprint::kindOf).toList(), positions, states));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "planta inválida: " + e.getMessage());
        }
    }

    private record Raw(String id, String name, String author, List<String> palette, List<String> kinds, int[] positions, int[] states) {
    }

    private static final Codec<Raw> RAW_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("id", "").forGetter(Raw::id),
            Codec.STRING.optionalFieldOf("name", "").forGetter(Raw::name),
            Codec.STRING.optionalFieldOf("author", "").forGetter(Raw::author),
            Codec.STRING.listOf().fieldOf("palette").forGetter(Raw::palette),
            Codec.STRING.listOf().fieldOf("kinds").forGetter(Raw::kinds),
            Codec.INT_STREAM.xmap(IntStream::toArray, IntStream::of).fieldOf("positions").forGetter(Raw::positions),
            Codec.INT_STREAM.xmap(IntStream::toArray, IntStream::of).fieldOf("states").forGetter(Raw::states)).apply(i, Raw::new));

    /** O formato salvo (NBT do item e da plataforma; JSON nos testes). */
    public static final Codec<Blueprint> CODEC = RAW_CODEC.flatXmap(
            r -> create(r.id(), r.name(), r.author(), r.palette(), r.kinds(), r.positions(), r.states()),
            b -> DataResult.success(new Raw(b.id, b.name, b.author, b.palette, b.kinds.stream().map(Enum::name).toList(), b.positions, b.states)));

    /** O formato de rede (item sincronizado e pacotes): varints, com tetos conferidos ao ler. */
    public static final StreamCodec<ByteBuf, Blueprint> STREAM_CODEC = StreamCodec.of(Blueprint::write, Blueprint::read);

    private static void write(ByteBuf raw, Blueprint b) {
        FriendlyByteBuf buf = new FriendlyByteBuf(raw);
        buf.writeUtf(b.id);
        buf.writeUtf(b.name);
        buf.writeUtf(b.author);
        buf.writeVarInt(b.palette.size());
        for (int i = 0; i < b.palette.size(); i++) {
            buf.writeUtf(b.palette.get(i));
            buf.writeUtf(b.kinds.get(i).name());
        }
        buf.writeVarInt(b.positions.length);
        for (int i = 0; i < b.positions.length; i++) {
            buf.writeVarInt(b.positions[i]);
            buf.writeVarInt(b.states[i]);
        }
    }

    private static Blueprint read(ByteBuf raw) {
        FriendlyByteBuf buf = new FriendlyByteBuf(raw);
        String id = buf.readUtf();
        String name = buf.readUtf();
        String author = buf.readUtf();
        int paletteSize = buf.readVarInt();
        if (paletteSize < 0 || paletteSize > MAX_ENTRIES) {
            throw new IllegalArgumentException("paleta de planta inválida: " + paletteSize);
        }
        List<String> palette = new ArrayList<>(paletteSize);
        List<RocketBlockKind> kinds = new ArrayList<>(paletteSize);
        for (int i = 0; i < paletteSize; i++) {
            palette.add(buf.readUtf());
            kinds.add(kindOf(buf.readUtf()));
        }
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_ENTRIES) {
            throw new IllegalArgumentException("planta com blocos demais: " + n);
        }
        int[] positions = new int[n];
        int[] states = new int[n];
        for (int i = 0; i < n; i++) {
            positions[i] = buf.readVarInt();
            states[i] = buf.readVarInt();
        }
        return new Blueprint(id, name, author, palette, kinds, positions, states);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Blueprint b && this.hash == b.hash && this.id.equals(b.id) && this.name.equals(b.name) && this.author.equals(b.author)
                && this.palette.equals(b.palette) && this.kinds.equals(b.kinds) && Arrays.equals(this.positions, b.positions)
                && Arrays.equals(this.states, b.states);
    }

    @Override
    public int hashCode() {
        return this.hash;
    }

    @Override
    public String toString() {
        return "Blueprint[" + (this.id.isEmpty() ? "salva" : this.id) + (this.name.isEmpty() ? "" : " '" + this.name + "'") + ", " + this.size() + " blocos, caixa "
                + (this.maxX - this.minX + 1) + "x" + (this.maxY - this.minY + 1) + "x" + (this.maxZ - this.minZ + 1) + "]";
    }
}
