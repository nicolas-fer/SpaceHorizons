package com.spacehorizons.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** O comando do piloto no servidor: saneamento, tempo limite e bordas dos bits de ação. */
class RocketFlightControlTest {
    private static RocketControlPayload with(int flags) {
        return new RocketControlPayload(0.0F, 0.0F, 0.0F, 0.5F, (byte) flags);
    }

    @Test
    void aHeldActionBitIsOnlyOneEdgeNotOnePerHeartbeat() {
        RocketFlight flight = new RocketFlight();
        flight.applyControl(with(RocketControlPayload.FLAG_NEXT_SAS), 0);
        assertEquals(RocketControlPayload.FLAG_NEXT_SAS, flight.takeEdges());
        flight.applyControl(with(RocketControlPayload.FLAG_NEXT_SAS), 5);
        flight.applyControl(with(RocketControlPayload.FLAG_NEXT_SAS), 10);
        assertEquals(0, flight.takeEdges(), "o bit continua ligado: sem nova borda");
        flight.applyControl(with(0), 11);
        flight.applyControl(with(RocketControlPayload.FLAG_NEXT_SAS), 12);
        assertEquals(RocketControlPayload.FLAG_NEXT_SAS, flight.takeEdges(), "soltou e apertou de novo: nova borda");
    }

    @Test
    void edgesAccumulateUntilTheTickConsumesThem() {
        RocketFlight flight = new RocketFlight();
        flight.applyControl(with(RocketControlPayload.FLAG_NEXT_SAS), 0);
        flight.applyControl(with(RocketControlPayload.FLAG_NEXT_SAS | RocketControlPayload.FLAG_DISASSEMBLE), 1);
        assertEquals(RocketControlPayload.FLAG_NEXT_SAS | RocketControlPayload.FLAG_DISASSEMBLE, flight.takeEdges());
        assertEquals(0, flight.takeEdges(), "consumidas");
    }

    @Test
    void theSticksGoNeutralAfterTheTimeoutButTheThrottleStays() {
        RocketFlight flight = new RocketFlight();
        flight.applyControl(new RocketControlPayload(1.0F, -1.0F, 0.5F, 0.5F, (byte) RocketControlPayload.FLAG_FULL_THROTTLE), 100);
        assertEquals(0.5F, flight.control(100 + RocketFlight.CONTROL_TIMEOUT_TICKS).throttle());
        assertEquals(1.0F, flight.control(100 + RocketFlight.CONTROL_TIMEOUT_TICKS).pitch(), "ainda dentro do tempo limite");
        RocketControlPayload late = flight.control(101 + RocketFlight.CONTROL_TIMEOUT_TICKS);
        assertEquals(new RocketControlPayload(0.0F, 0.0F, 0.0F, 0.5F, (byte) 0), late, "manche e bits de ação soltos; o acelerador fica");
        assertEquals(0.5F, flight.control(100_000).throttle(), "um cliente lento não zera o motor");
    }

    @Test
    void aStaleControlWithoutThrottleIsNeutral() {
        RocketFlight flight = new RocketFlight();
        flight.applyControl(new RocketControlPayload(1.0F, 0.0F, 0.0F, 0.0F, (byte) 0), 100);
        assertEquals(RocketControlPayload.NEUTRAL, flight.control(101 + RocketFlight.CONTROL_TIMEOUT_TICKS));
    }

    @Test
    void withoutAPilotTheThrottleIsDroppedAfterTheGrace() {
        RocketFlight flight = new RocketFlight();
        flight.applyControl(with(0), 100);
        for (int i = 0; i < RocketFlight.PILOT_GRACE_TICKS; i++) {
            flight.trackPilot(false);
        }
        assertEquals(0.5F, flight.control(100).throttle(), "a troca de dimensão deixa alguns ticks sem passageiro");
        flight.trackPilot(false);
        assertEquals(RocketControlPayload.NEUTRAL, flight.control(100), "sem piloto: o motor corta");
    }

    @Test
    void aPresentPilotResetsTheGraceCount() {
        RocketFlight flight = new RocketFlight();
        flight.applyControl(with(0), 100);
        for (int i = 0; i < RocketFlight.PILOT_GRACE_TICKS; i++) {
            flight.trackPilot(false);
        }
        flight.trackPilot(true);
        for (int i = 0; i < RocketFlight.PILOT_GRACE_TICKS; i++) {
            flight.trackPilot(false);
        }
        assertEquals(0.5F, flight.control(100).throttle());
    }

    @Test
    void theClientIsNotTrusted() {
        RocketFlight flight = new RocketFlight();
        flight.applyControl(new RocketControlPayload(9.0F, Float.NaN, -9.0F, 4.0F, (byte) 0xFF), 0);
        RocketControlPayload c = flight.control(0);
        assertEquals(1.0F, c.pitch());
        assertEquals(0.0F, c.yaw());
        assertEquals(-1.0F, c.roll());
        assertEquals(1.0F, c.throttle());
        assertEquals(0x1F, c.flags());
    }

    @Test
    void anUninitializedFlightIsLanded() {
        assertEquals(FlightMode.LANDED, new RocketFlight().mode());
    }
}
