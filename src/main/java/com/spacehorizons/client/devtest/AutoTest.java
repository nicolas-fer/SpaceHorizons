package com.spacehorizons.client.devtest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.oxygen.OxygenHud;
import com.spacehorizons.client.rocket.RocketKeys;
import com.spacehorizons.client.space.BodySky;
import com.spacehorizons.client.space.SkyAttitude;
import com.spacehorizons.client.space.SkyObserver;
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
import com.spacehorizons.survival.suit.SuitGear;
import com.spacehorizons.survival.suit.SuitPieces;
import com.spacehorizons.planet.PlanetTerrains;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceDimension;
import com.spacehorizons.space.SpaceFrame;
import com.spacehorizons.space.SunModel;
import com.spacehorizons.space.travel.TravelMath;

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
        if ("showcase".equals(ONLY)) {
            showcaseScenario();
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
        if ("space".equals(ONLY)) {
            spaceScenario();
            return;
        }
        if ("vacuum".equals(ONLY)) {
            vacuumScenario();
            return;
        }
        if ("suit".equals(ONLY)) {
            suitScenario();
            return;
        }
        if ("rocket".equals(ONLY)) {
            rocketScenario();
            return;
        }
        if ("cockpit".equals(ONLY)) {
            cockpitScenario();
            return;
        }
        if ("skytest".equals(ONLY)) {
            skyTestScenario();
            return;
        }
        if ("moontrip".equals(ONLY)) {
            moonTripScenario();
            return;
        }
        if (ONLY != null && ONLY.startsWith("flight")) {
            // flight = voos A, B, C e D; flightb, flightc, flightbc... = só os voos citados (depuração)
            flightScenario(ONLY.length() == 6 ? "abcd" : ONLY.substring(6));
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

        // (b) COSTURA em X (circunferência 655360 = x de -327680 a 327680); à meia-noite do spawn é meio-dia na costura
        cmd(20, "time set 18000");
        surface(327600, 0);
        cmd(10, "tp @s ~ ~25 ~");
        look(10, EAST, 8f);
        shot(80, "b_seam_327600_east");
        step(0, mc -> logHeights(mc, "costura lado leste", 327590, 327679, 0));
        surface(327670, 0);
        cmd(10, "tp @s ~ ~25 ~");
        look(10, EAST, 12f);
        shot(80, "b_seam_327670_east");
        step(0, mc -> logHeights(mc, "costura, perto", 327650, 327679, 0));
        step(0, mc -> logHeights(mc, "costura, depois", -327680, -327651, 0));
        surface(-327670, 0);
        cmd(10, "tp @s ~ ~25 ~");
        look(10, EAST, 12f);
        shot(80, "b_seam_after_east");
        look(10, 90f, 12f);
        shot(30, "b_seam_after_west");
        step(0, mc -> logHeights(mc, "costura lado oeste", -327680, -327600, 0));
        // atravessando de verdade, voando para leste
        surface(327600, 0);
        cmd(10, "tp @s ~ ~25 ~");
        fly(10);
        look(10, EAST, 12f);
        step(0, mc -> push(1.0, 0.0, 100));
        step(40, mc -> logPos(mc, "no meio da travessia"));
        shot(10, "b_seam_crossing_mid");
        step(60, mc -> logPos(mc, "depois de atravessar (esperado x negativo perto de -327680+)"));
        shot(40, "b_seam_crossing_after");

        cmd(20, "time set 6000");
        // (c) latitude: equador, médias e perto do polo (barreira em +-70 graus = ~127400 blocos)
        int[] lats = {0, 80000, 120000};
        for (int z : lats) {
            surface(0, z);
            look(10, EAST, 8f);
            shot(120, "c_lat" + z + "_e");
            look(10, SOUTH, 8f);
            shot(30, "c_lat" + z + "_s");
        }
        surface(0, -120000);
        look(10, EAST, 8f);
        shot(120, "c_lat_minus120000_e");

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
        cmd(20, "tp @s 163840 15000 40000");
        fly(20);
        look(20, EAST, 90f);
        shot(500, "d_orbit15000_east90_down");
        cmd(20, "tp @s 0 15000 -90000");
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
        travel("tr_out", "moon", 360_000, 12_000);
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
        travel("tr_back", "overworld", 85_000, 12_000);
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

    /** Blocos da vitrine (setblock), lado a lado no eixo X, com a frente virada para o sul (para a câmera). */
    private static final String[] SHOWCASE_BLOCKS = {"spacehorizons:steel_block", "spacehorizons:rocket_hull",
        "spacehorizons:launch_pad", "spacehorizons:refinery[facing=south,lit=false]", "spacehorizons:refinery[facing=south,lit=true]",
        "spacehorizons:oxygen_compressor[facing=south,lit=false]", "spacehorizons:oxygen_compressor[facing=south,lit=true]",
        "spacehorizons:rocket_cockpit[facing=south]", "spacehorizons:rocket_engine", "spacehorizons:rocket_fuel_tank",
        "spacehorizons:rocket_parachute", "spacehorizons:rocket_landing_leg"};
    /** Itens da vitrine (molduras e inventário). */
    private static final String[] SHOWCASE_ITEMS = {"steel_ingot", "steel_nugget", "steel_blend", "steel_plate", "basic_circuit",
        "forge_hammer", "fuel_canister", "oxygen_tank_small", "oxygen_tank_large", "space_helmet", "space_chestplate",
        "space_leggings", "space_boots"};

    /**
     * Vitrine de texturas e modelos: todos os blocos lado a lado (de longe, de perto e de vários ângulos), as máquinas
     * empilhadas como num foguete, os itens em molduras e no inventário, e o foguete de teste montado (retrato do
     * RocketRenderer) parado e deslocado para o alto, para conferir que os modelos com elements renderizam no retrato.
     */
    private void showcaseScenario() {
        int y = 150;
        int x0 = 20;
        cmd(20, "weather clear 1000000");
        cmd(10, "gamemode creative");
        cmd(10, "gamerule send_command_feedback false");
        cmd(10, "tp @s " + (x0 + 11) + " " + (y + 2) + " 8");
        fly(20);
        cmd(10, "fill " + (x0 - 3) + " " + (y - 1) + " -8 " + (x0 + 27) + " " + (y + 8) + " 8 minecraft:air");
        cmd(10, "fill " + (x0 - 3) + " " + (y - 1) + " -8 " + (x0 + 27) + " " + (y - 1) + " 8 minecraft:smooth_stone");
        for (int i = 0; i < SHOWCASE_BLOCKS.length; i++) {
            cmd(3, "setblock " + (x0 + 2 * i) + " " + y + " 0 " + SHOWCASE_BLOCKS[i]);
        }
        // pilha de peças como num foguete (motor, tanques, casco, cockpit, paraquedas) e pernas ao lado
        int sx = x0 + 2;
        String[] stack = {"rocket_engine", "rocket_fuel_tank", "rocket_fuel_tank", "rocket_hull", "rocket_cockpit[facing=south]",
            "rocket_parachute"};
        for (int i = 0; i < stack.length; i++) {
            cmd(3, "setblock " + sx + " " + (y + i) + " -5 spacehorizons:" + stack[i]);
        }
        cmd(3, "setblock " + (sx - 1) + " " + y + " -5 spacehorizons:rocket_landing_leg");
        cmd(3, "setblock " + (sx + 1) + " " + y + " -5 spacehorizons:rocket_landing_leg");
        cmd(3, "fill " + (sx + 4) + " " + y + " -6 " + (sx + 6) + " " + y + " -4 spacehorizons:launch_pad");
        cmd(3, "fill " + (sx + 8) + " " + y + " -6 " + (sx + 9) + " " + (y + 1) + " -5 spacehorizons:rocket_fuel_tank");
        // parede de molduras com os itens
        int wx = x0 + 12;
        cmd(3, "fill " + wx + " " + y + " -6 " + (wx + 12) + " " + (y + 1) + " -6 spacehorizons:steel_block");
        for (int i = 0; i < SHOWCASE_ITEMS.length; i++) {
            int fx = wx + (i % 7) * 2;
            int fy = y + i / 7;
            cmd(3, "summon item_frame " + fx + " " + fy + " -5 {Facing:3b,Fixed:1b,Item:{id:\"spacehorizons:" + SHOWCASE_ITEMS[i]
                    + "\",count:1}}");
        }
        cmd(10, "clear @s");
        for (String id : SHOWCASE_ITEMS) {
            cmd(2, "give @s spacehorizons:" + id);
        }
        for (String b : SHOWCASE_BLOCKS) {
            String id = b.contains("[") ? b.substring(0, b.indexOf('[')) : b;
            cmd(2, "give @s " + id);
        }

        // prints do mundo sem HUD (chat, avisos de conquista e hotbar escondidos)
        step(20, mc -> {
            mc.gui.hud.getChat().clearMessages(false);
            hud(mc, false);
        });
        // fileira inteira, de longe, e de perto em três trechos (frente para a câmera, olhando ao norte)
        rocketView("sc_row_wide", x0 + 11, y + 3.5, 9.5, 180f, 14f);
        for (int part = 0; part < 4; part++) {
            rocketView("sc_row_close" + part, x0 + 2.5 + part * 6, y + 1.3, 3.2, 180f, 20f);
        }
        rocketView("sc_row_oblique_left", x0 - 2.5, y + 2.6, 3.5, 225f, 22f);
        rocketView("sc_row_oblique_right", x0 + 25.5, y + 2.6, 3.5, 135f, 22f);
        rocketView("sc_row_back", x0 + 11, y + 2.5, -3.5, 0f, 18f);
        rocketView("sc_row_top", x0 + 11, y + 7.5, 2.5, 180f, 65f);
        // pilha e molduras (atrás da fileira)
        rocketView("sc_stack", sx + 3.5, y + 3.0, 1.5, 200f, 8f);
        rocketView("sc_stack_low", sx + 2.5, y + 0.3, -1.5, 150f, -10f);
        rocketView("sc_frames", wx + 6.5, y + 1.0, -2.0, 180f, 4f);
        // itens no inventário (sobrevivência, para ver todos os itens e blocos dados) e na mão
        step(10, mc -> {
            hud(mc, true);
            mc.gui.hud.getChat().clearMessages(false);
        });
        look(10, 180f, 10f);
        step(10, mc -> mc.player.getInventory().setSelectedSlot(5));
        shot(30, "sc_item_held");
        cmd(10, "gamemode survival");
        step(30, mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
        shot(40, "sc_inventory");
        step(10, mc -> mc.gui.setScreen(null));
        cmd(10, "gamemode creative");
        fly(10);
        step(10, mc -> hud(mc, false));

        // foguete de teste montado: retrato assado dos BlockStates (RocketRenderer)
        double bx = RocketDevtest.BASE.getX() + 0.5;
        double by = RocketDevtest.BASE.getY();
        double bz = RocketDevtest.BASE.getZ() + 0.5;
        cmd(10, RocketDevtest.clearCommand());
        for (String command : RocketDevtest.buildCommands()) {
            cmd(4, command);
        }
        rocketView("sc_rocket_blocks", bx + 6.0, by + 3.5, bz + 6.0, 135f, 14f);
        BlockPos cockpit = RocketDevtest.at(new BlockPos(0, 4, 0));
        cmd(20, "spacehorizons rocket assemble " + cockpit.getX() + " " + cockpit.getY() + " " + cockpit.getZ());
        rocketView("sc_rocket_portrait", bx + 6.0, by + 3.5, bz + 6.0, 135f, 14f);
        rocketView("sc_rocket_portrait_close", bx + 3.0, by + 2.5, bz + 3.0, 135f, 10f);
        cmd(20, "spacehorizons rocket nudge 0 12 0");
        rocketView("sc_rocket_portrait_air", bx + 7.0, by + 14.0, bz + 7.0, 135f, 8f);
        rocketView("sc_rocket_portrait_air_below", bx + 4.0, by + 8.0, bz + 4.0, 135f, -35f);
        step(40, mc -> {
            hud(mc, true);
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /** Mostra ou esconde o HUD (como o F1). */
    private static void hud(Minecraft mc, boolean visible) {
        if (mc.gui.hud.isHidden() == visible) {
            mc.gui.hud.toggle();
        }
    }

    /** Conquistas do mod, na ordem da árvore (id sem namespace). */
    private static final List<String> MOD_ADVANCEMENTS = List.of("root", "steel_blend", "steel_ingot", "forge_hammer",
            "steel_plate", "basic_circuit", "refinery", "fuel_canister", "fuel_full", "oxygen_compressor", "oxygen_full",
            "steel_block", "suit_up", "first_breath");
    /** Receitas que devem aparecer no livro de receitas, em ordem de obtenção dos itens-chave. */
    private static final List<String> MOD_RECIPES = List.of("steel_blend", "steel_ingot_from_blasting", "steel_nugget",
            "steel_block", "forge_hammer", "steel_plate", "basic_circuit", "fuel_canister", "refinery",
            "oxygen_tank_small", "oxygen_tank_large", "oxygen_compressor", "steel_ingot_from_nuggets", "steel_ingot_from_block",
            "space_helmet", "space_chestplate", "space_leggings", "space_boots");

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
            {"spacehorizons:steel_plate 2", "steel_plate feita; receitas de circuito, galao, calcas e botas do traje"},
            {"spacehorizons:basic_circuit", "basic_circuit feita; receitas da refinaria, do capacete e do peitoral do traje"},
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
        // vestindo as quatro peças do traje (a conquista suit_up olha o equipamento; first_breath é do cenário suit)
        cmd(10, "item replace entity @s armor.head with spacehorizons:space_helmet");
        cmd(10, "item replace entity @s armor.chest with spacehorizons:space_chestplate");
        cmd(10, "item replace entity @s armor.legs with spacehorizons:space_leggings");
        step(30, mc -> probeAdvancements(mc, "3 pecas do traje vestidas: suit_up ainda nao"));
        cmd(10, "item replace entity @s armor.feet with spacehorizons:space_boots");
        step(30, mc -> probeAdvancements(mc, "4 pecas do traje vestidas: suit_up feita"));
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
                    !SpaceDimension.is(level) && CompressorLogic.isBreathable(Planets.forLevel(level), pos.getY()), pos.getY(),
                    dimension.identifier().getPath());
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
        boolean vacuum = OxygenSetup.isVacuumAt(mc.level, p.getEyeY());
        SpaceHorizons.LOGGER.info(TAG + "OXIGENIO {}: reserva={}/{} ({}s) fonte={} sistema={} vacuo={} hudVisivel={} vida={} "
                + "criativo={} dim={} y={}", label, s.reserve(), OxygenMath.MAX_RESERVE, f(s.secondsLeft()), s.supplied(),
                s.enabled(), vacuum, OxygenHud.isVisible(mc), f(p.getHealth()), p.isCreative(),
                mc.level.dimension().identifier().getPath(), f(p.getY()));
    }

    /**
     * Recipiente de oxigênio para o cenário: o tanque de verdade quando a capacidade é a de um tanque do mod (pequeno ou
     * grande), senão um item qualquer com os componentes do contrato aplicados por comando.
     */
    private static String tank(int capacity, int oxygen) {
        if (capacity == com.spacehorizons.survival.oxygen.OxygenAmounts.SMALL_TANK_CAPACITY) {
            return "spacehorizons:oxygen_tank_small[spacehorizons:oxygen=" + oxygen + "]";
        }
        if (capacity == com.spacehorizons.survival.oxygen.OxygenAmounts.LARGE_TANK_CAPACITY) {
            return "spacehorizons:oxygen_tank_large[spacehorizons:oxygen=" + oxygen + "]";
        }
        return "minecraft:iron_ingot[spacehorizons:oxygen_capacity=" + capacity + ",spacehorizons:oxygen=" + oxygen + "]";
    }

    /** Foto do estado do traje no cliente (o que o jogador vê), para comparar o começo e o fim de um trecho. */
    private record SuitProbe(int reserve, int frozen, float health, int tank, boolean supplied, int pieces, List<Integer> tanks) {
    }

    /** Um trecho medido: fotos a cada {@code every} ticks (a primeira no tick 1), a menor vida vista e o intervalo. */
    private record SuitRun(List<SuitProbe> samples, int every, float minHealth) {
        SuitProbe start() {
            return samples.get(0);
        }

        SuitProbe end() {
            return samples.get(samples.size() - 1);
        }

        /** Foto do tick {@code tick} (múltiplo do intervalo). */
        SuitProbe at(int tick) {
            return samples.get(tick / every);
        }
    }

    private static SuitProbe suitProbe(Minecraft mc) {
        LocalPlayer p = mc.player;
        OxygenState s = p.getData(OxygenSetup.OXYGEN);
        List<Integer> tanks = new ArrayList<>();
        for (ItemStack stack : SuitGear.containers(p)) {
            tanks.add(OxygenStorage.get(stack));
        }
        return new SuitProbe(s.reserve(), p.getTicksFrozen(), p.getHealth(), SuitGear.totalOxygen(p), s.supplied(),
                SuitGear.piecesOf(p).count(), tanks);
    }

    /**
     * Traje espacial e frio do vácuo, em sobrevivência na Lua: (A) sem traje congela em ~7 s, a reserva cai e o frio
     * machuca; (A2) sem a regeneração natural, o dano de frio é 1 a cada 2 s; (B) capacete e peitoral com um recipiente
     * cheio: reserva cheia, recipiente gasta 1 por tick e ainda congela (só 2 peças); (D) 4 peças: continua respirando
     * e descongela; conquistas do traje; (E) recipientes de cargas diferentes acabam (o mais vazio primeiro) e a
     * reserva volta a cair; (H) só o capacete não respira; (F) regra desligada e (G) criativo não congelam nem
     * gastam o recipiente. Cada trecho loga "SUIT RESULTADO ... OK/FALHA" e tira prints.
     */
    private void suitScenario() {
        cmd(20, "weather clear 1000000");
        cmd(20, "difficulty normal");
        cmd(5, "gamerule spacehorizons:vacuum_damage true");
        cmd(5, "gamerule natural_health_regeneration true");
        // sem as mensagens de comando no chat, os prints mostram o HUD limpo
        cmd(5, "gamerule send_command_feedback false");
        cmd(5, "gamerule show_advancement_messages false");
        cmd(20, "spacehorizons goto moon");
        cmd(300, "time set 6000");
        // ainda em criativo: zera conquistas e livro de receitas, simula as receitas e confere o ponto de partida
        cmd(10, "clear @s");
        cmd(10, "advancement revoke @s everything");
        cmd(10, "recipe take @s *");
        step(10, mc -> ContentCraftCheck.run(mc.getSingleplayerServer()));
        look(20, EAST, 10f);
        step(60, mc -> probeAdvancements(mc, "inicio do cenario suit"));
        cmd(5, "gamemode survival");
        cmd(5, "effect give @s minecraft:instant_health 1 5");

        // A: sem traje, a partir do zero: a reserva cai 1 por tick e o congelamento sobe ~1 por tick (7 s para 140)
        suitTicker("A sem traje", 260, 20, new int[] {100, 230}, run -> {
            if (run.start().frozen() > 30) {
                return "devia comecar quase descongelado, frozen=" + run.start().frozen();
            }
            // taxa: o saldo é de 1 por tick, então do tick 20 ao 60 o congelamento sobe ~40
            int rose = run.at(60).frozen() - run.at(20).frozen();
            if (rose < 36 || rose > 44) {
                return "do tick 20 ao 60 o congelamento devia subir ~40 (1 por tick), subiu " + rose;
            }
            if (run.at(140).frozen() < 138) {
                return "no tick 140 devia estar totalmente congelado, frozen=" + run.at(140).frozen();
            }
            if (run.end().reserve() >= run.start().reserve() - 200) {
                return "a reserva devia cair ~1 por tick, de " + run.start().reserve() + " para " + run.end().reserve();
            }
            return run.minHealth() < 20.0F ? null : "devia ter levado dano de frio (a vida nunca saiu de 20)";
        });

        // A2: o mesmo, sem a regeneração natural: o dano de frio fica visível (1 a cada 40 ticks = 5 em 200 ticks)
        cmd(5, "gamerule natural_health_regeneration false");
        cmd(5, "effect give @s minecraft:instant_health 1 5");
        cmd(5, "spacehorizons oxygen set 600");
        suitTicker("A2 sem traje e sem regeneracao", 200, 40, new int[] {190}, run -> {
            float lost = run.start().health() - run.end().health();
            return lost >= 4.0F && lost <= 6.0F ? null : "devia perder ~5 de vida em 200 ticks (1 a cada 40), perdeu " + lost;
        });
        cmd(5, "gamerule natural_health_regeneration true");

        // B: capacete e peitoral com um recipiente de 2400 (ainda sem calças e botas)
        cmd(5, "effect give @s minecraft:instant_health 1 5");
        cmd(5, "item replace entity @s armor.head with spacehorizons:space_helmet");
        cmd(5, "item replace entity @s armor.chest with spacehorizons:space_chestplate");
        cmd(5, "item replace entity @s hotbar.0 with " + tank(2400, 2400));
        suitTicker("B capacete e peitoral com recipiente", 300, 30, new int[] {60, 280}, run -> {
            int spent = run.start().tank() - run.end().tank();
            if (run.end().reserve() != OxygenMath.MAX_RESERVE) {
                return "reserva devia estar cheia, " + run.end().reserve();
            }
            if (Math.abs(spent - 299) > 3) {
                return "recipiente devia gastar ~299 em 300 ticks (1 por tick), gastou " + spent;
            }
            if (run.minHealth() >= 20.0F) {
                return "com so 2 pecas devia haver dano de frio";
            }
            return run.end().frozen() >= 138 ? null : "com so 2 pecas devia estar congelado, frozen=" + run.end().frozen();
        });

        // D: traje completo (terceira pessoa de frente, depois primeira pessoa e a tela do inventário)
        cmd(5, "effect give @s minecraft:instant_health 1 5");
        cmd(5, "item replace entity @s armor.legs with spacehorizons:space_leggings");
        cmd(5, "item replace entity @s armor.feet with spacehorizons:space_boots");
        step(5, mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        suitTicker("D1 traje completo em terceira pessoa", 60, 20, new int[] {50}, null);
        step(5, mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        suitTicker("D2 traje completo", 260, 30, new int[] {250}, run -> {
            int spent = run.start().tank() - run.end().tank();
            if (run.end().frozen() != 0) {
                return "com 4 pecas devia ter descongelado, frozen=" + run.end().frozen();
            }
            if (run.end().reserve() != OxygenMath.MAX_RESERVE) {
                return "reserva devia estar cheia, " + run.end().reserve();
            }
            return Math.abs(spent - 259) > 3 ? "recipiente devia gastar ~259, gastou " + spent : null;
        });
        step(30, mc -> probeAdvancements(mc, "traje completo ha 600+ ticks respirando: suit_up e first_breath devem estar feitas"));
        step(10, mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
        shot(40, "suit_inventory");
        step(10, mc -> mc.gui.setScreen(null));
        step(10, mc -> openAdvancementsTab(mc));
        shot(40, "suit_advancements");
        step(10, mc -> mc.gui.setScreen(null));

        // E: quatro recipientes de cargas diferentes (barra de atalhos, mochila e mão secundária): sai do MAIS VAZIO,
        // seja qual for o lugar. Ordem de varredura: hotbar.0=120, hotbar.1=60, inventory.3=90, mão secundária=30.
        cmd(5, "item replace entity @s hotbar.0 with " + tank(2400, 120));
        cmd(5, "item replace entity @s hotbar.1 with " + tank(2400, 60));
        cmd(5, "item replace entity @s inventory.3 with " + tank(2400, 90));
        cmd(5, "item replace entity @s weapon.offhand with " + tank(2400, 30));
        suitTicker("E recipientes acabam", 380, 20, new int[] {30, 100, 160, 360}, run -> {
            // aos 100 ticks: a mão secundária e o hotbar.1 (os mais vazios) esgotaram, o inventory.3 gastou o que faltou
            // (~99 ticks de consumo menos o que os dois deram) e o hotbar.0 (o mais cheio) está intacto
            List<Integer> first = run.start().tanks();
            List<Integer> at100 = run.at(100).tanks();
            int expectedInventory = first.get(2) - (99 - first.get(1) - first.get(3));
            boolean order = first.size() == 4 && at100.size() == 4 && Math.abs(at100.get(0) - first.get(0)) <= 1
                    && at100.get(1) == 0 && at100.get(3) == 0 && Math.abs(at100.get(2) - expectedInventory) <= 4;
            if (!order) {
                return "no tick 100 esperado [" + first.get(0) + ", 0, ~" + expectedInventory + ", 0] (o mais vazio primeiro), veio "
                        + at100 + " (inicio " + first + ")";
            }
            if (run.end().tank() != 0) {
                return "os recipientes deviam ter acabado, sobrou " + run.end().tank();
            }
            return run.end().reserve() < OxygenMath.MAX_RESERVE - 40 ? null : "a reserva devia estar caindo, " + run.end().reserve();
        });

        // H: só o capacete, com recipiente cheio: não respira (só capacete + peitoral respiram)
        cmd(5, "effect give @s minecraft:instant_health 1 5");
        cmd(5, "spacehorizons oxygen set 600");
        cmd(5, "clear @s");
        cmd(5, "item replace entity @s armor.head with spacehorizons:space_helmet");
        cmd(5, "item replace entity @s hotbar.0 with " + tank(2400, 2400));
        suitTicker("H so o capacete", 100, 20, new int[] {90}, run -> {
            if (run.end().tank() != 2400) {
                return "o recipiente nao devia ser tocado, " + run.end().tank();
            }
            return run.end().reserve() < run.start().reserve() - 80 ? null
                    : "sem peitoral a reserva devia cair, " + run.start().reserve() + " -> " + run.end().reserve();
        });

        // F: regra de jogo desligada, com capacete, peitoral e recipiente (do trecho H): sem frio e sem gastar o recipiente
        cmd(5, "item replace entity @s armor.chest with spacehorizons:space_chestplate");
        cmd(5, "gamerule spacehorizons:vacuum_damage false");
        suitTicker("F regra desligada", 100, 20, new int[] {90}, run -> run.end().frozen() == 0
                && run.end().reserve() == OxygenMath.MAX_RESERVE && run.end().tank() == run.start().tank() ? null
                : "sem a regra nao devia haver frio nem consumo: frozen=" + run.end().frozen() + " reserva=" + run.end().reserve()
                + " recipiente=" + run.end().tank());
        cmd(5, "gamerule spacehorizons:vacuum_damage true");

        // G: criativo no vácuo, ainda com capacete, peitoral e recipiente: isento do frio e não gasta o recipiente
        cmd(5, "gamemode creative");
        suitTicker("G criativo", 100, 20, new int[] {90}, run -> run.end().frozen() == 0
                && run.end().reserve() == OxygenMath.MAX_RESERVE && run.end().tank() == run.start().tank() ? null
                : "criativo devia ser isento: frozen=" + run.end().frozen() + " reserva=" + run.end().reserve()
                + " recipiente=" + run.end().tank());

        cmd(5, "gamerule send_command_feedback true");
        cmd(5, "gamerule show_advancement_messages true");
        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /**
     * Loga a cada {@code every} ticks o estado do traje, tira prints nos ticks indicados e, no último tick, entrega o
     * trecho medido a {@code check} (devolve null se estiver certo, ou o motivo da falha).
     */
    private void suitTicker(String label, int ticks, int every, int[] shotAt, java.util.function.Function<SuitRun, String> check) {
        tickerStep(0, new Ticker() {
            int n;
            final List<SuitProbe> samples = new ArrayList<>();
            float minHealth = Float.MAX_VALUE;

            @Override
            public boolean tick(Minecraft mc) {
                n++;
                minHealth = Math.min(minHealth, mc.player.getHealth());
                if (n == 1 || n % every == 0) {
                    samples.add(suitProbe(mc));
                    logSuit(mc, label + " tick=" + n);
                }
                for (int at : shotAt) {
                    if (n == at) {
                        logSuit(mc, label + " PRINT tick=" + n);
                        grab(mc, "suit_" + label.split(" ")[0].toLowerCase() + "_t" + n);
                    }
                }
                if (n >= ticks) {
                    if (n % every != 0) {
                        samples.add(suitProbe(mc));
                    }
                    SuitRun run = new SuitRun(samples, every, minHealth);
                    String problem = check == null ? null : check.apply(run);
                    SpaceHorizons.LOGGER.info(TAG + "SUIT RESULTADO {}: {} (inicio={} fim={} menorVida={})", label,
                            check == null ? "sem verificacao automatica" : problem == null ? "OK" : "FALHA: " + problem,
                            run.start(), run.end(), f(minHealth));
                    return true;
                }
                return false;
            }
        });
    }

    private static void logSuit(Minecraft mc, String label) {
        LocalPlayer p = mc.player;
        OxygenState s = p.getData(OxygenSetup.OXYGEN);
        SuitPieces pieces = SuitGear.piecesOf(p);
        List<Integer> tanks = new ArrayList<>();
        for (ItemStack stack : SuitGear.containers(p)) {
            tanks.add(OxygenStorage.get(stack));
        }
        SpaceHorizons.LOGGER.info(TAG + "SUIT {}: pecas={} (capacete={} peitoral={} calcas={} botas={}) respira={} frio_protegido={} "
                + "reserva={} fonte={} congelado={}/{} cheio={} vida={} recipientes={} total={} hud='{}' hudVisivel={} dim={} criativo={}",
                label, pieces.count(), pieces.helmet(), pieces.chestplate(), pieces.leggings(), pieces.boots(), pieces.canBreathe(),
                pieces.protectsFromCold(), s.reserve(), s.supplied(), p.getTicksFrozen(), p.getTicksRequiredToFreeze(),
                p.isFullyFrozen(), f(p.getHealth()), tanks, SuitGear.totalOxygen(p), OxygenHud.label(mc, s).getString(),
                OxygenHud.isVisible(mc), mc.level.dimension().identifier().getPath(), p.isCreative());
    }

    // ======================================================================= dimensão de espaço aberto (frente A3)

    /** Ponto do espaço: a Lua no relógio atual do mundo. */
    private static double[] moonSpace(Minecraft mc) {
        double[] m = new double[3];
        SolarSystem.MOON.positionAt(SpaceSun.clock(mc.level), m);
        return m;
    }

    /** Ponto do espaço a {@code altitude} blocos sobre a superfície da Lua, do lado voltado para a Terra. */
    private static double[] nearMoon(Minecraft mc, double altitude) {
        double[] m = moonSpace(mc);
        double len = Math.sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2]);
        double d = SolarSystem.MOON.planet().radius() + altitude;
        return new double[] {m[0] - m[0] / len * d, m[1] - m[1] / len * d, m[2] - m[2] / len * d};
    }

    /**
     * Relógio dentro dos próximos 8 dias (um ciclo lunar) em que a Lua está mais a +X (cima da dimensão, Y positivo) ou mais a -X
     * (Y negativo) do espaço: a faixa de Y que o contrato de SpaceDimension produz perto da Lua.
     */
    private static long moonClock(Minecraft mc, boolean plusX) {
        long from = (long) Math.floor(SpaceSun.clock(mc.level) / 24000.0) * 24000L;
        long best = from;
        double bestX = plusX ? -1.0e18 : 1.0e18;
        double[] m = new double[3];
        for (long c = from; c < from + 8 * 24000L; c += 100) {
            SolarSystem.MOON.positionAt(c, m);
            if (plusX ? m[0] > bestX : m[0] < bestX) {
                bestX = m[0];
                best = c;
            }
        }
        return best;
    }

    /**
     * {@code /spacehorizons goto space x y z} para um ponto dado em coordenadas do ESPAÇO do SolarSystem (convertido para as da
     * dimensão na hora, com o relógio do mundo): de um planeta é a travessia contínua, do espaço é um teleporte.
     */
    private void gotoSpacePoint(int wait, java.util.function.Function<Minecraft, double[]> spacePoint, String label) {
        step(wait, mc -> {
            double[] s = spacePoint.apply(mc);
            double[] d = new double[3];
            SpaceDimension.fromSpace(s[0], s[1], s[2], d);
            String command = String.format(java.util.Locale.ROOT, "spacehorizons goto space %.3f %.3f %.3f", d[0], d[1], d[2]);
            SpaceHorizons.LOGGER.info(TAG + "{}: /{}", label, command);
            mc.player.connection.sendCommand(command);
        });
    }

    /**
     * Manda o comando e fica {@code ticks} ticks de olho numa travessia contínua: loga a troca de dimensão (tick, posição) e toda tela aberta (a de
     * carregamento não pode aparecer), e no fim o resultado.
     */
    private void transferWatch(String prefix, String command, int ticks) {
        tickerStep(0, new Ticker() {
            int n;
            int screens;
            String firstDim;
            String lastDim;

            @Override
            public boolean tick(Minecraft mc) {
                n++;
                String dim = mc.level.dimension().identifier().getPath();
                if (firstDim == null) {
                    firstDim = dim;
                }
                // o comando sai com o observador já ligado: a troca e uma tela de carregamento não passam despercebidas
                if (n == 3) {
                    SpaceHorizons.LOGGER.info(TAG + "{} /{}", prefix, command);
                    mc.player.connection.sendCommand(command);
                }
                if (lastDim != null && !lastDim.equals(dim)) {
                    SpaceHorizons.LOGGER.info(TAG + "{} TROCA DE DIMENSAO tick={} {} -> {} pos=({}, {}, {}) yaw={} pitch={}", prefix, n,
                            lastDim, dim, f(mc.player.getX()), f(mc.player.getY()), f(mc.player.getZ()), f(mc.player.getYRot()),
                            f(mc.player.getXRot()));
                }
                lastDim = dim;
                if (mc.gui.screen() != null) {
                    screens++;
                    if (screens <= 3) {
                        SpaceHorizons.LOGGER.info(TAG + "{} TELA ABERTA tick={} {}", prefix, n, mc.gui.screen().getClass().getName());
                    }
                }
                if (n >= ticks) {
                    SpaceHorizons.LOGGER.info(TAG + "{} RESULTADO: dimensao {} -> {}, ticks com tela aberta={} de {} ({})", prefix, firstDim,
                            dim, screens, n, screens == 0 ? "SEM tela de carregamento" : "COM tela");
                    return true;
                }
                return false;
            }
        });
    }

    /** Olha para o centro de um corpo visto de dentro da dimensão de espaço (pelo SkyObserver) e loga azimute/elevação. */
    private static void lookInSpace(Minecraft mc, SolarSystem.Body target) {
        double clock = SpaceSun.clock(mc.level);
        SkyObserver observer = SkyObserver.forSpace(clock, mc.player.getX(), mc.player.getY(), mc.player.getZ());
        BodySky.Apparent a = observer.apparent(target);
        double elevation = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, a.y()))));
        float yaw = (float) Math.toDegrees(Math.atan2(-a.x(), a.z()));
        SpaceHorizons.LOGGER.info(TAG + "ESPACO olhando para {}: relogio={} distancia={} raio angular={} graus azimute(yaw)={} elevacao={} graus",
                target.planet().id().getPath(), f(clock), f(a.distance()), f(Math.toDegrees(a.angularRadius())), f(yaw), f(elevation));
        mc.player.setYRot(yaw);
        mc.player.setXRot((float) Math.max(-90.0, Math.min(90.0, -elevation)));
    }

    /**
     * Estado do jogador no espaço (ou no planeta): dimensão, posição, posição no espaço, corpo mais próximo e altitude, a gravidade
     * (cliente e servidor), o clima e o oxigênio. Vale em qualquer dimensão.
     */
    private static void logSpace(Minecraft mc, String label) {
        LocalPlayer p = mc.player;
        double clock = SpaceSun.clock(mc.level);
        double[] s = new double[3];
        String where;
        if (SpaceDimension.is(mc.level)) {
            SpaceDimension.toSpace(p.getX(), p.getY(), p.getZ(), s);
            SolarSystem.Body nearest = TravelMath.nearestBody(clock, s[0], s[1], s[2]);
            where = nearest.planet().id().getPath() + " altitude=" + f(TravelMath.altitudeOver(nearest, clock, s[0], s[1], s[2]));
        } else {
            java.util.Optional<SolarSystem.Body> body = Planets.forLevel(mc.level).flatMap(SolarSystem::bodyOf);
            if (body.isPresent()) {
                SpaceFrame.toSpace(body.get(), clock, p.getX(), p.getY(), p.getZ(), s);
                where = body.get().planet().id().getPath() + " altitude=" + f(p.getY() - body.get().planet().seaLevel());
            } else {
                where = "fora do sistema";
            }
        }
        net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
        net.minecraft.server.level.ServerPlayer sp = server == null ? null : server.getPlayerList().getPlayer(p.getUUID());
        double serverGravity = sp == null ? Double.NaN : sp.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY);
        OxygenState oxygen = p.getData(OxygenSetup.OXYGEN);
        SpaceHorizons.LOGGER.info(TAG + "ESPACO {}: dim={} tipo={} noEspaco={} pos=({}, {}, {}) espaco=({}, {}, {}) {} gravidade(cliente)={} "
                + "gravidade(servidor)={} podeTerClima={} chuva={} trovoada={} vivo={} vida={} vacuo={} oxigenio={}/{} hudVisivel={} criativo={}",
                label, mc.level.dimension().identifier().getPath(), mc.level.dimensionTypeRegistration().unwrapKey().map(k -> k.identifier().toString())
                        .orElse("?"), SpaceSun.inSpace(), f(p.getX()), f(p.getY()), f(p.getZ()), f(s[0]), f(s[1]), f(s[2]), where,
                f(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY)), f(serverGravity), mc.level.canHaveWeather(),
                f(mc.level.getRainLevel(1.0F)), f(mc.level.getThunderLevel(1.0F)), p.isAlive(), f(p.getHealth()),
                OxygenSetup.isVacuumAt(mc.level, p.getEyeY()), oxygen.reserve(), OxygenMath.MAX_RESERVE, OxygenHud.isVisible(mc), p.isCreative());
    }

    /**
     * Voo em linha reta no espaço aberto empurrado pelo servidor (ver {@link SpaceFlightProbe}): começa no ponto atual, espera o fim,
     * tira um print no meio e deixa o resultado (tempo de trabalho do tick, ticks lentos, passos segurados) no log.
     */
    private void flightLeg(String label, double blocksPerSecond, double dirX, double dirZ, int ticks, boolean tickets, int distance) {
        double perTick = blocksPerSecond / 20.0;
        double norm = Math.hypot(dirX, dirZ);
        Vec3 velocity = new Vec3(dirX / norm * perTick, 0.0, dirZ / norm * perTick);
        step(5, mc -> SpaceFlightProbe.start(mc.getSingleplayerServer(), mc.player.getUUID(), label, velocity, ticks, tickets, distance));
        tickerStep(0, new Ticker() {
            int n;
            boolean shot;

            @Override
            public boolean tick(Minecraft mc) {
                n++;
                if (!shot && n == 40) {
                    shot = true;
                    logSpace(mc, "voo " + label + " tick=" + n);
                    grab(mc, "sp_fly_" + label);
                }
                if (SpaceFlightProbe.isDone() && n > 5) {
                    return true;
                }
                if (n > ticks * 12 + 1200) {
                    SpaceHorizons.LOGGER.error(TAG + "VOO {} nao terminou em {} ticks do cliente", label, n);
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * Dimensão de espaço aberto e o céu visto de dentro dela. A: a Terra a 4.900 de altitude (antes da troca); B: a travessia
     * contínua para o espaço no mesmo ponto, sem tela de carregamento; C: sol, estrelas e a Lua perto da Terra; D: sobrevivência
     * (gravidade 0, vácuo, oxigênio, compressor); E: meio do caminho; F: perto da Lua com a Lua em Y negativo (o vácuo do mundo
     * desligado); G: a Lua em Y positivo; H: voos em linha reta medindo o custo dos chunks da dimensão vazia; I: a volta à Terra e à
     * Lua pelo comando (contínua dentro da zona do corpo, teleporte à superfície fora dela).
     */
    private void spaceScenario() {
        cmd(5, "gamerule send_command_feedback false");
        cmd(5, "gamerule show_advancement_messages false");
        cmd(5, "difficulty normal");
        cmd(5, "weather clear 1000000");
        cmd(5, "time set 6000");

        // A. a Terra a 4.900 de altitude (a vista de antes da troca) olhando para baixo e de lado
        tickerStep(0, waitForMap(24000));
        cmd(20, "tp @s 0 4963 0");
        fly(20);
        look(20, EAST, 90f);
        shot(450, "sp_a_earth_4900_down");
        look(10, EAST, 40f);
        shot(60, "sp_a_earth_4900_oblique");
        look(10, EAST, 90f);
        step(5, mc -> logSpace(mc, "A Terra a 4.900, antes da troca"));

        // B. a travessia contínua para o espaço, no mesmo ponto e com o mesmo olhar: o mesmo print do outro lado
        transferWatch("sp_b", "spacehorizons goto space", 90);
        step(5, mc -> logSpace(mc, "B no espaco, mesmo ponto"));
        shot(300, "sp_b_space_4900_down");
        look(10, EAST, 40f);
        shot(60, "sp_b_space_4900_oblique");

        // C. o céu do espaço perto da Terra: a Lua, o sol a pino (relógio 6000) e as estrelas
        step(5, mc -> lookInSpace(mc, SolarSystem.MOON));
        shot(60, "sp_c_near_earth_moon");
        look(10, EAST, -90f);
        shot(30, "sp_c_near_earth_sun_zenith");
        look(10, 135f, 0f);
        shot(30, "sp_c_near_earth_stars");
        step(5, mc -> lookInSpace(mc, SolarSystem.EARTH));
        shot(30, "sp_c_near_earth_earth");

        // D. sobrevivência no espaço: gravidade 0, vácuo (a reserva cai) e o compressor sem ar
        cmd(5, "gamemode survival");
        cmd(5, "effect give @s minecraft:instant_health 1 5");
        step(20, mc -> logSpace(mc, "D1 sobrevivencia no espaco (esperado: gravidade 0 nos dois lados, vacuo, HUD visivel)"));
        oxygenTicker("S espaco (a reserva cai)", 160, 40, 150);
        step(5, mc -> logSpace(mc, "D2 sobrevivencia depois de 160 ticks"));
        cmd(5, "gamemode creative");
        fly(10);
        cmd(5, "setblock 0 8 0 spacehorizons:oxygen_compressor");
        cmd(5, "item replace block 0 8 0 container.0 with spacehorizons:oxygen_tank_small");
        cmd(5, "item replace block 0 8 0 container.1 with minecraft:coal 4");
        step(40, mc -> probeCompressor(mc, "espaco t=40 (esperado NO_AIR)", new BlockPos(0, 8, 0)));
        step(60, mc -> probeCompressor(mc, "espaco t=100 (esperado NO_AIR)", new BlockPos(0, 8, 0)));
        cmd(5, "setblock 0 8 0 minecraft:air");

        // E. meio do caminho entre a Terra e a Lua (a Terra e a Lua, as duas, vistas do espaço aberto)
        gotoSpacePoint(10, mc -> {
            double[] m = moonSpace(mc);
            return new double[] {m[0] * 0.5, m[1] * 0.5, m[2] * 0.5};
        }, "E meio do caminho");
        step(40, mc -> logSpace(mc, "E meio do caminho"));
        step(5, mc -> lookInSpace(mc, SolarSystem.EARTH));
        shot(150, "sp_e_mid_earth");
        step(5, mc -> lookInSpace(mc, SolarSystem.MOON));
        shot(150, "sp_e_mid_moon");
        look(10, 60f, 20f);
        shot(30, "sp_e_mid_stars");

        // F. perto da Lua (1.000 de altitude), com a Lua em Y negativo no espaço (relógio 6000): o vácuo do mundo desligado
        gotoSpacePoint(10, mc -> nearMoon(mc, 1000.0), "F perto da Lua");
        step(60, mc -> logSpace(mc, "F perto da Lua, Y negativo"));
        step(5, mc -> lookInSpace(mc, SolarSystem.EARTH));
        shot(150, "sp_f_moon_earth");
        step(5, mc -> lookInSpace(mc, SolarSystem.MOON));
        shot(150, "sp_f_moon_moon");
        look(10, 0f, 0f);
        shot(30, "sp_f_moon_horizon");
        step(100, mc -> logSpace(mc, "F depois de 5 s em Y negativo (esperado: vivo)"));

        // G. a Lua em Y positivo (a outra metade do mês lunar)
        step(5, mc -> mc.player.connection.sendCommand("time set " + moonClock(mc, true)));
        gotoSpacePoint(40, mc -> nearMoon(mc, 1000.0), "G perto da Lua, Y positivo");
        step(60, mc -> logSpace(mc, "G perto da Lua, Y positivo"));
        step(5, mc -> lookInSpace(mc, SolarSystem.EARTH));
        shot(150, "sp_g_moon_plusy_earth");
        step(5, mc -> lookInSpace(mc, SolarSystem.MOON));
        shot(150, "sp_g_moon_plusy_moon");
        cmd(5, "time set 6000");

        // H. voos em linha reta no espaço vazio, empurrados pelo servidor: o custo dos chunks da dimensão de altura 16
        int k = 0;
        for (Object[] leg : new Object[][] {
                {"300bps", 300.0, 1.0, 0.0, 200, true, 0},
                {"600bps", 600.0, 1.0, 0.0, 200, true, 0},
                {"1200bps", 1200.0, 1.0, 0.0, 200, true, 0},
                {"4000bps", 4000.0, 1.0, 0.0, 200, true, 0},
                {"600bps_sem_tickets", 600.0, 1.0, 0.0, 200, false, 0},
                {"600bps_diagonal", 600.0, 1.0, 1.0, 200, true, 0},
                {"600bps_dist2", 600.0, 1.0, 0.0, 200, true, 2},
                {"1200bps_dist2", 1200.0, 1.0, 0.0, 200, true, 2},
        }) {
            // um ponto novo (chunks nunca vistos) a ~280.000 acima do centro da Terra (Y = 300.000 com o viés de 20.000), longe dos
            // dois corpos (a Terra tem raio 104.304 e a Lua está a 600.000)
            double startX = 20_000.0 * (++k);
            cmd(10, String.format(java.util.Locale.ROOT, "spacehorizons goto space %.1f 300000 %.1f", startX, -startX));
            step(120, mc -> logSpace(mc, "H inicio do voo " + leg[0]));
            flightLeg((String) leg[0], (Double) leg[1], (Double) leg[2], (Double) leg[3], (Integer) leg[4], (Boolean) leg[5], (Integer) leg[6]);
            step(60, mc -> {
            });
        }

        // I. a volta pelo comando: dentro da zona do corpo é a travessia contínua; fora dela, o teleporte à superfície
        cmd(10, "time set 6000");
        // 4.900 de altitude sobre o spawn da Terra: dentro da zona da Terra (saída em 5.000), com o viés Y de SpaceDimension
        cmd(20, String.format(java.util.Locale.ROOT, "spacehorizons goto space 0 %.1f 0",
                Planets.EARTH.radius() + 4_900.0 + SpaceDimension.Y_BIAS));
        look(60, EAST, 90f);
        step(5, mc -> logSpace(mc, "I1 no espaco sobre a Terra, antes da volta"));
        transferWatch("sp_i1", "spacehorizons goto earth", 90);
        step(5, mc -> logSpace(mc, "I1 depois do goto earth (esperado: dim=overworld, mesmo ponto)"));
        shot(450, "sp_i1_back_earth_4900_down");
        look(10, EAST, 40f);
        shot(60, "sp_i1_back_earth_4900_oblique");

        gotoSpacePoint(10, mc -> nearMoon(mc, 1000.0), "I2 perto da Lua");
        step(60, mc -> logSpace(mc, "I2 perto da Lua, antes da volta"));
        step(5, mc -> lookInSpace(mc, SolarSystem.MOON));
        transferWatch("sp_i2", "spacehorizons goto moon", 90);
        step(5, mc -> logSpace(mc, "I2 depois do goto moon (esperado: dim=moon, altitude ~1000)"));
        shot(300, "sp_i2_back_moon_down");

        // fora das zonas: teleporte à superfície (com tela de carregamento)
        gotoSpacePoint(10, mc -> {
            double[] m = moonSpace(mc);
            return new double[] {m[0] * 0.5, m[1] * 0.5, m[2] * 0.5};
        }, "I3 meio do caminho");
        step(60, mc -> logSpace(mc, "I3 meio do caminho, antes de goto earth"));
        cmd(10, "spacehorizons goto earth");
        step(300, mc -> logSpace(mc, "I3 depois do goto earth fora da zona (esperado: overworld na superficie)"));
        shot(60, "sp_i3_earth_surface");
        // ~30.000 de altitude sobre a Terra (fora da zona: Y = raio + 30.000 + viés)
        cmd(20, String.format(java.util.Locale.ROOT, "spacehorizons goto space 0 %.1f 0",
                Planets.EARTH.radius() + 30_000.0 + SpaceDimension.Y_BIAS));
        step(60, mc -> logSpace(mc, "I4 de volta ao espaco"));
        cmd(10, "spacehorizons goto moon");
        step(300, mc -> logSpace(mc, "I4 depois do goto moon fora da zona (esperado: moon na superficie)"));
        shot(60, "sp_i4_moon_surface");

        cmd(5, "gamerule send_command_feedback true");
        cmd(5, "gamerule show_advancement_messages true");
        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /**
     * Spike S1 das naves: o céu do espaço aberto com atitude virtual ({@code /spacehorizons skytest}). A: a travessia contínua
     * Terra para espaço com o modo desligado (sem salto, a 4.900 de altitude); B: a 30.000 de altitude, com o relógio em 0 (sol a
     * leste, a 90 graus do nadir da Terra: o terminador aparece), atitudes identidade, yaw 90, pitch 45, roll 90 e uma
     * combinação, cada uma com um print apontado entre a Terra e o sol e um de câmera fixa; C: giro contínuo, 6 prints em 3 s; D: ligar
     * e desligar (sem salto) e a ida à Terra com o modo ligado.
     */
    private void skyTestScenario() {
        cmd(5, "gamerule send_command_feedback false");
        cmd(5, "gamerule show_advancement_messages false");
        cmd(5, "weather clear 1000000");
        cmd(5, "time set 6000");
        step(5, mc -> mc.options.fov().set(110));

        // A. a travessia contínua com o modo desligado: o mesmo ponto nos dois lados
        tickerStep(0, waitForMap(24000));
        cmd(20, "tp @s 0 4963 0");
        fly(20);
        look(20, EAST, 90f);
        shot(300, "sk_a1_earth_4900_down");
        transferWatch("sk_a", "spacehorizons goto space", 90);
        step(5, mc -> logSpace(mc, "A no espaco, mesmo ponto"));
        shot(200, "sk_a2_space_4900_down");

        // B. a 30.000 de altitude, sol a leste logo abaixo do horizonte local (relógio 22.500: o terminador cruza a calota visível da Terra)
        cmd(10, "time set 22500");
        cmd(10, String.format(java.util.Locale.ROOT, "spacehorizons goto space 0 %.1f 0", Planets.EARTH.radius() + 30_000.0 + SpaceDimension.Y_BIAS));
        step(120, mc -> logSpace(mc, "B a 30.000 de altitude"));
        fly(10);
        skyAttitudePrints("b0_identity", null);
        skyAttitudePrints("b1_yaw90", "spacehorizons skytest 90 0 0");
        skyAttitudePrints("b2_pitch45", "spacehorizons skytest 0 45 0");
        skyAttitudePrints("b3_roll90", "spacehorizons skytest 0 0 90");
        skyAttitudePrints("b4_combo", "spacehorizons skytest 90 45 90");
        // a Lua com a mesma atitude: apontada e com zoom (FOV 30), para ver o lado iluminado dela
        step(5, mc -> {
            double clock = SpaceSun.clock(mc.level);
            SkyObserver observer = SkyObserver.of(mc.level, mc.player.getX(), mc.player.getY(), mc.player.getZ(), clock).orElseThrow();
            BodySky.Apparent moon = observer.apparent(SolarSystem.MOON);
            mc.player.setYRot((float) Math.toDegrees(Math.atan2(-moon.x(), moon.z())));
            mc.player.setXRot((float) -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, moon.y())))));
            mc.options.fov().set(30);
        });
        shot(20, "sk_b5_combo_moon_zoom");
        step(5, mc -> mc.options.fov().set(110));

        // B2. prova de equivalência pixel a pixel: girar a nave por psi em torno do "cima" e a câmera para yaw0 + psi mostra o mesmo céu
        // (estrelas, sol, Terra e Lua) que a nave sem atitude olhando para yaw0 (a rotação em torno do cima leva o yaw Y a Y - psi)
        cmd(10, "spacehorizons skytest off");
        look(30, EAST, -10f);
        shot(40, "sk_e0_ref_east");
        cmd(10, "spacehorizons skytest 90 0 0");
        look(30, 0f, -10f);
        shot(40, "sk_e1_yaw90_cam0");
        cmd(10, "spacehorizons skytest 180 0 0");
        look(30, 90f, -10f);
        shot(40, "sk_e2_yaw180_cam90");

        // C. giro contínuo de 60 graus/s em torno do eixo local z: a câmera olha ao longo do eixo (yaw 0 = +z), então o céu gira em torno do centro
        cmd(10, "spacehorizons skytest off");
        look(20, SOUTH, 0f);
        tickerStep(0, new Ticker() {
            int n;
            long startNanos;
            int shots;

            @Override
            public boolean tick(Minecraft mc) {
                if (n == 0) {
                    mc.player.connection.sendCommand("spacehorizons skytest spin 60 z");
                } else if (n == 5) {
                    startNanos = System.nanoTime();
                }
                n++;
                if (n > 5 && (n - 5) % 10 == 0 && shots < 6) {
                    shots++;
                    org.joml.Quaterniondc q = SkyAttitude.current();
                    double secs = (System.nanoTime() - startNanos) * 1.0e-9;
                    SpaceHorizons.LOGGER.info(TAG + "GIRO print {} t={}s atitude(R)={}", shots, f(secs),
                            q == null ? "desligada" : f(q.x()) + "," + f(q.y()) + "," + f(q.z()) + "," + f(q.w()));
                    logSun(mc, "giro" + shots);
                    grab(mc, "sk_c_spin" + shots);
                }
                return shots >= 6 && (n - 5) % 10 == 5;
            }
        });

        // D. ligar e desligar: o mesmo ponto e olhar, atitude pitch 45 ligada e desligada duas vezes
        look(10, EAST, -30f);
        cmd(10, "spacehorizons skytest off");
        shot(40, "sk_d1_off");
        cmd(10, "spacehorizons skytest 0 45 0");
        shot(40, "sk_d2_on");
        cmd(10, "spacehorizons skytest off");
        shot(40, "sk_d3_off_again");
        // modo ligado, ida à Terra (4.900) e volta: o estado persiste e a Terra não é afetada
        cmd(10, "spacehorizons skytest 0 45 0");
        cmd(10, String.format(java.util.Locale.ROOT, "spacehorizons goto space 0 %.1f 0", Planets.EARTH.radius() + 4_900.0 + SpaceDimension.Y_BIAS));
        look(60, EAST, 90f);
        step(5, mc -> logSpace(mc, "D4 no espaco a 4.900 com a atitude ligada"));
        shot(60, "sk_d4_space_on_4900");
        transferWatch("sk_d5", "spacehorizons goto earth", 90);
        step(5, mc -> logSpace(mc, "D5 de volta a Terra com o modo ligado (esperado: overworld, atitude sem efeito)"));
        shot(200, "sk_d5_earth_with_mode_on");
        transferWatch("sk_d6", "spacehorizons goto space", 90);
        step(5, mc -> logSpace(mc, "D6 de volta ao espaco com o modo ligado"));
        shot(100, "sk_d6_space_on_again");
        cmd(10, "spacehorizons skytest off");

        cmd(5, "gamerule send_command_feedback true");
        cmd(5, "gamerule show_advancement_messages true");
        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /**
     * Liga a atitude (ou não, com command null) e tira dois prints: apontado para o meio entre a Terra e o sol (os dois no quadro, para
     * ver o lado iluminado da Terra virado para o sol) e de câmera fixa (leste, 30 graus para baixo), que mostra o céu girando.
     */
    private void skyAttitudePrints(String tag, String command) {
        if (command != null) {
            cmd(10, command);
        }
        step(30, mc -> {
            double clock = SpaceSun.clock(mc.level);
            SkyObserver observer = SkyObserver.of(mc.level, mc.player.getX(), mc.player.getY(), mc.player.getZ(), clock).orElseThrow();
            double[] sun = new double[3];
            observer.sun(sun);
            BodySky.Apparent earth = observer.apparent(SolarSystem.EARTH);
            double[] mid = {sun[0] + earth.x(), sun[1] + earth.y(), sun[2] + earth.z()};
            double norm = Math.sqrt(mid[0] * mid[0] + mid[1] * mid[1] + mid[2] * mid[2]);
            mid[0] /= norm;
            mid[1] /= norm;
            mid[2] /= norm;
            mc.player.setYRot((float) Math.toDegrees(Math.atan2(-mid[0], mid[2])));
            mc.player.setXRot((float) -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, mid[1])))));
        });
        shot(15, "sk_" + tag + "_aimed");
        look(10, EAST, -30f);
        shot(15, "sk_" + tag + "_fixed");
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

    /** Clique direito de verdade num bloco, pela face dada (o servidor valida o alcance como no jogo). */
    private static void useOnBlock(Minecraft mc, BlockPos pos, Direction face) {
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false));
    }

    /** Ponto de vista de fora do foguete: teleporta, voa e olha na direção dada. */
    private void rocketView(String name, double x, double y, double z, float yaw, float pitch) {
        cmd(10, "tp @s " + x + " " + y + " " + z);
        fly(5);
        look(10, yaw, pitch);
        shot(40, name);
    }

    /**
     * Foguete de blocos, protótipo 3a-1 (docs/DESIGN_FOGUETE.md): constrói por comandos um foguete pequeno sobre uma plataforma
     * 3x3 no ar (motor, tanque, cockpit, cascos, pernas, e uma fornalha e um baú para testar block entities), abastece o tanque
     * com galões de verdade, monta com a mão vazia (clique no cockpit), confere que os blocos sumiram e a entidade existe,
     * tira prints de vários ângulos, senta o jogador (terceira e primeira pessoa), move a entidade 10 blocos para cima e
     * devolve, desmonta e compara o mundo com o de antes, recusa a montagem encostada em terreno, e repete com yaw de 90 graus
     * para conferir a rotação dos estados. Também salva e recarrega a entidade em NBT e lê o pacote de spawn.
     */
    private void rocketScenario() {
        BlockPos tank = RocketDevtest.at(new BlockPos(0, 2, 0));
        BlockPos cockpit = RocketDevtest.at(new BlockPos(0, 4, 0));
        double bx = RocketDevtest.BASE.getX() + 0.5;
        double by = RocketDevtest.BASE.getY();
        double bz = RocketDevtest.BASE.getZ() + 0.5;
        cmd(20, "weather clear 1000000");
        cmd(10, "gamemode creative");
        fly(10);
        cmd(10, "tp @s " + (bx + 2.7) + " " + (by + 0.88) + " " + bz);
        fly(10);
        cmd(10, "clear @s");
        step(10, mc -> ContentCraftCheck.run(mc.getSingleplayerServer()));
        cmd(10, RocketDevtest.clearCommand());
        for (String command : RocketDevtest.buildCommands()) {
            cmd(4, command);
        }
        // de pé a leste do foguete, olhando para oeste: o raio da mira bate no lado do tanque (nada no caminho)
        look(30, 90f, 0f);
        shot(30, "rk_a_blocks_built");
        step(0, mc -> RocketDevtest.tankDisplay(mc, "vazio"));

        // abastece com dois galões cheios (clique direito de verdade no tanque)
        cmd(5, "give @s spacehorizons:fuel_canister[spacehorizons:fuel_mb=1000]");
        cmd(5, "give @s spacehorizons:fuel_canister[spacehorizons:fuel_mb=1000]");
        step(10, mc -> {
            mc.player.getInventory().setSelectedSlot(0);
            useOnBlock(mc, tank, Direction.EAST);
        });
        step(10, mc -> {
            mc.player.getInventory().setSelectedSlot(1);
            useOnBlock(mc, tank, Direction.EAST);
        });
        step(15, mc -> RocketDevtest.tankDisplay(mc, "com 2000 mB"));
        shot(5, "rk_b_tank_hud");
        step(5, RocketDevtest::tankBreakAndReplace);
        step(30, RocketDevtest::snapshot);

        // monta com a mão vazia: clique direito no cockpit
        step(20, mc -> mc.player.getInventory().setSelectedSlot(8));
        step(10, mc -> useOnBlock(mc, cockpit, Direction.EAST));
        step(25, mc -> RocketDevtest.afterAssemble(mc, "montado"));
        step(5, mc -> RocketDevtest.clientView(mc, "montado"));
        shot(30, "rk_c_assembled_same_spot");
        rocketView("rk_d_assembled_south", bx, by + 2.5, bz + 8.0, 180f, 6f);
        rocketView("rk_d_assembled_east", bx + 9.0, by + 2.5, bz, 90f, 6f);
        rocketView("rk_d_assembled_north", bx, by + 2.5, bz - 8.0, 0f, 6f);
        rocketView("rk_d_assembled_west", bx - 9.0, by + 2.5, bz, -90f, 6f);
        rocketView("rk_d_assembled_oblique_high", bx + 7.0, by + 11.0, bz + 7.0, 135f, 33f);
        rocketView("rk_d_assembled_close_up", bx + 3.5, by + 3.5, bz + 3.5, 135f, 12f);

        // senta o piloto (clique direito de verdade na entidade) e olha em terceira e primeira pessoa
        cmd(10, "tp @s " + (bx + 2.7) + " " + (by + 0.88) + " " + bz);
        fly(5);
        look(10, 90f, 0f);
        step(10, RocketDevtest::clickRocket);
        step(20, mc -> RocketDevtest.seatCheck(mc, "sentado"));
        step(5, mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
        shot(20, "rk_e_seated_third_person");
        step(5, mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        shot(20, "rk_e_seated_first_person");
        step(0, mc -> RocketDevtest.clientView(mc, "sentado"));
        // move a entidade 10 blocos para cima com o piloto: o retrato fora do chão, o piloto vai junto
        cmd(10, "spacehorizons rocket nudge 0 10 0");
        step(25, mc -> RocketDevtest.seatCheck(mc, "sentado 10 blocos acima"));
        step(5, mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
        shot(20, "rk_f_nudged_up_seated_third_person");
        step(5, mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        // desembarca (o mesmo caminho do Shift) e olha de fora para o foguete no ar
        step(5, RocketDevtest::dismount);
        rocketView("rk_g_floating_east", bx + 12.0, by + 8.0, bz, 90f, -17f);
        rocketView("rk_g_floating_south", bx, by + 8.0, bz + 12.0, 180f, -17f);
        step(0, mc -> RocketDevtest.clientView(mc, "no ar"));
        // devolve ao lugar
        cmd(10, "spacehorizons rocket nudge 0 -10 0");
        step(25, mc -> RocketDevtest.afterAssemble(mc, "devolvido"));
        // desmonta: os blocos voltam iguais
        cmd(10, "spacehorizons rocket disassemble");
        step(25, mc -> RocketDevtest.afterLand(mc, "pousou (yaw 0)", 0));
        step(5, mc -> RocketDevtest.clientView(mc, "depois de pousar"));
        rocketView("rk_h_landed_east", bx + 9.0, by + 2.5, bz, 90f, 6f);
        rocketView("rk_h_landed_oblique", bx + 7.0, by + 11.0, bz + 7.0, 135f, 33f);

        // recusa: pedra (terreno) encostada no casco
        cmd(10, RocketDevtest.terrainBlockCommand(true));
        cmd(10, "tp @s " + (bx + 2.7) + " " + (by + 0.88) + " " + (bz + 1.0));
        fly(5);
        look(10, 90f, 0f);
        step(10, mc -> useOnBlock(mc, cockpit, Direction.EAST));
        step(25, mc -> RocketDevtest.afterAssemble(mc, "depois do clique com terreno encostado"));
        step(5, mc -> RocketDevtest.assembleExpectingRefusal(mc, "terreno encostado"));
        shot(20, "rk_i_terrain_refusal");
        cmd(10, RocketDevtest.terrainBlockCommand(false));

        // monta de novo, grava e recarrega a entidade, gira 90 graus e pousa: os estados giram com ela
        step(10, mc -> useOnBlock(mc, cockpit, Direction.EAST));
        step(25, mc -> RocketDevtest.afterAssemble(mc, "remontado"));
        step(5, RocketDevtest::saveLoadRoundTrip);
        cmd(5, "spacehorizons rocket yaw 90");
        cmd(10, "spacehorizons rocket info");
        rocketView("rk_j_yaw90_east", bx + 9.0, by + 2.5, bz, 90f, 6f);
        rocketView("rk_j_yaw90_south", bx, by + 2.5, bz + 9.0, 180f, 6f);
        cmd(10, "spacehorizons rocket disassemble");
        step(25, mc -> RocketDevtest.afterLand(mc, "pousou com yaw 90", 1));
        rocketView("rk_k_landed_rotated_east", bx + 9.0, by + 2.5, bz, 90f, 6f);
        rocketView("rk_k_landed_rotated_south", bx, by + 2.5, bz + 9.0, 180f, 6f);
        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /**
     * Cliente do foguete (fase B2): monta o foguete como no cenário {@code rocket}, senta o piloto e confere as teclas (Shift
     * sobe o acelerador sem desmontar, Z/X/T/P/G mandam flags de borda, W/D/E dão giro), o pacote que o cliente mandou e o que o
     * SERVIDOR recebeu; fixa telemetria falsa para o painel ter números e tira prints do painel em primeira e terceira pessoa;
     * gira a atitude por comando (arfagem, rolagem, guinada) e confere a câmera contra a direção esperada; testa o olhar livre e a
     * tecla de câmera; liga o motor (dado sincronizado só no cliente) para ver chama e fumaça, de dentro e de fora.
     */
    private void cockpitScenario() {
        BlockPos tank = RocketDevtest.at(new BlockPos(0, 2, 0));
        BlockPos cockpit = RocketDevtest.at(new BlockPos(0, 4, 0));
        double bx = RocketDevtest.BASE.getX() + 0.5;
        double by = RocketDevtest.BASE.getY();
        double bz = RocketDevtest.BASE.getZ() + 0.5;
        cmd(20, "weather clear 1000000");
        cmd(10, "gamemode creative");
        fly(10);
        cmd(10, "tp @s " + (bx + 2.7) + " " + (by + 0.88) + " " + bz);
        fly(10);
        cmd(10, "clear @s");
        cmd(10, RocketDevtest.clearCommand());
        for (String command : RocketDevtest.buildCommands()) {
            cmd(4, command);
        }
        look(20, 90f, 0f);
        cmd(5, "give @s spacehorizons:fuel_canister[spacehorizons:fuel_mb=1000]");
        cmd(5, "give @s spacehorizons:fuel_canister[spacehorizons:fuel_mb=1000]");
        step(10, mc -> {
            mc.player.getInventory().setSelectedSlot(0);
            useOnBlock(mc, tank, Direction.EAST);
        });
        step(10, mc -> {
            mc.player.getInventory().setSelectedSlot(1);
            useOnBlock(mc, tank, Direction.EAST);
        });
        step(20, mc -> mc.player.getInventory().setSelectedSlot(8));
        step(10, mc -> useOnBlock(mc, cockpit, Direction.EAST));
        step(25, mc -> RocketDevtest.afterAssemble(mc, "montado"));
        // senta (clique direito de verdade) e confere o embarque: primeira pessoa, olhar livre zerado, ajuda no chat
        cmd(10, "tp @s " + (bx + 2.7) + " " + (by + 0.88) + " " + bz);
        fly(5);
        look(10, 90f, 0f);
        step(10, RocketDevtest::clickRocket);
        step(15, mc -> CockpitDevtest.boarded(mc, "sentou"));
        shot(10, "cp_a_boarding_chat_and_hint");
        step(5, mc -> CockpitDevtest.pinOrbitTelemetry());
        step(0, mc -> CockpitDevtest.camera(mc, "pousado, em pé, primeira pessoa"));
        shot(10, "cp_b_first_person_landed_panel");

        // Shift apertado por 30 ticks: o acelerador sobe 0,02 por tick (0,6) e o jogador NÃO desmonta
        step(5, mc -> mc.options.keyShift.setDown(true));
        step(31, mc -> CockpitDevtest.sent(mc, "Shift apertado por 30 ticks (esperado acelerador ~0,60 e ainda sentado)"));
        step(0, mc -> CockpitDevtest.serverReceived(mc, "Shift apertado"));
        step(0, mc -> mc.options.keyShift.setDown(false));
        // Ctrl desce
        step(5, mc -> mc.options.keySprint.setDown(true));
        step(11, mc -> CockpitDevtest.sent(mc, "Ctrl apertado por 10 ticks (esperado ~0,40)"));
        step(0, mc -> mc.options.keySprint.setDown(false));
        // Z cheio, T SAS, P paraquedas, X corta: flags de borda
        step(5, mc -> CockpitDevtest.click(RocketKeys.FULL_THROTTLE));
        step(3, mc -> CockpitDevtest.sent(mc, "Z (esperado acelerador 1,00 e flag 8)"));
        // T e E pelo caminho real do teclado (o código da tecla): o vanilla também recebe o clique (chat e inventário), e o
        // cliente tem que descartá-lo; a tela continua fechada e o foguete recebe o comando
        step(2, mc -> CockpitDevtest.clickRawKey(com.mojang.blaze3d.platform.InputConstants.KEY_T));
        step(3, mc -> CockpitDevtest.sent(mc, "T pelo teclado (esperado flag 1 e NENHUMA tela de chat aberta)"));
        step(2, mc -> CockpitDevtest.clickRawKey(com.mojang.blaze3d.platform.InputConstants.KEY_E));
        step(3, mc -> CockpitDevtest.sent(mc, "E pelo teclado (esperado NENHUM inventário aberto)"));
        step(2, mc -> CockpitDevtest.clickRawKey(com.mojang.blaze3d.platform.InputConstants.KEY_Q));
        step(3, mc -> CockpitDevtest.sent(mc, "Q pelo teclado (esperado nada solto: só o item da mão, vazio, e nenhuma tela)"));
        step(2, mc -> CockpitDevtest.click(RocketKeys.PARACHUTE));
        step(3, mc -> CockpitDevtest.sent(mc, "P (esperado flag 2)"));
        step(2, mc -> CockpitDevtest.click(RocketKeys.CUT_THROTTLE));
        step(3, mc -> CockpitDevtest.sent(mc, "X (esperado acelerador 0,00 e flag 16)"));
        // W + D + E
        step(2, mc -> {
            mc.options.keyUp.setDown(true);
            mc.options.keyRight.setDown(true);
            RocketKeys.ROLL_RIGHT.setDown(true);
        });
        step(8, mc -> CockpitDevtest.sent(mc, "W + D + E (esperado pitch=1 yaw=1 roll=1)"));
        step(0, mc -> CockpitDevtest.serverReceived(mc, "W + D + E"));
        step(0, mc -> CockpitDevtest.releaseAll(mc));
        // S + A + Q
        step(2, mc -> {
            mc.options.keyDown.setDown(true);
            mc.options.keyLeft.setDown(true);
            RocketKeys.ROLL_LEFT.setDown(true);
        });
        step(8, mc -> CockpitDevtest.sent(mc, "S + A + Q (esperado pitch=-1 yaw=-1 roll=-1)"));
        step(0, mc -> CockpitDevtest.releaseAll(mc));

        // acelerador a 80% (Shift por 40 ticks) para o print do painel; telemetria falsa de órbita já fixada
        step(5, mc -> mc.options.keyShift.setDown(true));
        step(41, mc -> mc.options.keyShift.setDown(false));
        step(5, mc -> CockpitDevtest.sent(mc, "acelerador a ~80% para o print"));
        step(0, mc -> CockpitDevtest.setClientData(mc, "THROTTLE", 0.8F));
        step(0, mc -> CockpitDevtest.setClientData(mc, "FLIGHT_MODE", (byte) 1));
        step(0, mc -> CockpitDevtest.setClientData(mc, "SAS_MODE", (byte) 2));
        shot(15, "cp_c_first_person_panel_orbit");

        // terceira pessoa (a tecla V de verdade)
        step(5, mc -> CockpitDevtest.click(RocketKeys.CAMERA));
        step(10, mc -> CockpitDevtest.camera(mc, "terceira pessoa (V)"));
        shot(10, "cp_d_third_person_panel_orbit");

        // atitude por comando: arfagem 30, e a câmera acompanha (terceira e primeira pessoa)
        cmd(10, "spacehorizons rocket pitch 30");
        step(10, mc -> CockpitDevtest.camera(mc, "arfagem 30, terceira pessoa"));
        shot(5, "cp_e_third_person_pitch30");
        step(5, mc -> CockpitDevtest.click(RocketKeys.CAMERA));
        step(10, mc -> CockpitDevtest.camera(mc, "arfagem 30, primeira pessoa"));
        shot(5, "cp_f_first_person_pitch30");
        cmd(10, "spacehorizons rocket roll 45");
        step(10, mc -> CockpitDevtest.camera(mc, "rolagem 45, primeira pessoa"));
        shot(5, "cp_g_first_person_roll45");
        cmd(10, "spacehorizons rocket pitch 90");
        step(10, mc -> CockpitDevtest.camera(mc, "arfagem 90 (deitado), primeira pessoa"));
        shot(5, "cp_h_first_person_pitch90");
        cmd(10, "spacehorizons rocket yaw 90");
        step(10, mc -> CockpitDevtest.camera(mc, "guinada 90 (de pé), primeira pessoa"));

        // olhar livre com o mouse (yaw/pitch do jogador) por cima da atitude
        step(5, mc -> {
            mc.player.setYRot(60f);
            mc.player.setXRot(-20f);
        });
        step(10, mc -> CockpitDevtest.camera(mc, "olhar livre yaw 60 pitch -20"));
        shot(5, "cp_i_first_person_freelook");
        step(5, mc -> {
            mc.player.setYRot(0f);
            mc.player.setXRot(0f);
        });

        // reentrada: telemetria falsa de descida com calor e paraquedas, e o calor sincronizado só no cliente
        cmd(10, "spacehorizons rocket pitch 0");
        step(5, mc -> CockpitDevtest.pinReentryTelemetry());
        step(0, mc -> CockpitDevtest.setClientData(mc, "HEAT", 1.4F));
        step(0, mc -> CockpitDevtest.setClientData(mc, "THROTTLE", 0.0F));
        step(0, mc -> CockpitDevtest.setClientData(mc, "SAS_MODE", (byte) 3));
        step(15, mc -> CockpitDevtest.effects(mc, "reentrada (calor 1,4)"));
        shot(5, "cp_j_first_person_reentry_panel");

        // G em voo: o cliente recusa e avisa; pousado: manda a flag de desmontar (o servidor só age com a física de voo, fase B1)
        step(5, mc -> CockpitDevtest.setClientData(mc, "HEAT", 0.0F));
        step(0, mc -> CockpitDevtest.setClientData(mc, "THROTTLE", 1.0F));
        step(0, mc -> CockpitDevtest.setClientData(mc, "FLIGHT_MODE", (byte) 1));
        step(5, mc -> CockpitDevtest.click(RocketKeys.DISASSEMBLE));
        step(3, mc -> CockpitDevtest.sent(mc, "G em voo: o cliente recusa e avisa (esperado nenhuma flag nova de desmontar)"));
        step(0, mc -> CockpitDevtest.setClientData(mc, "FLIGHT_MODE", (byte) 0));
        step(5, mc -> CockpitDevtest.click(RocketKeys.DISASSEMBLE));
        step(3, mc -> CockpitDevtest.sent(mc, "G pousado (esperado flag 4 de desmontar)"));
        step(0, mc -> CockpitDevtest.serverReceived(mc, "G pousado"));
        // motor ligado e chama, vista de fora (desembarca pelo caminho vanilla)
        step(5, RocketDevtest::dismount);
        rocketView("cp_k_exhaust_outside_east", bx + 11.0, by + 1.5, bz, 90f, 5f);
        rocketView("cp_l_exhaust_outside_oblique", bx + 7.0, by + 7.0, bz + 7.0, 135f, 25f);
        step(0, mc -> CockpitDevtest.effects(mc, "motor ligado, visto de fora"));
        step(10, mc -> CockpitDevtest.setClientData(mc, "THROTTLE", 0.0F));
        step(0, mc -> CockpitDevtest.unpinTelemetry());
        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /** Espera um marco do piloto de teste do voo (servidor); o tempo esgotado só é logado (o roteiro segue). */
    private Ticker waitFor(String milestone, int maxTicks) {
        int[] waited = {0};
        return mc -> {
            if (FlightDevtest.reached(milestone)) {
                return true;
            }
            if (mc.gui.screen() != null && waited[0] % 20 == 0) {
                SpaceHorizons.LOGGER.warn(TAG + "tela aberta durante o voo (esperando '{}'): {}", milestone, mc.gui.screen().getClass().getSimpleName());
            }
            if (++waited[0] > maxTicks) {
                SpaceHorizons.LOGGER.error(TAG + "tempo esgotado esperando o marco '{}'", milestone);
                return true;
            }
            return false;
        };
    }

    /**
     * O piloto real sobe o acelerador com Shift por {@code up} ticks, espera {@code hold} ticks e o desce com Ctrl por {@code down} ticks,
     * logando o que o cliente comanda e mostra (pacote enviado, painel, dado sincronizado) em cada ponto.
     */
    private void realThrottleWindow(int up, int hold, int down, String where, boolean releaseWithCut) {
        step(0, mc -> {
            CockpitDevtest.sent(mc, "janela " + where + ": antes de apertar Shift");
            mc.options.keyShift.setDown(true);
        });
        step(up, mc -> {
            mc.options.keyShift.setDown(false);
            CockpitDevtest.sent(mc, "janela " + where + ": Shift solto");
        });
        step(hold / 2, mc -> CockpitDevtest.sent(mc, "janela " + where + ": meio da espera"));
        step(hold / 2, mc -> {
            CockpitDevtest.sent(mc, "janela " + where + ": fim da espera");
            FlightDevtest.clientCheck(mc, "janela " + where + ": fim da espera");
            mc.options.keySprint.setDown(true);
        });
        step(down, mc -> {
            mc.options.keySprint.setDown(false);
            CockpitDevtest.sent(mc, "janela " + where + ": Ctrl solto");
        });
    }

    private void thirdPerson() {
        step(5, mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
    }

    private void firstPerson() {
        step(5, mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
    }

    /**
     * Voo no servidor (fase B1): um "piloto de teste" no servidor injeta comandos e o autoteste espera os marcos e tira os
     * prints. Voo A: salto suborbital pela dimensão de espaço com pouso suave; B: batida de propósito; C: órbita.
     */
    private void flightScenario(String parts) {
        cmd(20, "weather clear 1000000");
        cmd(10, "gamemode creative");
        // o relógio anda durante os voos: a Terra gira em relação ao referencial inercial (lançar para o leste é mais barato)
        cmd(5, "gamerule advance_time true");
        fly(10);
        if (parts.contains("l")) {
            // voo L: como o R, mas no espaço o cliente do piloto fica LENTO (250 ms a mais por tick): o batimento de 5 ticks do cliente passa do
            // tempo limite de 10 ticks do servidor
            step(10, mc -> FlightDevtest.build(mc, 'L'));
            tickerStep(10, waitFor("built_l", 200));
            firstPerson();
            step(0, FlightDevtest::go);
            tickerStep(0, waitFor("space_a", 4000));
            tickerStep(0, waitFor("real1_start", 400));
            step(0, mc -> FlightDevtest.setClientLag(250));
            step(0, mc -> mc.options.keyShift.setDown(true));
            step(18, mc -> mc.options.keyShift.setDown(false));
            step(40, mc -> CockpitDevtest.sent(mc, "cliente lento: fim da espera"));
            step(0, mc -> FlightDevtest.setClientLag(0));
            tickerStep(0, waitFor("real1_end", 900));
            step(10, mc -> {
                SpaceHorizons.LOGGER.info(TAG + "FIM");
                mc.stop();
            });
        }
        if (parts.contains("r")) {
            // voo R: o voo A com duas janelas em que SÓ o piloto real comanda o acelerador, pelas teclas (Shift sobe, Ctrl desce): uma no
            // espaço logo depois da travessia Terra para espaço e outra na Terra logo depois da reentrada
            step(10, mc -> FlightDevtest.build(mc, 'R'));
            tickerStep(10, waitFor("built_r", 200));
            firstPerson();
            step(0, FlightDevtest::go);
            tickerStep(0, waitFor("liftoff_a", 400));
            tickerStep(0, waitFor("space_a", 2000));
            tickerStep(0, waitFor("real1_start", 400));
            realThrottleWindow(18, 100, 26, "espaço", false);
            tickerStep(0, waitFor("real1_end", 400));
            tickerStep(0, waitFor("reentry_a", 5000));
            tickerStep(0, waitFor("real2_start", 400));
            realThrottleWindow(25, 20, 26, "Terra", true);
            tickerStep(0, waitFor("real2_end", 400));
            tickerStep(0, waitFor("end_a", 6000));
            step(20, mc -> FlightDevtest.clientCheck(mc, "R pousado"));
        }
        if (parts.contains("a")) {
        step(10, mc -> FlightDevtest.build(mc, 'A'));
        tickerStep(10, waitFor("built_a", 200));
        thirdPerson();
        step(5, mc -> FlightDevtest.clientCheck(mc, "A na plataforma"));
        shot(40, "fl_a1_pad_third_person");
        step(0, FlightDevtest::go);
        tickerStep(0, waitFor("liftoff_a", 400));
        shot(25, "fl_a2_liftoff_third_person");
        shot(25, "fl_a2b_ascending");
        tickerStep(0, waitFor("climb_a", 800));
        shot(2, "fl_a3_climb_1000");
        tickerStep(0, waitFor("space_a", 2000));
        step(30, mc -> FlightDevtest.clientCheck(mc, "A no espaço (30 ticks depois)"));
        shot(10, "fl_a4_space_third_person");
        firstPerson();
        shot(10, "fl_a5_space_first_person");
        thirdPerson();
        tickerStep(0, waitFor("apogee_a", 3000));
        shot(20, "fl_a5b_apogee");
        tickerStep(0, waitFor("reentry_a", 3000));
        step(20, mc -> FlightDevtest.clientCheck(mc, "A voltou à Terra"));
        shot(10, "fl_a6_reentry");
        tickerStep(0, waitFor("chute_a", 4000));
        shot(20, "fl_a7_parachute");
        tickerStep(0, waitFor("end_a", 4000));
        step(20, mc -> FlightDevtest.clientCheck(mc, "A pousado"));
        shot(20, "fl_a8_landed_third_person");
        }
        if (parts.contains("b")) {

        // voo B: bate de propósito (em sobrevivência, para o dano valer)
        cmd(20, "gamemode survival");
        cmd(10, "effect give @s minecraft:instant_health 1 10");
        step(20, mc -> FlightDevtest.build(mc, 'B'));
        tickerStep(10, waitFor("built_b", 200));
        thirdPerson();
        shot(30, "fl_b1_pad");
        step(0, FlightDevtest::go);
        tickerStep(0, waitFor("crashed_b", 1500));
        shot(15, "fl_b2_crashed");
        cmd(5, "gamemode creative");
        step(5, mc -> mc.player.getAbilities().flying = false);
        }

        // voo C: órbita
        if (parts.contains("c")) {
        step(20, mc -> FlightDevtest.build(mc, 'C'));
        tickerStep(10, waitFor("built_c", 200));
        thirdPerson();
        step(0, FlightDevtest::go);
        tickerStep(0, waitFor("space_c", 4000));
        tickerStep(0, waitFor("orbit_c", 6000));
        shot(30, "fl_c1_orbit_third_person");
        firstPerson();
        shot(5, "fl_c2_orbit_first_person");
        tickerStep(0, waitFor("reentry_c", 18_000));
        step(10, mc -> FlightDevtest.clientCheck(mc, "C voltou à Terra"));
        shot(5, "fl_c3_reentry");
        tickerStep(0, waitFor("end_c", 6000));
        shot(30, "fl_c4_landed");
        step(5, mc -> FlightDevtest.clientCheck(mc, "C em órbita"));
        }

        // voo D: como o C, mas sem o freio retrógrado no espaço (o jogador comum): a pré-carga do pouso é que carrega a descida
        if (parts.contains("d")) {
        step(20, mc -> FlightDevtest.build(mc, 'D'));
        tickerStep(10, waitFor("built_d", 200));
        thirdPerson();
        step(0, FlightDevtest::go);
        tickerStep(0, waitFor("space_d", 4000));
        tickerStep(0, waitFor("orbit_d", 6000));
        tickerStep(0, waitFor("descent_d", 18_000));
        shot(5, "fl_d1_space_descent_3000");
        tickerStep(0, waitFor("descent1200_d", 4000));
        shot(2, "fl_d2_space_descent_1200");
        tickerStep(0, waitFor("reentry_d", 4000));
        step(10, mc -> FlightDevtest.clientCheck(mc, "D voltou à Terra"));
        shot(5, "fl_d3_switch");
        tickerStep(0, waitFor("end_d", 6000));
        shot(30, "fl_d4_landed");
        step(5, mc -> FlightDevtest.clientCheck(mc, "D pousado"));
        }
        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /**
     * Viagem criativa Terra -> Lua -> Terra com o tempo correndo (o piloto automático voa no referencial preso à Terra e mira o ponto
     * de interceptação da Lua; ver FlightPlan). É o trecho (f)/(g) do roteiro completo, sozinho.
     */
    private void moonTripScenario() {
        cmd(20, "spacehorizons goto earth");
        cmd(300, "time set 6000");
        cmd(10, "tp @s 0 200 0");
        fly(20);
        look(40, EAST, -90f);
        cmd(5, "gamerule advance_time true");
        cmd(5, "spacehorizons fly moon");
        travel("tr_out", "moon", 360_000, 14_000);
        cmd(5, "gamerule advance_time false");
        step(300, mc -> logPos(mc, "chegada na Lua"));
        step(0, mc -> logArrival(mc, "Lua"));
        lookAtBody(300, SolarSystem.EARTH);
        shot(60, "g_moon_arrival_earth");
        look(20, EAST, 90f);
        shot(40, "g_moon_arrival_down");
        look(20, EAST, 5f);
        shot(40, "g_moon_arrival_horizon_e");
        // volta
        cmd(20, "tp @s ~ ~300 ~");
        fly(20);
        look(40, EAST, -90f);
        cmd(5, "gamerule advance_time true");
        cmd(5, "spacehorizons fly earth");
        travel("tr_back", "overworld", 85_000, 14_000);
        cmd(5, "gamerule advance_time false");
        cmd(200, "time set 6000");
        step(100, mc -> logPos(mc, "chegada na Terra"));
        step(0, mc -> logArrival(mc, "Terra"));
        look(300, EAST, 90f);
        shot(60, "h_earth_arrival_down");
        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
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
            if (SpaceDimension.is(mc.level)) {
                // no espaço aberto o sol local sai do SkyObserver (os eixos da dimensão são os do spawn da Terra)
                double clock = SpaceSun.clock(mc.level);
                double[] sun = new double[3];
                // SkyObserver.of: com o modo skytest ligado, é o observador com a atitude virtual
                SkyObserver observer = SkyObserver.of(mc.level, mc.player.getX(), mc.player.getY(), mc.player.getZ(), clock).orElseThrow();
                observer.sun(sun);
                BodySky.Apparent earth = observer.apparent(SolarSystem.EARTH);
                BodySky.Apparent moon = observer.apparent(SolarSystem.MOON);
                org.joml.Quaterniondc attitude = SkyAttitude.current();
                SpaceHorizons.LOGGER.info(TAG + "SOL {} (espaco): relogio={} sol=({}, {}, {}) altura={} graus terra=({}, {}, {}) lua=({}, {}, {}) "
                        + "atitude(R)={}", label, f(clock), f(sun[0]), f(sun[1]), f(sun[2]), f(Math.toDegrees(Math.asin(sun[1]))), f(earth.x()),
                        f(earth.y()), f(earth.z()), f(moon.x()), f(moon.y()), f(moon.z()),
                        attitude == null ? "desligada" : f(attitude.x()) + "," + f(attitude.y()) + "," + f(attitude.z()) + "," + f(attitude.w()));
            }
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
