package com.spacehorizons.client.devtest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.spacehorizons.SpaceHorizons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
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

    private record Step(int waitTicks, Consumer<Minecraft> action) {
    }

    /** Com -Dspacehorizons.autotest.fresh=NOME, cria um mundo novo com o tipo "Space Horizons" a partir do menu. */
    private static final String FRESH_WORLD = System.getProperty("spacehorizons.autotest.fresh");

    private final List<Step> steps = new ArrayList<>();
    private boolean freshRequested;
    private int index;
    private int countdown = 200;
    private int pushTicks;
    private double pushX;
    private double pushZ;

    private AutoTest() {
        cmd(0, "gamemode creative");
        cmd(20, "gamerule advance_time false");
        cmd(20, "time set noon");
        step(20, mc -> logPos(mc, "spawn (esperado dim=minecraft:overworld com curvatura)"));
        cmd(20, "spacehorizons goto earth");
        look(200, EAST, 15f);
        shot(20, "01_surface_horizon");

        // nuvens vistas do chão, curvando no horizonte
        cmd(20, "tp @s 0 200 0");
        fly(20);
        look(20, EAST, -8f);
        shot(300, "01b_clouds_ground");

        // subida: horizonte (em altitude alta o limbo do planeta fica bem abaixo, por isso o pitch cresce) e para baixo
        int[] alts = {600, 2000, 5000, 15000, 40000};
        float[] horizonPitch = {10f, 15f, 25f, 55f, 70f};
        float[] downPitch = {55f, 70f, 75f, 85f, 90f};
        int[] waits = {900, 700, 700, 600, 600};
        for (int i = 0; i < alts.length; i++) {
            int alt = alts[i];
            cmd(20, "tp @s 0 " + alt + " 0");
            fly(20);
            look(20, EAST, horizonPitch[i]);
            // espera o DH e a malha orbital carregarem
            shot(waits[i], String.format("%02d_alt%d_horizon", 2 + i * 2, alt));
            look(20, EAST, downPitch[i]);
            shot(60, String.format("%02d_alt%d_down", 3 + i * 2, alt));
        }

        // P1: a 600 blocos olhando para o céu (procura faixas concêntricas)
        cmd(20, "tp @s 0 600 0");
        fly(20);
        look(20, EAST, -30f);
        shot(500, "10_alt600_sky_up");
        look(20, EAST, -80f);
        shot(40, "10b_alt600_zenith");

        // P4: "continentes nadando" - mesmo pitch em x=0 e x=+3000, a 15.000 blocos
        cmd(20, "tp @s 0 15000 0");
        fly(20);
        look(20, EAST, 70f);
        shot(700, "11_p4_alt15000_x0");
        cmd(20, "tp @s 3000 15000 0");
        fly(20);
        look(20, EAST, 70f);
        shot(500, "11b_p4_alt15000_x3000");

        // P6: armor stands brilhantes no chão a 50, 100, 150 e 200 blocos à frente, vistos de ~40 blocos de altura
        cmd(20, "tp @s 0 200 0");
        fly(200);
        for (int d : new int[] {50, 100, 150, 200}) {
            cmd(20, "execute positioned " + d + " 0 0 positioned over world_surface run summon minecraft:armor_stand ~ ~ ~ "
                    + "{Glowing:1b,NoGravity:1b,ShowArms:1b}");
        }
        cmd(40, "execute positioned 0 0 0 positioned over world_surface run tp @s ~ ~40 ~");
        fly(20);
        look(20, EAST, 12f);
        shot(200, "12_entities_row_40up");
        look(20, EAST, 35f);
        shot(40, "12b_entities_row_down");
        // um pouco mais perto, para ver o assentamento no chão
        cmd(20, "execute positioned 90 0 0 positioned over world_surface run tp @s ~ ~12 ~");
        fly(20);
        look(20, EAST, 8f);
        shot(120, "12c_entities_row_low");
        cmd(20, "kill @e[type=minecraft:armor_stand]");

        // P5: nuvens distantes vistas de baixo e de cima da camada
        cmd(20, "tp @s 0 120 0");
        fly(20);
        look(20, EAST, -12f);
        shot(300, "13_clouds_low");
        cmd(20, "tp @s 0 300 0");
        fly(20);
        look(20, EAST, 4f);
        shot(300, "13b_clouds_above");

        cmd(20, "tp @s 32740 140 0");
        fly(20);
        look(20, EAST, 10f);
        shot(200, "06_before_seam");
        step(20, mc -> logPos(mc, "antes da costura"));
        step(0, mc -> push(1.0, 0.0, 60));
        step(80, mc -> logPos(mc, "depois de empurrar 60 ticks para leste (esperado x perto de -32768+)"));
        shot(100, "07_after_seam");

        // borda entre chunks vanilla e LODs do DH, vista de perto
        cmd(20, "tp @s 0 90 0");
        fly(20);
        look(20, EAST, 8f);
        shot(200, "08a_chunk_lod_border");

        // chega no polo por cima: a parede de gelo é alta
        cmd(20, "tp @s 0 330 12600");
        fly(20);
        look(20, SOUTH, 35f);
        shot(200, "08_near_pole");
        step(0, mc -> push(0.0, 1.0, 120));
        step(140, mc -> logPos(mc, "depois de empurrar 120 ticks para o sul (esperado z <= ~12741)"));
        shot(20, "09_pole_barrier");

        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
        });
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
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || index >= steps.size()) {
            return;
        }
        if (pushTicks > 0) {
            player.setDeltaMovement(pushX, 0.0, pushZ);
            pushTicks--;
        }
        if (--countdown > 0) {
            return;
        }
        Step step = steps.get(index++);
        try {
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
        steps.add(new Step(wait, action));
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

    private void shot(int wait, String name) {
        step(wait, mc -> {
            logPos(mc, "print " + name);
            Screenshot.grab(mc.gameDirectory, "autotest_" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                    msg -> SpaceHorizons.LOGGER.info(TAG + "print salvo: {}", msg.getString()));
        });
    }

    private static void logPos(Minecraft mc, String label) {
        LocalPlayer p = mc.player;
        SpaceHorizons.LOGGER.info(TAG + "{}: dim={} x={} y={} z={}", label,
                mc.level.dimension().identifier(), String.format("%.2f", p.getX()), String.format("%.2f", p.getY()),
                String.format("%.2f", p.getZ()));
    }
}
