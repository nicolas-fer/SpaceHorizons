package com.spacehorizons.ship;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.EngineChargeBlockEntity;
import com.spacehorizons.rocket.RocketBlockOrder;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketException;
import com.spacehorizons.rocket.RocketFuelTankBlockEntity;
import com.spacehorizons.rocket.RocketLanding;
import com.spacehorizons.rocket.RocketPortrait;
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.ship.block.AntigravityGeneratorBlock;
import com.spacehorizons.ship.deck.DeckCrew;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Âncora no ar (spike S6): a nave parada vira blocos reais no lugar onde a entidade está, pelo caminho do pouso ({@link RocketLanding#plan}: o
 * retrato girado ao quarto de volta, o combustível repartido pelos tanques e a carga pelos motores), só que em lotes como a conversão do estaleiro
 * ({@link RocketToYardJob}): colocar (sólidos antes dos presos, com o NBT de cada block entity conferido na hora), avisar os vizinhos, conferir tudo
 * contra o romaneio ({@link ShipManifest}) e, num tick só, tirar a tripulação do convés na mesma posição do mundo, pôr os passageiros em pé perto da
 * ponte, remover a entidade e registrar a âncora ({@link ShipAnchors}). A entidade (congelada) segue sendo a verdade até o último passo: com perda
 * ou erro, os blocos colocados são retirados e ela fica.
 */
final class AnchorJob extends ConversionJob {
    /** Colocação: clientes avisados, sem recalcular a forma dos vizinhos (os estados guardados já são os finais), como no pouso. */
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Desfazer: sem soltar itens nem avisar os vizinhos no meio. */
    private static final int REMOVE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    /** Avisar e conferir custam uma fração da colocação (quatro blocos por unidade do orçamento). */
    private static final int CHEAP_PER_UNIT = 4;

    private enum Phase {
        PLACE, NOTIFY, VERIFY, FINISH
    }

    /** Um bloco a colocar: posição no mundo, posição local girada (long), estado e o NBT final (já com o combustível do tanque ou a carga do motor). */
    private record Item(BlockPos pos, long local, BlockState state, CompoundTag nbt) {
    }

    private final ServerLevel level;
    private final RocketEntity rocket;
    private final UUID rocketUuid;
    private final int quarterTurns;
    private final BlockPos origin;
    private final RocketPortrait rotated;
    private final List<Item> items = new ArrayList<>();
    private final ShipManifest expected;
    private final ShipManifest.Detail expectedDetail;
    private final ShipManifest.Builder actual = new ShipManifest.Builder();
    private Phase phase = Phase.PLACE;
    private int cursor;
    private int placed;
    private int cheap;
    private int strictMismatches;
    private int anchorId = -1;

    AnchorJob(ServerLevel level, RocketEntity rocket, int quarterTurns, BlockPos origin) throws ShipConversionException {
        super("entidade -> blocos no ar (âncora)", 0.0);
        long t0 = System.nanoTime();
        this.level = level;
        this.rocket = rocket;
        this.rocketUuid = rocket.getUUID();
        this.quarterTurns = quarterTurns;
        this.origin = origin;
        RocketPortrait portrait = rocket.portrait();
        if (portrait.isEmpty()) {
            throw new ShipConversionException("A nave não tem blocos.");
        }
        this.rotated = portrait.rotated(quarterTurns);
        ShipAnchors.preCheck(level, this.rotated, origin);
        List<RocketLanding.Placement> plan;
        try {
            plan = RocketLanding.plan(level, this.rotated, origin, rocket.fuel().floor());
        } catch (RocketException e) {
            throw new ShipConversionException(e.component().getString());
        }
        ShipManifest.Builder expectedBuilder = new ShipManifest.Builder();
        List<Item> attached = new ArrayList<>();
        for (RocketLanding.Placement p : plan) {
            CompoundTag nbt = p.nbt();
            BlockState placedState = p.state();
            if (placedState.is(ModBlocks.ANTIGRAVITY_GENERATOR.get()) && placedState.getValue(AntigravityGeneratorBlock.CHARGED)) {
                // ancorar gasta a carga de todos os geradores da nave (decisão A3: energia, não combustível); eles se recarregam com o bloco no mundo
                placedState = placedState.setValue(AntigravityGeneratorBlock.CHARGED, false);
            }
            if (p.tank()) {
                nbt = nbt == null ? new CompoundTag() : nbt.copy();
                nbt.putInt(RocketFuelTankBlockEntity.FUEL_KEY, p.fuelMb());
            } else if (p.charge()) {
                nbt = nbt == null ? new CompoundTag() : nbt.copy();
                nbt.putInt(EngineChargeBlockEntity.CHARGE_KEY, p.fuelMb());
            }
            long local = BlockPos.asLong(p.pos().getX() - origin.getX(), p.pos().getY() - origin.getY(), p.pos().getZ() - origin.getZ());
            Item item = new Item(p.pos(), local, placedState, nbt);
            (RocketBlockOrder.isAttached(placedState) ? attached : this.items).add(item);
            ShipBlockKeys.addBlock(expectedBuilder, local, placedState);
            if (nbt != null) {
                expectedBuilder.blockEntity(local, nbt);
            }
        }
        this.items.addAll(attached);
        this.expected = expectedBuilder.build();
        this.expectedDetail = expectedBuilder.detail();
        this.packetBytes = RocketPortrait.encodedSize(portrait);
        this.startupMs = (System.nanoTime() - t0) / 1.0e6;
        SpaceHorizons.LOGGER.info("[anchor] {} blocos da nave {} preparados em {} ms (canto {}, {} quarto(s) de volta, caixa {}x{}x{}): {}", this.items.size(),
                this.rocketUuid, String.format(java.util.Locale.ROOT, "%.1f", this.startupMs), origin.toShortString(), quarterTurns, this.rotated.sizeX,
                this.rotated.sizeY, this.rotated.sizeZ, this.expected.summary());
    }

    UUID rocketUuid() {
        return this.rocketUuid;
    }

    @Override
    int shipId() {
        return this.anchorId;
    }

    @Override
    int[] counts() {
        return new int[] {this.expected.blocks(), this.expected.blockEntities(), this.expected.totalItems()};
    }

    @Override
    boolean step(MinecraftServer server, ConversionBudget budget) {
        if (this.rocket.isRemoved()) {
            throw new IllegalStateException("a nave foi removida durante a âncora");
        }
        while (this.phase != Phase.FINISH && !budget.exhausted()) {
            switch (this.phase) {
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
        if (this.error == null) {
            finish();
        }
        return true;
    }

    private void spendCheap(ConversionBudget budget) {
        if (++this.cheap % CHEAP_PER_UNIT == 0) {
            budget.spend(1);
        }
    }

    private void place(ConversionBudget budget) {
        while (this.cursor < this.items.size() && !budget.exhausted()) {
            Item item = this.items.get(this.cursor++);
            BlockState existing = this.level.getBlockState(item.pos());
            if (!existing.isAir()) {
                this.notes.add("substituiu " + ShipBlockKeys.blockId(existing) + " em " + item.pos().toShortString());
            }
            this.level.setBlock(item.pos(), item.state(), PLACE_FLAGS);
            this.placed = this.cursor;
            if (item.nbt() != null) {
                BlockEntity loaded = BlockEntity.loadStatic(item.pos(), item.state(), item.nbt(), this.level.registryAccess());
                if (loaded != null) {
                    this.level.setBlockEntity(loaded);
                    // integridade estrita na hora (mesmo tick, antes de qualquer tick do jogo): o NBT salvo de volta é igual ao colocado?
                    CompoundTag back = ShipBlockKeys.nbtOf(loaded, this.level.registryAccess());
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
            this.level.updateNeighborsAt(item.pos(), item.state().getBlock());
            if (item.nbt() != null) {
                this.level.sendBlockUpdated(item.pos(), item.state(), item.state(), Block.UPDATE_CLIENTS);
            }
            spendCheap(budget);
        }
        if (this.cursor >= this.items.size()) {
            this.phase = Phase.VERIFY;
            this.cursor = 0;
        }
    }

    private void verify(ConversionBudget budget) {
        while (this.cursor < this.items.size() && !budget.exhausted()) {
            Item item = this.items.get(this.cursor++);
            BlockState state = this.level.getBlockState(item.pos());
            if (!state.isAir()) {
                ShipBlockKeys.addBlock(this.actual, item.local(), state);
                if (state.hasBlockEntity() && this.level.getBlockEntity(item.pos()) instanceof BlockEntity be) {
                    this.actual.blockEntity(item.local(), ShipBlockKeys.nbtOf(be, this.level.registryAccess()));
                }
            }
            spendCheap(budget);
        }
        if (this.cursor >= this.items.size()) {
            ShipManifest after = this.actual.build();
            this.losses.addAll(this.expected.diff(after));
            if (!this.losses.isEmpty()) {
                this.losses.addAll(this.expectedDetail.diffPositions(this.actual.detail(), 8));
            }
            if (this.expected.volatileBlockEntities() > 0) {
                this.notes.add(this.expected.volatileBlockEntities() + " fornalha(s) com " + this.expected.volatileItems()
                        + " itens (fora do romaneio, integridade conferida na colocação)");
            }
            this.phase = Phase.FINISH;
        }
    }

    /** Último passo, num tick só: a tripulação desce do convés para os blocos, os passageiros ficam em pé, a entidade some e a âncora é registrada. */
    private void finish() {
        if (!this.losses.isEmpty()) {
            this.error = "âncora com perdas, entidade mantida: " + this.losses.size() + " diferença(s)";
            abort(this.level.getServer(), this.error);
            return;
        }
        // a pose da entidade agora é a da grade (a acomodação a pôs lá): sair do convés leva cada um ao mesmo ponto do mundo, em cima dos blocos
        int crew = 0;
        for (ServerPlayer player : List.copyOf(this.level.players())) {
            DeckCrew.Member m = DeckCrew.member(player.getUUID());
            if (m != null && m.rocketUuid().equals(this.rocketUuid)) {
                DeckCrew.leave(player, "a nave ancorou no ar");
                player.sendSystemMessage(Component.literal("Nave ancorada no ar: " + this.expected.blocks() + " blocos."));
                crew++;
            }
        }
        // sentados (o piloto): em pé no chão livre mais perto da ponte, com a vista relativa à nave
        BlockPos cockpit = this.origin.offset(this.rotated.cockpit());
        for (Entity passenger : List.copyOf(this.rocket.getPassengers())) {
            passenger.stopRiding();
            Vec3 at = ShipSpots.standNear(this.level, cockpit, 8);
            passenger.teleportTo(this.level, at.x, at.y, at.z, java.util.Set.of(), passenger.getYRot(), passenger.getXRot(), true);
            if (passenger instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.literal("Nave ancorada no ar: " + this.expected.blocks() + " blocos."));
                PacketDistributor.sendToPlayer(player, new AnchorStatusPayload(AnchorStatus.ANCHORED, List.of(), false));
            }
            this.notes.add("passageiro " + passenger.getName().getString() + " em pé em " + at);
        }
        this.rocket.discard();
        ShipAnchors.Anchor anchor = ShipAnchors.get(this.level.getServer()).add(this.level, this.origin, this.rotated.sizeX, this.rotated.sizeY, this.rotated.sizeZ,
                cockpit, this.quarterTurns, this.level.getGameTime());
        this.anchorId = anchor.id();
        if (crew > 0) {
            this.notes.add(crew + " tripulante(s) do convés nos blocos");
        }
        SpaceHorizons.LOGGER.info("[anchor] nave {} ancorada no ar: âncora {} em {} ({}x{}x{}), {} tripulante(s) a pé", this.rocketUuid, anchor.id(),
                this.origin.toShortString(), this.rotated.sizeX, this.rotated.sizeY, this.rotated.sizeZ, crew);
    }

    /** Falhou ou teve perdas antes de remover a entidade: retira os blocos já colocados; a entidade segue intacta (e é descongelada pela fila). */
    @Override
    void abort(MinecraftServer server, String reason) {
        SpaceHorizons.LOGGER.error("[anchor] âncora da nave {} cancelada: {}", this.rocketUuid, reason);
        if (this.error == null) {
            this.error = reason;
        }
        for (int i = this.placed - 1; i >= 0; i--) {
            BlockPos pos = this.items.get(i).pos();
            this.level.removeBlockEntity(pos);
            this.level.setBlock(pos, Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
        }
        this.placed = 0;
        this.phase = Phase.FINISH;
    }
}
