package com.spacehorizons.client.devtest;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import org.joml.Vector3d;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.ship.DeckClient;
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.content.ModEntities;
import com.spacehorizons.rocket.FlightMode;
import com.spacehorizons.rocket.PortraitGrid;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketException;
import com.spacehorizons.rocket.RocketPortrait;
import com.spacehorizons.ship.ShipAssembler;
import com.spacehorizons.ship.deck.DeckCrew;
import com.spacehorizons.ship.deck.DeckFrame;
import com.spacehorizons.ship.deck.DeckState;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Autoteste do convés na nave real (fase 2e, frente 2): {@code -PshOnly=shipdeckreal}. Um jogador só, em sobrevivência, em pé:
 * <ol>
 *   <li>a nave de teste é montada no chão pela ponte COM o jogador em pé sobre o piso dela: ele entra no convés na mesma posição relativa;</li>
 *   <li>uma nave pairando baixo e devagar (entidade roteirizada em modo de voo): o jogador pula do chão e passa a estar no convés;</li>
 *   <li>atrito: a distância de parada depois de um empurrão é muito maior no gelo do retrato que no ferro (o bloco sob o pé é o do retrato);</li>
 *   <li>queda dentro do convés: o dano segue o vanilla em coordenadas locais (ferro machuca, slime não);</li>
 *   <li>levantar do assento da nave em voo deixa o jogador em pé no convés.</li>
 * </ol>
 * O trecho da subida, da descida e do pouso com o tripulante em pé está no {@code shipascent} e no {@code shipdescent}.
 */
final class ShipDeckRealDevtest {
    private static final String TAG = "[AUTOTEST] SHIPDECKREAL ";
    private static final BlockPos BASE = ShipGroundDevtest.BASE;

    /** Tamanho da nave pairando: 30 x 8 x 10, piso (y = 0) de ferro com uma faixa de gelo e um canteiro de slime. */
    private static final int SX = 30;
    private static final int SY = 8;
    private static final int SZ = 10;
    /** Topo do piso no referencial local. */
    private static final double FLOOR_TOP = 1.0;
    /** Onde o jogador pula: faixa de ferro, no meio (x e z locais). */
    private static final double JUMP_X = 8.5;
    private static final double JUMP_Z = 2.5;
    /** O quanto o topo do piso fica acima do chão no pairar: abaixo da altura do pulo (1,25). */
    private static final double HOVER_ABOVE_GROUND = 0.9;
    private static final double SLOW_SPEED = 0.3;

    private static boolean registered;
    private static RocketEntity landedShip;
    private static RocketEntity hover;
    private static HoverScript script;
    private static Vector3d expectedLocal;
    private static long fallDamages0;

    // ---- medida do deslize ----
    private enum Slide {
        IDLE, SETTLE, RUN, DONE
    }

    private static volatile Slide slide = Slide.IDLE;
    private static int slideTicks;
    private static int settleTicks;
    private static double slideStartX;
    private static double slideDistance;
    private static double slideTargetX;
    private static double slideTargetZ;

    private ShipDeckRealDevtest() {
    }

    private static void check(String name, boolean ok, String detail) {
        ShipDevtest.check("DECKREAL " + name, ok, detail);
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private static <T> T onServer(Minecraft mc, java.util.function.Function<ServerPlayer, T> body) {
        return ShipDevtest.onServer(mc, server -> body.apply(server.getPlayerList().getPlayers().get(0)));
    }

    // ============================================================================================================ 1. montar com o jogador em pé

    static void build(Minecraft mc) {
        ShipGroundDevtest.build(mc, 0);
    }

    /** O ponto onde o jogador fica em pé sobre o piso da nave no chão (o bloco (1, 1, 0) é casco; a ponte é a (0, 1, 0)). */
    static Vec3 groundStand() {
        return new Vec3(BASE.getX() + 1.5, BASE.getY() + 2.0, BASE.getZ() + 0.5);
    }

    /** Monta a nave pela ponte com o jogador em pé sobre o piso: ele não senta (o assembler é "null") e entra no convés. */
    static void assembleStanding(Minecraft mc) {
        registerListener();
        Vec3 stand = groundStand();
        String result = onServer(mc, player -> {
            ServerLevel level = (ServerLevel) player.level();
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.setHealth(player.getMaxHealth());
            fallDamages0 = DeckCrew.FALL_DAMAGES.get();
            try {
                landedShip = ShipAssembler.assemble(level, BASE.offset(0, 1, 0), null);
            } catch (RocketException e) {
                return "recusada: " + e.getMessage();
            }
            DeckFrame frame = landedShip.deckFrame();
            expectedLocal = frame.toLocal(stand.x, stand.y, stand.z);
            return "ok";
        });
        check("montagem com o jogador em pé", "ok".equals(result), result);
    }

    static boolean aboardClient(Minecraft mc) {
        return DeckClient.isAboard() && DeckClient.rocket() != null;
    }

    static void checkAssembled(Minecraft mc) {
        double[] info = onServer(mc, player -> {
            DeckCrew.Member m = DeckCrew.member(player.getUUID());
            if (m == null) {
                return new double[] {0.0};
            }
            DeckState s = m.state();
            return new double[] {1.0, s.pos.x, s.pos.y, s.pos.z, player.getHealth(), DeckCrew.FALL_DAMAGES.get() - fallDamages0, player.getVehicle() == null ? 0.0 : 1.0};
        });
        check("montou em pé: o jogador é tripulante (embarque automático da montagem)", info[0] == 1.0, "tripulante " + (info[0] == 1.0));
        if (info[0] != 1.0 || expectedLocal == null) {
            return;
        }
        double dx = Math.hypot(info[1] - expectedLocal.x, info[3] - expectedLocal.z);
        check("mesma posição relativa à nave (xz) que tinha antes de montar", dx < 0.05, "esperada (" + f(expectedLocal.x) + ", " + f(expectedLocal.z) + "), no convés ("
                + f(info[1]) + ", " + f(info[3]) + "), diferença " + f(dx));
        check("pés no topo do piso do retrato", Math.abs(info[2] - FLOOR_TOP) < 0.01, "y local " + f(info[2]) + " (piso " + f(FLOOR_TOP) + ")");
        check("sem dano de queda ao montar", info[4] >= 20.0 && info[5] == 0.0, "vida " + f(info[4]) + ", danos de queda " + (long) info[5]);
        check("em pé (não sentado)", info[6] == 0.0, "veículo " + (info[6] != 0.0));
        check("cliente no referencial da nave, no chão do convés", DeckClient.isAboard() && Math.abs(DeckClient.state().pos.y - FLOOR_TOP) < 0.01,
                "y local do cliente " + f(DeckClient.state().pos.y));
    }

    // ============================================================================================================ 2. nave pairando: pular do chão

    /** O roteiro da nave pairando: parada num ponto, depois a {@link #SLOW_SPEED} em z; o modo de voo é posto uma vez. */
    private static final class HoverScript implements Consumer<RocketEntity> {
        double x;
        double y;
        double z;
        volatile boolean moving;
        int movedTicks;

        @Override
        public void accept(RocketEntity rocket) {
            if (this.moving) {
                this.z += SLOW_SPEED;
                this.movedTicks++;
            }
            rocket.setPos(this.x, this.y, this.z);
            rocket.setDeltaMovement(0.0, 0.0, this.moving ? SLOW_SPEED : 0.0);
        }
    }

    static RocketPortrait hoverPortrait() {
        PortraitGrid grid = PortraitGrid.empty(SX, SY, SZ);
        List<BlockState> palette = List.of(Blocks.AIR.defaultBlockState(), Blocks.IRON_BLOCK.defaultBlockState(), Blocks.PACKED_ICE.defaultBlockState(),
                Blocks.SLIME_BLOCK.defaultBlockState(), ModBlocks.ROCKET_COCKPIT.get().defaultBlockState());
        for (int x = 0; x < SX; x++) {
            for (int z = 0; z < SZ; z++) {
                // z 0..4 ferro, z 5..9 gelo; um canteiro de slime no ferro (x 14..16)
                grid.set(x, 0, z, z >= 5 ? 2 : (x >= 14 && x <= 16 ? 3 : 1));
            }
        }
        grid.set(3, 1, 4, 4);
        return new RocketPortrait(UUID.randomUUID(), grid, palette, new Long2ObjectLinkedOpenHashMap<>(), new BlockPos(3, 1, 4));
    }

    /** Gera a nave pairando baixo sobre a plataforma, com o piso a {@link #HOVER_ABOVE_GROUND} do chão sobre o ponto do pulo, e põe o jogador no chão ali. */
    static void spawnHover(Minecraft mc) {
        if (landedShip != null && !landedShip.isRemoved()) {
            ShipDevtest.onServer(mc, server -> {
                landedShip.discard();
                return null;
            });
        }
        String result = onServer(mc, player -> {
            ServerLevel level = (ServerLevel) player.level();
            RocketEntity rocket = ModEntities.ROCKET.get().create(level, EntitySpawnReason.TRIGGERED);
            if (rocket == null) {
                return "sem entidade";
            }
            rocket.setPortrait(hoverPortrait());
            rocket.setFuelMb(0.0);
            double groundY = BASE.getY() + 1.0;
            double jumpWorldX = BASE.getX() + 6.5;
            double jumpWorldZ = BASE.getZ() + 0.5;
            // o ponto do pulo no mundo com a nave no centro (0, 0, 0): a atitude é a identidade, então só soma
            rocket.setPos(0.0, 0.0, 0.0);
            Vector3d w0 = rocket.deckFrame().toWorld(JUMP_X, FLOOR_TOP, JUMP_Z);
            script = new HoverScript();
            script.x = jumpWorldX - w0.x;
            script.y = groundY + HOVER_ABOVE_GROUND - w0.y;
            script.z = jumpWorldZ - w0.z;
            rocket.setPos(script.x, script.y, script.z);
            rocket.setDevtestScript(script);
            rocket.flight().setMode(FlightMode.FLIGHT);
            level.addFreshEntity(rocket);
            hover = rocket;
            player.teleportTo(jumpWorldX, groundY, jumpWorldZ);
            player.setDeltaMovement(Vec3.ZERO);
            player.setHealth(player.getMaxHealth());
            fallDamages0 = DeckCrew.FALL_DAMAGES.get();
            return "ok";
        });
        check("nave pairando criada em modo de voo", "ok".equals(result), result);
    }

    /** O jogador pula (a velocidade vertical do pulo do vanilla) ali no chão, sob o piso da nave que paira. */
    static void jump(Minecraft mc) {
        LocalPlayerAccess.jump(mc);
    }

    private static final class LocalPlayerAccess {
        static void jump(Minecraft mc) {
            var p = mc.player;
            boolean crew = DeckClient.isAboard();
            check("antes do pulo o jogador está no chão, fora do convés", !crew && p.onGround(), "no convés " + crew + ", no chão " + p.onGround());
            p.setDeltaMovement(0.0, 0.42, 0.0);
            p.setOnGround(false);
        }
    }

    static boolean boardedByStepping(Minecraft mc) {
        boolean server = onServer(mc, player -> DeckCrew.isCrew(player));
        return server && DeckClient.isAboard() && DeckClient.rocket() != null;
    }

    static void checkStepped(Minecraft mc) {
        double[] info = onServer(mc, player -> {
            DeckCrew.Member m = DeckCrew.member(player.getUUID());
            DeckState s = m == null ? new DeckState() : m.state();
            return new double[] {m == null ? 0.0 : 1.0, s.pos.x, s.pos.y, s.pos.z, player.getHealth(), DeckCrew.FALL_DAMAGES.get() - fallDamages0, player.getY()};
        });
        check("pulou do chão e passou a estar no convés (embarque ao pisar)", info[0] == 1.0, "tripulante " + (info[0] == 1.0));
        check("pés no topo do piso do retrato depois de embarcar", Math.abs(info[2] - FLOOR_TOP) < 0.01, "y local " + f(info[2]));
        check("mesmo ponto do pulo (xz)", Math.hypot(info[1] - JUMP_X, info[3] - JUMP_Z) < 1.0, "local (" + f(info[1]) + ", " + f(info[3]) + "), pulo (" + JUMP_X + ", " + JUMP_Z + ")");
        check("sem dano de queda ao embarcar", info[4] >= 20.0 && info[5] == 0.0, "vida " + f(info[4]));
        check("o jogador no mundo está sobre o piso da nave (sem saltar para o convés)", Math.abs(info[6] - (BASE.getY() + 1.0 + HOVER_ABOVE_GROUND)) < 0.05,
                "y no mundo " + f(info[6]) + " (piso " + f(BASE.getY() + 1.0 + HOVER_ABOVE_GROUND) + ")");
    }

    /** A nave começa a andar devagar; depois de {@code ticks} ticks o jogador está no mesmo ponto local, acompanhando-a. */
    static void startMoving(Minecraft mc) {
        script.moving = true;
    }

    static void checkMoving(Minecraft mc) {
        double[] info = onServer(mc, player -> {
            DeckCrew.Member m = DeckCrew.member(player.getUUID());
            DeckState s = m == null ? new DeckState() : m.state();
            return new double[] {m == null ? 0.0 : 1.0, s.pos.x, s.pos.y, s.pos.z, player.getZ() - (BASE.getZ() + 0.5), script.movedTicks, player.getHealth()};
        });
        check("seguiu a bordo com a nave andando", info[0] == 1.0, "tripulante " + (info[0] == 1.0));
        check("a nave andou e o jogador foi junto (z no mundo)", info[4] > 0.8 * SLOW_SPEED * info[5], "andou " + f(info[4]) + " blocos em " + (int) info[5] + " ticks");
        check("parado no mesmo ponto local, no piso", Math.hypot(info[1] - JUMP_X, info[3] - JUMP_Z) < 1.0 && Math.abs(info[2] - FLOOR_TOP) < 0.01,
                "local (" + f(info[1]) + ", " + f(info[2]) + ", " + f(info[3]) + ")");
        script.moving = false;
    }

    // ============================================================================================================ 3. atrito

    private static void registerListener() {
        if (registered) {
            return;
        }
        registered = true;
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> onClientTick());
    }

    /** Leva o jogador (servidor) a um ponto local do convés, sem velocidade, e arma a medida do deslize (empurrão de 0,4 b/t em +x depois de assentar). */
    static void startSlide(Minecraft mc, double localX, double localZ) {
        registerListener();
        slideTargetX = localX;
        slideTargetZ = localZ;
        slide = Slide.SETTLE;
        settleTicks = 0;
        slideTicks = 0;
        slideDistance = 0.0;
        onServer(mc, player -> DeckCrew.devtestRelocate(player, localX, FLOOR_TOP, localZ, true));
    }

    private static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !DeckClient.isAboard()) {
            return;
        }
        DeckState s = DeckClient.state();
        if (slide == Slide.SETTLE) {
            boolean there = Math.hypot(s.pos.x - slideTargetX, s.pos.z - slideTargetZ) < 0.05 && mc.player.onGround() && mc.player.getDeltaMovement().horizontalDistance() < 0.01;
            settleTicks = there ? settleTicks + 1 : 0;
            if (settleTicks >= 10) {
                slideStartX = s.pos.x;
                mc.player.setDeltaMovement(0.4, 0.0, 0.0);
                slide = Slide.RUN;
                slideTicks = 0;
            }
        } else if (slide == Slide.RUN) {
            slideTicks++;
            slideDistance = s.pos.x - slideStartX;
            if (mc.player.getDeltaMovement().horizontalDistance() < 0.003 || slideTicks > 300) {
                slide = Slide.DONE;
            }
        }
    }

    static boolean slideDone(Minecraft mc) {
        return slide == Slide.DONE;
    }

    private static double ironSlide;

    static void recordIron(Minecraft mc) {
        ironSlide = slideDistance;
        BlockState below = Blocks.IRON_BLOCK.defaultBlockState();
        SpaceHorizons.LOGGER.info(TAG + "deslize no {}: {} blocos em {} ticks", below.getBlock().getName().getString(), f(ironSlide), slideTicks);
    }

    static void checkIce(Minecraft mc) {
        double ice = slideDistance;
        SpaceHorizons.LOGGER.info(TAG + "deslize no gelo: {} blocos em {} ticks (ferro: {} blocos)", f(ice), slideTicks, f(ironSlide));
        check("atrito do ferro: o jogador para logo (< 1,5 bloco)", ironSlide > 0.3 && ironSlide < 1.5, "parou em " + f(ironSlide) + " blocos");
        check("atrito do gelo do retrato: escorrega muito mais que no ferro", ice > 2.5 * ironSlide && ice > 2.0, "gelo " + f(ice) + " blocos, ferro " + f(ironSlide) + " blocos, razão "
                + f(ice / Math.max(1.0e-6, ironSlide)));
    }

    // ============================================================================================================ 4. queda no convés

    private static double healthBefore;

    /** Larga o jogador a 6 blocos do piso (y local 7) sobre o ponto {@code (x, z)}: ele cai no ferro ou no slime do retrato. */
    static void drop(Minecraft mc, double localX, double localZ) {
        onServer(mc, player -> {
            player.setHealth(player.getMaxHealth());
            healthBefore = player.getHealth();
            fallDamages0 = DeckCrew.FALL_DAMAGES.get();
            return DeckCrew.devtestRelocate(player, localX, FLOOR_TOP + 6.0, localZ, false);
        });
    }

    static boolean landedAfterDrop(Minecraft mc) {
        return DeckClient.isAboard() && Math.abs(DeckClient.state().pos.y - FLOOR_TOP) < 0.01 && Minecraft.getInstance().player.onGround();
    }

    static void checkDrop(Minecraft mc, boolean expectDamage, String what) {
        double[] info = onServer(mc, player -> new double[] {player.getHealth(), DeckCrew.FALL_DAMAGES.get() - fallDamages0});
        if (expectDamage) {
            check("queda de 6 blocos no " + what + " do convés machuca (vanilla em coordenadas locais)", info[0] < healthBefore && info[1] >= 1.0,
                    "vida " + f(healthBefore) + " -> " + f(info[0]) + ", danos " + (long) info[1]);
        } else {
            check("queda de 6 blocos no " + what + " do convés não machuca", info[0] >= healthBefore && info[1] == 0.0, "vida " + f(healthBefore) + " -> " + f(info[0]));
        }
    }

    // ============================================================================================================ 5. levantar do assento

    /** Senta o jogador na nave pairando (como o clique no cockpit) e confere que ele deixou o convés. */
    static void sit(Minecraft mc) {
        boolean sat = onServer(mc, player -> {
            player.setDeltaMovement(Vec3.ZERO);
            return player.startRiding(hover, true, true);
        });
        check("sentou na nave pairando", sat, "sentou " + sat);
    }

    static boolean sentClient(Minecraft mc) {
        return !DeckClient.isAboard() && Minecraft.getInstance().player.isPassenger();
    }

    /** Levanta do assento: a nave está em voo, então o jogador fica em pé no convés. */
    static void standUp(Minecraft mc) {
        onServer(mc, player -> {
            player.stopRiding();
            return null;
        });
    }

    static boolean standingAgain(Minecraft mc) {
        return onServer(mc, player -> DeckCrew.isCrew(player)) && DeckClient.isAboard();
    }

    static void checkStandUp(Minecraft mc) {
        double[] info = onServer(mc, player -> {
            DeckCrew.Member m = DeckCrew.member(player.getUUID());
            DeckState s = m == null ? new DeckState() : m.state();
            return new double[] {m == null ? 0.0 : 1.0, s.pos.x, s.pos.y, s.pos.z, player.getVehicle() == null ? 0.0 : 1.0};
        });
        boolean onTop = Math.abs(info[2] - FLOOR_TOP) < 0.02 || Math.abs(info[2] - (FLOOR_TOP + 1.0)) < 0.02;
        check("levantar do assento em voo: tripulante em pé no convés", info[0] == 1.0 && info[4] == 0.0, "tripulante " + (info[0] == 1.0) + ", sentado " + (info[4] != 0.0));
        check("em pé sobre um sólido do retrato (piso ou cockpit)", onTop, "y local " + f(info[2]) + " em (" + f(info[1]) + ", " + f(info[3]) + ")");
    }

    static void cleanup(Minecraft mc) {
        ShipDevtest.onServer(mc, server -> {
            if (hover != null && !hover.isRemoved()) {
                hover.discard();
            }
            return null;
        });
    }
}
