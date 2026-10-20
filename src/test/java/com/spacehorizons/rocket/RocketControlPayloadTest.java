package com.spacehorizons.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Pacote de controle do piloto e o stub de voo que guarda o último comando. */
class RocketControlPayloadTest {
    @Test
    void codecDeRedeIdaEVolta() {
        RocketControlPayload original = new RocketControlPayload(0.5F, -1.0F, 0.25F, 0.75F,
                (byte) (RocketControlPayload.FLAG_PARACHUTE | RocketControlPayload.FLAG_FULL_THROTTLE));
        ByteBuf buf = Unpooled.buffer();
        RocketControlPayload.CODEC.encode(buf, original);
        assertEquals(17, buf.readableBytes(), "4 floats e 1 byte de bits");
        assertEquals(original, RocketControlPayload.CODEC.decode(buf));
    }

    @Test
    void saneamentoLimitaOQueOClienteMandaENaoConfiaEmNaN() {
        RocketControlPayload sujo = new RocketControlPayload(5.0F, -9.0F, Float.NaN, 3.0F, (byte) 0xFF);
        RocketControlPayload limpo = sujo.sanitized();
        assertEquals(1.0F, limpo.pitch());
        assertEquals(-1.0F, limpo.yaw());
        assertEquals(0.0F, limpo.roll());
        assertEquals(1.0F, limpo.throttle());
        assertEquals(0x1F, limpo.flags(), "só os 5 bits conhecidos");
        assertEquals(0.0F, new RocketControlPayload(0, 0, 0, -2.0F, (byte) 0).sanitized().throttle());
        assertEquals(0.0F, new RocketControlPayload(0, 0, 0, Float.POSITIVE_INFINITY, (byte) 0).sanitized().throttle());
    }

    @Test
    void bitsDeComando() {
        RocketControlPayload p = new RocketControlPayload(0, 0, 0, 0, (byte) RocketControlPayload.FLAG_DISASSEMBLE);
        assertTrue(p.has(RocketControlPayload.FLAG_DISASSEMBLE));
        assertFalse(p.has(RocketControlPayload.FLAG_NEXT_SAS));
        assertFalse(RocketControlPayload.NEUTRAL.has(RocketControlPayload.FLAG_CUT_THROTTLE));
    }

    @Test
    void stubDeVooGuardaOUltimoComandoESoltaOMancheSemPacoteHa10Ticks() {
        RocketFlight flight = new RocketFlight();
        assertEquals(FlightMode.LANDED, flight.mode());
        assertEquals(RocketControlPayload.NEUTRAL, flight.control(100));
        RocketControlPayload cmd = new RocketControlPayload(1.0F, 0.0F, 0.0F, 0.5F, (byte) 0);
        flight.applyControl(cmd, 100);
        assertEquals(cmd, flight.control(100));
        assertEquals(cmd, flight.control(110), "ainda vale no décimo tick");
        assertEquals(new RocketControlPayload(0.0F, 0.0F, 0.0F, 0.5F, (byte) 0), flight.control(111), "11 ticks sem pacote: manche solto, acelerador mantido");
        flight.applyControl(new RocketControlPayload(9.0F, 0, 0, 2.0F, (byte) 0), 120);
        assertEquals(1.0F, flight.control(120).pitch(), "o comando é saneado ao guardar");
    }

    @Test
    void modoDeVooVoltaDoByteEIdDesconhecidoViraPousado() {
        for (FlightMode mode : FlightMode.values()) {
            assertEquals(mode, FlightMode.byId(mode.id()));
        }
        assertEquals(FlightMode.LANDED, FlightMode.byId((byte) 99));
    }
}
