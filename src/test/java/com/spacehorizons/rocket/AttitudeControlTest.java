package com.spacehorizons.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import com.spacehorizons.space.flight.AttitudeMath;
import com.spacehorizons.space.flight.AttitudeMath.SasMode;
import com.spacehorizons.space.flight.V3;

/** O controle de atitude: taxas limitadas, giro que persiste, SAS estabilizar e prógrado/retrógrado. */
class AttitudeControlTest {
    private static final V3 UP = new V3(0, 1, 0);

    @Test
    void aCommandRampsTheSpinUpWithBoundedAcceleration() {
        AttitudeControl.Result r = AttitudeControl.step(new Quaterniond(), V3.ZERO, SasMode.OFF, 1.0, 0, 0, null);
        assertEquals(AttitudeControl.SPIN_ACCEL, r.spin().x(), 1e-12);
        AttitudeControl.Result next = r;
        for (int i = 0; i < 20; i++) {
            next = AttitudeControl.step(next.attitude(), next.spin(), SasMode.OFF, 1.0, 0, 0, null);
        }
        assertEquals(AttitudeControl.MAX_SPIN, next.spin().x(), 1e-12, "a taxa para no máximo");
    }

    @Test
    void withTheSasOffTheSpinPersistsWithoutInput() {
        V3 spin = new V3(0.03, 0.0, -0.02);
        AttitudeControl.Result r = AttitudeControl.step(new Quaterniond(), spin, SasMode.OFF, 0, 0, 0, null);
        assertEquals(0.03, r.spin().x(), 1e-12);
        assertEquals(-0.02, r.spin().z(), 1e-12);
    }

    @Test
    void stabilizeBrakesTheSpinToZeroAndHoldsTheAttitude() {
        AttitudeControl.Result r = new AttitudeControl.Result(new Quaterniond(), new V3(0.04, 0.03, -0.04));
        for (int i = 0; i < 10; i++) {
            r = AttitudeControl.step(r.attitude(), r.spin(), SasMode.STABILIZE, 0, 0, 0, null);
        }
        assertEquals(0.0, r.spin().length(), 1e-12);
        Quaterniond held = new Quaterniond(r.attitude());
        AttitudeControl.Result again = AttitudeControl.step(r.attitude(), r.spin(), SasMode.STABILIZE, 0, 0, 0, null);
        assertEquals(0.0, held.difference(again.attitude(), new Quaterniond()).angle(), 1e-12);
    }

    @Test
    void anInputOnOneAxisLeavesTheOthersToTheSas() {
        AttitudeControl.Result r = AttitudeControl.step(new Quaterniond(), new V3(0.0, 0.0, 0.03), SasMode.STABILIZE, 1.0, 0, 0, null);
        assertTrue(r.spin().x() > 0.0, "o eixo comandado gira");
        assertEquals(0.02, r.spin().z(), 1e-12, "o livre freia");
    }

    @Test
    void prograde_slewsTheNoseTowardsTheVelocityByAtMostTheMaxRate() {
        // nariz em +Y; alvo a 90 graus: um passo gira no máximo MAX_SPIN
        V3 target = new V3(1, 0, 0);
        AttitudeControl.Result r = AttitudeControl.step(new Quaterniond(), V3.ZERO, SasMode.PROGRADE, 0, 0, 0, target);
        assertEquals(AttitudeControl.MAX_SPIN, AttitudeMath.noseDirection(r.attitude()).angleTo(UP), 1e-9);
        for (int i = 0; i < 100; i++) {
            r = AttitudeControl.step(r.attitude(), r.spin(), SasMode.PROGRADE, 0, 0, 0, target);
        }
        assertEquals(0.0, AttitudeMath.noseDirection(r.attitude()).angleTo(target), 1e-9, "chega e fica");
        assertEquals(0.0, r.spin().length(), 0.0);
    }

    @Test
    void aPilotCommandOverridesTheProgradeHold() {
        V3 target = new V3(1, 0, 0);
        AttitudeControl.Result r = AttitudeControl.step(new Quaterniond(), V3.ZERO, SasMode.PROGRADE, 0, 0, 1.0, target);
        assertTrue(r.spin().y() > 0.0, "rolagem comandada manda");
        assertEquals(0.0, AttitudeMath.noseDirection(r.attitude()).angleTo(UP), 1e-9, "a rolagem não tira o nariz do lugar");
    }

    @Test
    void progradeWithoutATargetActsAsStabilize() {
        AttitudeControl.Result r = AttitudeControl.step(new Quaterniond(), new V3(0.02, 0, 0), SasMode.PROGRADE, 0, 0, 0, null);
        assertEquals(0.01, r.spin().x(), 1e-12);
    }

    @Test
    void theDeadzoneIgnoresTinyInputs() {
        AttitudeControl.Result r = AttitudeControl.step(new Quaterniond(), V3.ZERO, SasMode.OFF, 0.005, 0.005, 0.005, null);
        assertEquals(0.0, r.spin().length(), 0.0);
    }
}
