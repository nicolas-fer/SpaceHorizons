package com.spacehorizons.ship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModEntities;
import com.spacehorizons.rocket.PortraitGrid;
import com.spacehorizons.rocket.RocketAssembly;
import com.spacehorizons.rocket.RocketBlockKind;
import com.spacehorizons.rocket.RocketBlockOrder;
import com.spacehorizons.rocket.RocketDismount;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketKinds;
import com.spacehorizons.rocket.RocketPortrait;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Blocos do estaleiro → entidade (plano 3.4, spike S3), em lotes. Fases: ler a caixa por seções (blocos e NBT dos block entities, nada
 * muda no mundo), montar o retrato e a entidade num tick só (conferindo contra o romaneio de entrada) e remover os blocos em lotes,
 * liberando a célula no fim. Os jogadores na nave voltam para o foguete sentados (o primeiro) ou ao lado dele.
 *
 * <p>A nave pode ter sido EDITADA no estaleiro (a verdade são os blocos): o retrato sai do que há na caixa agora, e as diferenças em
 * relação ao romaneio de entrada são relatadas (esperadas se alguém construiu ou quebrou algo, perdas se não).
 */
final class YardToRocketJob extends ConversionJob {
    private enum Phase {
        READ, BUILD, CLEAR, FINISH
    }

    /** Uma célula lida da caixa: posição no estaleiro, estado e NBT do block entity (ou null). */
    private record Cell(BlockPos pos, BlockState state, @Nullable CompoundTag nbt) {
    }

    /** Quanto tempo (ticks) esperamos o passageiro chegar à dimensão de destino para sentá-lo. */
    private static final int SEAT_TIMEOUT = 100;

    private final MinecraftServer server;
    private final ShipRegistry registry;
    private final ServerLevel yard;
    private final int shipId;
    private final ShipYard.Box box;
    private final ShipFlightSnapshot snapshot;
    private final List<BoxSections.Section> sections;
    private final List<Cell> cells = new ArrayList<>();
    private Phase phase = Phase.READ;
    private int cursor;
    private ShipManifest after = ShipManifest.EMPTY;
    private List<Cell> clearOrder = List.of();
    private boolean entityAdded;
    private final List<Entity> pendingSeat = new ArrayList<>();
    private RocketEntity rocket;
    private int seatTicks;

    YardToRocketJob(MinecraftServer server, int shipId) throws ShipConversionException {
        super("blocos -> entidade", 0.0);
        long t0 = System.nanoTime();
        this.server = server;
        this.registry = ShipRegistry.get(server);
        this.yard = ShipYard.level(server);
        this.shipId = shipId;
        if (this.yard == null) {
            throw new ShipConversionException("Este mundo não tem a dimensão spacehorizons:shipyard.");
        }
        ShipRecord ship = this.registry.byId(shipId);
        if (ship == null) {
            throw new ShipConversionException("Não existe a nave " + shipId + " (veja /spacehorizons ship list).");
        }
        if (ship.snapshot().isEmpty()) {
            throw new ShipConversionException("A nave " + shipId + " não veio de um foguete (é uma nave de teste): não há estado de voo para devolver.");
        }
        if (ship.phase() != ShipRecord.Phase.READY) {
            throw new ShipConversionException("A nave " + shipId + " está numa conversão (" + ship.phase() + ").");
        }
        this.snapshot = ship.snapshot().get();
        this.box = ship.box();
        this.sections = BoxSections.of(this.box);
        this.registry.update(ship.withPhase(ShipRecord.Phase.EMPTYING));
        ShipChunkTickets.hold(this.yard, ship, this.yard.getGameTime());
        this.startupMs = (System.nanoTime() - t0) / 1.0e6;
        SpaceHorizons.LOGGER.info("[ship-conv] nave {}: lendo a caixa {}x{}x{} ({} seções) para devolver ao foguete", shipId, this.box.sizeX(), this.box.sizeY(),
                this.box.sizeZ(), this.sections.size());
    }

    @Override
    int shipId() {
        return this.shipId;
    }

    @Override
    int[] counts() {
        return new int[] {this.after.blocks(), this.after.blockEntities(), this.after.totalItems()};
    }

    @Override
    boolean step(MinecraftServer server, ConversionBudget budget) {
        trySeat();
        while (this.phase != Phase.FINISH && !budget.exhausted()) {
            switch (this.phase) {
                case READ -> read(budget);
                case BUILD -> build();
                case CLEAR -> clear(budget);
                default -> {
                }
            }
        }
        if (this.phase != Phase.FINISH) {
            return false;
        }
        if (this.error != null) {
            return true;    // recusada ou falhou antes de remover qualquer bloco: a nave continua no estaleiro
        }
        if (!this.pendingSeat.isEmpty() && this.seatTicks < SEAT_TIMEOUT) {
            return false;   // espera o passageiro chegar para sentá-lo
        }
        finish();
        return true;
    }

    // ---------------------------------------------------------------- leitura

    private void read(ConversionBudget budget) {
        while (this.cursor < this.sections.size() && !budget.exhausted()) {
            BoxSections.Section s = this.sections.get(this.cursor++);
            LevelChunk chunk = this.yard.getChunk(s.cx(), s.cz());
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(s.sy() << 4));
            if (section.hasOnlyAir()) {
                budget.spend(1);
                continue;
            }
            budget.spend(BoxSections.SECTION_COST);
            int[] c = BoxSections.clip(this.box, s);
            for (int y = c[2]; y <= c[3]; y++) {
                for (int z = c[4]; z <= c[5]; z++) {
                    for (int x = c[0]; x <= c[1]; x++) {
                        BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                        if (state.isAir()) {
                            continue;
                        }
                        BlockPos pos = new BlockPos(x, y, z);
                        CompoundTag nbt = null;
                        if (state.hasBlockEntity() && chunk.getBlockEntity(pos) instanceof BlockEntity be) {
                            nbt = ShipBlockKeys.nbtOf(be, this.yard.registryAccess());
                        }
                        this.cells.add(new Cell(pos, state, nbt));
                    }
                }
            }
        }
        if (this.cursor >= this.sections.size()) {
            this.phase = Phase.BUILD;
        }
    }

    // ---------------------------------------------------------------- retrato e entidade

    private void build() {
        if (this.cells.isEmpty()) {
            fail("a caixa da nave está vazia");
            return;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Cell cell : this.cells) {
            minX = Math.min(minX, cell.pos().getX());
            minY = Math.min(minY, cell.pos().getY());
            minZ = Math.min(minZ, cell.pos().getZ());
            maxX = Math.max(maxX, cell.pos().getX());
            maxY = Math.max(maxY, cell.pos().getY());
            maxZ = Math.max(maxZ, cell.pos().getZ());
        }
        int sx = maxX - minX + 1;
        int sy = maxY - minY + 1;
        int sz = maxZ - minZ + 1;
        if (sx > RocketAssembly.MAX_EXTENT || sy > RocketAssembly.MAX_EXTENT || sz > RocketAssembly.MAX_EXTENT || (long) sx * sy * sz > PortraitGrid.MAX_VOLUME) {
            fail("a nave (" + sx + "x" + sy + "x" + sz + ") é maior do que o retrato do foguete suporta (" + RocketAssembly.MAX_EXTENT + " de lado)");
            return;
        }
        PortraitGrid grid = PortraitGrid.empty(sx, sy, sz);
        List<BlockState> palette = new ArrayList<>();
        palette.add(Blocks.AIR.defaultBlockState());
        Map<BlockState, Integer> index = new HashMap<>();
        index.put(palette.get(0), 0);
        Long2ObjectMap<CompoundTag> blockEntities = new Long2ObjectLinkedOpenHashMap<>();
        ShipManifest.Builder builder = new ShipManifest.Builder();
        BlockPos cockpit = null;
        int fuel = 0;
        for (Cell cell : this.cells) {
            int idx = index.computeIfAbsent(cell.state(), st -> {
                palette.add(st);
                return palette.size() - 1;
            });
            int lx = cell.pos().getX() - minX;
            int ly = cell.pos().getY() - minY;
            int lz = cell.pos().getZ() - minZ;
            grid.set(lx, ly, lz, idx);
            // o romaneio usa o referencial da caixa da nave (o mesmo da entrada), não o do retrato apertado
            long rel = BlockPos.asLong(cell.pos().getX() - this.box.minX(), cell.pos().getY() - this.box.minY(), cell.pos().getZ() - this.box.minZ());
            ShipBlockKeys.addBlock(builder, rel, cell.state());
            if (cell.nbt() != null) {
                blockEntities.put(BlockPos.asLong(lx, ly, lz), cell.nbt());
                builder.blockEntity(rel, cell.nbt());
                fuel += cell.nbt().getIntOr(ShipManifest.FUEL_KEY, 0);
            }
            if (cockpit == null && RocketKinds.partKind(cell.state()) == RocketBlockKind.COCKPIT) {
                cockpit = new BlockPos(lx, ly, lz);
            }
        }
        if (cockpit == null) {
            // sem cockpit na caixa (quebrado por alguém): o do snapshot, se ainda dentro, ou a origem
            BlockPos old = this.snapshot.cockpit().offset(this.box.minX() - minX, this.box.minY() - minY, this.box.minZ() - minZ);
            cockpit = grid.inside(old.getX(), old.getY(), old.getZ()) ? old : BlockPos.ZERO;
            this.notes.add("sem bloco de cockpit na nave: usada a posição do snapshot");
        }
        RocketPortrait portrait = new RocketPortrait(UUID.randomUUID(), grid, palette, blockEntities, cockpit);
        this.packetBytes = RocketPortrait.encodedSize(portrait);
        this.after = builder.build();
        this.losses.addAll(this.snapshot.manifest().diff(this.after));
        if (this.snapshot.manifest().volatileBlockEntities() > 0) {
            this.notes.add(this.after.volatileBlockEntities() + " fornalha(s): itens " + this.snapshot.manifest().volatileItems() + " -> " + this.after.volatileItems()
                    + " (informativo: cozinham no estaleiro)");
        }

        // a entidade, no lugar de onde veio
        ResourceKey<Level> destKey = ResourceKey.create(Registries.DIMENSION, this.snapshot.dimension());
        ServerLevel dest = this.server.getLevel(destKey);
        if (dest == null) {
            dest = this.server.overworld();
            this.notes.add("dimensão de origem " + this.snapshot.dimension() + " indisponível: foguete criado no overworld");
        }
        RocketEntity entity = ModEntities.ROCKET.get().create(dest, EntitySpawnReason.LOAD);
        if (entity == null) {
            fail("não foi possível criar a entidade do foguete");
            return;
        }
        entity.setPortrait(portrait);
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, dest.registryAccess(), this.snapshot.rocket()));
        entity.setFuelMb(fuel);
        entity.setPos(this.snapshot.x(), this.snapshot.y(), this.snapshot.z());
        if (dest.getEntity(entity.getUUID()) != null) {
            entity.setUUID(UUID.randomUUID());
            this.notes.add("o UUID do foguete já existe no destino: gerado outro");
        }
        dest.getChunk(BlockPos.containing(this.snapshot.center()));
        ShipRecord ship = this.registry.byId(this.shipId);
        this.registry.update(ship.withPhase(ShipRecord.Phase.REMOVING));
        if (!dest.addFreshEntity(entity)) {
            this.registry.update(this.registry.byId(this.shipId).withPhase(ShipRecord.Phase.READY));
            fail("não foi possível adicionar a entidade do foguete ao mundo");
            return;
        }
        this.entityAdded = true;
        this.rocket = entity;

        movePeople(dest, entity);

        // ordem da remoção: presos primeiro (nada solta quando o apoio some)
        List<Cell> order = new ArrayList<>(this.cells.size());
        for (boolean attachedPass : new boolean[] {true, false}) {
            for (Cell cell : this.cells) {
                if (RocketBlockOrder.isAttached(cell.state()) == attachedPass) {
                    order.add(cell);
                }
            }
        }
        this.clearOrder = order;
        this.cursor = 0;
        this.phase = Phase.CLEAR;
        SpaceHorizons.LOGGER.info("[ship-conv] nave {}: foguete recriado em {} {} ({} blocos, pacote do retrato {} bytes), removendo os blocos do estaleiro", this.shipId,
                dest.dimension().identifier(), this.snapshot.center(), portrait.blockCount(), this.packetBytes);
    }

    /** Leva quem está na nave para o foguete: o piloto (quem estava a bordo, ou o primeiro jogador) senta, os outros ficam ao lado dele. */
    private void movePeople(ServerLevel dest, RocketEntity entity) {
        List<Entity> candidates = new ArrayList<>();
        for (UUID id : this.snapshot.passengers()) {
            Entity e = this.yard.getEntity(id);
            if (e != null && this.box.contains(e.getX(), e.getY(), e.getZ())) {
                candidates.add(e);
            }
        }
        for (ServerPlayer player : this.yard.players()) {
            if (ShipYard.cellContains(this.registry.byId(this.shipId).cell(), player.getX(), player.getZ()) && !candidates.contains(player)) {
                candidates.add(player);
            }
        }
        Quaternionf attitude = new Quaternionf(entity.attitude());
        Vector3f pivot = entity.pivotLocal();
        Vec3 center = entity.position();
        boolean seated = false;
        for (Entity person : candidates) {
            float[] view = ShipFrames.viewToWorld(attitude, person.getYRot(), person.getXRot());
            Vec3 target;
            boolean pilot = !seated && entity.getPassengers().isEmpty();
            if (pilot) {
                target = entity.passengerPositionAt(center, attitude, person);
                seated = true;
            } else {
                Vec3 spot = RocketDismount.find(dest, entity.getBoundingBox(), person.getType());
                if (spot == null) {
                    // sem chão livre em volta (foguete no ar): a mesma posição relativa à nave, no foguete
                    Vector3d world = ShipFrames.localToWorld(attitude, center.x, center.y, center.z, pivot, person.getX() - this.box.minX(),
                            person.getY() - this.box.minY(), person.getZ() - this.box.minZ());
                    spot = new Vec3(world.x, world.y, world.z);
                }
                target = spot;
            }
            Entity moved = person instanceof ServerPlayer player ? teleportPlayer(player, dest, target, view) : person.teleport(
                    new TeleportTransition(dest, target, Vec3.ZERO, view[0], view[1], TeleportTransition.DO_NOTHING));
            if (moved == null) {
                this.notes.add("não foi possível mover " + person.getName().getString() + " para o foguete");
                continue;
            }
            if (pilot) {
                this.pendingSeat.add(moved);
            }
            this.notes.add(person.getName().getString() + (pilot ? " sentado no foguete" : " ao lado do foguete"));
        }
    }

    private static Entity teleportPlayer(ServerPlayer player, ServerLevel dest, Vec3 target, float[] view) {
        player.teleport(new TeleportTransition(dest, target, Vec3.ZERO, view[0], view[1], TeleportTransition.DO_NOTHING));
        return player;
    }

    /** Senta quem foi teleportado como piloto assim que ele chega à dimensão do foguete. */
    private void trySeat() {
        if (this.pendingSeat.isEmpty() || this.rocket == null) {
            return;
        }
        this.seatTicks++;
        for (Entity e : List.copyOf(this.pendingSeat)) {
            if (e.isRemoved()) {
                this.pendingSeat.remove(e);
            } else if (e.level() == this.rocket.level()) {
                if (e.startRiding(this.rocket)) {
                    this.pendingSeat.remove(e);
                    SpaceHorizons.LOGGER.info("[ship-conv] {} sentado no foguete em {} ticks", e.getName().getString(), this.seatTicks);
                }
            }
        }
        if (!this.pendingSeat.isEmpty() && this.seatTicks >= SEAT_TIMEOUT) {
            this.notes.add("o piloto não pôde ser sentado em " + SEAT_TIMEOUT + " ticks");
        }
    }

    // ---------------------------------------------------------------- remoção dos blocos

    private void clear(ConversionBudget budget) {
        while (this.cursor < this.clearOrder.size() && !budget.exhausted()) {
            Cell cell = this.clearOrder.get(this.cursor++);
            BoxSections.clearAt(this.yard, cell.pos());
            budget.spend(1);
        }
        if (this.cursor >= this.clearOrder.size()) {
            this.phase = Phase.FINISH;
        }
    }

    private void finish() {
        ShipRecord ship = this.registry.byId(this.shipId);
        if (ship != null) {
            ShipChunkTickets.release(this.yard, ship);
            this.registry.destroy(this.shipId);
        }
        SpaceHorizons.LOGGER.info("[ship-conv] nave {} devolvida ao foguete; célula liberada", this.shipId);
    }

    private void fail(String reason) {
        this.error = reason;
        ShipRecord ship = this.registry.byId(this.shipId);
        if (ship != null && !this.entityAdded) {
            this.registry.update(ship.withPhase(ShipRecord.Phase.READY));
        }
        this.phase = Phase.FINISH;
        this.pendingSeat.clear();
        SpaceHorizons.LOGGER.error("[ship-conv] conversão blocos -> entidade da nave {} cancelada: {}", this.shipId, reason);
    }

    @Override
    void abort(MinecraftServer server, String reason) {
        // antes da entidade existir: a nave volta a READY; depois, o fechamento do mundo cairia na recuperação (REMOVING limpa a caixa)
        ShipRecord ship = this.registry.byId(this.shipId);
        if (ship != null && !this.entityAdded) {
            this.registry.update(ship.withPhase(ShipRecord.Phase.READY));
        }
        this.pendingSeat.clear();
        this.phase = Phase.FINISH;
    }
}
