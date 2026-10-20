package com.spacehorizons.ship;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * O "romaneio" de uma nave na conversão entidade ↔ blocos (spike S3): o que entrou tem de ser o que saiu. Guarda totais e dois
 * hashes que não dependem da ordem (soma de misturas por posição), mais a contagem de blocos por tipo e de itens por id, para o
 * relatório de perdas dizer O QUE sumiu e não só "diferente". Lógica pura (só NBT, sem registros do jogo), testável sem o Minecraft.
 *
 * <p>Fornalhas (block entities com progresso de cozimento) mudam sozinhas com o tempo (o minério vira lingote): o conteúdo e o
 * progresso delas não entram no hash do NBT nem na contagem de itens, e ficam separados em {@code volatileItems}, só informativos.
 * A integridade estrita delas é conferida na hora da colocação, no mesmo tick, antes de qualquer tick do jogo (ver o job).
 *
 * @param blocks blocos que não são ar
 * @param blockEntities block entities
 * @param stateHash hash (soma) das posições com o estado de cada bloco
 * @param nbtHash hash (soma) das posições com o NBT de cada block entity (sem os campos voláteis)
 * @param perBlock blocos por id
 * @param items itens por id, em todos os inventários (inclusive dentro de outros itens), menos os das fornalhas
 * @param volatileBlockEntities quantas fornalhas
 * @param volatileItems itens nas fornalhas
 * @param fuelMb combustível somado dos tanques (chave {@value #FUEL_KEY} do NBT)
 */
public record ShipManifest(int blocks, int blockEntities, long stateHash, long nbtHash, Map<String, Integer> perBlock, Map<String, Integer> items,
        int volatileBlockEntities, int volatileItems, int fuelMb) {
    /** Chave do NBT do tanque de combustível (a mesma de {@code RocketFuelTankBlockEntity.FUEL_KEY}). */
    public static final String FUEL_KEY = "fuel_mb";
    /** Campos do NBT de uma fornalha que mudam com o tempo: fora do hash. */
    static final Set<String> VOLATILE_KEYS = Set.of("lit_time_remaining", "lit_total_time", "cooking_time_spent", "cooking_total_time", "TransferCooldown",
            "Items", "RecipesUsed");
    /** Marca de uma fornalha (e do que cozinha): este campo existe no NBT dela. */
    static final String VOLATILE_MARK = "cooking_time_spent";
    /** Os campos que um ItemStack salvo pode ter (mais nada): é assim que se reconhece um item dentro de um NBT qualquer. */
    private static final Set<String> ITEM_KEYS = Set.of("id", "count", "Slot", "components");

    public static final Codec<ShipManifest> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("blocks").forGetter(ShipManifest::blocks),
            Codec.INT.fieldOf("block_entities").forGetter(ShipManifest::blockEntities),
            Codec.LONG.fieldOf("state_hash").forGetter(ShipManifest::stateHash),
            Codec.LONG.fieldOf("nbt_hash").forGetter(ShipManifest::nbtHash),
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("per_block").forGetter(ShipManifest::perBlock),
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("items").forGetter(ShipManifest::items),
            Codec.INT.fieldOf("volatile_block_entities").forGetter(ShipManifest::volatileBlockEntities),
            Codec.INT.fieldOf("volatile_items").forGetter(ShipManifest::volatileItems),
            Codec.INT.fieldOf("fuel_mb").forGetter(ShipManifest::fuelMb)
    ).apply(i, ShipManifest::new));

    public static final ShipManifest EMPTY = new ShipManifest(0, 0, 0L, 0L, Map.of(), Map.of(), 0, 0, 0);

    /** Total de itens (fora das fornalhas). */
    public int totalItems() {
        int total = 0;
        for (int n : this.items.values()) {
            total += n;
        }
        return total;
    }

    // ---------------------------------------------------------------- comparação

    /**
     * As diferenças de {@code after} em relação a este romaneio, uma frase cada, em pt-BR; lista vazia = nada se perdeu nem apareceu.
     * Os itens e blocos por tipo dizem o que foi; os hashes pegam o que a contagem não vê (um bloco no lugar errado, um NBT alterado).
     */
    public List<String> diff(ShipManifest after) {
        List<String> out = new ArrayList<>();
        if (this.blocks != after.blocks) {
            out.add("blocos: " + this.blocks + " -> " + after.blocks);
        }
        diffMap("bloco ", this.perBlock, after.perBlock, out);
        if (this.blockEntities != after.blockEntities) {
            out.add("block entities: " + this.blockEntities + " -> " + after.blockEntities);
        }
        diffMap("item ", this.items, after.items, out);
        if (this.fuelMb != after.fuelMb) {
            out.add("combustível dos tanques: " + this.fuelMb + " mB -> " + after.fuelMb + " mB");
        }
        if (this.blocks == after.blocks && this.perBlock.equals(after.perBlock) && this.stateHash != after.stateHash) {
            out.add("mesmos blocos por tipo, mas algum está em outra posição ou com outro estado (hash dos estados)");
        }
        if (this.blockEntities == after.blockEntities && this.nbtHash != after.nbtHash) {
            out.add("algum NBT de block entity mudou (hash dos NBT, sem fornalhas)");
        }
        return out;
    }

    private static void diffMap(String prefix, Map<String, Integer> before, Map<String, Integer> after, List<String> out) {
        Set<String> keys = new java.util.TreeSet<>(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            int a = before.getOrDefault(key, 0);
            int b = after.getOrDefault(key, 0);
            if (a != b) {
                out.add(prefix + key + ": " + a + " -> " + b + (b < a ? " (perdeu " + (a - b) + ")" : " (ganhou " + (b - a) + ")"));
            }
        }
    }

    /** Resumo numa linha para o log. */
    public String summary() {
        return this.blocks + " blocos, " + this.blockEntities + " block entities, " + totalItems() + " itens (" + this.items.size() + " tipos), "
                + this.fuelMb + " mB nos tanques, " + this.volatileBlockEntities + " fornalha(s) com " + this.volatileItems + " itens";
    }

    // ---------------------------------------------------------------- construção

    /** Acumula blocos e block entities (posição como long local, a mesma dos dois lados) e produz o romaneio. */
    public static final class Builder {
        private int blocks;
        private int blockEntities;
        private long stateHash;
        private long nbtHash;
        private final Map<String, Integer> perBlock = new TreeMap<>();
        private final Map<String, Integer> items = new TreeMap<>();
        private int volatileBlockEntities;
        private int volatileItems;
        private int fuelMb;
        private final Long2LongMap stateByPos = new Long2LongOpenHashMap();
        private final Long2LongMap nbtByPos = new Long2LongOpenHashMap();

        /** Um bloco que não é ar. {@code stateKey} identifica o estado completo (ex.: {@code BlockState.toString()}). */
        public void block(long pos, String blockId, String stateKey) {
            this.blocks++;
            this.perBlock.merge(blockId, 1, Integer::sum);
            long h = mix(pos ^ mix(hash64(stateKey)));
            this.stateHash += h;
            this.stateByPos.put(pos, h);
        }

        /** O NBT de um block entity (o salvo com os metadados, sem x/y/z). */
        public void blockEntity(long pos, CompoundTag nbt) {
            this.blockEntities++;
            this.fuelMb += nbt.getIntOr(FUEL_KEY, 0);
            boolean volatileBe = nbt.contains(VOLATILE_MARK);
            CompoundTag hashed = nbt;
            if (volatileBe) {
                this.volatileBlockEntities++;
                Map<String, Integer> inside = new TreeMap<>();
                countItems(nbt, inside, true);
                for (int n : inside.values()) {
                    this.volatileItems += n;
                }
                hashed = nbt.copy();
                for (String key : VOLATILE_KEYS) {
                    hashed.remove(key);
                }
            } else {
                countItems(nbt, this.items, true);
            }
            long h = mix(pos ^ mix(hashed.hashCode() * 0x9E3779B97F4A7C15L));
            this.nbtHash += h;
            this.nbtByPos.put(pos, h);
        }

        public ShipManifest build() {
            return new ShipManifest(this.blocks, this.blockEntities, this.stateHash, this.nbtHash, Map.copyOf(this.perBlock), Map.copyOf(this.items),
                    this.volatileBlockEntities, this.volatileItems, this.fuelMb);
        }

        /** Hashes por posição, para o relatório dizer ONDE (só existe em memória; o romaneio persistido não os guarda). */
        public Detail detail() {
            return new Detail(this.stateByPos, this.nbtByPos);
        }
    }

    /** Hash de cada posição: estado do bloco e NBT do block entity. */
    public record Detail(Long2LongMap states, Long2LongMap nbts) {
        /** As posições (locais) que diferem de {@code after}, no máximo {@code limit}, como texto "estado em x,y,z" / "NBT em x,y,z" / "ausente em ...". */
        public List<String> diffPositions(Detail after, int limit) {
            List<String> out = new ArrayList<>();
            for (Long2LongMap.Entry e : this.states.long2LongEntrySet()) {
                if (out.size() >= limit) {
                    return out;
                }
                if (!after.states.containsKey(e.getLongKey())) {
                    out.add("bloco ausente em " + pos(e.getLongKey()));
                } else if (after.states.get(e.getLongKey()) != e.getLongValue()) {
                    out.add("estado diferente em " + pos(e.getLongKey()));
                }
            }
            for (Long2LongMap.Entry e : after.states.long2LongEntrySet()) {
                if (out.size() >= limit) {
                    return out;
                }
                if (!this.states.containsKey(e.getLongKey())) {
                    out.add("bloco a mais em " + pos(e.getLongKey()));
                }
            }
            for (Long2LongMap.Entry e : this.nbts.long2LongEntrySet()) {
                if (out.size() >= limit) {
                    return out;
                }
                if (!after.nbts.containsKey(e.getLongKey())) {
                    out.add("block entity ausente em " + pos(e.getLongKey()));
                } else if (after.nbts.get(e.getLongKey()) != e.getLongValue()) {
                    out.add("NBT diferente em " + pos(e.getLongKey()));
                }
            }
            return out;
        }

        private static String pos(long packed) {
            net.minecraft.core.BlockPos p = net.minecraft.core.BlockPos.of(packed);
            return p.getX() + "," + p.getY() + "," + p.getZ();
        }
    }

    // ---------------------------------------------------------------- itens dentro de um NBT

    /**
     * Soma os itens encontrados em {@code tag} (recursivo: listas e compostos, inclusive itens dentro de itens) em {@code into}.
     * Um item é um composto com "id" (texto) cujos campos são só os de um ItemStack; {@code count} ausente vale 1. O composto da
     * própria block entity tem "id" mas também outros campos, então não conta como item (a menos que seja só id/count/Slot/components).
     */
    public static void countItems(Tag tag, Map<String, Integer> into, boolean top) {
        if (tag instanceof CompoundTag compound) {
            if (!top && compound.getString("id").isPresent() && ITEM_KEYS.containsAll(compound.keySet())) {
                int count = compound.getIntOr("count", 1);
                into.merge(compound.getStringOr("id", "?"), count, Integer::sum);
            }
            for (String key : compound.keySet()) {
                Tag child = compound.get(key);
                if (child != null) {
                    countItems(child, into, false);
                }
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) {
                countItems(child, into, false);
            }
        }
    }

    // ---------------------------------------------------------------- mistura

    /** Mistura de 64 bits (finalizador do splitmix64). */
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** FNV-1a de 64 bits do texto (estável entre execuções). */
    static long hash64(String s) {
        long h = 0xCBF29CE484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001B3L;
        }
        return h;
    }
}
