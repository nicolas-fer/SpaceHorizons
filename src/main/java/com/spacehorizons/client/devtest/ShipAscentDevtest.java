package com.spacehorizons.client.devtest;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.RocketControlPayload;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketFuelTankBlockEntity;
import com.spacehorizons.rocket.RocketPortrait;
import com.spacehorizons.ship.ShipAssembler;
import com.spacehorizons.ship.ShipCensusService;
import com.spacehorizons.ship.ShipConversions;
import com.spacehorizons.ship.ShipFlight;
import com.spacehorizons.ship.ShipFlightTicker;
import com.spacehorizons.ship.ShipRecord;
import com.spacehorizons.ship.ShipRegistry;
import com.spacehorizons.ship.ShipSeatEntity;
import com.spacehorizons.ship.ShipYard;
import com.spacehorizons.space.SpaceClock;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.flight.AttitudeMath;
import com.spacehorizons.space.flight.BodyPhysics;
import com.spacehorizons.space.flight.Orbit;
import com.spacehorizons.space.flight.FlightState;
import com.spacehorizons.space.flight.V3;
import com.spacehorizons.space.flight.Zones;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Subida da nave até a altitude de saída (docs/PLANO_NAVES.md, fase 2a-ii): -PshOnly=shipascent. A nave é montada no chão (cenário do
 * {@link ShipGroundDevtest}), o piloto acelera até cruzar a altitude de saída da Terra, a entidade vira blocos no estaleiro e o piloto
 * senta na ponte com o voo virtual ligado. As conferências saem como {@code [AUTOTEST] SHIPASCENT CHECK PASSOU} ou {@code CHECK [REDACTED]}.
 */
final class ShipAscentDevtest {
    static final String TAG = "[AUTOTEST] SHIPASCENT ";
    static final BlockPos BASE = ShipGroundDevtest.BASE;
    /** Itens do baú do foguete de teste no chão ({@link ShipGroundDevtest#build}): 10 diamantes e 20 lingotes de ferro. */
    static final int GROUND_DIAMONDS = 10;
    static final int GROUND_IRON = 20;
    /** Colunas extras de tanques 2x2x4 na nave de teste: a subida gasta ~31.500 mB e a circularização precisa de mais Δv (só o shipascent). */
    static final int EXTRA_COLUMNS = 1;
    static final int TANK_MB = 4000;
    /** Tolerância relativa entre a altitude e a velocidade da entidade no instante da troca e as da nave virtual logo depois. */
    static final double TOLERANCE = 0.01;
    /** Tolerância relativa da velocidade: a conferência anda 1 a 2 ticks de empuxo depois da troca (ver ShipAscentDevtest.verify). */
    static final double SPEED_TOLERANCE = 0.02;

    private static int passed;
    private static int failed;
    private static RocketEntity ship;
    private static Set<Integer> idsBefore = new HashSet<>();
    private static boolean armed;
    private static boolean registered;
    private static boolean done;
    private static int climbTicks;
    private static double maxAltitude;
    private static boolean riddenAll = true;
    private static boolean halfMarked;
    private static boolean frozenMarked;
    private static double freezeAltitude = Double.NaN;
    private static double freezeSpeed = Double.NaN;
    private static double freezeFuel = Double.NaN;
    /** O piloto de missão (virada de gravidade, {@link MissionPilot}) que sobe a nave até a troca. */
    private static MissionPilot pilot;
    /** Acelerador máximo da subida: a nave de teste tem que chegar à altitude de saída em órbita ligada, não em fuga. */
    static final double ASCENT_THROTTLE = 0.5;
    /** Ticks depois da troca em que a conferência de estabilidade (mB dos tanques e acelerador) roda. */
    static final int SETTLE_TICKS = 100;
    private static boolean tradeDone;
    private static int settleTicks;
    private static boolean settled;
    private static int tradeTankMb;
    private static ShipRecord tradeRec;
    /**
     * Folga da periapse alvo sobre a altitude de entrada (blocos). O alvo é relativo à entrada, não um número fixo: a periapse não passa da
     * altitude em que a nave queima (perto do apoastro, ~6.000 nesta subida), então um alvo fixo perto dela pode ser inalcançável e a queima
     * não pararia nunca.
     */
    static final double PERI_MARGIN = 300.0;
    /** Periapse alvo da circularização no voo virtual (altitude sobre a superfície): acima da altitude de entrada, para não reentrar. */
    static final double PERI_TARGET = Zones.enterAltitude(SolarSystem.EARTH.planet()) + PERI_MARGIN;
    /** Ticks de fase: a circularização vira falha se o prógrado ou a queima não fecharem neste prazo. */
    static final int CIRC_TIMEOUT = 20_000;
    /** Teto da queima, em múltiplos da duração estimada: a queima para aqui mesmo sem a periapse no alvo. */
    static final double BURN_CAP_FACTOR = 3.0;
    /** Ticks entre pulsos do "próximo SAS" até o SAS ficar na horizontal. */
    static final int SAS_PULSE_TICKS = 20;
    /** Fases da circularização: 0 põe o SAS na horizontal e espera a metade da queima antes do apoastro, 1 queima na horizontal, 3 acabou. */
    private static int circPhase;
    /** Fica falso se o SAS sair da horizontal durante a queima. */
    private static boolean burnSasHorizontal = true;
    /** Duração estimada da queima (ticks, no começo dela) e ticks já queimados. */
    private static double burnPlanned;
    private static int burnElapsed;
    private static int circTicks;
    private static double circSpeedStart;
    private static double circFuelStart;

    private ShipAscentDevtest() {
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        SpaceHorizons.LOGGER.info(TAG + "CHECK {} {}: {}", ok ? "PASSOU" : "[REDACTED]", name, detail);
    }

    static void summary() {
        SpaceHorizons.LOGGER.info(TAG + "RESUMO: {} verificações passaram, {} falharam", passed, failed);
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /** Altitude do voo (blocos acima da superfície do corpo), em eixos inerciais. */
    static double altitudeOf(FlightState state) {
        return state.r().length() - state.body().planet().radius();
    }

    /** Monta a nave no chão (o cenário do foguete de teste) e prepara as contagens de antes da conversão. */
    static void build(Minecraft mc) {
        ShipGroundDevtest.build(mc, EXTRA_COLUMNS);
        ShipGroundDevtest.onServer(mc, "ids antes", (server, level, player) -> {
            idsBefore = new HashSet<>();
            for (ShipRecord rec : ShipRegistry.get(server).all()) {
                idsBefore.add(rec.id());
            }
        });
    }

    /** Monta a nave pela ponte, senta o piloto e deixa o laço de voo armado. */
    static void assemble(Minecraft mc) {
        ShipGroundDevtest.onServer(mc, "montagem", (server, level, player) -> {
            try {
                ship = ShipAssembler.assemble(level, BASE.offset(0, 1, 0), player);
                player.startRiding(ship, true, true);
            } catch (com.spacehorizons.rocket.RocketException e) {
                check("montagem aceita", false, e.getMessage());
                FlightDevtest.mark("sa_done");
                return;
            }
            RocketPortrait portrait = ship.portrait();
            check("montagem: " + ShipGroundDevtest.expectedBlocks() + " blocos no retrato", portrait.blockCount() == ShipGroundDevtest.expectedBlocks(),
                    "retrato " + portrait.blockCount());
            check("piloto sentado na nave no chão", player.getVehicle() == ship, "veículo=" + player.getVehicle());
            SpaceHorizons.LOGGER.info("{}MONTADA: {} blocos, combustível {} mB, altitude de saída {} blocos", TAG, portrait.blockCount(), f(ship.fuelMb()),
                    f(Zones.exitAltitude(SolarSystem.EARTH.planet())));
            FlightDevtest.mark("sa_assembled");
        });
    }

    /** Liga o acelerador do piloto sentado e arma a conferência, que roda em todo tick do servidor. */
    static void climb(Minecraft mc) {
        ShipGroundDevtest.onServer(mc, "partida", (server, level, player) -> {
            if (ship == null || !(player.getVehicle() instanceof RocketEntity)) {
                check("subida: piloto sentado antes de decolar", false, "veículo=" + player.getVehicle());
                FlightDevtest.mark("sa_done");
                return;
            }
            armed = true;
            pilot = new MissionPilot(ship.stats(), ship.portrait().sizeY / 2.0, (body, x, z) -> Double.NaN,
                    line -> SpaceHorizons.LOGGER.info(TAG + line));
            if (!registered) {
                registered = true;
                NeoForge.EVENT_BUS.addListener(ServerTickEvent.Pre.class, e -> tick(e.getServer()));
            }
            SpaceHorizons.LOGGER.info("{}SUBIDA: empuxo {} t·b/t², combustível {} mB, altitude de saída {} blocos", TAG, f(ship.stats().thrust()),
                    f(ship.fuelMb()), f(Zones.exitAltitude(SolarSystem.EARTH.planet())));
        });
    }

    /** Todo tick: acelera enquanto a nave sobe; quando ela congela (cruzou a altitude de saída), guarda o voo; quando some, confere. */
    private static void tick(MinecraftServer server) {
        if (!armed || done || ship == null || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        if (tradeDone) {
            // depois da troca: a conferência de estabilidade roda SETTLE_TICKS ticks depois (ver settle); então o voo virtual circulariza
            if (!settled) {
                if (++settleTicks >= SETTLE_TICKS) {
                    settled = true;
                    settle(server);
                }
                return;
            }
            circularize(server);
            return;
        }
        if (!ship.isRemoved() && !ShipConversions.isFrozen(ship)) {
            if (!(player.getVehicle() instanceof RocketEntity r && r == ship)) {
                riddenAll = false;
            }
            climbTicks++;
            // a virada de gravidade do piloto de missão, com o acelerador limitado a ASCENT_THROTTLE (órbita ligada, não fuga)
            var snap = ship.flight().snapshot();
            if (snap != null) {
                MissionPilot.Out out = pilot.step(snap, SpaceClock.ticks(server.overworld()));
                int flags = out.nextSas() ? RocketControlPayload.FLAG_NEXT_SAS : 0;
                ship.applyControl(new RocketControlPayload((float) out.pitch(), (float) out.yaw(), 0.0F,
                        (float) Math.min(out.throttle(), ASCENT_THROTTLE), (byte) flags));
            }
            // pousada, o estado de voo é nulo: altitude 0 até decolar
            FlightState now = snap == null ? null : snap.state();
            double alt = now == null ? 0.0 : altitudeOf(now);
            maxAltitude = Math.max(maxAltitude, alt);
            double half = Zones.exitAltitude(SolarSystem.EARTH.planet()) / 2.0;
            if (!halfMarked && alt >= half) {
                halfMarked = true;
                SpaceHorizons.LOGGER.info("{}SUBIDA metade: altitude {} blocos em {} ticks", TAG, f(alt), climbTicks);
                FlightDevtest.mark("sa_half");
            }
            if (snap != null && now != null && (climbTicks % 25 == 0 && climbTicks <= 300)) {
                // diagnóstico da virada: o nariz (em graus acima do horizonte local) e o comando de arfagem do piloto
                V3 nose = AttitudeMath.noseDirection(snap.attitude());
                double elevation = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, nose.dot(now.r().unit())))));
                double radial = now.v().dot(now.r().unit());
                double horizontal = now.v().minus(now.r().unit().times(radial)).length();
                SpaceHorizons.LOGGER.info("{}SUBIDA diag tick {}: altitude {} blocos, nariz {}° acima do horizonte, v radial {} b/t, v horizontal {} b/t",
                        TAG, climbTicks, f(alt), f(elevation), f(radial), f(horizontal));
            }
            if (climbTicks % 100 == 0) {
                SpaceHorizons.LOGGER.info("{}SUBIDA tick {}: altitude {} blocos, velocidade {} b/t, combustível {} mB", TAG, climbTicks, f(alt),
                        f(ship.getDeltaMovement().length()), f(ship.fuelMb()));
            }
            if (climbTicks > 4_000) {
                done = true;
                check("subida: a nave cruzou a altitude de saída em 4.000 ticks", false, "altitude máxima " + f(maxAltitude));
                FlightDevtest.mark("sa_done");
            }
            return;
        }
        if (!frozenMarked && ShipConversions.isFrozen(ship) && !ship.isRemoved()) {
            frozenMarked = true;
            FlightState at = ship.flight().snapshot().state();
            freezeAltitude = altitudeOf(at);
            freezeSpeed = at.v().length();
            freezeFuel = ship.fuelMb();
            check("cruzou a altitude de saída: a conversão começou (" + f(freezeAltitude) + " blocos)",
                    freezeAltitude >= Zones.exitAltitude(SolarSystem.EARTH.planet()), "altitude " + f(freezeAltitude));
            SpaceHorizons.LOGGER.info("{}ESTAVEL: entidade congelada, altitude {} blocos, velocidade {} b/t, piloto sentado: {}", TAG, f(freezeAltitude),
                    f(freezeSpeed), player.getVehicle() == ship);
            FlightDevtest.mark("sa_frozen");
        }
        if (ship.isRemoved()) {
            verify(server, player);
            tradeDone = true;
        }
    }

    /** Os mB dos tanques da caixa da nave no estaleiro: {quantidade de tanques, mB somados}. */
    private static int[] tanksMb(ServerLevel yard, ShipYard.Box box) {
        int tanks = 0;
        int mb = 0;
        for (int x = 0; x < box.sizeX(); x++) {
            for (int y = 0; y < box.sizeY(); y++) {
                for (int z = 0; z < box.sizeZ(); z++) {
                    BlockEntity be = yard.getBlockEntity(new BlockPos(box.minX() + x, box.minY() + y, box.minZ() + z));
                    if (be instanceof RocketFuelTankBlockEntity tank) {
                        tanks++;
                        mb += tank.fuelMb();
                    }
                }
            }
        }
        return new int[] {tanks, mb};
    }

    /**
     * A conferência de estabilidade, {@link #SETTLE_TICKS} ticks depois da troca: os mB dos tanques continuam iguais aos do instante da troca
     * (a nave parada não gasta combustível) e o acelerador do voo virtual segue em 0.
     */
    private static void settle(MinecraftServer server) {
        if (tradeRec == null) {
            return;
        }
        ServerLevel yard = ShipYard.level(server);
        if (yard == null) {
            check("estaleiro existe depois da troca", false, "sem dimensão do estaleiro");
            return;
        }
        int[] now = tanksMb(yard, tradeRec.box());
        check("mB dos tanques iguais " + SETTLE_TICKS + " ticks depois da troca (" + tradeTankMb + " mB no instante)", now[1] == tradeTankMb,
                "agora " + now[1] + " mB em " + now[0] + " tanques");
        ShipFlightTicker.Live live = ShipFlightTicker.live(tradeRec.id());
        check("acelerador 0 no voo virtual " + SETTLE_TICKS + " ticks depois da troca", live != null && live.state().throttle() == 0.0,
                live == null ? "sem voo" : "acelerador " + f(live.state().throttle()));
    }

    /** As conferências depois da troca: a nave no estaleiro com os blocos, o baú, os tanques, a coluna, o piloto e o voo virtual. */
    private static void verify(MinecraftServer server, ServerPlayer player) {
        ShipRecord rec = null;
        for (ShipRecord r : ShipRegistry.get(server).all()) {
            if (!idsBefore.contains(r.id()) && r.flies()) {
                rec = r;
            }
        }
        check("nave registrada no estaleiro com voo virtual", rec != null, rec == null ? "nenhuma nova" : "nave " + rec.id() + " fase " + rec.phase());
        if (rec == null) {
            return;
        }
        ServerLevel yard = ShipYard.level(server);
        if (yard == null) {
            check("estaleiro existe", false, "sem dimensão do estaleiro");
            return;
        }
        ShipYard.Box box = rec.box();
        int blocks = 0;
        int diamonds = 0;
        int iron = 0;
        int tanks = 0;
        int tankMb = 0;
        for (int x = 0; x < box.sizeX(); x++) {
            for (int y = 0; y < box.sizeY(); y++) {
                for (int z = 0; z < box.sizeZ(); z++) {
                    BlockPos pos = new BlockPos(box.minX() + x, box.minY() + y, box.minZ() + z);
                    if (!yard.getBlockState(pos).isAir()) {
                        blocks++;
                    }
                    BlockEntity be = yard.getBlockEntity(pos);
                    if (be instanceof ChestBlockEntity chest) {
                        for (int i = 0; i < chest.getContainerSize(); i++) {
                            ItemStack stack = chest.getItem(i);
                            if (stack.is(Items.DIAMOND)) {
                                diamonds += stack.getCount();
                            } else if (stack.is(Items.IRON_INGOT)) {
                                iron += stack.getCount();
                            }
                        }
                    }
                    if (be instanceof RocketFuelTankBlockEntity tank) {
                        tanks++;
                        tankMb += tank.fuelMb();
                    }
                }
            }
        }
        tradeRec = rec;
        tradeTankMb = tankMb;
        check("blocos: " + ShipGroundDevtest.expectedBlocks() + " no estaleiro (igual à nave no chão)", blocks == ShipGroundDevtest.expectedBlocks(),
                "blocos " + blocks);
        check("baú: " + GROUND_DIAMONDS + " diamantes e " + GROUND_IRON + " ferros, como no chão", diamonds == GROUND_DIAMONDS && iron == GROUND_IRON,
                "diamantes " + diamonds + ", ferros " + iron);
        // o combustível da subida foi gasto: a conferência é com o que a entidade tinha no instante da troca, repartido pelos tanques
        check("tanques: " + ShipGroundDevtest.tankCount() + " com o combustível da entidade no instante da troca (" + f(freezeFuel) + " mB)",
                tanks == ShipGroundDevtest.tankCount() && Math.abs(tankMb - freezeFuel) < 1.0, "tanques " + tanks + ", total " + tankMb + " mB");
        int columns = ShipCensusService.scanNow(server, rec).census().tankStructures().size();
        check("coluna de tanques formada (multibloco 2x2x4)", columns == 1 + EXTRA_COLUMNS, "colunas " + columns);
        check("entidade removida", ship.isRemoved(), "removida=" + ship.isRemoved());
        boolean inYard = ShipYard.is(player.level()) && player.getVehicle() instanceof ShipSeatEntity
                && ShipYard.cellContains(rec.cell(), player.getX(), player.getZ());
        check("piloto no estaleiro e sentado na ponte", inYard, "nível " + player.level().dimension().identifier() + ", veículo " + player.getVehicle());
        ShipFlightTicker.Live live = ShipFlightTicker.live(rec.id());
        check("voo virtual ativo", live != null, live == null ? "sem voo" : "ligado");
        if (live == null) {
            return;
        }
        check("acelerador 0 no voo virtual logo após a troca (o piloto religa se quiser)", live.state().throttle() == 0.0,
                "acelerador " + f(live.state().throttle()));
        double alt = altitudeOf(live.state().orbit());
        double speed = live.state().orbit().v().length();
        // o tick do job e os ticks de voo até a conferência andam a nave: a tolerância da altitude são 2 ticks de deslocamento na velocidade da troca
        double altTol = 2.0 * freezeSpeed + TOLERANCE * freezeAltitude;
        double altErr = Math.abs(alt - freezeAltitude);
        double speedErr = Math.abs(speed - freezeSpeed) / Math.max(1.0e-9, freezeSpeed);
        check("estado coerente: altitude " + f(alt) + " vs " + f(freezeAltitude) + " blocos (tolerância " + f(altTol) + ")", altErr <= altTol, "diferença " + f(altErr) + " blocos");
        check("estado coerente: velocidade " + f(speed) + " vs " + f(freezeSpeed) + " b/t", speedErr <= SPEED_TOLERANCE, "erro relativo " + f(speedErr * 100) + "%");
        // a órbita kepleriana do estado virtual logo após a troca (só registro: a conferência final é depois da circularização)
        Orbit orbit = Orbit.of(live.state().orbit(), SpaceClock.ticks(server.overworld()));
        double radius = live.state().orbit().body().planet().radius();
        double apoAlt = orbit.bound() ? orbit.apoapsis() - radius : Double.POSITIVE_INFINITY;
        double periAlt = orbit.periapsis() - radius;
        SpaceHorizons.LOGGER.info("{}ORBITA antes da circularização: apoapse {} periapse {} (altitudes sobre a superfície; ligada: {}), altitude máxima da subida {} blocos",
                TAG, f(apoAlt), f(periAlt), orbit.bound(), f(maxAltitude));
    }

    /**
     * A circularização no voo virtual, depois da conferência de estabilidade: o SAS vai para a horizontal (no apoastro ela é o prógrado, e
     * com o acelerador cheio a horizontal sobe a periapse), a nave coasta e queima na horizontal, com o acelerador em 1, até a periapse
     * passar de {@link #PERI_TARGET} de altitude ou acabar o combustível. A queima começa quando o tempo até o apoastro chega à metade da
     * duração estimada da queima.
     */
    private static void circularize(MinecraftServer server) {
        ShipFlightTicker.Live live = tradeRec == null ? null : ShipFlightTicker.live(tradeRec.id());
        if (live == null) {
            check("voo virtual para a circularização", false, "sem voo");
            finishCircularize(server);
            return;
        }
        int id = tradeRec.id();
        double clock = SpaceClock.ticks(server.overworld());
        FlightState st = live.state().orbit();
        Orbit o = Orbit.of(st, clock);
        double radius = st.body().planet().radius();
        double periAlt = o.periapsis() - radius;
        double apoAlt = o.bound() ? o.apoapsis() - radius : Double.POSITIVE_INFINITY;
        if (circPhase == 0 && circTicks == 0) {
            circSpeedStart = st.v().length();
            circFuelStart = st.fuelMb();
            SpaceHorizons.LOGGER.info("{}CIRCULARIZAÇÃO começa: apoapse {} periapse {} blocos de altitude, velocidade {} b/t, combustível {} mB", TAG,
                    f(apoAlt), f(periAlt), f(circSpeedStart), f(circFuelStart));
        }
        circTicks++;
        AttitudeMath.SasMode sas = live.state().sas();
        int flags = 0;
        double throttle = 0.0;
        if (circPhase == 0) {
            // pulsa o "próximo SAS" até a horizontal (o ciclo é desligado -> estabilizar -> prógrado -> retrógrado -> horizontal) e
            // espera a metade da queima estimada antes do apoastro (tempo até o apoastro, como MissionPilot): a queima começa com o nariz na horizontal
            if (!o.bound()) {
                check("órbita ligada para queimar no apoastro", false, "órbita aberta");
                finishCircularize(server);
                return;
            }
            double rApo = o.apoapsis();
            double vApo = st.r().cross(st.v()).length() / rApo;
            double deficit = BodyPhysics.of(st.body()).circularSpeed(rApo) - vApo;
            double burnTicks = st.fuelMb() > 0.0 ? live.stats().burnTime(st.fuelMb(), deficit) : Double.POSITIVE_INFINITY;
            if (sas == AttitudeMath.SasMode.HORIZONTAL && o.timeTo(Math.PI) <= 0.5 * burnTicks) {
                circPhase = 1;
                burnPlanned = burnTicks;
                SpaceHorizons.LOGGER.info("{}CIRCULARIZAÇÃO queima começa: déficit {} b/t, duração estimada {} ticks, tempo ao apoastro {} ticks, apoapse {} blocos de altitude",
                        TAG, f(deficit), f(burnTicks), f(o.timeTo(Math.PI)), f(rApo - radius));
            } else if (sas != AttitudeMath.SasMode.HORIZONTAL && circTicks % SAS_PULSE_TICKS == 1) {
                flags = RocketControlPayload.FLAG_NEXT_SAS;
            }
        }
        if (circPhase == 1) {
            // queima na horizontal com o acelerador cheio até a periapse passar do alvo (ou acabar o combustível)
            if (!o.bound()) {
                check("órbita ligada durante a queima", false, "órbita aberta com periapse " + f(periAlt));
                finishCircularize(server);
                return;
            }
            if (sas != AttitudeMath.SasMode.HORIZONTAL) {
                burnSasHorizontal = false;
            }
            // queima cheia na horizontal até a periapse passar do alvo; o teto (múltiplos da duração estimada no começo) só segura uma queima
            // que não converge. Sem corte pela velocidade radial: numa queima de ~20 ticks a gravidade sozinha já passa de 1 b/t de radial
            burnElapsed++;
            throttle = 1.0;
            if (periAlt >= PERI_TARGET) {
                finishCircularize(server);
                return;
            }
            if (burnElapsed > BURN_CAP_FACTOR * burnPlanned) {
                check("queima convergiu em " + f(BURN_CAP_FACTOR) + "x a duração estimada", false, "periapse " + f(periAlt) + " em " + burnElapsed + " ticks");
                finishCircularize(server);
                return;
            }
            if (st.fuelMb() <= 0.0) {
                check("combustível para a circularização", false, "sem combustível com periapse " + f(periAlt));
                finishCircularize(server);
                return;
            }
        }
        if (circTicks > CIRC_TIMEOUT) {
            check("circularização fechou em " + CIRC_TIMEOUT + " ticks", false, "periapse " + f(periAlt));
            finishCircularize(server);
            return;
        }
        if (circTicks % 10 == 0) {
            double proa = Math.toDegrees(ShipFlight.forward(live.state().attitude()).angleTo(st.v().unit()));
            SpaceHorizons.LOGGER.info("{}CIRCULARIZAÇÃO fase {} tick {}: apoapse {} periapse {} blocos de altitude, acelerador {}, SAS {}, combustível {} mB, proa a {} graus do prógrado, tempo ao apoastro {} ticks, v {} b/t",
                    TAG, circPhase, circTicks, f(apoAlt), f(periAlt), f(throttle), sas, f(st.fuelMb()), f(proa), f(o.timeTo(Math.PI)), f(st.v().length()));
        }
        ShipFlightTicker.applyControl(id, new RocketControlPayload(0.0F, 0.0F, 0.0F, (float) throttle, (byte) flags), server.overworld().getGameTime(), true);
    }

    /** Fecha a circularização: corta o acelerador, confere a órbita final e a queima, e encerra o autoteste. */
    private static void finishCircularize(MinecraftServer server) {
        ShipFlightTicker.Live live = tradeRec == null ? null : ShipFlightTicker.live(tradeRec.id());
        if (live != null) {
            ShipFlightTicker.applyControl(tradeRec.id(), new RocketControlPayload(0.0F, 0.0F, 0.0F, 0.0F, (byte) 0), server.overworld().getGameTime(), true);
            double clock = SpaceClock.ticks(server.overworld());
            FlightState st = live.state().orbit();
            Orbit o = Orbit.of(st, clock);
            double radius = st.body().planet().radius();
            double apoAlt = o.bound() ? o.apoapsis() - radius : Double.POSITIVE_INFINITY;
            double periAlt = o.periapsis() - radius;
            double spent = circFuelStart - st.fuelMb();
            SpaceHorizons.LOGGER.info("{}ORBITA depois da circularização: apoapse {} periapse {} blocos de altitude (ligada: {}), velocidade {} b/t, combustível gasto {} mB em {} ticks",
                    TAG, f(apoAlt), f(periAlt), o.bound(), f(st.v().length()), f(spent), circTicks);
            check("órbita ligada depois da circularização (apoapse " + f(apoAlt) + " blocos de altitude)", o.bound() && Double.isFinite(apoAlt),
                    "apoapse " + f(apoAlt));
            check("periapse acima de " + f(PERI_TARGET) + " blocos de altitude depois da circularização: " + f(periAlt) + " blocos",
                    periAlt >= PERI_TARGET, "periapse " + f(periAlt));
            check("SAS horizontal durante a queima", circPhase == 1 && burnSasHorizontal, "fase " + circPhase + ", horizontal " + burnSasHorizontal);
        }
        ShipFlightTicker.clearDevtestPilot(tradeRec == null ? -1 : tradeRec.id());
        circPhase = 3;
        done = true;
        FlightDevtest.mark("sa_done");
    }
}
