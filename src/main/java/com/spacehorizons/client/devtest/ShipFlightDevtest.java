package com.spacehorizons.client.devtest;

import java.util.Locale;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.RocketControlPayload;
import com.spacehorizons.rocket.RocketFuelTankBlockEntity;
import com.spacehorizons.ship.ShipCensus;
import com.spacehorizons.ship.ShipCensusResult;
import com.spacehorizons.ship.ShipCensusService;
import com.spacehorizons.ship.ShipFlight;
import com.spacehorizons.ship.ShipFlightTicker;
import com.spacehorizons.ship.ShipKinds;
import com.spacehorizons.ship.ShipRecord;
import com.spacehorizons.ship.ShipRegistry;
import com.spacehorizons.ship.ShipBlockKind;
import com.spacehorizons.ship.ShipYard;
import com.spacehorizons.space.SpaceClock;
import com.spacehorizons.space.flight.AttitudeMath.SasMode;
import com.spacehorizons.space.flight.BodyPhysics;
import com.spacehorizons.space.flight.Orbit;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Cenário {@code shipflight} do autoteste (fase 1c das naves): um "piloto de teste" roda NO SERVIDOR, a cada tick lê o estado de voo virtual
 * da nave e manda o comando como se o cliente mandasse o pacote ({@code ShipFlightTicker.applyControl}), no estilo de {@link FlightDevtest}.
 * Fases: censo contra a contagem célula a célula; coast de 300 ticks (órbita circular estável); arfagem; SAS estabilizar; guinada e SAS prógrado;
 * queima prógrada de 40 ticks (apoastro sobe, combustível confere com Tsiolkovsky); combustível dos tanques de blocos de acordo com o do voo
 * (e um galão "despejado" por fora); estado gravado no registro. Tudo vai para o log com {@code SHIPCHECK PASSOU/FALHOU}.
 */
final class ShipFlightDevtest {
    private static final String TAG = "[AUTOTEST] SHIPFLIGHT ";
    private static boolean registered;
    private static volatile boolean done;
    private static int shipId;
    private static int phase;
    private static int phaseTicks;
    private static long totalTicks;
    private static double rMin;
    private static double rMax;
    private static double[] before;
    private static double fuelBefore;
    private static double speedBefore;
    private static double fuelAtSync;

    private ShipFlightDevtest() {
    }

    static boolean done() {
        return done;
    }

    /** Liga o piloto de teste para a nave {@code id} (a nave de voo já criada). */
    static void begin(Minecraft mc, int id) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            SpaceHorizons.LOGGER.warn("{}sem servidor integrado", TAG);
            return;
        }
        server.execute(() -> {
            shipId = id;
            phase = 0;
            phaseTicks = 0;
            totalTicks = 0;
            done = false;
            if (!registered) {
                registered = true;
                NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> {
                    try {
                        onTick(e.getServer());
                    } catch (RuntimeException ex) {
                        SpaceHorizons.LOGGER.error(TAG + "piloto de teste falhou", ex);
                        done = true;
                    }
                });
            }
            SpaceHorizons.LOGGER.info("{}piloto de teste armado para a nave {}", TAG, id);
        });
    }

    private static void apply(MinecraftServer server, double pitch, double yaw, double roll, double throttle, int flags) {
        ShipFlightTicker.applyControl(shipId, new RocketControlPayload((float) pitch, (float) yaw, (float) roll, (float) throttle, (byte) flags),
                server.overworld().getGameTime(), true);
    }

    private static double[] orbitNumbers(ShipFlightTicker.Live live, double clock) {
        Orbit o = Orbit.of(live.state().orbit(), clock);
        double radius = BodyPhysics.of(live.state().orbit().body()).radius();
        return new double[] {o.apoapsis() - radius, o.periapsis() - radius, o.e(), live.state().orbit().r().length(), live.state().orbit().v().length(),
                Math.toDegrees(o.inclination())};
    }

    private static String fmt(double[] n) {
        return String.format(Locale.ROOT, "apoastro=%.0f periastro=%.0f e=%.2e r=%.0f v=%.3f b/t inclinacao=%.1f graus", n[0], n[1], n[2], n[3], n[4], n[5]);
    }

    private static void next(int p) {
        phase = p;
        phaseTicks = 0;
    }

    private static double bowToVelocityDegrees(ShipFlightTicker.Live live) {
        return Math.toDegrees(ShipFlight.forward(live.state().attitude()).angleTo(live.state().orbit().v().unit()));
    }

    private static void onTick(MinecraftServer server) {
        if (done || shipId == 0) {
            return;
        }
        totalTicks++;
        phaseTicks++;
        ShipFlightTicker.Live live = ShipFlightTicker.live(shipId);
        double clock = SpaceClock.ticks(server.overworld());
        if (live == null) {
            if (totalTicks > 200) {
                ShipDevtest.check("a nave de voo está viva no servidor", false, "sem estado vivo depois de 200 ticks");
                done = true;
            }
            return;
        }
        switch (phase) {
            case 0 -> {
                checkCensus(server, live);
                before = orbitNumbers(live, clock);
                SpaceHorizons.LOGGER.info("{}estado inicial: {} | massa seca={} t empuxo={} combustível={} mB Δv={} b/t", TAG, fmt(before),
                        String.format(Locale.ROOT, "%.2f", live.stats().dryMassT()), live.stats().thrust(), live.state().fuelMb(),
                        String.format(Locale.ROOT, "%.2f", live.stats().deltaV(live.state().fuelMb())));
                rMin = rMax = live.state().orbit().r().length();
                next(1);
            }
            case 1 -> {
                double r = live.state().orbit().r().length();
                rMin = Math.min(rMin, r);
                rMax = Math.max(rMax, r);
                if (phaseTicks >= 300) {
                    double[] n = orbitNumbers(live, clock);
                    ShipDevtest.check("órbita circular estável por 300 ticks (raio varia menos de 0,05 blocos)", rMax - rMin < 0.05,
                            String.format(Locale.ROOT, "variação=%.5f %s", rMax - rMin, fmt(n)));
                    ShipDevtest.check("órbita inicial a 30.000 de altitude, circular", Math.abs(n[0] - 30_000.0) < 1.0 && Math.abs(n[1] - 30_000.0) < 1.0, fmt(n));
                    ShipDevtest.check("sem empuxo o estado não gasta combustível", Math.abs(live.state().fuelMb() - before0Fuel()) < 1.0e-6,
                            "combustível=" + live.state().fuelMb());
                    next(2);
                }
            }
            case 2 -> {
                // arfagem por 20 ticks com o piloto de teste
                if (phaseTicks == 1) {
                    fwdBefore = ShipFlight.forward(live.state().attitude());
                }
                apply(server, 1.0, 0.0, 0.0, 0.0, 0);
                if (phaseTicks >= 20) {
                    double angle = Math.toDegrees(ShipFlight.forward(live.state().attitude()).angleTo(fwdBefore));
                    ShipDevtest.check("arfagem de 20 ticks gira a proa", angle > 5.0, String.format(Locale.ROOT, "proa girou %.1f graus; giro=%s", angle, live.state().spin()));
                    next(3);
                }
            }
            case 3 -> {
                // um pulso de "próximo SAS" (desligado -> estabilizar) e depois comando neutro
                apply(server, 0.0, 0.0, 0.0, 0.0, phaseTicks == 1 ? RocketControlPayload.FLAG_NEXT_SAS : 0);
                if (phaseTicks >= 40) {
                    ShipDevtest.check("SAS estabilizar liga com o pulso e zera o giro", live.state().sas() == SasMode.STABILIZE && live.state().spin().length() < 1.0e-9,
                            "sas=" + live.state().sas() + " giro=" + live.state().spin());
                    next(4);
                }
            }
            case 4 -> {
                // guinada de 25 ticks, depois SAS prógrado (estabilizar -> prógrado)
                if (phaseTicks <= 25) {
                    apply(server, 0.0, 1.0, 0.0, 0.0, 0);
                } else {
                    apply(server, 0.0, 0.0, 0.0, 0.0, phaseTicks == 26 ? RocketControlPayload.FLAG_NEXT_SAS : 0);
                }
                if (phaseTicks == 25) {
                    angle25 = bowToVelocityDegrees(live);
                }
                if (phaseTicks >= 300) {
                    double angle = bowToVelocityDegrees(live);
                    ShipDevtest.check("SAS prógrado leva a proa de " + String.format(Locale.ROOT, "%.1f", angle25) + " graus do prógrado a menos de 0,5",
                            live.state().sas() == SasMode.PROGRADE && angle < 0.5, String.format(Locale.ROOT, "sas=%s proa a %.3f graus da velocidade", live.state().sas(), angle));
                    before = orbitNumbers(live, clock);
                    fuelBefore = live.state().fuelMb();
                    speedBefore = live.state().orbit().v().length();
                    SpaceHorizons.LOGGER.info("{}antes da queima: {} combustível={}", TAG, fmt(before), fuelBefore);
                    next(5);
                }
            }
            case 5 -> {
                // queima prógrada a 100% por 40 ticks
                if (phaseTicks <= 40) {
                    apply(server, 0.0, 0.0, 0.0, 1.0, 0);
                } else {
                    apply(server, 0.0, 0.0, 0.0, 0.0, 0);
                }
                if (phaseTicks == 41) {
                    double[] after = orbitNumbers(live, clock);
                    double burned = fuelBefore - live.state().fuelMb();
                    double expectedMb = 40.0 * live.stats().fullThrottleFuelMbPerTick();
                    double dvExpected = live.stats().exhaustVelocity() * Math.log(live.stats().wetMass(fuelBefore) / live.stats().wetMass(live.state().fuelMb()));
                    double dvMeasured = live.state().orbit().v().length() - speedBefore;
                    SpaceHorizons.LOGGER.info("{}depois da queima: {} combustível={} gasto={} Δv medido={} esperado={}", TAG, fmt(after), live.state().fuelMb(),
                            burned, String.format(Locale.ROOT, "%.3f", dvMeasured), String.format(Locale.ROOT, "%.3f", dvExpected));
                    ShipDevtest.check("a queima prógrada sobe o apoastro em mais de 20.000 blocos", after[0] > before[0] + 20_000.0,
                            String.format(Locale.ROOT, "%.0f -> %.0f", before[0], after[0]));
                    ShipDevtest.check("o periastro quase não muda (queima curta)", after[1] > before[1] - 1_500.0, String.format(Locale.ROOT, "%.0f -> %.0f", before[1], after[1]));
                    ShipDevtest.check("combustível gasto = vazão x ticks", Math.abs(burned - expectedMb) < 1.0, String.format(Locale.ROOT, "gasto=%.2f esperado=%.2f", burned, expectedMb));
                    ShipDevtest.check("Δv medido bate com Tsiolkovsky (3%)", Math.abs(dvMeasured - dvExpected) < 0.03 * dvExpected,
                            String.format(Locale.ROOT, "medido=%.3f esperado=%.3f", dvMeasured, dvExpected));
                    ShipDevtest.check("a nave não foi empurrada para o chão da fase 1", !ShipFlight.onFloor(live.state().orbit()), "r=" + live.state().orbit().r().length());
                    next(6);
                }
            }
            case 6 -> {
                apply(server, 0.0, 0.0, 0.0, 0.0, 0);
                if (phaseTicks == 30) {
                    ShipCensusResult census = ShipCensusService.latest(shipId);
                    long sum = tankSum(server, census);
                    ShipDevtest.check("tanques de blocos acompanham o combustível do voo", Math.abs(sum - live.state().fuelMb()) <= 8.0,
                            String.format(Locale.ROOT, "tanques=%d voo=%.1f", sum, live.state().fuelMb()));
                    fuelAtSync = live.state().fuelMb();
                    // um galão despejado por fora: +1000 mB no primeiro tanque
                    ServerLevel yard = ShipYard.level(server);
                    BlockPos pos = census.tanks().get(0);
                    if (yard.getBlockEntity(pos) instanceof RocketFuelTankBlockEntity tank) {
                        tank.setFuel(tank.fuelMb() + 1000);
                    }
                }
                if (phaseTicks == 60) {
                    double gained = live.state().fuelMb() - fuelAtSync;
                    ShipDevtest.check("combustível despejado num tanque entra no voo (+1000 mB)", Math.abs(gained - 1000.0) <= 8.0, String.format(Locale.ROOT, "ganhou=%.1f", gained));
                    next(7);
                }
            }
            case 7 -> {
                if (phaseTicks == 120) {
                    ShipRecord ship = ShipRegistry.get(server).byId(shipId);
                    var data = ship.flight().orElseThrow();
                    ShipDevtest.check("estado de voo gravado no registro da nave", Math.abs(data.fuelMb() - live.state().fuelMb()) < 1.0 && data.body().equals("earth"),
                            String.format(Locale.ROOT, "gravado=%.1f vivo=%.1f corpo=%s", data.fuelMb(), live.state().fuelMb(), data.body()));
                    ShipDevtest.check("a posição gravada é a de até 100 ticks atrás (a nave anda ~80 b/t)", Math.abs(data.r().get(0) - live.state().orbit().r().x()) < 100 * 100.0,
                            "x gravado=" + data.r().get(0) + " vivo=" + live.state().orbit().r().x());
                    ShipFlightTicker.clearDevtestPilot(shipId);
                    done = true;
                    SpaceHorizons.LOGGER.info("{}fim do piloto de teste em {} ticks", TAG, totalTicks);
                }
            }
            default -> done = true;
        }
    }

    private static double before0Fuel() {
        return before0Fuel;
    }

    private static double before0Fuel;
    private static com.spacehorizons.space.flight.V3 fwdBefore;
    private static double angle25;

    /** Soma, lendo os blocos de verdade, o combustível dos tanques da nave. */
    private static long tankSum(MinecraftServer server, ShipCensusResult census) {
        ServerLevel yard = ShipYard.level(server);
        long sum = 0;
        for (BlockPos pos : census.tanks()) {
            if (yard.getBlockEntity(pos) instanceof RocketFuelTankBlockEntity tank) {
                sum += tank.fuelMb();
            }
        }
        return sum;
    }

    /** O censo do voo contra a contagem célula a célula da caixa (independente da paleta). */
    private static void checkCensus(MinecraftServer server, ShipFlightTicker.Live live) {
        ServerLevel yard = ShipYard.level(server);
        ShipRecord ship = ShipRegistry.get(server).byId(shipId);
        ShipYard.Box box = ship.box();
        int[] counts = new int[ShipBlockKind.values().length];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    counts[ShipKinds.classify(yard.getBlockState(p.set(x, y, z))).ordinal()]++;
                }
            }
        }
        ShipCensus manual = ShipCensus.EMPTY;
        for (ShipBlockKind kind : ShipBlockKind.values()) {
            manual = manual.plus(kind, counts[kind.ordinal()]);
        }
        ShipCensusResult scanned = ShipCensusService.scanNow(server, ship);
        ShipDevtest.check("censo por paleta (lotes) igual à contagem célula a célula", scanned.census().equals(manual), scanned.census() + " contra " + manual);
        ShipDevtest.check("o voo usa esse censo", live.census().equals(manual), live.census() + " contra " + manual);
        ShipDevtest.check("a nave tem 1 ponte, 4 motores, 8 tanques, 3 assentos e 4 eclusas", manual.bridges() == 1 && manual.engines() == 4 && manual.tanks() == 8
                && manual.seats() == 3 && manual.airlocks() == 4, manual.toString());
        before0Fuel = live.state().fuelMb();
        ShipDevtest.check("combustível inicial = 8 tanques de 4.000 mB", Math.abs(before0Fuel - 32_000.0) < 1.0e-9 && scanned.tankFuelMb() == 32_000L,
                "voo=" + before0Fuel + " tanques=" + scanned.tankFuelMb());
    }
}
