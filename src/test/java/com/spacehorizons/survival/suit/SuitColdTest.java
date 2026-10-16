package com.spacehorizons.survival.suit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SuitColdTest {
    /** Ticks para ficar totalmente congelado: o getTicksRequiredToFreeze() do vanilla. */
    private static final int REQUIRED = 140;
    private static final SuitPieces TWO = new SuitPieces(true, true, false, false);

    /** Um tick do jogo: o vanilla desconta o degelo e só então roda o nosso fim de tick. */
    private static int tick(int frozen) {
        int afterVanilla = Math.max(0, frozen - SuitCold.VANILLA_THAW_PER_TICK);
        return SuitCold.nextFrozenTicks(afterVanilla, REQUIRED);
    }

    // ---- quem sofre o frio ----

    @Test
    void exposedInVacuumWithoutTheFullSuit() {
        assertTrue(SuitCold.exposed(true, false, true, SuitPieces.NONE));
        assertTrue(SuitCold.exposed(true, false, true, TWO));
        assertTrue(SuitCold.exposed(true, false, true, new SuitPieces(true, true, true, false)));
    }

    @Test
    void notExposedWithTheFullSuit() {
        assertFalse(SuitCold.exposed(true, false, true, SuitPieces.FULL));
    }

    @Test
    void notExposedInAirCreativeOrWithTheRuleOff() {
        assertFalse(SuitCold.exposed(true, false, false, SuitPieces.NONE), "no ar");
        assertFalse(SuitCold.exposed(true, true, true, SuitPieces.NONE), "criativo, espectador ou morto");
        assertFalse(SuitCold.exposed(false, false, true, SuitPieces.NONE), "regra de jogo desligada");
    }

    // ---- taxa de congelamento ----

    @Test
    void netRateIsOneFreezeTickPerTick() {
        // depois do degelo do vanilla (-2), o nosso fim de tick soma 3: saldo líquido de +1 por tick
        assertEquals(50 + SuitCold.NET_FREEZE_PER_TICK, tick(50));
        assertEquals(1, SuitCold.NET_FREEZE_PER_TICK);
        assertEquals(2, SuitCold.VANILLA_THAW_PER_TICK);
    }

    @Test
    void getsFullyFrozenInAboutSevenSeconds() {
        int frozen = 0;
        int ticks = 0;
        while (frozen < REQUIRED) {
            frozen = tick(frozen);
            ticks++;
            assertTrue(ticks < 200, "nunca congelou");
        }
        // 140 ticks de saldo (7 s), mais os 2 do primeiro tick
        assertTrue(ticks >= 136 && ticks <= 140, "levou " + ticks + " ticks");
    }

    @Test
    void neverGoesPastTheRequiredTicks() {
        int frozen = REQUIRED - 1;
        for (int i = 0; i < 50; i++) {
            frozen = tick(frozen);
            assertTrue(frozen <= REQUIRED);
        }
        assertEquals(REQUIRED, frozen);
        assertEquals(REQUIRED, SuitCold.nextFrozenTicks(REQUIRED, REQUIRED));
        assertEquals(REQUIRED, SuitCold.nextFrozenTicks(10_000, REQUIRED));
    }

    @Test
    void negativeValuesAreTreatedAsZero() {
        assertEquals(SuitCold.NET_FREEZE_PER_TICK + SuitCold.VANILLA_THAW_PER_TICK, SuitCold.nextFrozenTicks(-5, REQUIRED));
    }

    @Test
    void afterTheVanillaThawTheFrozenPlayerIsNotSeenAsFrozenByTheVanilla() {
        // por isso o dano de congelar é nosso: o vanilla só dá dano se achar o jogador congelado depois do degelo
        int atTheEndOfTheTick = SuitCold.nextFrozenTicks(REQUIRED, REQUIRED);
        assertEquals(REQUIRED, atTheEndOfTheTick);
        assertTrue(atTheEndOfTheTick - SuitCold.VANILLA_THAW_PER_TICK < REQUIRED);
    }

    @Test
    void thawsTwiceAsFastOnceProtected() {
        // com o traje completo nada é somado; o degelo do vanilla desconta 2 por tick: 140 ticks sobem em 70
        int frozen = REQUIRED;
        int ticks = 0;
        while (frozen > 0) {
            frozen = Math.max(0, frozen - SuitCold.VANILLA_THAW_PER_TICK);
            ticks++;
        }
        assertEquals(70, ticks);
    }

    // ---- dano ----

    @Test
    void noDamageUntilFullyFrozen() {
        for (int tickCount = 0; tickCount < 200; tickCount++) {
            assertEquals(0.0F, SuitCold.damageAt(REQUIRED - 1, REQUIRED, tickCount));
            assertEquals(0.0F, SuitCold.damageAt(0, REQUIRED, tickCount));
        }
    }

    @Test
    void damageEveryFortyTicksWhenFullyFrozen() {
        int hits = 0;
        for (int tickCount = 0; tickCount < 400; tickCount++) {
            float damage = SuitCold.damageAt(REQUIRED, REQUIRED, tickCount);
            if (damage > 0.0F) {
                assertEquals(0, tickCount % SuitCold.DAMAGE_INTERVAL_TICKS);
                assertEquals(SuitCold.DAMAGE_AMOUNT, damage);
                hits++;
            }
        }
        assertEquals(10, hits);
        assertEquals(40, SuitCold.DAMAGE_INTERVAL_TICKS);
        assertEquals(1.0F, SuitCold.DAMAGE_AMOUNT);
    }

    @Test
    void damageCadenceToleratesNegativeTickCounts() {
        assertEquals(SuitCold.DAMAGE_AMOUNT, SuitCold.damageAt(REQUIRED, REQUIRED, -40));
        assertEquals(0.0F, SuitCold.damageAt(REQUIRED, REQUIRED, -41));
    }
}
