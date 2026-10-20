package com.spacehorizons.ship;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spacehorizons.SpaceHorizons;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jspecify.annotations.Nullable;

/**
 * O registro das naves do mundo (estado persistido, no Overworld como o resto do estado do mod): as naves ({@link ShipRecord}), o
 * alocador de células ({@link ShipYardAllocator}), o próximo id e, por jogador, de onde ele veio ao entrar numa nave (para
 * {@code ship leave}).
 */
public final class ShipRegistry extends SavedData {
    /** De onde o jogador veio ao entrar no estaleiro. */
    public record ReturnPoint(Identifier dimension, double x, double y, double z, float yRot, float xRot) {
        static final Codec<ReturnPoint> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("dimension").forGetter(ReturnPoint::dimension),
                Codec.DOUBLE.fieldOf("x").forGetter(ReturnPoint::x),
                Codec.DOUBLE.fieldOf("y").forGetter(ReturnPoint::y),
                Codec.DOUBLE.fieldOf("z").forGetter(ReturnPoint::z),
                Codec.FLOAT.fieldOf("yaw").forGetter(ReturnPoint::yRot),
                Codec.FLOAT.fieldOf("pitch").forGetter(ReturnPoint::xRot)
        ).apply(i, ReturnPoint::new));

        /** Guarda o ponto de um jogador que está em {@code dimension}. */
        public static ReturnPoint of(ResourceKey<Level> dimension, double x, double y, double z, float yRot, float xRot) {
            return new ReturnPoint(dimension.identifier(), x, y, z, yRot, xRot);
        }

        public ResourceKey<Level> levelKey() {
            return ResourceKey.create(Registries.DIMENSION, dimension);
        }
    }

    private record ReturnEntry(UUID player, ReturnPoint point) {
        static final Codec<ReturnEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(ReturnEntry::player),
                ReturnPoint.CODEC.fieldOf("point").forGetter(ReturnEntry::point)
        ).apply(i, ReturnEntry::new));
    }

    public static final Codec<ShipRegistry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("next_id").forGetter(r -> r.nextId),
            ShipYardAllocator.CODEC.fieldOf("allocator").forGetter(r -> r.allocator),
            ShipRecord.CODEC.listOf().fieldOf("ships").forGetter(r -> new ArrayList<>(r.ships.values())),
            ReturnEntry.CODEC.listOf().fieldOf("returns").forGetter(r -> r.returns.entrySet().stream()
                    .map(e -> new ReturnEntry(e.getKey(), e.getValue())).toList())
    ).apply(i, ShipRegistry::new));

    public static final SavedDataType<ShipRegistry> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "ships"), ShipRegistry::new, CODEC);

    private int nextId = 1;
    private final ShipYardAllocator allocator;
    private final Map<Integer, ShipRecord> ships = new TreeMap<>();
    private final Map<UUID, ReturnPoint> returns = new HashMap<>();
    /** Esta instância já começou a sessão (as células em quarentena foram liberadas)? Não persistido. */
    private boolean sessionStarted;

    public ShipRegistry() {
        this.allocator = new ShipYardAllocator();
    }

    private ShipRegistry(int nextId, ShipYardAllocator allocator, List<ShipRecord> ships, List<ReturnEntry> returns) {
        this.nextId = nextId;
        this.allocator = allocator;
        for (ShipRecord ship : ships) {
            this.ships.put(ship.id(), ship);
        }
        for (ReturnEntry entry : returns) {
            this.returns.put(entry.player(), entry.point());
        }
    }

    /** O registro do mundo (no Overworld). A primeira chamada de cada sessão libera as células em quarentena. */
    public static ShipRegistry get(MinecraftServer server) {
        ShipRegistry registry = server.overworld().getDataStorage().computeIfAbsent(TYPE);
        registry.beginSession();
        return registry;
    }

    /** Começa a sessão desta instância (uma vez): as células de naves destruídas na sessão anterior ficam reaproveitáveis. */
    void beginSession() {
        if (!sessionStarted) {
            sessionStarted = true;
            if (allocator.pendingCount() > 0) {
                allocator.startSession();
                setDirty();
            }
        }
    }

    /** Cria uma nave numa célula (a menor livre ou uma nova). Valida o tamanho. */
    public ShipRecord create(String name, int sx, int sy, int sz, @Nullable UUID owner, long gameTime) {
        ShipYard.checkSize(sx, sy, sz);
        int cell = allocator.allocate();
        ShipRecord ship = new ShipRecord(nextId++, cell, name, sx, sy, sz, Optional.ofNullable(owner), gameTime);
        ships.put(ship.id(), ship);
        setDirty();
        return ship;
    }

    /** Remove a nave do registro e põe a célula dela em quarentena (a limpeza dos blocos é de quem chama). */
    public boolean destroy(int id) {
        ShipRecord ship = ships.remove(id);
        if (ship == null) {
            return false;
        }
        allocator.release(ship.cell());
        setDirty();
        return true;
    }

    public @Nullable ShipRecord byId(int id) {
        return ships.get(id);
    }

    public Collection<ShipRecord> all() {
        return Collections.unmodifiableCollection(ships.values());
    }

    /** A nave cuja célula contém o ponto (x, z) do estaleiro, ou null. */
    public @Nullable ShipRecord atPosition(double x, double z) {
        for (ShipRecord ship : ships.values()) {
            if (ShipYard.cellContains(ship.cell(), x, z)) {
                return ship;
            }
        }
        return null;
    }

    public ShipYardAllocator allocator() {
        return allocator;
    }

    public void setReturn(UUID player, ReturnPoint point) {
        returns.put(player, point);
        setDirty();
    }

    public @Nullable ReturnPoint returnPoint(UUID player) {
        return returns.get(player);
    }

    public void clearReturn(UUID player) {
        if (returns.remove(player) != null) {
            setDirty();
        }
    }
}
