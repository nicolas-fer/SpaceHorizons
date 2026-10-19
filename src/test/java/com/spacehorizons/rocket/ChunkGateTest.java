package com.spacehorizons.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.spacehorizons.rocket.ChunkGate.Decision;
import com.spacehorizons.rocket.ChunkGate.Probe;

/** Os chunks à frente do foguete e o portão que segura a simulação. */
class ChunkGateTest {
    @Test
    void probesAreAskedAtTheFixedStepsExtrapolatingTheVelocity() {
        List<Probe> probes = ChunkGate.probes(8.0, 8.0, 32.0, 0.0, 0.0);
        assertEquals(ChunkGate.REQUEST_STEPS.length, probes.size());
        for (Probe p : probes) {
            assertEquals(Math.floorDiv((int) (8.0 + 32.0 * p.step()), 16), p.chunkX(), "chunk X do passo " + p.step());
            assertEquals(0, p.chunkZ());
        }
    }

    @Test
    void theNearStepsGetRadiusTwoAndTheFarOnesRadiusZero() {
        for (Probe p : ChunkGate.probes(0, 0, 10, 10, 0.0)) {
            assertEquals(p.step() <= 2 ? 2 : 0, p.radius(), "passo " + p.step());
        }
    }

    @Test
    void onlyTheFirstFourTicksHoldTheStep() {
        for (Probe p : ChunkGate.probes(0, 0, 10, 0, 0.0)) {
            assertEquals(p.step() == 1 || p.step() == 2 || p.step() == 4, p.gating(), "passo " + p.step());
        }
    }

    @Test
    void theRocketsOwnChunkIsRequestedToo() {
        Probe first = ChunkGate.probes(100.0, -20.0, 0, 0, 0.0).get(0);
        assertEquals(0, first.step());
        assertEquals(6, first.chunkX());
        assertEquals(-2, first.chunkZ());
        assertEquals(2, first.radius());
    }

    @Test
    void theXSeamIsNormalizedOnPlanets() {
        // x = 32.760 com 100 b/t para leste: 4 ticks à frente já passou de C/2 = 32.768 e volta pelo outro lado
        double c = 65_536.0;
        List<Probe> probes = ChunkGate.probes(32_760.0, 0.0, 100.0, 0.0, c);
        Probe four = probes.stream().filter(p -> p.step() == 4).findFirst().orElseThrow();
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

    @Test
    void theFootprintAddsTheNeighbourChunksTheBoxTouchesInTheNearSteps() {
        // a 1 bloco da borda do chunk (x = 15) com meia-extensão 3: toca os chunks 0 e 1 em X; em Z (z = 8) só o 0
        List<Probe> probes = ChunkGate.probes(15.0, 8.0, 0.0, 0.0, 0.0, 3.0);
        for (int k : new int[] {0, 1, 2, 4}) {
            long atStep = probes.stream().filter(p -> p.step() == k).count();
            assertEquals(2, atStep, "passo " + k);
            assertTrue(probes.stream().anyMatch(p -> p.step() == k && p.chunkX() == 1 && p.chunkZ() == 0), "vizinho no passo " + k);
        }
        // passos longe (8, 16, 32) pedem só o chunk do centro
        assertEquals(1, probes.stream().filter(p -> p.step() == 8).count());
        // os vizinhos do passo 1 também seguram o passo
        assertTrue(probes.stream().filter(p -> p.step() == 1).allMatch(Probe::gating));
    }

    @Test
    void withoutFootprintTheProbesAreTheCentreChunksOnly() {
        assertEquals(ChunkGate.REQUEST_STEPS.length, ChunkGate.probes(15.0, 8.0, 0.0, 0.0, 0.0, 0.0).size());
    }
}
