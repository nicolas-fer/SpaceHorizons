package com.spacehorizons.client.devtest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import org.joml.Quaternionf;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.rocket.RocketKeys;
import com.spacehorizons.client.rocket.RocketPilot;
import com.spacehorizons.client.ship.AnchorClient;
import com.spacehorizons.client.ship.AnchorHud;
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.rocket.RocketAttitude;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketException;
import com.spacehorizons.rocket.RocketLanding;
import com.spacehorizons.rocket.RocketPortrait;
import com.spacehorizons.ship.AnchorStatus;
import com.spacehorizons.ship.ShipAnchors;
import com.spacehorizons.ship.ShipConversions;
import com.spacehorizons.ship.ShipTestRocket;
import com.spacehorizons.ship.block.AntigravityGeneratorBlock;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Autoteste da âncora completa (fase 2f): {@code -PshOnly=shipanchor2}. O jogador pilota (sentado) uma nave pequena parada no ar, e a tecla de ancorar do
 * painel é apertada de verdade ({@code KeyMapping.click}): sem gerador (recusa), com um bloco encostado (recusa, com os blocos destacados), rápido demais
 * (recusa), pronta e ancorada pela tecla com gerador (a carga do gerador se gasta e volta), sem carga depois de desancorar. Cada estado do painel é
 * conferido no cliente e fotografado. Também cobre os limites do S6: o pouso antigo no ar registra a âncora e a âncora some se a ponte é quebrada. O
 * roteiro do cliente fica no {@code AutoTest}; as medidas, aqui.
 */
final class ShipAnchor2Devtest {
    private static final String TAG = "[AUTOTEST] SHIPANCHOR2 ";
    private static final double HOVER_ABOVE_GROUND = 60.0;
    /** A recarga encurtada do gerador (ticks) durante o teste. */
    private static final int RECHARGE = 160;

    private static UUID rocketUuid;
    private static ShipAnchors.Anchor anchor;
    private static BlockPos stone;
    private static int refusalsBefore;
    private static int reportsBefore;
    private static volatile Consumer<RocketEntity> script;

    private ShipAnchor2Devtest() {
    }

    static void check(String name, boolean ok, String detail) {
        ShipDevtest.check("ANCHOR2 " + name, ok, detail);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private static RocketEntity rocket(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (rocketUuid != null && level.getEntity(rocketUuid) instanceof RocketEntity r && !r.isRemoved()) {
                return r;
            }
        }
        return null;
    }

    // ============================================================================================================ a nave

    /** O retrato do convés com a ponte, e (se {@code generator}) um gerador de antigravidade carregado no piso, ao lado da ponte. */
    private static ShipTestRocket.Built build(ServerLevel level, boolean generator) {
        ShipTestRocket.Built base = ShipTestRocket.build(level.registryAccess(), ShipTestRocket.preset(500));
        RocketPortrait ship = ShipAnchorDevtest.airShip(level.registryAccess(), base.portrait());
        if (!generator) {
            return new ShipTestRocket.Built(ship, base.tankFuelMb(), base.chests(), base.expectedItems());
        }
        List<BlockState> palette = new ArrayList<>(ship.palette());
        palette.add(ModBlocks.ANTIGRAVITY_GENERATOR.get().defaultBlockState());
        var grid = ship.grid().rotated(0);
        BlockPos at = new BlockPos(ship.cockpit().getX() + 1, 1, ship.cockpit().getZ() + 1);
        grid.set(at.getX(), at.getY(), at.getZ(), palette.size() - 1);
        RocketPortrait withGenerator = new RocketPortrait(UUID.randomUUID(), grid, palette,
                new it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<>(ship.blockEntities()), ship.cockpit());
        return new ShipTestRocket.Built(withGenerator, base.tankFuelMb(), base.chests(), base.expectedItems());
    }

    /** Tira a nave anterior (e o piloto) e gera uma nova parada a 60 blocos do chão, com o jogador sentado nela (servidor). */
    static boolean spawn(Minecraft mc, boolean generator, String label) {
        return ShipDevtest.onServer(mc, server -> {
            ServerPlayer player = player(server);
            ServerLevel level = (ServerLevel) player.level();
            RocketEntity old = rocket(server);
            if (old != null) {
                old.ejectPassengers();
                old.discard();
            }
            script = null;
            ShipTestRocket.Built built = build(level, generator);
            RocketPortrait p = built.portrait();
            int px = (int) Math.floor(player.getX());
            int pz = (int) Math.floor(player.getZ());
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, px, pz);
            Vec3 at = new Vec3(px + 0.3, ground + HOVER_ABOVE_GROUND + p.sizeY / 2.0 + 0.27, pz - 0.2);
            RocketEntity rocket = ShipTestRocket.spawn(level, at, built);
            rocket.setShip(true);
            rocket.setAttitude(RocketAttitude.fromYaw(0.0));
            rocketUuid = rocket.getUUID();
            boolean riding = player.startRiding(rocket, true, true);
            SpaceHorizons.LOGGER.info(TAG + "{}: nave {}x{}x{} ({} blocos, gerador {}) em {}, piloto sentado {}", label, p.sizeX, p.sizeY, p.sizeZ, p.blockCount(), generator,
                    at, riding);
            check(label + ": o jogador pilota a nave (sentado, passageiro de controle)", riding && rocket.getControllingPassenger() == player, "sentou " + riding);
            return riding;
        });
    }

    /** A nave vai e volta no eixo leste-oeste a {@code speed} b/t (roteiro, no lugar do voo; manda o estado da âncora como o controlador de voo faria). */
    static void moveEast(Minecraft mc, double speed) {
        ShipDevtest.onServer(mc, server -> {
            RocketEntity r = rocket(server);
            ServerPlayer player = player(server);
            Quaternionf attitude = new Quaternionf(r.attitude());
            int[] tick = new int[1];
            script = rocket -> {
                Vec3 before = rocket.position();
                // vai e volta (10 ticks para cada lado): a nave fica a menos de 30 blocos de onde estava, sem sair dos chunks carregados
                Vec3 next = before.add(((tick[0]++ / 10) % 2 == 0 ? speed : -speed), 0.0, 0.0);
                rocket.setPos(next);
                rocket.setDeltaMovement(next.subtract(before));
                rocket.setAttitude(attitude);
                rocket.positionRider(player);
                ShipAnchors.tickPilot(rocket, player, rocket.level().getGameTime());
            };
            r.setDevtestScript(script);
            return null;
        });
    }

    /** O piloto está sentado e o cliente já o trata como piloto. */
    static boolean piloting(Minecraft mc) {
        return RocketPilot.isPiloting();
    }

    // ============================================================================================================ o painel e a tecla

    /** O estado esperado chegou ao painel (cliente) e o painel o mostra (a camada de desenho decide por {@link AnchorHud#shown}). */
    static boolean statusIs(AnchorStatus expected) {
        return AnchorClient.current() == expected && AnchorHud.shown() == expected;
    }

    /** O texto do painel agora, para o log e o relatório. */
    static String hudText() {
        AnchorStatus s = AnchorHud.shown();
        return s == null ? "(sem painel)" : AnchorHud.text(s).getString();
    }

    static void checkPanel(String label, AnchorStatus expected) {
        AnchorStatus now = AnchorHud.shown();
        check(label + ": o painel mostra \"" + hudText() + "\"", now == expected, "esperado " + expected + ", painel " + now);
    }

    /** Aperta a tecla de ancorar do painel (o clique que o jogo consumiria do teclado). */
    static void pressAnchorKey(Minecraft mc) {
        refusalsBefore = AnchorClient.refusals();
        reportsBefore = ShipConversions.reports().size();
        KeyMapping.click(RocketKeys.ANCHOR.getKey());
        SpaceHorizons.LOGGER.info(TAG + "tecla de ancorar apertada ({})", RocketKeys.ANCHOR.getTranslatedKeyMessage().getString());
    }

    static boolean refused(Minecraft mc) {
        return AnchorClient.refusals() > refusalsBefore;
    }

    /** A recusa chegou: a mensagem da barra, e a nave segue entidade e solta. */
    static void checkRefusal(Minecraft mc, String label, AnchorStatus expected, boolean expectHighlight) {
        String msg = AnchorClient.lastMessage();
        String expectedKey = "message.spacehorizons.anchor." + expected.key();
        boolean entityKept = ShipDevtest.onServer(mc, server -> rocket(server) != null && !ShipConversions.isFrozen(rocket(server)) && ShipAnchors.all().isEmpty());
        check(label + ": recusa com mensagem clara na tela", expectedKey.equals(AnchorClient.lastKey()), "mensagem \"" + msg + "\" (" + AnchorClient.lastKey() + ")");
        check(label + ": a nave segue entidade, sem congelar, sem âncora", entityKept, "entidade " + entityKept);
        if (expectHighlight) {
            check(label + ": os blocos que impedem ficam destacados (partículas)", AnchorClient.highlighting() && !AnchorClient.blockers().isEmpty(),
                    "blocos " + AnchorClient.blockers());
        }
    }

    // ============================================================================================================ bloco encostado

    /** Põe uma pedra encostada no casco oeste, na altura da janela (à vista pelo vidro). */
    static void placeStone(Minecraft mc) {
        ShipDevtest.onServer(mc, server -> {
            RocketEntity r = rocket(server);
            ServerLevel level = (ServerLevel) r.level();
            RocketPortrait p = r.portrait();
            BlockPos origin = RocketLanding.originFor(r.position(), p.sizeX, p.sizeY, p.sizeZ);
            stone = origin.offset(-1, 6, p.sizeZ / 2);
            level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
            return null;
        });
    }

    static void removeStone(Minecraft mc) {
        ShipDevtest.onServer(mc, server -> {
            ((ServerLevel) player(server).level()).setBlockAndUpdate(stone, Blocks.AIR.defaultBlockState());
            return null;
        });
    }

    static Vec3 stoneCenter() {
        return Vec3.atCenterOf(stone);
    }

    static void checkBlockersAreTheStone(Minecraft mc) {
        check("sem espaço: o servidor aponta a pedra encostada", AnchorClient.blockers().contains(stone), "blocos " + AnchorClient.blockers() + ", pedra " + stone);
    }

    // ============================================================================================================ ancorar pela tecla

    static boolean anchored(Minecraft mc) {
        return ShipDevtest.onServer(mc, server -> rocket(server) == null && ShipConversions.reports().size() > reportsBefore && !ShipAnchors.all().isEmpty());
    }

    /** Ancorou: a entidade sumiu, o relatório está sem perdas, a âncora está registrada e os geradores gastaram a carga. */
    static void checkAnchoredByKey(Minecraft mc) {
        List<String> r = ShipDevtest.onServer(mc, server -> {
            List<String> out = new ArrayList<>();
            ServerLevel level = (ServerLevel) player(server).level();
            var reports = ShipConversions.reports();
            var report = reports.get(reports.size() - 1);
            anchor = ShipAnchors.all().get(ShipAnchors.all().size() - 1);
            out.add(String.valueOf(report.ok() && report.losses().isEmpty()));
            out.add(report.line());
            int generators = 0;
            int charged = 0;
            for (BlockPos pos : BlockPos.betweenClosed(anchor.min(), anchor.min().offset(anchor.sizeX() - 1, anchor.sizeY() - 1, anchor.sizeZ() - 1))) {
                BlockState state = level.getBlockState(pos);
                if (state.is(ModBlocks.ANTIGRAVITY_GENERATOR.get())) {
                    generators++;
                    if (state.getValue(AntigravityGeneratorBlock.CHARGED)) {
                        charged++;
                    }
                }
            }
            out.add(String.valueOf(generators == 1 && charged == 0));
            out.add(generators + " gerador(es), " + charged + " carregado(s) logo depois de ancorar");
            out.add(String.valueOf(!player(server).isPassenger()));
            return out;
        });
        check("ancorar pela tecla com gerador: romaneio com 0 perdas", Boolean.parseBoolean(r.get(0)), r.get(1));
        check("ancorar gastou a carga do gerador (energia, não combustível)", Boolean.parseBoolean(r.get(2)), r.get(3));
        check("o piloto saiu do assento (em pé na nave ancorada)", Boolean.parseBoolean(r.get(4)), "");
    }

    /** Depois da recarga, o gerador do bloco volta a estar carregado (a carga é recuperada com o bloco no mundo). */
    static boolean generatorRecharged(Minecraft mc) {
        return ShipDevtest.onServer(mc, server -> {
            ServerLevel level = (ServerLevel) player(server).level();
            for (BlockPos pos : BlockPos.betweenClosed(anchor.min(), anchor.min().offset(anchor.sizeX() - 1, anchor.sizeY() - 1, anchor.sizeZ() - 1))) {
                BlockState state = level.getBlockState(pos);
                if (state.is(ModBlocks.ANTIGRAVITY_GENERATOR.get())) {
                    return state.getValue(AntigravityGeneratorBlock.CHARGED);
                }
            }
            return false;
        });
    }

    /** Desancora com o jogador como piloto (senta de volta): a nave volta entidade com o gerador sem carga (em voo não recarrega). */
    static void unanchorWithPilot(Minecraft mc) {
        String r = ShipDevtest.onServer(mc, server -> {
            ServerPlayer player = player(server);
            ServerLevel level = (ServerLevel) player.level();
            try {
                RocketEntity ship = ShipAnchors.unanchor(level, anchor, player);
                rocketUuid = ship.getUUID();
                int[] c = AntigravityGeneratorBlock.count(ship.portrait());
                return "ok geradores " + c[0] + " carregados " + c[1] + " piloto " + (ship.getControllingPassenger() == player);
            } catch (RocketException e) {
                return "RECUSOU " + e.component().getString();
            }
        });
        check("desancorar com o piloto: a nave volta entidade, o piloto senta, o gerador sem carga", r.startsWith("ok geradores 1 carregados 0") && r.endsWith("true"), r);
    }

    /** Ancorar de novo logo depois de desancorar: o servidor recusa por falta de carga, sem tocar no mundo. */
    static void shortRecharge() {
        AntigravityGeneratorBlock.rechargeTicks = RECHARGE;
    }

    static void restoreRecharge() {
        AntigravityGeneratorBlock.rechargeTicks = AntigravityGeneratorBlock.DEFAULT_RECHARGE_TICKS;
    }

    // ============================================================================================================ limites do S6

    /** O pouso antigo (clique agachado na entidade parada no ar) agora registra a âncora; o piloto sai antes. */
    static void landInAir(Minecraft mc) {
        List<String> r = ShipDevtest.onServer(mc, server -> {
            List<String> out = new ArrayList<>();
            RocketEntity ship = rocket(server);
            ServerLevel level = (ServerLevel) ship.level();
            try {
                RocketLanding.land(level, ship);
            } catch (RocketException e) {
                out.add("false");
                out.add("RECUSOU " + e.component().getString());
                return out;
            }
            List<ShipAnchors.Anchor> all = ShipAnchors.all();
            anchor = all.isEmpty() ? null : all.get(all.size() - 1);
            boolean bridge = anchor != null && level.getBlockState(anchor.cockpit()).is(ModBlocks.SHIP_BRIDGE.get());
            out.add(String.valueOf(all.size() == 1 && bridge));
            out.add("âncoras " + all.size() + ", ponte no bloco registrado " + bridge + (anchor == null ? "" : ", caixa " + anchor.min().toShortString()));
            return out;
        });
        check("pouso antigo no ar (clique agachado): vira blocos E registra a âncora", Boolean.parseBoolean(r.get(0)), r.get(1));
    }

    /** O jogador quebra a ponte da nave ancorada. */
    static void breakBridge(Minecraft mc) {
        ShipDevtest.onServer(mc, server -> {
            ((ServerLevel) player(server).level()).setBlockAndUpdate(anchor.cockpit(), Blocks.AIR.defaultBlockState());
            return null;
        });
    }

    static boolean anchorsGone(Minecraft mc) {
        return ShipDevtest.onServer(mc, server -> ShipAnchors.all().isEmpty());
    }

    // ============================================================================================================ prints e fim

    static Vec3 bridgeEye() {
        return Vec3.atCenterOf(anchor.cockpit());
    }

    static void summary() {
        ShipDevtest.summary();
        SpaceHorizons.LOGGER.info(String.format(Locale.ROOT, "%sFIM", TAG));
    }
}
