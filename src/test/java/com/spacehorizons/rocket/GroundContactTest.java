package com.spacehorizons.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

/** As contas do contato com o chão: extensões da caixa girada e a regra de "tocou". */
class GroundContactTest {
    @Test
    void anUprightBoxKeepsItsHalfSizes() {
        GroundContact.Extents e = GroundContact.extents(new Quaterniond(), 1.0, 3.0, 2.0);
        assertEquals(1.0, e.x(), 1e-12);
        assertEquals(3.0, e.y(), 1e-12);
        assertEquals(2.0, e.z(), 1e-12);
    }

    @Test
    void aQuarterTurnAroundXSwapsTheVerticalAndDepthExtents() {
        GroundContact.Extents e = GroundContact.extents(new Quaterniond().rotationX(Math.PI / 2.0), 1.0, 3.0, 2.0);
        assertEquals(1.0, e.x(), 1e-9);
        assertEquals(2.0, e.y(), 1e-9, "deitado: a altura vira a profundidade");
        assertEquals(3.0, e.z(), 1e-9);
    }

    @Test
    void aTiltedBoxIsTallerThanItsHalfHeightAlongTheVertical() {
        GroundContact.Extents e = GroundContact.extents(new Quaterniond().rotationX(Math.toRadians(30.0)), 1.0, 3.0, 1.0);
        assertTrue(e.y() > 3.0 * Math.cos(Math.toRadians(30.0)));
        assertEquals(3.0 * Math.cos(Math.toRadians(30.0)) + 1.0 * Math.sin(Math.toRadians(30.0)), e.y(), 1e-9);
    }

    @Test
    void theBaseTouchingOrBelowTheSurfaceWhileDescendingIsContact() {
        assertTrue(GroundContact.touching(66.0, 3.0, 63.0, -1.0), "base exatamente na superfície");
        assertTrue(GroundContact.touching(65.0, 3.0, 63.0, -1.0), "afundou");
        assertFalse(GroundContact.touching(66.5, 3.0, 63.0, -1.0), "ainda no ar");
    }

    @Test
    void risingFromTheGroundIsNotAnImpactUnlessItIsDeepInsideTheTerrain() {
        assertFalse(GroundContact.touching(66.0, 3.0, 63.0, 0.4), "a decolagem não bate no chão de onde saiu");
        assertFalse(GroundContact.touching(65.5, 3.0, 63.0, 0.4), "meio bloco abaixo, subindo");
        assertTrue(GroundContact.touching(61.0, 3.0, 63.0, 0.4), "bem abaixo da superfície: bateu de lado numa encosta");
    }

    @Test
    void unknownTerrainNeverTouches() {
        assertFalse(GroundContact.touching(0.0, 3.0, Double.NaN, -50.0));
    }
}
