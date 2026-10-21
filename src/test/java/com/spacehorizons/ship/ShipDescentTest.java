package com.spacehorizons.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.flight.FlightState;
import com.spacehorizons.space.flight.FrameBridge;
import com.spacehorizons.space.flight.FrameBridge.Local;
import com.spacehorizons.space.flight.V3;
import com.spacehorizons.space.flight.Zones;

/** A descida da nave (fase 2b, parte 1): a decisão de um tick, a recusa com tripulante em pé e o estado virtual -> dimensão do corpo (sem o jogo). */
class ShipDescentTest {
    private static final double EPS = 1e-6;
    private static final double CLOCK = 1000.0;

    /** Nave na Terra a {@code altitude} blocos sobre a superfície, com a velocidade dada (eixos inerciais). */
    private static FlightState onEarth(double altitude, V3 v) {
        double radius = SolarSystem.EARTH.planet().radius();
        return new FlightState(SolarSystem.EARTH, new V3(0.0, radius + altitude, 0.0), v, 100.0, 0.0);
    }

    private static final V3 DOWN = new V3(0.0, -1.0, 0.0);
    private static final V3 UP = new V3(0.0, 1.0, 0.0);

    @Test
    void decisaoSoDesceUmaVezEFloraSoSemPodeDescer() {
        // já começou: não começa outra (vale uma vez só), e a nave segue voando
        assertEquals(ShipDescent.Step.FLY, ShipDescent.decide(true, true, true, true));
        // acima da altitude de entrada: voa no espaço, mesmo cruzando o critério
        assertEquals(ShipDescent.Step.FLY, ShipDescent.decide(false, false, true, true));
        // abaixo, mas com tripulante em pé: segurada na altitude de entrada (D4)
        assertEquals(ShipDescent.Step.FLOOR, ShipDescent.decide(false, true, true, false));
        assertEquals(ShipDescent.Step.FLOOR, ShipDescent.decide(false, true, false, false));
        // abaixo, todos sentados, mas a dimensão do corpo ainda não pode receber: voa (freando no ar)
        assertEquals(ShipDescent.Step.FLY, ShipDescent.decide(false, true, false, true));
        // abaixo, todos sentados e a dimensão pode receber: começa a descida
        assertEquals(ShipDescent.Step.START, ShipDescent.decide(false, true, true, true));
    }

    @Test
    void recusaQuandoHaAlguemEmPe() {
        assertTrue(ShipDescent.mayDescend(0));
        assertFalse(ShipDescent.mayDescend(1));
        assertFalse(ShipDescent.mayDescend(3));
    }

    @Test
    void abaixoDaAltitudeDeEntradaEDescendoComDimensaoQuePodeReceber() {
        double enter = Zones.enterAltitude(SolarSystem.EARTH.planet());
        assertTrue(ShipDescent.below(onEarth(enter - 1000.0, DOWN)));
        assertFalse(ShipDescent.below(onEarth(enter + 1000.0, DOWN)));

        // descendo, a 500 blocos do terreno (abaixo de 600, entra à força qualquer que seja a velocidade)
        assertTrue(ShipDescent.crossed(onEarth(500.0, DOWN), 500.0, 70.0));
        // rápido e alto (2.000 do terreno com 50 de horizontal): o foguete também não entra, a nave freia no ar
        assertFalse(ShipDescent.crossed(onEarth(2000.0, DOWN), 2000.0, 50.0));
        // devagar a 1.000 do terreno: entra (entrada lenta é até 1.200 com horizontal até 25)
        assertTrue(ShipDescent.crossed(onEarth(1000.0, DOWN), 1000.0, 20.0));
        // subindo nunca desce, mesmo com a dimensão pronta
        assertFalse(ShipDescent.crossed(onEarth(500.0, UP), 500.0, 70.0));
    }

    @Test
    void estadoVirtualViraLocalEVoltaSemPerder() {
        FlightState o = onEarth(3000.0, new V3(12.0, -4.0, 30.0));
        Local l = ShipDescent.local(o, CLOCK, 1.0);
        FlightState back = FrameBridge.fromDimension(SolarSystem.EARTH, CLOCK, 1.0, l, o.fuelMb());
        assertEquals(o.r().x(), back.r().x(), EPS * 1e6);
        assertEquals(o.r().y(), back.r().y(), EPS * 1e6);
        assertEquals(o.r().z(), back.r().z(), EPS * 1e6);
        assertEquals(o.v().x(), back.v().x(), EPS * 1e3);
        assertEquals(o.v().y(), back.v().y(), EPS * 1e3);
        assertEquals(o.v().z(), back.v().z(), EPS * 1e3);
    }
}
