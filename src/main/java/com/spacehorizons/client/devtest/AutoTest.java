package com.spacehorizons.client.devtest;

import com.spacehorizons.story.StoryLogic;
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

import org.lwjgl.sdl.SDLVideo;

import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
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
    private boolean windowPrepared;
    private int menuTicks;
    private int index;
    private Ticker active;
    private int countdown = 200;
    private int pushTicks;
    private double pushX;
    private double pushZ;

    private AutoTest() {
        if ("shipdeck2b".equals(ONLY)) {
            // o observador do autoteste de dois clientes: não cria mundo, entra pela LAN do anfitrião
            shipDeck2ObserverScenario();
            return;
        }
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
        if ("sunsky".equals(ONLY)) {
            sunSkyScenario();
            return;
        }
        if ("earthdetail".equals(ONLY)) {
            earthDetailScenario();
            return;
        }
        if ("moontrip".equals(ONLY)) {
            moonTripScenario();
            return;
        }
        if ("mission".equals(ONLY)) {
            missionScenario();
            return;
        }
        if ("shipyard".equals(ONLY)) {
            shipYardScenario();
            return;
        }
        if ("shipconvert".equals(ONLY)) {
            shipConvertScenario();
            return;
        }
        if ("shipflight".equals(ONLY)) {
            shipFlightScenario();
            return;
        }
        if ("shipascent".equals(ONLY)) {
            shipAscentScenario();
        }
        if ("shipdescent".equals(ONLY)) {
            shipDescentScenario();
            return;
        }
        if ("shipground".equals(ONLY)) {
            shipGroundScenario();
            return;
        }
        if ("shipdeck2".equals(ONLY)) {
            shipDeck2HostScenario();
            return;
        }
        if ("shipdeck".equals(ONLY)) {
            shipDeckScenario();
            return;
        }
        if ("shipdeckreal".equals(ONLY)) {
            shipDeckRealScenario();
            return;
        }
        if ("shipdeckuse".equals(ONLY)) {
            shipDeckUseScenario();
            return;
        }
        if ("shipseam".equals(ONLY)) {
            shipSeamScenario();
            return;
        }
        if ("shipanchor".equals(ONLY)) {
            shipAnchorScenario();
            return;
        }
        if ("shipanchor2".equals(ONLY)) {
            shipAnchor2Scenario();
            return;
        }
        if ("shipsee".equals(ONLY)) {
            shipSeeScenario();
            return;
        }
        if ("moonbase".equals(ONLY)) {
            moonbaseScenario();
            return;
        }
        if ("story".equals(ONLY)) {
            storyScenario();
            return;
        }
        if ("marstrip".equals(ONLY)) {
            marsTripScenario();
            return;
        }
        if ("marsorbit".equals(ONLY)) {
            marsOrbitScenario();
            return;
        }
        if ("ship".equals(ONLY)) {
            shipScenario();
            return;
        }
        if ("multiblock".equals(ONLY)) {
            multiblockScenario();
            return;
        }
        if ("blueprint".equals(ONLY)) {
            blueprintScenario();
            return;
        }
        if ("tier2".equals(ONLY)) {
            tier2Scenario();
            return;
        }
        if ("mars".equals(ONLY)) {
            marsScenario();
            return;
        }
        if (ONLY != null && ONLY.startsWith("flight")) {
            // flight = voos A, B, C e D; flightb, flightc, flightbc... = só os voos citados (depuração)
            flightScenario(ONLY.length() == 6 ? "abcd" : ONLY.substring(6));
            return;
        }
        if (ONLY != null && ONLY.matches("jump(c|e?\\d*)(v\\d+)?")) {
            // jump54 = cruzeiro a 54 blocos/tick no espaço, 3a pessoa (jump20, jump90...); jump54v10 = o mesmo com voo rápido desligado e distância 10;
            // jumpc = o voo C real (órbita, física e atitude de verdade) medido da decolagem à órbita estável
            jumpScenario(ONLY);
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
            if (mc.gui.screen() instanceof PauseScreen) {
                // em um jogador a pausa para também o servidor integrado e o marco nunca chegaria (aconteceu na descida da nave)
                SpaceHorizons.LOGGER.warn(TAG + "menu de pausa aberto durante o voo (esperando '{}'): fechado", milestone);
                mc.gui.setScreen(null);
            } else if (mc.gui.screen() != null && waited[0] % 20 == 0) {
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
     * Cenário {@code jump}: mede o "vai e volta" do foguete em voo rápido na dimensão de espaço (ver {@link JumpProbe}). O foguete de
     * teste é levado ao espaço a 8.000 de altitude e voa em linha reta, a velocidade constante, em 3a pessoa, por {@link #JUMP_TICKS}
     * ticks; o CSV sai em {@code run/jump_<rótulo>.csv}. O nome é {@code jump<velocidade em b/tick>[v<distância de visão>]}: com o
     * {@code v}, o voo rápido é desligado e a distância de visão e de simulação do cliente fixadas no valor dado.
     */
    private void jumpScenario(String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("jump(c|e?\\d*)(?:v(\\d+))?").matcher(name);
        if (!m.matches()) {
            return;
        }
        boolean orbit = "c".equals(m.group(1));
        boolean earth = m.group(1).startsWith("e");     // jumpe54: cruzeiro DENTRO da dimensão da Terra
        String digits = earth ? m.group(1).substring(1) : m.group(1);
        double speed = orbit || digits.isEmpty() ? 54.0 : Double.parseDouble(digits);
        int view = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        String label = (orbit ? "orbit" : (earth ? "e" : "s") + (int) speed) + (view > 0 ? "_v" + view : "_fast");
        cmd(5, "gamerule send_command_feedback false");
        cmd(20, "weather clear 1000000");
        cmd(10, "gamemode creative");
        cmd(5, "gamerule advance_time true");
        fly(10);
        step(10, mc -> FlightDevtest.build(mc, 'C'));
        tickerStep(10, waitFor("built_c", 200));
        step(10, mc -> {
            if (view > 0) {
                com.spacehorizons.client.rocket.FastFlightClient.setDisabledForTest(true);
                mc.options.renderDistance().set(view);
                mc.options.simulationDistance().set(view);
                mc.options.broadcastOptions();
            }
            SpaceHorizons.LOGGER.info(TAG + "JUMP {}: distância de visão {} simulação {} (voo rápido {})", label, mc.options.renderDistance().get(),
                    mc.options.simulationDistance().get(), view > 0 ? "desligado" : "ligado");
        });
        if (orbit) {
            // o voo C de verdade: da decolagem à órbita estável (a física, a atitude e o acelerador reais; passa por todas as velocidades)
            thirdPerson();
            step(20, mc -> JumpProbe.start(label));
            step(0, FlightDevtest::go);
            tickerStep(0, waitFor("orbit_stable_c", 12_000));
            step(5, mc -> {
                JumpProbe.stop(mc);
                FlightDevtest.clientCheck(mc, "JUMP fim (órbita)");
            });
            step(40, mc -> {
                SpaceHorizons.LOGGER.info(TAG + "FIM");
                mc.stop();
            });
            return;
        }
        step(5, mc -> {
            if (earth) {
                FlightDevtest.startCruiseEarth(mc, speed);
            } else {
                FlightDevtest.startCruise(mc, speed);
            }
        });
        tickerStep(0, waitFor("cruise_started", 400));
        step(60, mc -> FlightDevtest.clientCheck(mc, "JUMP depois da travessia"));
        thirdPerson();
        step(40, mc -> JumpProbe.start(label));
        shot(100, "jump_" + label + "_a");
        shot(300, "jump_" + label + "_b");
        step(JUMP_TICKS - 400, mc -> {
            JumpProbe.stop(mc);
            FlightDevtest.clientCheck(mc, "JUMP fim");
            FlightDevtest.stopCruise();
        });
        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /** Duração (ticks de cliente) da medição do cenário jump: 40 s a 20 ticks/s. */
    private static final int JUMP_TICKS = 800;

    /** Aponta a câmera para o sol do ponto atual do planeta (yaw e pitch do SunModel no ponto do jogador). */
    private static void lookAtSun(Minecraft mc, float pitchOffset) {
        java.util.Optional<com.spacehorizons.planet.PlanetDefinition> planet = Planets.forLevel(mc.level);
        float[] sun = new float[3];
        SpaceSun.localAt(mc.level, planet.orElse(Planets.EARTH), mc.player.getX(), mc.player.getZ(), sun);
        double elevation = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, sun[1]))));
        mc.player.setYRot((float) Math.toDegrees(Math.atan2(-sun[0], sun[2])));
        mc.player.setXRot((float) Math.max(-90.0, Math.min(90.0, -elevation + pitchOffset)));
        SpaceHorizons.LOGGER.info(TAG + "SUNSKY sol a elevacao={} graus", f(elevation));
    }

    /** O sol redondo no céu dos planetas do mod: Terra de dia (sol alto e baixo) e Lua, apontando a câmera para ele. */
    private void sunSkyScenario() {
        cmd(5, "gamerule send_command_feedback false");
        cmd(5, "weather clear 1000000");
        cmd(5, "gamerule advance_time false");
        cmd(20, "time set 3000");
        cmd(10, "tp @s 0 120 0");
        fly(20);
        step(10, mc -> lookAtSun(mc, 0f));
        shot(60, "ss_earth_day_sun");
        step(10, mc -> lookAtSun(mc, 25f));
        shot(40, "ss_earth_day_sun_low_in_frame");
        cmd(10, "time set 11500");
        step(10, mc -> lookAtSun(mc, 0f));
        shot(60, "ss_earth_sunset_sun");
        cmd(10, "time set 6000");
        step(10, mc -> lookAtSun(mc, 0f));
        shot(60, "ss_earth_noon_sun_up");
        cmd(10, "spacehorizons goto moon");
        cmd(200, "time set 3000");
        cmd(10, "tp @s ~ ~200 ~");
        fly(20);
        step(10, mc -> lookAtSun(mc, 0f));
        shot(100, "ss_moon_sun");
        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /** Ponto de costa com relevo escolhido pelo cenário earthdetail (x, z do mundo). */
    private double[] detailSpot = {0.0, 0.0};

    /**
     * Detalhe da Terra vista de cima (item de polimento da malha orbital): prints de uma costa com relevo a 1.000 e 3.000 de altitude
     * (dimensão do planeta, malha orbital) e a 10.000 (dimensão de espaço, esfera do corpo), olhando para baixo e de viés, mais o custo
     * (fps médio de 100 quadros) de cada vista.
     */
    private void earthDetailScenario() {
        cmd(5, "gamerule send_command_feedback false");
        cmd(5, "weather clear 1000000");
        cmd(5, "time set 6000");
        cmd(5, "gamerule advance_time false");
        step(5, mc -> mc.options.fov().set(70));
        tickerStep(0, waitForMap(24000));
        step(5, mc -> {
            detailSpot = pickCoastSpot();
            SpaceHorizons.LOGGER.info(TAG + "EARTHDETAIL ponto escolhido x={} z={}", f(detailSpot[0]), f(detailSpot[1]));
        });
        for (int altitude : new int[] {1000, 3000}) {
            step(20, mc -> mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.1f %d %.1f", detailSpot[0],
                    Planets.EARTH.seaLevel() + altitude, detailSpot[1])));
            fly(20);
            look(20, EAST, 90f);
            shot(500, "ed_" + altitude + "_down");
            fpsProbe("alt " + altitude + " baixo");
            look(10, EAST, 40f);
            shot(120, "ed_" + altitude + "_oblique");
            fpsProbe("alt " + altitude + " obliquo");
        }
        step(20, mc -> {
            double clock = SpaceSun.clock(mc.level);
            double[] space = new double[3];
            SpaceFrame.toSpace(SolarSystem.EARTH, clock, detailSpot[0], Planets.EARTH.seaLevel() + 10_000.0, detailSpot[1], space);
            double[] dim = new double[3];
            SpaceDimension.fromSpace(space[0], space[1], space[2], dim);
            mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "spacehorizons goto space %.1f %.1f %.1f", dim[0], dim[1], dim[2]));
        });
        step(200, mc -> lookInSpace(mc, SolarSystem.EARTH));
        step(5, mc -> logSpace(mc, "EARTHDETAIL a 10.000"));
        shot(500, "ed_10000_down");
        fpsProbe("alt 10000 baixo");
        step(10, mc -> mc.player.setXRot(mc.player.getXRot() - 40f));
        shot(120, "ed_10000_oblique");
        fpsProbe("alt 10000 obliquo");
        cmd(5, "gamerule send_command_feedback true");
        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /** Fps médio dos próximos 100 quadros, no log (custo de render da vista atual). */
    private void fpsProbe(String label) {
        tickerStep(0, new Ticker() {
            int n;
            long sum;

            @Override
            public boolean tick(Minecraft mc) {
                sum += mc.getFps();
                if (++n >= 100) {
                    SpaceHorizons.LOGGER.info(TAG + "EARTHDETAIL fps medio ({}): {}", label, f(sum / (double) n));
                    return true;
                }
                return false;
            }
        });
    }

    /** Procura no mapa da Terra uma costa com montanha por perto (onde a falta de detalhe mais aparece); cai no spawn se não achar. */
    private static double[] pickCoastSpot() {
        com.spacehorizons.planet.map.PlanetMap map = PlanetTerrains.map(Planets.EARTH);
        if (map == null) {
            return new double[] {0.0, 0.0};
        }
        double best = -1.0;
        double[] spot = {0.0, 0.0};
        for (int xi = -60; xi <= 60; xi++) {
            for (int zi = -25; zi <= 25; zi++) {
                double x = xi * 1_000.0;
                double z = zi * 1_000.0;
                boolean center = map.waterAt(x, z);
                boolean mixed = false;
                double hmax = -1e9;
                double hmin = 1e9;
                for (int d = 0; d < 8; d++) {
                    double ax = x + 3_000.0 * Math.cos(d * Math.PI / 4);
                    double az = z + 3_000.0 * Math.sin(d * Math.PI / 4);
                    mixed |= map.waterAt(ax, az) != center;
                    hmax = Math.max(hmax, map.heightAt(ax, az));
                    hmin = Math.min(hmin, map.heightAt(ax, az));
                }
                double score = mixed ? hmax - hmin : -1.0;
                if (score > best) {
                    best = score;
                    spot = new double[] {x, z};
                }
            }
        }
        return spot;
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

    /**
     * Relógio (ticks do Overworld) do lançamento da MISSÃO: com ele a Lua cruza o plano do equador perto da chegada, então a janela da TLI
     * (o primeiro depois da órbita baixa) já sai quase no plano e a correção de meio de curso é só a do lado de passagem. Em qualquer outro relógio
     * a janela existe, mas a Lua chega a até 100.000 blocos fora do plano do equador e a correção custa dezenas de blocos/tick (ver docs/MISSAO_LUA.md).
     */
    private static final int MISSION_CLOCK = 68_300;
    /** Ticks por segundo da missão: o servidor anda mais depressa que o tempo real para os ~55.000 ticks caberem em minutos. */
    private static final int MISSION_RATE = 300;

    /**
     * A MISSÃO COMPLETA com o foguete de verdade (ver {@link MissionDevtest}): Terra, órbita baixa, TLI, chegada e captura na Lua, pouso, decolagem,
     * volta, reentrada e pouso na Terra. O piloto roda no servidor a {@value #MISSION_RATE} ticks/s; em cada marco o ritmo volta a 20 ticks/s
     * por um momento, para o cliente desenhar o quadro do print.
     */
    private void missionScenario() {
        cmd(5, "gamerule send_command_feedback false");
        cmd(20, "weather clear 1000000");
        cmd(10, "gamemode creative");
        cmd(5, "time set " + MISSION_CLOCK);
        cmd(5, "gamerule advance_time true");
        fly(10);
        step(10, mc -> FlightDevtest.build(mc, 'M'));
        tickerStep(10, waitFor("built_m", 300));
        thirdPerson();
        step(5, mc -> FlightDevtest.clientCheck(mc, "M na plataforma"));
        shot(40, "ms_01_pad");
        step(0, MissionDevtest::go);
        missionShot("phase_ASCENT", "ms_02_liftoff", 400);
        missionShot("phase_COAST_TO_APOAPSIS", "ms_03_ascent_cutoff", 2_000);
        missionShot("phase_LEO_WAIT", "ms_04_leo", 3_000);
        missionShot("phase_TLI", "ms_05_tli_burn", 20_000);
        missionShot("phase_COAST_TO_MOON", "ms_06_tli_done", 1_000);
        missionShot("phase_CAPTURE_COAST", "ms_07_moon_soi", 60_000);
        missionShot("phase_CAPTURE", "ms_08_capture_burn", 20_000);
        missionShot("phase_LUNAR_ORBIT", "ms_09_lunar_orbit", 2_000);
        missionShot("phase_LUNAR_DESCENT", "ms_10_lunar_descent", 4_000);
        missionShot("phase_MOON_LANDED", "ms_11_moon_landed", 6_000);
        step(0, mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        shot(20, "ms_11b_moon_landed_first_person");
        step(0, mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
        missionShot("phase_LUNAR_ASCENT", "ms_12_moon_liftoff", 2_000);
        missionShot("phase_LUNAR_ORBIT_2", "ms_13_lunar_orbit_after_liftoff", 4_000);
        missionShot("phase_TEI", "ms_14_return_burn", 20_000);
        missionShot("phase_COAST_HOME", "ms_15_return_coast", 1_000);
        missionShot("phase_EARTH_ENTRY", "ms_16_earth_arrival", 60_000);
        missionShot("phase_EARTH_LANDING", "ms_17_reentry", 6_000);
        missionShot("phase_DONE", "ms_18_earth_landed", 6_000);
        tickerStep(0, waitFor("end", 6_000));
        step(20, mc -> FlightDevtest.clientCheck(mc, "M pousado na Terra"));
        cmd(2, "tick rate 20");
        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /**
     * Multiblocos de tanque e motor, painel de projeto e foguetes grandes (etapa 1 do docs/PLANO_TIERS.md): a formação no mundo (motor 2x2x2 e
     * 3x3x3 ao pôr e quebrar blocos), o galão numa estrutura de tanques, um projeto com defeitos (o painel aponta cada um no bloco certo) e,
     * para cada projeto (não decola, salto, órbita, Lua e volta, Lua pouso e volta, foguete grande e gigante): construir sobre a plataforma,
     * abrir o painel (números e destinos, prints), montar, voar uma subida curta com o modelo grande, pousar e conferir que as estruturas
     * voltam formadas e o combustível bate. O grande e o gigante vão ao cliente em PARTES (limite do pacote de spawn baixado de propósito).
     */
    private void multiblockScenario() {
        BlockPos pad = MultiblockDevtest.BASE;
        double bx = pad.getX() + 0.5;
        double by = pad.getY();
        double bz = pad.getZ() + 0.5;
        cmd(5, "gamerule send_command_feedback false");
        cmd(20, "weather clear 1000000");
        cmd(10, "gamemode creative");
        cmd(5, "time set 6000");
        cmd(5, "gamerule advance_time true");
        fly(10);
        cmd(10, "clear @s");

        // A. a formação dos motores no mundo (pôr e quebrar blocos) e o galão numa estrutura de tanques
        cmd(10, "tp @s " + (bx + 9.0) + " " + (by + 6.0) + " " + (bz + 9.0));
        fly(10);
        step(10, MultiblockDevtest::formingInWorld);
        look(30, -45f, 20f);
        shot(40, "mb_a1_forming_in_world");
        cmd(10, "tp @s " + (bx - 9.5) + " " + (by + 6.0) + " " + (bz + 1.5));
        fly(10);
        look(20, 90f, 12f);
        shot(20, "mb_a2_tank_column_empty");
        cmd(5, "give @s spacehorizons:fuel_canister[spacehorizons:fuel_mb=1000]");
        cmd(5, "give @s spacehorizons:fuel_canister[spacehorizons:fuel_mb=1000]");
        BlockPos column = MultiblockDevtest.TANK_COLUMN;
        step(10, mc -> {
            mc.player.getInventory().setSelectedSlot(0);
            useOnBlock(mc, column.offset(1, 7, 1), Direction.EAST);
        });
        step(10, mc -> {
            mc.player.getInventory().setSelectedSlot(1);
            useOnBlock(mc, column.offset(0, 4, 0), Direction.WEST);
        });
        step(20, mc -> MultiblockDevtest.structureFuel(mc, "dois galões (no bloco de cima e no de baixo)", 2000));
        step(5, mc -> MultiblockDevtest.structureText(mc, column.offset(0, 3, 1)));
        shot(10, "mb_a3_tank_column_filled");

        // B. o projeto com defeitos: o painel aponta cada um no bloco certo
        cmd(10, "tp @s " + (bx + 12.0) + " " + (by + 4.0) + " " + (bz + 1.0));
        fly(10);
        step(10, MultiblockDevtest::buildFlawed);
        tickerStep(5, waitFor("built_flawed", 200));
        look(10, 90f, 8f);
        step(5, mc -> MultiblockDevtest.pinPanel(true));
        shot(60, "mb_b1_flawed_panel_and_highlights");
        step(0, MultiblockDevtest::flawedPanel);
        step(20, mc -> MultiblockDevtest.pinPanel(false));

        // C. cada projeto
        for (String key : MultiblockDevtest.keys()) {
            boolean big = key.equals("big") || key.equals("giant");
            if (key.equals("big")) {
                step(5, mc -> MultiblockDevtest.forceParts(true));
            }
            double dist = key.equals("giant") ? 44.0 : key.equals("big") ? 34.0 : 16.0;
            double eyeY = key.equals("giant") ? by + 18.0 : key.equals("big") ? by + 12.0 : by + 8.0;
            step(10, mc -> MultiblockDevtest.build(mc, key));
            cmd(20, "tp @s " + (bx + dist) + " " + eyeY + " " + (bz + 0.5));
            fly(10);
            look(10, 90f, 0f);
            step(5, mc -> MultiblockDevtest.pinPanel(true));
            shot(60, "mb_" + key + "_a_panel");
            step(0, mc -> MultiblockDevtest.panel(mc, key));
            step(5, mc -> MultiblockDevtest.pinPanel(false));
            step(10, mc -> MultiblockDevtest.assemble(mc, key));
            tickerStep(10, waitFor("assembled_" + key, 200));
            step(40, mc -> MultiblockDevtest.clientView(mc, key, big));
            cmd(5, "tp @s " + (bx + dist) + " " + eyeY + " " + (bz + 0.5));
            look(10, 90f, 0f);
            shot(20, "mb_" + key + "_b_assembled");
            step(10, mc -> MultiblockDevtest.fly(mc, key));
            tickerStep(5, waitFor("burning_" + key, 600));
            thirdPerson();
            shot(5, "mb_" + key + "_c_flight");
            tickerStep(0, waitFor("landed_" + key, 6_000));
            if (!key.equals("none")) {
                step(20, mc -> MultiblockDevtest.afterLand(mc, key));
                tickerStep(5, waitFor("land_checked_" + key, 200));
                step(5, mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
                cmd(5, "tp @s " + (bx + dist) + " " + eyeY + " " + (bz + 0.5));
                look(10, 90f, 0f);
            }
            if (key.equals("giant")) {
                step(5, mc -> MultiblockDevtest.forceParts(false));
            }
        }
        step(20, mc -> {
            MultiblockDevtest.summary();
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /** Espera o marco {@code marker} da missão e tira o print {@code name} com o jogo no ritmo normal (depois volta ao ritmo rápido). */
    private void missionShot(String marker, String name, int maxTicks) {
        int[] waited = {0};
        tickerStep(0, mc -> {
            if (FlightDevtest.reached(marker) || FlightDevtest.reached("failed")) {
                return true;
            }
            if (++waited[0] > maxTicks * 20) {
                SpaceHorizons.LOGGER.error(TAG + "tempo esgotado esperando o marco '{}'", marker);
                return true;
            }
            return false;
        });
        cmd(1, "tick rate 20");
        step(50, mc -> FlightDevtest.clientCheck(mc, name));
        shot(5, name);
        cmd(1, "tick rate " + MISSION_RATE);
    }

    private static String f(double v) {
        return String.format("%.2f", v);
    }

    /**
     * Janela e opções iguais em todo autoteste: janela maximizada, escala da interface 2, sem pausar ao perder o
     * foco (a janela sem foco abria o menu e estragava os prints), sem limite de fps fora de foco e sem a tela de
     * acessibilidade da primeira execução. Roda no primeiro tick do cliente (thread da janela).
     */
    private void prepareWindow(Minecraft mc) {
        windowPrepared = true;
        SDLVideo.SDL_MaximizeWindow(mc.getWindow().handle());
        mc.options.guiScale().set(2);
        mc.options.pauseOnLostFocus = false;
        mc.options.onboardAccessibility = false;
        mc.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
        mc.options.save();
        SpaceHorizons.LOGGER.info(TAG + "janela maximizada, escala da interface 2");
    }

    public static void register() {
        AutoTest test = new AutoTest();
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> test.tick());
        SpaceHorizons.LOGGER.info(TAG + "roteiro com {} passos registrado", test.steps.size());
    }

    private void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!windowPrepared) {
            prepareWindow(mc);
        }
        if (mc.gui.screen() instanceof AccessibilityOnboardingScreen) {
            // pasta de jogo nova (sem options.txt): a tela de acessibilidade da primeira execução fica na frente do
            // menu e o roteiro esperaria para sempre; prepareClientTest já grava a opção, isto é só a rede de segurança
            mc.gui.setScreen(new TitleScreen());
            SpaceHorizons.LOGGER.info(TAG + "tela de acessibilidade da primeira execução fechada");
        }
        if ("shipdeck2b".equals(ONLY) && !freshRequested && mc.level == null && mc.gui.screen() instanceof TitleScreen) {
            // o observador espera o anfitrião abrir a LAN e entra
            if (ShipDeck2Devtest.hostReady(mc)) {
                freshRequested = true;
                ShipDeck2Devtest.join(mc);
            }
            return;
        }
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
        mc.createWorldOpenFlows().createFreshLevel(FRESH_WORLD, settings, new WorldOptions(0x5EAC0FFEEL, "story".equals(ONLY), false),
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
        java.util.Optional<SolarSystem.Body> body = Planets.forLevel(mc.level).map(SolarSystem::skyBodyOf);
        if (body.isEmpty()) {
            return null;
        }
        double[] sun = new double[3];
        SunModel.localDirection(body.get(), mc.player.getX(), mc.player.getZ(), SpaceSun.clock(mc.level), sun);
        return sun;
    }

    /** Loga relógio, sol local (eixos do Minecraft), altura e o relógio efetivo que o céu e a luz do chão usam. */
    private static void logSun(Minecraft mc, String label) {
        java.util.Optional<SolarSystem.Body> body = Planets.forLevel(mc.level).map(SolarSystem::skyBodyOf);
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

    // ===================================================================================================================
    // SHIPYARD (spike S2 das naves): -PshOnly=shipyard. Bloco separado; a leitura do servidor fica em ShipDevtest.
    // ===================================================================================================================

    /** Onde o jogador estava na Terra antes de entrar na primeira nave (para conferir o {@code ship leave}). */
    private Vec3 shipEarthPoint;
    /** Quadro de céu escolhido pela busca (sol ou lua pela janela). */
    private ShipDevtest.SkyShot shipSky;
    /** Chunks carregados no estaleiro com as duas naves seguradas (para comparar depois da soltura). */
    private int shipYardChunksHeld;
    /** Luz no ponto da lâmpada com ela apagada: {servidor bloco, servidor céu, cliente bloco, cliente céu}. */
    private int[] shipLightOff = new int[4];

    /**
     * Spike S2: cria duas naves de 64 em células distantes, entra na primeira (anda, abre a porta, acende e apaga a luz, abre o baú,
     * olha o sol, a Terra e a Lua pelas janelas), vai para a segunda, sai, espera os tickets caírem, entra de novo (as naves vieram do
     * disco), fecha e reabre o mundo com o jogador dentro e confere tudo outra vez. Mede tick, chunks e memória em cada situação.
     */
    private void shipYardScenario() {
        cmd(5, "gamerule send_command_feedback false");
        cmd(5, "gamerule show_advancement_messages false");
        cmd(5, "weather clear 1000000");
        cmd(5, "time set 6000");
        step(5, mc -> mc.options.fov().set(90));

        // A. a dimensão existe (world preset novo) e o servidor sem naves: linha de base
        step(100, mc -> {
            ShipDevtest.check("dimensao spacehorizons:shipyard existe no mundo novo", ShipDevtest.hasYard(mc), "world preset");
            ShipDevtest.check("sem naves no mundo novo", ShipDevtest.shipCount(mc) == 0, "naves=" + ShipDevtest.shipCount(mc));
            ShipDevtest.logStats(mc, "A linha de base (Terra, sem naves)", true);
        });
        step(100, mc -> ShipDevtest.logStats(mc, "A linha de base 2 (Terra, sem naves)", false));

        // B. cria duas naves de 64 em duas células
        cmd(10, "spacehorizons ship create 64");
        step(100, mc -> ShipDevtest.logStats(mc, "B depois da nave 1 criada (jogador na Terra, nave segurada)", false));
        cmd(10, "spacehorizons ship create 64");
        step(100, mc -> {
            ShipDevtest.check("duas naves registradas", ShipDevtest.shipCount(mc) == 2, "naves=" + ShipDevtest.shipCount(mc));
            var l1 = ShipDevtest.layout(mc, 1);
            var l2 = ShipDevtest.layout(mc, 2);
            int gap = Math.max(Math.abs(l2.box().minX() - l1.box().minX()), Math.abs(l2.box().minZ() - l1.box().minZ())) - 64;
            SpaceHorizons.LOGGER.info(TAG + "SHIP caixas: nave 1 em ({}, {}, {}), nave 2 em ({}, {}, {}); folga entre as caixas = {} blocos",
                    l1.box().minX(), l1.box().minY(), l1.box().minZ(), l2.box().minX(), l2.box().minY(), l2.box().minZ(), gap);
            ShipDevtest.check("naves em celulas distantes (folga >= 448 blocos de vazio)", gap >= 448, "folga=" + gap);
            ShipDevtest.check("as duas naves seguradas por tickets", ShipDevtest.heldCount(mc) == 2, "seguradas=" + ShipDevtest.heldCount(mc));
            ShipDevtest.check("a Terra intocada (jogador continua no overworld)", mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD,
                    String.valueOf(mc.level.dimension().identifier()));
            ShipDevtest.logStats(mc, "B duas naves criadas, jogador na Terra", true);
            shipEarthPoint = mc.player.position();
        });
        step(20, mc -> ShipDevtest.check("estado inicial do baú da nave 1",
                ShipDevtest.chestItems(mc, 1) == com.spacehorizons.ship.ShipBuilder.CHEST_TOTAL, "itens=" + ShipDevtest.chestItems(mc, 1)));

        // C. entra na nave 1
        cmd(10, "spacehorizons ship goto 1");
        tickerStep(0, waitInYard(1, 700));
        step(60, mc -> {
            var l = ShipDevtest.layout(mc, 1);
            mc.player.getAbilities().flying = false;
            mc.player.onUpdateAbilities();
            ShipDevtest.check("jogador no estaleiro, dentro da caixa da nave 1", l.box().contains(mc.player.getX(), mc.player.getY(), mc.player.getZ()),
                    "pos=(" + f(mc.player.getX()) + ", " + f(mc.player.getY()) + ", " + f(mc.player.getZ()) + ")");
            ShipDevtest.check("a nave ao redor do jogador e a 1", ShipDevtest.shipAroundPlayer(mc) == 1, "id=" + ShipDevtest.shipAroundPlayer(mc));
            ShipDevtest.check("gravidade normal (atributo 0,08)",
                    Math.abs(mc.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY) - 0.08) < 1.0E-6,
                    "gravity=" + mc.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY));
            logShipLight(mc, 1, "C entrada");
        });
        look(10, EAST, 5f);
        shot(40, "shipyard_01_ship1_hall_east");

        // D. gravidade: sobe 8 blocos e cai no piso (sem voar)
        cmd(10, "tp @s ~ ~8 ~");
        tickerStep(0, new Ticker() {
            int n;

            @Override
            public boolean tick(Minecraft mc) {
                n++;
                if (n < 5 || (!mc.player.onGround() && n < 120)) {
                    return false;
                }
                double floor = ShipDevtest.layout(mc, 1).spawn().getY();
                ShipDevtest.check("gravidade: caiu de 8 blocos e pousou no piso da nave", Math.abs(mc.player.getY() - floor) < 0.05 && !mc.player.getAbilities().flying,
                        "y=" + f(mc.player.getY()) + " piso=" + f(floor) + " ticks=" + n);
                return true;
            }
        });

        // E. anda ate a porta (movimento de verdade), abre, entra na cabine
        step(5, mc -> {
            var l = ShipDevtest.layout(mc, 1);
            SpaceHorizons.LOGGER.info(TAG + "SHIP anda de x={} para a porta em x={}", f(mc.player.getX()), l.doorLower().getX());
        });
        tickerStep(0, walkTo(1, l -> l.doorLower().getX() - 1.0, l -> l.spawn().getZ() + 0.5, 160, "ate a porta"));
        aimAt(5, 1, l -> Vec3.atCenterOf(l.doorLower()).add(0.0, 0.5, 0.0));
        shot(15, "shipyard_02_door_closed");
        useBlock(5, 1, l -> l.doorLower(), Direction.WEST, "porta");
        step(30, mc -> ShipDevtest.check("porta aberta com o botao direito", ShipDevtest.doorOpen(mc, 1), "porta aberta=" + ShipDevtest.doorOpen(mc, 1)));
        shot(5, "shipyard_03_door_open");
        tickerStep(0, walkTo(1, l -> l.doorLower().getX() + 3.5, l -> l.spawn().getZ() + 0.5, 160, "para dentro da cabine"));
        step(5, mc -> {
            var l = ShipDevtest.layout(mc, 1);
            ShipDevtest.check("jogador dentro da cabine (atravessou a porta)", mc.player.getX() > l.cabinMin().getX() + 1 && mc.player.getX() < l.cabinMax().getX(),
                    "x=" + f(mc.player.getX()) + " cabine de " + l.cabinMin().getX() + " a " + l.cabinMax().getX());
        });

        // F. luz: lampada de redstone apagada, acende pela alavanca, apaga, acende de novo e fica acesa
        aimAt(5, 1, l -> Vec3.atCenterOf(l.lamp()));
        step(40, mc -> {
            logShipLight(mc, 1, "F lampada apagada");
            shipLightOff = ShipDevtest.light(mc, 1, ShipDevtest.layout(mc, 1).lightProbe());
        });
        shot(5, "shipyard_04_cabin_lamp_off");
        aimAt(5, 1, l -> Vec3.atCenterOf(l.lever()));
        useBlock(5, 1, l -> l.lever(), Direction.SOUTH, "alavanca (liga)");
        step(40, mc -> {
            ShipDevtest.check("alavanca acende a lampada de redstone", ShipDevtest.lampLit(mc, 1) && ShipDevtest.leverPowered(mc, 1),
                    "lampada=" + ShipDevtest.lampLit(mc, 1) + " alavanca=" + ShipDevtest.leverPowered(mc, 1));
            logShipLight(mc, 1, "F lampada acesa");
            int[] on = ShipDevtest.light(mc, 1, ShipDevtest.layout(mc, 1).lightProbe());
            ShipDevtest.check("luz de bloco sobe com a lampada acesa (servidor e cliente)", on[0] >= 12 && on[0] > shipLightOff[0] + 5 && on[2] >= 12,
                    "apagada=" + shipLightOff[0] + "/" + shipLightOff[2] + " acesa=" + on[0] + "/" + on[2]);
        });
        aimAt(5, 1, l -> Vec3.atCenterOf(l.lamp()));
        shot(5, "shipyard_05_cabin_lamp_on");
        useBlock(5, 1, l -> l.lever(), Direction.SOUTH, "alavanca (desliga)");
        step(40, mc -> {
            ShipDevtest.check("alavanca apaga a lampada", !ShipDevtest.lampLit(mc, 1), "lampada=" + ShipDevtest.lampLit(mc, 1));
            logShipLight(mc, 1, "F lampada apagada de novo");
        });
        useBlock(5, 1, l -> l.lever(), Direction.SOUTH, "alavanca (liga de novo: fica acesa)");
        step(40, mc -> ShipDevtest.check("lampada acesa de novo (fica acesa para o teste de salvar)", ShipDevtest.lampLit(mc, 1),
                "lampada=" + ShipDevtest.lampLit(mc, 1)));

        // G. baú e fornalha
        tickerStep(0, walkTo(1, l -> l.chest().getX() - 2.0, l -> l.chest().getZ() + 1.0, 160, "ate o bau"));
        aimAt(5, 1, l -> Vec3.atCenterOf(l.chest()));
        useBlock(5, 1, l -> l.chest(), Direction.WEST, "bau");
        step(30, mc -> {
            boolean open = mc.player.containerMenu != mc.player.inventoryMenu;
            int total = 0;
            if (open) {
                for (int i = 0; i < 27; i++) {
                    total += mc.player.containerMenu.getSlot(i).getItem().getCount();
                }
            }
            ShipDevtest.check("bau aberto no cliente com os itens da nave", open && total == com.spacehorizons.ship.ShipBuilder.CHEST_TOTAL,
                    "tela=" + (mc.gui.screen() == null ? "nenhuma" : mc.gui.screen().getClass().getSimpleName()) + " itens=" + total);
        });
        shot(5, "shipyard_06_chest_open");
        step(10, mc -> mc.player.closeContainer());
        step(10, mc -> {
            ShipDevtest.takeDiamonds(mc, 1, 10);
            ShipDevtest.check("10 diamantes tirados do bau (restam 54)", ShipDevtest.chestDiamonds(mc, 1) == 54, "diamantes=" + ShipDevtest.chestDiamonds(mc, 1));
        });
        aimAt(10, 1, l -> Vec3.atCenterOf(l.furnace()));
        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "SHIP fornalha da nave 1: {}", ShipDevtest.furnace(mc, 1));
            ShipDevtest.check("fornalha acesa na nave", ShipDevtest.furnaceLit(mc, 1), ShipDevtest.furnace(mc, 1));
        });
        shot(5, "shipyard_07_furnace_lit");

        // H. janelas: a Terra pelo vidro do piso, o sol e a Lua pelas janelas das paredes e do teto
        step(5, mc -> {
            var l = ShipDevtest.layout(mc, 1);
            Vec3 stand = new Vec3(l.floorWindowCenter().getX() + 0.5, l.spawn().getY(), l.floorWindowCenter().getZ() + 0.5);
            mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f 0 90", stand.x, stand.y, stand.z));
        });
        look(40, 0f, 90f);
        step(5, mc -> {
            String sight = ShipDevtest.sightLine(mc.level, mc.player.getEyePosition(), new Vec3(0.0, -1.0, 0.0));
            ShipDevtest.check("vidro do piso: a linha de visao para baixo passa pela janela", sight.startsWith("janela"), sight);
            SkyObserver o = com.spacehorizons.client.ship.ShipSkyClient.observer(SpaceSun.clock(mc.level));
            var earth = o.apparent(SolarSystem.EARTH);
            SpaceHorizons.LOGGER.info(TAG + "SHIP Terra vista da nave: direcao=({}, {}, {}) raio angular={} graus distancia={}", f(earth.x()), f(earth.y()),
                    f(earth.z()), f(Math.toDegrees(earth.angularRadius())), f(earth.distance()));
        });
        shot(10, "shipyard_08_floor_window_earth");
        step(5, mc -> {
            shipSky = ShipDevtest.findSkyShot(mc, ShipDevtest.layout(mc, 1), null, 20.0, 0, 12_000);
            ShipDevtest.check("o sol aparece por uma janela em algum momento do dia", shipSky != null,
                    shipSky == null ? "nenhum relogio achado" : "relogio=" + shipSky.clock() + " altura=" + f(shipSky.elevation()) + " graus");
            if (shipSky != null) {
                mc.player.connection.sendCommand("time set " + shipSky.clock());
                mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.2f %.2f", shipSky.stand().x, shipSky.stand().y,
                        shipSky.stand().z, ShipDevtest.yawOf(shipSky.dir()), ShipDevtest.pitchOf(shipSky.dir())));
            }
        });
        step(60, mc -> {
            if (shipSky != null) {
                mc.player.setYRot(ShipDevtest.yawOf(shipSky.dir()));
                mc.player.setXRot(ShipDevtest.pitchOf(shipSky.dir()));
                SpaceHorizons.LOGGER.info(TAG + "SHIP olhando o sol: {}", ShipDevtest.sightLine(mc.level, mc.player.getEyePosition(), shipSky.dir()));
            }
        });
        shot(10, "shipyard_09_window_sun");
        step(5, mc -> {
            shipSky = ShipDevtest.findSkyShot(mc, ShipDevtest.layout(mc, 1), SolarSystem.MOON, 12.0, 0, 23_750);
            ShipDevtest.check("a Lua aparece por uma janela em algum momento do dia", shipSky != null,
                    shipSky == null ? "nenhum relogio achado" : "relogio=" + shipSky.clock() + " altura=" + f(shipSky.elevation()) + " graus");
            if (shipSky != null) {
                mc.player.connection.sendCommand("time set " + shipSky.clock());
                mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.2f %.2f", shipSky.stand().x, shipSky.stand().y,
                        shipSky.stand().z, ShipDevtest.yawOf(shipSky.dir()), ShipDevtest.pitchOf(shipSky.dir())));
            }
        });
        step(60, mc -> {
            if (shipSky != null) {
                mc.player.setYRot(ShipDevtest.yawOf(shipSky.dir()));
                mc.player.setXRot(ShipDevtest.pitchOf(shipSky.dir()));
                SpaceHorizons.LOGGER.info(TAG + "SHIP olhando a Lua: {}", ShipDevtest.sightLine(mc.level, mc.player.getEyePosition(), shipSky.dir()));
            }
        });
        shot(10, "shipyard_10_window_moon");
        step(5, mc -> mc.options.fov().set(30));
        shot(20, "shipyard_10b_window_moon_zoom");
        step(5, mc -> mc.options.fov().set(90));
        cmd(10, "time set 6000");

        // I. segunda nave: entra, confere que e independente (lampada apagada, baú intacto), olha a Terra pelo piso
        cmd(10, "spacehorizons ship goto 2");
        tickerStep(0, waitInYard(2, 700));
        step(40, mc -> {
            mc.player.getAbilities().flying = false;
            mc.player.onUpdateAbilities();
            var l = ShipDevtest.layout(mc, 2);
            ShipDevtest.check("jogador dentro da caixa da nave 2", l.box().contains(mc.player.getX(), mc.player.getY(), mc.player.getZ()),
                    "pos=(" + f(mc.player.getX()) + ", " + f(mc.player.getY()) + ", " + f(mc.player.getZ()) + ")");
            ShipDevtest.check("a nave 1 continua segurada com o jogador na 2", ShipDevtest.isHeld(mc, 1) && ShipDevtest.isHeld(mc, 2),
                    "seguradas=" + ShipDevtest.heldCount(mc));
            ShipDevtest.check("nave 2 independente: lampada apagada, baú com 115 itens", !ShipDevtest.lampLit(mc, 2) && ShipDevtest.chestItems(mc, 2) == 115,
                    "lampada=" + ShipDevtest.lampLit(mc, 2) + " itens=" + ShipDevtest.chestItems(mc, 2));
            ShipDevtest.check("nave 1 ainda com a lampada acesa e 105 itens", ShipDevtest.lampLit(mc, 1) && ShipDevtest.chestItems(mc, 1) == 105,
                    "lampada=" + ShipDevtest.lampLit(mc, 1) + " itens=" + ShipDevtest.chestItems(mc, 1));
            logShipLight(mc, 2, "I nave 2 na entrada");
        });
        look(10, EAST, 5f);
        shot(30, "shipyard_11_ship2_entry");
        step(5, mc -> {
            var l = ShipDevtest.layout(mc, 2);
            Vec3 stand = new Vec3(l.floorWindowCenter().getX() + 0.5, l.spawn().getY(), l.floorWindowCenter().getZ() + 0.5);
            mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f 0 90", stand.x, stand.y, stand.z));
        });
        look(40, 0f, 90f);
        shot(10, "shipyard_12_ship2_floor_window_earth");
        step(100, mc -> ShipDevtest.logStats(mc, "I duas naves seguradas, jogador na nave 2", true));
        step(100, mc -> ShipDevtest.logStats(mc, "I duas naves seguradas, jogador na nave 2 (segunda amostra)", false));

        // J. sai: de volta a Terra no mesmo ponto
        cmd(10, "spacehorizons ship leave");
        tickerStep(0, waitOverworld(700));
        step(60, mc -> {
            double d = shipEarthPoint == null ? 99.0 : mc.player.position().distanceTo(shipEarthPoint);
            ShipDevtest.check("ship leave: de volta a Terra no ponto de onde saiu", d < 3.0, "distancia=" + f(d));
        });
        look(10, EAST, 10f);
        shot(30, "shipyard_13_back_on_earth");
        step(100, mc -> {
            shipYardChunksHeld = ShipDevtest.yardChunks(mc);
            ShipDevtest.logStats(mc, "J jogador na Terra, as duas naves ainda seguradas (sem ninguem dentro)", true);
        });

        // K. sem ninguem dentro, os tickets caem depois do tempo de espera (reduzido para 100 ticks no teste)
        step(5, mc -> ShipDevtest.setReleaseTicks(mc, 100));
        step(300, mc -> {
            int held = ShipDevtest.heldCount(mc);
            int chunks = ShipDevtest.yardChunks(mc);
            ShipDevtest.check("tickets soltos depois do tempo sem ninguem", held == 0, "seguradas=" + held);
            ShipDevtest.check("chunks do estaleiro descarregados depois da soltura", chunks < shipYardChunksHeld,
                    "carregados antes=" + shipYardChunksHeld + " depois=" + chunks);
            ShipDevtest.logStats(mc, "K naves congeladas (tickets soltos)", true);
            ShipDevtest.setReleaseTicks(mc, com.spacehorizons.ship.ShipChunkTickets.DEFAULT_RELEASE_TICKS);
        });

        // L. de volta: as naves vem do disco (foram descarregadas): baú, lampada, porta e fornalha como ficaram
        cmd(10, "spacehorizons ship goto 1");
        tickerStep(0, waitInYard(1, 700));
        step(60, mc -> {
            ShipDevtest.check("nave 1 recarregada do disco: baú com 54 diamantes", ShipDevtest.chestDiamonds(mc, 1) == 54, "diamantes=" + ShipDevtest.chestDiamonds(mc, 1));
            ShipDevtest.check("nave 1 recarregada do disco: lampada acesa, alavanca ligada, porta aberta",
                    ShipDevtest.lampLit(mc, 1) && ShipDevtest.leverPowered(mc, 1) && ShipDevtest.doorOpen(mc, 1),
                    "lampada=" + ShipDevtest.lampLit(mc, 1) + " alavanca=" + ShipDevtest.leverPowered(mc, 1) + " porta=" + ShipDevtest.doorOpen(mc, 1));
            ShipDevtest.check("nave 1 recarregada do disco: fornalha acesa", ShipDevtest.furnaceLit(mc, 1), ShipDevtest.furnace(mc, 1));
            ShipDevtest.check("nave 1 segurada de novo ao entrar", ShipDevtest.isHeld(mc, 1), "seguradas=" + ShipDevtest.heldCount(mc));
            logShipLight(mc, 1, "L nave 1 vinda do disco");
        });
        step(5, mc -> {
            var l = ShipDevtest.layout(mc, 1);
            mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f -90 0", l.lightProbe().getX() + 0.5,
                    (double) l.spawn().getY(), l.spawn().getZ() + 0.5));
        });
        aimAt(40, 1, l -> Vec3.atCenterOf(l.lamp()));
        shot(10, "shipyard_14_ship1_from_disk_lamp_on");
        step(100, mc -> ShipDevtest.logStats(mc, "L nave 1 recarregada, jogador dentro", true));

        // M. fecha e reabre o mundo COM o jogador dentro da nave 1
        step(10, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "SHIP fechando o mundo com o jogador na nave {}", ShipDevtest.shipAroundPlayer(mc));
            ShipDevtest.startReload(mc, FRESH_WORLD);
        });
        step(400, mc -> {
            ShipDevtest.check("RELOAD: o mundo reabriu com o jogador no estaleiro", com.spacehorizons.ship.ShipYard.is(mc.level),
                    String.valueOf(mc.level.dimension().identifier()));
            ShipDevtest.check("RELOAD: o jogador continua dentro da nave 1", ShipDevtest.shipAroundPlayer(mc) == 1, "nave=" + ShipDevtest.shipAroundPlayer(mc));
            ShipDevtest.check("RELOAD: as duas naves continuam registradas", ShipDevtest.shipCount(mc) == 2, "naves=" + ShipDevtest.shipCount(mc));
            ShipDevtest.check("RELOAD: nave 1 segurada de novo (jogador dentro)", ShipDevtest.isHeld(mc, 1), "seguradas=" + ShipDevtest.heldCount(mc));
        });
        step(5, mc -> {
            ShipDevtest.check("RELOAD nave 1: baú com 54 diamantes e 105 itens", ShipDevtest.chestDiamonds(mc, 1) == 54 && ShipDevtest.chestItems(mc, 1) == 105,
                    "diamantes=" + ShipDevtest.chestDiamonds(mc, 1) + " itens=" + ShipDevtest.chestItems(mc, 1));
            ShipDevtest.check("RELOAD nave 1: lampada acesa, alavanca ligada, porta aberta",
                    ShipDevtest.lampLit(mc, 1) && ShipDevtest.leverPowered(mc, 1) && ShipDevtest.doorOpen(mc, 1),
                    "lampada=" + ShipDevtest.lampLit(mc, 1) + " alavanca=" + ShipDevtest.leverPowered(mc, 1) + " porta=" + ShipDevtest.doorOpen(mc, 1));
            ShipDevtest.check("RELOAD nave 1: fornalha acesa", ShipDevtest.furnaceLit(mc, 1), ShipDevtest.furnace(mc, 1));
            logShipLight(mc, 1, "M nave 1 depois de reabrir o mundo");
        });
        look(10, EAST, 5f);
        shot(40, "shipyard_15_after_reload_ship1");
        step(100, mc -> ShipDevtest.logStats(mc, "M depois de reabrir o mundo, jogador na nave 1", true));
        cmd(10, "spacehorizons ship goto 2");
        tickerStep(0, waitInYard(2, 700));
        step(60, mc -> {
            ShipDevtest.check("RELOAD nave 2: baú com 115 itens, lampada apagada, fornalha acesa",
                    ShipDevtest.chestItems(mc, 2) == 115 && !ShipDevtest.lampLit(mc, 2) && ShipDevtest.furnaceLit(mc, 2),
                    "itens=" + ShipDevtest.chestItems(mc, 2) + " lampada=" + ShipDevtest.lampLit(mc, 2) + " " + ShipDevtest.furnace(mc, 2));
            ShipDevtest.check("RELOAD: as duas naves seguradas de novo", ShipDevtest.isHeld(mc, 1) && ShipDevtest.isHeld(mc, 2), "seguradas=" + ShipDevtest.heldCount(mc));
        });
        look(10, EAST, 5f);
        shot(30, "shipyard_16_after_reload_ship2");

        // N. sai (o ponto de retorno também foi salvo) e termina
        cmd(10, "spacehorizons ship leave");
        tickerStep(0, waitOverworld(700));
        step(60, mc -> {
            double d = shipEarthPoint == null ? 99.0 : mc.player.position().distanceTo(shipEarthPoint);
            ShipDevtest.check("depois de reabrir o mundo, ship leave leva ao ponto salvo na Terra", d < 3.0, "distancia=" + f(d));
            ShipDevtest.logStats(mc, "N fim (Terra, naves seguradas ate caírem)", false);
        });
        cmd(5, "gamerule send_command_feedback true");
        cmd(5, "gamerule show_advancement_messages true");
        step(20, mc -> {
            ShipDevtest.summary();
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /** Espera o jogador chegar ao estaleiro, sem tela de carregamento e com o chunk dele carregado. */
    private Ticker waitInYard(int shipId, int maxTicks) {
        return new Ticker() {
            int n;

            @Override
            public boolean tick(Minecraft mc) {
                n++;
                boolean there = com.spacehorizons.ship.ShipYard.is(mc.level) && mc.gui.screen() == null
                        && mc.level.hasChunk(mc.player.blockPosition().getX() >> 4, mc.player.blockPosition().getZ() >> 4);
                if (there && n > 20) {
                    int around = ShipDevtest.shipAroundPlayer(mc);
                    SpaceHorizons.LOGGER.info(TAG + "SHIP chegou ao estaleiro em {} ticks (nave ao redor={}, esperado {})", n, around, shipId);
                    return true;
                }
                if (n > maxTicks) {
                    ShipDevtest.check("chegada ao estaleiro (nave " + shipId + ")", false, "nao chegou em " + maxTicks + " ticks; dim=" + mc.level.dimension().identifier()
                            + " tela=" + (mc.gui.screen() == null ? "nenhuma" : mc.gui.screen().getClass().getSimpleName()));
                    return true;
                }
                return false;
            }
        };
    }

    private Ticker waitOverworld(int maxTicks) {
        return new Ticker() {
            int n;

            @Override
            public boolean tick(Minecraft mc) {
                n++;
                boolean there = mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD && mc.gui.screen() == null
                        && mc.level.hasChunk(mc.player.blockPosition().getX() >> 4, mc.player.blockPosition().getZ() >> 4);
                if (there && n > 20) {
                    SpaceHorizons.LOGGER.info(TAG + "SHIP de volta ao overworld em {} ticks", n);
                    return true;
                }
                if (n > maxTicks) {
                    ShipDevtest.check("volta ao overworld", false, "nao voltou em " + maxTicks + " ticks; dim=" + mc.level.dimension().identifier());
                    return true;
                }
                return false;
            }
        };
    }

    /** Anda de verdade (velocidade horizontal a cada tick, mantendo a gravidade) até o ponto, ou o tempo acabar. */
    private Ticker walkTo(int shipId, java.util.function.Function<com.spacehorizons.ship.ShipBuilder.Layout, Double> x,
            java.util.function.Function<com.spacehorizons.ship.ShipBuilder.Layout, Double> z, int maxTicks, String label) {
        return new Ticker() {
            int n;
            double startX;
            double startZ;
            double tx;
            double tz;

            @Override
            public boolean tick(Minecraft mc) {
                LocalPlayer p = mc.player;
                if (n == 0) {
                    var l = ShipDevtest.layout(mc, shipId);
                    tx = x.apply(l);
                    tz = z.apply(l);
                    startX = p.getX();
                    startZ = p.getZ();
                }
                n++;
                double dx = tx - p.getX();
                double dz = tz - p.getZ();
                double dist = Math.hypot(dx, dz);
                if (dist < 0.3 || n > maxTicks) {
                    p.setDeltaMovement(0.0, p.getDeltaMovement().y, 0.0);
                    SpaceHorizons.LOGGER.info(TAG + "SHIP andou {} em {} ticks: de ({}, {}) para ({}, {}), faltam {}", label, n, f(startX), f(startZ), f(p.getX()),
                            f(p.getZ()), f(dist));
                    ShipDevtest.check("andou " + label, dist < 0.6 && Math.hypot(p.getX() - startX, p.getZ() - startZ) > 0.5, "faltam " + f(dist));
                    return true;
                }
                double speed = Math.min(0.2, dist);
                p.setDeltaMovement(dx / dist * speed, p.getDeltaMovement().y, dz / dist * speed);
                p.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
                return false;
            }
        };
    }

    /** Aponta a câmera para um ponto da nave. */
    private void aimAt(int wait, int shipId, java.util.function.Function<com.spacehorizons.ship.ShipBuilder.Layout, Vec3> target) {
        step(wait, mc -> {
            Vec3 t = target.apply(ShipDevtest.layout(mc, shipId));
            Vec3 d = t.subtract(mc.player.getEyePosition());
            mc.player.setYRot(ShipDevtest.yawOf(d));
            mc.player.setXRot(ShipDevtest.pitchOf(d));
        });
    }

    /** Clique direito de verdade no bloco, pela face dada. */
    private void useBlock(int wait, int shipId, java.util.function.Function<com.spacehorizons.ship.ShipBuilder.Layout, BlockPos> block, Direction face, String label) {
        step(wait, mc -> {
            BlockPos pos = block.apply(ShipDevtest.layout(mc, shipId));
            Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
            SpaceHorizons.LOGGER.info(TAG + "SHIP clique direito em {} ({}): {}", label, pos, result);
        });
    }

    /** Luz de bloco e de céu no ponto de medida da nave, no servidor e no cliente, e a verificação de que não há luz de céu dentro. */
    private static void logShipLight(Minecraft mc, int shipId, String label) {
        var l = ShipDevtest.layout(mc, shipId);
        int[] probe = ShipDevtest.light(mc, shipId, l.lightProbe());
        int[] spawn = ShipDevtest.light(mc, shipId, l.spawn());
        SpaceHorizons.LOGGER.info(TAG + "SHIP LUZ {}: ponto da lampada bloco(servidor)={} bloco(cliente)={} ceu(servidor)={} ceu(cliente)={} | centro do salao bloco={}/{} ceu={}/{}",
                label, probe[0], probe[2], probe[1], probe[3], spawn[0], spawn[2], spawn[1], spawn[3]);
        ShipDevtest.logIntegrity(mc, shipId, label);
        ShipDevtest.check("sem luz de ceu dentro da nave (" + label + ")", probe[1] == 0 && probe[3] == 0 && spawn[1] == 0 && spawn[3] == 0,
                "ceu servidor/cliente=" + probe[1] + "/" + probe[3] + " e " + spawn[1] + "/" + spawn[3]);
    }

    // ===================================================================================================================
    // SHIPCONVERT (spike S3 das naves): -PshOnly=shipconvert. Bloco separado; as conferências ficam em ShipConvertDevtest.
    // ===================================================================================================================

    /** Id da nave criada pela conversão em andamento e o baú escolhido para abrir ({x, y, z, total no servidor}). */
    private int convShipId;
    private int[] convChest;
    private double convRowZ;
    private Vec3 convWalkStart;
    private com.spacehorizons.ship.ShipYard.Box convBox;

    /**
     * Spike S3: gera por código foguetes grandes (~500, ~8.000 e ~4.000 blocos, com dezenas de baús cheios, tanques com carga, fornalha e
     * barril), converte cada um em nave no estaleiro e de volta, e confere tudo: o retrato chegou inteiro ao cliente, o romaneio não tem
     * perdas, os itens batem com a contagem exata da geração, o NBT, o combustível, e o pouso final devolve os mesmos blocos. O de 4.000
     * leva o piloto sentado (anda e abre um baú na nave, fecha e reabre o mundo com a nave no estaleiro e só então a devolve). O de 4.000
     * se repete com orçamentos diferentes (lotes pequenos e um tick só) para medir o hitch.
     */
    private void shipConvertScenario() {
        cmd(5, "gamerule send_command_feedback false");
        cmd(5, "gamerule show_advancement_messages false");
        cmd(5, "weather clear 1000000");
        cmd(5, "time set 6000");
        step(5, mc -> mc.options.fov().set(70));
        step(100, mc -> {
            ShipDevtest.check("CONV: dimensao spacehorizons:shipyard existe no mundo novo", ShipDevtest.hasYard(mc), "world preset");
            ShipDevtest.check("CONV: sem naves no mundo novo", ShipDevtest.shipCount(mc) == 0, "naves=" + ShipDevtest.shipCount(mc));
            ShipDevtest.logStats(mc, "CONV linha de base (Terra, sem naves)", true);
        });
        cmd(20, "tp @s 0 200 0");
        fly(20);
        step(60, mc -> logPos(mc, "CONV posição de partida"));

        convertCycle("c0500", 500, false, false, 1500, 20);
        packetGuardCycle();
        convertCycle("c8000", 8000, false, false, 1500, 20);
        convertCycle("c8000_um_tick", 8000, false, false, 1_000_000, 0);
        convertCycle("c4000", 4000, true, true, 1500, 20);
        convertCycle("c4000_lotes_pequenos", 4000, false, false, 400, 20);
        convertCycle("c4000_um_tick", 4000, false, false, 1_000_000, 0);
        reloadMidConversionCycle();

        cmd(5, "spacehorizons ship budget 1500 20");
        cmd(5, "gamerule send_command_feedback true");
        cmd(5, "gamerule show_advancement_messages true");
        step(20, mc -> {
            ShipDevtest.logStats(mc, "CONV fim (Terra)", true);
            ShipDevtest.summary();
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /**
     * Um ciclo completo: gera o foguete, converte em nave, confere, (com piloto: anda, abre o baú, e com {@code reload} fecha e reabre o
     * mundo), converte de volta, confere, pousa no chão e confere os blocos, os itens e o combustível outra vez.
     */
    private void convertCycle(String tag, int blocks, boolean pilot, boolean reload, int units, int millis) {
        String c = "CONV " + tag + ": ";
        cmd(5, "spacehorizons ship budget " + units + " " + millis);
        step(10, mc -> ShipConvertDevtest.spawn(mc, blocks));
        step(60, mc -> {
            int got = ShipConvertDevtest.clientBlocks(mc);
            int expected = ShipConvertDevtest.current.portrait().blockCount();
            ShipDevtest.check(c + "o retrato chegou ao cliente com todos os blocos (pacote de spawn de " + ShipConvertDevtest.packetBytes() + " bytes)", got == expected,
                    "cliente=" + got + " esperado=" + expected);
            ShipDevtest.check(c + "o pacote do retrato cabe no limite de 1 MiB", ShipConvertDevtest.packetBytes() < (1 << 20), ShipConvertDevtest.packetBytes() + " bytes");
            // o foguete inteiro à vista, de longe
            Vec3 center = ShipConvertDevtest.rocketCenter;
            Vec3 from = new Vec3(center.x - ShipConvertDevtest.currentSpec.sx() / 2.0 - 30.0, center.y, center.z);
            mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f -90 0", from.x, from.y, from.z));
        });
        shot(60, "shipconvert_" + tag + "_01_rocket_outside");
        if (pilot) {
            step(10, mc -> ShipConvertDevtest.seatPlayer(mc));
            step(10, mc -> ShipDevtest.check(c + "o piloto está sentado no foguete", ShipConvertDevtest.playerRidesRocket(mc), ShipConvertDevtest.playerState(mc)));
        }

        // ---- entidade -> blocos ----
        cmd(5, "spacehorizons ship fromrocket");
        tickerStep(0, waitConversion(c + "entidade -> blocos", 6000));
        step(40, mc -> {
            com.spacehorizons.ship.ConversionReport r = ShipConvertDevtest.lastReport(mc);
            convShipId = ShipConvertDevtest.firstShipId(mc);
            ShipConvertDevtest.logReport(tag + " entidade->blocos", r);
            ShipDevtest.check(c + "conversão entidade -> blocos terminou sem erro e sem perdas",
                    r != null && r.ok() && r.losses().isEmpty() && r.blocks() == ShipConvertDevtest.current.portrait().blockCount(),
                    r == null ? "sem relatório" : r.losses().toString() + " " + r.error());
            ShipDevtest.check(c + "nave registrada, pronta e com o snapshot do voo", convShipId > 0 && ShipConvertDevtest.shipPhase(mc, convShipId).equals("READY+snapshot"),
                    "id=" + convShipId + " fase=" + ShipConvertDevtest.shipPhase(mc, convShipId));
            ShipDevtest.check(c + "a entidade do foguete foi removida", !ShipConvertDevtest.rocketEntityExists(mc), "existe=" + ShipConvertDevtest.rocketEntityExists(mc));
            Object[] chk = ShipConvertDevtest.yardCheck(mc, convShipId);
            ShipDevtest.check(c + "no estaleiro: itens dos baús e do barril EXATOS (" + ShipConvertDevtest.current.expectedTotalItems() + " itens contados à parte)", "".equals(chk[0]),
                    "diferenças=[" + chk[0] + "]");
            ShipDevtest.check(c + "no estaleiro: todas as células iguais ao retrato (estado a estado)", ((Integer) chk[1]) == 0, "diferentes=" + chk[1]);
            ShipDevtest.check(c + "no estaleiro: combustível dos tanques = piso do combustível da entidade (" + expectedFuel() + " mB)",
                    ((Integer) chk[2]) == expectedFuel(), "tanques=" + chk[2]);
            if (pilot) {
                ShipDevtest.check(c + "o piloto foi para dentro da nave, no estaleiro", com.spacehorizons.ship.ShipYard.is(mc.level) || ShipConvertDevtest.playerState(mc).contains("shipyard"),
                        ShipConvertDevtest.playerState(mc));
            }
        });

        if (pilot) {
            tickerStep(0, waitInYard(1, 900));
            step(40, mc -> {
                mc.player.getAbilities().flying = false;
                mc.player.onUpdateAbilities();
                ShipDevtest.check(c + "o jogador está dentro da caixa da nave no cliente", com.spacehorizons.ship.ShipYard.is(mc.level)
                        && ShipConvertDevtest.playerState(mc).contains("shipyard"), ShipConvertDevtest.playerState(mc));
                SpaceHorizons.LOGGER.info(TAG + "SHIPCONV jogador na nave: {}", ShipConvertDevtest.playerState(mc));
            });
            look(10, 0f, 10f);
            shot(40, "shipconvert_" + tag + "_02_inside_cockpit_south");
            look(10, 90f, 10f);
            shot(20, "shipconvert_" + tag + "_03_inside_west");
            look(10, EAST, 0f);
            shot(20, "shipconvert_" + tag + "_04_inside_east");
            look(10, 180f, 60f);
            shot(20, "shipconvert_" + tag + "_05_inside_floor_tanks");
            // anda de verdade até a parede leste (a fileira z = centro + 1 está livre dos tanques) e abre um baú de lá
            step(5, mc -> {
                var box = ShipConvertDevtest.boxOf(mc, convShipId);
                Vec3 from = new Vec3(box.maxX() - 1, mc.player.getY(), box.minZ() + ShipConvertDevtest.currentSpec.sz() / 2.0 + 1.0);
                convChest = ShipConvertDevtest.nearestChest(mc, convShipId, from);
                SpaceHorizons.LOGGER.info(TAG + "SHIPCONV baú escolhido: {}", convChest == null ? "nenhum" : java.util.Arrays.toString(convChest));
                ShipDevtest.check(c + "há um baú cheio na parede leste da nave", convChest != null && convChest[3] > 0, "baú=" + (convChest == null ? "null" : java.util.Arrays.toString(convChest)));
            });
            step(1, mc -> {
                var box = ShipConvertDevtest.boxOf(mc, convShipId);
                convRowZ = box.minZ() + ShipConvertDevtest.currentSpec.sz() / 2 + 1.5;
                convWalkStart = mc.player.position();
            });
            tickerStep(0, walkToPoint(mc -> new Vec3(mc.player.getX(), mc.player.getY(), convRowZ), 60, "para a fileira livre dos tanques"));
            tickerStep(0, walkToPoint(mc -> new Vec3(convChest[0] - 3.0, mc.player.getY(), convRowZ), 400, "pela fileira ate a parede leste"));
            tickerStep(0, walkToPoint(mc -> new Vec3(convChest[0] - 1.5, mc.player.getY(), convChest[2] + 0.5), 200, "ate o baú"));
            step(5, mc -> {
                Vec3 t = Vec3.atCenterOf(new BlockPos(convChest[0], convChest[1], convChest[2]));
                Vec3 d = t.subtract(mc.player.getEyePosition());
                mc.player.setYRot(ShipDevtest.yawOf(d));
                mc.player.setXRot(ShipDevtest.pitchOf(d));
            });
            step(15, mc -> {
                BlockPos pos = new BlockPos(convChest[0], convChest[1], convChest[2]);
                Vec3 hit = Vec3.atCenterOf(pos).add(-0.5, 0.0, 0.0);
                var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.WEST, pos, false));
                SpaceHorizons.LOGGER.info(TAG + "SHIPCONV clique direito no baú {}: {}", pos, result);
            });
            step(30, mc -> {
                boolean open = mc.player.containerMenu != mc.player.inventoryMenu;
                int total = 0;
                if (open) {
                    for (int i = 0; i < 27; i++) {
                        total += mc.player.containerMenu.getSlot(i).getItem().getCount();
                    }
                }
                ShipDevtest.check(c + "baú aberto no cliente com os itens vindos do foguete (27 pilhas, soma igual à do servidor)", open && total == convChest[3] && total > 0,
                        "aberto=" + open + " cliente=" + total + " servidor=" + convChest[3]);
            });
            shot(5, "shipconvert_" + tag + "_06_chest_open_in_ship");
            step(10, mc -> mc.player.closeContainer());
            step(5, mc -> {
                double moved = convWalkStart == null ? 0.0 : Math.hypot(mc.player.getX() - convWalkStart.x, mc.player.getZ() - convWalkStart.z);
                ShipDevtest.check(c + "andou dentro da nave de verdade (mais de 5 blocos no estaleiro)", moved > 5.0, "andou " + f(moved) + " blocos");
            });
        }

        if (reload) {
            step(10, mc -> {
                SpaceHorizons.LOGGER.info(TAG + "SHIPCONV fechando o mundo com a nave {} no estaleiro", convShipId);
                ShipDevtest.startReload(mc, FRESH_WORLD);
            });
            step(400, mc -> {
                ShipDevtest.check(c + "RELOAD: o mundo reabriu com o jogador no estaleiro", com.spacehorizons.ship.ShipYard.is(mc.level), String.valueOf(mc.level.dimension().identifier()));
                ShipDevtest.check(c + "RELOAD: a nave continua registrada, pronta e com o snapshot do voo", ShipConvertDevtest.shipPhase(mc, convShipId).equals("READY+snapshot"),
                        "fase=" + ShipConvertDevtest.shipPhase(mc, convShipId));
                Object[] chk = ShipConvertDevtest.yardCheck(mc, convShipId);
                ShipDevtest.check(c + "RELOAD: itens exatos depois de salvar e reabrir", "".equals(chk[0]), "diferenças=[" + chk[0] + "]");
                ShipDevtest.check(c + "RELOAD: células iguais ao retrato", ((Integer) chk[1]) == 0, "diferentes=" + chk[1]);
                ShipDevtest.check(c + "RELOAD: combustível dos tanques igual", ((Integer) chk[2]) == expectedFuel(), "tanques=" + chk[2]);
            });
            look(10, EAST, 5f);
            shot(40, "shipconvert_" + tag + "_07_after_reload_in_ship");
        }

        // ---- blocos -> entidade ----
        step(5, mc -> {
            convBox = ShipConvertDevtest.boxOf(mc, convShipId);
            SpaceHorizons.LOGGER.info(TAG + "SHIPCONV devolvendo a nave {} ao foguete", convShipId);
            mc.player.connection.sendCommand("spacehorizons ship torocket " + convShipId);
        });
        tickerStep(0, waitConversion(c + "blocos -> entidade", 6000));
        if (pilot) {
            tickerStep(0, waitOverworld(900));
        }
        step(60, mc -> {
            com.spacehorizons.ship.ConversionReport r = ShipConvertDevtest.lastReport(mc);
            ShipConvertDevtest.logReport(tag + " blocos->entidade", r);
            ShipDevtest.check(c + "conversão blocos -> entidade terminou sem erro e sem perdas em relação à entrada",
                    r != null && r.ok() && r.losses().isEmpty() && r.blocks() == ShipConvertDevtest.current.portrait().blockCount(),
                    r == null ? "sem relatório" : r.losses().toString() + " " + r.error());
            ShipDevtest.check(c + "a entidade do foguete voltou", ShipConvertDevtest.rocketEntityExists(mc), String.valueOf(ShipConvertDevtest.rocketInfo(mc)));
            Vec3 at = ShipConvertDevtest.serverRocketPos(mc);
            ShipDevtest.check(c + "o foguete voltou ao lugar de onde saiu", at != null && at.distanceTo(ShipConvertDevtest.rocketCenter) < 0.01,
                    "pos=" + at + " original=" + ShipConvertDevtest.rocketCenter);
            double fuel = ShipConvertDevtest.serverRocketFuel(mc);
            ShipDevtest.check(c + "combustível da entidade = soma dos tanques (" + expectedFuel() + " mB)", Math.floor(fuel) == expectedFuel(), "combustivel=" + fuel);
            ShipDevtest.check(c + "a nave saiu do registro e a célula foi liberada", ShipDevtest.shipCount(mc) == 0 && ShipDevtest.heldCount(mc) == 0,
                    "naves=" + ShipDevtest.shipCount(mc) + " seguradas=" + ShipDevtest.heldCount(mc));
            ShipDevtest.check(c + "o retrato chegou de novo ao cliente com todos os blocos", ShipConvertDevtest.clientBlocks(mc) == ShipConvertDevtest.current.portrait().blockCount(),
                    "cliente=" + ShipConvertDevtest.clientBlocks(mc));
            if (pilot) {
                ShipDevtest.check(c + "o piloto voltou sentado no foguete, no overworld", ShipConvertDevtest.playerRidesRocket(mc), ShipConvertDevtest.playerState(mc));
            }
        });
        step(5, mc -> {
            // a caixa do estaleiro ficou vazia (a célula liberada)
            int left = ShipConvertDevtest.nonAirInBox(mc, convBox);
            ShipDevtest.check(c + "a caixa do estaleiro ficou vazia depois da devolução", left == 0, "blocos que sobraram=" + left);
        });
        if (pilot) {
            shot(20, "shipconvert_" + tag + "_08_back_as_entity_from_cockpit");
            step(10, mc -> ShipConvertDevtest.dismountPlayer(mc));
            step(20, mc -> {
                Vec3 center = ShipConvertDevtest.rocketCenter;
                Vec3 from = new Vec3(center.x - ShipConvertDevtest.currentSpec.sx() / 2.0 - 30.0, center.y, center.z);
                mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f -90 0", from.x, from.y, from.z));
                mc.player.getAbilities().flying = true;
                mc.player.onUpdateAbilities();
            });
        }
        shot(40, "shipconvert_" + tag + "_09_back_as_entity_outside");

        // ---- pouso no chão: os blocos de volta ----
        step(10, mc -> {
            String err = ShipConvertDevtest.land(mc);
            ShipDevtest.check(c + "o foguete desmontou no chão (RocketLanding)", "".equals(err), "erro=" + err);
            if ("".equals(err)) {
                Object[] chk = ShipConvertDevtest.landedCheck(mc);
                ShipDevtest.check(c + "no chão: itens EXATOS depois da ida e volta (" + ShipConvertDevtest.current.expectedTotalItems() + " contados à parte)", "".equals(chk[0]),
                        "diferenças=[" + chk[0] + "]");
                ShipDevtest.check(c + "no chão: todas as células iguais ao retrato original", ((Integer) chk[1]) == 0, "diferentes=" + chk[1]);
                ShipDevtest.check(c + "no chão: combustível dos tanques igual", ((Integer) chk[2]) == expectedFuel(), "tanques=" + chk[2]);
            }
        });
        shot(60, "shipconvert_" + tag + "_10_landed_blocks");
        step(10, mc -> ShipConvertDevtest.clearLanded(mc));
        step(20, mc -> ShipConvertDevtest.discardRocket(mc));
        cmd(10, "tp @s 0 200 0");
        fly(20);
    }

    private static int expectedFuel() {
        return com.spacehorizons.ship.ShipTestRocket.expectedFuelMb(ShipConvertDevtest.current);
    }

    /** Espera a conversão terminar (a fila do servidor vazia), com o limite de ticks; o relatório é lido depois. */
    private Ticker waitConversion(String label, int maxTicks) {
        return new Ticker() {
            int n;
            boolean started;

            @Override
            public boolean tick(Minecraft mc) {
                n++;
                boolean busy = ShipConvertDevtest.busy(mc);
                started |= busy;
                if ((started && !busy) || (n > 40 && !busy)) {
                    SpaceHorizons.LOGGER.info(TAG + "SHIPCONV {}: terminou em {} ticks de cliente", label, n);
                    return true;
                }
                if (n > maxTicks) {
                    ShipDevtest.check(label + ": terminou no tempo", false, "ainda ocupado depois de " + maxTicks + " ticks");
                    return true;
                }
                return false;
            }
        };
    }

    /** Anda de verdade até o ponto (calculado na hora), ou o tempo acabar. */
    private Ticker walkToPoint(java.util.function.Function<Minecraft, Vec3> target, int maxTicks, String label) {
        return new Ticker() {
            int n;
            Vec3 goal;
            Vec3 start;

            @Override
            public boolean tick(Minecraft mc) {
                LocalPlayer p = mc.player;
                if (n == 0) {
                    goal = target.apply(mc);
                    start = p.position();
                }
                n++;
                double dx = goal.x - p.getX();
                double dz = goal.z - p.getZ();
                double dist = Math.hypot(dx, dz);
                if (dist < 0.3 || n > maxTicks) {
                    p.setDeltaMovement(0.0, p.getDeltaMovement().y, 0.0);
                    SpaceHorizons.LOGGER.info(TAG + "SHIPCONV andou {} em {} ticks: de ({}, {}) para ({}, {}), faltam {}", label, n, f(start.x), f(start.z), f(p.getX()),
                            f(p.getZ()), f(dist));
                    ShipDevtest.check("CONV andou " + label, dist < 0.8, "faltam " + f(dist));
                    return true;
                }
                double speed = Math.min(0.2, dist);
                p.setDeltaMovement(dx / dist * speed, p.getDeltaMovement().y, dz / dist * speed);
                p.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
                return false;
            }
        };
    }

    /**
     * A guarda do pacote: com o teto do pacote de spawn baixado para 10.000 bytes, a nave de 500 blocos (24.500 bytes) NÃO volta a foguete: a
     * conversão recusa, a nave continua no estaleiro intacta e nenhuma entidade aparece. Com o teto de volta ao normal, volta e pousa.
     */
    private void packetGuardCycle() {
        String c = "CONV guarda_pacote: ";
        cmd(5, "spacehorizons ship budget 1500 20");
        step(10, mc -> ShipConvertDevtest.spawn(mc, 500));
        cmd(40, "spacehorizons ship fromrocket");
        tickerStep(0, waitConversion(c + "entidade -> blocos", 3000));
        step(30, mc -> {
            convShipId = ShipConvertDevtest.firstShipId(mc);
            ShipDevtest.check(c + "nave pronta no estaleiro", convShipId > 0 && ShipConvertDevtest.shipPhase(mc, convShipId).equals("READY+snapshot"),
                    "id=" + convShipId + " fase=" + ShipConvertDevtest.shipPhase(mc, convShipId));
            ShipConvertDevtest.setMaxPacket(mc, 10_000);
        });
        step(5, mc -> mc.player.connection.sendCommand("spacehorizons ship torocket " + convShipId));
        tickerStep(0, waitConversion(c + "blocos -> entidade (tem de ser recusada)", 3000));
        step(40, mc -> {
            com.spacehorizons.ship.ConversionReport r = ShipConvertDevtest.lastReport(mc);
            ShipConvertDevtest.logReport("guarda_pacote recusada", r);
            ShipDevtest.check(c + "a conversão foi recusada citando o pacote", r != null && !r.ok() && r.error().contains("pacote"), r == null ? "sem relatório" : r.error());
            ShipDevtest.check(c + "a nave continua pronta no estaleiro", ShipConvertDevtest.shipPhase(mc, convShipId).equals("READY+snapshot"),
                    "fase=" + ShipConvertDevtest.shipPhase(mc, convShipId));
            ShipDevtest.check(c + "nenhuma entidade de foguete apareceu", !ShipConvertDevtest.rocketEntityExists(mc), "existe=" + ShipConvertDevtest.rocketEntityExists(mc));
            Object[] chk = ShipConvertDevtest.yardCheck(mc, convShipId);
            ShipDevtest.check(c + "a nave segue intacta (itens exatos, células iguais, combustível igual)",
                    "".equals(chk[0]) && ((Integer) chk[1]) == 0 && ((Integer) chk[2]) == expectedFuel(), "itens=[" + chk[0] + "] células=" + chk[1] + " combustível=" + chk[2]);
            ShipConvertDevtest.setMaxPacket(mc, 1 << 20);
        });
        step(5, mc -> mc.player.connection.sendCommand("spacehorizons ship torocket " + convShipId));
        tickerStep(0, waitConversion(c + "blocos -> entidade (com o teto normal)", 3000));
        step(40, mc -> {
            com.spacehorizons.ship.ConversionReport r = ShipConvertDevtest.lastReport(mc);
            ShipDevtest.check(c + "com o teto normal a nave volta a foguete sem perdas", r != null && r.ok() && r.losses().isEmpty() && ShipConvertDevtest.rocketEntityExists(mc),
                    r == null ? "sem relatório" : r.losses() + " " + r.error());
        });
        landAndCleanup(c);
    }

    /** Pousa o foguete (entidade) no chão, confere os blocos, os itens e o combustível, e limpa o lugar. */
    private void landAndCleanup(String c) {
        step(10, mc -> {
            String err = ShipConvertDevtest.land(mc);
            ShipDevtest.check(c + "o foguete desmontou no chão (RocketLanding)", "".equals(err), "erro=" + err);
            if ("".equals(err)) {
                Object[] chk = ShipConvertDevtest.landedCheck(mc);
                ShipDevtest.check(c + "no chão: itens EXATOS depois da ida e volta", "".equals(chk[0]), "diferenças=[" + chk[0] + "]");
                ShipDevtest.check(c + "no chão: todas as células iguais ao retrato original", ((Integer) chk[1]) == 0, "diferentes=" + chk[1]);
                ShipDevtest.check(c + "no chão: combustível dos tanques igual", ((Integer) chk[2]) == expectedFuel(), "tanques=" + chk[2]);
            }
        });
        step(10, mc -> ShipConvertDevtest.clearLanded(mc));
        step(20, mc -> ShipConvertDevtest.discardRocket(mc));
    }

    /**
     * Fecha e reabre o mundo NO MEIO de uma conversão entidade -> blocos (orçamento minúsculo, a nave ainda em FILLING com blocos pela
     * metade): ao reabrir, a recuperação limpa a caixa e esquece a nave, a entidade segue inteira (ela era a verdade) e uma conversão nova
     * funciona normalmente, ida e volta e pouso sem perdas.
     */
    private void reloadMidConversionCycle() {
        String c = "CONV reload_no_meio: ";
        cmd(5, "spacehorizons ship budget 25 0");
        step(10, mc -> ShipConvertDevtest.spawn(mc, 4000));
        cmd(40, "spacehorizons ship fromrocket");
        step(40, mc -> {
            convShipId = ShipConvertDevtest.firstShipId(mc);
            String phase = ShipConvertDevtest.shipPhase(mc, convShipId);
            ShipDevtest.check(c + "a conversão está em andamento e a nave em FILLING", ShipConvertDevtest.busy(mc) && phase.startsWith("FILLING"),
                    "ocupado=" + ShipConvertDevtest.busy(mc) + " fase=" + phase);
            convBox = ShipConvertDevtest.boxOf(mc, convShipId);
            SpaceHorizons.LOGGER.info(TAG + "SHIPCONV fechando o mundo NO MEIO da conversão (nave {} em {})", convShipId, phase);
            ShipDevtest.startReload(mc, FRESH_WORLD);
        });
        step(400, mc -> SpaceHorizons.LOGGER.info(TAG + "SHIPCONV mundo reaberto depois de fechar no meio da conversão"));
        tickerStep(0, waitConversion(c + "limpeza de recuperação", 3000));
        step(40, mc -> {
            com.spacehorizons.ship.ConversionReport r = ShipConvertDevtest.lastReport(mc);
            ShipConvertDevtest.logReport("reload_no_meio recuperação", r);
            ShipDevtest.check(c + "a recuperação limpou a caixa e esqueceu a nave", ShipDevtest.shipCount(mc) == 0 && ShipDevtest.heldCount(mc) == 0
                    && ShipConvertDevtest.nonAirInBox(mc, convBox) == 0, "naves=" + ShipDevtest.shipCount(mc) + " seguradas=" + ShipDevtest.heldCount(mc)
                    + " blocos na caixa=" + ShipConvertDevtest.nonAirInBox(mc, convBox));
            String info = ShipConvertDevtest.rocketInfo(mc);
            ShipDevtest.check(c + "a entidade do foguete continua inteira no overworld", info != null && info.contains("blocos=" + ShipConvertDevtest.current.portrait().blockCount()),
                    String.valueOf(info));
        });
        cmd(5, "spacehorizons ship budget 1500 20");
        cmd(10, "spacehorizons ship fromrocket");
        tickerStep(0, waitConversion(c + "entidade -> blocos (depois da recuperação)", 3000));
        step(30, mc -> {
            com.spacehorizons.ship.ConversionReport r = ShipConvertDevtest.lastReport(mc);
            convShipId = ShipConvertDevtest.firstShipId(mc);
            ShipDevtest.check(c + "nova conversão entidade -> blocos sem perdas", r != null && r.ok() && r.losses().isEmpty(), r == null ? "sem relatório" : r.losses() + " " + r.error());
            Object[] chk = ShipConvertDevtest.yardCheck(mc, convShipId);
            ShipDevtest.check(c + "no estaleiro: itens exatos, células iguais, combustível igual", "".equals(chk[0]) && ((Integer) chk[1]) == 0 && ((Integer) chk[2]) == expectedFuel(),
                    "itens=[" + chk[0] + "] células=" + chk[1] + " combustível=" + chk[2]);
        });
        step(5, mc -> mc.player.connection.sendCommand("spacehorizons ship torocket " + convShipId));
        tickerStep(0, waitConversion(c + "blocos -> entidade (depois da recuperação)", 3000));
        step(30, mc -> {
            com.spacehorizons.ship.ConversionReport r = ShipConvertDevtest.lastReport(mc);
            ShipDevtest.check(c + "nova conversão blocos -> entidade sem perdas", r != null && r.ok() && r.losses().isEmpty() && ShipConvertDevtest.rocketEntityExists(mc),
                    r == null ? "sem relatório" : r.losses() + " " + r.error());
        });
        landAndCleanup(c);
        cmd(10, "tp @s 0 200 0");
        fly(20);
    }

    // ===================================================================================================================
    // NAVES NO CHÃO (docs/PLANO_NAVES.md, fase 2a-i): -PshOnly=shipground. Nave montada pela ponte no chão, recusa sem motor, empuxo dos
    // bocais para baixo, retrato, mundo, piloto e subida de 500 blocos. Bloco separado; as conferências ficam em ShipGroundDevtest.
    // ===================================================================================================================
    private void shipGroundScenario() {
        BlockPos b = ShipGroundDevtest.BASE;
        double bx = b.getX() + 0.5;
        double by = b.getY();
        double bz = b.getZ() + 0.5;
        cmd(5, "gamerule send_command_feedback false");
        cmd(5, "gamerule show_advancement_messages false");
        cmd(5, "weather clear 1000000");
        cmd(5, "time set 6000");
        cmd(5, "gamemode creative");
        cmd(5, "clear @s");
        step(10, ShipGroundDevtest::build);
        cmd(20, "tp @s " + (bx + 9.0) + " " + (by + 2.5) + " " + (bz + 7.0));
        fly(10);
        look(20, 140f, 12f);
        shot(60, "sg_1_chao_bocais");
        // de baixo para cima, perto do chão: os bocais para baixo e o horizontal de frente
        cmd(20, "tp @s " + (bx + 6.0) + " " + (by + 1.0) + " " + (bz + 6.0));
        look(20, 135f, -5f);
        shot(40, "sg_1b_chao_perto");
        step(10, ShipGroundDevtest::refusal);
        step(10, ShipGroundDevtest::assemble);
        tickerStep(5, waitFor("sg_assembled", 200));
        thirdPerson();
        shot(40, "sg_2_montada");
        step(10, ShipGroundDevtest::fly);
        tickerStep(5, waitFor("sg_flown", 3_000));
        thirdPerson();
        shot(20, "sg_3_voo");
        step(20, mc -> {
            ShipGroundDevtest.summary();
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    // NAVE SUBINDO ATÉ A ALTITUDE DE SAÍDA (docs/PLANO_NAVES.md, fase 2a-ii): -PshOnly=shipascent. Montada no chão, sobe pelo acelerador
    // do piloto; ao cruzar a altitude de saída a entidade vira blocos no estaleiro e o piloto senta na ponte com o voo virtual ligado.
    // Bloco separado; as conferências ficam em ShipAscentDevtest.
    // ===================================================================================================================
    private void shipAscentScenario() {
        BlockPos b = ShipAscentDevtest.BASE;
        double bx = b.getX() + 0.5;
        double by = b.getY();
        double bz = b.getZ() + 0.5;
        cmd(5, "gamerule send_command_feedback false");
        cmd(5, "gamerule show_advancement_messages false");
        cmd(5, "weather clear 1000000");
        cmd(5, "time set 6000");
        cmd(5, ShipAscentDevtest.STANDING ? "gamemode survival" : "gamemode creative");
        cmd(5, "difficulty peaceful");
        cmd(5, "clear @s");
        step(10, ShipAscentDevtest::build);
        // em pé sobre o piso da nave (o bloco de casco ao lado da ponte): a montagem põe o jogador no convés; sentado, ao lado dela
        cmd(20, ShipAscentDevtest.STANDING ? "tp @s " + (bx + 1.0) + " " + (by + 2.0) + " " + bz : "tp @s " + (bx + 9.0) + " " + (by + 2.5) + " " + (bz + 7.0));
        look(20, 140f, 12f);
        shot(40, "sa_1_chao");
        step(10, ShipAscentDevtest::assemble);
        tickerStep(5, waitFor("sa_assembled", 200));
        thirdPerson();
        shot(40, "sa_1b_montada");
        step(10, ShipAscentDevtest::climb);
        tickerStep(5, waitFor("sa_half", 6_000));
        thirdPerson();
        shot(20, "sa_2_subindo");
        tickerStep(5, waitFor("sa_frozen", 6_000));
        tickerStep(5, waitFor("sa_done", 1_200));
        look(20, 140f, 12f);
        shot(60, "sa_3_estaleiro");
        look(20, 0f, -70f);
        shot(20, "sa_4_ceu_virtual");
        step(20, mc -> {
            ShipAscentDevtest.summary();
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    // ===================================================================================================================
    // NAVE, DESCIDA (docs/PLANO_NAVES.md, fase 2b, parte 1): -PshOnly=shipdescent. O começo do shipascent (montada, subida, troca e
    // circularização); depois a nave virtual é posta a ~1.000 blocos, descendo, e vira entidade na Terra. Bloco separado; as conferências
    // ficam em ShipDescentDevtest. Pouso e desmontagem ficam para a próxima execução.
    // ===================================================================================================================
    private void shipDescentScenario() {
        BlockPos b = ShipAscentDevtest.BASE;
        double bx = b.getX() + 0.5;
        double by = b.getY();
        double bz = b.getZ() + 0.5;
        cmd(5, "gamerule send_command_feedback false");
        cmd(5, "gamerule show_advancement_messages false");
        cmd(5, "weather clear 1000000");
        cmd(5, "time set 6000");
        cmd(5, ShipAscentDevtest.STANDING ? "gamemode survival" : "gamemode creative");
        cmd(5, "difficulty peaceful");
        cmd(5, "clear @s");
        step(10, ShipAscentDevtest::build);
        cmd(20, ShipAscentDevtest.STANDING ? "tp @s " + (bx + 1.0) + " " + (by + 2.0) + " " + bz : "tp @s " + (bx + 9.0) + " " + (by + 2.5) + " " + (bz + 7.0));
        look(20, 140f, 12f);
        shot(40, "sd_1_chao");
        step(10, ShipAscentDevtest::assemble);
        tickerStep(5, waitFor("sa_assembled", 200));
        step(5, ShipDescentDevtest::photo);
        thirdPerson();
        shot(40, "sd_1b_montada");
        step(10, ShipAscentDevtest::climb);
        tickerStep(5, waitFor("sa_half", 6_000));
        thirdPerson();
        shot(20, "sd_2_subindo");
        tickerStep(5, waitFor("sa_frozen", 6_000));
        tickerStep(5, waitFor("sa_done", 1_200));
        step(20, ShipDescentDevtest::start);
        tickerStep(5, waitFor("sd_done", 4_000));
        if (ShipAscentDevtest.STANDING) {
            // quem está em pé desce junto: entra no convés da entidade, na mesma posição relativa à nave
            tickerStep(0, waitUntil("SHIPDESCENT: tripulante no convés da entidade", 300, ShipDescentDevtest::crewOnEntity));
            step(5, ShipDescentDevtest::checkCrewOnEntity);
        }
        // a nave cai: o piloto da missão assume já, na fase da descida da Terra (freia no retrógrado até o contato)
        step(0, mc -> MissionDevtest.goFrom(mc, MissionPilot.Phase.EARTH_LANDING));
        thirdPerson();
        shot(10, "sd_3_atmosfera");
        tickerStep(5, waitFor("end", 6_000));
        shot(40, "sd_4_pousada");
        step(10, ShipDescentDevtest::landAndCheck);
        tickerStep(5, waitFor("sd_landed", 200));
        thirdPerson();
        shot(40, "sd_5_desmontada");
        step(20, mc -> {
            ShipAscentDevtest.summary();
            SpaceHorizons.LOGGER.info(ShipAscentDevtest.TAG + "FIM");
            mc.stop();
        });
    }

    // ===================================================================================================================
    // NAVES, FASE 1 (docs/PLANO_NAVES.md, 1b a 1e): -PshOnly=shipflight (voo virtual, piloto de teste no servidor) e -PshOnly=ship (a nave
    // inteira: sentar, olhar pela janela, girar, queimar e andar pelo interior). Bloco separado; a leitura do servidor fica em ShipFlightDevtest.
    // ===================================================================================================================

    /** Voo virtual da nave (1c): cria a nave de voo e liga o piloto de teste do servidor, que roda as fases e confere os números. */
    private void shipFlightScenario() {
        cmd(5, "gamerule send_command_feedback false");
        cmd(5, "gamerule show_advancement_messages false");
        cmd(5, "weather clear 1000000");
        cmd(5, "time set 6000");
        cmd(10, "spacehorizons ship create flight");
        step(60, mc -> {
            ShipDevtest.check("a nave de voo foi registrada", ShipDevtest.shipCount(mc) == 1, "naves=" + ShipDevtest.shipCount(mc));
            ShipFlightDevtest.begin(mc, 1);
        });
        tickerStep(0, new Ticker() {
            int n;

            @Override
            public boolean tick(Minecraft mc) {
                n++;
                if (n > 4000) {
                    ShipDevtest.check("o piloto de teste terminou a tempo", false, "ticks=" + n);
                    return true;
                }
                return ShipFlightDevtest.done();
            }
        });
        cmd(5, "gamerule send_command_feedback true");
        cmd(5, "gamerule show_advancement_messages true");
        step(20, mc -> {
            ShipDevtest.summary();
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    private com.spacehorizons.ship.ShipBuilder.FlightLayout fl;
    private ShipPilotDevtest.Live shipLiveRef;
    private com.spacehorizons.space.flight.V3 shipForwardStart;
    private Vec3 shipStandPos;
    private double shipApBefore;
    private double shipFuelBefore;

    /** Espera a condição (checada a cada tick do cliente) por no máximo {@code maxTicks}; registra se não veio. */
    private Ticker waitUntil(String label, int maxTicks, java.util.function.Predicate<Minecraft> condition) {
        return new Ticker() {
            int n;

            @Override
            public boolean tick(Minecraft mc) {
                n++;
                if (condition.test(mc)) {
                    SpaceHorizons.LOGGER.info(TAG + "SHIP {} em {} ticks", label, n);
                    return true;
                }
                if (n > maxTicks) {
                    ShipDevtest.check(label, false, "não aconteceu em " + maxTicks + " ticks");
                    return true;
                }
                return false;
            }
        };
    }

    /** Segura uma tecla (de movimento ou do piloto) por {@code ticks} ticks do cliente e solta. */
    private void holdKey(int wait, java.util.function.Function<Minecraft, net.minecraft.client.KeyMapping> key, int ticks) {
        step(wait, mc -> key.apply(mc).setDown(true));
        step(ticks, mc -> key.apply(mc).setDown(false));
    }

    private static void rightClick(Minecraft mc, BlockPos pos, Direction face, String label) {
        Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
        SpaceHorizons.LOGGER.info(TAG + "SHIP clique direito em {} ({}): {}", label, pos, result);
    }

    private static void lookAtPoint(Minecraft mc, Vec3 target) {
        Vec3 d = target.subtract(mc.player.getEyePosition());
        mc.player.setYRot(ShipDevtest.yawOf(d));
        mc.player.setXRot(ShipDevtest.pitchOf(d));
    }

    /**
     * A nave inteira (fases 1d e 1e): cria a nave de voo, entra, olha o céu pelas janelas (a Terra à direita), senta no assento da ponte, muda o SAS
     * e gira a atitude com as teclas (o céu gira: a Terra sai da janela da proa; mede o tremor), alinha pelo SAS prógrado, liga o motor com a
     * alavanca do acelerador (a órbita muda: apoastro e periastro no painel), levanta do assento com a queima ligada e anda pelo interior (abre a eclusa
     * do anteparo) sem ser empurrado.
     */
    private void shipScenario() {
        String c = "SHIP: ";
        cmd(5, "gamerule send_command_feedback false");
        cmd(5, "gamerule show_advancement_messages false");
        cmd(5, "weather clear 1000000");
        cmd(5, "time set 6000");
        step(5, mc -> mc.options.fov().set(90));
        cmd(10, "spacehorizons ship create flight");
        cmd(60, "spacehorizons ship goto 1");
        tickerStep(0, waitInYard(1, 700));
        step(60, mc -> {
            var box = ShipDevtest.onServer(mc, server -> com.spacehorizons.ship.ShipRegistry.get(server).byId(1).box());
            fl = com.spacehorizons.ship.ShipBuilder.flightLayout(box);
            mc.player.getAbilities().flying = false;
            mc.player.onUpdateAbilities();
            ShipDevtest.check(c + "jogador na sala da ponte, dentro da caixa da nave de voo", box.contains(mc.player.getX(), mc.player.getY(), mc.player.getZ()),
                    "pos=(" + f(mc.player.getX()) + ", " + f(mc.player.getY()) + ", " + f(mc.player.getZ()) + ")");
            ShipDevtest.check(c + "o cliente recebe o estado virtual da nave (pacotes)", com.spacehorizons.client.ship.ShipRenderClient.active()
                    && com.spacehorizons.client.ship.ShipRenderClient.packetCount() > 5, "pacotes=" + com.spacehorizons.client.ship.ShipRenderClient.packetCount());
            ShipDevtest.check(c + "o céu usa a atitude da nave (fonte externa ligada)", SkyAttitude.current() != null, String.valueOf(SkyAttitude.current()));
            ShipPilotDevtest.Live live = ShipPilotDevtest.live(mc, 1);
            shipForwardStart = live.forward();
            SpaceHorizons.LOGGER.info(TAG + "SHIP estado inicial: {}", ShipPilotDevtest.describe(live));
        });
        // A. a proa: a Terra no canto direito do quadro
        look(10, 180f, 0f);
        step(30, mc -> {
            double[] e = ShipPilotDevtest.earthFromView(mc, 180f, 0f);
            SpaceHorizons.LOGGER.info(TAG + "SHIP olhando a proa (norte): Terra a {} graus do centro do olhar, raio angular {} graus", f(e[0]), f(e[1]));
            ShipDevtest.check(c + "olhando a proa a Terra está à direita (~90 graus do olhar, a borda dentro do quadro)", e[0] > 80 && e[0] < 100 && e[0] - e[1] < 55.0,
                    "angulo=" + f(e[0]) + " raio=" + f(e[1]));
            String sight = ShipDevtest.sightLine(mc.level, mc.player.getEyePosition(), ShipPilotDevtest.lookVec(180f, 0f));
            ShipDevtest.check(c + "a proa tem janela (a linha de visão passa por vidro da nave e dá no espaço)", sight.startsWith("janela"), sight);
        });
        shot(5, "ship_01_hall_bow_earth_right");
        look(10, -90f, 0f);
        step(30, mc -> {
            double[] e = ShipPilotDevtest.earthFromView(mc, -90f, 0f);
            SpaceHorizons.LOGGER.info(TAG + "SHIP olhando o leste: Terra a {} graus do centro do olhar", f(e[0]));
            ShipDevtest.check(c + "olhando o leste a Terra está de frente", e[0] < 10.0, "angulo=" + f(e[0]));
            String sight = ShipDevtest.sightLine(mc.level, mc.player.getEyePosition(), ShipPilotDevtest.lookVec(-90f, 0f));
            ShipDevtest.check(c + "o lado leste tem janela", sight.startsWith("janela"), sight);
        });
        shot(5, "ship_02_hall_east_earth");

        // B. anda até o assento do piloto e senta (clique direito de verdade)
        tickerStep(0, walkToPoint(mc -> new Vec3(fl.pilotSeat().getX() + 0.5, fl.pilotSeat().getY(), fl.pilotSeat().getZ() + 2.6), 300, "até o assento do piloto"));
        step(5, mc -> {
            shipStandPos = mc.player.position();
            lookAtPoint(mc, Vec3.atCenterOf(fl.pilotSeat()));
        });
        step(5, mc -> rightClick(mc, fl.pilotSeat(), Direction.UP, "assento do piloto"));
        tickerStep(0, waitUntil(c + "sentou no assento da ponte", 60, mc -> mc.player.getVehicle() instanceof com.spacehorizons.ship.ShipSeatEntity));
        step(20, mc -> {
            boolean seated = mc.player.getVehicle() instanceof com.spacehorizons.ship.ShipSeatEntity;
            double moved = shipStandPos == null ? 99.0 : Math.hypot(mc.player.getX() - fl.pilotSeat().getX() - 0.5, mc.player.getZ() - fl.pilotSeat().getZ() - 0.5);
            ShipDevtest.check(c + "sentado no assento (entidade do assento), sem teletransporte: fica sobre o bloco", seated && moved < 0.6 && ShipDevtest.shipAroundPlayer(mc) == 1,
                    "sentado=" + seated + " distância ao centro do assento=" + f(moved));
            ShipDevtest.check(c + "o piloto está pilotando no cliente (teclas do foguete ativas)", com.spacehorizons.client.ship.ShipPilot.isPiloting(), "pilotando");
        });
        look(5, 180f, 0f);
        step(60, mc -> {
            ShipDevtest.check(c + "o cliente manda o comando e recebe a telemetria do servidor", com.spacehorizons.client.ship.ShipPilot.sentCount() > 3
                    && com.spacehorizons.client.rocket.RocketTelemetryClient.current() != null, "pacotes enviados=" + com.spacehorizons.client.ship.ShipPilot.sentCount()
                    + " telemetria=" + (com.spacehorizons.client.rocket.RocketTelemetryClient.current() != null));
            var t = com.spacehorizons.client.rocket.RocketTelemetryClient.current();
            if (t != null) {
                SpaceHorizons.LOGGER.info(TAG + "SHIP painel: altitude={} apoastro={} periastro={} combustível={}/{} Δv={} b/t", f(t.altitude()), f(t.apoapsisAltitude()),
                        f(t.periapsisAltitude()), f(t.fuelMb()), f(t.fuelCapacityMb()), f(t.deltaV()));
                ShipDevtest.check(c + "o painel mostra a órbita circular a 30.000 (apoastro e periastro)", Math.abs(t.apoapsisAltitude() - 30_000.0) < 5.0
                        && Math.abs(t.periapsisAltitude() - 30_000.0) < 5.0 && t.fuelMb() > 31_000.0, "ap=" + f(t.apoapsisAltitude()) + " pe=" + f(t.periapsisAltitude()));
            }
        });
        shot(5, "ship_03_seated_panel_bow");

        // C. SAS estabilizar (tecla T) e gira a atitude com a tecla A (guinada à esquerda) até ~90 graus
        step(5, mc -> CockpitDevtest.click(RocketKeys.NEXT_SAS));
        step(15, mc -> {
            ShipPilotDevtest.Live l = ShipPilotDevtest.live(mc, 1);
            ShipDevtest.check(c + "a tecla T chegou ao servidor: SAS estabilizar", l.sas() == 1, "sas=" + l.sas());
            ShipPilotDevtest.startFrames();
        });
        step(5, mc -> mc.options.keyLeft.setDown(true));
        tickerStep(0, waitUntil(c + "a proa girou 82 graus com a tecla A", 120, mc -> {
            ShipPilotDevtest.Live l = ShipPilotDevtest.live(mc, 1);
            return ShipPilotDevtest.angleDeg(l.forward(), shipForwardStart) >= 82.0;
        }));
        step(0, mc -> mc.options.keyLeft.setDown(false));
        step(25, mc -> {
            double[] j = ShipPilotDevtest.stopFrames();
            ShipPilotDevtest.Live l = ShipPilotDevtest.live(mc, 1);
            double turned = ShipPilotDevtest.angleDeg(l.forward(), shipForwardStart);
            SpaceHorizons.LOGGER.info(TAG + "SHIP depois do giro: a proa girou {} graus; {}", f(turned), ShipPilotDevtest.describe(l));
            ShipDevtest.check(c + "a proa girou ~90 graus (SAS estabilizar parou o giro)", turned > 85.0 && turned < 100.0, "girou=" + f(turned));
            ShipDevtest.check(c + "sem tremor no giro: velocidade angular do céu quadro a quadro varia menos de 15% na janela de giro constante",
                    j[4] >= 5 && j[3] < 0.15, String.format(java.util.Locale.ROOT, "quadros=%.0f janela=%.0f média=%.1f graus/s desvio máximo=%.1f%%", j[0], j[4], j[2], j[3] * 100.0));
            double[] e = ShipPilotDevtest.earthFromView(mc, 180f, 0f);
            SpaceHorizons.LOGGER.info(TAG + "SHIP depois do giro, olhando a proa: Terra a {} graus do olhar (raio {})", f(e[0]), f(e[1]));
            ShipDevtest.check(c + "depois do giro de 90 graus a Terra saiu da janela da proa (disco todo fora do quadro)", e[0] - e[1] > 60.0, "angulo=" + f(e[0]) + " raio=" + f(e[1]));
            ShipDevtest.check(c + "ainda sentado depois de segurar A (Shift e sprint mascarados não importam aqui)", mc.player.getVehicle() instanceof com.spacehorizons.ship.ShipSeatEntity, "veiculo=" + mc.player.getVehicle());
        });
        shot(5, "ship_04_turned_90_bow_no_earth");
        look(10, -90f, 0f);
        shot(20, "ship_05_turned_90_east_window");
        look(10, 180f, 0f);

        // D. SAS prógrado alinha a proa; liga o motor com a alavanca (Shift sobe o acelerador, que fica onde se deixa)
        step(5, mc -> CockpitDevtest.click(RocketKeys.NEXT_SAS));
        tickerStep(0, waitUntil(c + "a proa alinhou com o prógrado (SAS prógrado, menos de 1 grau)", 250, mc -> {
            ShipPilotDevtest.Live l = ShipPilotDevtest.live(mc, 1);
            return l.sas() == 2 && ShipPilotDevtest.angleDeg(l.forward(), l.velocity()) < 1.0;
        }));
        step(10, mc -> {
            shipLiveRef = ShipPilotDevtest.live(mc, 1);
            shipApBefore = shipLiveRef.apoapsis();
            shipFuelBefore = shipLiveRef.fuelMb();
            SpaceHorizons.LOGGER.info(TAG + "SHIP antes da queima: {}", ShipPilotDevtest.describe(shipLiveRef));
        });
        step(5, mc -> mc.options.keyShift.setDown(true));
        step(7, mc -> mc.options.keyShift.setDown(false));
        step(10, mc -> {
            ShipPilotDevtest.Live l = ShipPilotDevtest.live(mc, 1);
            ShipDevtest.check(c + "Shift sobe o acelerador (alavanca) e o jogador continua sentado", l.throttle() > 0.08 && l.throttle() < 0.4
                    && mc.player.getVehicle() instanceof com.spacehorizons.ship.ShipSeatEntity, "acelerador=" + f(l.throttle()) + " veiculo=" + mc.player.getVehicle());
            ShipDevtest.check(c + "o acelerador fica onde se deixou depois de soltar Shift", Math.abs(com.spacehorizons.client.ship.ShipPilot.throttle() - l.throttle()) < 0.05,
                    "painel=" + f(com.spacehorizons.client.ship.ShipPilot.throttle()) + " servidor=" + f(l.throttle()));
        });
        step(40, mc -> {
            ShipPilotDevtest.Live l = ShipPilotDevtest.live(mc, 1);
            var t = com.spacehorizons.client.rocket.RocketTelemetryClient.current();
            SpaceHorizons.LOGGER.info(TAG + "SHIP queimando: {} | painel: apoastro={} periastro={} combustível={}", ShipPilotDevtest.describe(l), t == null ? "?" : f(t.apoapsisAltitude()),
                    t == null ? "?" : f(t.periapsisAltitude()), t == null ? "?" : f(t.fuelMb()));
            ShipDevtest.check(c + "a queima muda a órbita: o apoastro sobe e o combustível cai", l.apoapsis() > shipApBefore + 1_000.0 && l.fuelMb() < shipFuelBefore - 500.0,
                    String.format(java.util.Locale.ROOT, "apoastro %.0f -> %.0f, combustível %.0f -> %.0f", shipApBefore, l.apoapsis(), shipFuelBefore, l.fuelMb()));
            ShipDevtest.check(c + "o painel do piloto mostra o apoastro novo e o combustível (telemetria do servidor)", t != null && Math.abs(t.apoapsisAltitude() - l.apoapsis()) < 3_000.0
                    && Math.abs(t.fuelMb() - l.fuelMb()) < 1_500.0, "painel=" + (t == null ? "?" : f(t.apoapsisAltitude()) + "/" + f(t.fuelMb())));
        });
        shot(5, "ship_06_burning_panel_orbit_changing");

        // E. levanta do assento com a queima ligada (tecla G) e anda pelo interior sem ser empurrado
        step(5, mc -> CockpitDevtest.click(RocketKeys.DISASSEMBLE));
        step(20, mc -> {
            ShipPilotDevtest.Live l = ShipPilotDevtest.live(mc, 1);
            ShipDevtest.check(c + "a tecla G levantou o jogador do assento (a queima continua ligada)", mc.player.getVehicle() == null && l.throttle() > 0.08,
                    "veiculo=" + mc.player.getVehicle() + " acelerador=" + f(l.throttle()));
            shipStandPos = mc.player.position();
            SpaceHorizons.LOGGER.info(TAG + "SHIP em pé em ({}, {}, {}) com a queima ligada: {}", f(shipStandPos.x), f(shipStandPos.y), f(shipStandPos.z), ShipPilotDevtest.describe(l));
        });
        step(30, mc -> {
            ShipPilotDevtest.Live l = ShipPilotDevtest.live(mc, 1);
            double drift = mc.player.position().distanceTo(shipStandPos);
            ShipDevtest.check(c + "parado durante a queima o jogador não é empurrado (deriva menor que 0,05 bloco em 30 ticks, motor ligado)", drift < 0.05 && l.throttle() > 0.08,
                    "deriva=" + f(drift) + " acelerador=" + f(l.throttle()) + " " + ShipPilotDevtest.describe(l));
            shipFuelBefore = l.fuelMb();
        });
        tickerStep(0, walkToPoint(mc -> new Vec3(fl.bulkheadDoor().getX() + 0.5, fl.hall().getY(), fl.bulkheadDoor().getZ() - 1.6), 400, "até o anteparo, queimando"));
        step(5, mc -> lookAtPoint(mc, Vec3.atCenterOf(fl.bulkheadDoor())));
        step(5, mc -> rightClick(mc, fl.bulkheadDoor(), Direction.NORTH, "eclusa de baixo"));
        step(5, mc -> rightClick(mc, fl.bulkheadDoor().above(), Direction.NORTH, "eclusa de cima"));
        step(10, mc -> {
            boolean open = mc.level.getBlockState(fl.bulkheadDoor()).getValue(com.spacehorizons.ship.block.ShipAirlockBlock.OPEN)
                    && mc.level.getBlockState(fl.bulkheadDoor().above()).getValue(com.spacehorizons.ship.block.ShipAirlockBlock.OPEN);
            ShipDevtest.check(c + "a eclusa do anteparo abre com o botão direito", open, "aberta=" + open);
        });
        shot(5, "ship_07_airlock_open_burning");
        tickerStep(0, walkToPoint(mc -> new Vec3(fl.bulkheadDoor().getX() + 0.5, fl.hall().getY(), fl.bulkheadDoor().getZ() + 12.5), 500, "pela eclusa até a sala dos motores, queimando"));
        step(5, mc -> {
            ShipPilotDevtest.Live l = ShipPilotDevtest.live(mc, 1);
            ShipDevtest.check(c + "o jogador atravessou o anteparo e andou pela nave com o motor ligado", mc.player.getZ() > fl.bulkheadDoor().getZ() + 8.0 && l.throttle() > 0.08
                    && l.fuelMb() < shipFuelBefore - 200.0, "z=" + f(mc.player.getZ()) + " acelerador=" + f(l.throttle()) + " combustível " + f(shipFuelBefore) + " -> " + f(l.fuelMb()));
            SpaceHorizons.LOGGER.info(TAG + "SHIP depois de andar: {}", ShipPilotDevtest.describe(l));
        });
        look(5, 0f, 5f);
        shot(20, "ship_08_engine_room_while_burning");
        step(5, mc -> {
            ShipPilotDevtest.Live l = ShipPilotDevtest.live(mc, 1);
            ShipDevtest.check(c + "a órbita mudou durante a caminhada (queima contínua, apoastro maior que antes de levantar)", l.apoapsis() > shipApBefore + 3_000.0,
                    String.format(java.util.Locale.ROOT, "apoastro %.0f -> %.0f", shipApBefore, l.apoapsis()));
        });

        // F. motores em direcoes diferentes: dois viram o bocal para o oeste (empurram para o leste = a direita da nave, a Terra) e dois seguem
        // empurrando pela proa. A queima continua ligada: o apoastro continua subindo (componente pela proa) e o periastro cai (componente para a Terra).
        step(5, mc -> {
            ShipPilotDevtest.Live l = ShipPilotDevtest.live(mc, 1);
            shipApBefore = l.apoapsis();
            shipPeBefore = l.periapsis();
            String census = ShipDevtest.onServer(mc, server -> {
                var yard = com.spacehorizons.ship.ShipYard.level(server);
                for (int i = 0; i < 2; i++) {
                    yard.setBlock(fl.engines().get(i), ModBlocks.SHIP_IMPULSE_ENGINE.get().defaultBlockState()
                            .setValue(com.spacehorizons.ship.block.ShipImpulseEngineBlock.FACING, Direction.WEST), 3);
                }
                var result = com.spacehorizons.ship.ShipCensusService.scanNow(server, com.spacehorizons.ship.ShipRegistry.get(server).byId(1));
                return result.arrangement().toString();
            });
            SpaceHorizons.LOGGER.info(TAG + "SHIP dois motores viraram o bocal para o oeste: {} | antes: apoastro={} periastro={}", census, f(shipApBefore), f(shipPeBefore));
        });
        look(5, 0f, 5f);
        step(20, mc -> {
            String line = ShipDevtest.onServer(mc, server -> {
                var live = com.spacehorizons.ship.ShipFlightTicker.live(1);
                return live == null ? "?" : live.arrangement() + " empuxo=" + live.stats().thrust();
            });
            ShipDevtest.check(c + "o voo adotou os motores em direcoes diferentes (2 para o sul, 2 para o oeste: empuxo liquido 2*raiz(2)*8)", line.contains("nozzleWest=2")
                    && line.contains("nozzleSouth=2") && line.contains("empuxo=22.6"), line);
        });
        shot(5, "ship_09_engines_two_directions_burning");
        step(120, mc -> {
            ShipPilotDevtest.Live l = ShipPilotDevtest.live(mc, 1);
            SpaceHorizons.LOGGER.info(TAG + "SHIP depois da queima com motores em duas direcoes: apoastro {} -> {}, periastro {} -> {}", f(shipApBefore), f(l.apoapsis()),
                    f(shipPeBefore), f(l.periapsis()));
            ShipDevtest.check(c + "motores em duas direcoes: o apoastro sobe (componente pela proa) e o periastro tambem muda (componente para a Terra, a direita)",
                    l.apoapsis() > shipApBefore + 500.0 && Math.abs(l.periapsis() - shipPeBefore) > 100.0,
                    String.format(java.util.Locale.ROOT, "apoastro %.0f -> %.0f, periastro %.0f -> %.0f", shipApBefore, l.apoapsis(), shipPeBefore, l.periapsis()));
        });
        shot(5, "ship_10_after_mixed_burn_panel");
        cmd(5, "gamerule send_command_feedback true");
        cmd(5, "gamerule show_advancement_messages true");
        step(20, mc -> {
            ShipDevtest.summary();
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    // ===================================================================================================================
    // BLUEPRINT (plantas holográficas de montagem, docs/PLANO_TIERS.md seção 4): -PshOnly=blueprint. Bloco separado; a leitura do servidor
    // e as verificações ficam em BlueprintDevtest.
    // ===================================================================================================================

    /**
     * Plantas holográficas: escolhe a planta orbital pelo menu do item (clique no ar) e a põe numa plataforma vazia (clique de verdade, print do
     * holograma); põe parte dos blocos com dois errados (print com o vermelho e o progresso no painel); completa (print sem fantasmas), monta e
     * voa um pouco; depois salva o foguete de outra plataforma com a planta em branco, reaplica a planta salva numa terceira plataforma (print),
     * completa e confere o projeto, e tira a planta com agachado + clique.
     */
    private void blueprintScenario() {
        BlockPos a = BlueprintDevtest.PAD_A;
        BlockPos b = BlueprintDevtest.PAD_B_CLICK;
        BlockPos c = BlueprintDevtest.PAD_C;
        cmd(5, "gamerule send_command_feedback false");
        cmd(20, "weather clear 1000000");
        cmd(10, "gamemode creative");
        cmd(5, "time set 6000");
        cmd(5, "gamerule advance_time false");
        fly(10);
        cmd(10, "clear @s");

        // A. menu e planta orbital numa plataforma vazia
        cmd(10, "tp @s " + (a.getX() + 0.5) + " " + (a.getY() + 1.2) + " " + (a.getZ() + 3.5));
        fly(10);
        step(10, BlueprintDevtest::prepareA);
        tickerStep(5, waitFor("bp_prepared_a", 200));
        cmd(10, "give @s spacehorizons:rocket_blueprint");
        look(10, 0f, -60f);
        step(10, BlueprintDevtest::openMenu);
        shot(20, "bp_1_menu");
        step(5, mc -> BlueprintDevtest.choose(mc, "orbital"));
        step(20, mc -> BlueprintDevtest.checkHeld(mc, "o menu trocou a planta do item pela orbital",
                com.spacehorizons.rocket.blueprint.Blueprints.ORBITAL));
        look(5, 180f, 45f);
        step(5, mc -> useOnBlock(mc, a, Direction.UP));
        step(30, mc -> BlueprintDevtest.checkActive(mc, "plataforma vazia", a, com.spacehorizons.rocket.blueprint.Blueprints.ORBITAL,
                com.spacehorizons.rocket.blueprint.Blueprints.ORBITAL.size() - 1, 1));
        cmd(10, "tp @s " + (a.getX() + 9.5) + " " + (a.getY() + 7.0) + " " + (a.getZ() + 0.5));
        fly(5);
        look(10, 90f, 25f);
        step(5, mc -> mc.player.getInventory().setSelectedSlot(8));
        step(5, mc -> com.spacehorizons.client.rocket.RocketDesignClient.pinForTest(a));
        shot(60, "bp_2_hologram_orbital_empty_pad");

        // B. parte dos blocos, dois errados
        step(5, BlueprintDevtest::placePartial);
        tickerStep(5, waitFor("bp_partial", 200));
        step(30, BlueprintDevtest::checkPartialClient);
        shot(20, "bp_3_partial_wrong_red_panel");

        // C. completa, monta e voa
        step(5, mc -> BlueprintDevtest.complete(mc, a, com.spacehorizons.rocket.blueprint.Blueprints.ORBITAL, "bp_complete_a"));
        tickerStep(5, waitFor("bp_complete_a", 200));
        step(40, mc -> BlueprintDevtest.checkCompleteClient(mc, a, com.spacehorizons.rocket.blueprint.Blueprints.ORBITAL, "orbital completa"));
        shot(10, "bp_4_complete_no_ghosts");
        step(5, mc -> com.spacehorizons.client.rocket.RocketDesignClient.pinForTest(null));
        step(5, BlueprintDevtest::assemble);
        tickerStep(10, waitFor("bp_assembled", 200));
        step(20, BlueprintDevtest::fly);
        tickerStep(5, waitFor("bp_burning", 600));
        thirdPerson();
        shot(5, "bp_5_flight");
        tickerStep(0, waitFor("bp_landed", 6_000));
        firstPerson();
        cmd(20, "tp @s " + (a.getX() + 9.5) + " " + (a.getY() + 7.0) + " " + (a.getZ() + 0.5));
        fly(5);
        look(10, 90f, 25f);
        step(30, BlueprintDevtest::checkAfterLaunch);
        shot(10, "bp_6_after_launch_hologram_again");

        // D. salvar um foguete como planta e reaplicar em outra plataforma
        cmd(10, "tp @s " + (b.getX() - 1.5) + " " + (b.getY() + 1.2) + " " + (b.getZ() - 1.5));
        fly(20);
        step(10, BlueprintDevtest::buildSource);
        tickerStep(5, waitFor("bp_source", 200));
        cmd(10, "give @s spacehorizons:rocket_blueprint");
        step(10, mc -> mc.player.getInventory().setSelectedSlot(1));
        look(5, 45f, 60f);
        step(5, mc -> useOnBlock(mc, b, Direction.UP));
        step(20, BlueprintDevtest::checkSaved);
        cmd(10, "tp @s " + (b.getX() + 14.5) + " " + (b.getY() + 9.0) + " " + (b.getZ() + 2.5));
        fly(5);
        look(10, 90f, 20f);
        shot(30, "bp_7_source_rocket");
        cmd(10, "tp @s " + (c.getX() + 0.5) + " " + (c.getY() + 1.2) + " " + (c.getZ() + 3.5));
        fly(20);
        look(5, 180f, 45f);
        step(5, mc -> useOnBlock(mc, c, Direction.UP));
        step(30, mc -> {
            com.spacehorizons.rocket.blueprint.Blueprint saved = BlueprintDevtest.held(mc);
            if (saved != null) {
                BlueprintDevtest.checkActive(mc, "planta salva reaplicada", c, saved, saved.size() - 1, 1);
            } else {
                BlueprintDevtest.check("planta salva na mão para reaplicar", false, "sem planta");
            }
        });
        cmd(10, "tp @s " + (c.getX() + 22.5) + " " + (c.getY() + 11.0) + " " + (c.getZ() + 3.0));
        fly(5);
        look(10, 90f, 16f);
        step(5, mc -> mc.player.getInventory().setSelectedSlot(8));
        step(5, mc -> com.spacehorizons.client.rocket.RocketDesignClient.pinForTest(c));
        shot(60, "bp_8_saved_blueprint_reapplied");
        step(5, mc -> {
            com.spacehorizons.rocket.blueprint.Blueprint saved = com.spacehorizons.client.rocket.blueprint.BlueprintHologram.blueprintAt(mc.level, c);
            if (saved != null) {
                BlueprintDevtest.complete(mc, c, saved, "bp_complete_c");
            } else {
                FlightDevtest.mark("bp_complete_c");
            }
        });
        tickerStep(5, waitFor("bp_complete_c", 200));
        step(40, mc -> {
            com.spacehorizons.rocket.blueprint.Blueprint saved = com.spacehorizons.client.rocket.blueprint.BlueprintHologram.blueprintAt(mc.level, c);
            if (saved != null) {
                BlueprintDevtest.checkCompleteClient(mc, c, saved, "planta salva completa");
            }
        });
        shot(10, "bp_9_saved_blueprint_complete");
        step(5, mc -> com.spacehorizons.client.rocket.RocketDesignClient.pinForTest(null));

        // E. agachado + clique tira a planta
        cmd(10, "tp @s " + (c.getX() + 0.5) + " " + (c.getY() + 1.2) + " " + (c.getZ() - 2.5));
        fly(10);
        step(5, mc -> mc.player.getInventory().setSelectedSlot(1));
        look(5, 0f, 45f);
        step(5, mc -> mc.options.keyShift.setDown(true));
        step(5, mc -> useOnBlock(mc, c, Direction.UP));
        step(5, mc -> mc.options.keyShift.setDown(false));
        step(20, mc -> BlueprintDevtest.checkCleared(mc, c));
        step(20, mc -> {
            BlueprintDevtest.summary();
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    private double shipPeBefore;

    // ===================================================================================================================
    // TIER 2 (docs/PLANO_TIERS.md, seção 9): -PshOnly=tier2. Bloco separado; as conferências ficam em Tier2Devtest.
    // Foguete nuclear (motor 2x2x2 + hidrogênio 2x2x8: barra e galão pelo clique, painel, montagem, subida curta, pouso, combustível e núcleo
    // conferidos), iônico com RTG e xenônio (painel: não decola, Δv alto; e tenta decolar) e fusão 3x3x3 (painel), e as receitas.
    // ===================================================================================================================

    private void tier2Scenario() {
        BlockPos pad = Tier2Devtest.BASE;
        double bx = pad.getX() + 0.5;
        double by = pad.getY();
        double bz = pad.getZ() + 0.5;
        cmd(5, "gamerule send_command_feedback false");
        cmd(20, "weather clear 1000000");
        cmd(10, "gamemode creative");
        cmd(5, "time set 6000");
        cmd(5, "gamerule advance_time true");
        fly(10);
        cmd(10, "clear @s");
        step(10, Tier2Devtest::recipes);

        // A. nuclear: construir, carregar uma barra e um galão pelo clique, completar a carga (perto: o alcance do clique no criativo é de 5 blocos)
        cmd(10, "tp @s " + (bx + 3.5) + " " + (by + 3.0) + " " + (bz + 1.0));
        fly(10);
        step(10, Tier2Devtest::buildNuclear);
        tickerStep(5, waitFor("t2_built_nuclear", 200));
        cmd(5, "give @s spacehorizons:nuclear_fuel_rod 1");
        cmd(5, "give @s spacehorizons:hydrogen_canister[spacehorizons:fuel_mb=1000]");
        step(10, mc -> {
            mc.player.getInventory().setSelectedSlot(0);
            useOnBlock(mc, pad.offset(1, 2, 1), Direction.EAST);
        });
        step(10, mc -> {
            mc.player.getInventory().setSelectedSlot(1);
            useOnBlock(mc, pad.offset(1, 6, 1), Direction.EAST);
        });
        step(20, Tier2Devtest::fillNuclear);
        cmd(10, "tp @s " + (bx + 14.0) + " " + (by + 7.0) + " " + (bz + 1.0));
        fly(10);
        look(10, 90f, 5f);
        step(5, mc -> Tier2Devtest.pinPanel(true));
        shot(60, "t2_nuclear_a_panel");
        step(0, mc -> Tier2Devtest.panel(mc, "nuclear"));
        step(5, mc -> Tier2Devtest.pinPanel(false));
        step(10, mc -> Tier2Devtest.assemble(mc, "nuclear"));
        tickerStep(10, waitFor("t2_assembled_nuclear", 200));
        shot(20, "t2_nuclear_b_assembled");
        step(10, mc -> Tier2Devtest.fly(mc, "nuclear"));
        tickerStep(5, waitFor("t2_burning_nuclear", 600));
        thirdPerson();
        shot(5, "t2_nuclear_c_flight");
        tickerStep(0, waitFor("t2_landed_nuclear", 6_000));
        step(20, mc -> Tier2Devtest.afterLand(mc, "nuclear"));
        tickerStep(5, waitFor("t2_land_checked_nuclear", 200));
        firstPerson();
        cmd(5, "tp @s " + (bx + 14.0) + " " + (by + 7.0) + " " + (bz + 1.0));
        look(10, 90f, 5f);
        shot(20, "t2_nuclear_d_landed_blocks");

        // B. iônico: painel (não decola na Terra, Δv alto) e a tentativa de decolar
        step(10, Tier2Devtest::buildIon);
        cmd(10, "tp @s " + (bx + 10.0) + " " + (by + 5.0) + " " + (bz + 0.5));
        fly(10);
        look(10, 90f, 5f);
        step(5, mc -> Tier2Devtest.pinPanel(true));
        shot(60, "t2_ion_a_panel");
        step(0, mc -> Tier2Devtest.panel(mc, "ion"));
        step(5, mc -> Tier2Devtest.pinPanel(false));
        step(10, mc -> Tier2Devtest.assemble(mc, "ion"));
        tickerStep(10, waitFor("t2_assembled_ion", 200));
        step(10, mc -> Tier2Devtest.fly(mc, "ion"));
        tickerStep(0, waitFor("t2_landed_ion", 2_000));
        step(10, Tier2Devtest::discardRocket);

        // C. fusão 3x3x3: um galão de hélio-3 pelo clique, a carga completa e o painel
        step(10, Tier2Devtest::buildFusion);
        tickerStep(5, waitFor("t2_built_fusion", 200));
        cmd(5, "clear @s");
        cmd(5, "give @s spacehorizons:helium3_canister[spacehorizons:fuel_mb=1000]");
        cmd(10, "tp @s " + (bx + 6.0) + " " + (by + 3.0) + " " + (bz + 0.5));
        fly(10);
        step(10, mc -> {
            mc.player.getInventory().setSelectedSlot(0);
            useOnBlock(mc, pad.offset(1, 2, 0), Direction.EAST);
        });
        step(20, Tier2Devtest::fillFusion);
        cmd(10, "tp @s " + (bx + 12.0) + " " + (by + 5.0) + " " + (bz + 0.5));
        look(10, 90f, 5f);
        step(5, mc -> Tier2Devtest.pinPanel(true));
        shot(60, "t2_fusion_a_panel");
        step(0, mc -> Tier2Devtest.panel(mc, "fusion"));
        step(5, mc -> Tier2Devtest.pinPanel(false));
        step(40, mc -> {
            Tier2Devtest.summary();
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    // ===================================================================================================================
    // CONVÉS ANDÁVEL (docs/PLANO_NAVE_ATMOSFERA.md, spike S5): -PshOnly=shipdeck. Um foguete oco de ~4.000 blocos, roteirizado (sobe, paira e desce a 1, 10,
    // 50 e 100 b/t, gira 20 graus/s e fica a 30 graus), com o jogador no referencial dele e um robô andando em quadrado por 2.000 ticks; repete com
    // 100 ms de latência por sentido. Bloco separado; as medidas ficam em ShipDeckDevtest. SH_DECK_CONFIGS (ambiente, só para depurar) troca a
    // matriz: "10:0,100:100:s" (velocidade:latência[:s com prints]).
    // ===================================================================================================================
    private void shipDeckRealScenario() {
        BlockPos b = ShipGroundDevtest.BASE;
        cmd(5, "gamerule send_command_feedback false");
        cmd(5, "gamerule show_advancement_messages false");
        cmd(5, "weather clear 1000000");
        cmd(5, "time set 6000");
        cmd(5, "difficulty peaceful");
        cmd(5, "gamemode survival");
        step(10, ShipDeckRealDevtest::build);
        // 1. montar com o jogador em pé sobre o piso: ele entra no convés
        cmd(20, "tp @s " + (b.getX() + 1.5) + " " + (b.getY() + 2.0) + " " + (b.getZ() + 0.5));
        step(20, ShipDeckRealDevtest::assembleStanding);
        tickerStep(0, waitUntil("DECKREAL: o cliente entrou no convés ao montar", 200, ShipDeckRealDevtest::aboardClient));
        step(60, mc -> ShipDeckRealDevtest.checkAssembled(mc));
        shot(5, "sdr_1_montada");
        // 2. nave pairando baixo e devagar: pula do chão e passa a estar no convés
        step(5, ShipDeckRealDevtest::spawnHover);
        step(40, ShipDeckRealDevtest::jump);
        tickerStep(0, waitUntil("DECKREAL: pulou do chão e embarcou", 80, ShipDeckRealDevtest::boardedByStepping));
        step(20, ShipDeckRealDevtest::checkStepped);
        shot(5, "sdr_2_embarcou");
        step(5, ShipDeckRealDevtest::startMoving);
        step(40, ShipDeckRealDevtest::checkMoving);
        // 3. atrito: ferro e gelo do retrato
        step(5, mc -> ShipDeckRealDevtest.startSlide(mc, 3.5, 1.5));
        tickerStep(0, waitUntil("DECKREAL: deslize no ferro medido", 600, ShipDeckRealDevtest::slideDone));
        step(2, ShipDeckRealDevtest::recordIron);
        step(5, mc -> ShipDeckRealDevtest.startSlide(mc, 3.5, 6.5));
        tickerStep(0, waitUntil("DECKREAL: deslize no gelo medido", 600, ShipDeckRealDevtest::slideDone));
        step(2, ShipDeckRealDevtest::checkIce);
        // 4. queda dentro do convés: ferro machuca, slime não
        step(5, mc -> ShipDeckRealDevtest.drop(mc, 10.5, 2.5));
        tickerStep(0, waitUntil("DECKREAL: caiu no ferro", 200, ShipDeckRealDevtest::landedAfterDrop));
        step(10, mc -> ShipDeckRealDevtest.checkDrop(mc, true, "ferro"));
        step(5, mc -> ShipDeckRealDevtest.drop(mc, 15.5, 2.5));
        tickerStep(0, waitUntil("DECKREAL: caiu no slime", 200, ShipDeckRealDevtest::landedAfterDrop));
        step(10, mc -> ShipDeckRealDevtest.checkDrop(mc, false, "slime"));
        // 5. levantar do assento da nave em voo
        step(5, ShipDeckRealDevtest::sit);
        tickerStep(0, waitUntil("DECKREAL: sentado, fora do convés", 100, ShipDeckRealDevtest::sentClient));
        step(10, ShipDeckRealDevtest::standUp);
        tickerStep(0, waitUntil("DECKREAL: em pé no convés ao levantar", 100, ShipDeckRealDevtest::standingAgain));
        step(20, ShipDeckRealDevtest::checkStandUp);
        shot(5, "sdr_3_em_pe");
        step(5, ShipDeckRealDevtest::cleanup);
        step(20, mc -> {
            ShipDevtest.summary();
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    // ===================================================================================================================
    // TROCAS SEM COSTURA (docs/PLANO_NAVE_ATMOSFERA.md, fase 2g): -PshOnly=shipseam. Mede, quadro a quadro, o salto da câmera e do casco em cada troca:
    // decolar e pousar com o jogador em pé, entrar ao pisar, entrar, sentar, levantar e sair do convés de uma nave que anda e gira, ancorar e desancorar
    // (a pé e pela ponte). As medidas ficam em ShipSeamDevtest; o critério é < 0,1 bloco e < 1 grau entre quadros seguidos.
    // ===================================================================================================================
    private void shipSeamScenario() {
        BlockPos b = ShipGroundDevtest.BASE;
        java.util.function.BiFunction<String, org.joml.Vector3d, Consumer<Minecraft>> open = (label, v) -> mc -> ShipSeamDevtest.begin(label, v, 0.0);
        org.joml.Vector3d still = new org.joml.Vector3d();
        org.joml.Vector3d slowZ = new org.joml.Vector3d(0.0, 0.0, 0.3);
        org.joml.Vector3d fastX = new org.joml.Vector3d(2.5, 0.0, 0.0);
        cmd(5, "gamerule send_command_feedback false");
        cmd(5, "gamerule show_advancement_messages false");
        cmd(5, "weather clear 1000000");
        cmd(5, "time set 6000");
        cmd(5, "difficulty peaceful");
        cmd(5, "gamemode survival");
        step(5, mc -> {
            mc.options.fov().set(90);
            ShipSeamDevtest.register();
        });
        step(10, ShipDeckRealDevtest::build);
        // 1. decolar: montar no chão pela ponte com o jogador em pé sobre o piso (ele entra no convés)
        cmd(20, "tp @s " + (b.getX() + 1.5) + " " + (b.getY() + 2.0) + " " + (b.getZ() + 0.5));
        step(40, open.apply("decolar (montar, jogador em pé)", still));
        step(1, ShipSeamDevtest::assembleStanding);
        tickerStep(0, waitUntil("SEAM: o cliente entrou no convés ao montar", 200, ShipSeamDevtest::aboard));
        step(60, ShipSeamDevtest::end);
        // 2. pousar: desmontar no chão com o jogador em pé no convés
        step(5, open.apply("pousar (desmontar, jogador em pé)", still));
        step(1, ShipSeamDevtest::landNow);
        tickerStep(0, waitUntil("SEAM: em pé no chão depois de pousar", 200, ShipSeamDevtest::standingOnGround));
        step(60, ShipSeamDevtest::end);
        // 3. nave lenta (0,3 b/t, girando 0,5 grau por tick): entrar pisando, sentar, levantar e sair
        step(10, mc -> ShipSeamDevtest.standOnGround(mc));
        step(20, mc -> ShipSeamDevtest.spawnScripted(mc, slowZ, 0.5, 40, false, 0.9, 15.0, 5.0, true));
        step(25, mc -> ShipSeamDevtest.begin("entrar pisando (nave a 0,3 b/t)", slowZ, 0.5));
        step(10, ShipSeamDevtest::jump);
        tickerStep(0, waitUntil("SEAM: embarcou ao pisar", 100, ShipSeamDevtest::boardedByStepping));
        step(60, ShipSeamDevtest::end);
        step(5, mc -> ShipSeamDevtest.begin("sentar (assento)", slowZ, 0.5));
        step(1, ShipSeamDevtest::sit);
        tickerStep(0, waitUntil("SEAM: sentado", 100, ShipSeamDevtest::sat));
        step(60, ShipSeamDevtest::end);
        step(5, mc -> ShipSeamDevtest.begin("levantar (assento)", slowZ, 0.5));
        step(1, ShipSeamDevtest::standUp);
        tickerStep(0, waitUntil("SEAM: em pé no convés ao levantar", 100, ShipSeamDevtest::standingAboard));
        step(60, ShipSeamDevtest::end);
        step(5, mc -> ShipSeamDevtest.begin("sair (comando)", slowZ, 0.5));
        step(1, ShipSeamDevtest::leaveNow);
        tickerStep(0, waitUntil("SEAM: fora do convés", 100, ShipSeamDevtest::notAboard));
        step(60, ShipSeamDevtest::end);
        step(5, ShipSeamDevtest::cleanup);
        // 4. nave rápida demais para o embarque automático (2,5 b/t), girando: entrar pelo comando, sentar, levantar e sair (informativo: o casco do vanilla,
        // que é o destino de quem sai, treme por si só 1 tick de movimento da nave)
        step(20, ShipSeamDevtest::standOnGround);
        step(20, mc -> ShipSeamDevtest.spawnScripted(mc, fastX, 0.5, 45, true, 0.0, 8.5, 2.5, true));
        step(25, mc -> ShipSeamDevtest.begin("[info 2,5 b/t] entrar (comando)", fastX, 0.5));
        tickerStep(0, waitUntil("SEAM: no convés pelo comando", 200, ShipSeamDevtest::aboard));
        step(60, ShipSeamDevtest::endInfo);
        step(5, mc -> ShipSeamDevtest.begin("[info 2,5 b/t] sentar (assento)", fastX, 0.5));
        step(1, ShipSeamDevtest::sit);
        tickerStep(0, waitUntil("SEAM: sentado", 100, ShipSeamDevtest::sat));
        step(60, ShipSeamDevtest::endInfo);
        step(5, mc -> ShipSeamDevtest.begin("[info 2,5 b/t] levantar (assento)", fastX, 0.5));
        step(1, ShipSeamDevtest::standUp);
        tickerStep(0, waitUntil("SEAM: em pé no convés ao levantar", 100, ShipSeamDevtest::standingAboard));
        step(60, ShipSeamDevtest::endInfo);
        step(5, mc -> ShipSeamDevtest.begin("[info 2,5 b/t] sair (comando)", fastX, 0.5));
        step(1, ShipSeamDevtest::leaveNow);
        tickerStep(0, waitUntil("SEAM: fora do convés", 100, ShipSeamDevtest::notAboard));
        step(60, ShipSeamDevtest::endInfo);
        step(5, ShipSeamDevtest::cleanup);
        // 5. ancorar e desancorar (a pé), montar pela ponte e ancorar sentado
        // longe da plataforma do chão (a nave de 28 blocos de altura parada a 60 do chão encostaria nela): 200 blocos a leste, em criativo para a queda
        cmd(10, "gamemode creative");
        cmd(10, "tp @s 200 120 0");
        step(160, mc -> {
        });
        cmd(5, "gamemode survival");
        step(40, mc -> {
        });
        step(10, ShipAnchorDevtest::begin);
        tickerStep(0, waitUntil("SEAM: embarcou no convés da nave da âncora", 600, ShipAnchorDevtest::aboard));
        tickerStep(0, waitUntil("SEAM: a nave parou a 60 blocos do chão", 400, ShipAnchorDevtest::scriptDone));
        step(20, ShipAnchorDevtest::stopScript);
        step(20, open.apply("ancorar (a pé no convés)", still));
        step(1, mc -> ShipAnchorDevtest.anchorNow(mc, "ancorar 1 (em pé no convés)"));
        tickerStep(0, waitUntil("SEAM: ancorou 1", 400, ShipAnchorDevtest::anchored));
        step(60, ShipSeamDevtest::end);
        step(2, mc -> ShipAnchorDevtest.checkAnchored(mc, "ancorar 1 (em pé no convés)", false));
        step(10, ShipAnchorDevtest::standAtStart);
        step(30, open.apply("desancorar (a pé volta ao convés)", still));
        step(1, ShipAnchorDevtest::unanchor);
        tickerStep(0, waitUntil("SEAM: de volta ao convés depois de desancorar", 200, ShipAnchorDevtest::aboard));
        step(60, ShipSeamDevtest::end);
        step(10, ShipAnchorDevtest::stopScript);
        step(20, mc -> ShipAnchorDevtest.anchorNow(mc, "ancorar 2"));
        tickerStep(0, waitUntil("SEAM: ancorou 2", 400, ShipAnchorDevtest::anchored));
        step(2, mc -> ShipAnchorDevtest.checkAnchored(mc, "ancorar 2", false));
        step(30, ShipAnchorDevtest::standAtBridge);
        step(20, mc -> mc.options.keyShift.setDown(true));
        step(10, open.apply("desancorar montando pela ponte", still));
        step(1, mc -> {
            ShipAnchorDevtest.clickBridge(mc);
            mc.options.keyShift.setDown(false);
        });
        step(60, ShipSeamDevtest::end);
        step(2, ShipAnchorDevtest::checkBridgeMounted);
        step(20, open.apply("ancorar (piloto sentado)", still));
        step(1, mc -> ShipAnchorDevtest.anchorNow(mc, "ancorar 3 (piloto sentado)"));
        tickerStep(0, waitUntil("SEAM: ancorou 3", 400, ShipAnchorDevtest::anchored));
        step(60, ShipSeamDevtest::end);
        step(20, mc -> {
            ShipSeamDevtest.summary();
            ShipDevtest.summary();
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    private void shipDeckScenario() {
        cmd(5, "gamerule send_command_feedback false");
        cmd(5, "gamerule show_advancement_messages false");
        cmd(5, "weather clear 1000000");
        cmd(5, "time set 6000");
        cmd(5, "difficulty peaceful");
        cmd(5, "gamemode survival");
        step(5, mc -> mc.options.fov().set(90));
        String env = System.getenv("SH_DECK_CONFIGS");
        List<int[]> configs = new ArrayList<>();
        if (env == null || env.isBlank()) {
            for (int latency : new int[] {0, 100}) {
                for (int speed : new int[] {1, 10, 50, 100}) {
                    // prints em 3a pessoa na nave a 50 b/t sem latência e a 100 b/t com latência
                    configs.add(new int[] {speed, latency, (speed == 50 && latency == 0) || (speed == 100 && latency == 100) ? 1 : 0});
                }
            }
        } else {
            for (String part : env.split(",")) {
                String[] f = part.trim().split(":");
                configs.add(new int[] {Integer.parseInt(f[0]), Integer.parseInt(f[1]), f.length > 2 ? 1 : 0});
            }
        }
        for (int[] c : configs) {
            int speed = c[0];
            int latency = c[1];
            boolean shots = c[2] == 1;
            step(10, mc -> ShipDeckDevtest.begin(mc, speed, latency));
            tickerStep(0, waitUntil("DECK: embarcou no convés e a nave chegou ao cliente", 600, ShipDeckDevtest::ready));
            tickerStep(0, deckWalk(ShipDeckDevtest.RUN_TICKS, shots ? "sdk_v" + speed + "_l" + latency : null));
            step(5, mc -> {
                ShipDeckDevtest.release(mc);
                ShipDeckDevtest.finish(mc);
            });
            step(60, mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        }
        step(20, mc -> {
            ShipDeckDevtest.summary();
            ShipDevtest.summary();
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /**
     * Porta, baú e assento com a nave em movimento (fase 2e, frente 3): {@code -PshOnly=shipdeckuse}. O roteiro inteiro está em
     * {@link ShipDeckUseDevtest#tick}; aqui só a preparação do mundo e o fim.
     */
    private void shipDeckUseScenario() {
        cmd(5, "gamerule send_command_feedback false");
        cmd(5, "gamerule show_advancement_messages false");
        cmd(5, "weather clear 1000000");
        cmd(5, "time set 6000");
        cmd(5, "difficulty peaceful");
        cmd(5, "gamemode survival");
        step(5, mc -> mc.options.fov().set(90));
        step(10, ShipDeckUseDevtest::begin);
        tickerStep(0, waitUntil("DECKUSE: embarcou no convés e a nave chegou ao cliente", 600, ShipDeckUseDevtest::ready));
        tickerStep(0, ShipDeckUseDevtest::tick);
        step(20, mc -> {
            ShipDevtest.summary();
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    // ===================================================================================================================
    // CONVÉS ANDÁVEL, DOIS CLIENTES (fase 2e): ./gradlew shipdeck2 (A = -PshOnly=shipdeck2, B = shipdeck2b). O anfitrião A roda o roteiro do shipdeck e o
    // observador B entra pela LAN e mede onde desenha A (ShipDeck2Devtest). SH_DECK_CONFIGS troca a matriz de A como no shipdeck.
    // ===================================================================================================================
    private void shipDeck2HostScenario() {
        cmd(5, "gamerule send_command_feedback false");
        cmd(5, "gamerule show_advancement_messages false");
        cmd(5, "weather clear 1000000");
        cmd(5, "time set 6000");
        cmd(5, "difficulty peaceful");
        cmd(5, "gamemode survival");
        step(5, mc -> mc.options.fov().set(90));
        step(5, ShipDeck2Devtest::hostOpen);
        tickerStep(0, waitUntil("DECK2: o observador B entrou no servidor", 6_000, ShipDeck2Devtest::observerJoined));
        String env = System.getenv("SH_DECK_CONFIGS");
        List<int[]> configs = new ArrayList<>();
        if (env == null || env.isBlank()) {
            // 1, 10 e 100 b/t sem latência e 100 b/t com 100 ms por sentido
            configs.add(new int[] {1, 0});
            configs.add(new int[] {10, 0});
            configs.add(new int[] {100, 0});
            configs.add(new int[] {100, 100});
        } else {
            for (String part : env.split(",")) {
                String[] f = part.trim().split(":");
                configs.add(new int[] {Integer.parseInt(f[0]), Integer.parseInt(f[1])});
            }
        }
        for (int i = 0; i < configs.size(); i++) {
            int index = i;
            int speed = configs.get(i)[0];
            int latency = configs.get(i)[1];
            step(10, mc -> ShipDeck2Devtest.hostBegin(mc, index, speed, latency));
            tickerStep(0, waitUntil("DECK2: embarcou no convés e a nave chegou ao cliente", 600, ShipDeckDevtest::ready));
            tickerStep(0, deckWalk(ShipDeckDevtest.RUN_TICKS, null));
            step(5, mc -> {
                ShipDeckDevtest.release(mc);
                ShipDeck2Devtest.hostStopFollow(mc);
                ShipDeckDevtest.finish(mc);
                ShipDeck2Devtest.hostEnd(mc, index);
            });
            tickerStep(0, waitUntil("DECK2: o observador mediu o cenário " + index, 1_200, mc -> ShipDeck2Devtest.acked(mc, index)));
        }
        step(20, mc -> {
            ShipDeck2Devtest.hostFinish(mc);
            ShipDeckDevtest.summary();
            ShipDevtest.summary();
        });
        step(100, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /** O observador: sem mundo próprio; entra pela LAN, segue o estado que A publica, tira os prints e mede ao fim de cada cenário. */
    private void shipDeck2ObserverScenario() {
        step(1, mc -> ShipDeck2Devtest.observerStart(mc));
        tickerStep(0, mc -> ShipDeck2Devtest.observerTick(mc, name -> grab(mc, name)));
        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM (observador)");
            mc.stop();
        });
    }

    /** O robô anda em quadrado até {@code untilTick} da corrida; com {@code shotPrefix}, tira prints em 3a e em 1a pessoa em instantes fixos. */
    private Ticker deckWalk(int untilTick, String shotPrefix) {
        return new Ticker() {
            int guard;

            @Override
            public boolean tick(Minecraft mc) {
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                boolean done = ShipDeckDevtest.walk(mc, untilTick);
                int t = ShipDeckDevtest.runTick();
                if ("shipdeck2".equals(ONLY)) {
                    ShipDeck2Devtest.hostTick(mc, t);
                }
                if (shotPrefix != null) {
                    switch (t) {
                        case 200 -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                        case 260 -> grab(mc, shotPrefix + "_3a_pessoa_t260");
                        case 650 -> grab(mc, shotPrefix + "_3a_pessoa_t650");
                        case 700 -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                        case 760 -> grab(mc, shotPrefix + "_1a_pessoa_t760");
                        case 1_050 -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                        case 1_100 -> grab(mc, shotPrefix + "_3a_pessoa_t1100");
                        case 1_500 -> grab(mc, shotPrefix + "_3a_pessoa_t1500");
                        default -> {
                        }
                    }
                }
                if (!done && ++guard > untilTick * 2 + 600) {
                    ShipDevtest.check("DECK: a corrida terminou no tempo", false, "tick da corrida " + t + " depois de " + guard + " ticks");
                    return true;
                }
                return done;
            }
        };
    }

    // ===================================================================================================================
    // MARTE (gerador de planetas por parâmetros): -PshOnly=mars. Bloco separado; buscas e conferências em MarsDevtest.
    // ===================================================================================================================

    /** Pontos achados pelas buscas do MarsDevtest durante o roteiro. */
    private int[] marsCrater;
    private int[] marsVein;
    private int[] marsVolcano;

    /**
     * Marte: vai pelo comando de dev, confere chão, gravidade e paleta, tira prints do chão de dia, do céu (meio-dia, sol
     * baixo e noite), de uma cratera vista da borda, de um veio de minério (o subsolo em volta é cavado e o minério fica), a
     * 1.000 e 5.000 de altitude (curvatura, cores e LOD do DH) e de um vulcão de cima; mede tick e geração de chunks.
     */
    private void marsScenario() {
        cmd(20, "gamerule advance_time false");
        cmd(20, "weather clear 1000000");
        cmd(20, "time set 6000");
        cmd(20, "spacehorizons goto mars");
        cmd(300, "time set 6000");
        step(100, mc -> {
            MarsDevtest.checkArrival(mc);
            MarsDevtest.logStats(mc, "chegada (300 ticks)");
        });
        // (a) chão de dia, no ponto de chegada (meio-dia local no tick 6000)
        look(10, EAST, 8f);
        shot(60, "mars_ground_e");
        look(10, 90f, 8f);
        shot(30, "mars_ground_w");
        look(10, SOUTH, 35f);
        shot(30, "mars_ground_s_down");
        // (b) céu: o sol a pino, o sol baixo da tarde e a noite
        lookAtSun(10);
        shot(30, "mars_sky_noon_sun");
        step(10, mc -> {
            long t = marsClockForSunHeight(mc, Math.toRadians(6.0));
            SpaceHorizons.LOGGER.info(TAG + "MARS sol a 6 graus na tarde em relogio={}", t);
            mc.player.connection.sendCommand("time set " + t);
        });
        look(40, 90f, 4f);
        shot(30, "mars_sky_dusk_w");
        lookAtSun(10);
        shot(20, "mars_sky_dusk_sun");
        step(10, mc -> {
            long t = Math.round(6_000 + Planets.spec(Planets.MARS).dayLengthTicks() / 2.0);
            mc.player.connection.sendCommand("time set " + t);
        });
        look(40, EAST, -50f);
        shot(30, "mars_sky_night");
        cmd(20, "time set 6000");
        step(20, mc -> {
            double[] sun = sunHere(mc);
            MarsDevtest.check("sol_a_pino_ao_meio_dia", sun != null && sun[1] > 0.95,
                    sun == null ? "sem sol" : "altura do sol=" + f(Math.toDegrees(Math.asin(sun[1]))) + " graus");
        });

        // (c) uma cratera vista da borda
        step(10, mc -> {
            marsCrater = MarsDevtest.findCrater();
            MarsDevtest.check("cratera_encontrada", marsCrater != null, marsCrater == null ? "nenhuma em 24.000 blocos"
                    : "fundo (" + marsCrater[0] + ", " + marsCrater[1] + "), borda x=" + marsCrater[2] + " y=" + marsCrater[4]);
            if (marsCrater != null) {
                mc.player.connection.sendCommand("tp @s " + marsCrater[2] + " " + (marsCrater[4] + 40) + " " + marsCrater[3]);
            }
        });
        fly(300);
        step(10, mc -> {
            if (marsCrater == null) {
                return;
            }
            // o mundo gerado tem o mesmo relevo da função (heightmap do cliente = altura analítica + 1)
            int worst = 0;
            for (int dx = -48; dx <= 48; dx += 16) {
                for (int dz = -48; dz <= 48; dz += 16) {
                    int x = marsCrater[2] + dx;
                    int z = marsCrater[3] + dz;
                    int h = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                    worst = Math.max(worst, Math.abs(h - MarsDevtest.terrain().surfaceHeight(x, z)));
                }
            }
            MarsDevtest.check("relevo_igual_ao_analitico", worst == 0, "maior diferença em 49 colunas da borda=" + worst);
            mc.player.setYRot(90f);
            mc.player.setXRot(28f);
        });
        shot(60, "mars_crater_from_rim");
        step(5, mc -> mc.player.setXRot(12f));
        shot(20, "mars_crater_wide");

        // (d) um veio de minério: cava o subsolo em volta e deixa o minério à mostra
        step(10, mc -> {
            int bx = mc.player.getBlockX();
            int bz = mc.player.getBlockZ();
            marsVein = MarsDevtest.findVein(bx, bz);
            MarsDevtest.check("veio_encontrado", marsVein != null, marsVein == null ? "nenhum perto de (" + bx + ", " + bz + ")"
                    : "(" + marsVein[0] + ", " + marsVein[1] + ", " + marsVein[2] + ")");
            if (marsVein != null) {
                mc.player.connection.sendCommand("tp @s " + marsVein[0] + " " + marsVein[1] + " " + marsVein[2]);
            }
        });
        step(100, mc -> {
            if (marsVein == null) {
                return;
            }
            int x = marsVein[0];
            int y = marsVein[1];
            int z = marsVein[2];
            mc.player.connection.sendCommand("fill " + (x - 4) + " " + (y - 3) + " " + (z - 4) + " " + (x + 4) + " " + (y + 3) + " "
                    + (z + 4) + " air replace " + MarsDevtest.subsurfaceId());
            mc.player.connection.sendCommand("effect give @s minecraft:night_vision infinite 0 true");
        });
        step(40, mc -> {
            if (marsVein == null) {
                return;
            }
            MarsDevtest.checkVein(mc, marsVein);
            mc.player.connection.sendCommand("tp @s " + (marsVein[0] - 4) + " " + (marsVein[1] - 1) + " " + (marsVein[2] - 3)
                    + " facing " + marsVein[0] + " " + marsVein[1] + " " + marsVein[2]);
        });
        shot(40, "mars_ore_vein");

        // (e) de cima: 1.000 e 5.000 de altitude sobre a chegada (malha orbital, curvatura, LOD do DH, limbo caramelo)
        cmd(20, "effect clear @s minecraft:night_vision");
        cmd(20, "tp @s 0 1063 0");
        fly(20);
        look(20, EAST, 90f);
        shot(500, "mars_alt1000_down");
        look(10, EAST, 25f);
        shot(60, "mars_alt1000_horizon");
        step(5, mc -> MarsDevtest.logStats(mc, "altitude 1000"));
        cmd(20, "tp @s 0 5063 0");
        fly(20);
        look(20, EAST, 90f);
        shot(500, "mars_alt5000_down");
        look(10, EAST, 30f);
        shot(60, "mars_alt5000_limb");
        step(5, mc -> MarsDevtest.logStats(mc, "altitude 5000"));

        // (f) um vulcão em escudo visto de cima
        step(10, mc -> {
            marsVolcano = MarsDevtest.findVolcano();
            MarsDevtest.check("vulcao_encontrado", marsVolcano != null, marsVolcano == null ? "nenhum"
                    : "topo (" + marsVolcano[0] + ", " + marsVolcano[1] + ") y=" + marsVolcano[2]);
            if (marsVolcano != null) {
                mc.player.connection.sendCommand("tp @s " + marsVolcano[0] + " " + (marsVolcano[2] + 1_500) + " " + (marsVolcano[1] - 3_000));
            }
        });
        fly(20);
        look(20, SOUTH, 40f);
        shot(500, "mars_volcano");
        step(5, mc -> MarsDevtest.logStats(mc, "vulcao"));

        step(40, mc -> {
            MarsDevtest.summary();
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /** Relógio (ticks) da tarde do dia 0 em que o sol de Marte, no ponto do jogador, está a {@code altitude} rad do horizonte. */
    private static long marsClockForSunHeight(Minecraft mc, double altitude) {
        SolarSystem.Body body = SolarSystem.skyBodyOf(Planets.MARS);
        double day = Planets.spec(Planets.MARS).dayLengthTicks();
        double target = Math.sin(altitude);
        double[] sun = new double[3];
        for (double t = 6_000; t < 6_000 + day / 2; t += 10) {
            SunModel.localDirection(body, mc.player.getX(), mc.player.getZ(), t, sun);
            if (sun[1] <= target) {
                return Math.round(t);
            }
        }
        return Math.round(6_000 + day / 4);
    }

    // ===================================================================================================================
    // ÂNCORA NO AR (docs/PLANO_NAVE_ATMOSFERA.md, spike S6): -PshOnly=shipanchor. A nave de ~4.000 blocos para a 60 blocos do chão com o jogador a pé no
    // convés, ancora (vira blocos reais no ar), o robô anda, abre o baú e liga a fornalha, desancora, voa 100 blocos e ancora de novo; depois monta pela
    // ponte e ancora com o jogador sentado, e uma nave de ~8.000 blocos mede o tempo. Bloco separado; as medidas ficam em ShipAnchorDevtest.
    // ===================================================================================================================
    private void shipAnchorScenario() {
        cmd(5, "gamerule send_command_feedback false");
        cmd(5, "gamerule show_advancement_messages false");
        cmd(5, "weather clear 1000000");
        cmd(5, "time set 6000");
        cmd(5, "difficulty peaceful");
        cmd(5, "gamemode survival");
        step(5, mc -> mc.options.fov().set(90));
        // (1) a nave desce até parar a 60 blocos do chão, com o jogador em pé no convés
        step(10, ShipAnchorDevtest::begin);
        tickerStep(0, waitUntil("ANCHOR: embarcou no convés e a nave chegou ao cliente", 600, ShipAnchorDevtest::aboard));
        tickerStep(0, waitUntil("ANCHOR: a nave parou a 60 blocos do chão", 400, ShipAnchorDevtest::scriptDone));
        step(20, ShipAnchorDevtest::stopScript);
        // (2) recusa sem volume livre
        step(10, ShipAnchorDevtest::refusalCheck);
        // (3) ancora com o jogador parado no convés
        step(20, mc -> ShipAnchorDevtest.anchorNow(mc, "ancorar 1 (em pé no convés)"));
        tickerStep(0, waitUntil("ANCHOR: ancorou 1", 400, ShipAnchorDevtest::anchored));
        step(2, mc -> ShipAnchorDevtest.checkAnchored(mc, "ancorar 1 (em pé no convés)", false));
        step(15, mc -> ShipAnchorDevtest.checkClientAfterAnchor(mc, "ancorar 1 (em pé no convés)"));
        // (4) areia, cascalho, água e mobs
        step(5, ShipAnchorDevtest::placeHazards);
        step(60, ShipAnchorDevtest::checkHazards);
        // (5) prints: de dentro e de fora
        step(5, mc -> lookAtPoint(mc, ShipAnchorDevtest.worldPoint(20.5, 6.0, 20.5)));
        shot(40, "shipanchor_01_dentro");
        cmd(5, "gamemode spectator");
        step(10, mc -> {
            Vec3 eye = ShipAnchorDevtest.outsideEye();
            mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f", eye.x, eye.y - 1.62, eye.z));
        });
        step(10, mc -> lookAtPoint(mc, ShipAnchorDevtest.anchorCenter()));
        shot(80, "shipanchor_02_fora");
        cmd(5, "gamemode survival");
        step(10, ShipAnchorDevtest::standAtStart);
        // (6) o robô anda até um baú e abre, depois liga a fornalha
        step(20, mc -> {
        });
        tickerStep(0, mc -> ShipAnchorDevtest.walkTo(mc, ShipAnchorDevtest.worldPoint(5.5, 1.0, 2.5), 300, "até o baú"));
        step(10, mc -> ShipAnchorDevtest.use(mc, ShipAnchorDevtest.CHEST_LOCAL, Direction.SOUTH, "baú"));
        step(20, ShipAnchorDevtest::checkChestOpen);
        shot(5, "shipanchor_03_bau_aberto");
        step(10, mc -> mc.player.closeContainer());
        step(5, ShipAnchorDevtest::giveFurnaceItems);
        tickerStep(10, mc -> ShipAnchorDevtest.walkTo(mc, ShipAnchorDevtest.worldPoint(13.5, 1.0, 3.5), 300, "até a fornalha"));
        step(10, mc -> ShipAnchorDevtest.use(mc, ShipAnchorDevtest.furnaceLocal(), Direction.SOUTH, "fornalha"));
        step(20, ShipAnchorDevtest::loadFurnace);
        step(10, mc -> mc.player.closeContainer());
        step(40, ShipAnchorDevtest::checkFurnaceLit);
        step(5, mc -> {
            BlockPos furnace = ShipAnchorDevtest.worldOf(ShipAnchorDevtest.furnaceLocal());
            lookAtPoint(mc, Vec3.atCenterOf(furnace).add(0.0, 0.0, 0.5));
        });
        shot(20, "shipanchor_04_fornalha_acesa");
        // (7) desancora (comando de dev: o jogador em pé volta ao convés), voa 100 blocos e ancora de novo
        step(10, ShipAnchorDevtest::standAtStart);
        step(30, ShipAnchorDevtest::unanchor);
        step(15, ShipAnchorDevtest::checkClientAfterUnanchor);
        step(5, ShipAnchorDevtest::fly);
        tickerStep(0, waitUntil("ANCHOR: voou 100 blocos", 400, ShipAnchorDevtest::scriptDone));
        step(20, ShipAnchorDevtest::stopScript);
        step(20, mc -> ShipAnchorDevtest.anchorNow(mc, "ancorar 2 (depois de voar 100 blocos)"));
        tickerStep(0, waitUntil("ANCHOR: ancorou 2", 400, ShipAnchorDevtest::anchored));
        step(2, mc -> ShipAnchorDevtest.checkAnchored(mc, "ancorar 2 (depois de voar 100 blocos)", false));
        step(15, mc -> ShipAnchorDevtest.checkClientAfterAnchor(mc, "ancorar 2 (depois de voar 100 blocos)"));
        cmd(5, "gamemode spectator");
        step(10, mc -> {
            Vec3 eye = ShipAnchorDevtest.outsideEye();
            mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f", eye.x, eye.y - 1.62, eye.z));
        });
        step(10, mc -> lookAtPoint(mc, ShipAnchorDevtest.anchorCenter()));
        shot(80, "shipanchor_05_fora_depois_de_voar");
        cmd(5, "gamemode survival");
        // (8) monta pela ponte (agachado, mão vazia) e ancora com o jogador sentado
        step(10, ShipAnchorDevtest::standAtBridge);
        step(20, mc -> mc.options.keyShift.setDown(true));
        // solta o agachar no mesmo tick do clique: segurado, o vanilla desmonta o piloto logo depois de sentar
        step(10, mc -> {
            ShipAnchorDevtest.clickBridge(mc);
            mc.options.keyShift.setDown(false);
        });
        step(20, ShipAnchorDevtest::checkBridgeMounted);
        step(20, mc -> ShipAnchorDevtest.anchorNow(mc, "ancorar 3 (piloto sentado)"));
        tickerStep(0, waitUntil("ANCHOR: ancorou 3", 400, ShipAnchorDevtest::anchored));
        step(2, mc -> ShipAnchorDevtest.checkAnchored(mc, "ancorar 3 (piloto sentado)", true));
        step(15, mc -> ShipAnchorDevtest.checkClientAfterAnchor(mc, "ancorar 3 (piloto sentado)"));
        // (9) nave grande: o tempo da conversão de ~8.000 blocos
        step(10, ShipAnchorDevtest::bigShip);
        tickerStep(0, waitUntil("ANCHOR: nave grande ancorou", 400, ShipAnchorDevtest::bigAnchored));
        step(2, ShipAnchorDevtest::checkBig);
        step(20, mc -> {
            ShipAnchorDevtest.summary();
            ShipDevtest.summary();
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    // ===================================================================================================================
    // ÂNCORA COMPLETA (docs/PLANO_NAVE_ATMOSFERA.md, fase 2f): -PshOnly=shipanchor2. O jogador pilota uma nave parada no ar e aperta a tecla de ancorar do painel:
    // sem gerador (recusa), com pedra encostada (recusa e destaque), rápido demais (recusa), pronta e ancorada pela tecla (gasta a carga do gerador), sem carga
    // depois de desancorar; o pouso antigo no ar registra a âncora e quebrar a ponte desfaz o registro. Cada estado do painel é conferido e fotografado.
    // Bloco separado; as medidas ficam em ShipAnchor2Devtest.
    // ===================================================================================================================
    private void shipAnchor2Scenario() {
        cmd(5, "gamerule send_command_feedback false");
        cmd(5, "gamerule show_advancement_messages false");
        cmd(5, "weather clear 1000000");
        cmd(5, "time set 6000");
        cmd(5, "difficulty peaceful");
        cmd(5, "gamemode survival");
        step(5, mc -> mc.options.fov().set(90));
        step(5, mc -> ShipAnchor2Devtest.shortRecharge());
        // (1) sem gerador: o painel avisa e a tecla recusa
        step(10, mc -> ShipAnchor2Devtest.spawn(mc, false, "sem gerador"));
        tickerStep(0, waitUntil("ANCHOR2: o cliente pilota a nave", 400, ShipAnchor2Devtest::piloting));
        tickerStep(0, waitUntil("ANCHOR2: o painel mostra \"falta gerador\"", 200, mc -> ShipAnchor2Devtest.statusIs(com.spacehorizons.ship.AnchorStatus.NO_GENERATOR)));
        step(5, mc -> ShipAnchor2Devtest.checkPanel("sem gerador", com.spacehorizons.ship.AnchorStatus.NO_GENERATOR));
        shot(10, "shipanchor2_01_sem_gerador");
        step(5, ShipAnchor2Devtest::pressAnchorKey);
        tickerStep(0, waitUntil("ANCHOR2: a recusa chegou (sem gerador)", 100, ShipAnchor2Devtest::refused));
        step(3, mc -> ShipAnchor2Devtest.checkRefusal(mc, "sem gerador", com.spacehorizons.ship.AnchorStatus.NO_GENERATOR, false));
        // (2) com gerador e uma pedra encostada: sem espaço livre, recusa com os blocos destacados
        step(10, mc -> ShipAnchor2Devtest.spawn(mc, true, "com gerador e pedra encostada"));
        tickerStep(0, waitUntil("ANCHOR2: o cliente pilota a nave", 400, ShipAnchor2Devtest::piloting));
        step(5, ShipAnchor2Devtest::placeStone);
        tickerStep(0, waitUntil("ANCHOR2: o painel mostra \"sem espaço livre\"", 200, mc -> ShipAnchor2Devtest.statusIs(com.spacehorizons.ship.AnchorStatus.NO_ROOM)));
        step(5, mc -> ShipAnchor2Devtest.checkPanel("sem espaço", com.spacehorizons.ship.AnchorStatus.NO_ROOM));
        step(5, ShipAnchor2Devtest::pressAnchorKey);
        tickerStep(0, waitUntil("ANCHOR2: a recusa chegou (pedra encostada)", 100, ShipAnchor2Devtest::refused));
        step(3, mc -> ShipAnchor2Devtest.checkRefusal(mc, "pedra encostada", com.spacehorizons.ship.AnchorStatus.NO_ROOM, true));
        step(2, ShipAnchor2Devtest::checkBlockersAreTheStone);
        step(5, mc -> lookAtPoint(mc, ShipAnchor2Devtest.stoneCenter()));
        shot(20, "shipanchor2_02_sem_espaco");
        // (3) a pedra sai: pronta para ancorar; a tecla ancora e gasta a carga do gerador
        step(5, ShipAnchor2Devtest::removeStone);
        tickerStep(0, waitUntil("ANCHOR2: o painel mostra \"pode ancorar\"", 200, mc -> ShipAnchor2Devtest.statusIs(com.spacehorizons.ship.AnchorStatus.READY)));
        step(5, mc -> ShipAnchor2Devtest.checkPanel("pronta", com.spacehorizons.ship.AnchorStatus.READY));
        shot(10, "shipanchor2_03_pode_ancorar");
        step(5, ShipAnchor2Devtest::pressAnchorKey);
        tickerStep(0, waitUntil("ANCHOR2: ancorou pela tecla", 600, ShipAnchor2Devtest::anchored));
        step(2, ShipAnchor2Devtest::checkAnchoredByKey);
        tickerStep(0, waitUntil("ANCHOR2: o painel mostra \"nave ancorada\"", 100, mc -> ShipAnchor2Devtest.statusIs(com.spacehorizons.ship.AnchorStatus.ANCHORED)));
        step(3, mc -> ShipAnchor2Devtest.checkPanel("ancorada", com.spacehorizons.ship.AnchorStatus.ANCHORED));
        shot(5, "shipanchor2_04_ancorada");
        // (4) desancora (o piloto senta) com o gerador sem carga: o painel diz que falta carga e a tecla recusa
        step(5, ShipAnchor2Devtest::unanchorWithPilot);
        tickerStep(0, waitUntil("ANCHOR2: o cliente pilota a nave de novo", 400, ShipAnchor2Devtest::piloting));
        tickerStep(0, waitUntil("ANCHOR2: o painel mostra \"gerador sem carga\"", 200, mc -> ShipAnchor2Devtest.statusIs(com.spacehorizons.ship.AnchorStatus.NO_ENERGY)));
        step(5, mc -> ShipAnchor2Devtest.checkPanel("sem carga", com.spacehorizons.ship.AnchorStatus.NO_ENERGY));
        shot(10, "shipanchor2_05_sem_carga");
        step(5, ShipAnchor2Devtest::pressAnchorKey);
        tickerStep(0, waitUntil("ANCHOR2: a recusa chegou (sem carga)", 100, ShipAnchor2Devtest::refused));
        step(3, mc -> ShipAnchor2Devtest.checkRefusal(mc, "sem carga", com.spacehorizons.ship.AnchorStatus.NO_ENERGY, false));
        // (5) limites do S6: o pouso antigo no ar registra a âncora (e recarrega o gerador com o bloco no mundo); quebrar a ponte desfaz o registro
        step(5, ShipAnchor2Devtest::landInAir);
        tickerStep(0, waitUntil("ANCHOR2: o gerador recarregou com a nave em blocos", 600, ShipAnchor2Devtest::generatorRecharged));
        step(5, ShipAnchor2Devtest::breakBridge);
        tickerStep(0, waitUntil("ANCHOR2: quebrar a ponte tirou a âncora do registro", 200, ShipAnchor2Devtest::anchorsGone));
        // (6) rápido demais: a nave vai e volta a 2,5 b/t
        step(10, mc -> ShipAnchor2Devtest.spawn(mc, true, "rápido demais"));
        tickerStep(0, waitUntil("ANCHOR2: o cliente pilota a nave", 400, ShipAnchor2Devtest::piloting));
        step(5, mc -> ShipAnchor2Devtest.moveEast(mc, 2.5));
        tickerStep(0, waitUntil("ANCHOR2: o painel mostra \"rápido demais\"", 200, mc -> ShipAnchor2Devtest.statusIs(com.spacehorizons.ship.AnchorStatus.TOO_FAST)));
        step(3, mc -> ShipAnchor2Devtest.checkPanel("rápido demais", com.spacehorizons.ship.AnchorStatus.TOO_FAST));
        step(2, ShipAnchor2Devtest::pressAnchorKey);
        tickerStep(0, waitUntil("ANCHOR2: a recusa chegou (rápido demais)", 100, ShipAnchor2Devtest::refused));
        step(2, mc -> ShipAnchor2Devtest.checkRefusal(mc, "rápido demais", com.spacehorizons.ship.AnchorStatus.TOO_FAST, false));
        shot(2, "shipanchor2_06_rapido_demais");
        step(20, mc -> {
            ShipAnchor2Devtest.restoreRecharge();
            ShipAnchor2Devtest.summary();
            mc.stop();
        });
    }

    // ===================================================================================================================
    // OUTRAS NAVES E FOGUETES NO CÉU (docs/PLANO_NAVES.md, 4.3, fase 2d): -PshOnly=shipsee. Uma nave em órbita (ship create flight) e um foguete estacionado no espaço a
    // 300 blocos dela, na mesma órbita. De dentro do foguete, de um jogador livre na dimensão de espaço e de dentro da nave, o outro aparece na direção, no tamanho e na atitude
    // certos (caixa na tela medida em pixels contra a calculada da mesma câmera); a nave gira e o foguete se move no céu. Bloco separado; as medidas ficam em ShipSeeDevtest.
    // ===================================================================================================================
    private ShipSeeDevtest.Setup seeSetup;
    private ShipSeeDevtest.Frame seeBase;
    private java.util.UUID seeRocketId;
    private long seePacketsStart;
    private long seeTicksStart;

    /** Uma visada: aponta a câmera para o alvo (mais um desvio), tira o quadro sem os veículos e com eles e compara a caixa que mudou com a esperada. */
    private void seeView(String name, java.util.function.Function<Minecraft, ShipSeeDevtest.Setup> setup, double yawOffset, double pitchOffset, boolean fullyVisible) {
        step(5, mc -> {
            seeSetup = setup.apply(mc);
            ShipSeeDevtest.aim(mc, ShipSeeDevtest.centerVector(seeSetup), yawOffset, pitchOffset);
        });
        step(2, mc -> com.spacehorizons.client.space.VesselRenderer.setEnabled(false));
        step(8, ShipSeeDevtest::capture);
        tickerStep(0, mc -> ShipSeeDevtest.captured());
        step(1, mc -> {
            seeBase = ShipSeeDevtest.take();
            com.spacehorizons.client.space.VesselRenderer.setEnabled(true);
        });
        step(8, mc -> {
            seeSetup = setup.apply(mc);
            ShipSeeDevtest.capture(mc);
            grab(mc, "shipsee_" + name);
        });
        tickerStep(0, mc -> ShipSeeDevtest.captured());
        step(1, mc -> ShipSeeDevtest.evaluate(mc, name, seeSetup, seeBase, ShipSeeDevtest.take(), fullyVisible));
    }

    private void shipSeeScenario() {
        String c = "SEE: ";
        java.util.UUID shipId = com.spacehorizons.space.vessel.VesselEntry.shipUuid(1);
        cmd(5, "gamerule send_command_feedback false");
        cmd(5, "gamerule show_advancement_messages false");
        cmd(5, "weather clear 1000000");
        cmd(5, "time set 6000");
        step(5, mc -> {
            mc.options.fov().set(90);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        });
        cmd(10, "spacehorizons ship create flight");
        step(60, mc -> ShipDevtest.check(c + "a nave de voo foi registrada", ShipDevtest.shipCount(mc) == 1, "naves=" + ShipDevtest.shipCount(mc)));

        // B. DE DENTRO DO FOGUETE: o foguete estaciona no espaço a 300 blocos à frente da nave (e 60 acima), o piloto vê a nave
        cmd(10, "spacehorizons vessel rocketboard 1 0 60 -300");
        cmd(1, "spacehorizons vessel freeze on");
        tickerStep(0, waitUntil(c + "no foguete estacionado no espaço, com a nave e o casco dela no cliente", 900, mc -> ShipSeeDevtest.spaceAndRiding(mc)
                && com.spacehorizons.client.space.VesselClient.count() == 1 && com.spacehorizons.client.space.VesselClient.hullReady(shipId)));
        step(10, mc -> {
            seeRocketId = ((com.spacehorizons.rocket.RocketEntity) mc.player.getVehicle()).getUUID();
            SpaceHorizons.LOGGER.info(TAG + "SEE B no foguete {}: {}", seeRocketId, ShipSeeDevtest.clientSummary());
            ShipDevtest.check(c + "B o piloto do foguete recebe só a nave (o foguete dele não vai), com o casco", com.spacehorizons.client.space.VesselClient.count() == 1
                    && com.spacehorizons.client.space.VesselClient.hullReady(shipId), ShipSeeDevtest.clientSummary());
            seePacketsStart = com.spacehorizons.client.space.VesselClient.packetCount();
            seeTicksStart = mc.level.getGameTime();
            double[] hull = ShipDevtest.onServer(mc, server -> new double[] {com.spacehorizons.ship.VesselDev.lastShipHullMs(), com.spacehorizons.ship.VesselDev.lastShipHullCells(),
                    com.spacehorizons.ship.VesselDev.lastShipHullBytes()});
            SpaceHorizons.LOGGER.info(TAG + "SEE casco da nave: {} células, {} bytes, varredura de {} ms", f(hull[1]), f(hull[2]), f(hull[0]));
            ShipDevtest.check(c + "B o casco da nave cabe num pacote simples (menos de 256 KiB) e foi lido em menos de 250 ms", hull[2] > 0 && hull[2] < 256 * 1024 && hull[0] < 250.0,
                    "bytes=" + f(hull[2]) + " ms=" + f(hull[0]));
        });
        step(5, mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
        step(100, mc -> {
            long packets = com.spacehorizons.client.space.VesselClient.packetCount() - seePacketsStart;
            long ticks = mc.level.getGameTime() - seeTicksStart;
            SpaceHorizons.LOGGER.info(TAG + "SEE taxa: {} pacotes de estado em {} ticks", packets, ticks);
            ShipDevtest.check(c + "o estado dos veículos vai a 5 Hz (a cada 4 ticks): " + packets + " pacotes em " + ticks + " ticks", ticks > 60 && Math.abs(packets - ticks / 4.0) <= 3.0,
                    "pacotes=" + packets + " ticks=" + ticks);
        });
        seeView("B1_foguete_olha_a_nave_centro", mc -> ShipSeeDevtest.fromRocket(mc, "B1", seeRocketId, shipId), -35.0, 10.0, true);
        seeView("B2_foguete_olha_a_nave_desvio", mc -> ShipSeeDevtest.fromRocket(mc, "B2", seeRocketId, shipId), 38.0, 15.0, true);
        step(5, mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));

        // C. JOGADOR LIVRE NA DIMENSÃO DE ESPAÇO: a 150 blocos da nave, vê a nave e o foguete
        step(5, mc -> {
            double[] at = ShipDevtest.onServer(mc, server -> {
                double[] off = com.spacehorizons.ship.VesselDev.yardToSpace(server, 1, 150.0, -20.0, -120.0);
                return com.spacehorizons.ship.VesselDev.spaceDimensionAt(server, shipId, off[0], off[1], off[2]);
            });
            SpaceHorizons.LOGGER.info(TAG + "SEE C jogador livre na dimensão de espaço em ({}, {}, {})", f(at[0]), f(at[1]), f(at[2]));
            mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "spacehorizons goto space %.4f %.4f %.4f", at[0], at[1], at[2]));
        });
        tickerStep(0, waitUntil(c + "C livre no espaço, vendo a nave e o foguete, com os cascos", 600, mc -> SpaceDimension.is(mc.level) && mc.player.getVehicle() == null
                && com.spacehorizons.client.space.VesselClient.count() == 2 && com.spacehorizons.client.space.VesselClient.hullReady(shipId)
                && com.spacehorizons.client.space.VesselClient.hullReady(seeRocketId)));
        step(20, mc -> {
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
            SpaceHorizons.LOGGER.info(TAG + "SEE C: {}", ShipSeeDevtest.clientSummary());
            ShipDevtest.check(c + "C o jogador livre vê os dois veículos (a nave e o foguete)", com.spacehorizons.client.space.VesselClient.count() == 2, ShipSeeDevtest.clientSummary());
        });
        seeView("C1_livre_olha_a_nave", mc -> ShipSeeDevtest.fromFree(mc, "C1", shipId), 0.0, 0.0, true);
        seeView("C2_livre_olha_o_foguete", mc -> ShipSeeDevtest.fromFree(mc, "C2", seeRocketId), 0.0, 0.0, true);
        seeView("C3_livre_olha_o_foguete_desvio", mc -> ShipSeeDevtest.fromFree(mc, "C3", seeRocketId), -16.0, 9.0, true);

        // A. DE DENTRO DA NAVE: o foguete aparece pela janela da proa, na posição relativa certa; a nave gira e ele se move no céu
        cmd(10, "spacehorizons ship goto 1");
        tickerStep(0, waitInYard(1, 700));
        tickerStep(0, waitUntil(c + "A na nave, vendo o foguete e o casco dele", 600, mc -> com.spacehorizons.client.space.VesselClient.count() == 1
                && com.spacehorizons.client.space.VesselClient.hullReady(seeRocketId)));
        step(30, mc -> {
            mc.player.getAbilities().flying = false;
            mc.player.onUpdateAbilities();
            SpaceHorizons.LOGGER.info(TAG + "SEE A na nave: {}", ShipSeeDevtest.clientSummary());
            ShipDevtest.check(c + "A quem está dentro da nave vê só o foguete (a nave dele não vai)", com.spacehorizons.client.space.VesselClient.count() == 1, ShipSeeDevtest.clientSummary());
        });
        seeView("A1_nave_olha_o_foguete_pela_janela", mc -> ShipSeeDevtest.fromShip(mc, "A1", 1, seeRocketId), 0.0, 0.0, false);
        seeView("A2_nave_olha_o_foguete_desvio", mc -> ShipSeeDevtest.fromShip(mc, "A2", 1, seeRocketId), 6.0, -3.0, false);
        // a nave gira 90 graus em torno do cima dela: o foguete, parado na órbita, passa da proa para o leste (a janela do lado)
        cmd(5, "spacehorizons vessel yaw 1 90");
        step(30, mc -> {
        });
        seeView("A3_nave_girada_90_o_foguete_no_leste", mc -> ShipSeeDevtest.fromShip(mc, "A3", 1, seeRocketId), 0.0, 0.0, false);
        cmd(5, "spacehorizons vessel yaw 1 -50");
        step(30, mc -> {
        });
        seeView("A4_nave_girada_40_o_foguete_a_nordeste", mc -> ShipSeeDevtest.fromShip(mc, "A4", 1, seeRocketId), 0.0, 0.0, false);

        cmd(5, "gamerule send_command_feedback true");
        cmd(5, "gamerule show_advancement_messages true");
        step(20, mc -> {
            ShipDevtest.summary();
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    // ===================================================================================================================
    // BASE LUNAR (docs/PLANO_SOBREVIVENCIA.md, etapa 4): -PshOnly=moonbase. Na Lua, uma sala selada de 5x3x5 com porta de ferro, gerador de oxigênio,
    // bateria, cabos e painéis no teto, e uma fábrica ao lado (eletrolisador, refinaria lunar e extrator de hélio-3 ligados a painéis e bateria). Em
    // sobrevivência com o traje: tira o capacete dentro da sala e respira sem gastar o tanque; abre a porta e a sala despressuriza; fecha e volta.
    // Confere a produção das máquinas, o painel de noite e o veio de gelo de uma cratera polar. Bloco separado; as conferências ficam em MoonbaseDevtest.
    // ===================================================================================================================
    private void moonbaseScenario() {
        cmd(5, "gamerule send_command_feedback false");
        cmd(5, "gamerule show_advancement_messages false");
        cmd(20, "weather clear 1000000");
        cmd(10, "gamemode creative");
        cmd(5, "gamerule advance_time false");
        cmd(20, "spacehorizons goto moon");
        cmd(300, "time set 6000");
        step(20, MoonbaseDevtest::build);
        step(5, mc -> mc.player.connection.sendCommand("time set " + MoonbaseDevtest.noonClock));
        step(10, mc -> mc.player.connection.sendCommand("tp @s " + MoonbaseDevtest.viewOutside()));
        fly(10);
        look(10, SOUTH, 20f);
        shot(300, "moonbase_a_overview");

        // dentro da sala, em sobrevivência com o traje completo
        cmd(5, "gamemode survival");
        step(5, mc -> mc.player.connection.sendCommand("tp @s " + MoonbaseDevtest.roomCenter()));
        step(5, MoonbaseDevtest::dressUp);
        step(20, MoonbaseDevtest::checkPressurized);
        look(5, -135f, 20f);
        shot(20, "moonbase_b_inside_suit");
        step(5, MoonbaseDevtest::removeHelmet);
        step(200, MoonbaseDevtest::checkBreathingWithoutHelmet);
        shot(10, "moonbase_c_inside_no_helmet");
        step(5, MoonbaseDevtest::checkGeneratorFeeds);
        step(40, MoonbaseDevtest::checkGeneratorIce);

        // porta aberta: a sala vaza
        look(5, 180f, 5f);
        step(5, mc -> MoonbaseDevtest.setDoor(mc, true));
        step(100, MoonbaseDevtest::checkLeak);
        shot(10, "moonbase_d_door_open");
        step(200, MoonbaseDevtest::checkSuffering);
        shot(10, "moonbase_e_vacuum_no_helmet");
        step(5, mc -> MoonbaseDevtest.setDoor(mc, false));
        step(150, MoonbaseDevtest::checkRepressurized);

        // fábrica: eletrolisador, refinaria lunar e extrator
        cmd(5, "gamemode creative");
        step(5, mc -> mc.player.connection.sendCommand("tp @s " + MoonbaseDevtest.viewFactory()));
        fly(10);
        look(10, 180f, 25f);
        step(300, MoonbaseDevtest::checkFactory);
        shot(10, "moonbase_f_factory");

        // noite: o painel não gera
        step(5, mc -> mc.player.connection.sendCommand("time set " + MoonbaseDevtest.nightClock));
        step(60, MoonbaseDevtest::checkNight);

        // gelo de uma cratera polar
        step(10, mc -> {
            int[] found = MoonbaseDevtest.findIce();
            MoonbaseDevtest.check("gelo_polar_achado", found != null, found == null ? "nenhum veio denso em 100.000 blocos" : "ok");
            if (found != null) {
                mc.player.connection.sendCommand("tp @s " + MoonbaseDevtest.iceSurfaceSpot());
            }
        });
        fly(10);
        cmd(5, "effect give @s minecraft:night_vision 999 0 true");
        step(400, mc -> {
            if (MoonbaseDevtest.iceFound()) {
                MoonbaseDevtest.carveIce(mc);
                mc.player.connection.sendCommand("tp @s " + MoonbaseDevtest.iceViewpoint());
            }
        });
        look(30, 90f, 5f);
        shot(60, "moonbase_g_lunar_ice");
        step(40, mc -> {
            MoonbaseDevtest.summary();
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    // ===================================================================================================================
    // HISTÓRIA (etapa 5, docs/PLANO_SOBREVIVENCIA.md, "Etapa 5: a trama"): -PshOnly=story, em SOBREVIVÊNCIA e com "gerar estruturas" ligado (só neste trecho).
    // Acha a estação da Terra (estrutura do vanilla), abre o baú, lê um diário e usa o terminal; vai à base lunar (desenho fixo), confere que a planta lunar
    // está trancada, usa o terminal e a destrava; vai aos destroços da sonda e lê o gancho de Marte. Confere itens, bússolas e as seis conquistas.
    // Bloco separado; as buscas e conferências ficam em StoryDevtest.
    // ===================================================================================================================
    private BlockPos storyTerminal;

    /** Espera o terminal {@code record} aparecer perto do jogador (o chunk gerado e chegado ao cliente) e guarda a posição. */
    private Ticker storyWaitTerminal(String record, int maxTicks) {
        return waitUntil("STORY terminal " + record + " apareceu", maxTicks, mc -> (storyTerminal = StoryDevtest.findTerminal(mc, record)) != null);
    }

    /** Põe o jogador a 2 blocos à frente do terminal (a tela olha para o sul) e olhando para ele. */
    private void storyStandAtTerminal(int wait) {
        step(wait, mc -> {
            BlockPos t = storyTerminal;
            mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.1f %d %.1f facing %.1f %.1f %.1f", t.getX() + 0.5, t.getY(),
                    t.getZ() + 2.5, t.getX() + 0.5, t.getY() + 0.5, t.getZ() + 0.5));
        });
    }

    /** Usa o terminal (clique direito pelo lado sul), espera a tela de leitura e confere o registro. */
    private void storyUseTerminal(String entryId, String shot) {
        step(20, mc -> rightClick(mc, storyTerminal, Direction.SOUTH, "terminal " + entryId));
        step(25, mc -> {
            boolean open = mc.gui.screen() instanceof com.spacehorizons.client.story.StoryScreen s && s.entryId().equals(entryId);
            StoryDevtest.check("terminal " + entryId + ": a tela de leitura abre", open, "tela=" + (mc.gui.screen() == null ? "nenhuma" : mc.gui.screen().getClass().getSimpleName()));
        });
        shot(5, shot);
        step(5, mc -> mc.gui.setScreen(null));
    }

    private void storyReadDiary(String entryId, String shot) {
        cmd(10, "spacehorizons story give " + entryId);
        step(15, mc -> {
            boolean held = StoryDevtest.holdFirst(mc, s -> StoryDevtest.isJournal(s, entryId));
            StoryDevtest.check("diário " + entryId + " está no inventário", held, "");
            var result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            SpaceHorizons.LOGGER.info(TAG + "STORY usou o diário {}: {}", entryId, result);
        });
        step(25, mc -> {
            boolean open = mc.gui.screen() instanceof com.spacehorizons.client.story.StoryScreen s && s.entryId().equals(entryId);
            StoryDevtest.check("diário " + entryId + ": a tela de leitura abre", open, "tela=" + (mc.gui.screen() == null ? "nenhuma" : mc.gui.screen().getClass().getSimpleName()));
        });
        shot(5, shot);
        step(5, mc -> {
            mc.gui.setScreen(null);
            StoryDevtest.check("diário " + entryId + " marcado como lido", StoryLogic.progress(mc.player).hasRead(entryId), StoryDevtest.progress(mc));
        });
    }

    private void storyScenario() {
        cmd(5, "gamerule send_command_feedback false");
        cmd(5, "gamerule show_advancement_messages false");
        cmd(5, "gamerule spacehorizons:vacuum_damage false");
        cmd(5, "weather clear 1000000");
        cmd(5, "time set 6000");
        cmd(5, "difficulty peaceful");
        step(5, mc -> mc.options.fov().set(90));
        // (1) a estação da Terra: achada pela busca de estruturas do vanilla, e o terminal e a antena existem
        cmd(10, "spacehorizons story where earth_station");
        cmd(10, "spacehorizons story goto earth_station");
        tickerStep(0, storyWaitTerminal("aurora", 2400));
        step(20, mc -> {
            StoryDevtest.check("estação da Terra: terminal aurora no mundo", storyTerminal != null, String.valueOf(storyTerminal));
            StoryDevtest.check("estação da Terra: a antena de cobre oxidado existe", StoryDevtest.findBlockOf(mc, com.spacehorizons.story.StoryStamper.state("minecraft:oxidized_copper").getBlock()) != null, "");
            mc.player.connection.sendCommand("gamemode survival");
        });
        step(20, mc -> {
            mc.player.setXRot(-8f);
            logPos(mc, "porta da estação da Terra");
        });
        shot(40, "story_01_estacao_fora");
        // (2) dentro: o baú dos diários (confere o conteúdo pela tela do baú) e o terminal
        storyStandAtTerminal(10);
        step(30, mc -> {
            mc.player.setXRot(10f);
        });
        shot(20, "story_02_estacao_dentro");
        step(10, mc -> {
            BlockPos chest = StoryDevtest.findBlockOf(mc, net.minecraft.world.level.block.Blocks.CHEST);
            StoryDevtest.check("estação da Terra: o baú existe", chest != null, String.valueOf(chest));
            if (chest != null) {
                mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.1f %d %.1f facing %.1f %.1f %.1f", chest.getX() + 0.5, chest.getY(),
                        chest.getZ() + 2.5, chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 0.5));
                storyChest = chest;
            }
        });
        step(15, mc -> {
            if (storyChest != null) {
                rightClick(mc, storyChest, Direction.SOUTH, "baú da estação");
            }
        });
        step(25, mc -> {
            var menu = mc.player.containerMenu;
            boolean a = false;
            boolean b = false;
            for (var slot : menu.slots) {
                a |= StoryDevtest.isJournal(slot.getItem(), "voss_01");
                b |= StoryDevtest.isJournal(slot.getItem(), "voss_02");
            }
            StoryDevtest.check("estação da Terra: o baú tem os dois diários de Helena Voss", a && b, "voss_01=" + a + " voss_02=" + b);
        });
        shot(5, "story_03_bau");
        step(5, mc -> mc.player.closeContainer());
        // (3) ler um diário: tela, lido, conquista
        storyReadDiary("voss_01", "story_04_diario");
        step(10, mc -> StoryDevtest.check("conquista story_observatory ao ler o diário", StoryDevtest.advancementDone(mc, "story_observatory"), ""));
        // (4) o terminal da Terra: planta orbital e bússola da base, uma vez só
        storyStandAtTerminal(10);
        storyUseTerminal("aurora_terminal", "story_05_terminal_terra");
        step(15, mc -> {
            StoryDevtest.check("terminal da Terra: planta orbital recebida", StoryDevtest.hasBlueprint(mc, "orbital"), "");
            StoryDevtest.check("terminal da Terra: bússola da base lunar recebida (dimensão moon)", StoryDevtest.hasCompassFor(mc, "moon"), "");
            StoryDevtest.check("terminal da Terra: anexo marcado como levado", StoryLogic.progress(mc.player).hasClaimed("aurora_terminal"), StoryDevtest.progress(mc));
            StoryDevtest.check("conquista story_coordinates", StoryDevtest.advancementDone(mc, "story_coordinates"), "");
            storyBlueprints = StoryDevtest.count(mc, ModItems.ROCKET_BLUEPRINT.get());
        });
        step(10, mc -> rightClick(mc, storyTerminal, Direction.SOUTH, "terminal de novo"));
        step(25, mc -> {
            mc.gui.setScreen(null);
            StoryDevtest.check("terminal da Terra: usar de novo não entrega outra planta", StoryDevtest.count(mc, ModItems.ROCKET_BLUEPRINT.get()) == storyBlueprints,
                    "plantas=" + StoryDevtest.count(mc, ModItems.ROCKET_BLUEPRINT.get()) + " antes=" + storyBlueprints);
            StoryDevtest.check("planta lunar ainda trancada (menu, servidor, sobrevivência)", !StoryDevtest.serverAcceptsBlueprint(mc, "lunar"), "");
            StoryDevtest.check("planta orbital livre no menu", StoryDevtest.serverAcceptsBlueprint(mc, "orbital"), "");
        });
        // (5) a base lunar: posição fixa, desenhada pelo gerador da Lua
        cmd(10, "spacehorizons goto moon");
        cmd(100, "spacehorizons story where moon_base");
        cmd(10, "spacehorizons story goto moon_base");
        tickerStep(0, storyWaitTerminal("selene", 2400));
        step(30, mc -> {
            StoryDevtest.check("base lunar: terminal selene no mundo", storyTerminal != null, String.valueOf(storyTerminal));
            StoryDevtest.check("base lunar: a plataforma de lançamento da expedição existe", StoryDevtest.findBlockOf(mc, ModBlocks.LAUNCH_PAD.get()) != null, "");
            StoryDevtest.check("planta lunar trancada antes do terminal (servidor)", !StoryDevtest.serverAcceptsBlueprint(mc, "lunar"), "");
            StoryDevtest.check("planta lunar trancada antes do terminal (cliente)", !StoryLogic.unlocksBlueprint(mc.player, "lunar"), "");
            mc.player.setXRot(-6f);
            logPos(mc, "porta da base lunar");
        });
        shot(60, "story_06_base_lunar_fora");
        storyStandAtTerminal(10);
        step(30, mc -> mc.player.setXRot(8f));
        shot(20, "story_07_base_lunar_dentro");
        storyReadDiary("reyes_01", "story_08_diario_lua");
        step(10, mc -> StoryDevtest.check("conquista story_selene", StoryDevtest.advancementDone(mc, "story_selene"), ""));
        storyStandAtTerminal(10);
        storyUseTerminal("selene_terminal", "story_09_terminal_base");
        step(15, mc -> {
            StoryDevtest.check("terminal da base: planta lunar recebida", StoryDevtest.hasBlueprint(mc, "lunar"), "");
            StoryDevtest.check("terminal da base: bússola da sonda recebida (dimensão moon)", StoryDevtest.hasCompassFor(mc, "moon"), "");
            StoryDevtest.check("planta lunar destravada no cliente", StoryLogic.unlocksBlueprint(mc.player, "lunar"), StoryDevtest.progress(mc));
            StoryDevtest.check("planta lunar destravada no servidor (menu, sobrevivência)", StoryDevtest.serverAcceptsBlueprint(mc, "lunar"), "");
            StoryDevtest.check("conquista story_lunar_plans", StoryDevtest.advancementDone(mc, "story_lunar_plans"), "");
        });
        // (6) os destroços da sonda e o gancho de Marte
        cmd(10, "spacehorizons story where moon_probe");
        cmd(10, "spacehorizons story goto moon_probe");
        tickerStep(0, storyWaitTerminal("vigia", 2400));
        step(30, mc -> {
            StoryDevtest.check("sonda: terminal vigia no mundo", storyTerminal != null, String.valueOf(storyTerminal));
            StoryDevtest.check("sonda: o RTG existe", StoryDevtest.findBlockOf(mc, ModBlocks.RTG_GENERATOR.get()) != null, "");
            mc.player.setXRot(-6f);
        });
        shot(60, "story_10_sonda_fora");
        storyStandAtTerminal(10);
        step(30, mc -> mc.player.setXRot(8f));
        shot(20, "story_11_sonda_dentro");
        storyUseTerminal("vigia_terminal", "story_12_terminal_sonda");
        step(15, mc -> {
            StoryDevtest.check("terminal da sonda: bússola de Marte recebida (dimensão mars)", StoryDevtest.hasCompassFor(mc, "mars"), "");
            StoryDevtest.check("conquista story_probe", StoryDevtest.advancementDone(mc, "story_probe"), "");
        });
        storyReadDiary("mars_hook", "story_13_gancho_de_marte");
        step(10, mc -> {
            for (String name : new String[] {"story_observatory", "story_coordinates", "story_selene", "story_lunar_plans", "story_probe", "story_mars"}) {
                StoryDevtest.check("conquista " + name, StoryDevtest.advancementDone(mc, name), "");
            }
            StoryDevtest.check("progresso completo", StoryLogic.progress(mc.player).claimed().size() == 3 && StoryLogic.progress(mc.player).read().size() >= 6,
                    StoryDevtest.progress(mc));
        });
        step(20, mc -> {
            StoryDevtest.summary();
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    private BlockPos storyChest;
    private int storyBlueprints;

    // ---------------------------------------------------------------- viagem a Marte (sistema solar centrado no sol)

    /**
     * Relógio (ticks do Overworld) do lançamento da viagem a Marte: a janela (Marte entre ~32 e ~69 graus à frente da Terra) abre ~8.500 ticks
     * depois do zero e fecha ~61.000; a órbita baixa sai ~900 ticks depois do lançamento, com Marte a ~60 graus, e o piloto espera o ponto
     * certo da órbita (docs/PLANO_TIERS.md, seção 11).
     */
    private static final int MARS_CLOCK = 20_000;
    /** Ticks por segundo da viagem a Marte (o voo interplanetário leva ~70.000 ticks entre a injeção e a esfera de Marte). */
    private static final int MARS_RATE = 300;

    /**
     * A VIAGEM A MARTE com o foguete de verdade (ver {@link MissionDevtest#goMars}): Terra, órbita baixa, janela e injeção, voo interplanetário
     * com correções, entrada na esfera de Marte, captura, descida pela atmosfera fina com paraquedas e pouso suave. O piloto roda no servidor a
     * {@value #MARS_RATE} ticks/s; em cada marco o ritmo volta a 20 ticks/s por um momento, para o cliente desenhar o quadro do print. Em cada
     * marco o log traz também o céu visto do foguete: distância, tamanho e fase de Marte, da Terra e da Lua.
     */
    private void marsTripScenario() {
        cmd(5, "gamerule send_command_feedback false");
        cmd(20, "weather clear 1000000");
        cmd(10, "gamemode creative");
        cmd(5, "time set " + MARS_CLOCK);
        cmd(5, "gamerule advance_time true");
        fly(10);
        step(10, mc -> FlightDevtest.build(mc, 'M'));
        tickerStep(10, waitFor("built_m", 300));
        thirdPerson();
        step(5, mc -> FlightDevtest.clientCheck(mc, "M na plataforma"));
        shot(40, "mt_01_pad");
        step(0, MissionDevtest::goMars);
        marsShot("phase_ASCENT", "mt_02_liftoff", 400);
        marsShot("phase_MARS_LEO_WAIT", "mt_03_leo", 3_000);
        marsShot("phase_MARS_TMI", "mt_04_injection_burn", 40_000);
        marsShot("phase_MARS_COAST", "mt_05_coast_start", 1_000);
        marsShot("phase_MARS_CAPTURE_COAST", "mt_06_mars_soi", 120_000);
        marsShot("phase_MARS_CAPTURE", "mt_07_capture_burn", 30_000);
        marsShot("phase_MARS_ORBIT", "mt_08_mars_orbit", 2_000);
        marsShot("phase_MARS_DESCENT", "mt_09_descent_start", 4_000);
        marsShot("phase_DONE", "mt_10_mars_landed", 20_000);
        tickerStep(0, waitFor("end", 6_000));
        step(20, mc -> FlightDevtest.clientCheck(mc, "M pousado em Marte"));
        step(0, mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        shot(20, "mt_11_mars_landed_first_person");
        cmd(2, "tick rate 20");
        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    /** O marco da viagem a Marte: como {@link #missionShot}, no ritmo da viagem e com o céu no log. */
    private void marsShot(String marker, String name, int maxTicks) {
        int[] waited = {0};
        tickerStep(0, mc -> {
            if (FlightDevtest.reached(marker) || FlightDevtest.reached("failed")) {
                return true;
            }
            if (++waited[0] > maxTicks * 20) {
                SpaceHorizons.LOGGER.error(TAG + "tempo esgotado esperando o marco '{}'", marker);
                return true;
            }
            return false;
        });
        cmd(1, "tick rate 20");
        step(50, mc -> {
            FlightDevtest.clientCheck(mc, name);
            skyBodiesLog(mc, name);
        });
        shot(5, name);
        cmd(1, "tick rate " + MARS_RATE);
    }

    /** Log do céu visto do observador agora: para cada corpo do sistema, distância ao centro, diâmetro aparente (graus) e fração iluminada. */
    private static void skyBodiesLog(Minecraft mc, String label) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        double clock = SpaceSun.clock(mc.level);
        java.util.Optional<SkyObserver> observer = SkyObserver.of(mc.level, mc.player.getX(), mc.player.getY(), mc.player.getZ(), clock);
        if (observer.isEmpty()) {
            return;
        }
        for (SolarSystem.Body body : SolarSystem.BODIES) {
            BodySky.Apparent a = observer.get().apparent(body);
            double[] sunFrom = new double[3];
            observer.get().sunFor(body, sunFrom);
            double lit = 0.5 * (1.0 - (sunFrom[0] * a.x() + sunFrom[1] * a.y() + sunFrom[2] * a.z()));
            SpaceHorizons.LOGGER.info("{}CÉU {}: {} a {} blocos, diâmetro aparente {} graus, fração iluminada {}", TAG, label, body.planet().id().getPath(),
                    String.format(java.util.Locale.ROOT, "%.0f", a.distance()), String.format(java.util.Locale.ROOT, "%.2f", Math.toDegrees(2.0 * a.angularRadius())),
                    String.format(java.util.Locale.ROOT, "%.2f", lit));
        }
    }

    /** Ticks que o foguete fica em órbita de Marte no cenário {@code marsorbit} (a amostra do pisca do planeta). */
    private static final int MARS_ORBIT_WAIT = 3_000;

    /**
     * DECOLAR DE MARTE, subir ao espaço, ficar em órbita de Marte e descer de volta (sistema solar centrado no sol), no ritmo normal de 20 ticks/s
     * (o do jogador; sem {@code /tick rate}) nas fases em que se olha o céu. Pega os quatro sintomas do relatório de um voo a partir de Marte:
     * (1) a esfera de Marte some e volta de um quadro para o outro (contador de mudanças de quadro do {@code BodyRenderer}, por fase); (2) o
     * acelerador "100% (real 0%)" com combustível sobrando (log do acelerador comandado e do aplicado a cada marco, e o piloto só passa de fase se
     * o foguete sobe); (3) a física com combustível 0 (a descida termina com combustível sobrando; o log traz TWR e velocidade); (4) a câmera
     * dentro de uma geometria cinza (prints em sequência na órbita, de fora e de dentro do foguete).
     */
    private void marsOrbitScenario() {
        cmd(5, "gamerule send_command_feedback false");
        cmd(20, "weather clear 1000000");
        cmd(10, "gamemode creative");
        cmd(5, "time set 6000");
        cmd(5, "gamerule advance_time true");
        cmd(20, "spacehorizons goto mars");
        cmd(200, "time set 6000");
        fly(10);
        step(10, mc -> FlightDevtest.build(mc, 'M'));
        tickerStep(10, waitFor("built_m", 300));
        thirdPerson();
        step(5, mc -> FlightDevtest.clientCheck(mc, "M na plataforma em Marte"));
        shot(40, "mo_01_pad");
        step(0, mc -> MissionDevtest.goMarsLaunch(mc, MARS_ORBIT_WAIT));
        step(0, AutoTest::flipsReset);
        orbitShot("phase_ASCENT", "mo_02_liftoff", 600);
        orbitShot("phase_COAST_TO_APOAPSIS", "mo_03_ascent_cutoff", 2_000);
        orbitShot("phase_CIRCULARIZE", "mo_04_circularize", 2_000);
        orbitShot("phase_MARS_ORBIT", "mo_05_orbit", 2_000);
        // a órbita estável: uma sequência de prints a cada ~1,5 s (o pisca dura de um quadro a mais de 1 s), de fora e depois de dentro
        for (int i = 0; i < 8; i++) {
            final int n = i;
            shot(25, "mo_06_orbit_seq" + i);
            step(0, mc -> {
                FlightDevtest.clientCheck(mc, "mo_06_orbit_seq" + n);
                skyBodiesLog(mc, "mo_06_orbit_seq" + n);
                flipsLog(mc, "mo_06_orbit_seq" + n);
            });
        }
        step(0, mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        for (int i = 0; i < 4; i++) {
            final int n = i;
            shot(40, "mo_07_orbit_first_person" + i);
            step(0, mc -> {
                skyBodiesLog(mc, "mo_07_orbit_first_person" + n);
                flipsLog(mc, "mo_07_orbit_first_person" + n);
            });
        }
        step(0, mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
        orbitShot("phase_MARS_BRAKE", "mo_08_brake", 6_000);
        orbitShot("phase_MARS_DESCENT", "mo_09_descent", 2_000);
        orbitShot("phase_DONE", "mo_10_landed", 6_000);
        tickerStep(0, waitFor("end", 6_000));
        step(20, mc -> {
            FlightDevtest.clientCheck(mc, "M pousado em Marte (volta)");
            flipsLog(mc, "fim");
        });
        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
    }

    private static long flipsBase;
    private static long framesBase;

    private static void flipsReset(Minecraft mc) {
        flipsBase = com.spacehorizons.client.space.BodyClient.diagFlips();
        framesBase = com.spacehorizons.client.space.BodyClient.diagFrames();
    }

    /** Quadros, mudanças de quadro (o pisca) e a máscara de corpos desenhados desde o último {@link #flipsReset}. */
    private static void flipsLog(Minecraft mc, String label) {
        SpaceHorizons.LOGGER.info("{}PISCA {}: quadros={} mudancas-do-conjunto-de-corpos={} mascara-atual={}", TAG, label,
                com.spacehorizons.client.space.BodyClient.diagFrames() - framesBase, com.spacehorizons.client.space.BodyClient.diagFlips() - flipsBase,
                Integer.toBinaryString(com.spacehorizons.client.space.BodyClient.diagMask()));
        flipsReset(mc);
    }

    /** Marco do cenário {@code marsorbit}: ritmo normal (sem {@code /tick rate}), e no marco o céu, o pisca e o acelerador no log. */
    private void orbitShot(String marker, String name, int maxTicks) {
        int[] waited = {0};
        tickerStep(0, mc -> {
            if (FlightDevtest.reached(marker) || FlightDevtest.reached("failed")) {
                return true;
            }
            if (++waited[0] > maxTicks * 20) {
                SpaceHorizons.LOGGER.error(TAG + "tempo esgotado esperando o marco '{}'", marker);
                return true;
            }
            return false;
        });
        step(5, mc -> {
            FlightDevtest.clientCheck(mc, name);
            skyBodiesLog(mc, name);
            flipsLog(mc, name);
        });
        shot(5, name);
    }
}
