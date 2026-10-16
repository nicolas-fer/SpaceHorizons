package com.spacehorizons.rocket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.spacehorizons.space.flight.RocketStats;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Retrato imutável de um foguete montado: os blocos (grade densa + paleta de BlockState), o NBT dos block entities, o
 * cockpit e um id estável (UUID, chave do cache de malha no cliente). Coordenadas locais 0..tamanho-1: a caixa mínima
 * do foguete é a origem, então o retrato não depende de onde o foguete foi montado.
 *
 * Três formas: NBT (salvar a entidade; paleta + lista esparsa com {@code Pos} em long), rede (spawn complexo, repetições
 * compactadas) e o objeto em memória usado por física e render. A grade e as repetições são lógica pura em
 * {@link PortraitGrid}.
 */
public final class RocketPortrait {
    /** Teto de entradas da paleta lida da rede ou do NBT (cada bloco do foguete usa no máximo um estado). */
    private static final int MAX_PALETTE = RocketAssembly.MAX_BLOCKS + 1;

    /** Sem blocos: o estado da entidade antes de receber o retrato (spawn complexo chega logo depois do spawn). */
    public static final RocketPortrait EMPTY = new RocketPortrait(new UUID(0L, 0L), PortraitGrid.empty(0, 0, 0),
            List.of(Blocks.AIR.defaultBlockState()), new Long2ObjectLinkedOpenHashMap<>(), BlockPos.ZERO);

    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;
    private final UUID id;
    private final PortraitGrid grid;
    private final List<BlockState> palette;
    private final Long2ObjectMap<CompoundTag> blockEntities;
    private final BlockPos cockpit;
    private volatile RocketCensus census;
    private volatile List<LoadedBlockEntity> loaded;

    /** A paleta tem o ar no índice 0; {@code blockEntities} mapeia a posição local (asLong) ao NBT com id e sem x/y/z. */
    public RocketPortrait(UUID id, PortraitGrid grid, List<BlockState> palette, Long2ObjectMap<CompoundTag> blockEntities,
            BlockPos cockpit) {
        if (palette.isEmpty() || !palette.get(0).isAir()) {
            throw new IllegalArgumentException("o índice 0 da paleta do retrato deve ser o ar");
        }
        if (grid.maxIndex() >= palette.size()) {
            throw new IllegalArgumentException("a grade usa um índice fora da paleta");
        }
        this.id = id;
        this.grid = grid;
        this.palette = List.copyOf(palette);
        this.sizeX = grid.sizeX;
        this.sizeY = grid.sizeY;
        this.sizeZ = grid.sizeZ;
        Long2ObjectMap<CompoundTag> copy = new Long2ObjectLinkedOpenHashMap<>();
        for (Long2ObjectMap.Entry<CompoundTag> entry : blockEntities.long2ObjectEntrySet()) {
            copy.put(entry.getLongKey(), entry.getValue().copy());
        }
        this.blockEntities = copy;
        this.cockpit = cockpit.immutable();
    }

    /** Chave do cache de malha: dois foguetes montados em momentos diferentes têm ids diferentes. */
    public UUID id() {
        return this.id;
    }

    public boolean isEmpty() {
        return this.grid.volume() == 0;
    }

    public PortraitGrid grid() {
        return this.grid;
    }

    public List<BlockState> palette() {
        return this.palette;
    }

    /** Posição local do cockpit (o assento do piloto). */
    public BlockPos cockpit() {
        return this.cockpit;
    }

    public boolean inside(int x, int y, int z) {
        return this.grid.inside(x, y, z);
    }

    public BlockState get(int x, int y, int z) {
        return this.palette.get(this.grid.get(x, y, z));
    }

    public BlockState get(BlockPos pos) {
        return this.get(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Quantos blocos o foguete tem. */
    public int blockCount() {
        return this.grid.nonEmptyCount();
    }

    /** NBT dos block entities (sem x/y/z) por posição local em long; a vista é só de leitura. */
    public Long2ObjectMap<CompoundTag> blockEntities() {
        return it.unimi.dsi.fastutil.longs.Long2ObjectMaps.unmodifiable(this.blockEntities);
    }

    /** Contagem de blocos por papel (calculada uma vez). */
    public RocketCensus census() {
        RocketCensus result = this.census;
        if (result == null) {
            RocketCensus c = RocketCensus.EMPTY;
            int[] perIndex = new int[this.palette.size()];
            for (int y = 0; y < this.sizeY; y++) {
                for (int z = 0; z < this.sizeZ; z++) {
                    for (int x = 0; x < this.sizeX; x++) {
                        perIndex[this.grid.get(x, y, z)]++;
                    }
                }
            }
            for (int i = 1; i < perIndex.length; i++) {
                c = c.plus(RocketKinds.ofPortraitState(this.palette.get(i)), perIndex[i]);
            }
            this.census = result = c;
        }
        return result;
    }

    /** Massa, empuxo, capacidade de combustível e contagens, a partir dos blocos. */
    public RocketStats stats() {
        return RocketSpecs.statsFor(this.census());
    }

    /** Posições locais dos tanques, de baixo para cima (y, z, x): a ordem em que o combustível é devolvido ao pousar. */
    public List<BlockPos> tankPositions() {
        List<BlockPos> tanks = new ArrayList<>();
        for (int y = 0; y < this.sizeY; y++) {
            for (int z = 0; z < this.sizeZ; z++) {
                for (int x = 0; x < this.sizeX; x++) {
                    if (RocketKinds.ofPortraitState(this.get(x, y, z)) == RocketBlockKind.FUEL_TANK) {
                        tanks.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return tanks;
    }

    /** Combustível guardado nos tanques quando o foguete foi montado, em mB (soma do NBT dos tanques). */
    public int fuelInTanksMb() {
        int total = 0;
        for (BlockPos tank : this.tankPositions()) {
            CompoundTag tag = this.blockEntities.get(tank.asLong());
            if (tag != null) {
                total += RocketFuel.clamp(tag.getIntOr(RocketFuelTankBlockEntity.FUEL_KEY, 0));
            }
        }
        return total;
    }

    // ---------------------------------------------------------------- giro

    /**
     * O retrato girado em torno do eixo vertical por {@code quarterTurns} quartos de volta horários (como
     * {@link Rotation#CLOCKWISE_90}): a grade, as posições dos block entities e o cockpit giram, e cada estado da paleta
     * passa por {@link BlockState#rotate(Rotation)} (a frente do cockpit, escadas, fornalhas...). Com 0 devolve o próprio retrato.
     */
    public RocketPortrait rotated(int quarterTurns) {
        int turns = Math.floorMod(quarterTurns, 4);
        if (turns == 0) {
            return this;
        }
        Rotation rotation = Rotation.values()[turns];
        List<BlockState> newPalette = new ArrayList<>(this.palette.size());
        for (BlockState state : this.palette) {
            newPalette.add(state.rotate(rotation));
        }
        Long2ObjectMap<CompoundTag> newEntities = new Long2ObjectLinkedOpenHashMap<>();
        for (Long2ObjectMap.Entry<CompoundTag> entry : this.blockEntities.long2ObjectEntrySet()) {
            newEntities.put(rotatedPos(turns, BlockPos.of(entry.getLongKey())).asLong(), entry.getValue());
        }
        return new RocketPortrait(UUID.randomUUID(), this.grid.rotated(turns), newPalette, newEntities, rotatedPos(turns, this.cockpit));
    }

    private BlockPos rotatedPos(int turns, BlockPos pos) {
        return new BlockPos(PortraitGrid.rotatedX(turns, pos.getX(), pos.getZ(), this.sizeX, this.sizeZ), pos.getY(),
                PortraitGrid.rotatedZ(turns, pos.getX(), pos.getZ(), this.sizeX, this.sizeZ));
    }

    // ---------------------------------------------------------------- block entities para o render (cliente)

    /** Block entity já carregado do NBT, sem Level: é o que os BlockEntityRenderers do vanilla aceitam. */
    public record LoadedBlockEntity(BlockPos localPos, BlockEntity blockEntity) {
    }

    /** Instancia os block entities uma vez (render thread). {@code registries} = o registryAccess do ClientLevel. */
    public List<LoadedBlockEntity> loadBlockEntities(HolderLookup.Provider registries) {
        List<LoadedBlockEntity> result = this.loaded;
        if (result == null) {
            result = new ArrayList<>();
            for (Long2ObjectMap.Entry<CompoundTag> entry : this.blockEntities.long2ObjectEntrySet()) {
                BlockPos pos = BlockPos.of(entry.getLongKey());
                BlockEntity blockEntity = BlockEntity.loadStatic(pos, this.get(pos), entry.getValue(), registries);
                if (blockEntity != null) {
                    result.add(new LoadedBlockEntity(pos, blockEntity));
                }
            }
            this.loaded = result = Collections.unmodifiableList(result);
        }
        return result;
    }

    // ---------------------------------------------------------------- NBT

    /** Id, tamanho, cockpit, paleta, lista esparsa de blocos ({Pos, State}) e NBT dos block entities ({Pos, Nbt}). */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("IdHi", this.id.getMostSignificantBits());
        tag.putLong("IdLo", this.id.getLeastSignificantBits());
        tag.putIntArray("Size", new int[] {this.sizeX, this.sizeY, this.sizeZ});
        tag.putLong("Cockpit", this.cockpit.asLong());
        ListTag paletteTag = new ListTag();
        for (BlockState state : this.palette) {
            paletteTag.add(NbtUtils.writeBlockState(state));
        }
        tag.put("Palette", paletteTag);
        tag.put("Blocks", this.grid.toSparseTag());
        ListTag entities = new ListTag();
        for (Long2ObjectMap.Entry<CompoundTag> entry : this.blockEntities.long2ObjectEntrySet()) {
            CompoundTag item = new CompoundTag();
            item.putLong("Pos", entry.getLongKey());
            item.put("Nbt", entry.getValue().copy());
            entities.add(item);
        }
        tag.put("BlockEntities", entities);
        return tag;
    }

    /** Lê o retrato salvo por {@link #save}; lança IllegalArgumentException se o NBT estiver corrompido. */
    public static RocketPortrait load(HolderLookup.Provider registries, CompoundTag tag) {
        int[] size = tag.getIntArray("Size").orElse(new int[0]);
        if (size.length != 3 || size[0] < 0 || size[1] < 0 || size[2] < 0 || size[0] > RocketAssembly.MAX_EXTENT
                || size[1] > RocketAssembly.MAX_EXTENT || size[2] > RocketAssembly.MAX_EXTENT) {
            throw new IllegalArgumentException("tamanho inválido no retrato do foguete");
        }
        List<BlockState> palette = new ArrayList<>();
        ListTag paletteTag = tag.getListOrEmpty("Palette");
        if (paletteTag.size() > MAX_PALETTE) {
            throw new IllegalArgumentException("paleta grande demais no retrato do foguete");
        }
        var blocks = registries.lookupOrThrow(Registries.BLOCK);
        for (int i = 0; i < paletteTag.size(); i++) {
            palette.add(NbtUtils.readBlockState(blocks, paletteTag.getCompoundOrEmpty(i)));
        }
        if (palette.isEmpty()) {
            palette.add(Blocks.AIR.defaultBlockState());
        }
        PortraitGrid grid = PortraitGrid.fromSparseTag(size[0], size[1], size[2], tag.getListOrEmpty("Blocks"));
        Long2ObjectMap<CompoundTag> entities = new Long2ObjectLinkedOpenHashMap<>();
        ListTag entityTags = tag.getListOrEmpty("BlockEntities");
        for (int i = 0; i < entityTags.size(); i++) {
            CompoundTag item = entityTags.getCompoundOrEmpty(i);
            entities.put(item.getLongOr("Pos", 0L), item.getCompoundOrEmpty("Nbt"));
        }
        UUID id = new UUID(tag.getLongOr("IdHi", 0L), tag.getLongOr("IdLo", 0L));
        return new RocketPortrait(id, grid, palette, entities, BlockPos.of(tag.getLongOr("Cockpit", 0L)));
    }

    // ---------------------------------------------------------------- rede

    /** Escreve o retrato para o pacote de spawn: id, tamanho, cockpit, paleta (ids de estado), repetições da grade e block entities. */
    public static void write(ByteBuf out, RocketPortrait portrait) {
        out.writeLong(portrait.id.getMostSignificantBits());
        out.writeLong(portrait.id.getLeastSignificantBits());
        VarInt.write(out, portrait.sizeX);
        VarInt.write(out, portrait.sizeY);
        VarInt.write(out, portrait.sizeZ);
        VarInt.write(out, portrait.cockpit.getX());
        VarInt.write(out, portrait.cockpit.getY());
        VarInt.write(out, portrait.cockpit.getZ());
        VarInt.write(out, portrait.palette.size());
        for (int i = 1; i < portrait.palette.size(); i++) {
            VarInt.write(out, Block.BLOCK_STATE_REGISTRY.getId(portrait.palette.get(i)));
        }
        portrait.grid.writeRuns(out);
        VarInt.write(out, portrait.blockEntities.size());
        for (Long2ObjectMap.Entry<CompoundTag> entry : portrait.blockEntities.long2ObjectEntrySet()) {
            out.writeLong(entry.getLongKey());
            ByteBufCodecs.COMPOUND_TAG.encode(out, entry.getValue());
        }
    }

    /** Inverso de {@link #write}; recusa (IllegalArgumentException) tamanhos, paleta ou repetições fora dos limites. */
    public static RocketPortrait read(ByteBuf in) {
        UUID id = new UUID(in.readLong(), in.readLong());
        int sizeX = VarInt.read(in);
        int sizeY = VarInt.read(in);
        int sizeZ = VarInt.read(in);
        if (sizeX < 0 || sizeY < 0 || sizeZ < 0 || sizeX > RocketAssembly.MAX_EXTENT || sizeY > RocketAssembly.MAX_EXTENT
                || sizeZ > RocketAssembly.MAX_EXTENT) {
            throw new IllegalArgumentException("tamanho de retrato inválido na rede");
        }
        BlockPos cockpit = new BlockPos(VarInt.read(in), VarInt.read(in), VarInt.read(in));
        int paletteSize = VarInt.read(in);
        if (paletteSize < 1 || paletteSize > MAX_PALETTE) {
            throw new IllegalArgumentException("paleta de retrato inválida na rede: " + paletteSize);
        }
        List<BlockState> palette = new ArrayList<>(paletteSize);
        palette.add(Blocks.AIR.defaultBlockState());
        for (int i = 1; i < paletteSize; i++) {
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(VarInt.read(in));
            palette.add(state == null ? Blocks.AIR.defaultBlockState() : state);
        }
        PortraitGrid grid = PortraitGrid.readRuns(in, sizeX, sizeY, sizeZ, paletteSize - 1);
        int count = VarInt.read(in);
        if (count < 0 || count > RocketAssembly.MAX_BLOCKS) {
            throw new IllegalArgumentException("quantidade de block entities inválida na rede: " + count);
        }
        Long2ObjectMap<CompoundTag> entities = new Long2ObjectLinkedOpenHashMap<>();
        for (int i = 0; i < count; i++) {
            long pos = in.readLong();
            entities.put(pos, ByteBufCodecs.COMPOUND_TAG.decode(in));
        }
        return new RocketPortrait(id, grid, palette, entities, cockpit);
    }

    /** Tamanho em bytes do retrato no pacote de spawn (para recusar a montagem de um foguete grande demais). */
    public static int encodedSize(RocketPortrait portrait) {
        ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
        try {
            write(buffer, portrait);
            return buffer.readableBytes();
        } finally {
            buffer.release();
        }
    }

    /** Monta o mapa de paleta de uma lista de estados na ordem em que aparecem (o ar é sempre o índice 0). */
    public static Map<BlockState, Integer> newPaletteIndex() {
        Map<BlockState, Integer> index = new HashMap<>();
        index.put(Blocks.AIR.defaultBlockState(), 0);
        return index;
    }
}
