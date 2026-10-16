package com.spacehorizons.survival.suit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SuitPiecesTest {
    @Test
    void breathingNeedsHelmetAndChestplate() {
        assertTrue(new SuitPieces(true, true, false, false).canBreathe());
        assertTrue(SuitPieces.FULL.canBreathe());
        assertFalse(SuitPieces.NONE.canBreathe());
        // uma peça só não basta, seja ela qual for
        assertFalse(new SuitPieces(true, false, false, false).canBreathe());
        assertFalse(new SuitPieces(false, true, false, false).canBreathe());
        assertFalse(new SuitPieces(false, false, true, true).canBreathe());
        assertFalse(new SuitPieces(true, false, true, true).canBreathe());
        assertFalse(new SuitPieces(false, true, true, true).canBreathe());
    }

    @Test
    void coldProtectionNeedsAllFourPieces() {
        assertTrue(SuitPieces.FULL.protectsFromCold());
        // faltando qualquer uma das quatro, o frio passa
        assertFalse(new SuitPieces(false, true, true, true).protectsFromCold());
        assertFalse(new SuitPieces(true, false, true, true).protectsFromCold());
        assertFalse(new SuitPieces(true, true, false, true).protectsFromCold());
        assertFalse(new SuitPieces(true, true, true, false).protectsFromCold());
        assertFalse(SuitPieces.NONE.protectsFromCold());
    }

    @Test
    void noSinglePieceIsEnoughForAnything() {
        for (int mask = 0; mask < 16; mask++) {
            SuitPieces pieces = new SuitPieces((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0, (mask & 8) != 0);
            if (pieces.count() <= 1) {
                assertFalse(pieces.canBreathe(), "mask " + mask);
                assertFalse(pieces.protectsFromCold(), "mask " + mask);
            }
            // quem está protegido do frio também respira (as quatro incluem capacete e peitoral)
            if (pieces.protectsFromCold()) {
                assertTrue(pieces.canBreathe(), "mask " + mask);
            }
        }
    }

    @Test
    void countsThePiecesWorn() {
        assertEquals(0, SuitPieces.NONE.count());
        assertEquals(4, SuitPieces.FULL.count());
        assertEquals(2, new SuitPieces(true, true, false, false).count());
        assertEquals(3, new SuitPieces(false, true, true, true).count());
    }
}
