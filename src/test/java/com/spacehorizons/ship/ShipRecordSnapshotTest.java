package com.spacehorizons.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;

/** O snapshot do voo no registro da nave (S3): ida e volta pelo NBT e pelo JSON, fase persistida e naves antigas (sem snapshot) ainda lidas. */
class ShipRecordSnapshotTest {
    private static ShipFlightSnapshot snapshot() {
        CompoundTag rocket = new CompoundTag();
        rocket.putDouble("FuelMb", 1234.75);
        rocket.putByte("FlightMode", (byte) 2);
        rocket.putFloat("Throttle", 0.5F);
        CompoundTag flight = new CompoundTag();
        flight.putString("Body", "earth");
        flight.putDouble("Qw", 1.0);
        rocket.put("Flight", flight);
        ShipManifest manifest = new ShipManifest(4003, 61, 12345L, -678L, Map.of("minecraft:chest", 40), Map.of("minecraft:diamond", 2560), 1, 76, 8000);
        return new ShipFlightSnapshot(Identifier.withDefaultNamespace("overworld"), 10.5, 200.0, -30.25, rocket, new BlockPos(26, 28, 26), new BlockPos(13, 1, 13),
                manifest, List.of(UUID.randomUUID()));
    }

    @Test
    void snapshotIdaEVoltaPeloNbt() {
        ShipFlightSnapshot s = snapshot();
        var encoded = ShipFlightSnapshot.CODEC.encodeStart(NbtOps.INSTANCE, s).getOrThrow();
        ShipFlightSnapshot back = ShipFlightSnapshot.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals(s, back);
        assertEquals(1234.75, back.rocket().getDoubleOr("FuelMb", 0.0));
    }

    @Test
    void naveComSnapshotEFaseIdaEVolta() {
        ShipRecord r = new ShipRecord(7, 3, "Foguete teste", 26, 28, 26, Optional.empty(), 99L).withSnapshot(snapshot()).withPhase(ShipRecord.Phase.REMOVING);
        var encoded = ShipRecord.CODEC.encodeStart(NbtOps.INSTANCE, r).getOrThrow();
        ShipRecord back = ShipRecord.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals(r, back);
        assertEquals(ShipRecord.Phase.REMOVING, back.phase());
        assertTrue(back.snapshot().isPresent());
    }

    @Test
    void naveAntigaSemCamposNovosLeComoPronta() {
        // registro do S2: sem "phase" nem "snapshot"
        ShipRecord old = new ShipRecord(1, 0, "A", 64, 64, 64, Optional.empty(), 5L);
        var json = ShipRecord.CODEC.encodeStart(JsonOps.INSTANCE, old).getOrThrow().getAsJsonObject();
        json.remove("phase");
        json.remove("snapshot");
        ShipRecord back = ShipRecord.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(ShipRecord.Phase.READY, back.phase());
        assertTrue(back.snapshot().isEmpty());
        assertEquals(old, back);
    }

    @Test
    void faseDesconhecidaVoltaAPronta() {
        ShipRecord old = new ShipRecord(1, 0, "A", 64, 64, 64, Optional.empty(), 5L);
        var json = ShipRecord.CODEC.encodeStart(JsonOps.INSTANCE, old).getOrThrow().getAsJsonObject();
        json.addProperty("phase", "DEPOIS_DA_ATUALIZACAO");
        assertEquals(ShipRecord.Phase.READY, ShipRecord.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().phase());
    }

    @Test
    void registroAtualizaAFaseEMarcaSujo() {
        ShipRegistry registry = new ShipRegistry();
        ShipRecord ship = registry.create("A", 32, 32, 32, null, 0L);
        registry.update(ship.withPhase(ShipRecord.Phase.FILLING));
        assertEquals(ShipRecord.Phase.FILLING, registry.byId(ship.id()).phase());
        assertTrue(registry.isDirty());
        // atualizar uma nave que não existe não a cria
        registry.update(new ShipRecord(99, 5, "fantasma", 32, 32, 32, Optional.empty(), 0L));
        assertEquals(1, registry.all().size());
    }
}
