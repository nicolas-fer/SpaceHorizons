package com.spacehorizons.client.devtest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.oxygen.OxygenHud;
import com.spacehorizons.client.space.SpaceSun;
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.content.ModDataComponents;
import com.spacehorizons.content.ModItems;
import com.spacehorizons.survival.compressor.CompressorLogic;
import com.spacehorizons.survival.compressor.CompressorMenu;
import com.spacehorizons.survival.compressor.OxygenCompressorBlock;
import com.spacehorizons.survival.compressor.OxygenCompressorBlockEntity;
import com.spacehorizons.survival.fuel.CanisterFuel;
import com.spacehorizons.survival.fuel.FuelCanisterItem;
import com.spacehorizons.survival.fuel.RefineryBlock;
import com.spacehorizons.survival.fuel.RefineryBlockEntity;
import com.spacehorizons.survival.fuel.RefineryLogic;
import com.spacehorizons.survival.oxygen.OxygenAmounts;
import com.spacehorizons.survival.oxygen.OxygenMath;
import com.spacehorizons.survival.oxygen.OxygenSetup;
import com.spacehorizons.survival.oxygen.OxygenState;
import com.spacehorizons.survival.oxygen.OxygenStorage;
import com.spacehorizons.survival.oxygen.OxygenTankItem;
import com.spacehorizons.planet.PlanetTerrains;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceFrame;
import com.spacehorizons.space.SunModel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Roteiro de teste automático, só em dev (-Dspacehorizons.autotest=true, run "clientTest").
 * Entra no planeta, tira prints em várias altitudes, testa o wrap em X e a barreira polar,
 * registra os resultados no log com o prefixo [AUTOTEST] e fecha o jogo.
 */
public final class AutoTest {
    private static final String TAG = "[AUTOTEST] ";
    // yaw do Minecraft: -90 = leste (X crescente), 0 = sul (Z crescente)
    private static final float EAST = -90f;
    private static final float SOUTH = 0f;

    /** Passo que roda por vários ticks; devolve true quando termina. */
    private interface Ticker {
        boolean tick(Minecraft mc);
    }

    private record Step(int waitTicks, Consumer<Minecraft> action, Ticker ticker) {
    }

    /** Com -Dspacehorizons.autotest.fresh=NOME, cria um mundo novo com o tipo "Space Horizons" a partir do menu. */
    private static final String FRESH_WORLD = System.getProperty("spacehorizons.autotest.fresh");

    private final List<Step> steps = new ArrayList<>();
    /** Com -PshOnly=NOME roda só um trecho do roteiro (hoje: clouds). */
    private static final String ONLY = System.getProperty("spacehorizons.autotest.only");
    private boolean freshRequested;
    private int menuTicks;
    private int index;
    private Ticker active;
    private int countdown = 200;
    private int pushTicks;
    private double pushX;
    private double pushZ;

    private AutoTest() {
        ServerSampler.launch();
        cmd(0, "gamemode creative");
        cmd(20, "gamerule advance_time false");
        cmd(20, "time set 6000");
        step(20, mc -> logPos(mc, "spawn (esperado dim=minecraft:overworld com curvatura)"));
        cmd(20, "spacehorizons goto earth");
        cmd(300, "time set 6000");
        if ("to_d".equals(ONLY)) {
            cmd(5, "weather clear 1000000");
        }
        if ("clouds".equals(ONLY)) {
            cloudsScenario();
            return;
        }
        if ("content".equals(ONLY)) {
            contentScenario();
            return;
        }
        if ("refinery".equals(ONLY)) {
            refineryScenario();
            return;
        }
        if ("compressor".equals(ONLY)) {
            compressorScenario();
            return;
        }
        if ("advancements".equals(ONLY)) {
            advancementsScenario();
            return;
        }
        if ("jei".equals(ONLY)) {
            jeiScenario();
            return;
        }
        if ("moonback".equals(ONLY)) {
            moonBackScenario();
            return;
        }
        if ("vacuum".equals(ONLY)) {
            vacuumScenario();
            return;
        }

        // (a) Terra vanilla: chão em vários pontos perto do spawn (biomas, árvores, relevo)
        int[][] spots = {{0, 0}, {1500, 0}, {0, 1500}, {-2500, 1000}, {4000, -3000}, {-6000, -2000}};
        for (int i = 0; i < spots.length; i++) {
            surface(spots[i][0], spots[i][1]);
            look(10, EAST, 8f);
            shot(60, "a_ground" + i + "_e");
            look(10, 90f, 8f);
            shot(30, "a_ground" + i + "_w");
            if (i == 0) {
                look(10, SOUTH, 8f);
                shot(30, "a_ground0_s");
                look(10, 180f, 8f);
                shot(30, "a_ground0_n");
            }
        }
        // caverna: procura ar sob a superfície nos chunks carregados do spawn
        surface(0, 0);
        step(10, AutoTest::caveTp);
        look(40, EAST, 5f);
        shot(60, "a_cave_e");
        look(10, 90f, 5f);
        shot(30, "a_cave_w");
        look(10, EAST, -60f);
        shot(30, "a_cave_up");
        // abaixo do nível do mar, no meio da pedra
        cmd(20, "tp @s 0 12 0");
        fly(20);
        look(40, EAST, 0f);
        shot(60, "a_deep_y12");

        // (b) COSTURA em X (circunferência 65536 = x de -32768 a 32768); à meia-noite do spawn é meio-dia na costura
        cmd(20, "time set 18000");
        surface(32700, 0);
        cmd(10, "tp @s ~ ~25 ~");
        look(10, EAST, 8f);
        shot(80, "b_seam_32700_east");
        step(0, mc -> logHeights(mc, "costura lado leste", 32690, 32767, 0));
        surface(32760, 0);
        cmd(10, "tp @s ~ ~25 ~");
        look(10, EAST, 12f);
        shot(80, "b_seam_32760_east");
        step(0, mc -> logHeights(mc, "costura, perto", 32740, 32767, 0));
        step(0, mc -> logHeights(mc, "costura, depois", -32768, -32741, 0));
        surface(-32760, 0);
        cmd(10, "tp @s ~ ~25 ~");
        look(10, EAST, 12f);
        shot(80, "b_seam_after_east");
        look(10, 90f, 12f);
        shot(30, "b_seam_after_west");
        step(0, mc -> logHeights(mc, "costura lado oeste", -32768, -32690, 0));
        // atravessando de verdade, voando para leste
        surface(32700, 0);
        cmd(10, "tp @s ~ ~25 ~");
        fly(10);
        look(10, EAST, 12f);
        step(0, mc -> push(1.0, 0.0, 100));
        step(40, mc -> logPos(mc, "no meio da travessia"));
        shot(10, "b_seam_crossing_mid");
        step(60, mc -> logPos(mc, "depois de atravessar (esperado x negativo perto de -32768+)"));
        shot(40, "b_seam_crossing_after");

        cmd(20, "time set 6000");
        // (c) latitude: equador, médias e perto do polo (barreira em +-70 graus = ~12740 blocos)
        int[] lats = {0, 8000, 12000};
        for (int z : lats) {
            surface(0, z);
            look(10, EAST, 8f);
            shot(120, "c_lat" + z + "_e");
            look(10, SOUTH, 8f);
            shot(30, "c_lat" + z + "_s");
        }
        surface(0, -12000);
        look(10, EAST, 8f);
        shot(120, "c_lat_minus12000_e");

        // (e) ÓRBITA: a Lua vista do chão do spawn em vários relógios
        cmd(20, "spacehorizons goto earth");
        cmd(300, "gamerule advance_time false");
        for (int day : new int[] {0, 1, 2, 4, 6}) {
            // dentro de cada dia, o relógio em que a Lua está mais alta no céu do spawn (quando ela nasce, é visível)
            step(20, mc -> {
                long best = moonCulmination(mc, day * 24000L);
                SpaceHorizons.LOGGER.info(TAG + "ORBITA dia {}: Lua mais alta em relogio={}", day, best);
                mc.player.connection.sendCommand("time set " + best);
            });
            lookAtBody(40, SolarSystem.MOON);
            shot(40, "e_moon_day" + day);
        }
        cmd(20, "time set 18000");
        look(30, EAST, -35f);
        shot(30, "e_sky_wide_t18000");

        // (d) malha orbital com as cores do mapa do planeta; espera o mapa ficar pronto
        cmd(20, "time set 6000");
        tickerStep(0, waitForMap(24000));
        cmd(20, "tp @s 0 5000 0");
        fly(20);
        look(20, EAST, 90f);
        shot(500, "d_orbit5000_down");
        look(10, EAST, 40f);
        shot(60, "d_orbit5000_oblique");
        cmd(20, "tp @s 0 15000 0");
        fly(20);
        look(20, EAST, 90f);
        shot(500, "d_orbit15000_down");
        look(10, EAST, 55f);
        shot(60, "d_orbit15000_limb");
        cmd(20, "tp @s 16384 15000 4000");
        fly(20);
        look(20, EAST, 90f);
        shot(500, "d_orbit15000_east90_down");
        cmd(20, "tp @s 0 15000 -9000");
        fly(20);
        look(20, 0f, 75f);
        shot(500, "d_orbit15000_pole");

        if ("to_d".equals(ONLY)) {
            step(40, mc -> {
                SpaceHorizons.LOGGER.info(TAG + "FIM");
                mc.stop();
            });
            return;
        }

        // (f) viagem Terra -> Lua com a Lua em movimento (o tempo corre durante o voo)
        cmd(20, "spacehorizons goto earth");
        cmd(300, "time set 6000");
        cmd(10, "tp @s 0 200 0");
        fly(20);
        look(40, EAST, -90f);
        cmd(5, "gamerule advance_time true");
        cmd(5, "spacehorizons fly moon");
        travel("tr_out", "moon", 36000, 6000);
        cmd(5, "gamerule advance_time false");
        step(300, mc -> logPos(mc, "chegada na Lua"));
        step(0, mc -> logArrival(mc, "Lua"));
        // (g) a Terra no céu da Lua, na direção calculada
        lookAtBody(300, SolarSystem.EARTH);
        shot(60, "g_moon_arrival_earth");
        look(20, EAST, 90f);
        shot(40, "g_moon_arrival_down");
        look(20, EAST, 5f);
        shot(40, "g_moon_arrival_horizon_e");

        cmd(20, "spacehorizons goto moon");
        cmd(300, "time set 6000");
        for (int t : new int[] {6000, 18000, 6000 + 24000 * 2, 6000 + 24000 * 4}) {
            cmd(40, "time set " + t);
            lookAtBody(40, SolarSystem.EARTH);
            shot(60, "g_moon_earth_t" + t);
        }
        cmd(20, "time set 18000");
        look(60, EAST, 15f);
        shot(60, "g_moon_midnight_ground");
        lookAtSun(10);
        shot(30, "g_moon_midnight_sun");
        look(10, EAST, 10f);
        jumpTest();
        shot(10, "g_after_jump");

        // volta para a Terra, também com o tempo correndo
        cmd(20, "tp @s ~ ~300 ~");
        fly(20);
        look(40, EAST, -90f);
        cmd(5, "gamerule advance_time true");
        cmd(5, "spacehorizons fly earth");
        travel("tr_back", "overworld", 8500, 6000);
        cmd(5, "gamerule advance_time false");
        cmd(200, "time set 6000");
        step(100, mc -> logPos(mc, "chegada na Terra"));
        step(0, mc -> logArrival(mc, "Terra"));
        look(300, EAST, 90f);
        shot(60, "h_earth_arrival_down");
        look(20, EAST, 10f);
        shot(40, "h_earth_arrival_horizon");
        look(20, EAST, -90f);
        shot(40, "h_earth_arrival_zenith");

        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /** As nuvens vistas de várias altitudes, olhando para baixo e de lado. */
    /**
     * Procura o que faz as nuvens do vanilla aparecerem gigantes a 5000 de altitude: cada variante faz um trecho do
     * roteiro completo e depois sobe a 5000 olhando para baixo.
     */
    private void cloudsScenario() {
        cmd(20, "gamerule advance_time false");
        cmd(20, "weather clear 1000000");
        cmd(20, "spacehorizons goto earth");
        cmd(300, "time set 6000");
        cloudProbe("base");
        // dentro da pedra, abaixo do nível do mar
        cmd(20, "tp @s 0 12 0");
        fly(20);
        step(80, mc -> {
        });
        cloudProbe("deep");
        // chuva e trovoada
        cmd(20, "weather rain 1000000");
        step(100, mc -> {
        });
        cloudProbe("rain");
        cmd(20, "weather thunder 1000000");
        step(100, mc -> {
        });
        cloudProbe("thunder");
        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /** Itens e blocos do mod no jogo: dá os itens, coloca o bloco à frente e tira prints (modelo, textura, nome). */
    private void contentScenario() {
        cmd(20, "weather clear 1000000");
        cmd(20, "tp @s 0 120 0");
        fly(20);
        cmd(20, "setblock 3 120 0 spacehorizons:steel_block");
        cmd(20, "give @s spacehorizons:steel_block 3");
        cmd(20, "give @s spacehorizons:steel_ingot 5");
        cmd(20, "give @s spacehorizons:steel_nugget 9");
        cmd(20, "give @s spacehorizons:steel_blend 4");
        cmd(20, "give @s spacehorizons:forge_hammer");
        cmd(20, "give @s spacehorizons:steel_plate 2");
        cmd(20, "give @s spacehorizons:basic_circuit 2");
        // simula receitas no servidor (resultado e sobras vão para o log)
        step(20, mc -> ContentCraftCheck.run(mc.getSingleplayerServer()));
        look(20, EAST, 28f);
        shot(60, "ct_block_and_hotbar");
        step(20, mc -> mc.player.getInventory().setSelectedSlot(4));
        shot(40, "ct_item_held");
        step(20, mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
        shot(40, "ct_inventory");
        step(20, mc -> mc.gui.setScreen(null));
        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /** Conquistas do mod, na ordem da árvore (id sem namespace). */
    private static final List<String> MOD_ADVANCEMENTS = List.of("root", "steel_blend", "steel_ingot", "forge_hammer",
            "steel_plate", "basic_circuit", "refinery", "fuel_canister", "fuel_full", "oxygen_compressor", "oxygen_full",
            "steel_block");
    /** Receitas que devem aparecer no livro de receitas, em ordem de obtenção dos itens-chave. */
    private static final List<String> MOD_RECIPES = List.of("steel_blend", "steel_ingot_from_blasting", "steel_nugget",
            "steel_block", "forge_hammer", "steel_plate", "basic_circuit", "fuel_canister", "refinery",
            "oxygen_tank_small", "oxygen_tank_large", "oxygen_compressor", "steel_ingot_from_nuggets", "steel_ingot_from_block");

    /**
     * Conquistas: em sobrevivência, dá os itens em sequência e, a cada passo, loga no servidor integrado quais
     * conquistas do mod estão concluídas e quais receitas estão no livro. No fim abre a tela de conquistas na aba do mod.
     */
    private void advancementsScenario() {
        cmd(20, "weather clear 1000000");
        cmd(20, "tp @s 0 120 0");
        cmd(20, "gamemode survival");
        cmd(20, "clear @s");
        // o mundo do autoteste pode ser reaproveitado: zera o progresso e o livro de receitas (a raiz volta sozinha)
        cmd(10, "advancement revoke @s everything");
        cmd(10, "recipe take @s *");
        step(40, mc -> probeAdvancements(mc, "inicio (so a raiz deve estar feita)"));
        step(10, mc -> openAdvancementsTab(mc));
        shot(40, "adv_screen_start");
        step(10, mc -> mc.gui.setScreen(null));
        String[][] dar = {
            {"minecraft:iron_ingot", "lingote de ferro: deve desbloquear so a receita da mistura de aco"},
            {"spacehorizons:steel_blend", "steel_blend feita; receita do alto-forno"},
            {"spacehorizons:steel_ingot 4", "steel_ingot feita; receitas de martelo, placa, bloco, pepita"},
            {"spacehorizons:forge_hammer", "forge_hammer feita"},
            {"spacehorizons:steel_plate 2", "steel_plate feita; receitas de circuito e galao"},
            {"spacehorizons:basic_circuit", "basic_circuit feita; receita da refinaria"},
            {"spacehorizons:refinery", "refinery feita"},
            {"spacehorizons:fuel_canister", "galao VAZIO: so fuel_canister, nao fuel_full"},
            {"spacehorizons:fuel_canister[spacehorizons:fuel_mb=1000]", "galao cheio: fuel_full"},
            {"spacehorizons:oxygen_compressor", "compressor: oxygen_compressor (filha do circuito); ainda nao oxygen_full"},
            {"spacehorizons:oxygen_tank_small", "tanque pequeno VAZIO: nada novo (oxygen_full ainda nao)"},
            {"spacehorizons:oxygen_tank_small[spacehorizons:oxygen=1200]", "tanque pequeno pela METADE: oxygen_full ainda nao"},
            {"spacehorizons:oxygen_tank_small[spacehorizons:oxygen=2400]", "tanque pequeno CHEIO: oxygen_full"},
            {"spacehorizons:steel_block", "steel_block feita"},
        };
        for (String[] d : dar) {
            cmd(10, "give @s " + d[0]);
            step(30, mc -> probeAdvancements(mc, d[1]));
        }
        // o outro ramo do "ou": revoga a conquista e conclui com o tanque GRANDE cheio
        cmd(10, "advancement revoke @s only spacehorizons:oxygen_full");
        cmd(10, "clear @s spacehorizons:oxygen_tank_small");
        step(30, mc -> probeAdvancements(mc, "oxygen_full revogada, sem tanque cheio no inventario"));
        cmd(10, "give @s spacehorizons:oxygen_tank_large[spacehorizons:oxygen=7200]");
        step(30, mc -> probeAdvancements(mc, "tanque GRANDE cheio: oxygen_full de novo"));
        step(20, mc -> openAdvancementsTab(mc));
        shot(40, "adv_screen_tab");
        step(10, mc -> mc.gui.setScreen(null));
        step(20, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /** Abre a tela de conquistas na aba "Space Horizons" e arrasta o painel para a raiz aparecer à esquerda. */
    private static void openAdvancementsTab(Minecraft mc) {
        net.minecraft.client.gui.screens.advancements.AdvancementsScreen screen =
                new net.minecraft.client.gui.screens.advancements.AdvancementsScreen(mc.player.connection.getAdvancements());
        mc.gui.setScreen(screen);
        net.minecraft.advancements.AdvancementHolder root = mc.player.connection.getAdvancements()
                .get(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "root"));
        SpaceHorizons.LOGGER.info(TAG + "CONQUISTAS holder da raiz no cliente: {}", root == null ? "AUSENTE" : root.id());
        if (root != null) {
            mc.player.connection.getAdvancements().setSelectedTab(root, false);
        }
        // arrasta o painel (mouseDragged sem botão pressionado ainda move o fundo no teste) para ver o começo da árvore
        screen.mouseDragged(new net.minecraft.client.input.MouseButtonEvent(0, 0,
                new net.minecraft.client.input.MouseButtonInfo(0, 0)), 140, 0);
    }

    private static void probeAdvancements(Minecraft mc, String label) {
        net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            SpaceHorizons.LOGGER.warn(TAG + "CONQUISTAS sem servidor integrado");
            return;
        }
        server.execute(() -> {
            net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            List<String> feitas = new ArrayList<>();
            List<String> faltam = new ArrayList<>();
            for (String id : MOD_ADVANCEMENTS) {
                net.minecraft.advancements.AdvancementHolder h = server.getAdvancements()
                        .get(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, id));
                if (h == null) {
                    faltam.add(id + "(AUSENTE NO SERVIDOR)");
                } else if (player.getAdvancements().getOrStartProgress(h).isDone()) {
                    feitas.add(id);
                } else {
                    faltam.add(id);
                }
            }
            List<String> receitas = new ArrayList<>();
            for (String r : MOD_RECIPES) {
                if (player.getRecipeBook().contains(ResourceKey.create(Registries.RECIPE,
                        Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, r)))) {
                    receitas.add(r);
                }
            }
            SpaceHorizons.LOGGER.info(TAG + "CONQUISTAS [{}] feitas={} faltam={} receitas_no_livro={}", label, feitas, faltam,
                    receitas);
        });
    }

    private static final BlockPos REFINERY_POS = new BlockPos(3, 120, 0);

    /**
     * Integração com o JEI: espera o runtime do JEI iniciar, abre pela API a categoria Refinaria (foco na refinaria
     * como estação, no carvão como entrada), as receitas do lingote de aço (alto-forno), a informação do galão e do
     * martelo, e por fim a tela da refinaria com a tela de receitas do JEI aberta por cima (aparece o "+").
     * Só roda com o JEI no classpath (padrão em dev; -PshJei=false o tira).
     */
    private void jeiScenario() {
        cmd(20, "weather clear 1000000");
        fly(10);
        cmd(20, "tp @s 0 120 0");
        cmd(20, "setblock 3 120 0 minecraft:air");
        cmd(5, "setblock 3 120 0 spacehorizons:refinery[facing=west]");
        look(20, EAST, 8f);
        cmd(10, "give @s minecraft:coal 16");
        tickerStep(0, new Ticker() {
            int n;

            @Override
            public boolean tick(Minecraft mc) {
                n++;
                if (com.spacehorizons.compat.jei.JeiDevtest.ready()) {
                    SpaceHorizons.LOGGER.info(TAG + "JEI pronto apos {} ticks", n);
                    return true;
                }
                if (n > 1200) {
                    SpaceHorizons.LOGGER.error(TAG + "JEI NAO INICIOU em 1200 ticks");
                    return true;
                }
                return false;
            }
        });
        step(20, mc -> com.spacehorizons.compat.jei.JeiDevtest.show("station", com.spacehorizons.compat.jei.JeiDevtest.refinery()));
        shot(40, "jei_a_refinery_station");
        step(10, mc -> mc.gui.setScreen(null));
        step(10, mc -> com.spacehorizons.compat.jei.JeiDevtest.show("input", com.spacehorizons.compat.jei.JeiDevtest.coal()));
        shot(40, "jei_b_coal_uses");
        step(10, mc -> mc.gui.setScreen(null));
        step(10, mc -> com.spacehorizons.compat.jei.JeiDevtest.show("output", com.spacehorizons.compat.jei.JeiDevtest.steelIngot()));
        shot(40, "jei_c_steel_ingot");
        step(10, mc -> mc.gui.setScreen(null));
        // mistura de aço como entrada: a receita do alto-forno (a categoria de blasting do JEI)
        step(10, mc -> com.spacehorizons.compat.jei.JeiDevtest.show("input", com.spacehorizons.compat.jei.JeiDevtest.steelBlend()));
        shot(40, "jei_c2_steel_blend_blast_furnace");
        step(10, mc -> mc.gui.setScreen(null));
        step(10, mc -> com.spacehorizons.compat.jei.JeiDevtest.show("input", com.spacehorizons.compat.jei.JeiDevtest.canister()));
        shot(40, "jei_d_canister_info");
        step(10, mc -> mc.gui.setScreen(null));
        step(10, mc -> com.spacehorizons.compat.jei.JeiDevtest.show("input", com.spacehorizons.compat.jei.JeiDevtest.hammer()));
        shot(40, "jei_e_hammer_info");
        step(10, mc -> mc.gui.setScreen(null));
        // tela da refinaria aberta de verdade, com a tela de receitas do JEI por cima ("+" para preencher a entrada)
        step(10, mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(REFINERY_POS), Direction.WEST, REFINERY_POS, false)));
        shot(40, "jei_f_refinery_screen");
        step(10, mc -> com.spacehorizons.compat.jei.JeiDevtest.show("station", com.spacehorizons.compat.jei.JeiDevtest.refinery()));
        shot(40, "jei_g_recipes_over_refinery");
        step(10, mc -> mc.gui.setScreen(null));
        step(10, mc -> mc.player.closeContainer());
        // compressor de oxigênio: categoria (estação e entrada), a receita do tanque, e a tela do compressor com o "+"
        cmd(10, "setblock 3 120 -3 minecraft:air");
        cmd(5, "setblock 3 120 -3 spacehorizons:oxygen_compressor[facing=west]");
        cmd(10, "give @s spacehorizons:oxygen_tank_small");
        step(20, mc -> com.spacehorizons.compat.jei.JeiDevtest.show("station", com.spacehorizons.compat.jei.JeiDevtest.compressor()));
        shot(40, "jei_h_compressor_station");
        step(10, mc -> mc.gui.setScreen(null));
        step(10, mc -> com.spacehorizons.compat.jei.JeiDevtest.show("input", com.spacehorizons.compat.jei.JeiDevtest.smallTank()));
        shot(40, "jei_i_small_tank_uses");
        step(10, mc -> mc.gui.setScreen(null));
        step(10, mc -> com.spacehorizons.compat.jei.JeiDevtest.show("output", com.spacehorizons.compat.jei.JeiDevtest.smallTank()));
        shot(40, "jei_j_small_tank_craft");
        step(10, mc -> mc.gui.setScreen(null));
        step(10, mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(new BlockPos(3, 120, -3)), Direction.WEST, new BlockPos(3, 120, -3), false)));
        shot(40, "jei_k_compressor_screen");
        step(10, mc -> com.spacehorizons.compat.jei.JeiDevtest.show("station", com.spacehorizons.compat.jei.JeiDevtest.compressor()));
        shot(40, "jei_l_recipes_over_compressor");
        step(10, mc -> mc.gui.setScreen(null));
        step(20, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /**
     * Refinaria: coloca o bloco virado para o jogador, enche os slots por comando (6 carvões, tábuas de combustível e
     * um galão vazio) e acompanha no log, a cada 50 ticks, tanque, progresso, chama e o galão. No meio abre a tela
     * (clique direito de verdade) e tira print; no fim testa a capability de item (funil) por cima, pelos lados e por
     * baixo, sem confirmar as transações.
     */
    private void refineryScenario() {
        cmd(20, "weather clear 1000000");
        // voando antes do teleporte: sem isso o jogador cai dezenas de blocos e fica fora do alcance do clique
        fly(10);
        cmd(20, "tp @s 0 120 0");
        cmd(20, "setblock 3 120 0 minecraft:air");
        cmd(5, "setblock 3 120 0 spacehorizons:refinery[facing=west]");
        look(20, EAST, 8f);
        shot(30, "rf_a_unlit_empty");
        cmd(10, "give @s spacehorizons:fuel_canister[spacehorizons:fuel_mb=500]");
        cmd(10, "give @s spacehorizons:refinery");
        cmd(10, "item replace block 3 120 0 container.2 with spacehorizons:fuel_canister");
        cmd(10, "item replace block 3 120 0 container.1 with minecraft:oak_planks 8");
        cmd(10, "item replace block 3 120 0 container.0 with minecraft:coal 6");
        step(5, mc -> probeRefinery(mc, "inicio", true));
        step(0, mc -> {
            // tooltip do galão pela mesma rota do jogo
            ItemStack half = mc.player.getInventory().getItem(0);
            SpaceHorizons.LOGGER.info(TAG + "REFINARIA tooltip do galao na mao: {}", half.getTooltipLines(
                    net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, net.minecraft.world.item.TooltipFlag.NORMAL)
                    .stream().map(Component::getString).toList());
        });
        tickerStep(0, new Ticker() {
            int n;

            @Override
            public boolean tick(Minecraft mc) {
                n++;
                if (n % 50 == 0) {
                    probeRefinery(mc, "t=" + n, false);
                }
                if (n == 40) {
                    grab(mc, "rf_b_lit_front");
                }
                if (n == 120) {
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(REFINERY_POS), Direction.WEST, REFINERY_POS, false));
                }
                if (n == 150) {
                    SpaceHorizons.LOGGER.info(TAG + "REFINARIA tela aberta: {}", mc.gui.screen() == null ? "nenhuma"
                            : mc.gui.screen().getClass().getSimpleName());
                    probeRefinery(mc, "com a tela aberta t=" + n, false);
                    grab(mc, "rf_c_screen_t150");
                }
                if (n == 160) {
                    mc.player.closeContainer();
                }
                if (n == 300) {
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(REFINERY_POS), Direction.WEST, REFINERY_POS, false));
                }
                if (n == 330) {
                    grab(mc, "rf_d_screen_t330");
                }
                if (n == 340) {
                    mc.player.closeContainer();
                }
                if (n == 450) {
                    grab(mc, "rf_e_lit_front_t450");
                }
                if (n == 700) {
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(REFINERY_POS), Direction.WEST, REFINERY_POS, false));
                }
                if (n == 720) {
                    grab(mc, "rf_f_screen_end");
                }
                if (n == 725) {
                    probeRefinery(mc, "fim", true);
                }
                return n >= 730;
            }
        });
        step(10, mc -> mc.player.closeContainer());
        // correções da revisão: o tanque vai para o item ao quebrar e volta ao colocar; o balde vazio da lava sai por baixo
        step(10, AutoTest::refineryBreakAndBucketCheck);
        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /** Quebra a refinaria (com combustível no tanque), recoloca com o item que caiu e testa o balde vazio pela face de baixo. */
    private static void refineryBreakAndBucketCheck(Minecraft mc) {
        net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            net.minecraft.server.level.ServerLevel level = server.getLevel(mc.level.dimension());
            if (level == null || !(level.getBlockEntity(REFINERY_POS) instanceof RefineryBlockEntity before)) {
                SpaceHorizons.LOGGER.warn(TAG + "REFINARIA quebra: block entity ausente");
                return;
            }
            // estado conhecido: sem galão (o tanque não esvazia) e com 1500 mB no tanque
            before.setItem(RefineryBlockEntity.SLOT_CANISTER, ItemStack.EMPTY);
            before.logic().drain(RefineryLogic.TANK_CAPACITY);
            before.logic().fill(1500);
            int tankBefore = before.logic().tank();
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(REFINERY_POS);
            level.destroyBlock(REFINERY_POS, true);
            ItemStack dropped = ItemStack.EMPTY;
            for (net.minecraft.world.entity.item.ItemEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(REFINERY_POS).inflate(2.0))) {
                if (e.getItem().is(com.spacehorizons.content.ModBlocks.REFINERY.get().asItem())) {
                    dropped = e.getItem().copy();
                    e.discard();
                }
            }
            Integer stored = dropped.get(com.spacehorizons.content.ModDataComponents.REFINERY_TANK_MB.get());
            level.setBlock(REFINERY_POS, state.setValue(RefineryBlock.LIT, false), 3);
            int tankAfter = -1;
            if (level.getBlockEntity(REFINERY_POS) instanceof RefineryBlockEntity placed) {
                placed.applyComponentsFromItemStack(dropped);
                tankAfter = placed.logic().tank();
                placed.setItem(RefineryBlockEntity.SLOT_FUEL, new ItemStack(net.minecraft.world.item.Items.BUCKET));
                boolean exposed = java.util.Arrays.stream(placed.getSlotsForFace(Direction.DOWN))
                        .anyMatch(i -> i == RefineryBlockEntity.SLOT_FUEL);
                boolean bucketOut = placed.canTakeItemThroughFace(RefineryBlockEntity.SLOT_FUEL,
                        placed.getItem(RefineryBlockEntity.SLOT_FUEL), Direction.DOWN);
                boolean coalOut = placed.canTakeItemThroughFace(RefineryBlockEntity.SLOT_FUEL,
                        new ItemStack(net.minecraft.world.item.Items.COAL), Direction.DOWN);
                SpaceHorizons.LOGGER.info(TAG + "REFINARIA balde por baixo: slot exposto={} balde sai={} carvao sai={} (esperado true true false)",
                        exposed, bucketOut, coalOut);
                placed.setItem(RefineryBlockEntity.SLOT_FUEL, ItemStack.EMPTY);
            }
            SpaceHorizons.LOGGER.info(TAG + "REFINARIA quebra: tanque antes={} mB, item caiu={} componente={} mB, tanque ao recolocar={} mB",
                    tankBefore, !dropped.isEmpty(), stored, tankAfter);
        });
    }

    private static final BlockPos COMPRESSOR_POS = new BlockPos(3, 120, 0);
    /** Compressor da Lua: escolhido ao chegar (3 blocos a leste do jogador, no chão). */
    private BlockPos moonCompressor;

    /**
     * Compressor de oxigênio. Na Terra (ar respirável): hotbar com tanques cheios e vazios (barras e tooltips), compressor
     * com um tanque pequeno e carvão enchendo (log da carga ao longo do tempo, telas durante e depois), funil por face,
     * ferramenta de quebra e o que cai ao quebrar com tanque dentro. Depois o mesmo compressor na Lua (sem ar): não enche,
     * e a tela avisa.
     */
    private void compressorScenario() {
        cmd(20, "weather clear 1000000");
        // voando antes do teleporte: sem isso o jogador cai dezenas de blocos e fica fora do alcance do clique
        fly(10);
        cmd(20, "tp @s 0 120 0");
        cmd(20, "setblock 3 120 0 minecraft:air");
        cmd(5, "setblock 3 120 0 spacehorizons:oxygen_compressor[facing=west]");
        look(20, EAST, 8f);
        shot(30, "cp_a_unlit_empty");
        // hotbar: pequeno vazio, meio e cheio; grande meio e cheio; e o compressor
        cmd(10, "give @s spacehorizons:oxygen_tank_small");
        cmd(10, "give @s spacehorizons:oxygen_tank_small[spacehorizons:oxygen=1200]");
        cmd(10, "give @s spacehorizons:oxygen_tank_small[spacehorizons:oxygen=2400]");
        cmd(10, "give @s spacehorizons:oxygen_tank_large[spacehorizons:oxygen=3600]");
        cmd(10, "give @s spacehorizons:oxygen_tank_large[spacehorizons:oxygen=7200]");
        cmd(10, "give @s spacehorizons:oxygen_compressor");
        step(20, AutoTest::logTankTooltips);
        shot(20, "cp_b_hotbar");
        step(5, AutoTest::compressorToolCheck);

        // Terra, ar respirável: tanque pequeno vazio e 4 carvões
        cmd(10, "item replace block 3 120 0 container.0 with spacehorizons:oxygen_tank_small");
        cmd(10, "item replace block 3 120 0 container.1 with minecraft:coal 4");
        step(5, mc -> probeCompressor(mc, "inicio", COMPRESSOR_POS));
        tickerStep(0, new Ticker() {
            int n;

            @Override
            public boolean tick(Minecraft mc) {
                n++;
                if (n % 25 == 0) {
                    probeCompressor(mc, "t=" + n, COMPRESSOR_POS);
                }
                if (n == 20) {
                    grab(mc, "cp_c_working_front");
                }
                if (n == 40 || n == 150 || n == 235) {
                    openMachine(mc, COMPRESSOR_POS);
                }
                if (n == 70 || n == 175 || n == 260) {
                    logCompressorScreen(mc, "t=" + n);
                    grab(mc, n == 70 ? "cp_d_screen_working" : n == 175 ? "cp_e_screen_t175" : "cp_f_screen_full");
                }
                if (n == 80 || n == 185 || n == 270) {
                    mc.player.closeContainer();
                }
                if (n == 290) {
                    grab(mc, "cp_g_front_unlit_after");
                    probeCompressor(mc, "fim do enchimento", COMPRESSOR_POS);
                }
                return n >= 295;
            }
        });
        step(10, mc -> compressorCapabilityCheck(mc));
        step(30, mc -> compressorBreakCheck(mc));

        // Lua: vácuo em qualquer altitude. (Na Terra não dá para testar o vácuo com um bloco: o mundo termina em y=319,
        // bem abaixo do topo da atmosfera, 1.863; esse limite é coberto pelo teste unitário.)
        cmd(20, "spacehorizons goto moon");
        cmd(300, "gamemode creative");
        fly(10);
        // o compressor vai no chão, 3 blocos a leste de onde o jogador chegou (a altura vem do mapa de alturas do cliente,
        // porque o jogador ainda está descendo logo depois da chegada); o jogador é posto ao lado, na mesma altura
        step(20, mc -> {
            int x = mc.player.blockPosition().getX() + 3;
            int z = mc.player.blockPosition().getZ();
            int y = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            moonCompressor = new BlockPos(x, y, z);
            mc.player.connection.sendCommand("tp @s " + (x - 2) + ".5 " + y + " " + z + ".5");
        });
        fly(40);
        look(10, EAST, 8f);
        step(10, mc -> mc.player.connection.sendCommand("setblock " + moonCompressor.getX() + " " + moonCompressor.getY() + " "
                + moonCompressor.getZ() + " spacehorizons:oxygen_compressor[facing=west]"));
        step(10, mc -> mc.player.connection.sendCommand("item replace block " + moonCompressor.getX() + " "
                + moonCompressor.getY() + " " + moonCompressor.getZ() + " container.0 with spacehorizons:oxygen_tank_small"));
        step(10, mc -> mc.player.connection.sendCommand("item replace block " + moonCompressor.getX() + " "
                + moonCompressor.getY() + " " + moonCompressor.getZ() + " container.1 with minecraft:coal 4"));
        tickerStep(0, new Ticker() {
            int n;

            @Override
            public boolean tick(Minecraft mc) {
                n++;
                if (n == 1 || n % 25 == 0) {
                    probeCompressor(mc, "Lua t=" + n, moonCompressor);
                }
                if (n == 40) {
                    openMachine(mc, moonCompressor);
                }
                if (n == 70) {
                    logCompressorScreen(mc, "Lua t=" + n);
                    grab(mc, "cp_h_moon_screen");
                }
                if (n == 80) {
                    mc.player.closeContainer();
                }
                if (n == 100) {
                    grab(mc, "cp_i_moon_front");
                }
                return n >= 110;
            }
        });
        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /** Clique direito de verdade no bloco (abre a tela), pela face que olha para o jogador (oeste). */
    private static void openMachine(Minecraft mc, BlockPos pos) {
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.WEST, pos, false));
    }

    /** Loga o que o cliente vê na tela do compressor (a situação que a tela mostra e o quanto o tanque está cheio). */
    private static void logCompressorScreen(Minecraft mc, String label) {
        if (mc.player.containerMenu instanceof CompressorMenu menu) {
            ItemStack tank = menu.getTank();
            SpaceHorizons.LOGGER.info(TAG + "COMPRESSOR tela {}: classe={} situacao={} tanque={} ({} de {}) fracao={} chama={}", label,
                    mc.gui.screen() == null ? "nenhuma" : mc.gui.screen().getClass().getSimpleName(), menu.getStatus(), tank.getItem(),
                    OxygenStorage.get(tank), OxygenStorage.capacity(tank), f(menu.getTankFraction()), menu.hasFlame());
        } else {
            SpaceHorizons.LOGGER.warn(TAG + "COMPRESSOR tela {}: menu aberto NAO e o do compressor ({})", label,
                    mc.player.containerMenu.getClass().getSimpleName());
        }
    }

    /** Nomes e tooltips (pela mesma rota do jogo) dos itens da hotbar: os tanques e o compressor. */
    private static void logTankTooltips(Minecraft mc) {
        for (int slot = 0; slot < 6; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            SpaceHorizons.LOGGER.info(TAG + "TANQUE hotbar {}: {} oxigenio={}/{} barra={} visivel={} tooltip={}", slot, stack.getItem(),
                    OxygenStorage.get(stack), OxygenStorage.capacity(stack), stack.getBarWidth(), stack.isBarVisible(),
                    stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player,
                            net.minecraft.world.item.TooltipFlag.NORMAL).stream().map(Component::getString).toList());
        }
    }

    /** Minerar: precisa de picareta (tag e ferramenta certa). */
    private static void compressorToolCheck(Minecraft mc) {
        net.minecraft.world.level.block.state.BlockState state = ModBlocks.OXYGEN_COMPRESSOR.get().defaultBlockState();
        SpaceHorizons.LOGGER.info(TAG + "COMPRESSOR minerar: tag picareta={} exige ferramenta certa={} madeira={} pedra={} ferro={} mao={} dureza={}",
                state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE), state.requiresCorrectToolForDrops(),
                new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state), new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(state),
                new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(state), ItemStack.EMPTY.isCorrectToolForDrops(state),
                f(state.getDestroySpeed(mc.level, COMPRESSOR_POS)));
    }

    /** Estado do block entity do compressor lido no servidor integrado: tanque, combustível, situação, queima e ar. */
    private static void probeCompressor(Minecraft mc, String label, BlockPos pos) {
        net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            SpaceHorizons.LOGGER.warn(TAG + "COMPRESSOR sem servidor integrado");
            return;
        }
        ResourceKey<net.minecraft.world.level.Level> dimension = mc.level.dimension();
        server.execute(() -> {
            net.minecraft.server.level.ServerLevel level = server.getLevel(dimension);
            if (level == null || !(level.getBlockEntity(pos) instanceof OxygenCompressorBlockEntity be)) {
                SpaceHorizons.LOGGER.warn(TAG + "COMPRESSOR {}: block entity ausente em {}", label, pos);
                return;
            }
            ItemStack tank = be.getItem(OxygenCompressorBlockEntity.SLOT_TANK);
            ItemStack fuel = be.getItem(OxygenCompressorBlockEntity.SLOT_FUEL);
            CompressorLogic l = be.logic();
            SpaceHorizons.LOGGER.info(TAG + "COMPRESSOR {}: situacao={} oxigenio={}/{} ({} de {}) combustivel={}x{} queima={}/{} "
                    + "aceso(bloco)={} ar respiravel={} y={} dim={}", label, l.status(), OxygenStorage.get(tank), OxygenStorage.capacity(tank),
                    OxygenAmounts.formatTime(OxygenStorage.get(tank)), OxygenAmounts.formatTime(OxygenStorage.capacity(tank)),
                    fuel.getItem(), fuel.getCount(), l.litTime(), l.litTotal(),
                    level.getBlockState(pos).getValue(OxygenCompressorBlock.LIT),
                    CompressorLogic.isBreathable(Planets.forLevel(level), pos.getY()), pos.getY(), dimension.identifier().getPath());
        });
    }

    /** Funil por face (capability de item), sem confirmar as transações: o que entra por cima, pelos lados e o que sai por baixo. */
    private static void compressorCapabilityCheck(Minecraft mc) {
        net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        ResourceKey<net.minecraft.world.level.Level> dimension = mc.level.dimension();
        server.execute(() -> {
            net.minecraft.server.level.ServerLevel level = server.getLevel(dimension);
            if (level == null || !(level.getBlockEntity(COMPRESSOR_POS) instanceof OxygenCompressorBlockEntity be)) {
                SpaceHorizons.LOGGER.warn(TAG + "COMPRESSOR funil: block entity ausente");
                return;
            }
            Item small = ModItems.OXYGEN_TANK_SMALL.get();
            ItemResource empty = ItemResource.of(small);
            ItemResource half = ItemResource.of(small, DataComponentPatch.builder().set(ModDataComponents.OXYGEN.get(), 1200).build());
            ItemResource full = ItemResource.of(small, DataComponentPatch.builder()
                    .set(ModDataComponents.OXYGEN.get(), OxygenAmounts.SMALL_TANK_CAPACITY).build());
            ItemResource coal = ItemResource.of(Items.COAL);
            ItemResource bucket = ItemResource.of(Items.BUCKET);

            // A: slot do tanque livre, 3 carvões no combustível: o que entra por cima, lados e baixo
            be.setItem(OxygenCompressorBlockEntity.SLOT_TANK, ItemStack.EMPTY);
            be.setItem(OxygenCompressorBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 3));
            for (Direction side : new Direction[] {Direction.UP, Direction.NORTH, Direction.DOWN}) {
                ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, COMPRESSOR_POS, side);
                if (handler == null) {
                    SpaceHorizons.LOGGER.warn(TAG + "COMPRESSOR capability de item AUSENTE em {}", side);
                    continue;
                }
                try (Transaction tx = Transaction.openRoot()) {
                    SpaceHorizons.LOGGER.info(TAG + "COMPRESSOR funil {} (slot do tanque livre): slots={} insere(tanque vazio)={} "
                            + "insere(tanque meio)={} insere(tanque cheio)={} insere(pedra)={} insere(carvao)={} extrai(carvao)={}",
                            side, handler.size(), handler.insert(empty, 1, tx), handler.insert(half, 1, tx), handler.insert(full, 1, tx),
                            handler.insert(ItemResource.of(Items.STONE), 1, tx), handler.insert(coal, 1, tx), handler.extract(coal, 1, tx));
                }
            }
            // B: tanque cheio no slot: só sai por baixo
            be.setItem(OxygenCompressorBlockEntity.SLOT_TANK, OxygenTankItem.fullStack(small));
            for (Direction side : new Direction[] {Direction.UP, Direction.NORTH, Direction.DOWN}) {
                ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, COMPRESSOR_POS, side);
                if (handler != null) {
                    try (Transaction tx = Transaction.openRoot()) {
                        SpaceHorizons.LOGGER.info(TAG + "COMPRESSOR funil {} (tanque cheio no slot): extrai(tanque cheio)={} (esperado 1 so por baixo)",
                                side, handler.extract(full, 1, tx));
                    }
                }
            }
            // C: tanque pela metade: nao sai nem por baixo; e o resto do combustivel (balde vazio) sai por baixo, o carvao nao
            be.setItem(OxygenCompressorBlockEntity.SLOT_TANK, OxygenTankItem.fullStack(small));
            OxygenStorage.set(be.getItem(OxygenCompressorBlockEntity.SLOT_TANK), 1200);
            be.setItem(OxygenCompressorBlockEntity.SLOT_FUEL, new ItemStack(Items.BUCKET));
            ResourceHandler<ItemResource> down = level.getCapability(Capabilities.Item.BLOCK, COMPRESSOR_POS, Direction.DOWN);
            if (down != null) {
                try (Transaction tx = Transaction.openRoot()) {
                    SpaceHorizons.LOGGER.info(TAG + "COMPRESSOR funil DOWN (tanque meio, balde no combustivel): extrai(tanque meio)={} "
                            + "extrai(balde)={} (esperado 0 e 1)", down.extract(half, 1, tx), down.extract(bucket, 1, tx));
                }
            }
            be.setItem(OxygenCompressorBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 3));
            if (down != null) {
                try (Transaction tx = Transaction.openRoot()) {
                    SpaceHorizons.LOGGER.info(TAG + "COMPRESSOR funil DOWN (carvao no combustivel): extrai(carvao)={} (esperado 0)",
                            down.extract(coal, 1, tx));
                }
            }
        });
    }

    /** Quebra o compressor com um tanque pela metade e 3 carvões dentro e confere o que cai (o bloco, o tanque com a mesma carga, o carvão). */
    private static void compressorBreakCheck(Minecraft mc) {
        net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        ResourceKey<net.minecraft.world.level.Level> dimension = mc.level.dimension();
        server.execute(() -> {
            net.minecraft.server.level.ServerLevel level = server.getLevel(dimension);
            if (level == null || !(level.getBlockEntity(COMPRESSOR_POS) instanceof OxygenCompressorBlockEntity be)) {
                SpaceHorizons.LOGGER.warn(TAG + "COMPRESSOR quebra: block entity ausente");
                return;
            }
            ItemStack tank = OxygenTankItem.fullStack(ModItems.OXYGEN_TANK_LARGE.get());
            OxygenStorage.set(tank, 4321);
            be.setItem(OxygenCompressorBlockEntity.SLOT_TANK, tank);
            be.setItem(OxygenCompressorBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 3));
            level.destroyBlock(COMPRESSOR_POS, true);
            List<String> drops = new ArrayList<>();
            for (net.minecraft.world.entity.item.ItemEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(COMPRESSOR_POS).inflate(2.0))) {
                ItemStack s = e.getItem();
                drops.add(s.getItem() + "x" + s.getCount() + (OxygenStorage.isContainer(s) ? "[oxigenio=" + OxygenStorage.get(s) + "/"
                        + OxygenStorage.capacity(s) + "]" : ""));
                e.discard();
            }
            SpaceHorizons.LOGGER.info(TAG + "COMPRESSOR quebra: bloco agora={} drops={} (esperado: o compressor, o tanque grande com 4321, 3 carvoes)",
                    level.getBlockState(COMPRESSOR_POS).getBlock(), drops);
        });
    }

    /** Loga o estado do block entity da refinaria lido no servidor integrado; com {@code capability} testa também o funil. */
    private static void probeRefinery(Minecraft mc, String label, boolean capability) {
        net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            SpaceHorizons.LOGGER.warn(TAG + "REFINARIA sem servidor integrado");
            return;
        }
        server.execute(() -> {
            net.minecraft.server.level.ServerLevel level = server.getLevel(mc.level.dimension());
            if (level == null || !(level.getBlockEntity(REFINERY_POS) instanceof RefineryBlockEntity be)) {
                SpaceHorizons.LOGGER.warn(TAG + "REFINARIA {}: block entity ausente", label);
                return;
            }
            RefineryLogic l = be.logic();
            ItemStack canister = be.getItem(RefineryBlockEntity.SLOT_CANISTER);
            SpaceHorizons.LOGGER.info(TAG + "REFINARIA {}: tanque={} mB queima={}/{} progresso={}/{} aceso(bloco)={} "
                    + "entrada={}x{} combustivel={}x{} galao={} ({} mB)", label, l.tank(), l.litTime(), l.litTotal(), l.progress(),
                    l.processTotal(), level.getBlockState(REFINERY_POS).getValue(RefineryBlock.LIT),
                    be.getItem(RefineryBlockEntity.SLOT_INPUT).getItem(), be.getItem(RefineryBlockEntity.SLOT_INPUT).getCount(),
                    be.getItem(RefineryBlockEntity.SLOT_FUEL).getItem(), be.getItem(RefineryBlockEntity.SLOT_FUEL).getCount(),
                    canister.getItem(), FuelCanisterItem.getFuel(canister));
            if (!capability) {
                return;
            }
            for (Direction side : new Direction[] {Direction.UP, Direction.NORTH, Direction.DOWN}) {
                ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, REFINERY_POS, side);
                if (handler == null) {
                    SpaceHorizons.LOGGER.warn(TAG + "REFINARIA capability de item AUSENTE em {}", side);
                    continue;
                }
                try (Transaction tx = Transaction.openRoot()) {
                    // nada é confirmado: a transação fecha sem commit e desfaz
                    SpaceHorizons.LOGGER.info(TAG + "REFINARIA funil {}: slots={} insere(pedra)={} insere(carvao,1)={} "
                            + "insere(tabua,1)={} insere(galao,1)={} extrai(galao,1)={}", side, handler.size(),
                            handler.insert(ItemResource.of(Items.STONE), 1, tx), handler.insert(ItemResource.of(Items.COAL), 1, tx),
                            handler.insert(ItemResource.of(Items.OAK_PLANKS), 1, tx),
                            handler.insert(ItemResource.of(ModItems.FUEL_CANISTER.get()), 1, tx),
                            handler.extract(ItemResource.of(ModItems.FUEL_CANISTER.get(), DataComponentPatch.builder()
                                    .set(ModDataComponents.FUEL_MB.get(), CanisterFuel.CAPACITY).build()), 1, tx));
                }
            }
        });
    }

    /** Ida à Lua e volta por teleporte entre dimensões; depois sobe a 5000 para ver se as nuvens planas do DH reaparecem. */
    private void moonBackScenario() {
        cmd(20, "gamerule advance_time false");
        cmd(20, "weather clear 1000000");
        cmd(20, "spacehorizons goto earth");
        cmd(300, "time set 6000");
        cloudProbe("before_moon");
        cmd(20, "spacehorizons goto moon");
        cmd(300, "time set 6000");
        cmd(20, "spacehorizons goto earth");
        cmd(400, "time set 6000");
        cloudProbe("after_moon");
        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /**
     * Vácuo e oxigênio em sobrevivência: Terra no ar (HUD escondido), Terra acima da atmosfera (reserva cai, depois
     * o dano), volta ao ar (recarrega), Lua (cai), fonte de teste (recarrega no vácuo), criativo (isento) e a regra
     * de jogo desligada. O jogador flutua (gravidade 0) para não morrer de queda ao testar a Terra a 2.700.
     */
    private void vacuumScenario() {
        cmd(20, "weather clear 1000000");
        cmd(20, "difficulty normal");
        cmd(20, "gamemode survival");
        cmd(20, "attribute @s minecraft:gravity base set 0");
        step(40, mc -> logOxygen(mc, "A Terra no ar, sobrevivência (esperado: cheia, HUD escondida)"));
        shot(10, "vac_a_earth_air");

        // B: Terra acima do topo da atmosfera (63 + 1.800 = 1.863)
        cmd(20, "tp @s 0 2700 0");
        oxygenTicker("B Terra y=2700", 700, 50, 40, 300, 540, 640);

        // C: de volta ao ar da Terra, a reserva recarrega
        cmd(20, "tp @s 0 200 0");
        oxygenTicker("C Terra no ar (recarga)", 120, 20, 15, 100);

        // D: Lua, vácuo em qualquer altitude
        cmd(20, "spacehorizons goto moon");
        cmd(300, "gamemode survival");
        cmd(20, "attribute @s minecraft:gravity base reset");
        cmd(20, "effect give @s minecraft:instant_health 1 5");
        oxygenTicker("D Lua (cai)", 260, 40, 30, 200);

        // E: fonte de teste abastece no vácuo
        cmd(5, "spacehorizons oxygen supply true");
        oxygenTicker("E Lua com fonte de teste", 80, 20, 60);
        cmd(5, "spacehorizons oxygen supply false");
        oxygenTicker("E2 Lua, fonte desligada", 40, 20);
        cmd(5, "spacehorizons oxygen status");

        // F: criativo no vácuo é isento: reserva cheia e HUD escondida
        cmd(10, "gamemode creative");
        oxygenTicker("F Lua, criativo (isento)", 60, 20, 50);

        // G: regra de jogo desligada: sobrevivência no vácuo sem consumo nem HUD
        cmd(10, "gamemode survival");
        cmd(5, "gamerule spacehorizons:vacuum_damage false");
        oxygenTicker("G Lua, regra desligada", 80, 20, 70);
        cmd(5, "gamerule spacehorizons:vacuum_damage true");
        oxygenTicker("G2 Lua, regra religada", 60, 20);

        cmd(5, "gamemode creative");
        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /** Loga a cada {@code every} ticks o estado de oxigênio do cliente e tira prints nos ticks indicados. */
    private void oxygenTicker(String label, int ticks, int every, int... shotAt) {
        tickerStep(0, new Ticker() {
            int n;

            @Override
            public boolean tick(Minecraft mc) {
                n++;
                if (n == 1 || n % every == 0) {
                    logOxygen(mc, label + " tick=" + n);
                }
                for (int at : shotAt) {
                    if (n == at) {
                        logOxygen(mc, label + " PRINT tick=" + n);
                        grab(mc, "vac_" + label.split(" ")[0].toLowerCase() + "_t" + n);
                    }
                }
                return n >= ticks;
            }
        });
    }

    private static void logOxygen(Minecraft mc, String label) {
        LocalPlayer p = mc.player;
        OxygenState s = p.getData(OxygenSetup.OXYGEN);
        boolean vacuum = Planets.forLevel(mc.level).map(planet -> OxygenMath.isVacuum(planet, p.getEyeY())).orElse(false);
        SpaceHorizons.LOGGER.info(TAG + "OXIGENIO {}: reserva={}/{} ({}s) fonte={} sistema={} vacuo={} hudVisivel={} vida={} "
                + "criativo={} dim={} y={}", label, s.reserve(), OxygenMath.MAX_RESERVE, f(s.secondsLeft()), s.supplied(),
                s.enabled(), vacuum, OxygenHud.isVisible(mc), f(p.getHealth()), p.isCreative(),
                mc.level.dimension().identifier().getPath(), f(p.getY()));
    }

    private void cloudProbe(String name) {
        cmd(20, "tp @s 0 5000 0");
        fly(20);
        look(20, EAST, 90f);
        shot(160, "cl_" + name + "_5000");
        cmd(20, "tp @s 0 200 0");
        fly(20);
        step(60, mc -> {
        });
    }

    /** Teleporta para a superfície de (x, z): sobe a 250, espera os chunks e ajusta pela altura do mapa de alturas do cliente. */
    private void surface(int x, int z) {
        cmd(20, "tp @s " + x + " 250 " + z);
        fly(10);
        step(360, mc -> {
            int h = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            if (h <= mc.level.getMinY() + 1) {
                SpaceHorizons.LOGGER.warn(TAG + "chunk de ({}, {}) sem altura no cliente (h={}), usando y=120", x, z, h);
                h = 118;
            }
            SpaceHorizons.LOGGER.info(TAG + "superfície em ({}, {}): y={}", x, z, h);
            mc.player.connection.sendCommand("tp @s " + x + " " + (h + 2) + " " + z);
        });
        step(60, mc -> {
        });
    }

    /** Procura um espaço de ar coberto, bem abaixo da superfície, nos chunks carregados perto do jogador. */
    private static void caveTp(Minecraft mc) {
        BlockPos origin = mc.player.blockPosition();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int bestY = Integer.MAX_VALUE;
        BlockPos best = null;
        for (int dx = -48; dx <= 48; dx += 2) {
            for (int dz = -48; dz <= 48; dz += 2) {
                int top = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, origin.getX() + dx, origin.getZ() + dz);
                for (int y = Math.min(top - 8, 56); y > mc.level.getMinY() + 8; y--) {
                    pos.set(origin.getX() + dx, y, origin.getZ() + dz);
                    if (mc.level.getBlockState(pos).isAir() && mc.level.getBlockState(pos.above()).isAir()
                            && !mc.level.getBlockState(pos.below()).isAir()
                            && mc.level.getBlockState(pos.below()).getFluidState().isEmpty() && !mc.level.canSeeSky(pos)) {
                        // uma caverna "grande": ar em volta também
                        int air = 0;
                        for (int ax = -2; ax <= 2; ax++) {
                            for (int az = -2; az <= 2; az++) {
                                if (mc.level.getBlockState(pos.offset(ax, 1, az)).isAir()) {
                                    air++;
                                }
                            }
                        }
                        if (air >= 20 && y < bestY) {
                            bestY = y;
                            best = pos.immutable();
                        }
                        break;
                    }
                }
            }
        }
        if (best == null) {
            SpaceHorizons.LOGGER.warn(TAG + "nenhuma caverna encontrada nos chunks carregados perto do spawn");
            mc.player.connection.sendCommand("tp @s " + origin.getX() + " 30 " + origin.getZ());
            return;
        }
        SpaceHorizons.LOGGER.info(TAG + "caverna encontrada em {}", best);
        mc.player.connection.sendCommand("tp @s " + best.getX() + " " + best.getY() + " " + best.getZ());
    }

    /** Loga a altura da superfície (heightmap do cliente) numa faixa de x, para ver se há penhasco na costura. */
    private static void logHeights(Minecraft mc, String label, int x0, int x1, int z) {
        StringBuilder sb = new StringBuilder();
        int prev = Integer.MIN_VALUE;
        int maxJump = 0;
        for (int x = x0; x <= x1; x += 4) {
            int h = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            if (prev != Integer.MIN_VALUE) {
                maxJump = Math.max(maxJump, Math.abs(h - prev));
            }
            prev = h;
            sb.append(x).append(':').append(h).append(' ');
        }
        SpaceHorizons.LOGGER.info(TAG + "ALTURAS {} z={} (salto máximo entre amostras de 4 em 4 blocos={}): {}", label, z, maxJump,
                sb);
    }

    /** Espera o mapa do planeta ficar pronto no cliente, logando o progresso. */
    private Ticker waitForMap(int maxTicks) {
        return new Ticker() {
            int n;

            @Override
            public boolean tick(Minecraft mc) {
                n++;
                if (PlanetTerrains.map(Planets.EARTH) != null) {
                    SpaceHorizons.LOGGER.info(TAG + "MAPA do planeta pronto no cliente após {} ticks de espera", n);
                    return true;
                }
                if (n % 600 == 0) {
                    SpaceHorizons.LOGGER.info(TAG + "MAPA do planeta ainda calculando ({} ticks de espera)", n);
                }
                if (n >= maxTicks) {
                    SpaceHorizons.LOGGER.error(TAG + "MAPA do planeta NÃO ficou pronto em {} ticks", maxTicks);
                    return true;
                }
                return false;
            }
        };
    }

    /** Loga a altitude sobre o chão e a velocidade logo após a chegada. */
    private static void logArrival(Minecraft mc, String where) {
        LocalPlayer p = mc.player;
        int h = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, (int) Math.floor(p.getX()), (int) Math.floor(p.getZ()));
        SpaceHorizons.LOGGER.info(TAG + "CHEGADA {}: y={} chão={} altitude sobre o chão={} velocidade(deltaMovement)={}", where,
                f(p.getY()), h, f(p.getY() - h), f(p.getDeltaMovement().length()));
    }

    /** Relógio dentro de [from, from + 24000) em que a Lua tem a maior elevação vista do jogador. */
    private static long moonCulmination(Minecraft mc, long from) {
        SolarSystem.Body observer = Planets.forLevel(mc.level).flatMap(SolarSystem::bodyOf).orElse(SolarSystem.EARTH);
        double[] cam = new double[3];
        double[] t = new double[3];
        double[] local = new double[3];
        long best = from;
        double bestElevation = -999;
        for (long c = from; c < from + 24000; c += 100) {
            SpaceFrame.toSpace(observer, c, mc.player.getX(), mc.player.getY(), mc.player.getZ(), cam);
            SolarSystem.MOON.positionAt(c, t);
            SpaceFrame.spaceToLocal(observer, c, mc.player.getX(), mc.player.getZ(), t[0] - cam[0], t[1] - cam[1], t[2] - cam[2],
                    local);
            double elevation = local[1] / Math.sqrt(local[0] * local[0] + local[1] * local[1] + local[2] * local[2]);
            if (elevation > bestElevation) {
                bestElevation = elevation;
                best = c;
            }
        }
        return best;
    }

    /** Aponta a câmera para o centro de outro corpo (pelo SolarSystem) e loga azimute/elevação e fase. */
    private void lookAtBody(int wait, SolarSystem.Body target) {
        step(wait, mc -> {
            java.util.Optional<SolarSystem.Body> observer = Planets.forLevel(mc.level).flatMap(SolarSystem::bodyOf);
            if (observer.isEmpty()) {
                return;
            }
            double clock = SpaceSun.clock(mc.level);
            double[] cam = new double[3];
            SpaceFrame.toSpace(observer.get(), clock, mc.player.getX(), mc.player.getY(), mc.player.getZ(), cam);
            double[] t = new double[3];
            target.positionAt(clock, t);
            double[] local = new double[3];
            SpaceFrame.spaceToLocal(observer.get(), clock, mc.player.getX(), mc.player.getZ(), t[0] - cam[0], t[1] - cam[1],
                    t[2] - cam[2], local);
            double len = Math.sqrt(local[0] * local[0] + local[1] * local[1] + local[2] * local[2]);
            double elevation = Math.toDegrees(Math.asin(local[1] / len));
            float yaw = (float) Math.toDegrees(Math.atan2(-local[0], local[2]));
            SpaceHorizons.LOGGER.info(TAG + "ORBITA {} visto de {}: relogio={} posição no espaço=({}, {}, {}) distância={} "
                    + "azimute(yaw)={} elevação={} graus, ângulo orbital da Lua={} graus, iluminada={}",
                    target.planet().id().getPath(), observer.get().planet().id().getPath(), f(clock), f(t[0]), f(t[1]),
                    f(t[2]), f(len), f(yaw), f(elevation), f(Math.toDegrees(SolarSystem.moonAngle(clock)) % 360.0),
                    f(SolarSystem.moonIlluminatedFraction(clock)));
            mc.player.setYRot(yaw);
            mc.player.setXRot((float) Math.max(-90.0, Math.min(90.0, -elevation)));
        });
    }

    /**
     * Acompanha uma viagem do piloto automático: loga troca de dimensão, telas abertas (tela de carregamento) e
     * velocidade; tira prints periódicos e, perto da esfera de influência, prints a cada 3 ticks.
     * Termina quando chega ao destino (dimensão certa e parado) ou estoura o limite de ticks.
     */
    private void travel(String prefix, String targetDim, double fastFromY, int maxTicks) {
        tickerStep(0, new Ticker() {
            int n;
            String lastDim = null;
            int changeTick = -1;
            int shots;
            int fastShots;
            int calm;
            boolean fast;
            double lx, ly, lz;
            boolean hasLast;
            int lastServerTick = -1;
            double lastSpeed;

            @Override
            public boolean tick(Minecraft mc) {
                LocalPlayer p = mc.player;
                n++;
                String dim = mc.level.dimension().identifier().getPath();
                // velocidade em blocos por tick DO SERVIDOR (o cliente pode rodar 0, 1 ou 2 ticks de servidor por tick seu);
                // no tick da troca de dimensão o salto de coordenadas não é movimento e é descartado
                var server = mc.getSingleplayerServer();
                int serverTick = server != null ? server.getTickCount() : n;
                boolean swapped = lastDim != null && !lastDim.equals(dim);
                double speed = lastSpeed;
                if (hasLast && serverTick > lastServerTick && !swapped) {
                    double dist = Math.sqrt((p.getX() - lx) * (p.getX() - lx) + (p.getY() - ly) * (p.getY() - ly)
                            + (p.getZ() - lz) * (p.getZ() - lz));
                    speed = dist / (serverTick - lastServerTick);
                } else if (!hasLast || swapped) {
                    speed = 0.0;
                }
                if (!hasLast || serverTick > lastServerTick || swapped) {
                    lastServerTick = serverTick;
                    lastSpeed = speed;
                    lx = p.getX();
                    ly = p.getY();
                    lz = p.getZ();
                    hasLast = true;
                }
                if (lastDim != null && !lastDim.equals(dim)) {
                    changeTick = n;
                    SpaceHorizons.LOGGER.info(TAG + "{} TROCA DE DIMENSAO tick={} {} -> {} pos=({}, {}, {}) velocidade={} blocos/tick "
                            + "yaw={} pitch={}", prefix, n, lastDim, dim, f(p.getX()), f(p.getY()), f(p.getZ()), f(speed),
                            f(p.getYRot()), f(p.getXRot()));
                }
                lastDim = dim;
                if (mc.gui.screen() != null) {
                    SpaceHorizons.LOGGER.info(TAG + "{} TELA ABERTA tick={} {}", prefix, n, mc.gui.screen().getClass().getName());
                }
                if (n % 100 == 0) {
                    logPos(mc, prefix + " tick=" + n + " velocidade=" + f(speed));
                }
                // modo rápido: perto da transferência (antes: altitude alta; depois: até 60 ticks)
                boolean nearSwap = changeTick < 0 ? p.getY() > fastFromY : n - changeTick < 60;
                if (nearSwap && !fast && changeTick < 0) {
                    fast = true;
                    p.setXRot(-90f);
                    SpaceHorizons.LOGGER.info(TAG + "{} modo rápido ligado tick={} y={}", prefix, n, f(p.getY()));
                }
                if (nearSwap && fastShots < 90 && n % 3 == 0) {
                    fastShots++;
                    grab(mc, String.format("%s_fast_%03d_%s", prefix, fastShots, dim));
                } else if (!nearSwap && n % 240 == 120) {
                    // alterna olhar para cima e para baixo; o print sai 20 ticks depois
                    p.setXRot(shots % 2 == 0 ? -90f : 90f);
                } else if (!nearSwap && n % 240 == 140) {
                    grab(mc, String.format("%s_%02d_%s_%s", prefix, shots++, dim, p.getXRot() < 0 ? "up" : "down"));
                }
                if (changeTick >= 0 && n - changeTick >= 1 && n - changeTick <= 200 && (n - changeTick) % 10 == 1) {
                    int ground = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, (int) Math.floor(p.getX()),
                            (int) Math.floor(p.getZ()));
                    SpaceHorizons.LOGGER.info(TAG + "{} pós-troca +{} ticks: velocidade relativa={} blocos/tick altitude={}",
                            prefix, n - changeTick, f(speed), f(p.getY() - ground));
                }
                if (changeTick >= 0 && dim.equals(targetDim) && n - changeTick > 80) {
                    calm = speed < 0.3 ? calm + 1 : 0;
                    if (calm >= 40) {
                        SpaceHorizons.LOGGER.info(TAG + "{} chegou ao destino tick={}", prefix, n);
                        return true;
                    }
                }
                if (n >= maxTicks) {
                    SpaceHorizons.LOGGER.error(TAG + "{} ESTOUROU o limite de {} ticks (dim={} trocou={})", prefix, maxTicks, dim,
                            changeTick >= 0);
                    return true;
                }
                return false;
            }
        });
    }

    /** Fica no chão, pula uma vez e loga a altura máxima do pulo. */
    private void jumpTest() {
        tickerStep(0, new Ticker() {
            int phase;
            int n;
            double y0;
            double maxY;

            @Override
            public boolean tick(Minecraft mc) {
                LocalPlayer p = mc.player;
                n++;
                if (phase == 0) {
                    if (n == 1) {
                        p.getAbilities().flying = false;
                        p.onUpdateAbilities();
                    }
                    if (p.onGround() && n > 40) {
                        y0 = p.getY();
                        maxY = y0;
                        p.jumpFromGround();
                        SpaceHorizons.LOGGER.info(TAG + "pulo: y0={} gravidade={}", f(y0),
                                p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY));
                        phase = 1;
                        n = 0;
                    } else if (n > 600) {
                        SpaceHorizons.LOGGER.error(TAG + "pulo: nunca ficou no chão");
                        return true;
                    }
                    return false;
                }
                maxY = Math.max(maxY, p.getY());
                if (n > 5 && p.onGround()) {
                    SpaceHorizons.LOGGER.info(TAG + "pulo: altura máxima={} blocos, duração={} ticks (Terra: ~1,25 blocos, ~12 ticks)",
                            f(maxY - y0), n);
                    return true;
                }
                if (n > 600) {
                    SpaceHorizons.LOGGER.error(TAG + "pulo: nunca voltou ao chão");
                    return true;
                }
                return false;
            }
        });
    }

    private static String f(double v) {
        return String.format("%.2f", v);
    }

    public static void register() {
        AutoTest test = new AutoTest();
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> test.tick());
        SpaceHorizons.LOGGER.info(TAG + "roteiro com {} passos registrado", test.steps.size());
    }

    private void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (FRESH_WORLD != null && !freshRequested && mc.level == null && mc.gui.screen() instanceof TitleScreen) {
            freshRequested = true;
            createFreshWorld(mc);
            return;
        }
        if (FRESH_WORLD != null && !freshRequested && mc.level == null && ++menuTicks % 200 == 0) {
            // diagnóstico: o que está na frente do menu inicial (telas de aviso, de integração etc.)
            SpaceHorizons.LOGGER.info(TAG + "aguardando o menu inicial; tela atual: {}",
                    mc.gui.screen() == null ? "nenhuma" : mc.gui.screen().getClass().getName());
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || index >= steps.size()) {
            return;
        }
        if (pushTicks > 0) {
            player.setDeltaMovement(pushX, 0.0, pushZ);
            pushTicks--;
        }
        if (active != null) {
            boolean done;
            try {
                done = active.tick(mc);
            } catch (RuntimeException e) {
                SpaceHorizons.LOGGER.error(TAG + "passo em andamento falhou", e);
                done = true;
            }
            if (!done) {
                return;
            }
            active = null;
            countdown = index < steps.size() ? Math.max(1, steps.get(index).waitTicks()) : 0;
            return;
        }
        if (--countdown > 0) {
            return;
        }
        Step step = steps.get(index++);
        try {
            if (step.ticker() != null) {
                active = step.ticker();
                return;
            }
            step.action().accept(mc);
        } catch (RuntimeException e) {
            SpaceHorizons.LOGGER.error(TAG + "passo {} falhou", index, e);
        }
        countdown = index < steps.size() ? Math.max(1, steps.get(index).waitTicks()) : 0;
    }

    private static void createFreshWorld(Minecraft mc) {
        SpaceHorizons.LOGGER.info(TAG + "criando mundo novo '{}' com o tipo spacehorizons:earth", FRESH_WORLD);
        ResourceKey<net.minecraft.world.level.levelgen.presets.WorldPreset> preset = ResourceKey.create(
                Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "earth"));
        LevelSettings settings = new LevelSettings(FRESH_WORLD, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(FRESH_WORLD, settings, new WorldOptions(0x5EAC0FFEEL, false, false),
                registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(preset).value()
                        .createWorldDimensions(),
                mc.gui.screen());
    }

    private void step(int wait, Consumer<Minecraft> action) {
        steps.add(new Step(wait, action, null));
    }

    private void tickerStep(int wait, Ticker ticker) {
        steps.add(new Step(wait, null, ticker));
    }

    private void cmd(int wait, String command) {
        step(wait, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "/{}", command);
            mc.player.connection.sendCommand(command);
        });
    }

    private void look(int wait, float yaw, float pitch) {
        step(wait, mc -> {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
        });
    }

    private void fly(int wait) {
        step(wait, mc -> {
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        });
    }

    private void push(double x, double z, int ticks) {
        pushX = x;
        pushZ = z;
        pushTicks = ticks;
    }

    /** Olha exatamente para o sol local do SunModel (yaw e pitch a partir da direção nos eixos do Minecraft). */
    private void lookAtSun(int wait) {
        step(wait, mc -> {
            double[] sun = sunHere(mc);
            if (sun == null) {
                return;
            }
            mc.player.setYRot((float) Math.toDegrees(Math.atan2(-sun[0], sun[2])));
            mc.player.setXRot((float) -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, sun[1])))));
        });
    }

    private static double[] sunHere(Minecraft mc) {
        java.util.Optional<SolarSystem.Body> body = Planets.forLevel(mc.level).flatMap(SolarSystem::bodyOf);
        if (body.isEmpty()) {
            return null;
        }
        double[] sun = new double[3];
        SunModel.localDirection(body.get(), mc.player.getX(), mc.player.getZ(), SpaceSun.clock(mc.level), sun);
        return sun;
    }

    /** Loga relógio, sol local (eixos do Minecraft), altura e o relógio efetivo que o céu e a luz do chão usam. */
    private static void logSun(Minecraft mc, String label) {
        java.util.Optional<SolarSystem.Body> body = Planets.forLevel(mc.level).flatMap(SolarSystem::bodyOf);
        if (body.isEmpty()) {
            return;
        }
        double clock = SpaceSun.clock(mc.level);
        double[] sun = sunHere(mc);
        double eff = SunModel.effectiveClock(body.get(), mc.player.getX(), mc.player.getZ(), clock);
        SpaceHorizons.LOGGER.info(TAG + "SOL {}: relogio={} efetivo={} (tick do dia {}) sol=({}, {}, {}) altura={} graus", label,
                f(clock), f(eff), f(Math.floorMod(Math.round(eff), 24000L)), f(sun[0]), f(sun[1]), f(sun[2]),
                f(Math.toDegrees(Math.asin(sun[1]))));
    }

    private void shot(int wait, String name) {
        step(wait, mc -> {
            logPos(mc, "print " + name);
            logSun(mc, name);
            grab(mc, name);
        });
    }

    private static void grab(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "autotest_" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                msg -> SpaceHorizons.LOGGER.info(TAG + "print salvo: {}", msg.getString()));
    }

    private static void logPos(Minecraft mc, String label) {
        LocalPlayer p = mc.player;
        SpaceHorizons.LOGGER.info(TAG + "{}: dim={} x={} y={} z={}", label,
                mc.level.dimension().identifier(), String.format("%.2f", p.getX()), String.format("%.2f", p.getY()),
                String.format("%.2f", p.getZ()));
    }
}
