package com.spacehorizons.client.devtest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.joml.Quaterniond;

import com.spacehorizons.planet.PlanetTerrains;
import com.spacehorizons.rocket.FlightMachine;
import com.spacehorizons.rocket.FlightMachine.Command;
import com.spacehorizons.rocket.FlightMachine.Event;
import com.spacehorizons.rocket.FlightMachine.Result;
import com.spacehorizons.rocket.FlightMachine.Snapshot;
import com.spacehorizons.rocket.FlightMachine.ZoneAccess;
import com.spacehorizons.rocket.FlightMode;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.flight.FrameBridge.Local;
import com.spacehorizons.space.flight.RocketStats;

/**
 * Roda a missão inteira SEM o jogo: o {@link MissionPilot} comanda a {@code FlightMachine} (a mesma física, zonas e contato com o chão do
 * servidor) sobre um terreno analítico. É onde a guiagem é ajustada e onde as correções de física e de zonas ficam provadas.
 */
final class MissionSim {
    /** O foguete de teste A do voo: 4 motores, 12 tanques (Δv ~253 cheio). */
    static final RocketStats ROCKET_A = RocketStats.of(4, 12, 1, 2, 1, 3, 4.0);
    static final FlightMachine.Box BOX_A = new FlightMachine.Box(1.5, 3.0, 1.0);
    /** O foguete da missão: 3x3, 9 motores e 45 tanques (Δv ~292 cheio, TWR ~1,8 na Terra). */
    static final RocketStats ROCKET_M = RocketStats.of(9, 45, 1, 2, 1, 8, 9.0);
    static final FlightMachine.Box BOX_M = new FlightMachine.Box(1.5, 4.0, 1.5);

    final RocketStats stats;
    final FlightMachine.Box box;
    final MissionPilot pilot;
    final List<String> log = new ArrayList<>();
    Snapshot snap;
    double clock;
    Local landedLocal;
    Quaterniond landedAttitude = new Quaterniond();
    double landedFuel;
    int tick;
    boolean crashed;
    String crashInfo = "";
    final List<String> events = new ArrayList<>();

    private final ZoneAccess zones = new ZoneAccess() {
        @Override
        public boolean hasSpace() {
            return true;
        }

        @Override
        public boolean hasBody(SolarSystem.Body body) {
            return true;
        }

        @Override
        public double terrainHeight(SolarSystem.Body body, double x, double z) {
            return surface(body, x, z) - 1.0;
        }
    };

    static double surface(SolarSystem.Body body, double x, double z) {
        return PlanetTerrains.get(body.planet()).surfaceHeight((int) Math.floor(x), (int) Math.floor(z)) + 1.0;
    }

    MissionSim(RocketStats stats, FlightMachine.Box box, double clock0, Consumer<String> sink) {
        this.stats = stats;
        this.box = box;
        this.clock = clock0;
        this.landedFuel = stats.fuelCapacityMb();
        this.snap = Snapshot.landed(SolarSystem.EARTH, this.landedFuel);
        double top = surface(SolarSystem.EARTH, 0.0, 0.0);
        this.landedLocal = new Local(0.0, top + box.hy(), 0.0, 0.0, 0.0, 0.0);
        this.pilot = new MissionPilot(stats, box.hy(), MissionSim::surface, line -> {
            String text = "[t=" + this.tick + " relógio=" + (long) this.clock + "] " + line;
            this.log.add(text);
            sink.accept(text);
        });
    }

    /** Recomeça da superfície da Lua (face voltada para a Terra) com {@code fuelMb} de combustível, na fase da espera pousado. */
    void startOnMoon(double fuelMb, double clock0) {
        this.clock = clock0;
        this.landedFuel = fuelMb;
        this.snap = Snapshot.landed(SolarSystem.MOON, fuelMb);
        double x = SolarSystem.MOON.planet().halfCircumference();
        double top = surface(SolarSystem.MOON, x, 0.0);
        this.landedLocal = new Local(x, top + this.box.hy(), 0.0, 0.0, 0.0, 0.0);
        this.pilot.forcePhase(MissionPilot.Phase.MOON_LANDED);
    }

    /** Começa pousado em Marte (a plataforma no ponto (0, 0)) com {@code fuelMb}, como o piloto da decolagem de Marte ({@code orbitTicks} em órbita). */
    void startOnMars(double fuelMb, double clock0, int orbitTicks) {
        this.clock = clock0;
        this.landedFuel = fuelMb;
        this.snap = Snapshot.landed(SolarSystem.MARS, fuelMb);
        double top = surface(SolarSystem.MARS, 0.0, 0.0);
        this.landedLocal = new Local(0.0, top + this.box.hy(), 0.0, 0.0, 0.0, 0.0);
        this.pilot.marsLaunch(orbitTicks);
    }

    /** Faz da missão a de Marte (ver {@link MissionPilot#toMars}). */
    MissionSim mars() {
        this.pilot.toMars();
        return this;
    }

    /** Roda até o fim da missão (ou {@code maxTicks}). */
    void run(int maxTicks) {
        while (this.tick < maxTicks && !this.pilot.finished() && !this.crashed) {
            stepOnce();
        }
    }

    /** Roda até a fase {@code phase} começar (ou {@code maxTicks}). */
    void runUntil(MissionPilot.Phase phase, int maxTicks) {
        while (this.tick < maxTicks && this.pilot.phase().ordinal() < phase.ordinal() && !this.pilot.finished() && !this.crashed) {
            stepOnce();
        }
    }

    /** Gancho de depuração: chamado a cada tick com o comando dado ao foguete (antes do passo). */
    java.util.function.BiConsumer<MissionSim, MissionPilot.Out> trace;

    private void stepOnce() {
        MissionPilot.Out o = this.pilot.step(this.snap, this.clock);
        if (this.trace != null) {
            this.trace.accept(this, o);
        }
        Command cmd = new Command(o.pitch(), o.yaw(), 0.0, o.throttle(), o.parachute(), o.nextSas());
        Result r = FlightMachine.step(this.snap, cmd, this.stats, this.box, this.clock, 1.0, this::top, this.zones, this.landedFuel,
                this.landedLocal, this.landedAttitude);
        this.tick++;
        this.clock += 1.0;
        if (r.event() == Event.CRASH) {
            this.crashed = true;
            this.crashInfo = String.format("BATIDA: vv=%.2f vh=%.2f inclinação=%.1f", r.impactVertical(), r.impactHorizontal(), r.tiltDegrees());
            this.log.add("[t=" + this.tick + "] " + this.crashInfo);
            this.snap = r.next();
            return;
        }
        if (r.event() != Event.NONE) {
            this.events.add("t=" + this.tick + " " + r.event() + (r.toBody() == null ? " (espaço)" : " (" + r.toBody().planet().id().getPath() + ")"));
            this.log.add("[t=" + this.tick + "] EVENTO " + r.event());
        }
        if (r.event() == Event.SOFT_LANDING) {
            this.landedLocal = r.local();
            this.landedAttitude = r.localAttitude();
            this.log.add(String.format("[t=%d] POUSO SUAVE: vv=%.2f vh=%.2f inclinação=%.1f combustível=%.0f", this.tick, r.impactVertical(), r.impactHorizontal(),
                    r.tiltDegrees(), r.next().fuelMb()));
        }
        this.snap = r.next();
        if (this.snap.mode() == FlightMode.LANDED) {
            this.landedFuel = this.snap.fuelMb();
        }
    }

    /** O solo para o contato: a superfície do terreno analítico da dimensão em que o foguete está (a zona do instantâneo atual). */
    private double top(double x, double z, double ex, double ez, double lo, double hi) {
        SolarSystem.Body body = this.snap.zoneBody();
        if (body == null) {
            return Double.NaN;
        }
        double best = Double.NaN;
        for (double dx : new double[] {-ex, 0.0, ex}) {
            for (double dz : new double[] {-ez, 0.0, ez}) {
                double h = surface(body, x + dx, z + dz);
                if (h >= lo - 1.0e-9) {
                    double clipped = Math.min(h, Math.floor(hi) + 1.0);
                    best = Double.isNaN(best) ? clipped : Math.max(best, clipped);
                }
            }
        }
        return best;
    }
}
