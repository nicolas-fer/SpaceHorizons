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

    /** Passo que roda por vários ticks; devolve true quando termina. */
    private interface Ticker {
        boolean tick(Minecraft mc);
    }

    private record Step(int waitTicks, Consumer<Minecraft> action, Ticker ticker) {
    }

    /** Com -Dspacehorizons.autotest.fresh=NOME, cria um mundo novo com o tipo "Space Horizons" a partir do menu. */
    private static final String FRESH_WORLD = System.getProperty("spacehorizons.autotest.fresh");

    private final List<Step> steps = new ArrayList<>();
    private boolean freshRequested;
    private int index;
    private Ticker active;
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

        // (a) do chão do spawn, olhando para o zênite: a Lua deve ser uma esfera, não a lua vanilla
        look(300, EAST, -90f);
        shot(40, "p3_01_ground_zenith");
        look(20, EAST, -60f);
        shot(40, "p3_01b_ground_up60");
        look(20, EAST, 15f);
        shot(40, "p3_01c_ground_horizon");
        // sobe um pouco para ver a Lua sem o chão no caminho
        cmd(20, "tp @s 0 2000 0");
        fly(20);
        look(20, EAST, -90f);
        shot(300, "p3_02_alt2000_zenith");

        // (b), (c) viagem Terra -> Lua pelo piloto automático, com prints rápidos em volta da transferência
        cmd(20, "tp @s 0 200 0");
        fly(20);
        look(40, EAST, -90f);
        cmd(5, "spacehorizons fly moon");
        travel("tr_out", "moon", 36000, 6000);
        step(20, mc -> logPos(mc, "chegada na Lua"));
        // (d) chegada: crateras, horizonte e a Terra no céu
        look(300, EAST, 90f);
        shot(60, "p3_10_moon_down");
        look(20, EAST, 60f);
        shot(40, "p3_10b_moon_down60");
        look(20, EAST, 5f);
        shot(40, "p3_11_moon_horizon_e");
        look(20, 90f, 5f);
        shot(40, "p3_11b_moon_horizon_w");
        look(20, EAST, -90f);
        shot(40, "p3_12_moon_earth_zenith");
        look(20, EAST, -50f);
        shot(40, "p3_12b_moon_sky_up50");

        // (e) chão da Lua: pulo e altura máxima (gravidade baixa)
        cmd(20, "spacehorizons goto moon");
        look(600, EAST, 15f);
        shot(60, "p3_20_moon_ground");
        look(20, EAST, -60f);
        shot(40, "p3_20b_moon_ground_sky");
        look(10, EAST, 10f);
        jumpTest();
        shot(10, "p3_21_after_jump");

        // (f) volta para a Terra
        cmd(20, "tp @s ~ ~300 ~");
        fly(20);
        look(40, EAST, -90f);
        cmd(5, "spacehorizons fly earth");
        travel("tr_back", "overworld", 8500, 6000);
        step(20, mc -> logPos(mc, "chegada na Terra"));
        look(300, EAST, 90f);
        shot(60, "p3_30_earth_down");
        look(20, EAST, 10f);
        shot(40, "p3_31_earth_horizon");
        look(20, EAST, -90f);
        shot(40, "p3_32_earth_zenith");

        // regressão resumida da fase 2 (altitudes e costura em X); acima de ~38.000 blocos a esfera de influência da Lua assume
        int[] alts = {2000, 30000};
        float[] horizonPitch = {15f, 70f};
        float[] downPitch = {70f, 90f};
        int[] waits = {700, 600};
        for (int i = 0; i < alts.length; i++) {
            int alt = alts[i];
            cmd(20, "tp @s 0 " + alt + " 0");
            fly(20);
            look(20, EAST, horizonPitch[i]);
            shot(waits[i], String.format("r_alt%d_horizon", alt));
            look(20, EAST, downPitch[i]);
            shot(60, String.format("r_alt%d_down", alt));
        }
        cmd(20, "tp @s 32740 140 0");
        fly(20);
        look(20, EAST, 10f);
        shot(200, "r_before_seam");
        step(0, mc -> push(1.0, 0.0, 60));
        step(80, mc -> logPos(mc, "depois de empurrar 60 ticks para leste (esperado x perto de -32768+)"));
        shot(100, "r_after_seam");

        step(40, mc -> {
            SpaceHorizons.LOGGER.info(TAG + "FIM");
            mc.stop();
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

            @Override
            public boolean tick(Minecraft mc) {
                LocalPlayer p = mc.player;
                n++;
                String dim = mc.level.dimension().identifier().getPath();
                double speed = hasLast ? Math.sqrt((p.getX() - lx) * (p.getX() - lx) + (p.getY() - ly) * (p.getY() - ly)
                        + (p.getZ() - lz) * (p.getZ() - lz)) : 0.0;
                lx = p.getX();
                ly = p.getY();
                lz = p.getZ();
                hasLast = true;
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

    private void shot(int wait, String name) {
        step(wait, mc -> {
            logPos(mc, "print " + name);
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
