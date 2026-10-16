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
    void theControlGoesNeutralAfterTheTimeout() {
        RocketFlight flight = new RocketFlight();
        flight.applyControl(with(RocketControlPayload.FLAG_FULL_THROTTLE), 100);
        assertEquals(0.5F, flight.control(100 + RocketFlight.CONTROL_TIMEOUT_TICKS).throttle());
        assertEquals(RocketControlPayload.NEUTRAL, flight.control(101 + RocketFlight.CONTROL_TIMEOUT_TICKS));
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
