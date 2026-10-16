package com.spacehorizons.survival.oxygen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.IntSupplier;

import org.junit.jupiter.api.Test;

import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.atmosphere.AtmosphereModel;

class OxygenMathTest {
    private static final int SEA = Planets.EARTH.seaLevel();

    private static OxygenState state(int reserve) {
        return new OxygenState(reserve, true, false);
    }

    // ---- vácuo ----

    @Test
    void earthIsBreathableInsideTheAtmosphere() {
        assertFalse(OxygenMath.isVacuum(Planets.EARTH, SEA));
        assertFalse(OxygenMath.isVacuum(Planets.EARTH, SEA + 150));
        assertFalse(OxygenMath.isVacuum(Planets.EARTH, SEA + 1_000));
        assertFalse(OxygenMath.isVacuum(Planets.EARTH, SEA + AtmosphereModel.EARTH_THICKNESS - 1));
        // abaixo do nível do mar (cavernas, oceano) também tem ar
        assertFalse(OxygenMath.isVacuum(Planets.EARTH, -50));
    }

    @Test
    void earthIsVacuumFromTheTopOfTheAtmosphere() {
        assertTrue(OxygenMath.isVacuum(Planets.EARTH, SEA + AtmosphereModel.EARTH_THICKNESS));
        assertTrue(OxygenMath.isVacuum(Planets.EARTH, SEA + 2_500));
        assertTrue(OxygenMath.isVacuum(Planets.EARTH, 15_000));
    }

    @Test
    void moonIsVacuumAtAnyAltitude() {
        assertTrue(OxygenMath.isVacuum(Planets.MOON, Planets.MOON.seaLevel()));
        assertTrue(OxygenMath.isVacuum(Planets.MOON, -50));
        assertTrue(OxygenMath.isVacuum(Planets.MOON, 200));
        assertTrue(OxygenMath.isVacuum(Planets.MOON, 5_000));
    }

    @Test
    void vacuumAgreesWithTheAtmosphereModelUsedByTheVisuals() {
        AtmosphereModel earth = AtmosphereModel.of(Planets.EARTH);
        for (double altitude = -100; altitude < 3_000; altitude += 37) {
            assertEquals(earth.isVacuum(altitude), OxygenMath.isVacuum(Planets.EARTH, altitude + SEA), "altitude " + altitude);
        }
    }

    // ---- consumo e recarga ----

    @Test
    void reserveDrainsByTheConsumptionPerTickInVacuum() {
        OxygenMath.Result r = OxygenMath.step(state(OxygenMath.MAX_RESERVE), true, false, true, 0, 1);
        assertEquals(OxygenMath.MAX_RESERVE - OxygenMath.CONSUMPTION_PER_TICK, r.state().reserve());
        assertEquals(0.0F, r.damage());
        assertFalse(r.state().supplied());
    }

    @Test
    void fullReserveLastsExactlyMaxOverConsumptionTicks() {
        OxygenState s = OxygenState.FULL;
        int ticks = 0;
        while (s.reserve() > 0) {
            s = OxygenMath.step(s, true, false, true, 0, ticks + 1).state();
            ticks++;
        }
        assertEquals(OxygenMath.MAX_RESERVE / OxygenMath.CONSUMPTION_PER_TICK, ticks);
    }

    @Test
    void reserveNeverGoesBelowZero() {
        OxygenMath.Result r = OxygenMath.step(state(0), true, false, true, 0, 1);
        assertEquals(0, r.state().reserve());
        r = OxygenMath.step(state(1), true, false, true, 0, 1);
        assertEquals(0, r.state().reserve());
    }

    @Test
    void reserveRechargesGraduallyOutsideVacuum() {
        OxygenState s = state(0);
        s = OxygenMath.step(s, true, false, false, 0, 1).state();
        assertEquals(OxygenMath.RECHARGE_PER_TICK, s.reserve());
        s = OxygenMath.step(s, true, false, false, 0, 2).state();
        assertEquals(2 * OxygenMath.RECHARGE_PER_TICK, s.reserve());
        assertFalse(s.isFull());
    }

    @Test
    void rechargeStopsAtTheMaximum() {
        OxygenState s = state(OxygenMath.MAX_RESERVE - 3);
        s = OxygenMath.step(s, true, false, false, 0, 1).state();
        assertEquals(OxygenMath.MAX_RESERVE, s.reserve());
        assertTrue(s.isFull());
        s = OxygenMath.step(s, true, false, false, 0, 2).state();
        assertEquals(OxygenMath.MAX_RESERVE, s.reserve());
    }

    @Test
    void emptyReserveRefillsInMaxOverRechargeTicks() {
        OxygenState s = state(0);
        int ticks = 0;
        while (!s.isFull()) {
            s = OxygenMath.step(s, true, false, false, 0, ++ticks).state();
        }
        assertEquals((int) Math.ceil(OxygenMath.MAX_RESERVE / (double) OxygenMath.RECHARGE_PER_TICK), ticks);
    }

    // ---- fontes ----

    @Test
    void fullSupplyStopsTheDrainAndRecharges() {
        OxygenMath.Result r = OxygenMath.step(state(100), true, false, true, OxygenMath.CONSUMPTION_PER_TICK, 1);
        assertEquals(100 + OxygenMath.RECHARGE_PER_TICK, r.state().reserve());
        assertTrue(r.state().supplied());
        assertEquals(0.0F, r.damage());
    }

    @Test
    void supplyNeverHurtsEvenWithEmptyReserve() {
        OxygenMath.Result r = OxygenMath.step(state(0), true, false, true, OxygenMath.CONSUMPTION_PER_TICK, 0);
        assertEquals(0.0F, r.damage());
    }

    @Test
    void totalSupplySumsSourcesAndIgnoresNegatives() {
        List<IntSupplier> sources = List.of(() -> 1, () -> 2, () -> -5, () -> 0);
        assertEquals(3, OxygenMath.totalSupply(sources));
        assertEquals(0, OxygenMath.totalSupply(List.of()));
    }

    @Test
    void totalSupplySaturatesInsteadOfOverflowing() {
        List<IntSupplier> sources = List.of(() -> Integer.MAX_VALUE, () -> Integer.MAX_VALUE, () -> 10);
        assertEquals(Integer.MAX_VALUE, OxygenMath.totalSupply(sources));
    }

    /** Fonte que conta quantas vezes foi consultada (para provar que as seguintes não gastam estoque à toa). */
    private static IntSupplier counting(int[] calls, int supply) {
        return () -> {
            calls[0]++;
            return supply;
        };
    }

    @Test
    void untilCoveredStopsAskingOnceTheConsumptionIsMet() {
        int[] first = {0};
        int[] second = {0};
        // a primeira fonte (gratuita) já cobre o consumo: a segunda (que gasta tanque) não é consultada
        int total = OxygenMath.totalSupplyUntilCovered(List.of(
                counting(first, OxygenMath.CONSUMPTION_PER_TICK), counting(second, OxygenMath.CONSUMPTION_PER_TICK)));
        assertEquals(OxygenMath.CONSUMPTION_PER_TICK, total);
        assertEquals(1, first[0]);
        assertEquals(0, second[0]);
    }

    @Test
    void untilCoveredAsksTheNextSourceWhenTheFirstIsNotEnough() {
        int[] first = {0};
        int[] second = {0};
        assertEquals(OxygenMath.CONSUMPTION_PER_TICK, OxygenMath.totalSupplyUntilCovered(List.of(
                counting(first, 0), counting(second, OxygenMath.CONSUMPTION_PER_TICK))));
        assertEquals(1, first[0]);
        assertEquals(1, second[0]);
        // fontes negativas valem zero e a lista vazia dá zero
        assertEquals(0, OxygenMath.totalSupplyUntilCovered(List.of(() -> -3)));
        assertEquals(0, OxygenMath.totalSupplyUntilCovered(List.of()));
    }

    // ---- dano ----

    @Test
    void damageOnlyWithEmptyReserveInVacuumWithoutSupply() {
        assertEquals(OxygenMath.DAMAGE_AMOUNT, OxygenMath.step(state(0), true, false, true, 0, 0).damage());
        // ainda com reserva (mesmo no último tick): sem dano
        assertEquals(0.0F, OxygenMath.step(state(OxygenMath.CONSUMPTION_PER_TICK + 1), true, false, true, 0, 0).damage());
        // fora do vácuo: sem dano mesmo com reserva zerada
        assertEquals(0.0F, OxygenMath.step(state(0), true, false, false, 0, 0).damage());
    }

    @Test
    void damageIsPeriodic() {
        int hits = 0;
        int seconds = 5;
        for (int tick = 100; tick < 100 + seconds * 20; tick++) {
            if (OxygenMath.damageAt(0, true, tick) > 0) {
                hits++;
            }
        }
        assertEquals(seconds * 20 / OxygenMath.DAMAGE_INTERVAL_TICKS, hits);
        assertEquals(OxygenMath.DAMAGE_AMOUNT, OxygenMath.damageAt(0, true, OxygenMath.DAMAGE_INTERVAL_TICKS * 7));
        assertEquals(0.0F, OxygenMath.damageAt(0, true, OxygenMath.DAMAGE_INTERVAL_TICKS * 7 + 1));
    }

    @Test
    void damageHandlesNegativeAndHugeTickCounters() {
        assertEquals(OxygenMath.DAMAGE_AMOUNT, OxygenMath.damageAt(0, true, -OxygenMath.DAMAGE_INTERVAL_TICKS));
        assertEquals(0.0F, OxygenMath.damageAt(0, true, -1));
        assertEquals(0.0F, OxygenMath.damageAt(0, true, Integer.MAX_VALUE));
    }

    @Test
    void totalDamageOverTimeMatchesTheInterval() {
        // 10 s de vácuo depois da reserva vazia = 10 golpes de DAMAGE_AMOUNT
        OxygenState s = state(0);
        float total = 0;
        for (int tick = 1; tick <= 200; tick++) {
            OxygenMath.Result r = OxygenMath.step(s, true, false, true, 0, tick);
            s = r.state();
            total += r.damage();
        }
        assertEquals(10 * OxygenMath.DAMAGE_AMOUNT, total, 1.0E-4F);
    }

    // ---- isenções e regra de jogo ----

    @Test
    void exemptPlayersKeepAFullReserveAndTakeNoDamage() {
        OxygenMath.Result r = OxygenMath.step(state(0), true, true, true, 0, 0);
        assertEquals(OxygenMath.MAX_RESERVE, r.state().reserve());
        assertEquals(0.0F, r.damage());
        assertTrue(r.state().enabled());
    }

    @Test
    void disabledSystemRestoresTheReserveAndFlagsTheHudOff() {
        OxygenMath.Result r = OxygenMath.step(state(0), false, false, true, 0, 0);
        assertEquals(OxygenMath.MAX_RESERVE, r.state().reserve());
        assertFalse(r.state().enabled());
        assertEquals(0.0F, r.damage());
    }

    // ---- estado ----

    @Test
    void stateClampsTheReserve() {
        assertEquals(0, new OxygenState(-5, true, false).reserve());
        assertEquals(OxygenMath.MAX_RESERVE, new OxygenState(OxygenMath.MAX_RESERVE + 100, true, false).reserve());
    }

    @Test
    void secondsLeftFollowsTheConsumption() {
        assertEquals(OxygenMath.MAX_RESERVE / (double) (OxygenMath.CONSUMPTION_PER_TICK * 20), OxygenState.FULL.secondsLeft(), 1.0E-9);
        assertEquals(0.0, state(0).secondsLeft(), 1.0E-9);
    }
}
