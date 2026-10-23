package com.spacehorizons.moonbase.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.moonbase.room.RoomScan.Status;

/** Pressão da sala e consumo do gerador: enche, mantém, vaza, passa fome. */
class RoomPressureTest {
    private static RoomPressure.Result run(Status status, int volume, int scans, int pressure, int oxygen, int energy) {
        RoomPressure.Result r = new RoomPressure.Result(pressure, oxygen, energy);
        for (int i = 0; i < scans; i++) {
            r = RoomPressure.step(status, volume, r.pressure(), r.oxygen(), r.energy());
        }
        return r;
    }

    @Test
    void sealedRoomWithPowerAndOxygenFillsInTenScansAndBecomesBreathableAtHalf() {
        RoomPressure.Result half = run(Status.SEALED, 75, 5, 0, 2_400, 10_000);
        assertEquals(500, half.pressure());
        assertTrue(half.breathable());
        assertFalse(run(Status.SEALED, 75, 4, 0, 2_400, 10_000).breathable());
        assertEquals(RoomPressure.MAX_PRESSURE, run(Status.SEALED, 75, 10, 0, 2_400, 10_000).pressure());
        assertEquals(RoomPressure.MAX_PRESSURE, run(Status.SEALED, 75, 40, 0, 2_400, 10_000).pressure(), "não passa do máximo");
    }

    @Test
    void maintenanceCostsOxygenAndPowerPerScan() {
        assertEquals(3, RoomPressure.maintenance(75));
        assertEquals(1, RoomPressure.maintenance(5));
        assertEquals(50, RoomPressure.maintenance(1_000));
        RoomPressure.Result r = run(Status.SEALED, 75, 10, 0, 2_400, 10_000);
        assertEquals(2_400 - 10 * 3, r.oxygen());
        assertEquals(10_000 - 10 * RoomPressure.POWER_PER_SCAN, r.energy());
    }

    @Test
    void aSmallTankLastsAboutSixAndAHalfMinutesInAMediumRoom() {
        // 2.400 de oxigênio, 3 por varredura de 10 ticks: 800 varreduras = 8.000 ticks = 6,7 min
        int scans = 0;
        RoomPressure.Result r = new RoomPressure.Result(RoomPressure.MAX_PRESSURE, 2_400, 1_000_000);
        while (r.oxygen() >= RoomPressure.maintenance(75)) {
            r = RoomPressure.step(Status.SEALED, 75, r.pressure(), r.oxygen(), r.energy());
            scans++;
        }
        assertEquals(800, scans);
        assertEquals(400.0, scans * RoomPressure.SCAN_INTERVAL_TICKS / 20.0, 0.001);
    }

    @Test
    void leakDropsThePressureFastAndKeepsTheOxygen() {
        RoomPressure.Result r = run(Status.LEAK, 75, 1, RoomPressure.MAX_PRESSURE, 2_400, 10_000);
        assertEquals(800, r.pressure());
        assertEquals(2_400, r.oxygen(), "o ar que vaza não gasta o reservatório");
        assertEquals(10_000, r.energy());
        assertEquals(0, run(Status.LEAK, 75, 5, RoomPressure.MAX_PRESSURE, 2_400, 10_000).pressure());
        // um vazamento tira a sala do ar respirável em 3 varreduras (30 ticks)
        assertFalse(run(Status.LEAK, 75, 3, RoomPressure.MAX_PRESSURE, 2_400, 10_000).breathable());
        assertTrue(run(Status.LEAK, 75, 2, RoomPressure.MAX_PRESSURE, 2_400, 10_000).breathable());
    }

    @Test
    void tooBigAndBlockedBehaveLikeLeaks() {
        assertEquals(800, run(Status.TOO_BIG, 1_001, 1, RoomPressure.MAX_PRESSURE, 100, 100).pressure());
        assertEquals(800, run(Status.BLOCKED, 0, 1, RoomPressure.MAX_PRESSURE, 100, 100).pressure());
    }

    @Test
    void sealedWithoutPowerOrOxygenLosesPressureSlowly() {
        RoomPressure.Result noPower = run(Status.SEALED, 75, 1, RoomPressure.MAX_PRESSURE, 2_400, RoomPressure.POWER_PER_SCAN - 1);
        assertEquals(990, noPower.pressure());
        assertEquals(2_400, noPower.oxygen());
        RoomPressure.Result noAir = run(Status.SEALED, 75, 1, RoomPressure.MAX_PRESSURE, 2, 10_000);
        assertEquals(990, noAir.pressure());
        assertEquals(10_000, noAir.energy());
        // 100 varreduras (1.000 ticks = 50 s) para zerar
        assertEquals(0, run(Status.SEALED, 75, 100, RoomPressure.MAX_PRESSURE, 0, 0).pressure());
        assertTrue(run(Status.SEALED, 75, 40, RoomPressure.MAX_PRESSURE, 0, 0).breathable(), "40 varreduras ainda respira (pressão 600)");
    }

    @Test
    void pressureIsClampedToTheValidRange() {
        assertEquals(RoomPressure.MAX_PRESSURE, run(Status.SEALED, 10, 1, 5_000, 100, 100).pressure());
        assertEquals(0, run(Status.LEAK, 10, 1, -50, 100, 100).pressure());
    }
}
