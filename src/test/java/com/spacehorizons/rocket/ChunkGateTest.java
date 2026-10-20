package com.spacehorizons.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.spacehorizons.rocket.ChunkGate.Cell;
import com.spacehorizons.rocket.ChunkGate.Decision;
import com.spacehorizons.rocket.ChunkGate.Requester;
import com.spacehorizons.rocket.ChunkGate.Sink;

/** O corredor de chunks à frente do foguete, os pedidos e o portão que segura a simulação. */
class ChunkGateTest {
    /** Um nível de mentira: guarda o que foi pedido e deixa os testes dizerem o que já carregou. */
    private static final class FakeLevel implements Sink {
        final Set<Long> loaded = new HashSet<>();
        int adds;

        @Override
        public void add(int chunkX, int chunkZ, int radius) {
            this.adds++;
        }

        @Override
        public boolean loaded(int chunkX, int chunkZ) {
            return this.loaded.contains(ChunkGate.key(chunkX, chunkZ));
        }

        void load(Cell c) {
            this.loaded.add(ChunkGate.key(c.chunkX(), c.chunkZ()));
        }
    }

    @Test
    void theCorridorIsContiguousAlongTheTrajectory() {
        // 64 blocos por tick para leste, 20 ticks: 80 chunks de comprimento sem buracos (as sondas esparsas deixavam entre os passos 8, 16, 32)
        List<Cell> cells = ChunkGate.corridor(8.0, 8.0, 64.0, 0.0, 0.0, 0.0, 20);
        Set<Integer> xs = new HashSet<>();
        for (Cell c : cells) {
            assertEquals(0, c.chunkZ());
            xs.add(c.chunkX());
        }
        int last = Math.floorDiv((int) (8.0 + 64.0 * 20), 16);
        for (int x = 0; x <= last; x++) {
            assertTrue(xs.contains(x), "falta o chunk " + x + " do corredor");
        }
    }

    @Test
    void theCorridorHasTheWidthOfTheFootprint() {
        // pegada de meia-extensão 20 (a caixa e a folga): 3 chunks de largura em Z (z de -12 a 28)
        List<Cell> cells = ChunkGate.corridor(8.0, 8.0, 32.0, 0.0, 0.0, 20.0, 10);
        Set<Integer> zs = new HashSet<>();
        for (Cell c : cells) {
            zs.add(c.chunkZ());
        }
        assertEquals(Set.of(-1, 0, 1), zs, "z de 8-20 a 8+20 toca os chunks -1 a 1");
    }

    @Test
    void cellsAreOrderedFromNearToFarAndNoChunkRepeats() {
        List<Cell> cells = ChunkGate.corridor(100.0, -20.0, 30.0, 12.0, 0.0, 0.0, 30);
        Set<Long> seen = new HashSet<>();
        int previous = -1;
        for (Cell c : cells) {
            assertTrue(c.ticksAhead() >= previous, "ordem por proximidade");
            previous = c.ticksAhead();
            assertTrue(seen.add(ChunkGate.key(c.chunkX(), c.chunkZ())), "chunk repetido");
        }
        assertEquals(6, cells.get(0).chunkX(), "o chunk do próprio foguete vem primeiro");
        assertEquals(-2, cells.get(0).chunkZ());
        assertEquals(0, cells.get(0).ticksAhead());
    }

    @Test
    void theNearCellsGetRadiusTwoAndTheFarOnesRadiusZero() {
        for (Cell c : ChunkGate.corridor(0, 0, 10, 10, 0.0, 0.0, 30)) {
            assertEquals(c.ticksAhead() <= ChunkGate.NEAR_STEPS ? ChunkGate.NEAR_RADIUS : 0, c.radius(), "ticks " + c.ticksAhead());
        }
    }

    @Test
    void onlyTheFootprintAtTheGatingStepsHoldsTheStep() {
        // 30 blocos por tick: os chunks do corredor entre os passos de segurar e depois do tick 4 NÃO seguram
        List<Cell> cells = ChunkGate.corridor(0.0, 8.0, 30.0, 0.0, 0.0, 0.0, 40);
        Set<Integer> gating = new HashSet<>();
        for (Cell c : cells) {
            if (c.gating()) {
                gating.add(c.chunkX());
            }
        }
        // x do tick 0, 1, 2 e 4: 0, 30, 60, 120 -> chunks 0, 1, 3, 7
        assertEquals(Set.of(0, 1, 3, 7), gating);
        assertTrue(cells.stream().anyMatch(c -> !c.gating() && c.chunkX() == 2), "o chunk 2 é pedido, mas não segura");
    }

    @Test
    void aSlowOrStoppedRocketAsksOnlyForItsFootprint() {
        List<Cell> still = ChunkGate.corridor(15.0, 8.0, 0.0, 0.0, 0.0, 3.0, 40);
        assertEquals(2, still.size(), "a 1 bloco da borda do chunk, meia-extensão 3: chunks 0 e 1");
        assertTrue(still.stream().allMatch(Cell::gating));
        assertTrue(still.stream().anyMatch(c -> c.chunkX() == 1 && c.chunkZ() == 0));
    }

    @Test
    void theXSeamIsNormalizedOnPlanets() {
        // x = 32.760 com 100 b/t para leste: 4 ticks à frente já passou de C/2 = 32.768 e volta pelo outro lado
        double c = 65_536.0;
        List<Cell> cells = ChunkGate.corridor(32_760.0, 0.0, 100.0, 0.0, c, 0.0, 10);
        int period = (int) (c / 16.0);
        for (Cell cell : cells) {
            assertTrue(cell.chunkX() >= -period / 2 && cell.chunkX() < period / 2, "chunk " + cell.chunkX() + " fora da faixa do planeta");
        }
        Cell four = cells.stream().filter(cell -> cell.gating() && cell.ticksAhead() == 4).findFirst().orElseThrow();
        assertEquals(Math.floorDiv((int) (32_760 + 400 - 65_536), 16), four.chunkX());
        assertEquals(-32_768.0, ChunkGate.normalize(32_768.0, c), 1e-9);
        assertEquals(32_767.5, ChunkGate.normalize(-32_768.5, c), 1e-9);
        assertEquals(5.0, ChunkGate.normalize(5.0, 0.0), 0.0, "sem circunferência não normaliza (espaço aberto)");
    }

    @Test
    void theGateHoldsWhileChunksAreMissingAndReleasesWhenTheyLoad() {
        ChunkGate gate = new ChunkGate();
        assertEquals(Decision.GO, gate.decide(false));
        assertEquals(Decision.HOLD, gate.decide(true));
        assertTrue(gate.isHolding());
        assertEquals(Decision.HOLD, gate.decide(true));
        assertEquals(2, gate.heldTicks());
        assertEquals(Decision.GO, gate.decide(false));
        assertFalse(gate.isHolding());
        assertEquals(0, gate.heldTicks());
    }

    @Test
    void afterTwoHundredTicksItGivesUpOnceAndKeepsGoing() {
        ChunkGate gate = new ChunkGate();
        for (int i = 0; i < ChunkGate.MAX_HELD_TICKS; i++) {
            assertEquals(Decision.HOLD, gate.decide(true), "tick " + i);
        }
        assertEquals(Decision.GIVE_UP, gate.decide(true), "estourou o teto: avisa uma vez");
        assertFalse(gate.isHolding());
        assertEquals(Decision.GO, gate.decide(true), "e segue sem segurar");
        assertEquals(Decision.GO, gate.decide(true));
        assertEquals(Decision.GO, gate.decide(false));
        // depois de carregar, o contador recomeça
        assertEquals(Decision.HOLD, gate.decide(true));
    }

    // ---------------------------------------------------------------- pedidos

    @Test
    void theRequesterAsksForNewChunksWithinTheBudgetPerTick() {
        FakeLevel level = new FakeLevel();
        Requester requester = new Requester();
        List<Cell> corridor = ChunkGate.corridor(8.0, 8.0, 90.0, 0.0, 0.0, 0.0, 80);
        assertTrue(corridor.size() > Requester.MAX_NEW_PER_TICK * 2, "o corredor é maior que o orçamento de um tick");
        requester.update(0, corridor, level);
        long gatingCells = corridor.stream().filter(Cell::gating).count();
        assertTrue(level.adds <= Requester.MAX_NEW_PER_TICK + gatingCells, "no máximo o orçamento (mais os que seguram o passo) por tick: " + level.adds);
        int firstTick = level.adds;
        requester.update(1, corridor, level);
        assertTrue(level.adds > firstTick, "o tick seguinte pede mais");
    }

    @Test
    void theRequesterCapsThePendingChunks() {
        FakeLevel level = new FakeLevel();
        Requester requester = new Requester();
        List<Cell> corridor = ChunkGate.corridor(8.0, 8.0, 90.0, 0.0, 0.0, 0.0, 80);
        for (int tick = 0; tick < 30; tick++) {
            requester.update(tick, corridor, level);
        }
        assertTrue(requester.pending() <= Requester.MAX_PENDING + corridor.stream().filter(Cell::gating).count(),
                "nada carregou: os pendentes não passam do teto: " + requester.pending());
    }

    @Test
    void aSlowServerShrinksTheRequestBudget() {
        assertEquals(1.0, Requester.budgetScale(20.0), 1e-9);
        assertEquals(1.0, Requester.budgetScale(Requester.FULL_BUDGET_TICK_MS), 1e-9);
        assertEquals(0.0, Requester.budgetScale(Requester.MIN_BUDGET_TICK_MS), 1e-9);
        assertEquals(0.0, Requester.budgetScale(500.0), 1e-9);
        double mid = Requester.budgetScale((Requester.FULL_BUDGET_TICK_MS + Requester.MIN_BUDGET_TICK_MS) / 2.0);
        assertEquals(0.5, mid, 1e-9);
        FakeLevel full = new FakeLevel();
        FakeLevel slow = new FakeLevel();
        List<Cell> corridor = ChunkGate.corridor(8.0, 8.0, 90.0, 0.0, 0.0, 0.0, 80);
        new Requester().update(0, corridor, full, 1.0);
        new Requester().update(0, corridor, slow, 0.0);
        assertTrue(slow.adds < full.adds, "o orçamento mínimo pede bem menos: " + slow.adds + " contra " + full.adds);
        long gating = corridor.stream().filter(Cell::gating).count();
        assertTrue(slow.adds >= gating, "o que segura o passo é pedido sempre");
    }

    @Test
    void theRequesterRenewsTicketsBeforeTheyExpire() {
        FakeLevel level = new FakeLevel();
        Requester requester = new Requester();
        List<Cell> still = ChunkGate.corridor(8.0, 8.0, 0.0, 0.0, 0.0, 0.0, 40);
        requester.update(0, still, level);
        int first = level.adds;
        requester.update(1, still, level);
        assertEquals(first, level.adds, "dentro do prazo não repete o pedido");
        requester.update(Requester.RENEW_TICKS, still, level);
        assertEquals(first + still.size(), level.adds, "no prazo renova o ticket de todos");
    }

    @Test
    void theRequesterReportsMissingGatingChunksUntilTheyLoad() {
        FakeLevel level = new FakeLevel();
        Requester requester = new Requester();
        List<Cell> corridor = ChunkGate.corridor(8.0, 8.0, 40.0, 0.0, 0.0, 0.0, 30);
        assertTrue(requester.update(0, corridor, level), "nada carregado: segura");
        for (Cell c : corridor) {
            if (c.gating()) {
                level.load(c);
            }
        }
        assertFalse(requester.update(1, corridor, level), "os que seguram o passo carregaram, os outros não importam");
    }

    @Test
    void theRequesterMeasuresTheGenerationLatencyAndWidensTheLookahead() {
        FakeLevel level = new FakeLevel();
        Requester requester = new Requester();
        int before = requester.lookaheadTicks();
        List<Cell> corridor = ChunkGate.corridor(8.0, 8.0, 40.0, 0.0, 0.0, 0.0, 30);
        requester.update(0, corridor, level);
        // a geração demora 40 ticks para tudo
        for (int tick = 1; tick < 40; tick++) {
            requester.update(tick, corridor, level);
        }
        corridor.forEach(level::load);
        requester.update(40, corridor, level);
        assertTrue(requester.latency() > 10.0, "a latência medida subiu: " + requester.latency());
        assertTrue(requester.lookaheadTicks() > before, "a antecedência acompanha a latência");
        assertTrue(requester.lookaheadTicks() <= Requester.MAX_LOOKAHEAD);
    }

    @Test
    void theRequesterForgetsChunksThatLeftTheCorridor() {
        FakeLevel level = new FakeLevel();
        Requester requester = new Requester();
        requester.update(0, ChunkGate.corridor(8.0, 8.0, 0.0, 0.0, 0.0, 0.0, 40), level);
        assertEquals(1, requester.tracked());
        // o foguete foi para longe: o chunk antigo não é mais pedido e some da lista depois do prazo
        List<Cell> far = ChunkGate.corridor(100_008.0, 8.0, 0.0, 0.0, 0.0, 0.0, 40);
        for (int tick = 1; tick <= 70; tick++) {
            requester.update(tick, far, level);
        }
        assertEquals(1, requester.tracked(), "só o chunk novo");
    }
}
