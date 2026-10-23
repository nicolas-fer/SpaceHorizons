package com.spacehorizons.client.devtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import com.spacehorizons.rocket.FlightMachine;
import com.spacehorizons.rocket.FlightMachine.Command;
import com.spacehorizons.rocket.FlightMachine.Event;
import com.spacehorizons.rocket.FlightMachine.Result;
import com.spacehorizons.rocket.FlightMachine.Snapshot;
import com.spacehorizons.rocket.FlightMode;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.flight.BodyPhysics;
import com.spacehorizons.space.flight.FlightIntegrator;
import com.spacehorizons.space.flight.FlightState;
import com.spacehorizons.space.flight.FrameBridge.Local;
import com.spacehorizons.space.flight.Control;
import com.spacehorizons.space.flight.V3;

/**
 * Decolar de Marte, subir ao espaço, ficar em órbita de Marte e descer de volta, sem o jogo (a mesma {@code FlightMachine}): o que o voo a partir de
 * Marte tem de fazer com Marte na física do sistema solar, e o que NÃO pode fazer (física sem combustível, empuxo ignorado).
 */
class MarsLaunchSimTest {
    private static final double CLOCK = 6_000;

    @Test
    void theRocketLeavesMarsOrbitsItAndLandsBack() {
        MissionSim sim = new MissionSim(MissionSim.ROCKET_M, MissionSim.BOX_M, CLOCK, line -> { });
        sim.startOnMars(MissionSim.ROCKET_M.fuelCapacityMb(), CLOCK, 800);
        sim.run(30_000);
        String why = sim.crashInfo + " " + sim.events + " fase=" + sim.pilot.phase() + " t=" + sim.tick + "\n"
                + String.join("\n", sim.log.subList(Math.max(0, sim.log.size() - 6), sim.log.size()));
        assertFalse(sim.crashed, why);
        assertEquals(MissionPilot.Phase.DONE, sim.pilot.phase(), why);
        List<String> events = sim.events.stream().map(e -> e.substring(e.indexOf(' ') + 1)).toList();
        assertEquals(List.of("LIFTOFF (mars)", "LEAVE_ZONE (espaço)", "ENTER_ZONE (mars)", "SOFT_LANDING (mars)"), events, sim.events.toString());
        // sobrou combustível (a subida e a volta custam ~100 b/t dos 292) e o Δv da subida ao espaço de Marte é pequeno (gravidade 0,38)
        assertTrue(sim.snap.fuelMb() > 50_000.0, "combustível restante = " + sim.snap.fuelMb());
    }

    private static Result step(Snapshot s, Command c, double fuel, Local at) {
        return FlightMachine.step(s, c, MissionSim.ROCKET_M, MissionSim.BOX_M, CLOCK, 1.0, (x, z, ex, ez, lo, hi) -> at.y() - MissionSim.BOX_M.hy(),
                new FlightMachine.ZoneAccess() {
                    @Override
                    public boolean hasSpace() {
                        return true;
                    }

                    @Override
                    public boolean hasBody(SolarSystem.Body body) {
                        return true;
                    }
                }, fuel, at, new Quaterniond());
    }

    @Test
    void withFuelTheEngineLiftsTheRocketOffMarsAtOnce() {
        double top = MissionSim.surface(SolarSystem.MARS, 0.0, 0.0);
        Local pad = new Local(0.0, top + MissionSim.BOX_M.hy(), 0.0, 0.0, 0.0, 0.0);
        Snapshot landed = Snapshot.landed(SolarSystem.MARS, 100_000.0);
        Result r = step(landed, new Command(0.0, 0.0, 0.0, 1.0, false, false), 100_000.0, pad);
        assertEquals(Event.LIFTOFF, r.event());
        assertEquals(FlightMode.FLIGHT, r.next().mode());
        assertEquals(1.0, r.next().throttle(), 0.0);
        assertTrue(r.next().state().fuelMb() <= 100_000.0);
    }

    @Test
    void withoutFuelNothingLiftsAndNothingBlowsUp() {
        double top = MissionSim.surface(SolarSystem.MARS, 0.0, 0.0);
        Local pad = new Local(0.0, top + MissionSim.BOX_M.hy(), 0.0, 0.0, 0.0, 0.0);
        Snapshot s = Snapshot.landed(SolarSystem.MARS, 0.0);
        for (int i = 0; i < 200; i++) {
            Result r = step(s, new Command(0.0, 0.0, 0.0, 1.0, false, false), 0.0, pad);
            assertEquals(Event.NONE, r.event());
            assertEquals(FlightMode.LANDED, r.next().mode());
            assertEquals(pad.y(), r.local().y(), 0.0);
            assertEquals(0.0, r.local().vy(), 0.0);
            s = r.next();
        }
        // em voo com o tanque vazio (acabou o combustível a meio da subida): só a gravidade de Marte age, a energia se conserva e a velocidade não passa
        // da que a queda de onde está dá (nada de "TWR 274" virando aceleração)
        BodyPhysics mars = BodyPhysics.of(SolarSystem.MARS);
        FlightState st = new FlightState(SolarSystem.MARS, new V3(mars.radius() + 3_000.0, 0.0, 0.0), new V3(60.0, 0.0, 30.0), 0.0, 0.0);
        double energy = 0.5 * st.v().lengthSquared() - mars.mu() / st.r().length();
        for (int i = 0; i < 5_000; i++) {
            st = FlightIntegrator.step(st, new Control(1.0, new V3(1.0, 0.0, 0.0), false), MissionSim.ROCKET_M, CLOCK + i);
            assertEquals(0.0, st.fuelMb(), 0.0);
            double now = 0.5 * st.v().lengthSquared() - mars.mu() / st.r().length();
            assertTrue(Math.abs(now - energy) < 5.0, "energia derivou em " + i + ": " + (now - energy));
            assertTrue(st.v().length() < 100.0, "velocidade = " + st.v().length());
        }
    }
}
