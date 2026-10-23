package com.spacehorizons.client.devtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * A viagem a Marte rodada sem o jogo: Terra, órbita baixa, janela e injeção, voo interplanetário com correções, esfera de Marte, captura,
 * descida com paraquedas e pouso. A mesma {@code FlightMachine} (física, esferas encadeadas, zonas, contato) e o piloto do autoteste
 * {@code marstrip}.
 */
class MarsSimTest {
    /** Relógio de lançamento: a janela de Marte (fase entre ~32 e ~69 graus) abre ~8.500 ticks depois do zero e fecha ~61.000. */
    static final double CLOCK = 20_000;

    @Test
    void theMissionRocketFliesToMarsAndLandsSoftly() {
        MissionSim sim = new MissionSim(MissionSim.ROCKET_M, MissionSim.BOX_M, CLOCK, line -> System.out.println(line));
        sim.mars().run(400_000);
        String why = sim.crashInfo + " " + sim.events + " fase=" + sim.pilot.phase() + " t=" + sim.tick + "\n"
                + String.join("\n", sim.log.subList(Math.max(0, sim.log.size() - 6), sim.log.size()));
        assertFalse(sim.crashed, why);
        assertEquals(MissionPilot.Phase.DONE, sim.pilot.phase(), why);
        List<String> events = sim.events.stream().map(e -> e.substring(e.indexOf(' ') + 1)).toList();
        assertEquals(List.of("LIFTOFF (earth)", "LEAVE_ZONE (espaço)", "ENTER_ZONE (mars)", "SOFT_LANDING (mars)"), events, sim.events.toString());
        double available = MissionSim.ROCKET_M.deltaV(MissionSim.ROCKET_M.fuelCapacityMb());
        double used = MissionSim.ROCKET_M.exhaustVelocity()
                * Math.log(MissionSim.ROCKET_M.wetMass(MissionSim.ROCKET_M.fuelCapacityMb()) / MissionSim.ROCKET_M.wetMass(sim.snap.fuelMb()));
        assertTrue(used < available - 10.0, "Δv gasto " + used + " de " + available);
    }
}
