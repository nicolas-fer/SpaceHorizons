package com.spacehorizons.ship;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.RocketBlockOrder;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketFuel;
import com.spacehorizons.rocket.RocketFuelTankBlockEntity;
import com.spacehorizons.rocket.RocketPortrait;
import com.spacehorizons.rocket.RocketSpecs;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Entidade → blocos no estaleiro (plano 3.2, spike S3), em lotes. Fases: carregar os chunks da caixa, colocar os blocos (sólidos antes dos
 * presos, com os block entities e o NBT, conferindo cada block entity assim que ele é colocado), avisar os vizinhos, conferir TUDO
 * contra o romaneio de entrada ({@link ShipManifest}) e, no fim, levar os passageiros para dentro e remover a entidade. A entidade
 * continua sendo a verdade (congelada) até o último passo: se algo falhar antes, a caixa é limpa e nada se perde.
 *
 * <p>O retrato entra sem rotação (a atitude do voo vai no {@link ShipFlightSnapshot}) com o canto mínimo da caixa da nave em
 * {@code box.min}; a caixa do registro é o retrato acrescido do mínimo de {@link ShipYard#MIN_SIZE}.
 */
final class RocketToYardJob extends ConversionJob {
    /** Colocação: clientes avisados, sem recalcular a forma dos vizinhos (os estados guardados já são os finais), como no pouso do foguete. */
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int COLUMN_COST = 64;

    private enum Phase {
        LOAD, PLACE, NOTIFY, VERIFY, FINISH
    }

    /** Um bloco a colocar: posição no estaleiro, posição local (long), estado e NBT do block entity (já com o combustível do tanque), ou null. */
    private record Item(BlockPos pos, long local, BlockState state, CompoundTag nbt) {
    }

    private final MinecraftServer server;
    private final RocketEntity rocket;
    private final UUID rocketUuid;
    private final ServerLevel yard;
    private final ShipRegistry registry;
    private final int shipId;
    private final ShipYard.Box box;
    private final RocketPortrait portrait;
    private final List<Item> items = new ArrayList<>();
    private final ShipManifest expected;
    private final ShipManifest.Detail expectedDetail;
    private final List<ChunkPos> columns = new ArrayList<>();
    private Phase phase = Phase.LOAD;
    private int cursor;
    private final ShipManifest.Builder actual = new ShipManifest.Builder();
    private int strictMismatches;

    RocketToYardJob(ServerLevel level, RocketEntity rocket) throws ShipConversionException {
        super("entidade -> blocos", 0.0);
        long t0 = System.nanoTime();
        this.server = level.getServer();
        this.rocket = rocket;
        this.rocketUuid = rocket.getUUID();
        this.yard = ShipYard.level(this.server);
        if (this.yard == null) {
            throw new ShipConversionException("Este mundo não tem a dimensão spacehorizons:shipyard (foi criado antes dela existir).");
        }
        this.portrait = rocket.portrait();
        if (this.portrait.isEmpty()) {
            throw new ShipConversionException("O foguete não tem blocos.");
        }
        this.registry = ShipRegistry.get(this.server);

        // os blocos a colocar: sólidos primeiro, presos depois; combustível repartido pelos tanques (como no pouso)
        List<BlockPos> tanks = this.portrait.tankPositions();
        int[] capacities = new int[tanks.size()];
        java.util.Arrays.fill(capacities, RocketSpecs.TANK_CAPACITY_MB);
        int[] shares = RocketFuel.distribute((int) Math.floor(rocket.fuelMb()), capacities);
        ShipManifest.Builder expectedBuilder = new ShipManifest.Builder();
        int psx = this.portrait.sizeX;
        int psy = this.portrait.sizeY;
        int psz = this.portrait.sizeZ;
        ShipYard.checkSize(Math.max(psx, ShipYard.MIN_SIZE), Math.max(psy, ShipYard.MIN_SIZE), Math.max(psz, ShipYard.MIN_SIZE));
        ShipRecord created = this.registry.create("Foguete " + this.rocketUuid.toString().substring(0, 8), Math.max(psx, ShipYard.MIN_SIZE),
                Math.max(psy, ShipYard.MIN_SIZE), Math.max(psz, ShipYard.MIN_SIZE), null, this.yard.getGameTime());
        this.shipId = created.id();
        this.box = created.box();
        List<Item> attached = new ArrayList<>();
        for (int y = 0; y < psy; y++) {
            for (int z = 0; z < psz; z++) {
                for (int x = 0; x < psx; x++) {
                    BlockState state = this.portrait.get(x, y, z);
                    if (state.isAir()) {
                        continue;
                    }
                    long local = BlockPos.asLong(x, y, z);
                    CompoundTag nbt = this.portrait.blockEntities().get(local);
                    int tankIndex = tanks.indexOf(new BlockPos(x, y, z));
                    if (tankIndex >= 0) {
                        nbt = nbt == null ? new CompoundTag() : nbt.copy();
                        nbt.putInt(RocketFuelTankBlockEntity.FUEL_KEY, shares[tankIndex]);
                    }
                    Item item = new Item(new BlockPos(this.box.minX() + x, this.box.minY() + y, this.box.minZ() + z), local, state, nbt);
                    (RocketBlockOrder.isAttached(state) ? attached : this.items).add(item);
                    ShipBlockKeys.addBlock(expectedBuilder, local, state);
                    if (nbt != null) {
                        expectedBuilder.blockEntity(local, nbt);
                    }
                }
            }
        }
        this.items.addAll(attached);
        this.expected = expectedBuilder.build();
        this.expectedDetail = expectedBuilder.detail();
        this.packetBytes = RocketPortrait.encodedSize(this.portrait);

        // as colunas de chunk que o retrato toca (a caixa do registro é maior)
        for (int cx = this.box.minX() >> 4; cx <= (this.box.minX() + psx - 1) >> 4; cx++) {
            for (int cz = this.box.minZ() >> 4; cz <= (this.box.minZ() + psz - 1) >> 4; cz++) {
                this.columns.add(new ChunkPos(cx, cz));
            }
        }

        // o snapshot do voo: o NBT da entidade sem o retrato (estado de voo, atitude, combustível...), de onde ela estava
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        rocket.saveWithoutId(output);
        CompoundTag rocketTag = output.buildResult();
        rocketTag.remove("Portrait");
        rocketTag.remove("Passengers");
        List<UUID> passengers = new ArrayList<>();
        for (Entity passenger : rocket.getPassengers()) {
            passengers.add(passenger.getUUID());
        }
        ShipFlightSnapshot snapshot = new ShipFlightSnapshot(level.dimension().identifier(), rocket.getX(), rocket.getY(), rocket.getZ(), rocketTag,
                new BlockPos(psx, psy, psz), this.portrait.cockpit(), this.expected, passengers);
        ShipRecord ship = created.withSnapshot(snapshot).withPhase(ShipRecord.Phase.FILLING);
        this.registry.update(ship);
        ShipChunkTickets.hold(this.yard, ship, this.yard.getGameTime());
        this.startupMs = (System.nanoTime() - t0) / 1.0e6;
        SpaceHorizons.LOGGER.info("[ship-conv] nave {}: {} blocos do foguete {} preparados em {} ms (caixa {}x{}x{}, {} colunas de chunk, pacote do retrato {} bytes): {}", this.shipId,
                this.items.size(), this.rocketUuid, String.format(java.util.Locale.ROOT, "%.1f", this.startupMs), this.box.sizeX(), this.box.sizeY(), this.box.sizeZ(),
                this.columns.size(), this.packetBytes, this.expected.summary());
    }

    UUID rocketUuid() {
        return this.rocketUuid;
    }

    @Override
    int shipId() {
        return this.shipId;
    }

    @Override
    int[] counts() {
        return new int[] {this.expected.blocks(), this.expected.blockEntities(), this.expected.totalItems()};
    }

    @Override
    boolean step(MinecraftServer server, ConversionBudget budget) {
        if (this.rocket.isRemoved()) {
            throw new IllegalStateException("o foguete foi removido durante a conversão");
        }
        while (this.phase != Phase.FINISH && !budget.exhausted()) {
            switch (this.phase) {
                case LOAD -> loadColumns(budget);
                case PLACE -> place(budget);
                case NOTIFY -> notifyNeighbors(budget);
                case VERIFY -> verify(budget);
                default -> {
                }
            }
        }
        if (this.phase != Phase.FINISH) {
            return false;
        }
        finish();
        return true;
    }

    private void loadColumns(ConversionBudget budget) {
        while (this.cursor < this.columns.size() && !budget.exhausted()) {
            ChunkPos c = this.columns.get(this.cursor++);
            this.yard.getChunk(c.x(), c.z());
            budget.spend(COLUMN_COST);
        }
        if (this.cursor >= this.columns.size()) {
            this.phase = Phase.PLACE;
            this.cursor = 0;
        }
    }

    private void place(ConversionBudget budget) {
        while (this.cursor < this.items.size() && !budget.exhausted()) {
            Item item = this.items.get(this.cursor++);
            this.yard.setBlock(item.pos(), item.state(), PLACE_FLAGS);
            if (item.nbt() != null) {
                BlockEntity loaded = BlockEntity.loadStatic(item.pos(), item.state(), item.nbt(), this.yard.registryAccess());
                if (loaded != null) {
                    this.yard.setBlockEntity(loaded);
                    // integridade estrita na hora (mesmo tick, antes de qualquer tick do jogo): o NBT salvo de volta é igual ao colocado?
                    CompoundTag back = ShipBlockKeys.nbtOf(loaded, this.yard.registryAccess());
                    if (!back.equals(item.nbt())) {
                        this.strictMismatches++;
                        if (this.strictMismatches <= 5) {
                            this.losses.add("NBT mudou ao carregar o block entity em " + item.pos().toShortString() + " (" + ShipBlockKeys.blockId(item.state())
                                    + "), campos: " + ShipBlockKeys.differingKeys(back, item.nbt()));
                        }
                    }
                } else {
                    this.losses.add("block entity não pôde ser criado em " + item.pos().toShortString() + " (" + ShipBlockKeys.blockId(item.state()) + ")");
                }
            }
            budget.spend(1);
        }
        if (this.cursor >= this.items.size()) {
            this.phase = Phase.NOTIFY;
            this.cursor = 0;
        }
    }

    private void notifyNeighbors(ConversionBudget budget) {
        while (this.cursor < this.items.size() && !budget.exhausted()) {
            Item item = this.items.get(this.cursor++);
            this.yard.updateNeighborsAt(item.pos(), item.state().getBlock());
            if (item.nbt() != null) {
                this.yard.sendBlockUpdated(item.pos(), item.state(), item.state(), Block.UPDATE_CLIENTS);
            }
            budget.spend(1);
        }
        if (this.cursor >= this.items.size()) {
            this.phase = Phase.VERIFY;
            this.cursor = 0;
        }
    }

    private void verify(ConversionBudget budget) {
        while (this.cursor < this.items.size() && !budget.exhausted()) {
            Item item = this.items.get(this.cursor++);
            BlockState state = this.yard.getBlockState(item.pos());
            if (!state.isAir()) {
                ShipBlockKeys.addBlock(this.actual, item.local(), state);
                if (state.hasBlockEntity() && this.yard.getBlockEntity(item.pos()) instanceof BlockEntity be) {
                    this.actual.blockEntity(item.local(), ShipBlockKeys.nbtOf(be, this.yard.registryAccess()));
                }
            }
            budget.spend(1);
        }
        if (this.cursor >= this.items.size()) {
            ShipManifest after = this.actual.build();
            this.losses.addAll(this.expected.diff(after));
            if (!this.losses.isEmpty()) {
                this.losses.addAll(this.expectedDetail.diffPositions(this.actual.detail(), 8));
            }
            if (this.expected.volatileBlockEntities() > 0) {
                this.notes.add(this.expected.volatileBlockEntities() + " fornalha(s) com " + this.expected.volatileItems() + " itens (fora do romaneio, integridade conferida na colocação)");
            }
            this.phase = Phase.FINISH;
        }
    }

    /** Última etapa, num tick só: passageiros para dentro da nave, a nave fica pronta e a entidade some. */
    private void finish() {
        ShipRecord ship = this.registry.byId(this.shipId);
        if (!this.losses.isEmpty()) {
            // não remove a entidade: ela continua sendo a verdade; a caixa é limpa
            this.error = "conversão com perdas, entidade mantida: " + this.losses.size() + " diferença(s)";
            abort(this.server, this.error);
            return;
        }
        this.registry.update(ship.withPhase(ShipRecord.Phase.READY));
        ShipRecord ready = this.registry.byId(this.shipId);
        Quaternionf attitude = new Quaternionf(this.rocket.attitude());
        Vector3f pivot = this.rocket.pivotLocal();
        Vec3 center = this.rocket.position();
        for (Entity passenger : List.copyOf(this.rocket.getPassengers())) {
            passenger.stopRiding();
            Vector3d local = ShipFrames.worldToLocal(attitude, center.x, center.y, center.z, pivot, passenger.getX(), passenger.getY(), passenger.getZ());
            BlockPos wanted = BlockPos.containing(this.box.minX() + local.x, this.box.minY() + local.y, this.box.minZ() + local.z);
            Vec3 at = ShipSpots.standNear(this.yard, wanted, 8);
            float[] view = ShipFrames.viewToLocal(attitude, passenger.getYRot(), passenger.getXRot());
            if (passenger instanceof ServerPlayer player) {
                ShipTransit.enterAt(player, ready, at, view[0], view[1]);
            } else {
                passenger.teleport(new TeleportTransition(this.yard, at, Vec3.ZERO, view[0], view[1], TeleportTransition.DO_NOTHING));
            }
            this.notes.add("passageiro " + passenger.getName().getString() + " levado para dentro da nave em " + at);
        }
        this.rocket.discard();
        SpaceHorizons.LOGGER.info("[ship-conv] nave {} pronta no estaleiro, foguete {} removido", this.shipId, this.rocketUuid);
    }

    /** Falhou ou teve perdas antes de remover a entidade: a caixa é limpa e a nave esquecida; a entidade segue intacta. */
    @Override
    void abort(MinecraftServer server, String reason) {
        SpaceHorizons.LOGGER.error("[ship-conv] conversão entidade -> blocos da nave {} cancelada: {}", this.shipId, reason);
        if (this.registry.byId(this.shipId) != null) {
            ShipConversions.enqueue(new ClearBoxJob(server, this.shipId, "cancelada: " + reason));
        }
        this.phase = Phase.FINISH;
    }

    /** Para o relatório: a conversão que foi cancelada deixa a entidade intacta. */
    Optional<String> failure() {
        return Optional.ofNullable(this.error);
    }
}
