package com.spacehorizons.space.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.spacehorizons.planet.PlanetTerrains;
import com.spacehorizons.space.SolarSystem;

import org.junit.jupiter.api.Test;

/**
 * O piloto automático alterna entre a dimensão do corpo e a de espaço pela regra {@link Autopilot#wantSpace}: simulando o plano inteiro
 * com a mesma regra, o jogador sai do corpo de origem UMA vez, volta ao corpo de destino UMA vez (antes de chegar) e nunca troca de
 * dimensão no meio (a primeira versão ia e vinha na chegada, porque a altitude de saída fica abaixo da de entrada).
 */
class FlightGuideSpaceModeTest {
    private static void run(SolarSystem.Body from, SolarSystem.Body target, double startAltitude) {
        double[] facing = new double[2];
        TravelMath.facingPoint(target, facing);
        int surface = PlanetTerrains.get(target.planet()).surfaceHeight((int) Math.floor(facing[0]), (int) Math.floor(facing[1]));
        FlightGuide guide = new FlightGuide(from, 0.0, from.planet().seaLevel() + startAltitude, 0.0, 6000.0, target, facing[0],
                surface + Autopilot.TARGET_ALTITUDE, facing[1], body -> true);
        boolean inSpace = false;
        int switches = 0;
        for (int tick = 0; tick < 40_000; tick++) {
            FlightGuide.Step step = guide.tick(6000.0 + tick, 0.0F, 0.0F);
            SolarSystem.Body where = step.state().target();
            double altitude = step.state().y() - where.planet().seaLevel();
            if (step.arrived()) {
                assertTrue(!inSpace, "chegou a " + target.planet().id() + " ainda estacionado no espaco");
                assertEquals(2, switches, "trocas de dimensao: sai uma vez e volta uma vez");
                return;
            }
            boolean want = Autopilot.wantSpace(inSpace, where, target, altitude);
            if (want != inSpace) {
                switches++;
                inSpace = want;
            }
        }
        throw new AssertionError("o voo nao chegou em 40.000 ticks");
    }

    @Test
    void terraParaLuaSaiUmaVezEVoltaUmaVez() {
        run(SolarSystem.EARTH, SolarSystem.MOON, 200.0);
    }

    @Test
    void luaParaTerraSaiUmaVezEVoltaUmaVez() {
        run(SolarSystem.MOON, SolarSystem.EARTH, 300.0);
    }
}
