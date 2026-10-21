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
import com.spacehorizons.ship.ShipFlightTicker;
import com.spacehorizons.ship.ShipRecord;
import com.spacehorizons.ship.ShipRegistry;
import com.spacehorizons.ship.ShipSeatEntity;
import com.spacehorizons.ship.ShipYard;
import com.spacehorizons.space.SpaceClock;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.flight.Orbit;
import com.spacehorizons.space.flight.FlightState;
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
    static final int TANK_COUNT = 16;
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
        ShipGroundDevtest.build(mc);
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
            check("montagem: " + ShipGroundDevtest.EXPECTED_BLOCKS + " blocos no retrato", portrait.blockCount() == ShipGroundDevtest.EXPECTED_BLOCKS,
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
        if (!ship.isRemoved() && !ShipConversions.isFrozen(ship)) {
            if (!(player.getVehicle() instanceof RocketEntity r && r == ship)) {
                riddenAll = false;
            }
            climbTicks++;
            ship.applyControl(new RocketControlPayload(0.0F, 0.0F, 0.0F, 1.0F, (byte) 0));
            // pousada, o estado de voo é nulo: altitude 0 até decolar
            FlightState now = ship.flight().snapshot().state();
            double alt = now == null ? 0.0 : altitudeOf(now);
            maxAltitude = Math.max(maxAltitude, alt);
            double half = Zones.exitAltitude(SolarSystem.EARTH.planet()) / 2.0;
            if (!halfMarked && alt >= half) {
                halfMarked = true;
                SpaceHorizons.LOGGER.info("{}SUBIDA metade: altitude {} blocos em {} ticks", TAG, f(alt), climbTicks);
                FlightDevtest.mark("sa_half");
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
            done = true;
            verify(server, player);
            FlightDevtest.mark("sa_done");
        }
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
        check("blocos: " + ShipGroundDevtest.EXPECTED_BLOCKS + " no estaleiro (igual à nave no chão)", blocks == ShipGroundDevtest.EXPECTED_BLOCKS,
                "blocos " + blocks);
        check("baú: " + GROUND_DIAMONDS + " diamantes e " + GROUND_IRON + " ferros, como no chão", diamonds == GROUND_DIAMONDS && iron == GROUND_IRON,
                "diamantes " + diamonds + ", ferros " + iron);
        // o combustível da subida foi gasto: a conferência é com o que a entidade tinha no instante da troca, repartido pelos tanques
        check("tanques: " + TANK_COUNT + " com o combustível da entidade no instante da troca (" + f(freezeFuel) + " mB)",
                tanks == TANK_COUNT && Math.abs(tankMb - freezeFuel) < 1.0, "tanques " + tanks + ", total " + tankMb + " mB");
        int columns = ShipCensusService.scanNow(server, rec).census().tankStructures().size();
        check("coluna de tanques formada (multibloco 2x2x4)", columns == 1, "colunas " + columns);
        check("entidade removida", ship.isRemoved(), "removida=" + ship.isRemoved());
        boolean inYard = ShipYard.is(player.level()) && player.getVehicle() instanceof ShipSeatEntity
                && ShipYard.cellContains(rec.cell(), player.getX(), player.getZ());
        check("piloto no estaleiro e sentado na ponte", inYard, "nível " + player.level().dimension().identifier() + ", veículo " + player.getVehicle());
        ShipFlightTicker.Live live = ShipFlightTicker.live(rec.id());
        check("voo virtual ativo", live != null, live == null ? "sem voo" : "ligado");
        if (live == null) {
            return;
        }
        double alt = altitudeOf(live.state().orbit());
        double speed = live.state().orbit().v().length();
        // o tick do job e os ticks de voo até a conferência andam a nave: a tolerância da altitude são 2 ticks de deslocamento na velocidade da troca
        double altTol = 2.0 * freezeSpeed + TOLERANCE * freezeAltitude;
        double altErr = Math.abs(alt - freezeAltitude);
        double speedErr = Math.abs(speed - freezeSpeed) / Math.max(1.0e-9, freezeSpeed);
        check("estado coerente: altitude " + f(alt) + " vs " + f(freezeAltitude) + " blocos (tolerância " + f(altTol) + ")", altErr <= altTol, "diferença " + f(altErr) + " blocos");
        check("estado coerente: velocidade " + f(speed) + " vs " + f(freezeSpeed) + " b/t", speedErr <= SPEED_TOLERANCE, "erro relativo " + f(speedErr * 100) + "%");
        Orbit orbit = Orbit.of(live.state().orbit(), SpaceClock.ticks(server.overworld()));
        SpaceHorizons.LOGGER.info("{}ORBITA: apoapse {} periapse {} (ligada: {}), altitude máxima da subida {} blocos", TAG, f(orbit.apoapsis()),
                f(orbit.periapsis()), orbit.bound(), f(maxAltitude));
        // apoapse infinita = órbita não ligada (hiperbólica): a conferência é só o periapse, que tem que ser um número",
        check("órbita calculada (periapse registrado; apoapse " + (orbit.bound() ? "finita" : "infinita, não ligada") + ")", !Double.isNaN(orbit.periapsis()), "peri " + f(orbit.periapsis()));
    }
}
