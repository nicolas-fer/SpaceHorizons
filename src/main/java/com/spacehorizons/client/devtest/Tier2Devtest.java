package com.spacehorizons.client.devtest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.rocket.RocketDesignClient;
import com.spacehorizons.client.rocket.RocketDesignModel;
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.content.ModItems;
import com.spacehorizons.content.connected.ConnectedFaces;
import com.spacehorizons.rocket.DesignReport;
import com.spacehorizons.rocket.EngineChargeBlockEntity;
import com.spacehorizons.rocket.EngineForm;
import com.spacehorizons.rocket.EngineType;
import com.spacehorizons.rocket.FlightMode;
import com.spacehorizons.rocket.MissionBudget;
import com.spacehorizons.rocket.RocketAssembler;
import com.spacehorizons.rocket.RocketAssembly;
import com.spacehorizons.rocket.RocketBlockKind;
import com.spacehorizons.rocket.RocketControlPayload;
import com.spacehorizons.rocket.RocketDesign;
import com.spacehorizons.rocket.RocketEngineBlock;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketException;
import com.spacehorizons.rocket.RocketFuelTankBlock;
import com.spacehorizons.rocket.RocketFuelTankBlockEntity;
import com.spacehorizons.rocket.RocketLanding;
import com.spacehorizons.rocket.RocketPortrait;
import com.spacehorizons.rocket.RocketSpecs;
import com.spacehorizons.space.flight.FuelLoad;
import com.spacehorizons.space.flight.Propellant;
import com.spacehorizons.space.flight.RocketStats;
import com.spacehorizons.survival.fuel.FuelCanisterItem;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Cenário {@code tier2} do autoteste (docs/PLANO_TIERS.md, seção 8): o foguete nuclear (motor 2x2x2 + tanque de hidrogênio 2x2x8, barra carregada
 * pelo clique e galão de hidrogênio pelo clique) no painel, montado, subindo um pouco, pousando e com o combustível e o núcleo conferidos na volta aos
 * blocos; o iônico com RTG e xenônio no painel (não decola na Terra, Δv alto) e tentando decolar; o de fusão 3x3x3 no painel; as receitas do Tier 2
 * carregadas no servidor. Log com o prefixo {@code [AUTOTEST] TIER2}; verificações {@code CHECK PASSOU}/{@code CHECK FALHOU} e o resumo no fim.
 */
final class Tier2Devtest {
    private static final String TAG = "[AUTOTEST] TIER2 ";
    static final BlockPos BASE = RocketDevtest.BASE;

    private static int passed;
    private static int failed;

    private Tier2Devtest() {
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

    // ---------------------------------------------------------------- receitas

    /** As receitas do Tier 2 carregaram no servidor e as de bancada dão o item certo (inclusive o galão de hidrogênio cheio). */
    static void recipes(Minecraft mc) {
        onServer(mc, "receitas", (server, level, player) -> {
            List<String> ids = List.of("titanium_ingot_from_smelting_raw_titanium", "titanium_ingot_from_blasting_raw_titanium", "titanium_block",
                    "titanium_ingot_from_block", "titanium_plate", "nuclear_fuel_rod", "superconductor_coil", "hydrogen_canister", "xenon_canister",
                    "helium3_canister", "hydrogen_canister_filling", "xenon_canister_filling", "hydrogen_tank", "xenon_tank", "rtg_generator",
                    "nuclear_engine", "ion_engine", "fusion_engine", "titanium_ingot_from_smelting_mars_titanium_ore",
                    "titanium_ingot_from_blasting_mars_titanium_ore");
            List<String> loaded = new ArrayList<>();
            for (var holder : server.getRecipeManager().getRecipes()) {
                Identifier id = holder.id().identifier();
                if (id.getNamespace().equals(SpaceHorizons.MODID)) {
                    loaded.add(id.getPath());
                }
            }
            List<String> missing = ids.stream().filter(id -> !loaded.contains(id)).toList();
            check("receitas do Tier 2 carregadas no servidor (" + ids.size() + ")", missing.isEmpty(), "faltando " + missing);
            ItemStack water = new ItemStack(Items.WATER_BUCKET);
            ItemStack filled = craft(level, 2, 2, new ItemStack(ModItems.HYDROGEN_CANISTER.get()), water.copy(), water.copy(),
                    new ItemStack(Items.REDSTONE));
            check("galão de hidrogênio enche na bancada (1.000 mB)", filled.is(ModItems.HYDROGEN_CANISTER.get()) && FuelCanisterItem.getFuel(filled) == 1000,
                    filled + " " + FuelCanisterItem.getFuel(filled) + " mB");
            ItemStack p = new ItemStack(ModItems.TITANIUM_PLATE.get());
            ItemStack ti = new ItemStack(ModItems.TITANIUM_INGOT.get());
            ItemStack engine = craft(level, 3, 3, p.copy(), new ItemStack(ModItems.BASIC_CIRCUIT.get()), p.copy(), p.copy(),
                    new ItemStack(ModBlocks.ROCKET_ENGINE.get()), p.copy(), ti.copy(), ItemStack.EMPTY, ti.copy());
            check("motor nuclear sai da bancada com titânio e o motor químico", engine.is(ModBlocks.NUCLEAR_ENGINE.get().asItem()), engine.toString());
        });
    }

    private static ItemStack craft(ServerLevel level, int w, int h, ItemStack... items) {
        CraftingInput input = CraftingInput.of(w, h, new ArrayList<>(Arrays.asList(items)));
        return level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).map(r -> r.value().assemble(input))
                .orElse(ItemStack.EMPTY);
    }

    // ---------------------------------------------------------------- construção

    private static BlockPos lastCockpit = BASE;
    private static RocketAssembly.Report lastReport;

    private static void put(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(BASE.offset(x, y, z), state, 3);
    }

    private static void fillBox(ServerLevel level, int x0, int y0, int z0, int w, int h, int d, BlockState state) {
        for (int y = y0; y < y0 + h; y++) {
            for (int z = z0; z < z0 + d; z++) {
                for (int x = x0; x < x0 + w; x++) {
                    put(level, x, y, z, state);
                }
            }
        }
    }

    private static void clear(ServerLevel level) {
        for (RocketEntity old : level.getEntitiesOfClass(RocketEntity.class, new AABB(BASE).inflate(200.0))) {
            old.discard();
        }
        for (int y = -1; y <= 40; y++) {
            for (int x = -8; x <= 8; x++) {
                for (int z = -8; z <= 8; z++) {
                    level.setBlock(BASE.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        fillBox(level, -3, 0, -3, 7, 1, 7, ModBlocks.LAUNCH_PAD.get().defaultBlockState());
    }

    /** Uma coluna de tanques (conexões calculadas, como faz quem escreve blocos por código), opcionalmente cheia. */
    private static void tankColumn(ServerLevel level, Block tank, int x0, int y0, int z0, int w, int h, int d, int fill) {
        fillBox(level, x0, y0, z0, w, h, d, tank.defaultBlockState());
        for (int y = y0; y < y0 + h; y++) {
            for (int z = z0; z < z0 + d; z++) {
                for (int x = x0; x < x0 + w; x++) {
                    BlockPos pos = BASE.offset(x, y, z);
                    ConnectedFaces.connectAround(level, pos, tank, RocketFuelTankBlock.rule());
                    if (fill >= 0 && level.getBlockEntity(pos) instanceof RocketFuelTankBlockEntity be) {
                        be.setFuel(fill);
                    }
                }
            }
        }
    }

    private static void analyzeAt(ServerLevel level, String key, int cockpitY) {
        lastCockpit = BASE.offset(0, cockpitY, 0);
        lastReport = RocketAssembly.analyze(RocketAssembler.worldGrid(level), List.of(new int[] {lastCockpit.getX(), lastCockpit.getY(), lastCockpit.getZ()}));
        var c = lastReport.result().census();
        SpaceHorizons.LOGGER.info("{}CONSTRUÍDO {}: {} blocos, motores {} (nuclear {}, iônico {}, fusão {}), tanques {} (hidrogênio {}, xenônio {}), RTG {}, "
                + "estruturas {}, montável={}", TAG, key, lastReport.result().cells().size(), c.allEngines(), c.count(RocketBlockKind.NUCLEAR_ENGINE),
                c.count(RocketBlockKind.ION_ENGINE), c.count(RocketBlockKind.FUSION_ENGINE), c.allTanks(), c.count(RocketBlockKind.HYDROGEN_TANK),
                c.count(RocketBlockKind.XENON_TANK), c.rtgs(), c.tier2().structures(), lastReport.assemblable());
        check(key + ": construído montável", lastReport.assemblable(), "recusas " + lastReport.blockers().stream().map(b -> b.reason().name()).toList());
    }

    /**
     * Foguete nuclear: motor 2x2x2 em (0,1,0), hidrogênio 2x2x8 em cima (y 3 a 10), cockpit (0,11,0) e paraquedas. Os tanques ficam vazios e os
     * motores sem núcleo: o roteiro carrega uma barra e um galão pelo clique e {@link #fillNuclear} completa o resto.
     */
    static void buildNuclear(Minecraft mc) {
        com.spacehorizons.client.rocket.RocketPilot.forgetPilotingForTest();
        onServer(mc, "construção nuclear", (server, level, player) -> {
            clear(level);
            fillBox(level, 0, 1, 0, 2, 2, 2, ModBlocks.NUCLEAR_ENGINE.get().defaultBlockState());
            tankColumn(level, ModBlocks.HYDROGEN_TANK.get(), 0, 3, 0, 2, 8, 2, -1);
            put(level, 0, 11, 0, ModBlocks.ROCKET_COCKPIT.get().defaultBlockState());
            put(level, 0, 12, 0, ModBlocks.ROCKET_PARACHUTE.get().defaultBlockState());
            BlockState corner = level.getBlockState(BASE.offset(0, 1, 0));
            int parts = 0;
            for (int i = 1; i < 8; i++) {
                parts += level.getBlockState(BASE.offset(i & 1, 1 + ((i >> 1) & 1), (i >> 2) & 1)).getValue(RocketEngineBlock.FORM) == EngineForm.PART ? 1 : 0;
            }
            check("nuclear 2x2x2 forma no mundo (mestre no canto mínimo, 7 partes)", corner.getValue(RocketEngineBlock.FORM) == EngineForm.MASTER_2 && parts == 7,
                    corner + " partes " + parts);
            FlightDevtest.mark("t2_built_nuclear");
        });
    }

    /** Depois dos cliques: a barra foi para um bloco do motor e o galão para a coluna de hidrogênio. Depois enche tudo (tanques e núcleos). */
    static void fillNuclear(Minecraft mc) {
        onServer(mc, "carga nuclear", (server, level, player) -> {
            int core = 0;
            int loaded = 0;
            for (int i = 0; i < 8; i++) {
                if (level.getBlockEntity(BASE.offset(i & 1, 1 + ((i >> 1) & 1), (i >> 2) & 1)) instanceof EngineChargeBlockEntity be) {
                    core += be.chargeMb();
                    loaded += be.chargeMb() > 0 ? 1 : 0;
                }
            }
            check("a barra clicada carregou um bloco do motor nuclear (32.000 mB)", core == EngineType.ROD_CORE_MB && loaded == 1, "núcleo " + core + " em " + loaded + " bloco(s)");
            int h2 = 0;
            for (int y = 3; y < 11; y++) {
                for (int z = 0; z < 2; z++) {
                    for (int x = 0; x < 2; x++) {
                        if (level.getBlockEntity(BASE.offset(x, y, z)) instanceof RocketFuelTankBlockEntity be) {
                            h2 += be.fuelMb();
                        }
                    }
                }
            }
            check("o galão de hidrogênio clicado foi para a coluna (1.000 mB)", h2 == 1000, "hidrogênio " + h2 + " mB");
            SpaceHorizons.LOGGER.info("{}mensagens: {} | {}", TAG, RocketFuelTankBlock.levelMessage(level, BASE.offset(0, 4, 0)).getString(),
                    com.spacehorizons.rocket.ChargedEngineBlock.chargeMessage(level, BASE.offset(0, 1, 0)).getString());
            for (int i = 0; i < 8; i++) {
                if (level.getBlockEntity(BASE.offset(i & 1, 1 + ((i >> 1) & 1), (i >> 2) & 1)) instanceof EngineChargeBlockEntity be) {
                    be.setCharge(EngineType.ROD_CORE_MB);
                }
            }
            tankColumn(level, ModBlocks.HYDROGEN_TANK.get(), 0, 3, 0, 2, 8, 2, 4000);
            analyzeAt(level, "nuclear", 11);
        });
    }

    /** Íonico: motor 1x1, gerador RTG ao lado, xenônio 1x1x4, cockpit e paraquedas; tudo cheio. */
    static void buildIon(Minecraft mc) {
        com.spacehorizons.client.rocket.RocketPilot.forgetPilotingForTest();
        onServer(mc, "construção iônica", (server, level, player) -> {
            clear(level);
            put(level, 0, 1, 0, ModBlocks.ION_ENGINE.get().defaultBlockState());
            put(level, 1, 2, 0, ModBlocks.RTG_GENERATOR.get().defaultBlockState());
            tankColumn(level, ModBlocks.XENON_TANK.get(), 0, 2, 0, 1, 4, 1, 4000);
            put(level, 0, 6, 0, ModBlocks.ROCKET_COCKPIT.get().defaultBlockState());
            put(level, 0, 7, 0, ModBlocks.ROCKET_PARACHUTE.get().defaultBlockState());
            analyzeAt(level, "ion", 6);
        });
    }

    /** Fusão: motor 3x3x3 (o mestre no centro) com hélio-3 (um galão pelo clique, o resto pelo block entity), cockpit e paraquedas. */
    static void buildFusion(Minecraft mc) {
        com.spacehorizons.client.rocket.RocketPilot.forgetPilotingForTest();
        onServer(mc, "construção fusão", (server, level, player) -> {
            clear(level);
            fillBox(level, -1, 1, -1, 3, 3, 3, ModBlocks.FUSION_ENGINE.get().defaultBlockState());
            put(level, 0, 4, 0, ModBlocks.ROCKET_COCKPIT.get().defaultBlockState());
            put(level, 0, 5, 0, ModBlocks.ROCKET_PARACHUTE.get().defaultBlockState());
            check("fusão 3x3x3 forma no mundo (mestre no centro)", level.getBlockState(BASE.offset(0, 2, 0)).getValue(RocketEngineBlock.FORM) == EngineForm.MASTER_3,
                    level.getBlockState(BASE.offset(0, 2, 0)).toString());
            FlightDevtest.mark("t2_built_fusion");
        });
    }

    static void fillFusion(Minecraft mc) {
        onServer(mc, "carga fusão", (server, level, player) -> {
            int he3 = 0;
            List<EngineChargeBlockEntity> engines = new ArrayList<>();
            for (int y = 1; y <= 3; y++) {
                for (int z = -1; z <= 1; z++) {
                    for (int x = -1; x <= 1; x++) {
                        if (level.getBlockEntity(BASE.offset(x, y, z)) instanceof EngineChargeBlockEntity be) {
                            he3 += be.chargeMb();
                            engines.add(be);
                        }
                    }
                }
            }
            check("o galão de hélio-3 clicado carregou o motor de fusão (1.000 mB)", he3 == 1000, "hélio-3 " + he3 + " mB em " + engines.size() + " blocos");
            for (EngineChargeBlockEntity be : engines) {
                be.setCharge(EngineType.FUSION_CHARGE_PER_BLOCK_MB);
            }
            analyzeAt(level, "fusion", 4);
        });
    }

    // ---------------------------------------------------------------- painel

    static void pinPanel(boolean on) {
        RocketDesignClient.pinForTest(on ? BASE : null);
    }

    /** O painel (o relatório do cliente) = o cálculo do servidor = as estatísticas da montagem, com motores e combustíveis por tipo. */
    static void panel(Minecraft mc, String key) {
        DesignReport client = RocketDesignClient.lastReport();
        onServer(mc, "painel " + key, (server, level, player) -> {
            DesignReport r = RocketDesign.analyze(level, BASE);
            SpaceHorizons.LOGGER.info("{}PAINEL {} (servidor): {}", TAG, key, RocketDesignModel.describe(r));
            SpaceHorizons.LOGGER.info("{}PAINEL {} linhas do Tier 2: {}", TAG, key, r.lines());
            check(key + ": relatório do cliente = cálculo do servidor", client != null && client.equals(r), "cliente " + (client == null ? "nenhum" : "recebeu"));
            RocketStats st = RocketSpecs.statsFor(lastReport.result().census());
            FuelLoad full = st.fullLoad();
            check(key + ": Δv e TWR do painel = estatísticas da montagem (cargas cheias, Δv em fases)",
                    Math.abs(r.deltaV() - st.deltaV(full)) < 1.0e-9 && Math.abs(r.twr() - st.twr(full, DesignReport.EARTH_GRAVITY)) < 1.0e-9,
                    "Δv " + f(r.deltaV()) + " b/t (" + f(r.deltaV() * 20) + " b/s), TWR " + f(r.twr()) + ", massa " + f(r.dryMassT()) + " / " + f(r.wetMassT()) + " t");
            switch (key) {
                case "nuclear" -> {
                    check("nuclear: Δv ~425 b/t e decola (TWR ~3,7)", r.deltaV() > 420 && r.deltaV() < 430 && r.twr() > 3.5 && r.twr() < 3.9,
                            "Δv " + f(r.deltaV()) + " TWR " + f(r.twr()));
                    check("nuclear: linhas do motor 2x2x2 e da coluna de hidrogênio", r.lines().stream().anyMatch(l -> l.kind() == RocketBlockKind.NUCLEAR_ENGINE
                            && l.cubes2() == 1) && r.lines().stream().anyMatch(l -> l.kind() == RocketBlockKind.HYDROGEN_TANK && l.cubes2() == 1), r.lines().toString());
                }
                case "ion" -> {
                    check("iônico: o painel diz que não decola na Terra", r.destinationValue() == MissionBudget.Destination.NO_LIFTOFF && r.twr() < 0.1,
                            r.destinationValue() + " TWR " + f(r.twr()));
                    check("iônico: Δv alto (~1.973 b/t)", r.deltaV() > 1900, "Δv " + f(r.deltaV()) + " b/t");
                    check("iônico: energia cheia com 1 RTG", r.lines().stream().anyMatch(l -> l.kind() == RocketBlockKind.ION_ENGINE && l.power() == 100), r.lines().toString());
                }
                case "fusion" -> {
                    check("fusão: Δv ~708 b/t, TWR ~49, Lua com pouso e volta", r.deltaV() > 700 && r.deltaV() < 716 && r.twr() > 45
                            && r.destinationValue() == MissionBudget.Destination.MOON_LANDING_RETURN, "Δv " + f(r.deltaV()) + " TWR " + f(r.twr()) + " " + r.destinationValue());
                    check("fusão: montável sem tanque", r.assemblable(), "problemas " + r.problems());
                }
                default -> {
                }
            }
        });
    }

    // ---------------------------------------------------------------- montar, voar, pousar

    private static FuelLoad assembled = FuelLoad.EMPTY;

    static void assemble(Minecraft mc, String key) {
        onServer(mc, "montagem " + key, (server, level, player) -> {
            try {
                RocketEntity rocket = RocketAssembler.assemble(level, lastCockpit, null);
                assembled = rocket.fuel();
                RocketStats st = rocket.stats();
                check(key + ": censo do retrato = censo da busca", rocket.portrait().census().sameShape(lastReport.result().census()),
                        "" + rocket.portrait().census().tier2().structures());
                check(key + ": a entidade leva a carga cheia por combustível", assembled.equals(st.capacity()), assembled + " de " + st.capacity());
                SpaceHorizons.LOGGER.info("{}MONTADO {}: carga {}, grupos {}, Δv {} b/t", TAG, key, assembled, st.groups(), f(st.deltaV(assembled)));
                FlightDevtest.mark("t2_assembled_" + key);
            } catch (RocketException e) {
                check(key + ": montagem aceita", false, e.getMessage());
                FlightDevtest.mark("t2_assembled_" + key);
            }
        });
    }

    private static boolean registered;
    private static boolean armed;
    private static String flyKey = "";
    private static int flyTicks;
    private static int burnTicks;
    private static double startY;
    private static double maxAltitude;
    private static boolean everFlew;
    private static boolean chuteSent;

    /** Acelerador cheio por alguns ticks (o iônico tenta 60 ticks e não sai do chão), depois paraquedas e pouso. */
    static void fly(Minecraft mc, String key) {
        onServer(mc, "partida " + key, (server, level, player) -> {
            List<RocketEntity> found = level.getEntitiesOfClass(RocketEntity.class, new AABB(BASE).inflate(60.0));
            if (found.isEmpty()) {
                check(key + ": foguete para voar", false, "nenhum");
                FlightDevtest.mark("t2_landed_" + key);
                return;
            }
            RocketEntity rocket = found.get(0);
            player.teleportTo(rocket.getX(), rocket.getY() + 2.0, rocket.getZ());
            check(key + ": piloto sentado", player.startRiding(rocket, true, true), "veículo=" + player.getVehicle());
            double accel = rocket.stats().acceleration(rocket.fuel(), 1.0) - DesignReport.EARTH_GRAVITY;
            burnTicks = accel <= 0.0 ? 60 : (int) Math.max(3, Math.min(120, Math.round(4.0 / accel)));
            flyKey = key;
            flyTicks = 0;
            startY = rocket.getY();
            maxAltitude = 0.0;
            everFlew = false;
            chuteSent = false;
            armed = true;
            if (!registered) {
                registered = true;
                NeoForge.EVENT_BUS.addListener(ServerTickEvent.Pre.class, e -> flyTick(e.getServer()));
            }
            SpaceHorizons.LOGGER.info("{}VOO {}: aceleração líquida {} b/t², queima de {} ticks, carga {}", TAG, key, f(accel), burnTicks, rocket.fuel());
        });
    }

    private static void flyTick(MinecraftServer server) {
        if (!armed || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        if (!(player.getVehicle() instanceof RocketEntity rocket)) {
            return;
        }
        flyTicks++;
        maxAltitude = Math.max(maxAltitude, rocket.getY() - startY);
        FlightMode mode = rocket.flight().mode();
        everFlew |= mode != FlightMode.LANDED;
        float throttle = flyTicks >= 2 && flyTicks < 2 + burnTicks ? 1.0F : 0.0F;
        int flags = flyTicks == 1 ? RocketControlPayload.FLAG_NEXT_SAS : 0;
        if (flyTicks == 2 + burnTicks + 2 && !chuteSent) {
            flags |= RocketControlPayload.FLAG_PARACHUTE;
            chuteSent = true;
        }
        rocket.applyControl(new RocketControlPayload(0.0F, 0.0F, 0.0F, throttle, (byte) flags));
        if (flyTicks == 2 + burnTicks / 2) {
            FlightDevtest.mark("t2_burning_" + flyKey);
        }
        if (flyTicks % 40 == 0) {
            SpaceHorizons.LOGGER.info("{}VOO {} tick {}: modo {} altitude {} carga {}", TAG, flyKey, flyTicks, mode, f(rocket.getY() - startY), rocket.fuel());
        }
        if ("ion".equals(flyKey) && flyTicks > 2 + burnTicks + 20) {
            armed = false;
            check("iônico: acelerador cheio na plataforma e o foguete não sai do chão", !everFlew && mode == FlightMode.LANDED,
                    "modo " + mode + " altitude máxima " + f(maxAltitude) + " carga " + rocket.fuel());
            FlightDevtest.mark("t2_landed_" + flyKey);
        } else if (!"ion".equals(flyKey) && everFlew && mode == FlightMode.LANDED && flyTicks > burnTicks + 10) {
            armed = false;
            FuelLoad now = rocket.fuel();
            double h2 = assembled.get(Propellant.HYDROGEN) - now.get(Propellant.HYDROGEN);
            double core = assembled.get(Propellant.NUCLEAR_CORE) - now.get(Propellant.NUCLEAR_CORE);
            check(flyKey + ": decolou, subiu e pousou suave", maxAltitude > 3.0, "altitude máxima " + f(maxAltitude) + " em " + flyTicks + " ticks");
            if ("nuclear".equals(flyKey)) {
                double expected = burnTicks * rocket.stats().groups().get(0).fullFlowT() / Propellant.HYDROGEN.tonsPerMb();
                check("nuclear: queimou hidrogênio e o núcleo na mesma conta (mB)", h2 > 0 && Math.abs(h2 - core) < 1.0e-6 && Math.abs(h2 - expected) < 1.0,
                        "hidrogênio " + f(h2) + " mB, núcleo " + f(core) + " mB, esperado " + f(expected) + " (" + burnTicks + " ticks a 100%)");
            }
            FlightDevtest.mark("t2_landed_" + flyKey);
        } else if (flyTicks > 4_000) {
            armed = false;
            check(flyKey + ": voo terminou no tempo", false, "modo " + mode);
            FlightDevtest.mark("t2_landed_" + flyKey);
        }
    }

    /** Desmonta e confere: os blocos voltam, o motor formado, o hidrogênio nos tanques e o núcleo nos motores batem com a entidade. */
    static void afterLand(Minecraft mc, String key) {
        onServer(mc, "pouso " + key, (server, level, player) -> {
            List<RocketEntity> rockets = level.getEntitiesOfClass(RocketEntity.class, new AABB(BASE).inflate(400.0));
            if (rockets.isEmpty()) {
                check(key + ": foguete para desmontar", false, "nenhum");
                FlightDevtest.mark("t2_land_checked_" + key);
                return;
            }
            RocketEntity rocket = rockets.get(0);
            RocketPortrait rotated = rocket.portrait().rotated(com.spacehorizons.rocket.PortraitGrid.quarterTurnsFromYaw(rocket.yawDegrees()));
            BlockPos origin = RocketLanding.originFor(rocket.position(), rotated.sizeX, rotated.sizeY, rotated.sizeZ);
            FuelLoad fuel = rocket.fuel().floor();
            try {
                RocketLanding.land(level, rocket);
            } catch (RocketException e) {
                check(key + ": desmontagem aceita", false, e.getMessage());
                FlightDevtest.mark("t2_land_checked_" + key);
                return;
            }
            int h2 = 0;
            int core = 0;
            int masters = 0;
            int different = 0;
            for (int y = 0; y < rotated.sizeY; y++) {
                for (int z = 0; z < rotated.sizeZ; z++) {
                    for (int x = 0; x < rotated.sizeX; x++) {
                        BlockState expected = rotated.get(x, y, z);
                        if (expected.isAir()) {
                            continue;
                        }
                        BlockPos pos = origin.offset(x, y, z);
                        BlockState actual = level.getBlockState(pos);
                        different += expected.equals(actual) ? 0 : 1;
                        if (level.getBlockEntity(pos) instanceof RocketFuelTankBlockEntity be && actual.is(ModBlocks.HYDROGEN_TANK.get())) {
                            h2 += be.fuelMb();
                        }
                        if (level.getBlockEntity(pos) instanceof EngineChargeBlockEntity be) {
                            core += be.chargeMb();
                        }
                        if (actual.is(ModBlocks.NUCLEAR_ENGINE.get()) && actual.getValue(RocketEngineBlock.FORM) == EngineForm.MASTER_2) {
                            masters++;
                        }
                    }
                }
            }
            check(key + ": pouso devolveu os blocos iguais ao retrato, com o motor 2x2x2 formado", different == 0 && masters == 1,
                    "diferentes " + different + ", mestres " + masters);
            check(key + ": hidrogênio devolvido aos tanques = o da entidade", h2 == (int) fuel.get(Propellant.HYDROGEN), h2 + " contra " + f(fuel.get(Propellant.HYDROGEN)));
            check(key + ": núcleo devolvido aos motores = o da entidade", core == (int) fuel.get(Propellant.NUCLEAR_CORE), core + " contra " + f(fuel.get(Propellant.NUCLEAR_CORE)));
            check(key + ": o voo gastou combustível (menos que o montado)", h2 < assembled.get(Propellant.HYDROGEN), h2 + " < " + f(assembled.get(Propellant.HYDROGEN)));
            FlightDevtest.mark("t2_land_checked_" + key);
        });
    }

    /** Remove o foguete que ficou montado (o iônico, que não decola) para a próxima construção. */
    static void discardRocket(Minecraft mc) {
        onServer(mc, "limpeza", (server, level, player) -> {
            player.stopRiding();
            for (RocketEntity old : level.getEntitiesOfClass(RocketEntity.class, new AABB(BASE).inflate(200.0))) {
                old.discard();
            }
        });
    }
}
