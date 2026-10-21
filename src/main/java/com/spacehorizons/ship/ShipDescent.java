package com.spacehorizons.ship;

import org.jspecify.annotations.Nullable;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrains;
import com.spacehorizons.rocket.FlightMachine;
import com.spacehorizons.rocket.FlightMode;
import com.spacehorizons.rocket.ZoneSwitch;
import com.spacehorizons.space.SpaceClock;
import com.spacehorizons.space.flight.FlightState;
import com.spacehorizons.space.flight.FrameBridge;
import com.spacehorizons.space.flight.FrameBridge.Local;
import com.spacehorizons.space.flight.V3;
import com.spacehorizons.space.flight.Zones;
import com.spacehorizons.space.travel.TravelTransfers;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * A descida da nave (fase 2b, parte 1): a nave do voo virtual desce ao corpo e vira entidade na dimensão dele, pelo mesmo critério
 * do foguete ({@link ZoneSwitch#canEnter}: abaixo da altitude de entrada e só quando a dimensão do corpo consegue carregar o terreno
 * por onde ela passa; à força, abaixo de 600 blocos). Tripulante em pé recusa a descida (decisão D4, versão simples): a nave é segurada
 * na altitude de entrada e os de bordo são avisados. A decisão e a conversão para o local são puras; {@link #plan} e os chunks usam o
 * servidor.
 */
public final class ShipDescent {
    /** O que o voo faz neste tick: seguir no espaço, ser segurada na altitude de entrada ou começar a descida. */
    public enum Step {
        FLY, FLOOR, START
    }

    /** O que a entidade recebe ao nascer na dimensão do corpo: o destino, a posição e a velocidade de coordenadas, e o voo. */
    public record Plan(ServerLevel dest, Vec3 pos, Vec3 velocity, FlightMachine.Snapshot flight) {
    }

    private ShipDescent() {
    }

    /**
     * A decisão de um tick. {@code started}: a descida já começou (vale uma vez só); {@code below}: a nave está abaixo da altitude de
     * entrada; {@code crossed}: descendo e a dimensão do corpo pode recebê-la ({@link #crossed}); {@code mayDescend}: não há tripulante em pé.
     */
    public static Step decide(boolean started, boolean below, boolean crossed, boolean mayDescend) {
        if (started || !below) {
            return Step.FLY;
        }
        if (!mayDescend) {
            return Step.FLOOR;
        }
        return crossed ? Step.START : Step.FLY;
    }

    /** Tripulante em pé dentro da nave recusa a descida: só quem está sentado pode descer (D4). */
    public static boolean mayDescend(int standingInside) {
        return standingInside == 0;
    }

    /** A nave está abaixo da altitude de entrada do corpo (a conta de {@link Zones}: altitude sobre a superfície). */
    public static boolean below(FlightState s) {
        return s.r().length() - s.body().planet().radius() <= Zones.enterAltitude(s.body().planet());
    }

    /** A nave desce (a velocidade aponta para o centro) e a dimensão do corpo consegue recebê-la (o mesmo critério do foguete). */
    public static boolean crossed(FlightState s, double heightAboveTerrain, double horizontalSpeed) {
        boolean descending = s.v().dot(s.r().unit()) < 0.0;
        return descending && ZoneSwitch.canEnter(s.body().planet(), heightAboveTerrain, horizontalSpeed);
    }

    /** O estado virtual (inercial, relativo ao corpo) em coordenadas da dimensão do corpo, no relógio {@code clock}, a taxa 1. */
    public static Local local(FlightState s, double clock) {
        return FrameBridge.toDimension(s.body(), clock, 1.0, s.r(), s.v());
    }

    /** Altura do ponto sobre o terreno, pelo mesmo critério do voo do foguete (a superfície da dimensão do corpo). */
    public static double heightAboveTerrain(Local l, PlanetDefinition planet) {
        return l.y() - PlanetTerrains.get(planet).surfaceHeight(Mth.floor(l.x()), Mth.floor(l.z()));
    }

    /** Meia-extensão da pegada da nave (para pedir os chunks do ponto de chegada); 2 se a nave não tem o retrato salvo. */
    public static double halfExtent(ShipRecord ship) {
        return ship.snapshot().map(s -> Math.max(s.portraitSize().getX(), s.portraitSize().getZ()) / 2.0 + 2.0).orElse(2.0);
    }

    /** Todos os chunks da pegada em (x, z) estão carregados na dimensão de chegada? */
    public static boolean chunksLoaded(ServerLevel dest, double x, double z, double halfExtent) {
        int x0 = Mth.floor(x - halfExtent) >> 4;
        int x1 = Mth.floor(x + halfExtent) >> 4;
        int z0 = Mth.floor(z - halfExtent) >> 4;
        int z1 = Mth.floor(z + halfExtent) >> 4;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                if (!dest.hasChunk(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Pede (com ticket) os chunks em volta de (x, z) na dimensão de chegada; é repetido a cada tick enquanto faltam. */
    public static void requestLoad(ServerLevel dest, double x, double z, double halfExtent) {
        dest.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, new ChunkPos(Mth.floor(x) >> 4, Mth.floor(z) >> 4),
                Mth.ceil(halfExtent / 16.0) + 1);
    }

    /**
     * O plano da descida a partir do estado vivo da nave (o do último tick, que não anda durante a leitura da caixa: ver
     * {@link YardToRocketJob}). Devolve null se a nave não voa ou o corpo não tem dimensão neste mundo.
     */
    public static @Nullable Plan plan(MinecraftServer server, int shipId) {
        ShipFlightTicker.Live live = ShipFlightTicker.live(shipId);
        if (live == null) {
            return null;
        }
        ShipFlight.State state = live.state();
        FlightState o = state.orbit();
        ServerLevel dest = TravelTransfers.findLevel(server, o.body().planet());
        if (dest == null) {
            return null;
        }
        Local l = local(o, SpaceClock.ticks(server.overworld()));
        V3 cv = l.coordinateVelocity(o.body());
        FlightMachine.Snapshot flight = new FlightMachine.Snapshot(FlightMode.FLIGHT, o, state.attitude(), state.spin(), state.sas(),
                state.throttle(), false, o.body(), o.fuelMb());
        return new Plan(dest, new Vec3(l.x(), l.y(), l.z()), new Vec3(cv.x(), cv.y(), cv.z()), flight);
    }
}
