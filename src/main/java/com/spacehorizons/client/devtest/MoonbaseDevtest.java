package com.spacehorizons.client.devtest;

import java.util.List;
import java.util.Locale;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.content.ModItems;
import com.spacehorizons.moonbase.machine.MachineStatus;
import com.spacehorizons.moonbase.machine.MoonBlockEntity;
import com.spacehorizons.moonbase.room.PressurizedRooms;
import com.spacehorizons.planet.PlanetTerrain;
import com.spacehorizons.planet.PlanetTerrains;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.SurfaceType;
import com.spacehorizons.planet.terrain.OreVeins;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SunModel;
import com.spacehorizons.survival.fuel.FuelCanisterItem;
import com.spacehorizons.survival.oxygen.OxygenMath;
import com.spacehorizons.survival.oxygen.OxygenSetup;
import com.spacehorizons.survival.oxygen.OxygenStorage;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Só em dev: as montagens e conferências do autoteste {@code -PshOnly=moonbase} (o roteiro fica no fim do AutoTest). Constrói na Lua uma sala
 * selada de 5x3x5 com porta de ferro, gerador de oxigênio, bateria, cabos e painéis no teto, e uma fábrica ao lado (painéis, bateria,
 * eletrolisador, refinaria lunar e extrator de hélio-3); confere o ar da sala com e sem capacete, o vazamento pela porta aberta, a produção
 * das máquinas e o veio de gelo das crateras polares. Log com o prefixo {@code [AUTOTEST] MOONBASE}.
 */
final class MoonbaseDevtest {
    private static final String TAG = "[AUTOTEST] MOONBASE ";
    /** Canto mínimo da casca da sala (x 0..6, y 0..4, z 0..6 a partir daqui). */
    private static BlockPos room;
    /** Primeiro bloco da fábrica (fileira de cabos ao longo de +x). */
    private static BlockPos factory;
    static long noonClock;
    static long nightClock;
    private static int passed;
    private static int failed;
    private static int tankAtHelmetOff;
    private static int[] ice;

    private MoonbaseDevtest() {
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        SpaceHorizons.LOGGER.info(TAG + "CHECK {} {}: {}", ok ? "PASSOU" : "FALHOU", name, detail);
    }

    static void summary() {
        SpaceHorizons.LOGGER.info(TAG + "RESUMO: {} verificações passaram, {} falharam", passed, failed);
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
        try {
            server.submit(() -> {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                work.run(server, player.level(), player);
            }).get(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            SpaceHorizons.LOGGER.error(TAG + label + " falhou", e);
            check(label + " sem exceção", false, e.toString());
        }
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    static String roomCenter() {
        return (room.getX() + 3.5) + " " + (room.getY() + 1.0) + " " + (room.getZ() + 3.5);
    }

    /** Vista de fora para a sala e a fábrica (olhando para o sul). */
    static String viewOutside() {
        return (room.getX() + 8.0) + " " + (room.getY() + 5.0) + " " + (room.getZ() - 12.0);
    }

    /** Vista da fábrica pelo lado sul (olhando para o norte). */
    static String viewFactory() {
        return (factory.getX() + 3.0) + " " + (factory.getY() + 3.0) + " " + (factory.getZ() + 8.0);
    }

    private static BlockPos at(BlockPos origin, int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    private static void put(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static MoonBlockEntity be(ServerLevel level, BlockPos pos) {
        return (MoonBlockEntity) level.getBlockEntity(pos);
    }

    // ------------------------------------------------------------------------------------------ montagem

    /** Nivela o terreno, constrói a sala e a fábrica e calcula o relógio do meio-dia e da noite no lugar. */
    static void build(Minecraft mc) {
        onServer(mc, "montagem", (server, level, player) -> {
            int ox = player.getBlockX() + 4;
            int oz = player.getBlockZ() + 4;
            PlanetTerrain terrain = PlanetTerrains.analytic(Planets.MOON);
            int top = 0;
            for (int x = ox - 2; x <= ox + 22; x++) {
                for (int z = oz - 3; z <= oz + 9; z++) {
                    top = Math.max(top, terrain.surfaceHeight(x, z));
                }
            }
            int y0 = top + 1;
            // limpa o volume e nivela o chão com andesito até y0 - 1
            for (int x = ox - 2; x <= ox + 22; x++) {
                for (int z = oz - 3; z <= oz + 9; z++) {
                    for (int y = terrain.surfaceHeight(x, z) + 1; y < y0; y++) {
                        put(level, new BlockPos(x, y, z), Blocks.ANDESITE.defaultBlockState());
                    }
                    for (int y = y0; y <= y0 + 8; y++) {
                        put(level, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }
            room = new BlockPos(ox, y0, oz);
            buildRoom(level);
            factory = new BlockPos(ox + 10, y0, oz + 3);
            buildFactory(level);

            // relógios do meio-dia e da noite no lugar (o sol local da Lua, varrendo um dia lunar de 192.000 ticks)
            double[] sun = new double[3];
            double best = -2;
            double worst = 2;
            var body = SolarSystem.skyBodyOf(Planets.MOON);
            for (long t = 0; t < 400_000; t += 250) {
                SunModel.localDirection(body, ox + 3.5, oz + 3.5, t, sun);
                if (sun[1] > best) {
                    best = sun[1];
                    noonClock = t;
                }
                if (sun[1] < worst) {
                    worst = sun[1];
                    nightClock = t;
                }
            }
            SpaceHorizons.LOGGER.info(TAG + "sala em {} fábrica em {}; meio-dia no relógio {} (altura {}), noite no {} (altura {})", room, factory,
                    noonClock, f(best), nightClock, f(worst));
        });
    }

    private static void buildRoom(ServerLevel level) {
        BlockState steel = ModBlocks.STEEL_BLOCK.get().defaultBlockState();
        for (int x = 0; x <= 6; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = 0; z <= 6; z++) {
                    boolean inside = x >= 1 && x <= 5 && y >= 1 && y <= 3 && z >= 1 && z <= 5;
                    put(level, at(room, x, y, z), inside ? Blocks.AIR.defaultBlockState() : steel);
                }
            }
        }
        // porta de ferro na parede z = 0, no meio
        BlockState door = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        put(level, at(room, 3, 1, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        put(level, at(room, 3, 2, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // painéis no teto, cabos logo abaixo, bateria e gerador de oxigênio na ponta
        for (int x = 2; x <= 4; x++) {
            put(level, at(room, x, 4, 2), ModBlocks.SOLAR_PANEL.get().defaultBlockState());
            put(level, at(room, x, 3, 2), ModBlocks.POWER_CABLE.get().defaultBlockState());
        }
        put(level, at(room, 5, 3, 2), ModBlocks.BATTERY.get().defaultBlockState());
        put(level, at(room, 5, 2, 2), ModBlocks.OXYGEN_GENERATOR.get().defaultBlockState());
        ItemStack tank = new ItemStack(ModItems.OXYGEN_TANK_SMALL.get());
        OxygenStorage.set(tank, OxygenStorage.capacity(tank));
        be(level, at(room, 5, 2, 2)).setItem(0, tank);
    }

    private static void buildFactory(ServerLevel level) {
        for (int i = 0; i < 4; i++) {
            put(level, at(factory, i, 0, 0), ModBlocks.POWER_CABLE.get().defaultBlockState());
            put(level, at(factory, i, 1, 0), ModBlocks.SOLAR_PANEL.get().defaultBlockState());
        }
        put(level, at(factory, 4, 0, 0), ModBlocks.BATTERY.get().defaultBlockState());
        put(level, at(factory, 4, 0, 1), ModBlocks.ELECTROLYZER.get().defaultBlockState());
        put(level, at(factory, 5, 0, 0), ModBlocks.LUNAR_REFINERY.get().defaultBlockState());
        put(level, at(factory, 4, 0, -1), ModBlocks.HELIUM3_EXTRACTOR.get().defaultBlockState());
        be(level, at(factory, 4, 0, 0)).setEnergy(50_000);

        MoonBlockEntity electrolyzer = be(level, at(factory, 4, 0, 1));
        electrolyzer.setItem(0, MoonBlockEntity.iceStack(2));
        electrolyzer.setItem(1, new ItemStack(ModItems.OXYGEN_TANK_SMALL.get()));
        electrolyzer.setItem(2, new ItemStack(ModItems.HYDROGEN_CANISTER.get()));
        MoonBlockEntity refinery = be(level, at(factory, 5, 0, 0));
        refinery.setItem(0, MoonBlockEntity.iceStack(3));
        refinery.setItem(2, new ItemStack(ModItems.FUEL_CANISTER.get()));
        MoonBlockEntity extractor = be(level, at(factory, 4, 0, -1));
        extractor.setItem(0, new ItemStack(Blocks.STONE.asItem(), 4));
        extractor.setItem(2, new ItemStack(ModItems.HELIUM3_CANISTER.get()));
    }

    /** Veste o traje inteiro com um tanque pequeno cheio no inventário e põe o jogador em sobrevivência. */
    static void dressUp(Minecraft mc) {
        onServer(mc, "traje", (server, level, player) -> {
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.SPACE_HELMET.get()));
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.SPACE_CHESTPLATE.get()));
            player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.SPACE_LEGGINGS.get()));
            player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SPACE_BOOTS.get()));
            ItemStack tank = new ItemStack(ModItems.OXYGEN_TANK_SMALL.get());
            OxygenStorage.set(tank, OxygenStorage.capacity(tank));
            player.getInventory().setItem(0, tank);
        });
    }

    // ------------------------------------------------------------------------------------------ conferências

    /** A sala está selada e pressurizada, as energias fluem e os painéis geram ao meio-dia. */
    static void checkPressurized(Minecraft mc) {
        onServer(mc, "sala pressurizada", (server, level, player) -> {
            MoonBlockEntity gen = be(level, at(room, 5, 2, 2));
            MoonBlockEntity battery = be(level, at(room, 5, 3, 2));
            MoonBlockEntity panel = be(level, at(room, 3, 4, 2));
            check("painel_gera_ao_meio_dia", panel.generation() > 0, "geração=" + panel.generation() + " UE/t");
            check("bateria_carregou_pelos_cabos", battery.energy() > 0 || gen.energy() > 0,
                    "bateria=" + battery.energy() + " gerador=" + gen.energy());
            check("gerador_pressurizou", gen.status() == MachineStatus.PRESSURIZED && gen.pressure() >= 500,
                    "situação=" + gen.status() + " pressão=" + gen.pressure() + " oxigênio=" + gen.oxygen());
            BlockPos eye = BlockPos.containing(player.getEyePosition());
            check("sala_registrada_com_ar", PressurizedRooms.breathableAt(level, eye) && !OxygenSetup.isInVacuum(player),
                    "olhos em " + eye + " vácuo do local=" + OxygenSetup.isVacuumAt(level, player.getEyeY()));
        });
    }

    static void removeHelmet(Minecraft mc) {
        onServer(mc, "tirar capacete", (server, level, player) -> {
            tankAtHelmetOff = totalTank(player);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            SpaceHorizons.LOGGER.info(TAG + "capacete tirado; tanque do traje={}", tankAtHelmetOff);
        });
    }

    private static int totalTank(ServerPlayer player) {
        return com.spacehorizons.survival.suit.SuitGear.totalOxygen(player);
    }

    /** Sem capacete dentro da sala: reserva cheia, tanque intacto, sem congelar. */
    static void checkBreathingWithoutHelmet(Minecraft mc) {
        onServer(mc, "respirar sem capacete", (server, level, player) -> {
            int reserve = player.getData(OxygenSetup.OXYGEN).reserve();
            check("sem_capacete_reserva_cheia", reserve == OxygenMath.MAX_RESERVE, "reserva=" + reserve);
            check("sem_capacete_tanque_intacto", totalTank(player) == tankAtHelmetOff, "tanque antes=" + tankAtHelmetOff + " depois=" + totalTank(player));
            check("sem_capacete_nao_congela", player.getTicksFrozen() == 0, "ticksFrozen=" + player.getTicksFrozen());
        });
    }

    static void setDoor(Minecraft mc, boolean open) {
        onServer(mc, open ? "abrir a porta" : "fechar a porta", (server, level, player) -> {
            for (int y = 1; y <= 2; y++) {
                BlockPos pos = at(room, 3, y, 0);
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof DoorBlock) {
                    put(level, pos, state.setValue(DoorBlock.OPEN, open));
                }
            }
        });
    }

    /** Porta aberta: a sala vaza, a pressão cai a zero e o jogador sem capacete está no vácuo. */
    static void checkLeak(Minecraft mc) {
        onServer(mc, "vazamento", (server, level, player) -> {
            MoonBlockEntity gen = be(level, at(room, 5, 2, 2));
            check("porta_aberta_vaza", gen.status() == MachineStatus.LEAK || gen.status() == MachineStatus.TOO_BIG,
                    "situação=" + gen.status() + " pressão=" + gen.pressure());
            check("sala_despressurizou", gen.pressure() == 0 && !PressurizedRooms.breathableAt(level, BlockPos.containing(player.getEyePosition())),
                    "pressão=" + gen.pressure());
            check("sem_ar_e_vacuo_de_novo", OxygenSetup.isInVacuum(player), "vácuo=" + OxygenSetup.isInVacuum(player));
        });
    }

    /** Depois de um tempo no vácuo sem capacete: a reserva caiu e o corpo está congelando. */
    static void checkSuffering(Minecraft mc) {
        onServer(mc, "sofrendo", (server, level, player) -> {
            int reserve = player.getData(OxygenSetup.OXYGEN).reserve();
            check("sem_ar_reserva_cai", reserve < OxygenMath.MAX_RESERVE, "reserva=" + reserve);
            check("sem_ar_congela", player.getTicksFrozen() > 0, "ticksFrozen=" + player.getTicksFrozen());
        });
    }

    /** Porta fechada de novo: a sala volta a pressurizar e o jogador volta a respirar. */
    static void checkRepressurized(Minecraft mc) {
        onServer(mc, "repressurizar", (server, level, player) -> {
            MoonBlockEntity gen = be(level, at(room, 5, 2, 2));
            check("porta_fechada_repressuriza", gen.status() == MachineStatus.PRESSURIZED || gen.pressure() >= 500,
                    "situação=" + gen.status() + " pressão=" + gen.pressure());
            int reserve = player.getData(OxygenSetup.OXYGEN).reserve();
            check("volta_a_respirar", reserve > 0 && !OxygenSetup.isInVacuum(player), "reserva=" + reserve);
        });
    }

    /** O gerador gasta o tanque do slot e o gelo dele: dá gelo, o reservatório sobe. */
    static void checkGeneratorFeeds(Minecraft mc) {
        onServer(mc, "gerador gelo", (server, level, player) -> {
            MoonBlockEntity gen = be(level, at(room, 5, 2, 2));
            gen.setItem(1, MoonBlockEntity.iceStack(1));
        });
    }

    static void checkGeneratorIce(Minecraft mc) {
        onServer(mc, "gerador gelo", (server, level, player) -> {
            MoonBlockEntity gen = be(level, at(room, 5, 2, 2));
            check("gerador_converte_gelo_em_oxigenio", gen.getItem(1).isEmpty() && gen.oxygen() > 1_000,
                    "gelo restante=" + gen.getItem(1).getCount() + " oxigênio=" + gen.oxygen());
        });
    }

    /** Eletrólise, refino e extração terminaram e encheram os galões e o tanque. */
    static void checkFactory(Minecraft mc) {
        onServer(mc, "fábrica", (server, level, player) -> {
            MoonBlockEntity battery = be(level, at(factory, 4, 0, 0));
            MoonBlockEntity electrolyzer = be(level, at(factory, 4, 0, 1));
            MoonBlockEntity refinery = be(level, at(factory, 5, 0, 0));
            MoonBlockEntity extractor = be(level, at(factory, 4, 0, -1));
            ItemStack o2 = electrolyzer.getItem(1);
            ItemStack h2 = electrolyzer.getItem(2);
            ItemStack fuel = refinery.getItem(2);
            ItemStack he3 = extractor.getItem(2);
            check("eletrolisador_encheu_o_tanque_de_oxigenio", OxygenStorage.get(o2) == 2_400, "oxigênio no tanque=" + OxygenStorage.get(o2)
                    + " no reservatório=" + electrolyzer.oxygen());
            check("eletrolisador_encheu_o_galao_de_hidrogenio", FuelCanisterItem.getFuel(h2) == 1_000,
                    "hidrogênio no galão=" + FuelCanisterItem.getFuel(h2) + " no reservatório=" + electrolyzer.fluidMb());
            check("refinaria_encheu_o_galao_de_combustivel", FuelCanisterItem.getFuel(fuel) == 1_000,
                    "combustível no galão=" + FuelCanisterItem.getFuel(fuel) + " no reservatório=" + refinery.fluidMb()
                            + " gelo restante=" + refinery.getItem(0).getCount());
            check("extrator_encheu_o_galao_de_helio3", FuelCanisterItem.getFuel(he3) == 1_000,
                    "hélio-3=" + FuelCanisterItem.getFuel(he3) + " regolito restante=" + extractor.getItem(0).getCount());
            check("gelo_consumido", electrolyzer.getItem(0).isEmpty() && refinery.getItem(0).isEmpty(),
                    "eletrolisador=" + electrolyzer.getItem(0).getCount() + " refinaria=" + refinery.getItem(0).getCount());
            SpaceHorizons.LOGGER.info(TAG + "bateria da fábrica={} UE; situações: eletrolisador={} refinaria={} extrator={}", battery.energy(),
                    electrolyzer.status(), refinery.status(), extractor.status());
        });
    }

    /** O extrator não funciona fora da Lua (conferido na Terra pela situação). */
    static void checkNight(Minecraft mc) {
        onServer(mc, "noite", (server, level, player) -> {
            MoonBlockEntity panel = be(level, at(factory, 0, 1, 0));
            check("painel_nao_gera_a_noite", panel.generation() == 0, "geração=" + panel.generation());
        });
    }

    // ------------------------------------------------------------------------------------------ gelo polar

    /** Um trecho denso do veio de gelo sob o fundo de uma cratera de latitude alta: {x, y, z}, ou null. */
    static int[] findIce() {
        PlanetTerrain terrain = PlanetTerrains.analytic(Planets.MOON);
        OreVeins veins = new OreVeins(Planets.MOON, Planets.spec(Planets.MOON).ores());
        double radius = Planets.MOON.radius();
        for (int deg = 56; deg <= 72; deg += 2) {
            for (int sign = -1; sign <= 1; sign += 2) {
                int z = (int) Math.round(sign * Math.toRadians(deg) * radius);
                for (int x = 0; x < 100_000; x += 8) {
                    if (terrain.surfaceType(x, z) != SurfaceType.CRATER_FLOOR) {
                        continue;
                    }
                    int surface = terrain.surfaceHeight(x, z);
                    for (int y = surface - 6; y > surface - 40; y -= 2) {
                        if (veins.veinAt(x, y, z, SurfaceType.CRATER_FLOOR) < 0) {
                            continue;
                        }
                        int n = 0;
                        for (int dx = -3; dx <= 3; dx++) {
                            for (int dy = -3; dy <= 3; dy++) {
                                for (int dz = -3; dz <= 3; dz++) {
                                    if (veins.veinAt(x + dx, y + dy, z + dz, SurfaceType.CRATER_FLOOR) >= 0) {
                                        n++;
                                    }
                                }
                            }
                        }
                        if (n >= 12) {
                            ice = new int[] {x, y, z};
                            SpaceHorizons.LOGGER.info(TAG + "gelo: ({}, {}, {}) a {} graus, {} blocos no cubo 7x7x7, chão em y={}", x, y, z, deg * sign, n,
                                    surface);
                            return ice;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Escava o cubo em volta do veio (o minério fica) e confere o mundo gerado contra a função. */
    static void carveIce(Minecraft mc) {
        onServer(mc, "gelo polar", (server, level, player) -> {
            OreVeins veins = new OreVeins(Planets.MOON, Planets.spec(Planets.MOON).ores());
            BlockState ore = ModBlocks.LUNAR_ICE_ORE.get().defaultBlockState();
            int expected = 0;
            int matched = 0;
            int stray = 0;
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        BlockPos pos = new BlockPos(ice[0] + dx, ice[1] + dy, ice[2] + dz);
                        boolean should = veins.veinAt(pos.getX(), pos.getY(), pos.getZ(), SurfaceType.CRATER_FLOOR) >= 0;
                        boolean is = level.getBlockState(pos).is(ore.getBlock());
                        if (should) {
                            expected++;
                            if (is) {
                                matched++;
                            }
                        } else if (is) {
                            stray++;
                        }
                    }
                }
            }
            check("veio_de_gelo_gerado_igual_a_funcao", expected > 0 && matched == expected && stray == 0,
                    "esperados=" + expected + " encontrados=" + matched + " fora da função=" + stray);
            // escava o que não é minério para ver o veio
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        BlockPos pos = new BlockPos(ice[0] + dx, ice[1] + dy, ice[2] + dz);
                        if (!level.getBlockState(pos).is(ore.getBlock())) {
                            put(level, pos, Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
            // o gelo sai como item com uma picareta de ferro
            ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
            BlockPos oreAt = null;
            for (int dx = -4; dx <= 4 && oreAt == null; dx++) {
                for (int dz = -4; dz <= 4 && oreAt == null; dz++) {
                    for (int dy = -3; dy <= 3; dy++) {
                        BlockPos pos = new BlockPos(ice[0] + dx, ice[1] + dy, ice[2] + dz);
                        if (level.getBlockState(pos).is(ore.getBlock())) {
                            oreAt = pos;
                            break;
                        }
                    }
                }
            }
            List<ItemStack> drops = oreAt == null ? List.of() : Block.getDrops(level.getBlockState(oreAt), level, oreAt, null, player, pick);
            boolean gotIce = drops.stream().anyMatch(s -> s.is(ModItems.LUNAR_ICE.get()));
            check("minerio_de_gelo_dropa_gelo_lunar", gotIce, "drops=" + drops);
        });
    }

    static boolean iceFound() {
        return ice != null;
    }

    static String iceViewpoint() {
        return (ice[0] + 3.5) + " " + (ice[1] + 2.0) + " " + (ice[2] + 0.5);
    }

    static String iceSurfaceSpot() {
        PlanetTerrain terrain = PlanetTerrains.analytic(Planets.MOON);
        return (ice[0] + 0.5) + " " + (terrain.surfaceHeight(ice[0], ice[2]) + 6) + " " + (ice[2] + 0.5);
    }
}
