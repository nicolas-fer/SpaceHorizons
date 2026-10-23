package com.spacehorizons.moonbase.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.spacehorizons.moonbase.room.RoomScan.Cell;
import com.spacehorizons.moonbase.room.RoomScan.Space;
import com.spacehorizons.moonbase.room.RoomScan.Status;

/** Selagem de sala: fechada, aberta (porta, parede quebrada), limite de volume, chunk descarregado. Mundo de teste em memória. */
class RoomScanTest {
    /** Um mundo de teste: tudo é ar, menos o que se põe como parede; fora da caixa [-50, 50]^3 é vazio. */
    private static final class World implements Space {
        private final Set<Long> walls = new HashSet<>();
        private int limit = 50;

        void wall(int x, int y, int z) {
            walls.add(key(x, y, z));
        }

        void open(int x, int y, int z) {
            walls.remove(key(x, y, z));
        }

        /** Caixa de paredes com o interior [1, w] x [1, h] x [1, d] (o cubo 0..w+1 é o casco). */
        void box(int w, int h, int d) {
            for (int x = 0; x <= w + 1; x++) {
                for (int y = 0; y <= h + 1; y++) {
                    for (int z = 0; z <= d + 1; z++) {
                        boolean inside = x >= 1 && x <= w && y >= 1 && y <= h && z >= 1 && z <= d;
                        if (!inside) {
                            wall(x, y, z);
                        }
                    }
                }
            }
        }

        private static long key(int x, int y, int z) {
            return ((long) (x + 1000) << 40) | ((long) (y + 1000) << 20) | (z + 1000);
        }

        @Override
        public Cell at(int x, int y, int z) {
            if (Math.abs(x) > limit || Math.abs(y) > limit || Math.abs(z) > limit) {
                return Cell.VOID;
            }
            return walls.contains(key(x, y, z)) ? Cell.WALL : Cell.AIR;
        }
    }

    @Test
    void closedBoxIsSealedWithTheInteriorVolume() {
        World w = new World();
        w.box(5, 3, 5);
        RoomScan.Result r = RoomScan.scan(w, 2, 2, 2);
        assertEquals(Status.SEALED, r.status());
        assertEquals(5 * 3 * 5, r.volume());
        assertEquals(75, r.cells().size());
        assertTrue(r.sealed());
    }

    @Test
    void sealedVolumeIsTheSameFromAnyStartCell() {
        World w = new World();
        w.box(4, 3, 6);
        assertEquals(RoomScan.scan(w, 1, 1, 1).volume(), RoomScan.scan(w, 4, 3, 6).volume());
    }

    @Test
    void openDoorLeaksToTheOutsideAndClosingSealsAgain() {
        World w = new World();
        w.box(5, 3, 5);
        // porta de 2 blocos de altura na parede z = 0 (o lado de fora é ar até o limite do mundo de teste)
        w.open(3, 1, 0);
        w.open(3, 2, 0);
        RoomScan.Result open = RoomScan.scan(w, 2, 2, 2);
        assertTrue(open.status() == Status.LEAK || open.status() == Status.TOO_BIG, "porta aberta deve vazar, deu " + open.status());
        assertEquals(false, open.sealed());
        w.wall(3, 1, 0);
        w.wall(3, 2, 0);
        assertEquals(Status.SEALED, RoomScan.scan(w, 2, 2, 2).status());
    }

    @Test
    void aSingleBrokenBlockInTheCeilingLeaks() {
        World w = new World();
        w.box(3, 3, 3);
        w.open(2, 4, 2);
        assertTrue(!RoomScan.scan(w, 2, 2, 2).sealed());
    }

    @Test
    void volumeCeilingStopsTheScanAndReportsTooBig() {
        World w = new World();
        w.box(10, 10, 11);   // 1.100 blocos de ar
        RoomScan.Result big = RoomScan.scan(w, 5, 5, 5);
        assertEquals(Status.TOO_BIG, big.status());
        assertEquals(RoomScan.MAX_VOLUME + 1, big.volume(), "o scan não passa de teto + 1 células");
        // a mesma caixa cabe com um teto maior
        assertEquals(Status.SEALED, RoomScan.scan(w, 5, 5, 5, 2_000).status());
        // uma sala exatamente no teto ainda é selada
        World exact = new World();
        exact.box(10, 10, 10);
        assertEquals(Status.SEALED, RoomScan.scan(exact, 5, 5, 5).status());
        assertEquals(1_000, RoomScan.scan(exact, 5, 5, 5).volume());
    }

    @Test
    void openSpaceIsAlwaysLeakOrTooBig() {
        World w = new World();
        RoomScan.Result r = RoomScan.scan(w, 0, 0, 0);
        assertTrue(r.status() == Status.TOO_BIG || r.status() == Status.LEAK);
    }

    @Test
    void reachingAnUnloadedChunkIsALeak() {
        World w = new World();
        w.limit = 3;
        // sala "selada" menos uma face que dá no vazio (chunk descarregado)
        w.box(2, 2, 2);
        w.open(1, 1, 0);     // um buraco na parede z = 0...
        // ...e do outro lado só existe VOID: o ar alcança o vazio
        RoomScan.Result r = RoomScan.scan(w, 1, 1, 1, 10_000);
        assertEquals(Status.LEAK, r.status());
    }

    @Test
    void startingInsideAWallOrVoidIsBlocked() {
        World w = new World();
        w.box(3, 3, 3);
        assertEquals(Status.BLOCKED, RoomScan.scan(w, 0, 0, 0).status());
        assertEquals(Status.BLOCKED, RoomScan.scan(w, 99, 0, 0).status());
        assertEquals(0, RoomScan.scan(w, 0, 0, 0).volume());
    }

    @Test
    void anInternalWallSplitsTheRoomIntoTwoSealedRooms() {
        World w = new World();
        w.box(6, 3, 3);
        for (int y = 1; y <= 3; y++) {
            for (int z = 1; z <= 3; z++) {
                w.wall(3, y, z);
            }
        }
        assertEquals(2 * 3 * 3, RoomScan.scan(w, 1, 1, 1).volume());
        assertEquals(3 * 3 * 3, RoomScan.scan(w, 5, 1, 1).volume());
    }

    @Test
    void aDoorInTheInternalWallJoinsTheRooms() {
        World w = new World();
        w.box(6, 3, 3);
        for (int y = 1; y <= 3; y++) {
            for (int z = 1; z <= 3; z++) {
                w.wall(3, y, z);
            }
        }
        w.open(3, 1, 2);
        w.open(3, 2, 2);
        assertEquals(Status.SEALED, RoomScan.scan(w, 1, 1, 1).status());
        // 6 x 3 x 3 de ar, menos a parede interna de 3 x 3, mais os 2 blocos da porta
        assertEquals(6 * 3 * 3 - 9 + 2, RoomScan.scan(w, 1, 1, 1).volume());
    }
}
