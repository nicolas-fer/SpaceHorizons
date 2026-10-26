package com.spacehorizons.ship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.rocket.FlightMode;
import com.spacehorizons.rocket.RocketControlPayload;
import com.spacehorizons.rocket.RocketFuel;
import com.spacehorizons.rocket.RocketFuelTankBlockEntity;
import com.spacehorizons.rocket.RocketTelemetryBuilder;
import com.spacehorizons.rocket.ZoneSwitch;
import com.spacehorizons.space.SpaceClock;
import com.spacehorizons.space.flight.FlightState;
import com.spacehorizons.space.flight.FrameBridge.Local;
import com.spacehorizons.space.flight.RocketStats;
import com.spacehorizons.space.flight.V3;
import com.spacehorizons.space.travel.TravelTransfers;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * O voo das naves no servidor (plano 2.5, fase 1c): a cada tick avança o {@link ShipFlight.State} de cada nave que voa (as do registro com
 * {@link ShipRecord#flight}), lê o comando do piloto ({@link ShipControlState}), mantém o combustível dos tanques de blocos de acordo com o
 * do voo, grava o estado no {@link ShipRegistry} e manda o estado virtual ({@link ShipStatePayload}) a quem está dentro da nave e a telemetria
 * ao piloto. Nenhuma entidade se move e ninguém dentro é empurrado: a nave é o referencial (plano 6).
 *
 * <p>Todas as naves que voam são integradas a cada tick, mesmo sem ninguém dentro: um passo de integrador custa poucas dezenas de operações,
 * então não há passo grosso (o plano previa Kepler para naves vazias; o custo medido não justifica o código a mais na fase 1).
 */
public final class ShipFlightTicker {
    /** De quantos em quantos ticks: estado aos clientes, telemetria ao piloto, combustível nos tanques, gravação e novo censo. */
    static final int STATE_INTERVAL = 2;
    static final int TELEMETRY_INTERVAL = 4;
    static final int FUEL_INTERVAL = 10;
    static final int SAVE_INTERVAL = 100;
    static final int CENSUS_INTERVAL = 600;

    /** O voo vivo de uma nave (memória do servidor; o que persiste é o {@link ShipFlightData} do registro). */
    public static final class Live {
        final int shipId;
        ShipFlight.State state;
        final ShipControlState control = new ShipControlState();
        ShipCensus census;
        /** A proa e os bocais dos motores (do último censo) e o que dela sai: o empuxo líquido, nos eixos canônicos da nave. */
        ShipArrangement arrangement;
        RocketStats stats;
        V3 thrustAxis = ShipSpecs.BOW_AXIS;
        List<BlockPos> tanks = List.of();
        double lastClock = Double.NaN;
        long lastDistributedMb = -1;
        long ticks;
        /** O piloto de teste do servidor (autoteste): conta como piloto a bordo sem ninguém no assento. */
        boolean devtestPilot;
        int stepsHeld;
        /** A descida começou (a conversão de volta para a entidade está na fila): não começa outra, e a nave segue voando até a leitura acabar. */
        boolean descentStarted;
        /** Taxa do relógio do último passo (relógio por tick; 0 com o relógio pausado, quando o chão não gira na física). */
        double rate = 1.0;

        /** Taxa do relógio do último passo do voo (0 com o relógio pausado). */
        public double rate() {
            return this.rate;
        }
        /** O aviso de descida recusada já foi dado nesta recusa (uma vez por recusa, não a cada tick). */
        boolean descentWarned;

        Live(int shipId, ShipFlight.State state, ShipCensus census, ShipArrangement arrangement) {
            this.shipId = shipId;
            this.state = state;
            this.control.setThrottle(state.throttle());
            adopt(census, arrangement);
        }

        /** Passa a usar este censo e esta disposição: massa, empuxo líquido e o eixo dele. */
        void adopt(ShipCensus newCensus, ShipArrangement newArrangement) {
            this.census = newCensus;
            this.arrangement = newArrangement;
            this.stats = ShipSpecs.statsFor(newCensus, newArrangement);
            this.thrustAxis = ShipSpecs.thrustAxis(newCensus, newArrangement);
        }

        public ShipArrangement arrangement() {
            return this.arrangement;
        }

        /** O id da nave. */
        public int shipId() {
            return this.shipId;
        }

        /** O relógio no fim do último passo (o do estado {@link #state}); NaN antes do primeiro passo. */
        public double clockAfterStep() {
            return this.lastClock + this.rate;
        }

        /** A descida da nave já começou (a conversão para entidade está na fila): ela não é mais um veículo do estaleiro. */
        public boolean descending() {
            return this.descentStarted;
        }

        /** O eixo do empuxo líquido, nos eixos canônicos da nave (a proa é -Z). */
        public V3 thrustAxis() {
            return this.thrustAxis;
        }

        public ShipFlight.State state() {
            return this.state;
        }

        public RocketStats stats() {
            return this.stats;
        }

        public ShipCensus census() {
            return this.census;
        }

        public ShipControlState control() {
            return this.control;
        }

        public long ticks() {
            return this.ticks;
        }
    }

    private static final Map<Integer, Live> LIVE = new HashMap<>();

    private ShipFlightTicker() {
    }

    // ---------------------------------------------------------------- consulta e controle

    /** O voo vivo da nave, ou null se ela não voa (ou ainda não foi carregada neste servidor). */
    public static @Nullable Live live(int shipId) {
        return LIVE.get(shipId);
    }

    /** Os voos vivos (cópia), para quem desenha as naves umas para as outras ({@link VesselSync}). */
    public static List<Live> liveAll() {
        return new ArrayList<>(LIVE.values());
    }

    /** O combustível em mB: o do voo se a nave voa, senão o dos tanques do último censo. */
    public static double fuelMb(int shipId, ShipCensusResult census) {
        Live live = LIVE.get(shipId);
        return live != null ? live.state.fuelMb() : census.tankFuelMb();
    }

    /** Liga o voo da nave: estado inicial de teste (órbita circular da Terra, ver {@link ShipFlight#testState}) com o combustível dos tanques. */
    public static void start(MinecraftServer server, ShipRecord ship, ShipCensusResult census) {
        double clock = SpaceClock.ticks(server.overworld());
        startWith(server, ship, ShipFlight.testState(clock, census.tankFuelMb()), census);
    }

    /** Liga o voo da nave com um estado dado (a nave que veio de um foguete, no instante em que cruzou a altitude de saída). */
    public static void startWith(MinecraftServer server, ShipRecord ship, ShipFlight.State state, ShipCensusResult census) {
        ShipRegistry registry = ShipRegistry.get(server);
        registry.update(ship.withFlight(ShipFlightData.of(state, census.census(), census.arrangement())));
        Live live = new Live(ship.id(), state, census.census(), census.arrangement());
        live.tanks = census.tanks();
        live.lastDistributedMb = census.tankFuelMb();
        LIVE.put(ship.id(), live);
        SpaceHorizons.LOGGER.info("[ship] voo da nave {} ligado: {}", ship.id(), describe(live));
    }

    /** Recebe o comando do piloto da nave (pacote do cliente ou piloto de teste do servidor). */
    public static void applyControl(int shipId, RocketControlPayload payload, long tick, boolean devtest) {
        Live live = LIVE.get(shipId);
        if (live == null) {
            return;
        }
        live.control.apply(payload, tick);
        if (devtest) {
            live.devtestPilot = true;
        }
    }

    /** Só dev: tira o piloto de teste do servidor (volta a valer só o assento). */
    public static void clearDevtestPilot(int shipId) {
        Live live = LIVE.get(shipId);
        if (live != null) {
            live.devtestPilot = false;
        }
    }

    /** Só dev: põe o estado de voo (por exemplo, uma atitude de teste). */
    public static void devtestSetState(int shipId, ShipFlight.State state) {
        Live live = LIVE.get(shipId);
        if (live != null) {
            live.state = state;
        }
    }

    /** Só dev: a descida da nave já começou (a conversão de volta para a entidade entrou na fila). */
    public static boolean devtestDescentStarted(int shipId) {
        Live live = LIVE.get(shipId);
        return live != null && live.descentStarted;
    }

    static String describe(Live live) {
        FlightState o = live.state.orbit();
        return String.format(Locale.ROOT, "corpo=%s r=%.0f altitude=%.0f v=%.3f b/t combustível=%.0f mB massa seca=%.2f t empuxo=%.1f proa=%d", o.body().planet().id().getPath(),
                o.r().length(), o.altitude(), o.v().length(), o.fuelMb(), live.stats.dryMassT(), live.stats.thrust(), live.arrangement.bow());
    }

    // ---------------------------------------------------------------- laço

    /** A cada tick do servidor (fim do tick): avança o voo de cada nave que voa. */
    public static void tick(MinecraftServer server) {
        ServerLevel yard = ShipYard.level(server);
        if (yard == null) {
            return;
        }
        ShipRegistry registry = ShipRegistry.get(server);
        List<ShipRecord> flying = null;
        for (ShipRecord ship : registry.all()) {
            if (ship.flies()) {
                if (flying == null) {
                    flying = new ArrayList<>();
                }
                flying.add(ship);
            }
        }
        if (flying == null) {
            LIVE.clear();
            return;
        }
        ServerLevel overworld = server.overworld();
        long now = overworld.getGameTime();
        double clock = SpaceClock.ticks(overworld);
        LIVE.keySet().removeIf(id -> registry.byId(id) == null || !registry.byId(id).flies());
        for (ShipRecord ship : flying) {
            Live live = LIVE.get(ship.id());
            if (live == null) {
                live = hydrate(server, ship);
                if (live == null) {
                    continue;
                }
                LIVE.put(ship.id(), live);
            }
            try {
                stepOne(server, yard, registry, ship, live, now, clock);
            } catch (RuntimeException e) {
                SpaceHorizons.LOGGER.error("[ship] o voo da nave {} falhou neste tick", ship.id(), e);
            }
        }
    }

    private static @Nullable Live hydrate(MinecraftServer server, ShipRecord ship) {
        ShipFlightData data = ship.flight().orElseThrow();
        ShipFlight.State state = data.toState();
        if (state == null) {
            SpaceHorizons.LOGGER.warn("[ship] nave {}: o estado de voo salvo aponta para um corpo desconhecido ({}); ignorado", ship.id(), data.body());
            return null;
        }
        Live live = new Live(ship.id(), state, data.censusValue(), data.arrangementValue());
        ShipCensusResult latest = ShipCensusService.latest(ship.id());
        if (latest != null) {
            live.tanks = latest.tanks();
        } else {
            ShipCensusService.request(server, ship);
        }
        SpaceHorizons.LOGGER.info("[ship] voo da nave {} retomado do disco: {}", ship.id(), describe(live));
        return live;
    }

    private static void stepOne(MinecraftServer server, ServerLevel yard, ShipRegistry registry, ShipRecord ship, Live live, long now, double clock) {
        ShipRecord current = registry.byId(ship.id());
        if (current == null || current.phase() == ShipRecord.Phase.FILLING || current.phase() == ShipRecord.Phase.REMOVING) {
            return;      // a conversão já tem a nave (ou a entidade): o voo para aqui
        }
        double rate = Double.isNaN(live.lastClock) ? 1.0 : clock - live.lastClock;
        if (!(rate >= 0.0 && rate <= 4.0)) {
            rate = 1.0;      // /time set e afins: não é a taxa do relógio
        }
        live.lastClock = clock;
        live.rate = rate;
        live.ticks++;

        // censo em lotes (adota o resultado novo quando termina; pede outro de tempos em tempos enquanto a nave está carregada)
        ShipCensusResult latest = ShipCensusService.latest(ship.id());
        if (latest != null && (!latest.census().equals(live.census) || !latest.arrangement().equals(live.arrangement))) {
            live.adopt(latest.census(), latest.arrangement());
        }
        if (latest != null) {
            live.tanks = latest.tanks();
        }
        if (live.ticks % CENSUS_INTERVAL == 1 && ShipChunkTickets.isHeld(ship.id())) {
            ShipCensusService.request(server, ship);
        }

        ServerPlayer pilot = ShipPilots.pilotOf(server, ship);
        live.control.trackPilot(pilot != null || live.devtestPilot);
        RocketControlPayload c = live.control.control(now);
        int edges = live.control.takeEdges();
        ShipFlight.Command command = new ShipFlight.Command(c.pitch(), c.yaw(), c.roll(), c.throttle(), (edges & RocketControlPayload.FLAG_NEXT_SAS) != 0);
        ShipFlight.State next = ShipFlight.advance(live.state, command, live.stats, live.thrustAxis, clock, rate);
        ShipFlight.State committed = descent(server, ship, live, next, clock + rate);
        if (committed == null) {
            return;      // segurada: a dimensão do corpo ainda não tem os chunks do ponto de chegada (a nave espera, sem andar)
        }
        live.state = committed;

        if (live.ticks % FUEL_INTERVAL == 0) {
            syncFuel(yard, live);
        }
        List<ServerPlayer> inside = playersInside(server, ship);
        if (live.ticks % STATE_INTERVAL == 0 && !inside.isEmpty()) {
            ShipStatePayload payload = statePayload(live, now, clock + rate, rate);
            for (ServerPlayer player : inside) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
        if (pilot != null && live.ticks % TELEMETRY_INTERVAL == 0) {
            PacketDistributor.sendToPlayer(pilot, RocketTelemetryBuilder.build(live.state.orbit(), FlightMode.FLIGHT, live.state.sas(), live.state.throttle(), true,
                    live.stats, clock + rate, rate, false));
        }
        if (live.ticks % SAVE_INTERVAL == 0) {
            save(registry, ship.id(), live);
        }
    }

    /** O estado virtual como o cliente o recebe, no relógio {@code clock} (o do fim do passo). */
    static ShipStatePayload statePayload(Live live, long tick, double clock, double rate) {
        FlightState o = live.state.orbit();
        var q = live.state.attitude();
        return new ShipStatePayload(live.shipId, tick, clock, rate, o.body().planet().id().getPath(), new double[] {o.r().x(), o.r().y(), o.r().z()},
                new double[] {o.v().x(), o.v().y(), o.v().z()}, new double[] {q.x, q.y, q.z, q.w},
                new double[] {live.state.spin().x(), live.state.spin().y(), live.state.spin().z()}, (float) live.state.throttle(), (byte) live.state.sas().ordinal(),
                (byte) live.arrangement.bow());
    }

    /** Os jogadores que estão na célula da nave, no estaleiro. */
    static List<ServerPlayer> playersInside(MinecraftServer server, ShipRecord ship) {
        List<ServerPlayer> out = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ShipYard.is(player.level()) && ShipYard.cellContains(ship.cell(), player.getX(), player.getZ())) {
                if (out == null) {
                    out = new ArrayList<>(2);
                }
                out.add(player);
            }
        }
        return out == null ? List.of() : out;
    }

    // ---------------------------------------------------------------- descida (fase 2b)

    /**
     * A descida neste tick: devolve o estado a gravar, ou null para segurar a nave (a dimensão do corpo ainda não tem os chunks do ponto de
     * chegada). Abaixo da altitude de entrada, desce quando a dimensão do corpo pode recebê-la ({@link ShipDescent#crossed}), uma vez só; quem
     * está em pé a bordo desce junto, em pé, na mesma posição relativa (a recusa da D4 caiu: fase 2e, frente 2).
     */
    private static ShipFlight.@Nullable State descent(MinecraftServer server, ShipRecord ship, Live live, ShipFlight.State next, double clockEnd) {
        FlightState o = next.orbit();
        PlanetDefinition planet = o.body().planet();
        boolean below = ShipDescent.below(o);
        Local l = null;
        boolean crossed = false;
        if (below && !live.descentStarted) {
            l = ShipDescent.local(o, clockEnd, live.rate);
            crossed = ShipDescent.crossed(o, ShipDescent.heightAboveTerrain(l, planet), ZoneSwitch.horizontalSpeed(l));
        }
        if (!below) {
            live.descentWarned = false;
        }
        return switch (ShipDescent.decide(live.descentStarted, below, crossed)) {
            case START -> start(server, ship, live, next, l, planet);
            case FLY -> next;
        };
    }

    /** Começa a descida: a dimensão do corpo tem os chunks (senão pede e segura a nave) e a conversão entra na fila. */
    private static ShipFlight.@Nullable State start(MinecraftServer server, ShipRecord ship, Live live, ShipFlight.State next, Local l, PlanetDefinition planet) {
        ServerLevel dest = TravelTransfers.findLevel(server, planet);
        if (dest == null) {
            warnPlayers(server, ship, live, "message.spacehorizons.ship.descent_no_body");
            return next.withOrbit(ShipFlight.floor(next.orbit()));
        }
        double half = ShipDescent.halfExtent(ship);
        if (!ShipDescent.chunksLoaded(dest, l.x(), l.z(), half)) {
            ShipDescent.requestLoad(dest, l.x(), l.z(), half);
            return null;
        }
        try {
            ShipConversions.startDescent(server, ship.id());
        } catch (ShipConversionException e) {
            SpaceHorizons.LOGGER.warn("[ship] nave {}: a descida foi recusada: {}", ship.id(), e.getMessage());
            return next.withOrbit(ShipFlight.floor(next.orbit()));
        }
        live.descentStarted = true;
        SpaceHorizons.LOGGER.info("[ship] nave {} começa a descida: altitude {} blocos, velocidade {} b/t", ship.id(), fmt(next.orbit().altitude()),
                fmt(next.orbit().speed()));
        return next;
    }

    /** Avisa quem está dentro da nave, uma vez por aviso ({@link Live#descentWarned}, de novo depois que a nave sobe da altitude de entrada). */
    private static void warnPlayers(MinecraftServer server, ShipRecord ship, Live live, String key) {
        if (live.descentWarned) {
            return;
        }
        live.descentWarned = true;
        for (ServerPlayer player : playersInside(server, ship)) {
            player.sendSystemMessage(Component.translatable(key));
        }
    }

    /** A conversão de descida falhou antes de tirar a nave do estaleiro: ela volta a poder descer (na próxima tentativa). */
    public static void descentCancelled(int shipId) {
        Live live = LIVE.get(shipId);
        if (live != null) {
            live.descentStarted = false;
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    // ---------------------------------------------------------------- combustível e gravação

    /**
     * Leva o combustível do voo aos tanques de blocos e traz deles o que mudou por fora (galão despejado, tanque quebrado ou posto). Só age
     * com todos os chunks dos tanques carregados; sem tanques, o combustível é zero.
     */
    private static void syncFuel(ServerLevel yard, Live live) {
        if (live.tanks.isEmpty()) {
            if (live.state.fuelMb() > 0.0) {
                live.state = live.state.withOrbit(live.state.orbit().withFuel(0.0));
            }
            return;
        }
        List<RocketFuelTankBlockEntity> entities = new ArrayList<>(live.tanks.size());
        long sum = 0;
        for (BlockPos pos : live.tanks) {
            if (yard.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
                return;      // nave descarregada: o voo segue, os tanques são acertados quando carregar
            }
            if (yard.getBlockEntity(pos) instanceof RocketFuelTankBlockEntity tank) {
                entities.add(tank);
                sum += tank.fuelMb();
            }
        }
        if (live.lastDistributedMb < 0) {
            live.lastDistributedMb = sum;
        }
        double capacity = (double) entities.size() * ShipSpecs.TANK_CAPACITY_MB;
        double fuel = ShipFuel.reconcile(live.state.fuelMb(), live.lastDistributedMb, sum, capacity);
        live.state = live.state.withOrbit(live.state.orbit().withFuel(fuel));
        int[] caps = new int[entities.size()];
        java.util.Arrays.fill(caps, ShipSpecs.TANK_CAPACITY_MB);
        int[] amounts = RocketFuel.distribute((int) Math.round(fuel), caps);
        long given = 0;
        for (int i = 0; i < amounts.length; i++) {
            entities.get(i).setFuel(amounts[i]);
            given += amounts[i];
        }
        live.lastDistributedMb = given;
    }

    private static void save(ShipRegistry registry, int shipId, Live live) {
        ShipRecord ship = registry.byId(shipId);
        if (ship != null) {
            registry.update(ship.withFlight(ShipFlightData.of(live.state, live.census, live.arrangement)));
        }
    }

    /** O servidor vai parar: grava o estado de todas as naves que voam. */
    public static void flush(MinecraftServer server) {
        if (LIVE.isEmpty() || ShipYard.level(server) == null) {
            return;
        }
        ShipRegistry registry = ShipRegistry.get(server);
        for (Live live : LIVE.values()) {
            save(registry, live.shipId, live);
        }
    }

    /** O servidor parou: esquece o voo vivo (o mundo seguinte, no mesmo processo, começa limpo). */
    public static void reset() {
        LIVE.clear();
    }
}
