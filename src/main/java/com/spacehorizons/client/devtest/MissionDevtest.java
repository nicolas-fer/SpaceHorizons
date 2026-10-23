package com.spacehorizons.client.devtest;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrains;
import com.spacehorizons.rocket.FlightMachine.Snapshot;
import com.spacehorizons.rocket.RocketControlPayload;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceClock;
import com.spacehorizons.space.flight.FlightState;
import com.spacehorizons.space.travel.TravelTransfers;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Cenário {@code mission} do autoteste: a MISSÃO COMPLETA (Terra → órbita baixa → TLI → captura na Lua → pouso → decolagem → volta →
 * reentrada → pouso) com o foguete de verdade, no servidor. O {@link MissionPilot} faz o que o jogador faria: lê o instantâneo de voo
 * do foguete e manda o comando de controle (como se o cliente mandasse o pacote). A montagem do foguete é do {@link FlightDevtest}
 * (voo {@code M}); aqui ficam o piloto, o acoplamento com o mundo e os marcos ({@code phase_<FASE>}) que o cliente espera para tirar os prints.
 */
final class MissionDevtest {
    private static final String TAG = "[AUTOTEST] MISSION ";

    private static boolean registered;
    private static boolean armed;
    private static @Nullable MissionPilot pilot;
    private static MissionPilot.Phase lastPhase = MissionPilot.Phase.PAD;
    private static MissionPilot.@Nullable Out current;
    private static int ticks;
    private static double lastClock = Double.NaN;
    private static long startNanos;
    private static double msptSum;
    private static double msptMax;
    private static int msptCount;
    private static long tickStartNanos;

    private MissionDevtest() {
    }

    /** Libera o piloto da missão (o foguete já está montado e o jogador sentado). */
    static void go(Minecraft mc) {
        goFrom(mc, null);
    }

    /**
     * O mesmo piloto a partir de uma fase ({@code null} = do começo): a descida da nave (shipdescent) usa {@code EARTH_LANDING} com a nave já
     * na atmosfera. O foguete (ou a nave) já está no ar ou no chão e o jogador sentado.
     */
    static void goFrom(Minecraft mc, MissionPilot.@Nullable Phase from) {
        goFrom(mc, from, false);
    }

    /** O piloto da viagem a Marte (cenário {@code marstrip}): depois da órbita baixa segue as fases {@code MARS_*}. */
    static void goMars(Minecraft mc) {
        goFrom(mc, null, true);
    }

    /** O piloto da decolagem de Marte, órbita e descida de volta (cenário {@code marsorbit}); fica {@code orbitTicks} ticks em órbita. */
    static void goMarsLaunch(Minecraft mc, int orbitTicks) {
        marsLaunchTicks = orbitTicks;
        goFrom(mc, null, false);
    }

    private static int marsLaunchTicks = -1;

    private static void goFrom(Minecraft mc, MissionPilot.@Nullable Phase from, boolean mars) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            SpaceHorizons.LOGGER.warn("{}sem servidor integrado", TAG);
            return;
        }
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            if (!(player.getVehicle() instanceof RocketEntity rocket)) {
                SpaceHorizons.LOGGER.error("{}o jogador não está no foguete", TAG);
                FlightDevtest.mark("failed");
                return;
            }
            double hy = rocket.portrait().sizeY / 2.0;
            pilot = new MissionPilot(rocket.stats(), hy, MissionDevtest::surfaceY, line -> SpaceHorizons.LOGGER.info(TAG + line));
            if (mars) {
                pilot.toMars();
            }
            if (marsLaunchTicks >= 0) {
                pilot.marsLaunch(marsLaunchTicks);
                marsLaunchTicks = -1;
            }
            if (from != null) {
                pilot.forcePhase(from);
            }
            ticks = 0;
            lastClock = Double.NaN;
            lastPhase = from != null ? from : MissionPilot.Phase.PAD;
            current = null;
            startNanos = System.nanoTime();
            SpaceHorizons.LOGGER.info("{}PARTIDA: relógio={} massa seca={} t, Δv cheio={} b/t, empuxo={}, altura da caixa={}", TAG, SpaceClock.ticks(player.level()),
                    String.format(Locale.ROOT, "%.2f", rocket.stats().dryMassT()), String.format(Locale.ROOT, "%.2f", rocket.stats().deltaV(rocket.fuelMb())),
                    rocket.stats().thrust(), hy * 2.0);
            armed = true;
            if (!registered) {
                registered = true;
                NeoForge.EVENT_BUS.addListener(ServerTickEvent.Pre.class, e -> {
                    tickStartNanos = System.nanoTime();
                    apply(e.getServer());
                });
                NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> {
                    try {
                        onTick(e.getServer());
                    } catch (RuntimeException ex) {
                        SpaceHorizons.LOGGER.error(TAG + "piloto de teste falhou", ex);
                    }
                });
            }
        });
    }

    /** Antes do passo: o comando do piloto volta a valer (o cliente real também manda o pacote dele, com o acelerador que ele guarda). */
    private static void apply(MinecraftServer server) {
        MissionPilot.Out out = current;
        if (!armed || out == null || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        if (server.getPlayerList().getPlayers().get(0).getVehicle() instanceof RocketEntity rocket) {
            int flags = (out.parachute() ? RocketControlPayload.FLAG_PARACHUTE : 0) | (out.nextSas() ? RocketControlPayload.FLAG_NEXT_SAS : 0);
            rocket.applyControl(new RocketControlPayload((float) out.pitch(), (float) out.yaw(), 0.0F, (float) out.throttle(), (byte) flags));
            if (out.nextSas()) {
                // uma borda só: o bit cai no pacote seguinte
                current = new MissionPilot.Out(out.pitch(), out.yaw(), out.throttle(), out.parachute(), false);
            }
        }
    }

    private static void onTick(MinecraftServer server) {
        MissionPilot p = pilot;
        if (!armed || p == null || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        double ms = (System.nanoTime() - tickStartNanos) / 1.0e6;
        msptSum += ms;
        msptMax = Math.max(msptMax, ms);
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        if (!(player.getVehicle() instanceof RocketEntity rocket)) {
            if (p.phase() != MissionPilot.Phase.DONE && p.phase() != MissionPilot.Phase.FAILED && ticks > 5) {
                SpaceHorizons.LOGGER.error("{}o jogador não está mais montado no foguete (fase {}, veículo={}): missão perdida", TAG, p.phase(), player.getVehicle());
                FlightDevtest.mark("failed");
                FlightDevtest.mark("end");
                armed = false;
            }
            return;
        }
        Snapshot snap = rocket.flight().snapshot();
        if (snap == null) {
            return;
        }
        ticks++;
        double clock = SpaceClock.ticks(player.level());
        // a taxa do relógio como o voo a mede (0 com o relógio pausado): o pouso no motor usa a velocidade relativa ao chão
        double rate = Double.isNaN(lastClock) ? 1.0 : clock - lastClock;
        lastClock = clock;
        p.clockRate(rate >= 0.0 && rate <= 4.0 ? rate : 1.0);
        current = p.step(snap, clock);
        if (p.phase() != lastPhase) {
            lastPhase = p.phase();
            FlightDevtest.mark("phase_" + lastPhase);
            SpaceHorizons.LOGGER.info("{}MARCO fase {} no tick {} (relógio {}), foguete em {} ({}, {}, {})", TAG, lastPhase, ticks, String.format(Locale.ROOT, "%.0f", clock),
                    rocket.level().dimension().identifier(), fmt(rocket.getX()), fmt(rocket.getY()), fmt(rocket.getZ()));
        }
        if (p.finished()) {
            // o acelerador da entidade fica no último comando: zera, senão um veículo com empuxo de sobra (a nave) decola de novo do chão
            current = null;
            rocket.applyControl(new RocketControlPayload(0.0F, 0.0F, 0.0F, 0.0F, (byte) 0));
            if (p.phase() == MissionPilot.Phase.FAILED) {
                FlightDevtest.mark("failed");
            }
            FlightDevtest.mark("end");
            SpaceHorizons.LOGGER.info("{}FIM da missão: fase {}, {} ticks, combustível restante {} mB, calor máximo {}, tempo de relógio real {} s", TAG, p.phase(), ticks,
                    fmt(rocket.fuelMb()), fmt(p.maxHeat()), (System.nanoTime() - startNanos) / 1_000_000_000L);
            armed = false;
            return;
        }
        // na descida da Terra (o pouso da nave começa nela) a posição vai ao log mais vezes: a fase é curta e é onde o pouso se decide
        if (ticks % (p.phase() == MissionPilot.Phase.EARTH_LANDING ? 20 : 500) == 0) {
            FlightState st = snap.state();
            SpaceHorizons.LOGGER.info("{}[{}] t={} dim={} modo={} pos=({}, {}, {}) segurado={} comb={} mB {} MSPT média={} ms máx={} ms", TAG, p.phase(), ticks,
                    rocket.level().dimension().identifier(), snap.mode(), fmt(rocket.getX()), fmt(rocket.getY()), fmt(rocket.getZ()), rocket.isHeld(), fmt(rocket.fuelMb()),
                    st == null ? "pousado" : String.format(Locale.ROOT, "corpo=%s alt=%.0f v=%.2f", st.body().planet().id().getPath(), st.altitude(), st.v().length()),
                    fmt(msptSum / 500.0), fmt(msptMax));
            msptSum = 0.0;
            msptMax = 0.0;
        }
    }

    /**
     * Superfície do terreno (y da face de cima) no ponto (x, z) da dimensão do corpo: o mapa de alturas do chunk, se carregado; senão o terreno
     * analítico (a Lua é toda analítica); NaN na Terra sem chunk (o piloto usa então a altitude).
     */
    private static double surfaceY(SolarSystem.Body body, double x, double z) {
        PlanetDefinition planet = body.planet();
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (server != null) {
            ServerLevel level = TravelTransfers.findLevel(server, planet);
            if (level != null && level.hasChunk(bx >> 4, bz >> 4)) {
                return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            }
        }
        if (planet.hasOcean()) {
            return Double.NaN;
        }
        return PlanetTerrains.get(planet).surfaceHeight(bx, bz) + 1.0;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
