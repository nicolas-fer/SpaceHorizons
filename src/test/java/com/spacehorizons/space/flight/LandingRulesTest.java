package com.spacehorizons.space.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.flight.LandingRules.Outcome;

class LandingRulesTest {
    @Test
    void gentleContactIsASoftLanding() {
        assertEquals(Outcome.SOFT_LANDING, LandingRules.contact(0.5, 0.1, 3));
        assertEquals(Outcome.SOFT_LANDING, LandingRules.contact(0.0, 0.0, 0));
    }

    @Test
    void limitsAreInclusive() {
        assertEquals(Outcome.SOFT_LANDING, LandingRules.contact(1.0, 0.5, 15));
        assertEquals(Outcome.SOFT_LANDING, LandingRules.contact(-1.0, 0.5, 15));
    }

    @Test
    void anyLimitExceededIsACrash() {
        assertEquals(Outcome.CRASH, LandingRules.contact(1.01, 0.1, 3));
        assertEquals(Outcome.CRASH, LandingRules.contact(0.5, 0.51, 3));
        assertEquals(Outcome.CRASH, LandingRules.contact(0.5, 0.1, 15.1));
        assertEquals(Outcome.CRASH, LandingRules.contact(-3.0, 0.0, 0));
    }

    @Test
    void theSignOfTheVerticalSpeedDoesNotMatter() {
        assertEquals(LandingRules.contact(0.9, 0.2, 5), LandingRules.contact(-0.9, 0.2, 5));
        assertEquals(LandingRules.contact(2.0, 0.2, 5), LandingRules.contact(-2.0, 0.2, 5));
    }

    @Test
    void designLimitsAre20BlocksPerSecondVertically() {
        // 1,0 b/t = 20 b/s vertical e 0,5 b/t = 10 b/s horizontal
        assertEquals(20.0, FlightConstants.LANDING_MAX_VERTICAL * 20, 0.0);
        assertEquals(10.0, FlightConstants.LANDING_MAX_HORIZONTAL * 20, 0.0);
        assertEquals(15.0, FlightConstants.LANDING_MAX_TILT_DEG, 0.0);
    }

    @Test
    void parachuteTerminalSpeedIsASoftLanding() {
        assertEquals(Outcome.SOFT_LANDING, LandingRules.contact(FlightConstants.PARACHUTE_TERMINAL_SPEED, 0, 0));
    }

    @Test
    void impactDamageGrowsWithSpeedLikeAFall() {
        assertEquals(0.0, LandingRules.impactDamage(0.5), 0.0);
        assertTrue(LandingRules.impactDamage(2.0) > 0);
        assertTrue(LandingRules.impactDamage(4.0) > LandingRules.impactDamage(2.0));
        // 3 b/t equivalem a uma queda de 56 blocos: mortal
        assertEquals(3.0 * 3.0 / 0.16 - 3.0, LandingRules.impactDamage(3.0), 1e-12);
        assertTrue(LandingRules.impactDamage(3.0) > 20);
        assertEquals(5.0, LandingRules.impactSpeed(3, 4), 1e-12);
    }
}
