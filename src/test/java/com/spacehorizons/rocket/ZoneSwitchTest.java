package com.spacehorizons.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.planet.Planets;
import com.spacehorizons.space.flight.FrameBridge.Local;

/** As regras de velocidade da troca de dimensão: entrar devagar (ou à força perto do chão), sair cedo se rápido, histerese. */
class ZoneSwitchTest {
    @Test
    void horizontalSpeedIgnoresTheVerticalComponent() {
        assertEquals(5.0, ZoneSwitch.horizontalSpeed(new Local(0, 0, 0, 3.0, -90.0, 4.0)), 1e-12);
    }

    @Test
    void entersWhenSlowAtAnyHeight() {
        assertTrue(ZoneSwitch.canEnter(Planets.EARTH, 3_900.0, 0.0));
        assertTrue(ZoneSwitch.canEnter(Planets.EARTH, 3_900.0, ZoneSwitch.ENTER_MAX_HORIZONTAL));
        assertFalse(ZoneSwitch.canEnter(Planets.EARTH, 3_900.0, ZoneSwitch.ENTER_MAX_HORIZONTAL + 0.1));
    }

    @Test
    void aFastRocketIsForcedInBelowAQuarterOfTheEnterAltitude() {
        assertEquals(1_000.0, ZoneSwitch.forceEnterHeight(Planets.EARTH), 1e-9);
        assertEquals(250.0, ZoneSwitch.forceEnterHeight(Planets.MOON), 1e-9);
        assertFalse(ZoneSwitch.canEnter(Planets.EARTH, 1_000.1, 90.0));
        assertTrue(ZoneSwitch.canEnter(Planets.EARTH, 1_000.0, 90.0));
        assertTrue(ZoneSwitch.canEnter(Planets.EARTH, -5.0, 90.0));
    }

    @Test
    void leavesEarlyOnlyWhenFastAndHighEnough() {
        assertTrue(ZoneSwitch.shouldLeaveEarly(Planets.EARTH, 1_000.0, 41.0));
        assertFalse(ZoneSwitch.shouldLeaveEarly(Planets.EARTH, 1_000.0, 40.0));
        assertFalse(ZoneSwitch.shouldLeaveEarly(Planets.EARTH, 999.0, 90.0));
        assertTrue(ZoneSwitch.shouldLeaveEarly(Planets.MOON, 250.0, 41.0));
        assertFalse(ZoneSwitch.shouldLeaveEarly(Planets.MOON, 249.0, 41.0));
    }

    @Test
    void thereIsAGapBetweenTheLeaveAndEnterSpeedsSoTheRocketDoesNotFlipFlop() {
        assertTrue(ZoneSwitch.LEAVE_MIN_HORIZONTAL > ZoneSwitch.ENTER_MAX_HORIZONTAL);
        for (double speed = ZoneSwitch.ENTER_MAX_HORIZONTAL + 0.5; speed <= ZoneSwitch.LEAVE_MIN_HORIZONTAL; speed += 0.5) {
            // nesta faixa, nem sai (já está dentro) nem entra (já está fora)
            assertFalse(ZoneSwitch.shouldLeaveEarly(Planets.EARTH, 2_000.0, speed), "sair a " + speed);
            assertFalse(ZoneSwitch.canEnter(Planets.EARTH, 2_000.0, speed), "entrar a " + speed);
        }
    }

    @Test
    void preloadsOnlyAtLoadableSpeeds() {
        assertTrue(ZoneSwitch.shouldPreload(10.0));
        assertTrue(ZoneSwitch.shouldPreload(50.0));
        assertFalse(ZoneSwitch.shouldPreload(50.1));
        assertFalse(ZoneSwitch.shouldPreload(90.0));
    }
}
