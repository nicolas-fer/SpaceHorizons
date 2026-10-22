package com.spacehorizons.ship;

import java.util.ArrayList;
import java.util.List;

import com.spacehorizons.rocket.RocketFuelTankBlockEntity;
import com.spacehorizons.ship.block.ShipBridgeBlock;
import com.spacehorizons.ship.block.ShipImpulseEngineBlock;

import net.minecraft.core.Direction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * O censo da caixa de uma nave, em lotes: percorre as SEÇÕES da caixa ({@link BoxSections}) e conta os blocos por papel pela paleta de
 * cada seção ({@code PalettedContainer.count}, sem ler célula a célula: uma seção só de ar ou de um bloco só custa quase nada). Os tanques
 * (block entities) e o combustível deles saem uma vez por coluna de chunk. A caixa é sempre a de uma célula do estaleiro, onde só há a
 * nave: contar a seção inteira é o mesmo que contar a parte dela dentro da caixa. Um passo respeita o orçamento ({@link ConversionBudget});
 * carregar um chunk do disco custa mais que ler um já carregado.
 */
final class ShipCensusScanner {
    /** Custo, em unidades do orçamento, de uma seção cuja paleta é lida e de um chunk que precisa ser carregado. */
    private static final int SECTION_COST = 4;
    private static final int CHUNK_LOAD_COST = 64;

    private final ServerLevel yard;
    private final ShipYard.Box box;
    private final List<BoxSections.Section> sections;
    private final long startTick;
    private final int[] counts = new int[ShipBlockKind.values().length];
    /** Pontes por lado da proa (o lado oposto ao facing) e motores por lado do bocal (ver {@link ShipArrangement}). */
    private final int[] bowVotes = new int[4];
    private final int[] nozzles = new int[4];
    private final List<BlockPos> tanks = new ArrayList<>();
    private long fuelMb;
    private int next;
    private int lastCx = Integer.MIN_VALUE;
    private int lastCz = Integer.MIN_VALUE;

    ShipCensusScanner(ServerLevel yard, ShipYard.Box box) {
        this.yard = yard;
        this.box = box;
        this.sections = BoxSections.of(box);
        this.startTick = yard.getGameTime();
    }

    /** Um passo dentro do orçamento; true quando varreu a caixa toda. */
    boolean step(ConversionBudget budget) {
        while (this.next < this.sections.size() && !budget.exhausted()) {
            BoxSections.Section s = this.sections.get(this.next++);
            boolean loaded = this.yard.getChunkSource().getChunkNow(s.cx(), s.cz()) != null;
            budget.spend(loaded ? SECTION_COST : CHUNK_LOAD_COST);
            LevelChunk chunk = this.yard.getChunk(s.cx(), s.cz());
            if (s.cx() != this.lastCx || s.cz() != this.lastCz) {
                this.lastCx = s.cx();
                this.lastCz = s.cz();
                readTanks(chunk);
            }
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(s.sy() << 4));
            if (section.hasOnlyAir()) {
                continue;
            }
            section.getStates().count(this::countState);
        }
        return this.next >= this.sections.size();
    }

    /** Conta {@code n} blocos deste estado: o papel, e a orientação das pontes e dos motores. */
    private void countState(BlockState state, int n) {
        ShipBlockKind kind = ShipKinds.classify(state);
        this.counts[kind.ordinal()] += n;
        if (kind == ShipBlockKind.BRIDGE) {
            this.bowVotes[ShipArrangement.opposite(ShipKinds.side(state.getValue(ShipBridgeBlock.FACING)))] += n;
        } else if (kind == ShipBlockKind.ENGINE) {
            // o bocal para cima ou para baixo não empurra no plano da nave: conta a massa (no kind), não o lado
            Direction nozzle = state.getValue(ShipImpulseEngineBlock.FACING);
            if (nozzle.getAxis().isHorizontal()) {
                this.nozzles[ShipKinds.side(nozzle)] += n;
            }
        }
    }

    /** Os tanques de combustível da coluna que estão dentro da caixa e o combustível deles. */
    private void readTanks(LevelChunk chunk) {
        for (java.util.Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
            // só o tanque químico: os tanques do Tier 2 (hidrogênio, xenônio) usam o mesmo block entity, mas a nave não queima esses combustíveis
            if (e.getValue() instanceof RocketFuelTankBlockEntity tank && tank.propellant() == com.spacehorizons.space.flight.Propellant.CHEMICAL
                    && this.box.contains(e.getKey().getX(), e.getKey().getY(), e.getKey().getZ())) {
                this.tanks.add(e.getKey().immutable());
                this.fuelMb += tank.fuelMb();
            }
        }
    }

    /** O resultado (só vale depois de {@link #step} devolver true). */
    ShipCensusResult result() {
        ShipCensus census = ShipCensus.EMPTY;
        for (ShipBlockKind kind : ShipBlockKind.values()) {
            census = census.plus(kind, this.counts[kind.ordinal()]);
        }
        // a proa é o lado que a maioria das pontes aponta (empate: a primeira na ordem norte, leste, sul, oeste; sem ponte: o norte)
        int bow = 0;
        for (int side = 1; side < 4; side++) {
            if (this.bowVotes[side] > this.bowVotes[bow]) {
                bow = side;
            }
        }
        ShipArrangement arrangement = new ShipArrangement(bow, this.nozzles[0], this.nozzles[1], this.nozzles[2], this.nozzles[3]);
        // as colunas de tanque formadas, pela mesma partição do foguete (Multiblocks), sobre as posições dos tanques achados
        long[] packed = new long[this.tanks.size()];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = this.tanks.get(i).asLong();
        }
        census = census.withTankStructures(com.spacehorizons.rocket.Multiblocks.tileTanks(packed).boxes());
        return new ShipCensusResult(census, List.copyOf(this.tanks), this.fuelMb, this.startTick, arrangement);
    }
}
