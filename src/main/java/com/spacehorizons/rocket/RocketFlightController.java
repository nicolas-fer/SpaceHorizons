package com.spacehorizons.rocket;

import java.util.List;
import java.util.Locale;

import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrains;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.rocket.FlightMachine.Event;
import com.spacehorizons.rocket.FlightMachine.Snapshot;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceClock;
import com.spacehorizons.space.SpaceDimension;
import com.spacehorizons.space.flight.FlightState;
import com.spacehorizons.space.flight.FrameBridge;
import com.spacehorizons.space.flight.FrameBridge.Local;
import com.spacehorizons.space.flight.V3;
import com.spacehorizons.space.flight.Zones;
import com.spacehorizons.space.travel.TravelTransferPayload;
import com.spacehorizons.space.travel.TravelTransfers;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * O encaixe do voo com o mundo, no servidor, a cada tick do foguete: lê o comando do piloto, pede os chunks à frente e
 * segura o passo se faltarem, roda a {@link FlightMachine}, e aplica o resultado (posição, atitude, dados sincronizados,
 * pouso, batida, troca de dimensão com o piloto) e a telemetria. A física e as decisões estão na {@link FlightMachine}.
 */
final class RocketFlightController {
    private static final int TELEMETRY_INTERVAL = 4;

    private RocketFlightController() {
    }

    static void tick(RocketEntity rocket, RocketFlight f) {
        if (!(rocket.level() instanceof ServerLevel level) || rocket.isRemoved() || rocket.portrait().isEmpty()) {
            return;
        }
        MinecraftServer server = level.getServer();
        boolean space = SpaceDimension.is(level);
        PlanetDefinition planet = Planets.forLevel(level).orElse(null);
        SolarSystem.Body levelBody = planet == null ? null : SolarSystem.bodyOf(planet).orElse(null);
        if (!space && levelBody == null) {
            return; // um mundo sem planeta (vanilla): o foguete só fica pousado
        }
        SolarSystem.Body zone = space ? null : levelBody;

        Snapshot snap = f.snapshot;
        if (snap == null || snap.zoneBody() != zone) {
            if (snap != null && snap.flying()) {
                SpaceHorizons.LOGGER.warn("Foguete em voo numa dimensão que não é a do voo salvo ({}): volta a pousado aqui", level.dimension().identifier());
            }
            snap = Snapshot.landed(zone, rocket.fuelMb());
            f.snapshot = snap;
        }

        long now = level.getGameTime();
        double clock = SpaceClock.ticks(level);
        double rate = Double.isNaN(f.lastClock) ? 1.0 : clock - f.lastClock;
        if (!(rate >= 0.0 && rate <= 4.0)) {
            rate = 1.0; // /time set e afins: não é a taxa do relógio
        }
        f.lastClock = clock;
        ServerPlayer pilot = rocket.getControllingPassenger() instanceof ServerPlayer p ? p : null;
        f.trackPilot(pilot != null);
        RocketControlPayload control = f.control(now);
        int edges = f.takeEdges();

        // ---- desmontar (só pousado): os blocos voltam; o foguete some
        if ((edges & RocketControlPayload.FLAG_DISASSEMBLE) != 0 && snap.mode() == FlightMode.LANDED) {
            try {
                RocketLanding.land(level, rocket);
            } catch (RocketException e) {
                if (pilot != null) {
                    pilot.sendSystemMessage(e.component());
                }
            }
            if (rocket.isRemoved()) {
                return;
            }
        }

        // o cliente já manda o acelerador integrado (0..1); os bits de cheio/corta são só informativos
        double throttle = control.throttle();
        boolean parachute = control.has(RocketControlPayload.FLAG_PARACHUTE) || (edges & RocketControlPayload.FLAG_PARACHUTE) != 0;
        boolean nextSas = (edges & RocketControlPayload.FLAG_NEXT_SAS) != 0;

        double fuel = rocket.fuelMb();
        Local landedLocal = new Local(rocket.getX(), rocket.getY(), rocket.getZ(), 0.0, 0.0, 0.0);
        Quaternionf attitude = new Quaternionf(rocket.attitude());
        Quaterniond landedAttitude = new Quaterniond(attitude.x, attitude.y, attitude.z, attitude.w);

        // ---- chunks à frente e travessia: só em voo
        if (!snap.flying()) {
            LandingPreloadDriver.release(f);
            rocket.setHeld(false);
            f.ahead.reset();
            f.below.reset();
            f.spacePos = null;
            rocket.setVirtualSpacePosition(null);
        }
        if (snap.flying()) {
            if (f.arrivalPending && f.arrivalTicks == 0) {
                f.lastVelocity = rocket.getDeltaMovement();
            }
            double circumference = zone != null ? zone.planet().circumferenceBlocks() : 0.0;
            boolean missing = false;
            if (zone == null) {
                // espaço aberto: o foguete fica estacionado num ponto fixo; só o chunk dele precisa estar carregado, e nada anda
                keepParkingLoaded(level, SpaceParking.pointFor(rocket.getUUID()));
            } else {
                missing = requestChunksAhead(f.ahead, level, now, rocket.position(), f.lastVelocity, circumference, footprint(rocket));
            }
            if (zone == null && snap.state() != null) {
                preloadBelow(server, f.below, now, snap.state(), clock, rate, footprint(rocket));
            }
            LandingPreloadDriver.tick(server, rocket, f, snap);
            if (pilot != null) {
                level.getChunkSource().move(pilot);
            }
            if (f.arrivalPending) {
                boolean ready = pilot == null
                        || (TravelTransfers.isReady(pilot) && level.getChunkSource().chunkMap.getPlayersWatching(rocket).contains(pilot));
                if (!ready && ++f.arrivalTicks <= RocketFlight.MAX_ARRIVAL_TICKS) {
                    f.restoreEdges(edges);
                    rocket.setDeltaMovement(Vec3.ZERO);
                    rocket.setHeld(true);
                    telemetry(rocket, pilot, now, snap, landedLocal, clock, rate, true);
                    return;
                }
                if (!ready) {
                    SpaceHorizons.LOGGER.warn("Foguete: o piloto não confirmou a travessia em {} ticks; a simulação segue assim mesmo", RocketFlight.MAX_ARRIVAL_TICKS);
                }
                SpaceHorizons.LOGGER.info("Foguete chegou à dimensão {} (esperou {} ticks pelo piloto)", level.dimension().identifier(), f.arrivalTicks);
                f.arrivalPending = false;
                f.arrivalTicks = 0;
            }
            ChunkGate.Decision decision = f.gate.decide(missing);
            if (decision == ChunkGate.Decision.GIVE_UP && !f.warnedChunks) {
                f.warnedChunks = true;
                SpaceHorizons.LOGGER.warn("Foguete: chunks à frente não carregaram em {} ticks; seguindo mesmo assim", ChunkGate.MAX_HELD_TICKS);
                if (pilot != null) {
                    pilot.sendSystemMessage(Component.translatable("message.spacehorizons.rocket.chunks_slow"));
                }
            }
            if (decision == ChunkGate.Decision.HOLD) {
                f.restoreEdges(edges);
                f.lastHeld = f.gate.heldTicks();
                rocket.setDeltaMovement(Vec3.ZERO);
                rocket.setHeld(true);
                telemetry(rocket, pilot, now, snap, landedLocal, clock, rate, true);
                return;
            }
            logHeld(f, level, rocket, snap);
        }

        // ---- o passo (anda: o cliente volta a interpolar)
        rocket.setHeld(false);
        RocketPortrait portrait = rocket.portrait();
        FlightMachine.Box box = new FlightMachine.Box(portrait.sizeX / 2.0, portrait.sizeY / 2.0, portrait.sizeZ / 2.0);
        FlightMachine.ZoneAccess zones = new FlightMachine.ZoneAccess() {
            @Override
            public boolean hasSpace() {
                // a nave não sai para o espaço: sem dimensão de espaço o voo segue na do corpo, como num teto (ver RocketEntity.ship)
                return !rocket.isShip() && TravelTransfers.findSpace(server) != null;
            }

            @Override
            public boolean hasBody(SolarSystem.Body body) {
                return TravelTransfers.findLevel(server, body.planet()) != null;
            }

            @Override
            public double terrainHeight(SolarSystem.Body body, double x, double z) {
                return PlanetTerrains.get(body.planet()).surfaceHeight(Mth.floor(x), Mth.floor(z));
            }
        };
        FlightMachine.Command command = new FlightMachine.Command(control.pitch(), control.yaw(), control.roll(), throttle, parachute, nextSas);
        FlightMachine.Result r = FlightMachine.step(snap, command, rocket.stats(), box, clock, rate,
                (x, z, ex, ez, lo, hi) -> terrainTop(level, x, z, ex, ez, lo, hi), zones, fuel, landedLocal, landedAttitude);
        Local loc = r.local();
        Vec3 center = new Vec3(loc.x(), loc.y(), loc.z());

        if (r.event() == Event.CRASH) {
            f.snapshot = r.next(); // pousado: o foguete devolve o ponto de saída no chão ao piloto
            LandingPreloadDriver.release(f);
            rocket.setPos(center);
            RocketCrash.crash(level, rocket, pilot, center, FlightMachineSpeed.of(r));
            return;
        }

        Vec3 old = rocket.position();
        Vec3 delta = center.subtract(old);

        if (r.transfer()) {
            ServerLevel dest = r.toBody() == null ? TravelTransfers.findSpace(server) : TravelTransfers.findLevel(server, r.toBody().planet());
            if (dest == null) {
                return;
            }
            // no espaço o foguete chega ao ponto de estacionamento (a posição real fica no estado de voo), não à posição real
            boolean toSpace = r.toBody() == null;
            Vec3 destCenter = toSpace ? SpaceParking.pointFor(rocket.getUUID()) : center;
            ChunkPos arrival = new ChunkPos(Mth.floor(destCenter.x) >> 4, Mth.floor(destCenter.z) >> 4);
            dest.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, arrival, ChunkGate.NEAR_RADIUS);
            // a chegada só vale com carregados os chunks que a pegada do foguete toca e o do ASSENTO do piloto (o positionRider do vanilla
            // bloqueia no getChunk do que falta)
            Vec3 seatAt = pilot == null ? destCenter : rocket.passengerPositionAt(destCenter, toFloat(r.localAttitude()), pilot);
            boolean arrivalReady = arrivalLoaded(dest, destCenter, footprint(rocket)) && arrivalLoaded(dest, seatAt, 1.0);
            dest.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, new ChunkPos(Mth.floor(seatAt.x) >> 4, Mth.floor(seatAt.z) >> 4), 0);
            ChunkGate.Decision decision = f.gate.decide(!arrivalReady);
            if (decision == ChunkGate.Decision.HOLD) {
                f.restoreEdges(edges);
                rocket.setDeltaMovement(Vec3.ZERO);
                rocket.setHeld(true);
                telemetry(rocket, pilot, now, snap, landedLocal, clock, rate, true);
                return;
            }
            Vec3 destVelocity = toSpace ? Vec3.ZERO : toVec(loc.coordinateVelocity(r.toBody()));
            transfer(rocket, f, dest, r, destCenter, toSpace ? center : null, pilot, snap, destVelocity, now, clock, rate);
            return;
        }

        f.snapshot = r.next();
        Snapshot next = r.next();

        boolean seam = zone != null && Math.abs(delta.x) > zone.planet().halfCircumference();
        boolean moving = next.flying() || r.event() == Event.SOFT_LANDING;
        Vec3 velocity = r.event() == Event.SOFT_LANDING ? Vec3.ZERO : seam ? f.lastVelocity : delta;
        if (zone == null && next.flying()) {
            // espaço aberto: estacionado; a posição real vai só no estado de voo e no dado sincronizado do céu
            Vec3 realBefore = f.spacePos;
            velocity = realBefore == null ? Vec3.ZERO : center.subtract(realBefore);
            f.spacePos = center;
            Vec3 park = SpaceParking.pointFor(rocket.getUUID());
            if (rocket.position().distanceToSqr(park) > 1.0e-6) {
                rocket.setPos(park);
            }
            rocket.setDeltaMovement(Vec3.ZERO);
            rocket.setVirtualSpacePosition(center);
        } else if (moving) {
            f.spacePos = null;
            rocket.setVirtualSpacePosition(null);
            if (seam) {
                rocket.teleport(new TeleportTransition(level, center, velocity, rocket.getYRot(), rocket.getXRot(), TeleportTransition.DO_NOTHING));
            } else {
                rocket.setPos(center);
                rocket.setDeltaMovement(velocity);
            }
        } else {
            rocket.setDeltaMovement(Vec3.ZERO);
        }
        f.lastVelocity = velocity;
        syncEntity(rocket, r, moving);

        if (r.event() == Event.LIFTOFF) {
            SpaceHorizons.LOGGER.info("Foguete decolou em ({}, {}, {}): {} mB, empuxo {}, massa {} t", fmt(loc.x()), fmt(loc.y()), fmt(loc.z()),
                    fmt(next.fuelMb()), fmt(rocket.stats().thrust()), fmt(rocket.stats().wetMass(next.fuelMb())));
        } else if (r.event() == Event.SOFT_LANDING) {
            LandingPreloadDriver.release(f);
            SpaceHorizons.LOGGER.info("Foguete pousou suave em ({}, {}, {}): v vertical {} b/t, horizontal {} b/t, inclinação {}°, {} mB restantes",
                    fmt(loc.x()), fmt(loc.y()), fmt(loc.z()), fmt(r.impactVertical()), fmt(r.impactHorizontal()), fmt(r.tiltDegrees()), fmt(next.fuelMb()));
        }

        if (zone != null && !f.warnedNoSpace && next.flying() && Zones.shouldLeave(zone.planet(), loc.y()) && TravelTransfers.findSpace(server) == null) {
            f.warnedNoSpace = true;
            SpaceHorizons.LOGGER.warn("Foguete acima da altitude de saída, mas este mundo não tem a dimensão de espaço: segue na dimensão do corpo");
            if (pilot != null) {
                pilot.sendSystemMessage(Component.translatable("message.spacehorizons.rocket.no_space_dimension"));
            }
        }
        if (pilot != null) {
            rocket.positionRider(pilot);
            level.getChunkSource().move(pilot);
        }
        telemetry(rocket, pilot, now, next, new Local(loc.x(), loc.y(), loc.z(), 0.0, 0.0, 0.0), clock + rate, rate, false);
    }

    /** Todos os chunks que a pegada ({@code center ± halfExtent}) toca na dimensão de destino estão carregados? */
    private static boolean arrivalLoaded(ServerLevel dest, Vec3 center, double halfExtent) {
        int cx0 = Mth.floor(center.x - halfExtent) >> 4;
        int cx1 = Mth.floor(center.x + halfExtent) >> 4;
        int cz0 = Mth.floor(center.z - halfExtent) >> 4;
        int cz1 = Mth.floor(center.z + halfExtent) >> 4;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                if (!dest.hasChunk(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Meia-extensão horizontal da pegada do foguete mais a folga do piloto e do teste de fluido do vanilla (1 bloco de cada lado). */
    private static double footprint(RocketEntity rocket) {
        RocketPortrait p = rocket.portrait();
        return Math.max(p.sizeX, p.sizeZ) / 2.0 + 2.0;
    }

    /** Quando uma espera por chunks acaba, registra quanto durou e onde (diagnóstico do ritmo de carga a alta velocidade). */
    private static void logHeld(RocketFlight f, ServerLevel level, RocketEntity rocket, Snapshot snap) {
        if (f.lastHeld >= 3) {
            SpaceHorizons.LOGGER.info("Foguete esperou {} ticks por chunks em {} (altitude {}, velocidade {} b/tick)", f.lastHeld,
                    level.dimension().identifier(), fmt(rocket.getY()), fmt(f.lastVelocity.length()));
        }
        f.lastHeld = 0;
    }

    /** Dados sincronizados com o resultado do passo (atitude local, acelerador, modos, calor, combustível). */
    private static void syncEntity(RocketEntity rocket, FlightMachine.Result r, boolean moving) {
        Snapshot next = r.next();
        if (moving) {
            rocket.setAttitude(toFloat(r.localAttitude()));
        }
        rocket.setThrottle((float) next.throttle());
        rocket.syncFlightData(next.mode(), (byte) next.sas().ordinal(), next.state() != null ? (float) next.state().heat() : 0.0F);
        if (next.flying()) {
            rocket.setFuelMb(next.fuelMb());
        }
    }

    /**
     * Troca de dimensão com o piloto: o instantâneo novo (já na zona de destino) é gravado ANTES, porque o vanilla recria a
     * entidade por NBT; o piloto é avisado (sem tela de carregamento) com a posição do ASSENTO no destino; depois
     * {@code rocket.teleport} leva o foguete e o passageiro. Se o teleporte for recusado, volta ao instantâneo anterior.
     */
    private static void transfer(RocketEntity rocket, RocketFlight f, ServerLevel dest, FlightMachine.Result r, Vec3 center,
            @Nullable Vec3 realInSpace, @Nullable ServerPlayer pilot, Snapshot previous, Vec3 destVelocity, long now, double clock, double rate) {
        f.snapshot = r.next();
        Quaternionf localAttitude = toFloat(r.localAttitude());
        rocket.setAttitude(localAttitude);
        rocket.setThrottle((float) r.next().throttle());
        rocket.syncFlightData(r.next().mode(), (byte) r.next().sas().ordinal(), r.next().state() != null ? (float) r.next().state().heat() : 0.0F);
        rocket.setFuelMb(r.next().fuelMb());
        // indo ao espaço, a posição real acompanha o foguete (o dado sincronizado e o NBT da recriação); vindo dele, o foguete volta a andar
        f.spacePos = realInSpace;
        rocket.setVirtualSpacePosition(realInSpace);
        if (pilot != null) {
            Vec3 seat = rocket.passengerPositionAt(center, localAttitude, pilot);
            PacketDistributor.sendToPlayer(pilot, new TravelTransferPayload(seat.x, seat.y, seat.z, pilot.getYRot(), pilot.getXRot()));
        }
        String from = rocket.level().dimension().identifier().toString();
        TeleportTransition transition = new TeleportTransition(dest, center, destVelocity, rocket.getYRot(), rocket.getXRot(), TeleportTransition.DO_NOTHING);
        Entity moved = rocket.teleport(transition);
        if (moved == null) {
            f.snapshot = previous;
            SpaceHorizons.LOGGER.warn("Foguete: a troca de dimensão {} -> {} foi recusada", from, dest.dimension().identifier());
            return;
        }
        SpaceHorizons.LOGGER.info("Foguete trocou de dimensão {} -> {} em ({}, {}, {}) com velocidade ({}, {}, {}), piloto={}", from,
                dest.dimension().identifier(), fmt(center.x), fmt(center.y), fmt(center.z), fmt(destVelocity.x), fmt(destVelocity.y), fmt(destVelocity.z),
                pilot == null ? "nenhum" : pilot.getName().getString());
    }

    // ---------------------------------------------------------------- chunks

    /**
     * No espaço, perto de um corpo e devagar o bastante para entrar na dimensão dele ({@link ZoneSwitch#shouldPreload}): pede à frente,
     * na dimensão do corpo, os chunks por onde o foguete vai entrar, para a troca não esperar a geração (nunca gateia o voo
     * do espaço: a espera, se faltar chunk, é a de {@code transfer}).
     */
    private static void preloadBelow(MinecraftServer server, ChunkGate.Requester requester, long now, FlightState state, double clock, double rate,
            double halfExtent) {
        SolarSystem.Body body = state.body();
        PlanetDefinition planet = body.planet();
        double altitude = state.r().length() - planet.radius();
        if (altitude >= Zones.exitAltitude(planet)) {
            return;
        }
        ServerLevel below = TravelTransfers.findLevel(server, planet);
        if (below == null) {
            return;
        }
        Local l = FrameBridge.toDimension(body, clock + rate, rate, state.r(), state.v());
        if (!ZoneSwitch.shouldPreload(ZoneSwitch.horizontalSpeed(l))) {
            return;
        }
        V3 cv = l.coordinateVelocity(body);
        requestChunksAhead(requester, below, now, new Vec3(l.x(), l.y(), l.z()), new Vec3(cv.x(), 0.0, cv.z()), planet.circumferenceBlocks(), halfExtent);
    }

    /** O chunk do ponto de estacionamento do foguete no espaço fica carregado (ticket com o raio dos chunks mais próximos do corredor). */
    private static void keepParkingLoaded(ServerLevel level, Vec3 park) {
        level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, new ChunkPos(Mth.floor(park.x) >> 4, Mth.floor(park.z) >> 4), ChunkGate.NEAR_RADIUS);
    }

    /**
     * Pede o corredor contínuo de chunks à frente (tickets ENDER_PEARL, ver {@link ChunkGate.Requester}) com antecedência proporcional
     * à latência de geração medida, e devolve true se algum dos chunks dos próximos {@link ChunkGate#GATING_STEPS} ticks ainda não está
     * carregado.
     */
    private static boolean requestChunksAhead(ChunkGate.Requester requester, ServerLevel level, long now, Vec3 position, Vec3 velocity,
            double circumference, double halfExtent) {
        List<ChunkGate.Cell> corridor = ChunkGate.corridor(position.x, position.z, velocity.x, velocity.z, circumference, halfExtent,
                requester.lookaheadTicks());
        double scale = ChunkGate.Requester.budgetScale(level.getServer().getAverageTickTimeNanos() / 1.0e6);
        return requester.update(now, corridor, new ChunkGate.Sink() {
            @Override
            public void add(int chunkX, int chunkZ, int radius) {
                level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, new ChunkPos(chunkX, chunkZ), radius);
            }

            @Override
            public boolean loaded(int chunkX, int chunkZ) {
                return level.hasChunk(chunkX, chunkZ);
            }
        }, scale);
    }

    // ---------------------------------------------------------------- terreno

    /** Máximo de colunas de amostra por eixo na pegada (caixas grandes são amostradas, com as bordas sempre incluídas). */
    private static final int MAX_SAMPLES_PER_AXIS = 6;

    /**
     * A altura da superfície sólida mais alta sob a pegada ({@code x ± ex, z ± ez}) na janela vertical {@code [yMin, yMax]}
     * (varre os blocos de cima para baixo em cada coluna amostrada); NaN se não há nenhuma na janela ou nenhum chunk da pegada
     * está carregado.
     */
    static double terrainTop(ServerLevel level, double x, double z, double ex, double ez, double yMin, double yMax) {
        int x0 = Mth.floor(x - ex);
        int x1 = Math.max(x0, Mth.ceil(x + ex) - 1);
        int z0 = Mth.floor(z - ez);
        int z1 = Math.max(z0, Mth.ceil(z + ez) - 1);
        int strideX = Math.max(1, (x1 - x0 + 1 + MAX_SAMPLES_PER_AXIS - 1) / MAX_SAMPLES_PER_AXIS);
        int strideZ = Math.max(1, (z1 - z0 + 1 + MAX_SAMPLES_PER_AXIS - 1) / MAX_SAMPLES_PER_AXIS);
        int top = Math.min(Mth.floor(yMax), level.getMaxY());
        int bottom = Math.max(Mth.floor(yMin), level.getMinY());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        double best = Double.NaN;
        for (int bx = x0; bx <= x1; bx += strideX) {
            for (int bz = z0; bz <= z1; bz += strideZ) {
                best = best(best, columnTop(level, cursor, bx, bz, top, bottom));
                if (bz < z1 && bz + strideZ > z1) {
                    best = best(best, columnTop(level, cursor, bx, z1, top, bottom));
                }
            }
            if (bx < x1 && bx + strideX > x1) {
                for (int bz = z0; bz <= z1; bz += strideZ) {
                    best = best(best, columnTop(level, cursor, x1, bz, top, bottom));
                }
            }
        }
        return best;
    }

    /** O maior dos dois, ignorando NaN (coluna sem superfície na janela). */
    private static double best(double a, double b) {
        return Double.isNaN(a) ? b : Double.isNaN(b) ? a : Math.max(a, b);
    }

    private static double columnTop(ServerLevel level, BlockPos.MutableBlockPos cursor, int bx, int bz, int top, int bottom) {
        if (!level.hasChunk(bx >> 4, bz >> 4)) {
            return Double.NaN;
        }
        for (int y = top; y >= bottom; y--) {
            cursor.set(bx, y, bz);
            BlockState state = level.getBlockState(cursor);
            if (!state.getCollisionShape(level, cursor).isEmpty() || !state.getFluidState().isEmpty()) {
                return y + 1.0;
            }
        }
        return Double.NaN;
    }

    // ---------------------------------------------------------------- telemetria

    /** Manda a telemetria ao piloto a cada {@link #TELEMETRY_INTERVAL} ticks (a do estado em voo, ou a de um estado em repouso se pousado). */
    private static void telemetry(RocketEntity rocket, @Nullable ServerPlayer pilot, long now, Snapshot s, Local at, double clock, double rate,
            boolean held) {
        if (pilot == null || now % TELEMETRY_INTERVAL != 0) {
            return;
        }
        FlightState state = s.state();
        if (state == null) {
            if (s.zoneBody() == null) {
                return;
            }
            state = FrameBridge.fromDimension(s.zoneBody(), clock, rate, new Local(at.x(), at.y(), at.z(), 0.0, 0.0, 0.0), rocket.fuelMb());
        }
        RocketFlight flight = rocket.flight();
        RocketTelemetryPayload payload = RocketTelemetryBuilder.build(state, s.mode(), s.sas(), s.throttle(), s.zoneBody() == null,
                rocket.stats(), clock, rate, held, LandingPreloadDriver.telemetryState(flight), (byte) flight.landing.percent());
        PacketDistributor.sendToPlayer(pilot, payload);
    }

    // ---------------------------------------------------------------- utilidades

    private static Quaternionf toFloat(Quaterniond q) {
        return new Quaternionf((float) q.x, (float) q.y, (float) q.z, (float) q.w).normalize();
    }

    private static Vec3 toVec(V3 v) {
        return new Vec3(v.x(), v.y(), v.z());
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /** Velocidade de impacto do resultado de uma batida. */
    private static final class FlightMachineSpeed {
        static double of(FlightMachine.Result r) {
            return com.spacehorizons.space.flight.LandingRules.impactSpeed(r.impactVertical(), r.impactHorizontal());
        }
    }
}
