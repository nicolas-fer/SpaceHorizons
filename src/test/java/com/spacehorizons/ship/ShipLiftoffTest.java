package com.spacehorizons.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.spacehorizons.rocket.Multiblocks;
import com.spacehorizons.rocket.RocketCensus;
import com.spacehorizons.rocket.RocketException;
import com.spacehorizons.rocket.RocketException.Reason;
import com.spacehorizons.rocket.RocketSpecs;

/** Empuxo de decolagem da nave e a recusa sem motor para decolar (sem o jogo). */
class ShipLiftoffTest {
    private static final double EPS = 1e-9;

    @Test
    void soSomaBocaisParaBaixo() throws RocketException {
        // bocal para cima e horizontal não decolam a nave: só os 3 para baixo contam
        ShipLiftoff.Nozzles nozzles = new ShipLiftoff.Nozzles(2, 3, 4);
        assertEquals(3 * ShipSpecs.ENGINE_THRUST, ShipLiftoff.thrust(nozzles, RocketCensus.EMPTY), EPS);
        assertEquals(3 * ShipSpecs.ENGINE_THRUST, ShipLiftoff.require(nozzles, RocketCensus.EMPTY), EPS);
    }

    @Test
    void somaMotoresDeFoguete() throws RocketException {
        // um bocal para baixo mais dois motores de foguete avulsos
        RocketCensus rocket = new RocketCensus(0, 2, 0, 0, 0, 0, 0);
        double expected = ShipSpecs.ENGINE_THRUST + 2 * RocketSpecs.ENGINE_THRUST;
        assertEquals(expected, ShipLiftoff.require(new ShipLiftoff.Nozzles(0, 1, 0), rocket), EPS);
    }

    @Test
    void motorDeFogueteFormadoEntraNoEmpuxo() throws RocketException {
        // um cubo 2x2x2 de motor de foguete, sem bocal de impulso: o empuxo é o da estrutura formada
        RocketCensus rocket = new RocketCensus(0, 8, 0, 0, 0, 0, 0, List.of(),
                List.of(new Multiblocks.Box(0, 0, 0, 2, 2, 2)));
        assertEquals(RocketSpecs.ENGINE_2_THRUST, ShipLiftoff.thrust(new ShipLiftoff.Nozzles(0, 0, 0), rocket), EPS);
        assertEquals(RocketSpecs.ENGINE_2_THRUST, ShipLiftoff.require(new ShipLiftoff.Nozzles(0, 0, 0), rocket), EPS);
    }

    @Test
    void recusaSemMotorParaBaixo() {
        // só bocais para cima e horizontais, e nenhum motor de foguete: não decola
        RocketCensus rocket = new RocketCensus(0, 0, 0, 0, 0, 0, 5);
        RocketException e = assertThrows(RocketException.class,
                () -> ShipLiftoff.require(new ShipLiftoff.Nozzles(3, 0, 5), rocket));
        assertEquals(Reason.NO_LIFTOFF_ENGINE, e.reason());
    }

    @Test
    void recusaSemNenhumMotor() {
        RocketException e = assertThrows(RocketException.class,
                () -> ShipLiftoff.require(new ShipLiftoff.Nozzles(0, 0, 0), RocketCensus.EMPTY));
        assertEquals(Reason.NO_LIFTOFF_ENGINE, e.reason());
    }
}
