package com.spacehorizons.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** O romaneio da conversão: contagem de itens no NBT, hashes sem ordem, fornalhas voláteis, diferenças e ida e volta do codec. */
class ShipManifestTest {
    private static CompoundTag stack(String id, int count, int slot) {
        CompoundTag t = new CompoundTag();
        t.putByte("Slot", (byte) slot);
        t.putString("id", id);
        t.putInt("count", count);
        return t;
    }

    private static CompoundTag chest(int diamonds, int iron) {
        CompoundTag t = new CompoundTag();
        t.putString("id", "minecraft:chest");
        ListTag items = new ListTag();
        items.add(stack("minecraft:diamond", diamonds, 0));
        items.add(stack("minecraft:iron_ingot", iron, 1));
        t.put("Items", items);
        return t;
    }

    private static ShipManifest sample(boolean swap) {
        ShipManifest.Builder b = new ShipManifest.Builder();
        long p1 = BlockPos.asLong(1, 1, 1);
        long p2 = BlockPos.asLong(2, 1, 1);
        long p3 = BlockPos.asLong(3, 1, 1);
        if (swap) {
            b.block(p3, "minecraft:stone", "stone");
            b.block(p2, "minecraft:chest", "chest[facing=north]");
            b.block(p1, "minecraft:chest", "chest[facing=north]");
        } else {
            b.block(p1, "minecraft:chest", "chest[facing=north]");
            b.block(p2, "minecraft:chest", "chest[facing=north]");
            b.block(p3, "minecraft:stone", "stone");
        }
        b.blockEntity(p1, chest(64, 10));
        b.blockEntity(p2, chest(3, 5));
        return b.build();
    }

    @Test
    void contaItensDeTodosOsInventarios() {
        ShipManifest m = sample(false);
        assertEquals(3, m.blocks());
        assertEquals(2, m.blockEntities());
        assertEquals(67, m.items().get("minecraft:diamond"));
        assertEquals(15, m.items().get("minecraft:iron_ingot"));
        assertEquals(82, m.totalItems());
        assertEquals(2, m.perBlock().get("minecraft:chest"));
    }

    @Test
    void oBlockEntityEmSiNaoContaComoItem() {
        // o composto do chest tem "id" mas também "Items": não é um ItemStack
        ShipManifest m = sample(false);
        assertFalse(m.items().containsKey("minecraft:chest"));
    }

    @Test
    void itemSemCountVale1EContaDentroDeOutroItem() {
        CompoundTag shulker = stack("minecraft:shulker_box", 1, 0);
        CompoundTag components = new CompoundTag();
        ListTag inside = new ListTag();
        CompoundTag one = new CompoundTag();
        one.putString("id", "minecraft:apple");      // sem count: vale 1
        inside.add(one);
        inside.add(stack("minecraft:apple", 4, 1));
        components.put("minecraft:container", inside);
        shulker.put("components", components);
        CompoundTag be = new CompoundTag();
        be.putString("id", "minecraft:chest");
        ListTag items = new ListTag();
        items.add(shulker);
        be.put("Items", items);
        ShipManifest.Builder b = new ShipManifest.Builder();
        b.blockEntity(1L, be);
        ShipManifest m = b.build();
        assertEquals(1, m.items().get("minecraft:shulker_box"));
        assertEquals(5, m.items().get("minecraft:apple"));
    }

    @Test
    void hashNaoDependeDaOrdemDeInsercao() {
        assertEquals(sample(false), sample(true));
    }

    @Test
    void semDiferencaQuandoIgual() {
        assertTrue(sample(false).diff(sample(true)).isEmpty());
    }

    @Test
    void relataOQueSumiu() {
        ShipManifest before = sample(false);
        ShipManifest.Builder b = new ShipManifest.Builder();
        b.block(BlockPos.asLong(1, 1, 1), "minecraft:chest", "chest[facing=north]");
        b.block(BlockPos.asLong(3, 1, 1), "minecraft:stone", "stone");
        b.blockEntity(BlockPos.asLong(1, 1, 1), chest(64, 10));
        List<String> diff = before.diff(b.build());
        String all = String.join(" | ", diff);
        assertTrue(all.contains("blocos: 3 -> 2"), all);
        assertTrue(all.contains("bloco minecraft:chest: 2 -> 1 (perdeu 1)"), all);
        assertTrue(all.contains("block entities: 2 -> 1"), all);
        assertTrue(all.contains("item minecraft:diamond: 67 -> 64 (perdeu 3)"), all);
        assertTrue(all.contains("item minecraft:iron_ingot: 15 -> 10 (perdeu 5)"), all);
    }

    @Test
    void pegaBlocoNoLugarErradoMesmoComAsMesmasContagens() {
        ShipManifest.Builder a = new ShipManifest.Builder();
        a.block(BlockPos.asLong(1, 1, 1), "minecraft:stone", "stone");
        a.block(BlockPos.asLong(2, 1, 1), "minecraft:dirt", "dirt");
        ShipManifest.Builder b = new ShipManifest.Builder();
        b.block(BlockPos.asLong(2, 1, 1), "minecraft:stone", "stone");
        b.block(BlockPos.asLong(1, 1, 1), "minecraft:dirt", "dirt");
        List<String> diff = a.build().diff(b.build());
        assertEquals(1, diff.size(), diff.toString());
        assertTrue(diff.get(0).contains("posição"), diff.get(0));
        List<String> where = a.detail().diffPositions(b.detail(), 10);
        assertEquals(2, where.size(), where.toString());
        assertTrue(where.toString().contains("estado diferente em 1,1,1") && where.toString().contains("estado diferente em 2,1,1"), where.toString());
    }

    @Test
    void pegaNbtAlteradoENomeiaAPosicao() {
        ShipManifest.Builder a = new ShipManifest.Builder();
        a.block(BlockPos.asLong(1, 1, 1), "minecraft:chest", "chest");
        a.blockEntity(BlockPos.asLong(1, 1, 1), chest(64, 10));
        ShipManifest.Builder b = new ShipManifest.Builder();
        b.block(BlockPos.asLong(1, 1, 1), "minecraft:chest", "chest");
        CompoundTag changed = chest(64, 10);
        changed.putString("CustomName", "outro");
        b.blockEntity(BlockPos.asLong(1, 1, 1), changed);
        assertTrue(String.join("|", a.build().diff(b.build())).contains("NBT"), "o hash do NBT tem de pegar");
        assertTrue(a.detail().diffPositions(b.detail(), 10).get(0).contains("NBT diferente em 1,1,1"));
    }

    @Test
    void fornalhaNaoEntraNoHashNemNaContagemMasFicaSeparada() {
        CompoundTag furnace = new CompoundTag();
        furnace.putString("id", "minecraft:furnace");
        furnace.putShort("cooking_time_spent", (short) 12);
        furnace.putShort("lit_time_remaining", (short) 100);
        ListTag items = new ListTag();
        items.add(stack("minecraft:raw_iron", 64, 0));
        furnace.put("Items", items);
        CompoundTag later = furnace.copy();
        later.putShort("cooking_time_spent", (short) 140);
        later.put("Items", new ListTag());     // cozinhou tudo e o minério sumiu: não é perda
        ShipManifest.Builder a = new ShipManifest.Builder();
        a.blockEntity(1L, furnace);
        ShipManifest.Builder b = new ShipManifest.Builder();
        b.blockEntity(1L, later);
        ShipManifest ma = a.build();
        assertEquals(1, ma.volatileBlockEntities());
        assertEquals(64, ma.volatileItems());
        assertTrue(ma.items().isEmpty());
        assertTrue(ma.diff(b.build()).isEmpty(), ma.diff(b.build()).toString());
    }

    @Test
    void somaOCombustivelDosTanques() {
        ShipManifest.Builder b = new ShipManifest.Builder();
        CompoundTag t1 = new CompoundTag();
        t1.putString("id", "spacehorizons:rocket_fuel_tank");
        t1.putInt("fuel_mb", 1200);
        CompoundTag t2 = t1.copy();
        t2.putInt("fuel_mb", 800);
        b.blockEntity(1L, t1);
        b.blockEntity(2L, t2);
        assertEquals(2000, b.build().fuelMb());
    }

    @Test
    void codecDaIdaEVolta() {
        ShipManifest m = sample(false);
        var json = ShipManifest.CODEC.encodeStart(JsonOps.INSTANCE, m).getOrThrow();
        ShipManifest back = ShipManifest.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(m, back);
        assertEquals(Map.of("minecraft:diamond", 67, "minecraft:iron_ingot", 15), back.items());
    }
}
