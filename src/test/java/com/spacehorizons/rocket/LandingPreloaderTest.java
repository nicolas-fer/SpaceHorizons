package com.spacehorizons.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.spacehorizons.rocket.ImpactPredictor.Point;
import com.spacehorizons.rocket.ImpactPredictor.Prediction;
import com.spacehorizons.rocket.LandingPreloader.State;

/** A seleção dos chunks do local de pouso e o controle de tickets (taxa limitada, soltar o que a previsão largou). */
class LandingPreloaderTest {
    /** Um mundo falso: os chunks pedidos carregam quando o teste manda. */
    private static final class FakeWorld implements LandingPreloader.Sink {
        final Set<Long> tickets = new HashSet<>();
        final Set<Long> loaded = new HashSet<>();
        int adds;
        int removes;

        @Override
        public void add(int chunkX, int chunkZ) {
            this.adds++;
            this.tickets.add(LandingPreloader.key(chunkX, chunkZ));
        }

        @Override
        public void remove(int chunkX, int chunkZ) {
            this.removes++;
            this.tickets.remove(LandingPreloader.key(chunkX, chunkZ));
        }

        @Override
        public boolean loaded(int chunkX, int chunkZ) {
            return this.loaded.contains(LandingPreloader.key(chunkX, chunkZ));
        }

        void loadAllRequested() {
            this.loaded.addAll(this.tickets);
        }
    }

    private static Prediction straightDown(double x, double z) {
        List<Point> path = new ArrayList<>();
        for (double y = 1_500.0; y >= 0.0; y -= 30.0) {
            path.add(new Point(x, y, z));
        }
        return new Prediction(x, 0.0, z, 100, path);
    }

    /** Descida com deriva lateral de {@code dx} blocos por ponto. */
    private static Prediction slanted(double x, double z, double dx) {
        List<Point> path = new ArrayList<>();
        int n = 50;
        for (int i = 0; i <= n; i++) {
            path.add(new Point(x - dx * (n - i), 1_500.0 * (n - i) / n, z));
        }
        return new Prediction(x, 0.0, z, 100, path);
    }

    @Test
    void onlyADescentBelowTheExitAltitudeIsWorthPredicting() {
        assertTrue(LandingPreloader.worthPredicting(4_000.0, 5_000.0, -3.0));
        assertFalse(LandingPreloader.worthPredicting(4_000.0, 5_000.0, 0.0), "subindo ou no apoastro: o impacto balístico é longe demais");
        assertFalse(LandingPreloader.worthPredicting(4_000.0, 5_000.0, 5.0));
        assertFalse(LandingPreloader.worthPredicting(5_200.0, 5_000.0, -3.0), "em órbita, acima da zona do planeta: a circularização muda tudo");
    }

    @Test
    void theImpactAreaIs7x7AroundTheImpactChunk() {
        List<Long> chunks = LandingPreloader.select(straightDown(8.0, 8.0), 0.0);
        Set<Long> set = new HashSet<>(chunks);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                assertTrue(set.contains(LandingPreloader.key(dx, dz)), "chunk " + dx + "," + dz);
            }
        }
        assertEquals(chunks.size(), set.size(), "sem repetição");
        // descida vertical: o trecho final cai dentro dos 7x7 (a pegada de 3x3 do ponto já está lá)
        assertEquals(49, chunks.size());
    }

    @Test
    void theImpactChunkComesFirstAndTheAreaGrowsOutward() {
        List<Long> chunks = LandingPreloader.select(straightDown(8.0, 8.0), 0.0);
        assertEquals(LandingPreloader.key(0, 0), chunks.get(0));
        // o anel de raio 1 (8 chunks) vem antes do de raio 2
        for (int i = 1; i <= 8; i++) {
            int x = LandingPreloader.keyX(chunks.get(i));
            int z = LandingPreloader.keyZ(chunks.get(i));
            assertEquals(1, Math.max(Math.abs(x), Math.abs(z)), "posição " + i);
        }
    }

    @Test
    void aSlantedFinalPathAddsTheChunksAlongTheApproach() {
        // chega vindo de oeste: 50 pontos a 40 blocos cada = 2.000 blocos = 125 chunks de comprimento
        List<Long> chunks = LandingPreloader.select(slanted(8.0, 8.0, 40.0), 0.0);
        Set<Long> set = new HashSet<>(chunks);
        assertTrue(chunks.size() > 49);
        assertTrue(set.contains(LandingPreloader.key(-20, 0)), "a aproximação a 20 chunks a oeste está na lista");
        assertTrue(set.contains(LandingPreloader.key(-20, 1)), "com a pegada de 3x3");
        assertFalse(set.contains(LandingPreloader.key(-20, 5)));
        assertTrue(chunks.size() <= LandingPreloader.MAX_CHUNKS);
    }

    @Test
    void theSelectionIsCapped() {
        List<Long> chunks = LandingPreloader.select(slanted(8.0, 8.0, 400.0), 0.0);
        assertEquals(LandingPreloader.MAX_CHUNKS, chunks.size());
    }

    @Test
    void theSeamOfThePlanetWrapsX() {
        double circumference = 655_360.0;
        // impacto logo depois da costura (x = -circ/2 + 8): a área vem em volta de x ≈ -327.672, sem chunks "do outro lado"
        double x = circumference / 2.0 + 8.0; // fora do intervalo normal: a seleção normaliza
        List<Long> chunks = LandingPreloader.select(straightDown(x, 0.0), circumference);
        int center = (int) Math.floor(ChunkGate.normalize(x, circumference)) >> 4;
        assertEquals(LandingPreloader.key(center, 0), chunks.get(0));
        for (long k : chunks) {
            assertTrue(LandingPreloader.keyX(k) >= -20_490 && LandingPreloader.keyX(k) <= -20_470, "chunk X normalizado: " + LandingPreloader.keyX(k));
        }
    }

    @Test
    void theFirstUpdateRequestsAtMostTheNewChunkRate() {
        LandingPreloader preloader = new LandingPreloader();
        FakeWorld world = new FakeWorld();
        preloader.update(slanted(8.0, 8.0, 40.0), 0.0, world);
        assertEquals(LandingPreloader.MAX_NEW_PER_UPDATE, world.tickets.size());
        assertEquals(State.LOADING, preloader.state());
        assertEquals(0, preloader.percent());
    }

    @Test
    void itDoesNotRequestMoreWhileTooManyChunksArePending() {
        LandingPreloader preloader = new LandingPreloader();
        FakeWorld world = new FakeWorld();
        Prediction p = slanted(8.0, 8.0, 40.0);
        for (int i = 0; i < 20; i++) {
            preloader.update(p, 0.0, world); // nada carrega: o gerador está parado
        }
        assertEquals(LandingPreloader.MAX_PENDING, world.tickets.size(), "pendentes acima do teto não são pedidos");
        assertEquals(LandingPreloader.MAX_PENDING, preloader.requestedCount());
    }

    @Test
    void whenChunksLoadTheRequestsContinueUntilReady() {
        LandingPreloader preloader = new LandingPreloader();
        FakeWorld world = new FakeWorld();
        Prediction p = straightDown(8.0, 8.0);
        int updates = 0;
        while (preloader.state() != State.READY && updates < 20) {
            preloader.update(p, 0.0, world);
            world.loadAllRequested();
            preloader.refresh(world);
            updates++;
        }
        assertEquals(State.READY, preloader.state());
        assertEquals(100, preloader.percent());
        assertEquals(49, world.tickets.size());
        assertTrue(updates >= 2, "49 chunks a 40 por atualização levam mais de uma");
    }

    @Test
    void thePercentIsTheLoadedShareOfTheArea() {
        LandingPreloader preloader = new LandingPreloader();
        FakeWorld world = new FakeWorld();
        Prediction p = straightDown(8.0, 8.0);
        preloader.update(p, 0.0, world);
        // carrega o centro e o anel 1 (9 de 49 chunks)
        List<Long> order = LandingPreloader.select(p, 0.0);
        for (int i = 0; i < 9; i++) {
            world.loaded.add(order.get(i));
        }
        preloader.refresh(world);
        assertEquals(100 * 9 / 49, preloader.percent());
        assertEquals(State.LOADING, preloader.state());
    }

    @Test
    void aNewPredictionReleasesTheTicketsItNoLongerNeedsAndKeepsTheOverlap() {
        LandingPreloader preloader = new LandingPreloader();
        FakeWorld world = new FakeWorld();
        for (int i = 0; i < 3; i++) {
            preloader.update(straightDown(8.0, 8.0), 0.0, world);
            world.loadAllRequested();
        }
        assertEquals(49, world.tickets.size());
        // o impacto andou 2 chunks para leste: 7x7 deslocado, 35 chunks em comum
        for (int i = 0; i < 3; i++) {
            preloader.update(straightDown(8.0 + 32.0, 8.0), 0.0, world);
            world.loadAllRequested();
        }
        assertEquals(49, world.tickets.size(), "só a área nova fica pedida");
        assertFalse(world.tickets.contains(LandingPreloader.key(-3, 0)), "a coluna que saiu foi solta");
        assertTrue(world.tickets.contains(LandingPreloader.key(5, 0)), "a que entrou foi pedida");
        assertTrue(world.tickets.contains(LandingPreloader.key(0, 0)), "a sobreposição continua");
        assertEquals(14, world.removes, "2 colunas de 7");
    }

    @Test
    void landingReleasesEverything() {
        LandingPreloader preloader = new LandingPreloader();
        FakeWorld world = new FakeWorld();
        for (int i = 0; i < 3; i++) {
            preloader.update(straightDown(8.0, 8.0), 0.0, world);
            world.loadAllRequested();
        }
        assertFalse(world.tickets.isEmpty());
        preloader.update(null, 0.0, world);
        assertTrue(world.tickets.isEmpty());
        assertEquals(0, preloader.requestedCount());
        assertEquals(State.IDLE, preloader.state());
        assertEquals(0, preloader.percent());
    }

    @Test
    void everyUpdateRenewsTheTicketsItKeeps() {
        LandingPreloader preloader = new LandingPreloader();
        FakeWorld world = new FakeWorld();
        Prediction p = straightDown(8.0, 8.0);
        for (int i = 0; i < 3; i++) {
            preloader.update(p, 0.0, world);
            world.loadAllRequested();
        }
        int before = world.adds;
        preloader.update(p, 0.0, world);
        assertEquals(49, world.adds - before, "49 renovações (o ticket expira em 300 ticks)");
    }
}
