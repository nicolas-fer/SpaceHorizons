package com.spacehorizons.client.devtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.flight.RocketStats;

/**
 * A missão completa (Terra, órbita baixa, TLI, captura, pouso na Lua, decolagem, volta, reentrada, pouso na Terra) rodada sem o jogo, com a
 * {@code FlightMachine} (física, zonas e contato com o chão do servidor) e o {@link MissionPilot} do cenário {@code mission} do autoteste.
 */
class MissionSimTest {
    /** Relógio de lançamento em que a Lua cruza o plano do equador perto da chegada (ver {@code AutoTest.MISSION_CLOCK}). */
    private static final double CLOCK = 68_300;

    private static double spent(RocketStats stats, double fuelFrom, double fuelTo) {
        return stats.exhaustVelocity() * Math.log(stats.wetMass(fuelFrom) / stats.wetMass(fuelTo));
    }

    @Test
    void theMissionRocketFliesTheWholeMissionAndLandsSoftlyOnBothBodies() {
        MissionSim sim = new MissionSim(MissionSim.ROCKET_M, MissionSim.BOX_M, CLOCK, line -> { });
        sim.run(120_000);
        String why = sim.crashInfo + " " + sim.events + " fase=" + sim.pilot.phase() + "\n" + String.join("\n", sim.log.subList(Math.max(0, sim.log.size() - 6), sim.log.size()));
        assertFalse(sim.crashed, why);
        assertEquals(MissionPilot.Phase.DONE, sim.pilot.phase(), why);
        // a sequência de eventos: sobe, sai para o espaço, entra na Lua e pousa, decola, sai, volta, entra na Terra e pousa
        List<String> events = sim.events.stream().map(e -> e.substring(e.indexOf(' ') + 1)).toList();
        assertEquals(List.of("LIFTOFF (earth)", "LEAVE_ZONE (espaço)", "ENTER_ZONE (moon)", "SOFT_LANDING (moon)", "LIFTOFF (moon)", "LEAVE_ZONE (espaço)",
                "ENTER_ZONE (earth)", "SOFT_LANDING (earth)"), events, sim.events.toString());
        // o Δv gasto cabe no do foguete, com margem
        double available = MissionSim.ROCKET_M.deltaV(MissionSim.ROCKET_M.fuelCapacityMb());
        double used = spent(MissionSim.ROCKET_M, MissionSim.ROCKET_M.fuelCapacityMb(), sim.snap.fuelMb());
        assertTrue(used < available - 10.0, "Δv gasto " + used + " de " + available);
        assertTrue(used > 240.0, "a missão não pode custar menos que a soma das fases: " + used);
        // a missão inteira leva menos de 60.000 ticks
        assertTrue(sim.tick < 60_000, "ticks = " + sim.tick);
    }

    @Test
    void theTestRocketADoesNotHaveTheDeltaVForTheRoundTrip() {
        // o foguete A (Δv 253 cheio): sobe, vai à Lua e pousa lá, mas não fecha a volta (o piloto desiste sem combustível ou o pouso na Terra bate)
        MissionSim sim = new MissionSim(MissionSim.ROCKET_A, MissionSim.BOX_A, CLOCK, line -> { });
        sim.run(120_000);
        boolean finishedSoftly = sim.pilot.phase() == MissionPilot.Phase.DONE && !sim.crashed;
        assertFalse(finishedSoftly, "o foguete A não deveria completar a ida e volta: " + sim.events);
        assertNotEquals(MissionPilot.Phase.PAD, sim.pilot.phase());
    }
}
