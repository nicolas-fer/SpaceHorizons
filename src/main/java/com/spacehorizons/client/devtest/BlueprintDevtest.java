package com.spacehorizons.client.devtest;

import java.util.List;
import java.util.Locale;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.rocket.RocketDesignClient;
import com.spacehorizons.client.rocket.RocketDesignModel;
import com.spacehorizons.client.rocket.blueprint.BlueprintHologram;
import com.spacehorizons.client.rocket.blueprint.BlueprintScreen;
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.content.connected.ConnectedFaces;
import com.spacehorizons.rocket.DesignReport;
import com.spacehorizons.rocket.FlightMode;
import com.spacehorizons.rocket.RocketAssembler;
import com.spacehorizons.rocket.RocketBlockKind;
import com.spacehorizons.rocket.RocketControlPayload;
import com.spacehorizons.rocket.RocketDesign;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketException;
import com.spacehorizons.rocket.RocketFuelTankBlock;
import com.spacehorizons.rocket.RocketFuelTankBlockEntity;
import com.spacehorizons.rocket.blueprint.Blueprint;
import com.spacehorizons.rocket.blueprint.BlueprintCheck;
import com.spacehorizons.rocket.blueprint.BlueprintStates;
import com.spacehorizons.rocket.blueprint.BlueprintWorld;
import com.spacehorizons.rocket.blueprint.Blueprints;
import com.spacehorizons.rocket.blueprint.LaunchPadBlockEntity;
import com.spacehorizons.rocket.blueprint.RocketBlueprintItem;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Cenário {@code blueprint} do autoteste (docs/PLANO_TIERS.md, seção 4): plantas holográficas de montagem. Escolhe a planta orbital pelo menu
 * do item e a põe numa plataforma vazia (holograma), põe parte dos blocos com dois errados (vermelho e progresso no painel), completa, monta e
 * voa um pouco; depois salva um foguete construído em outra plataforma como planta e a reaplica numa terceira. As verificações saem no log com
 * {@code [AUTOTEST] BLUEPRINT CHECK PASSOU/FALHOU} e o resumo no fim.
 */
final class BlueprintDevtest {
    private static final String TAG = "[AUTOTEST] BLUEPRINT ";
    /** A plataforma da planta orbital (vazia no começo, só a âncora). */
    static final BlockPos PAD_A = RocketDevtest.BASE;
    /** A plataforma do foguete que vira planta salva. */
    static final BlockPos PAD_B = PAD_A.offset(40, 0, 0);
    /** A plataforma onde a planta salva é reaplicada. */
    static final BlockPos PAD_C = PAD_A.offset(80, 0, 0);
    /** O canto da base de B que o jogador clica com a planta em branco (vira a âncora da planta salva). */
    static final BlockPos PAD_B_CLICK = PAD_B.offset(-2, 0, -2);

    private static int passed;
    private static int failed;
    private static @Nullable DesignReport sourceReport;

    private BlueprintDevtest() {
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

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static void clearBox(ServerLevel level, BlockPos center, int half, int below, int above) {
        for (int y = -below; y <= above; y++) {
            for (int x = -half; x <= half; x++) {
                for (int z = -half; z <= half; z++) {
                    level.setBlock(center.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    // ---------------------------------------------------------------- A. planta orbital numa plataforma vazia

    /** Limpa a área de A e põe só a plataforma-âncora. */
    static void prepareA(Minecraft mc) {
        onServer(mc, "preparar A", (server, level, player) -> {
            for (RocketEntity old : level.getEntitiesOfClass(RocketEntity.class, new AABB(PAD_A).inflate(200.0))) {
                old.discard();
            }
            clearBox(level, PAD_A, 8, 2, 24);
            level.setBlock(PAD_A, ModBlocks.LAUNCH_PAD.get().defaultBlockState(), 3);
            FlightDevtest.mark("bp_prepared_a");
        });
    }

    /** Abre o menu da planta pelo clique no ar de verdade (o item chama o gancho do cliente). */
    static void openMenu(Minecraft mc) {
        mc.player.getInventory().setSelectedSlot(0);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        check("o clique no ar com a planta abre o menu", mc.gui.screen() instanceof BlueprintScreen,
                "tela=" + (mc.gui.screen() == null ? "nenhuma" : mc.gui.screen().getClass().getSimpleName()));
    }

    /** Escolhe a planta {@code id} no menu aberto (o mesmo que o botão faz). */
    static void choose(Minecraft mc, String id) {
        if (mc.gui.screen() instanceof BlueprintScreen screen) {
            screen.choose(id);
        } else {
            check("menu aberto para escolher " + id, false, "sem menu");
        }
    }

    /** O item na mão guarda a planta esperada (no cliente, pelo componente sincronizado). */
    static void checkHeld(Minecraft mc, String label, @Nullable Blueprint expected) {
        ItemStack stack = mc.player.getMainHandItem();
        Blueprint held = RocketBlueprintItem.blueprintOf(stack);
        check(label, stack.getItem() instanceof RocketBlueprintItem && java.util.Objects.equals(held, expected),
                "na mão: " + stack.getHoverName().getString() + " planta=" + held);
    }

    /** A plataforma tem a planta no servidor e no cliente, e o cliente compara planta x mundo. */
    static void checkActive(Minecraft mc, String label, BlockPos pad, Blueprint expected, int expectedMissing, int expectedCorrect) {
        BlockPos copy = pad.immutable();
        Blueprint client = BlueprintHologram.blueprintAt(mc.level, copy);
        BlueprintHologram.invalidate();
        BlueprintHologram.State state = client == null ? null : BlueprintHologram.state(mc.level, copy, client);
        BlueprintCheck.Progress p = state == null ? null : state.progress();
        check(label + ": planta ativa no cliente", expected.equals(client), "cliente=" + client);
        check(label + ": holograma (cliente) mostra o que falta", p != null && p.missing() == expectedMissing && p.correct() == expectedCorrect && p.wrong() == 0,
                p == null ? "sem progresso" : "faltam " + p.missing() + " (esperado " + expectedMissing + "), certos " + p.correct() + " (esperado "
                        + expectedCorrect + "), errados " + p.wrong());
        onServer(mc, label + " no servidor", (server, level, player) -> {
            Blueprint serverSide = level.getBlockEntity(copy) instanceof LaunchPadBlockEntity be ? be.blueprint() : null;
            check(label + ": planta ativa no servidor (block entity)", expected.equals(serverSide), "servidor=" + serverSide);
        });
    }

    // ---------------------------------------------------------------- B. parte dos blocos, dois errados

    private static int partialMissing;

    /**
     * Põe as plataformas e os motores da planta (certos), um casco no lugar do primeiro tanque e um tanque no lugar do cockpit (errados); o
     * resto falta.
     */
    static void placePartial(Minecraft mc) {
        onServer(mc, "parte dos blocos", (server, level, player) -> {
            Blueprint b = Blueprints.ORBITAL;
            boolean hullPut = false;
            int missing = 0;
            for (int i = 0; i < b.size(); i++) {
                BlockPos pos = PAD_A.offset(b.dx(i), b.dy(i), b.dz(i));
                RocketBlockKind kind = b.kind(i);
                if (kind == RocketBlockKind.PAD || kind == RocketBlockKind.ENGINE) {
                    level.setBlock(pos, BlueprintStates.palette(b)[b.stateIndex(i)], 3);
                } else if (kind == RocketBlockKind.FUEL_TANK && !hullPut) {
                    level.setBlock(pos, ModBlocks.ROCKET_HULL.get().defaultBlockState(), 3);
                    hullPut = true;
                } else if (kind == RocketBlockKind.COCKPIT) {
                    level.setBlock(pos, ModBlocks.ROCKET_FUEL_TANK.get().defaultBlockState(), 3);
                } else {
                    missing++;
                }
            }
            partialMissing = missing;
            BlueprintCheck.Progress p = BlueprintWorld.check(level, PAD_A, b);
            check("parcial: servidor conta 2 errados e " + missing + " faltando", p.wrong() == 2 && p.missing() == missing,
                    "errados " + p.wrong() + ", faltam " + p.missing() + ", certos " + p.correct() + " de " + p.total());
            FlightDevtest.mark("bp_partial");
        });
    }

    /** O cliente vê o mesmo progresso (holograma e painel). */
    static void checkPartialClient(Minecraft mc) {
        BlueprintHologram.invalidate();
        BlueprintHologram.State state = BlueprintHologram.forPanel(mc.level, PAD_A);
        BlueprintCheck.Progress p = state == null ? null : state.progress();
        check("parcial: o cliente (holograma e painel) vê 2 errados e os que faltam", p != null && p.wrong() == 2 && p.missing() == partialMissing,
                p == null ? "sem progresso" : "errados " + p.wrong() + ", faltam " + p.missing() + " (esperado " + partialMissing + ")");
        // o painel de uma OUTRA plataforma do conjunto também acha a planta (está na âncora)
        BlueprintHologram.State fromCorner = BlueprintHologram.forPanel(mc.level, PAD_A.offset(-1, 0, -1));
        check("parcial: o painel de outra plataforma do conjunto acha a planta da âncora", fromCorner != null && fromCorner.blueprint().equals(Blueprints.ORBITAL),
                "achou=" + (fromCorner == null ? "nada" : fromCorner.blueprint()));
        DesignReport r = RocketDesignClient.lastReport();
        SpaceHorizons.LOGGER.info("{}PAINEL parcial: {}", TAG, r == null ? "sem relatório" : RocketDesignModel.describe(r));
    }

    // ---------------------------------------------------------------- C. completa, monta e voa

    /** Põe o que falta e o que está errado (a planta inteira), conecta os tanques e os enche. */
    static void complete(Minecraft mc, BlockPos pad, Blueprint b, String marker) {
        onServer(mc, "completar " + marker, (server, level, player) -> {
            int placed = BlueprintWorld.placeAll(level, pad, b);
            int tanks = 0;
            for (int i = 0; i < b.size(); i++) {
                BlockPos pos = pad.offset(b.dx(i), b.dy(i), b.dz(i));
                if (level.getBlockState(pos).is(ModBlocks.ROCKET_FUEL_TANK.get())) {
                    ConnectedFaces.connectAround(level, pos, ModBlocks.ROCKET_FUEL_TANK.get(), RocketFuelTankBlock.rule());
                    if (level.getBlockEntity(pos) instanceof RocketFuelTankBlockEntity tank) {
                        tank.setFuel(4000);
                        tanks++;
                    }
                }
            }
            BlueprintCheck.Progress p = BlueprintWorld.check(level, pad, b);
            check(marker + ": planta completa no servidor", p.complete(), "pôs " + placed + " blocos, " + tanks + " tanques cheios; faltam " + p.missing()
                    + ", errados " + p.wrong());
            FlightDevtest.mark(marker);
        });
    }

    /** O holograma some (planta completa) e o painel promete o destino do projeto da planta. */
    static void checkCompleteClient(Minecraft mc, BlockPos pad, Blueprint b, String label) {
        BlueprintHologram.invalidate();
        BlueprintHologram.State state = BlueprintHologram.forPanel(mc.level, pad);
        BlueprintCheck.Progress p = state == null ? null : state.progress();
        check(label + ": o cliente vê a planta completa (sem fantasmas)", p != null && p.complete(),
                p == null ? "sem progresso" : "faltam " + p.missing() + ", errados " + p.wrong());
        DesignReport client = RocketDesignClient.lastReport();
        BlockPos copy = pad.immutable();
        onServer(mc, label + " painel", (server, level, player) -> {
            DesignReport built = RocketDesign.analyze(level, copy);
            SpaceHorizons.LOGGER.info("{}PAINEL {} (servidor): {}", TAG, label, RocketDesignModel.describe(built));
            check(label + ": o foguete construído chega ao destino do projeto da planta", built.destinationValue() == b.design().destinationValue()
                    && built.assemblable(), "construído " + built.destinationValue() + " (montável=" + built.assemblable() + "), planta " + b.design().destinationValue());
            check(label + ": números do construído = do projeto da planta", built.blocks() == b.design().blocks()
                    && Math.abs(built.thrust() - b.design().thrust()) < 1.0e-9 && Math.abs(built.deltaV() - b.design().deltaV()) < 1.0e-9,
                    "blocos " + built.blocks() + "/" + b.design().blocks() + ", empuxo " + f(built.thrust()) + "/" + f(b.design().thrust()) + ", Δv "
                            + f(built.deltaV()) + "/" + f(b.design().deltaV()));
            if (client != null && client.padX() == copy.getX() && client.padZ() == copy.getZ()) {
                check(label + ": o painel do cliente diz o mesmo", client.destinationValue() == built.destinationValue(), "cliente " + client.destinationValue());
            }
        });
    }

    private static BlockPos cockpitOf(BlockPos pad, Blueprint b) {
        for (int i = 0; i < b.size(); i++) {
            if (b.kind(i) == RocketBlockKind.COCKPIT) {
                return pad.offset(b.dx(i), b.dy(i), b.dz(i));
            }
        }
        return pad;
    }

    static void assemble(Minecraft mc) {
        onServer(mc, "montagem", (server, level, player) -> {
            try {
                RocketEntity rocket = RocketAssembler.assemble(level, cockpitOf(PAD_A, Blueprints.ORBITAL), null);
                check("a planta orbital construída monta", true, rocket.portrait().blockCount() + " blocos, empuxo " + f(rocket.stats().thrust()) + ", combustível "
                        + f(rocket.fuelMb()) + " mB");
                FlightDevtest.mark("bp_assembled");
            } catch (RocketException e) {
                check("a planta orbital construída monta", false, e.getMessage());
                FlightDevtest.mark("bp_assembled");
                FlightDevtest.mark("bp_burning");
                FlightDevtest.mark("bp_landed");
            }
        });
    }

    // voo curto (subida com acelerador cheio, corte, paraquedas, pouso)
    private static boolean registered;
    private static boolean armed;
    private static int flyTicks;
    private static int burnTicks;
    private static double startY;
    private static double maxAltitude;
    private static boolean everFlew;

    static void fly(Minecraft mc) {
        onServer(mc, "voo", (server, level, player) -> {
            List<RocketEntity> found = level.getEntitiesOfClass(RocketEntity.class, new AABB(PAD_A).inflate(60.0));
            if (found.isEmpty()) {
                check("foguete para voar", false, "nenhum");
                FlightDevtest.mark("bp_burning");
                FlightDevtest.mark("bp_landed");
                return;
            }
            RocketEntity rocket = found.get(0);
            player.teleportTo(rocket.getX(), rocket.getY() + 2.0, rocket.getZ());
            check("piloto sentado no foguete da planta", player.startRiding(rocket, true, true), "veículo=" + player.getVehicle());
            double accelNet = rocket.stats().thrust() / rocket.stats().wetMass(rocket.fuelMb()) - DesignReport.EARTH_GRAVITY;
            burnTicks = accelNet <= 0.0 ? 100 : (int) Math.max(3, Math.min(120, Math.round(5.0 / accelNet)));
            flyTicks = 0;
            startY = rocket.getY();
            maxAltitude = 0.0;
            everFlew = false;
            armed = true;
            if (!registered) {
                registered = true;
                NeoForge.EVENT_BUS.addListener(ServerTickEvent.Pre.class, e -> flyTick(e.getServer()));
            }
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
        if (flyTicks == 2 + burnTicks + 2) {
            flags |= RocketControlPayload.FLAG_PARACHUTE;
        }
        rocket.applyControl(new RocketControlPayload(0.0F, 0.0F, 0.0F, throttle, (byte) flags));
        if (flyTicks == 2 + burnTicks / 2) {
            FlightDevtest.mark("bp_burning");
        }
        if (everFlew && mode == FlightMode.LANDED && flyTicks > burnTicks + 10) {
            armed = false;
            check("o foguete da planta orbital decolou e pousou", maxAltitude > 3.0, "altitude máxima " + f(maxAltitude) + " blocos em " + flyTicks + " ticks");
            player.stopRiding();
            rocket.discard();
            FlightDevtest.mark("bp_landed");
        } else if (flyTicks > 4_000) {
            armed = false;
            check("voo da planta terminou no tempo", false, "modo " + mode);
            FlightDevtest.mark("bp_landed");
        }
    }

    /** Depois que o foguete saiu, as plataformas ficam com a planta: o holograma volta a mostrar as peças que faltam (para reconstruir). */
    static void checkAfterLaunch(Minecraft mc) {
        Blueprint b = Blueprints.ORBITAL;
        int pads = b.count(RocketBlockKind.PAD);
        BlueprintHologram.invalidate();
        BlueprintHologram.State state = BlueprintHologram.forPanel(mc.level, PAD_A);
        BlueprintCheck.Progress p = state == null ? null : state.progress();
        check("depois da decolagem a planta continua na plataforma (holograma para reconstruir)", p != null && p.correct() == pads && p.missing() == b.size() - pads,
                p == null ? "sem planta" : "certos " + p.correct() + " (plataformas " + pads + "), faltam " + p.missing());
    }

    // ---------------------------------------------------------------- D. salvar um foguete como planta e reaplicar

    /** Em B: base 7x7 e o foguete "Lua e volta" do painel, com um baú e uma escada presos no casco (blocos comuns também vão na planta). */
    static void buildSource(Minecraft mc) {
        onServer(mc, "foguete de origem", (server, level, player) -> {
            clearBox(level, PAD_B, 8, 2, 30);
            clearBox(level, PAD_C, 8, 2, 30);
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    level.setBlock(PAD_B.offset(x, 0, z), ModBlocks.LAUNCH_PAD.get().defaultBlockState(), 3);
                }
            }
            Blueprint design = Blueprints.design("teste", 3, 2, 8, 2, 20);
            BlueprintWorld.placeAll(level, PAD_B, design);
            int top = design.maxY();
            // a última camada de cascos tem só (-1, top, -1) e (0, top, -1): o baú e a escada vão em cima deles (ligados por face)
            level.setBlock(PAD_B.offset(0, top + 1, -1), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST), 3);
            level.setBlock(PAD_B.offset(-1, top + 1, -1), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST), 3);
            level.setBlock(PAD_C, ModBlocks.LAUNCH_PAD.get().defaultBlockState(), 3);
            sourceReport = RocketDesign.analyze(level, PAD_B_CLICK);
            SpaceHorizons.LOGGER.info("{}ORIGEM: {}", TAG, RocketDesignModel.describe(sourceReport));
            check("foguete de origem construído e montável", sourceReport.found() && sourceReport.assemblable(), RocketDesignModel.describe(sourceReport));
            FlightDevtest.mark("bp_source");
        });
    }

    /** A planta em branco virou a planta do foguete de B (no item do cliente). */
    static void checkSaved(Minecraft mc) {
        ItemStack stack = mc.player.getMainHandItem();
        Blueprint saved = RocketBlueprintItem.blueprintOf(stack);
        DesignReport source = sourceReport;
        check("a planta em branco salvou o foguete da plataforma", saved != null && source != null,
                "item=" + stack.getHoverName().getString() + " planta=" + saved);
        if (saved == null || source == null) {
            return;
        }
        int parts = saved.size() - saved.count(RocketBlockKind.PAD);
        check("planta salva: todas as peças do foguete (as que a montagem leva)", parts == source.blocks(), parts + " peças, o painel conta " + source.blocks());
        check("planta salva: autor e âncora", saved.author().equals(mc.player.getName().getString()) && saved.grid().kindAt(0, 0, 0) == RocketBlockKind.PAD,
                "autor=" + saved.author());
        boolean chest = false;
        boolean stairs = false;
        for (String s : saved.palette()) {
            chest |= s.startsWith("minecraft:chest[facing=east");
            stairs |= s.startsWith("minecraft:oak_stairs[facing=west");
        }
        check("planta salva: blocos comuns com o estado exato (baú e escada)", chest && stairs, "paleta=" + saved.palette());
        check("planta salva: o projeto da planta = o painel do foguete de origem", saved.design().destinationValue() == source.destinationValue()
                && Math.abs(saved.design().deltaV() - source.deltaV()) < 1.0e-9, "planta " + saved.design().destinationValue() + " Δv " + f(saved.design().deltaV())
                        + ", origem " + source.destinationValue() + " Δv " + f(source.deltaV()));
        // a base 7x7 de B não entra inteira: só a pegada do foguete com a borda (e a plataforma clicada)
        check("planta salva: só as plataformas sob o foguete", saved.count(RocketBlockKind.PAD) < 49, saved.count(RocketBlockKind.PAD) + " plataformas");
    }

    /** Agachado + clique tirou a planta da plataforma (servidor e cliente). */
    static void checkCleared(Minecraft mc, BlockPos pad) {
        BlockPos copy = pad.immutable();
        check("agachado + clique tira a planta (cliente)", BlueprintHologram.blueprintAt(mc.level, copy) == null, "cliente=" + BlueprintHologram.blueprintAt(mc.level, copy));
        onServer(mc, "planta tirada", (server, level, player) -> check("agachado + clique tira a planta (servidor)",
                level.getBlockEntity(copy) instanceof LaunchPadBlockEntity be && be.blueprint() == null, "ok"));
    }

    static @Nullable Blueprint held(Minecraft mc) {
        return RocketBlueprintItem.blueprintOf(mc.player.getMainHandItem());
    }

    static BlockState padState() {
        return ModBlocks.LAUNCH_PAD.get().defaultBlockState();
    }
}
