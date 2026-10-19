package com.spacehorizons.space.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RocketStatsTest {
    /** Cockpit + 1 motor + 1 tanque + 3 blocos de estrutura: 1,9 t seco, 4.000 mB. */
    private static RocketStats minimal() {
        return RocketStats.of(1, 1, 0, 0, 1, 3, 1.0);
    }

    @Test
    void minimalRocketMatchesTheDesignNumbers() {
        RocketStats s = minimal();
        assertEquals(1.9, s.dryMassT(), 1e-12);
        assertEquals(2.5, s.thrust(), 1e-12);
        assertEquals(1_600.0, s.ispTicks(), 0.0);
        assertEquals(128.0, s.exhaustVelocity(), 1e-12);
        assertEquals(4_000.0, s.fuelCapacityMb(), 0.0);
        assertEquals(5.1, s.wetMass(4_000), 1e-12);
        assertEquals(1, s.engines());
        assertEquals(1, s.tanks());
    }

    @Test
    void minimalRocketCanReachSpace() {
        // o marco: encher o tanque dá um voo ao espaço (suborbital, Δv >= 35)
        RocketStats s = minimal();
        double dv = s.deltaV(s.fuelCapacityMb());
        assertEquals(128 * Math.log(5.1 / 1.9), dv, 1e-9);
        assertTrue(dv >= 35, "Δv = " + dv);
        assertEquals(0.0, s.deltaV(0), 0.0);
        // com a metade do combustível ainda há Δv, mas menos
        assertTrue(s.deltaV(2_000) < dv && s.deltaV(2_000) > 0);
    }

    @Test
    void thrustToWeightRatio() {
        RocketStats s = minimal();
        assertEquals(2.5 / (5.1 * 0.08), s.twr(4_000, 0.08), 1e-9);
        // vazio o foguete fica muito mais leve
        assertTrue(s.twr(0, 0.08) > s.twr(4_000, 0.08));
        assertEquals(Double.POSITIVE_INFINITY, s.twr(4_000, 0));
    }

    @Test
    void orbitRocketHasEnoughDeltaV() {
        // 5 tanques + 2 motores + 30 blocos de estrutura: 6 t seco, 16 t de combustível, 22 t no total
        RocketStats s = RocketStats.of(2, 5, 0, 0, 1, 30, 9.0);
        assertEquals(6.0, s.dryMassT(), 1e-9);
        assertEquals(22.0, s.wetMass(s.fuelCapacityMb()), 1e-9);
        assertTrue(s.deltaV(s.fuelCapacityMb()) >= 150);
        // foguete de 9 blocos² e 22 t: velocidade terminal de ~16 blocos/tick
        double terminal = Math.sqrt(FlightConstants.G0 / s.dragK0());
        assertEquals(16.0, terminal, 0.5);
    }

    @Test
    void roundTripToTheMoonRocketHasEnoughDeltaV() {
        // 21 tanques + 5 motores + 30 blocos: 11 t seco, 67,2 t de combustível, 78,2 t
        RocketStats s = RocketStats.of(5, 21, 0, 0, 1, 30, 9.0);
        assertEquals(11.0, s.dryMassT(), 1e-9);
        assertEquals(84_000.0, s.fuelCapacityMb(), 0.0);
        assertEquals(78.2, s.wetMass(s.fuelCapacityMb()), 1e-9);
        assertTrue(s.deltaV(s.fuelCapacityMb()) >= 247);
    }

    @Test
    void flowAndBurnTimeFollowTheDesign() {
        // 5 motores a 100%: 0,09765625 t/tick = 122,07 mB/tick
        RocketStats s = RocketStats.of(5, 21, 0, 0, 1, 30, 9.0);
        assertEquals(0.09765625, s.fullThrottleFlow(), 1e-12);
        assertEquals(122.0703125, s.fullThrottleFuelMbPerTick(), 1e-9);
        assertEquals(s.thrust() / s.wetMass(84_000), s.acceleration(84_000, 1.0), 1e-12);
        assertEquals(0.5 * s.acceleration(84_000, 1.0), s.acceleration(84_000, 0.5), 1e-12);
        assertEquals(Tsiolkovsky.burnTime(s.wetMass(84_000), 0.09765625, 128, 20), s.burnTime(84_000, 20), 1e-12);
    }

    @Test
    void parachuteCountDecidesTheChuteDrag() {
        RocketStats without = RocketStats.of(1, 1, 0, 0, 1, 3, 1.0);
        RocketStats with = RocketStats.of(1, 1, 1, 0, 1, 3, 1.0);
        assertEquals(0.0, without.parachuteK0(), 0.0);
        assertEquals(FlightConstants.G0 / 0.25, with.parachuteK0(), 1e-12);
        assertEquals(0.1, with.dryMassT() - without.dryMassT(), 1e-12);
        assertEquals(1, with.parachutes());
    }

    @Test
    void dragIsClampedToSaneTerminalSpeeds() {
        // pesado e fino: arrasto mínimo; leve e largo: arrasto máximo (a área mínima considerada é 1 bloco²)
        RocketStats tiny = RocketStats.of(5, 21, 0, 0, 1, 30, 0.01);
        RocketStats huge = RocketStats.of(1, 1, 0, 0, 1, 0, 10_000);
        assertEquals(FlightConstants.MAX_TERMINAL_SPEED, Math.sqrt(FlightConstants.G0 / tiny.dragK0()), 1e-9);
        assertEquals(FlightConstants.MIN_TERMINAL_SPEED, Math.sqrt(FlightConstants.G0 / huge.dragK0()), 1e-9);
    }

    @Test
    void invalidRocketsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RocketStats(0, 1, 500, 0, 1e-4, 0, 1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RocketStats(1, -1, 500, 0, 1e-4, 0, 1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RocketStats(1, 1, 0, 0, 1e-4, 0, 1, 0, 0, 0));
    }

    @Test
    void rocketWithoutEnginesHasNoDeltaV() {
        RocketStats s = RocketStats.of(0, 1, 0, 0, 1, 2, 1.0);
        assertEquals(0.0, s.deltaV(4_000), 0.0);
        assertEquals(Double.POSITIVE_INFINITY, s.burnTime(4_000, 10));
        assertEquals(0.0, s.fullThrottleFlow(), 0.0);
    }
}
