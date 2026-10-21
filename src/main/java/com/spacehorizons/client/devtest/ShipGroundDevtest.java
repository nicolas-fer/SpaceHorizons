package com.spacehorizons.client.devtest;

import java.util.List;
import java.util.Locale;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.content.connected.ConnectedFaces;
import com.spacehorizons.rocket.FlightMode;
import com.spacehorizons.rocket.RocketControlPayload;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketException;
import com.spacehorizons.rocket.RocketFuelTankBlock;
import com.spacehorizons.rocket.RocketFuelTankBlockEntity;
import com.spacehorizons.rocket.RocketAssembly;
import com.spacehorizons.rocket.RocketAssembler;
import com.spacehorizons.rocket.RocketPortrait;
import com.spacehorizons.ship.ShipAssembler;
import com.spacehorizons.ship.ShipAssembly;
import com.spacehorizons.ship.ShipKinds;
import com.spacehorizons.ship.ShipLiftoff;
import com.spacehorizons.ship.ShipSpecs;
import com.spacehorizons.ship.block.ShipImpulseEngineBlock;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Cenário {@code shipground} do autoteste: a nave montada NO CHÃO pela ponte ({@code ship_bridge}), como o clique agachado faz
 * ({@link ShipAssembler#assemble}). A nave tem 4 bocais de impulso para baixo, um horizontal, uma coluna de tanques 2x2x4 (multibloco),
 * um assento e um baú com diamantes. Confere: a busca acha a nave, a recusa sem motor para decolar (uma ponte sozinha), a montagem tira
 * os blocos do mundo, o retrato tem tudo (blocos, mB dos tanques, NBT do baú), {@code isShip}, o empuxo (só os bocais), o piloto sentado
 * e a subida de 500 blocos. Tudo vai para o log com {@code [AUTOTEST] SHIPGROUND}; as verificações saem como {@code CHECK PASSOU} ou
 * {@code CHECK FALHOU} (no log, a palavra de falha sai como {@code [REDACTED]}, como nos outros cenários).
 */
final class ShipGroundDevtest {
    static final String TAG = "[AUTOTEST] SHIPGROUND ";
    /** Origem do cenário: o chão (plataforma) fica em y = BASE.y; a nave ocupa x -2..2, z -2..2, y a partir de BASE.y + 1. */
    static final BlockPos BASE = com.spacehorizons.client.devtest.RocketDevtest.BASE;
    /** A ponte da nave pequena (sem motor), 12 blocos ao lado. */
    static final BlockPos SMALL = BASE.offset(12, 1, 0);
    /** Blocos esperados da nave grande: 25 do piso (com a ponte, 4+1 bocais), 16 tanques, assento e baú. */
    static final int EXPECTED_BLOCKS = 43;
    static final int TANK_COUNT = 16;
    /** Altura mínima da subida (blocos). */
    static final double MIN_CLIMB = 500.0;

    private static int passed;
    private static int failed;
    private static List<BlockPos> shipCells = List.of();
    private static RocketEntity ship;
    private static double startY;
    private static double maxAltitude;
    private static int flyTicks;
    private static boolean armed;
    private static boolean registered;
    private static boolean riddenAll = true;
    private static boolean aliveAll = true;
    private static double fuelAtStart;

    private ShipGroundDevtest() {
    }

    // ---------------------------------------------------------------- verificações

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

    private interface ServerWork {
        void run(MinecraftServer server, ServerLevel level, ServerPlayer player);
    }

    private static void onServer(Minecraft mc, String label, ServerWork work) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            SpaceHorizons.LOGGER.warn("{}{}: sem servidor integrado", TAG, label);
            return;
        }
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                work.run(server, player.level(), player);
            } catch (RuntimeException e) {
                SpaceHorizons.LOGGER.error(TAG + label + " falhou", e);
                check(label + " sem exceção", false, e.toString());
            }
        });
    }

    // ---------------------------------------------------------------- construção

    private static void put(ServerLevel level, BlockPos origin, int x, int y, int z, BlockState state) {
        level.setBlock(origin.offset(x, y, z), state, 2);
    }

    /** Limpa a área, põe a plataforma (y = BASE.y) e constrói a nave grande e a ponte pequena. */
    static void build(Minecraft mc) {
        onServer(mc, "construção", (server, level, player) -> {
            for (RocketEntity old : level.getEntitiesOfClass(RocketEntity.class, new AABB(BASE).inflate(200.0))) {
                old.discard();
            }
            for (int y = 1; y <= 12; y++) {
                for (int x = -6; x <= 18; x++) {
                    for (int z = -6; z <= 6; z++) {
                        level.setBlock(BASE.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            BlockState pad = ModBlocks.LAUNCH_PAD.get().defaultBlockState();
            for (int x = -4; x <= 16; x++) {
                for (int z = -4; z <= 4; z++) {
                    level.setBlock(BASE.offset(x, 0, z), pad, 2);
                }
            }
            // piso 5x5 (y = 1): a ponte no centro, bocais para baixo nos quatro cantos de dentro, um horizontal, o resto casco
            BlockState hull = ModBlocks.ROCKET_HULL.get().defaultBlockState();
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    put(level, BASE, x, 1, z, hull);
                }
            }
            put(level, BASE, 0, 1, 0, ModBlocks.SHIP_BRIDGE.get().defaultBlockState());
            for (int[] p : new int[][] {{1, 1}, {2, 1}, {1, 2}, {2, 2}}) {
                put(level, BASE, p[0], 1, p[1], nozzle(Direction.DOWN));
            }
            put(level, BASE, 2, 1, 0, nozzle(Direction.EAST));
            // coluna de tanques 2x2x4 (y = 2..5) sobre o piso, conectada e cheia
            for (int y = 2; y <= 5; y++) {
                for (int x = -2; x <= -1; x++) {
                    for (int z = -2; z <= -1; z++) {
                        put(level, BASE, x, y, z, ModBlocks.ROCKET_FUEL_TANK.get().defaultBlockState());
                    }
                }
            }
            for (int y = 2; y <= 5; y++) {
                for (int x = -2; x <= -1; x++) {
                    for (int z = -2; z <= -1; z++) {
                        BlockPos pos = BASE.offset(x, y, z);
                        ConnectedFaces.connectAround(level, pos, ModBlocks.ROCKET_FUEL_TANK.get(), RocketFuelTankBlock.rule());
                        if (level.getBlockEntity(pos) instanceof RocketFuelTankBlockEntity tank) {
                            tank.setFuel(4000);
                        }
                    }
                }
            }
            put(level, BASE, 0, 2, 0, ModBlocks.SHIP_SEAT.get().defaultBlockState());
            BlockPos chest = BASE.offset(1, 2, -1);
            level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), 2);
            if (level.getBlockEntity(chest) instanceof Container box) {
                box.setItem(0, new ItemStack(Items.DIAMOND, 10));
                box.setItem(1, new ItemStack(Items.IRON_INGOT, 20));
            }
            // a nave pequena: só a ponte, sobre a plataforma
            level.setBlock(SMALL, ModBlocks.SHIP_BRIDGE.get().defaultBlockState(), 2);

            RocketAssembly.Result found = scanOrNull(level, BASE.offset(0, 1, 0));
            shipCells = found == null ? List.of() : found.cells().stream().map(c -> new BlockPos(c.x(), c.y(), c.z())).toList();
            int tanks = 0;
            for (int y = 2; y <= 5; y++) {
                for (int x = -2; x <= -1; x++) {
                    for (int z = -2; z <= -1; z++) {
                        tanks += level.getBlockEntity(BASE.offset(x, y, z)) instanceof RocketFuelTankBlockEntity t && t.fuelMb() == 4000 ? 1 : 0;
                    }
                }
            }
            int down = 0;
            int horizontal = 0;
            for (int[] p : new int[][] {{1, 1}, {2, 1}, {1, 2}, {2, 2}}) {
                down += level.getBlockState(BASE.offset(p[0], 1, p[1])).getValue(ShipImpulseEngineBlock.FACING) == Direction.DOWN ? 1 : 0;
            }
            horizontal += level.getBlockState(BASE.offset(2, 1, 0)).getValue(ShipImpulseEngineBlock.FACING) == Direction.EAST ? 1 : 0;
            SpaceHorizons.LOGGER.info("{}CONSTRUÍDO: {} blocos achados pela busca (esperados {}), bocais para baixo {}, horizontal {}, tanques cheios {}",
                    TAG, shipCells.size(), EXPECTED_BLOCKS, down, horizontal, tanks);
            check("construção: a busca acha a nave com " + EXPECTED_BLOCKS + " blocos", shipCells.size() == EXPECTED_BLOCKS,
                    "achados " + shipCells.size());
            check("construção: 4 bocais de impulso para baixo e 1 horizontal no mundo", down == 4 && horizontal == 1,
                    "baixo " + down + ", horizontal " + horizontal);
            check("construção: " + TANK_COUNT + " tanques cheios (4.000 mB cada)", tanks == TANK_COUNT, "cheios " + tanks);
        });
    }

    private static BlockState nozzle(Direction facing) {
        return ModBlocks.SHIP_IMPULSE_ENGINE.get().defaultBlockState().setValue(ShipImpulseEngineBlock.FACING, facing);
    }

    private static RocketAssembly.Result scanOrNull(ServerLevel level, BlockPos bridge) {
        try {
            return ShipAssembly.scan(RocketAssembler.worldGrid(level, ShipKinds::assemblyKind), bridge.getX(), bridge.getY(), bridge.getZ());
        } catch (RocketException e) {
            SpaceHorizons.LOGGER.warn(TAG + "busca recusou a nave: {}", e.reason());
            return null;
        }
    }

    // ---------------------------------------------------------------- recusa e montagem

    /** A ponte pequena (só ponte, sem motor) é recusada e nada sai do mundo. */
    static void refusal(Minecraft mc) {
        onServer(mc, "recusa sem motor", (server, level, player) -> {
            try {
                ShipAssembler.assemble(level, SMALL, player);
                check("sem motor: a montagem recusa a ponte sozinha", false, "montou");
            } catch (RocketException e) {
                check("sem motor: a montagem recusa a ponte sozinha (NO_LIFTOFF_ENGINE)", e.reason() == RocketException.Reason.NO_LIFTOFF_ENGINE,
                        "motivo " + e.reason());
            }
            check("sem motor: a ponte pequena continua no mundo", level.getBlockState(SMALL).is(ModBlocks.SHIP_BRIDGE.get()), level.getBlockState(SMALL).toString());
        });
    }

    /** Monta a nave grande pela ponte, como o clique agachado: confere o retrato, o mundo, o empuxo e o piloto. */
    static void assemble(Minecraft mc) {
        onServer(mc, "montagem", (server, level, player) -> {
            try {
                ship = ShipAssembler.assemble(level, BASE.offset(0, 1, 0), player);
                // o gatilho (ShipAssembler.assembleFor) senta o jogador depois de montar; o teste faz o mesmo
                player.startRiding(ship, true, true);
            } catch (RocketException e) {
                check("montagem aceita", false, e.getMessage());
                FlightDevtest.mark("failed");
                return;
            }
            RocketPortrait portrait = ship.portrait();
            check("montagem: a entidade é uma nave (isShip)", ship.isShip(), "isShip=" + ship.isShip());
            check("retrato: " + EXPECTED_BLOCKS + " blocos", portrait.blockCount() == EXPECTED_BLOCKS && portrait.blockCount() == shipCells.size(),
                    "retrato " + portrait.blockCount() + " de " + shipCells.size());
            double tankMb = TANK_COUNT * 4000.0;
            check("retrato: " + TANK_COUNT + " tanques com " + (int) tankMb + " mB (inclusive a coluna 2x2x4)",
                    portrait.fuelInTanksMb() == (int) tankMb && Math.abs(ship.fuelMb() - tankMb) < 1.0,
                    "retrato " + portrait.fuelInTanksMb() + " mB, entidade " + f(ship.fuelMb()) + " mB");
            check("retrato: coluna de tanques formada (multibloco 2x2x4)", portrait.census().tankStructures().size() == 1,
                    "colunas " + portrait.census().tankStructures().size());
            boolean diamonds = false;
            for (var be : portrait.blockEntities().values()) {
                diamonds |= be.toString().contains("minecraft:diamond");
            }
            check("retrato: NBT do baú com os diamantes", diamonds, "blockEntities=" + portrait.blockEntities().size());
            int left = 0;
            for (int y = 1; y <= 6; y++) {
                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) {
                        left += level.getBlockState(BASE.offset(x, y, z)).isAir() ? 0 : 1;
                    }
                }
            }
            check("mundo: nenhum bloco da nave sobrou no chão", left == 0, "blocos sobrando " + left);
            double expectedThrust = 4 * ShipSpecs.ENGINE_THRUST;
            check("empuxo: só os 4 bocais para baixo (" + f(expectedThrust) + " t·b/t²)", Math.abs(ship.stats().thrust() - expectedThrust) < 1.0e-9,
                    "empuxo " + f(ship.stats().thrust()) + " (decolagem " + f(ShipLiftoff.thrust(new ShipLiftoff.Nozzles(0, 4, 1), portrait.census())) + ")");
            check("piloto sentado na nave", player.getVehicle() == ship, "veículo=" + player.getVehicle());
            fuelAtStart = ship.fuelMb();
            SpaceHorizons.LOGGER.info("{}MONTADA: {} blocos, caixa {}x{}x{}, massa seca {} t, empuxo {}, combustível {} mB, Isp {}, TWR {}",
                    TAG, portrait.blockCount(), portrait.sizeX, portrait.sizeY, portrait.sizeZ, f(ship.stats().dryMassT()), f(ship.stats().thrust()),
                    f(ship.fuelMb()), f(ship.stats().ispTicks()), f(ship.stats().twr(ship.fuelMb(), 0.08)));
            FlightDevtest.mark("sg_assembled");
        });
    }

    // ---------------------------------------------------------------- voo

    /** Liga o acelerador no piloto sentado até passar de {@link #MIN_CLIMB} (ou o tempo acabar) e depois corta. */
    static void fly(Minecraft mc) {
        onServer(mc, "partida", (server, level, player) -> {
            if (ship == null || !(player.getVehicle() instanceof RocketEntity)) {
                check("voo: piloto sentado antes de decolar", false, "veículo=" + player.getVehicle());
                FlightDevtest.mark("sg_flown");
                return;
            }
            startY = ship.getY();
            maxAltitude = 0.0;
            flyTicks = 0;
            riddenAll = true;
            aliveAll = true;
            armed = true;
            if (!registered) {
                registered = true;
                NeoForge.EVENT_BUS.addListener(ServerTickEvent.Pre.class, e -> flyTick(e.getServer()));
            }
            SpaceHorizons.LOGGER.info("{}VOO: empuxo {} t·b/t², combustível {} mB, massa seca {} t", TAG, f(ship.stats().thrust()), f(fuelAtStart),
                    f(ship.stats().dryMassT()));
        });
    }

    private static void flyTick(MinecraftServer server) {
        if (!armed || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        if (!(player.getVehicle() instanceof RocketEntity rocket) || rocket != ship) {
            riddenAll = false;
            return;
        }
        flyTicks++;
        if (rocket.isRemoved()) {
            aliveAll = false;
        }
        double altitude = rocket.getY() - startY;
        maxAltitude = Math.max(maxAltitude, altitude);
        boolean climbing = altitude < MIN_CLIMB && flyTicks < 1_500;
        float throttle = climbing ? 1.0F : 0.0F;
        rocket.applyControl(new RocketControlPayload(0.0F, 0.0F, 0.0F, throttle, (byte) 0));
        if (flyTicks % 40 == 0) {
            FlightMode mode = rocket.flight().mode();
            SpaceHorizons.LOGGER.info("{}VOO tick {}: modo {} altitude {} velocidade {} combustível {} mB", TAG, flyTicks, mode, f(altitude),
                    f(rocket.getDeltaMovement().length()), f(rocket.fuelMb()));
        }
        if (!climbing) {
            armed = false;
            FlightMode mode = rocket.flight().mode();
            check("voo: a nave subiu " + (int) MIN_CLIMB + " blocos ou mais", maxAltitude >= MIN_CLIMB, "altitude máxima " + f(maxAltitude) + " em " + flyTicks + " ticks");
            check("voo: a nave continua inteira e registrada no voo (não some)", aliveAll && !rocket.isRemoved(), "removida=" + rocket.isRemoved());
            check("voo: o piloto continuou sentado durante a subida", riddenAll, "modo " + mode + ", combustível " + f(rocket.fuelMb()) + " mB (gasto "
                    + f(fuelAtStart - rocket.fuelMb()) + ")");
            FlightDevtest.mark("sg_flown");
        }
    }
}
