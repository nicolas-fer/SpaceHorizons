package com.spacehorizons.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.planet.Planets;
import com.spacehorizons.space.flight.FrameBridge.Local;

/**
 * As regras da troca de dimensão: entrar devagar a 1.200 sobre o terreno (ou à força a 600), sair cedo se rápido e alto,
 * histerese em velocidade e em altura.
 */
class ZoneSwitchTest {
    @Test
    void horizontalSpeedIgnoresTheVerticalComponent() {
        assertEquals(5.0, ZoneSwitch.horizontalSpeed(new Local(0, 0, 0, 3.0, -90.0, 4.0)), 1e-12);
    }

    @Test
    void theEnterHeightsAre1200AndForced600OnEarthAndScaleWithTheBody() {
        assertEquals(1_200.0, ZoneSwitch.enterHeight(Planets.EARTH), 1e-9);
        assertEquals(600.0, ZoneSwitch.forceEnterHeight(Planets.EARTH), 1e-9);
        assertEquals(300.0, ZoneSwitch.enterHeight(Planets.MOON), 1e-9);
        assertEquals(150.0, ZoneSwitch.forceEnterHeight(Planets.MOON), 1e-9);
    }

    @Test
    void aSlowRocketOnlyEntersBelow1200AboveTheTerrain() {
        assertFalse(ZoneSwitch.canEnter(Planets.EARTH, 3_900.0, 0.0), "a 3.900 ainda fica no espaço");
        assertFalse(ZoneSwitch.canEnter(Planets.EARTH, 1_200.1, 0.0));
        assertTrue(ZoneSwitch.canEnter(Planets.EARTH, 1_200.0, 0.0));
        assertTrue(ZoneSwitch.canEnter(Planets.EARTH, 1_200.0, ZoneSwitch.ENTER_MAX_HORIZONTAL));
        assertFalse(ZoneSwitch.canEnter(Planets.EARTH, 1_200.0, ZoneSwitch.ENTER_MAX_HORIZONTAL + 0.1));
    }

    @Test
    void aFastRocketIsForcedInBelow600AboveTheTerrain() {
        assertFalse(ZoneSwitch.canEnter(Planets.EARTH, 600.1, 90.0));
        assertTrue(ZoneSwitch.canEnter(Planets.EARTH, 600.0, 90.0));
        assertTrue(ZoneSwitch.canEnter(Planets.EARTH, -5.0, 90.0));
    }

    @Test
    void leavesEarlyOnlyWhenFastHighAndWellAboveTheTerrain() {
        double h = ZoneSwitch.leaveHeight(Planets.EARTH);
        assertEquals(1_500.0, h, 1e-9);
        assertTrue(ZoneSwitch.shouldLeaveEarly(Planets.EARTH, 1_500.0, h, 41.0));
        assertFalse(ZoneSwitch.shouldLeaveEarly(Planets.EARTH, 1_500.0, h, 40.0), "devagar não sai");
        assertFalse(ZoneSwitch.shouldLeaveEarly(Planets.EARTH, 999.0, h, 90.0), "baixo (altitude) não sai");
        assertFalse(ZoneSwitch.shouldLeaveEarly(Planets.EARTH, 1_500.0, h - 1.0, 90.0), "perto do terreno não sai");
        assertTrue(ZoneSwitch.mayLeaveEarly(Planets.EARTH, 1_000.0, 41.0));
        assertFalse(ZoneSwitch.mayLeaveEarly(Planets.EARTH, 999.0, 41.0));
    }

    @Test
    void thereIsAGapBetweenTheLeaveAndEnterSpeedsSoTheRocketDoesNotFlipFlop() {
        assertTrue(ZoneSwitch.LEAVE_MIN_HORIZONTAL > ZoneSwitch.ENTER_MAX_HORIZONTAL);
        for (double speed = ZoneSwitch.ENTER_MAX_HORIZONTAL + 0.5; speed <= ZoneSwitch.LEAVE_MIN_HORIZONTAL; speed += 0.5) {
            // nesta faixa, nem sai (já está dentro) nem entra (já está fora)
            assertFalse(ZoneSwitch.shouldLeaveEarly(Planets.EARTH, 2_000.0, 2_000.0, speed), "sair a " + speed);
            assertFalse(ZoneSwitch.canEnter(Planets.EARTH, 1_000.0, speed), "entrar a " + speed);
        }
    }

    @Test
    void aRocketForcedInFastDoesNotLeaveRightAway() {
        // entrou à força a 600 sobre o terreno, rápido: a saída antecipada só vale acima de 1.500 sobre o terreno
        for (double height = 0.0; height < ZoneSwitch.leaveHeight(Planets.EARTH); height += 50.0) {
            assertFalse(ZoneSwitch.shouldLeaveEarly(Planets.EARTH, 1_200.0 + height, height, 90.0), "altura " + height);
        }
        // e a altura em que entra (1.200, devagar) fica abaixo da em que sai (1.500, rápido): histerese em altura
        assertTrue(ZoneSwitch.enterHeight(Planets.EARTH) < ZoneSwitch.leaveHeight(Planets.EARTH));
    }

    @Test
    void preloadsOnlyAtLoadableSpeeds() {
        assertTrue(ZoneSwitch.shouldPreload(10.0));
        assertTrue(ZoneSwitch.shouldPreload(50.0));
        assertFalse(ZoneSwitch.shouldPreload(50.1));
        assertFalse(ZoneSwitch.shouldPreload(90.0));
    }
}
