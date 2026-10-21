package com.spacehorizons.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.flight.AttitudeMath.SasMode;
import com.spacehorizons.space.flight.FlightState;
import com.spacehorizons.space.flight.V3;

/** Gatilho único da subida e a conversão do estado de voo da entidade no da nave (sem o jogo). */
class ShipAscentTest {
    private static final double EPS = 1e-9;

    @Test
    void gatilhoSoDisparaUmaVezNaAltitudeDeSaida() {
        // antes da altitude de saída: não converte
        assertFalse(ShipAscent.shouldConvert(true, true, false, false));
        // cruzou e é nave voando: converte
        assertTrue(ShipAscent.shouldConvert(true, true, true, false));
        // já pediu: não pede de novo (o job não é enfileirado a cada tick)
        assertFalse(ShipAscent.shouldConvert(true, true, true, true));
        // foguete comum, ou nave pousada, nunca converte aqui
        assertFalse(ShipAscent.shouldConvert(false, true, true, false));
        assertFalse(ShipAscent.shouldConvert(true, false, true, false));
    }

    @Test
    void estadoDeVooViraEstadoDaNaveSemMudarDeReferencial() {
        FlightState orbit = new FlightState(SolarSystem.EARTH, new V3(1.0e6, 2.0e6, 3.0e6), new V3(-4.0, 5.0, 6.0), 1234.5, 0.0, 0.0);
        Quaterniond attitude = new Quaterniond(0.0, 0.0, 0.0, 1.0);
        V3 spin = new V3(0.1, 0.2, 0.3);

        ShipFlight.State state = ShipAscent.flightState(orbit, attitude, spin, SasMode.OFF);

        assertSame(orbit, state.orbit());
        // o acelerador é cortado na troca: a nave começa com 0 (o piloto religa)
        assertEquals(0.0, state.throttle(), EPS);
        assertEquals(SasMode.OFF, state.sas());
        assertEquals(0.2, state.spin().y(), EPS);
        assertEquals(1.0, state.attitude().w, EPS);
        // a atitude é cópia: mudar a da entidade depois não mexe na da nave
        assertNotSame(attitude, state.attitude());
        attitude.set(0.0, 1.0, 0.0, 0.0);
        assertEquals(1.0, state.attitude().w, EPS);
    }
}
