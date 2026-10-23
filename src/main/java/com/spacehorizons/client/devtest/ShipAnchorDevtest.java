package com.spacehorizons.client.devtest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;

import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.ship.DeckClient;
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.rocket.PortraitGrid;
import com.spacehorizons.rocket.RocketAttitude;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketLanding;
import com.spacehorizons.rocket.RocketPortrait;
import com.spacehorizons.ship.ConversionReport;
import com.spacehorizons.ship.ShipAnchors;
import com.spacehorizons.ship.ShipConversionException;
import com.spacehorizons.ship.ShipConversions;
import com.spacehorizons.ship.ShipManifest;
import com.spacehorizons.ship.ShipTestRocket;
import com.spacehorizons.ship.deck.DeckCrew;
import com.spacehorizons.ship.deck.DeckFrame;
import com.spacehorizons.ship.deck.DeckState;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Autoteste da âncora no ar (spike S6): {@code -PshOnly=shipanchor}. A nave de teste de ~4.000 blocos (a do convés: casco de aço, piso de tijolo, baús,
 * tanques, motores; aqui com a PONTE no lugar do cockpit e a fornalha apagada e vazia) desce até parar a 60 blocos do chão com o jogador a pé no convés;
 * a âncora é recusada com um bloco encostado; ancora; areia, cascalho e água postos dentro não caem nem escorrem e mobs não nascem; o robô anda, abre
 * um baú e liga a fornalha; desancora (o jogador volta ao convés), voa 100 blocos e ancora de novo; monta pela ponte (agachado, mão vazia) e ancora
 * com o jogador sentado; e uma nave grande (~8.000 blocos) mede o tempo da conversão. Romaneio, tempo e posição relativa ao convés conferidos em cada
 * troca. As medidas ficam aqui; o roteiro do cliente, no {@code AutoTest}.
 */
final class ShipAnchorDevtest {
    private static final String TAG = "[AUTOTEST] SHIPANCHOR ";
    /** Altura do fundo da nave acima do chão ao parar. */
    static final double HOVER_ABOVE_GROUND = 60.0;
    /** Onde o jogador embarca no convés (local, pés no piso) e para onde volta nas trocas. */
    static final double[] START_LOCAL = {5.5, 1.0, 5.5};
    /** Um baú (parede sul, olhando para o sul) e a fornalha do retrato, em coordenadas locais. */
    static final BlockPos CHEST_LOCAL = new BlockPos(5, 1, 1);
    static final double LIMIT_POSITION = 0.1;
    static final double LIMIT_MS = 1_000.0;

    private static ShipTestRocket.Built built;
    private static UUID rocketUuid;
    private static int sx;
    private static int sy;
    private static int sz;
    private static int reportsBefore;
    private static volatile Script script;
    /** A posição local do tripulante antes da troca, a esperada no mundo depois (para o cliente) e a âncora da última ida. */
    private static Vector3d localBefore;
    private static Vec3 expectedWorld;
    private static Vec3 worldBefore;
    private static ShipAnchors.Anchor anchor;
    private static int itemsBefore;
    private static final List<String> TABLE = new ArrayList<>();

    private ShipAnchorDevtest() {
    }

    static void check(String name, boolean ok, String detail) {
        ShipDevtest.check("ANCHOR " + name, ok, detail);
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    // ============================================================================================================ a nave e o roteiro

    /** Movimento roteirizado (no lugar do voo): de {@code from} a {@code to} em linha reta a {@code speed} b/t, depois parado lá. */
    private static final class Script implements Consumer<RocketEntity> {
        private final Vec3 from;
        private final Vec3 to;
        private final double speed;
        private final Quaternionf attitude;
        private int tick;
        volatile boolean done;

        Script(Vec3 from, Vec3 to, double speed, Quaternionf attitude) {
            this.from = from;
            this.to = to;
            this.speed = speed;
            this.attitude = new Quaternionf(attitude);
        }

        @Override
        public void accept(RocketEntity rocket) {
            double total = this.from.distanceTo(this.to);
            double d = Math.min(total, ++this.tick * this.speed);
            Vec3 p = total < 1.0E-9 ? this.to : this.from.lerp(this.to, d / total);
            Vec3 before = rocket.position();
            rocket.setPos(p);
            rocket.setDeltaMovement(p.subtract(before));
            rocket.setAttitude(this.attitude);
            if (d >= total) {
                rocket.setDeltaMovement(Vec3.ZERO);
                this.done = true;
            }
        }
    }

    /** O retrato do convés com a PONTE da nave no lugar do cockpit do foguete (a montagem pela ponte precisa dela) e a fornalha apagada e vazia. */
    static RocketPortrait airShip(HolderLookup.Provider registries, RocketPortrait base) {
        List<BlockState> palette = new ArrayList<>(base.palette());
        BlockPos cockpit = base.cockpit();
        int cockpitIndex = base.grid().get(cockpit.getX(), cockpit.getY(), cockpit.getZ());
        palette.set(cockpitIndex, ModBlocks.SHIP_BRIDGE.get().defaultBlockState());
        Long2ObjectMap<CompoundTag> entities = new Long2ObjectLinkedOpenHashMap<>(base.blockEntities());
        for (int i = 0; i < palette.size(); i++) {
            BlockState s = palette.get(i);
            if (s.is(Blocks.FURNACE)) {
                palette.set(i, s.setValue(AbstractFurnaceBlock.LIT, false));
            }
        }
        for (Long2ObjectMap.Entry<CompoundTag> e : base.blockEntities().long2ObjectEntrySet()) {
            BlockPos local = BlockPos.of(e.getLongKey());
            BlockState state = base.get(local);
            if (state.is(Blocks.FURNACE)) {
                FurnaceBlockEntity empty = new FurnaceBlockEntity(local, state.setValue(AbstractFurnaceBlock.LIT, false));
                CompoundTag tag = empty.saveWithFullMetadata(registries);
                tag.remove("x");
                tag.remove("y");
                tag.remove("z");
                entities.put(e.getLongKey(), tag);
            }
        }
        return new RocketPortrait(UUID.randomUUID(), base.grid().rotated(0), palette, entities, cockpit);
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

    /** O centro que o chão pede: fundo da nave a 60 blocos do terreno sob o jogador. */
    private static double hoverCenterY(ServerLevel level, int x, int z, int sizeY) {
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        return ground + HOVER_ABOVE_GROUND + sizeY / 2.0;
    }

    /** Gera a nave 20 blocos acima do ponto de parada, fora da grade e girada 4 graus, com o jogador no convés, e a faz descer a 0,5 b/t até parar. */
    static boolean begin(Minecraft mc) {
        return ShipDevtest.onServer(mc, server -> {
            ServerPlayer player = player(server);
            ServerLevel level = (ServerLevel) player.level();
            ShipTestRocket.Built base = ShipTestRocket.build(level.registryAccess(), ShipTestRocket.preset(4_000));
            built = new ShipTestRocket.Built(airShip(level.registryAccess(), base.portrait()), base.tankFuelMb(), base.chests(), base.expectedItems());
            RocketPortrait p = built.portrait();
            sx = p.sizeX;
            sy = p.sizeY;
            sz = p.sizeZ;
            int px = (int) Math.floor(player.getX());
            int pz = (int) Math.floor(player.getZ());
            // fora da grade de propósito (a acomodação leva a nave ao ponto exato dos blocos)
            Vec3 stop = new Vec3(px + 0.3, hoverCenterY(level, px, pz, sy) + 0.27, pz - 0.2);
            Vec3 start = stop.add(0.0, 20.0, 0.0);
            Quaternionf attitude = RocketAttitude.fromYaw(4.0);
            RocketEntity rocket = ShipTestRocket.spawn(level, start, built);
            rocket.setShip(true);
            rocket.setAttitude(attitude);
            script = new Script(start, stop, 0.5, attitude);
            rocket.setDevtestScript(script);
            rocketUuid = rocket.getUUID();
            DeckState s = new DeckState();
            s.pos.set(START_LOCAL[0], START_LOCAL[1], START_LOCAL[2]);
            s.yaw = 0.0F;
            s.pitch = 10.0F;
            s.onGround = true;
            boolean boarded = DeckCrew.board(player, rocket, s);
            SpaceHorizons.LOGGER.info(TAG + "nave {}x{}x{} ({} blocos, {} block entities) gerada em {}, parada em {} (chão {}), embarcou {}", sx, sy, sz, p.blockCount(),
                    p.blockEntities().size(), start, stop, level.getHeight(Heightmap.Types.MOTION_BLOCKING, px, pz), boarded);
            check("o jogador embarcou no convés da nave", boarded, "");
            return boarded;
        });
    }

    /** O jogador está no convés e a nave chegou ao cliente. */
    static boolean aboard(Minecraft mc) {
        RocketEntity r = DeckClient.rocket();
        return DeckClient.isAboard() && r != null && !r.portrait().isEmpty();
    }

    /** O roteiro chegou ao fim (a nave está parada). */
    static boolean scriptDone(Minecraft mc) {
        Script s = script;
        return s != null && s.done;
    }

    /** Tira o roteiro: a nave fica onde está, pousada no ar (LANDED, sem empuxo), como depois de pairar. */
    static void stopScript(Minecraft mc) {
        ShipDevtest.onServer(mc, server -> {
            RocketEntity r = rocket(server);
            if (r != null) {
                r.setDevtestScript(null);
                r.setDeltaMovement(Vec3.ZERO);
                int ground = ((ServerLevel) r.level()).getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(r.getX()), (int) Math.floor(r.getZ()));
                SpaceHorizons.LOGGER.info(TAG + "nave parada em {}: fundo a {} blocos do chão", r.position(), f(r.getY() - r.portrait().sizeY / 2.0 - ground));
            }
            script = null;
            return null;
        });
    }

    // ============================================================================================================ recusa sem volume livre

    /** Um bloco de pedra encostado no casco (na folga de 1 bloco): a âncora é recusada antes de tocar no mundo, e a nave segue voando. */
    static void refusalCheck(Minecraft mc) {
        String result = ShipDevtest.onServer(mc, server -> {
            RocketEntity r = rocket(server);
            ServerLevel level = (ServerLevel) r.level();
            int turns = PortraitGrid.quarterTurnsFromYaw(r.yawDegrees());
            RocketPortrait rotated = r.portrait().rotated(turns);
            BlockPos origin = RocketLanding.originFor(r.position(), rotated.sizeX, rotated.sizeY, rotated.sizeZ);
            BlockPos stone = origin.offset(-1, 5, 5);
            level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
            String msg;
            try {
                ShipAnchors.request(level, r);
                msg = "ACEITOU";
            } catch (ShipConversionException e) {
                msg = e.getMessage();
            }
            level.setBlockAndUpdate(stone, Blocks.AIR.defaultBlockState());
            boolean frozen = ShipConversions.isFrozen(r);
            return msg + " | congelada depois=" + frozen + " | blocos no mundo na caixa=" + countBlocks(level, new AABB(origin.getX(), origin.getY(), origin.getZ(),
                    origin.getX() + rotated.sizeX, origin.getY() + rotated.sizeY, origin.getZ() + rotated.sizeZ));
        });
        check("recusa sem volume livre (pedra encostada no casco), sem tocar no mundo", result.contains("folga") && result.contains("congelada depois=false")
                && result.endsWith("caixa=0"), result);
    }

    private static int countBlocks(ServerLevel level, AABB box) {
        int n = 0;
        BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
        for (int x = (int) box.minX; x < box.maxX; x++) {
            for (int y = (int) box.minY; y < box.maxY; y++) {
                for (int z = (int) box.minZ; z < box.maxZ; z++) {
                    if (!level.getBlockState(c.set(x, y, z)).isAir()) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    // ============================================================================================================ ancorar

    /** Guarda a posição local do tripulante e pede a âncora (servidor). */
    static void anchorNow(Minecraft mc, String label) {
        String result = ShipDevtest.onServer(mc, server -> {
            ServerPlayer player = player(server);
            RocketEntity r = rocket(server);
            if (r == null) {
                return "sem nave";
            }
            DeckCrew.Member m = DeckCrew.member(player.getUUID());
            localBefore = m != null ? new Vector3d(m.state().pos) : null;
            worldBefore = player.position();
            itemsBefore = containerItems(r.portrait());
            reportsBefore = ShipConversions.reports().size();
            try {
                ShipAnchors.request((ServerLevel) r.level(), r);
                return "ok (acomodação " + f(ShipAnchors.lastSettleDistance) + " blocos, " + f(ShipAnchors.lastSettleDegrees) + " graus; local antes "
                        + (localBefore == null ? "sentado" : localBefore) + ")";
            } catch (ShipConversionException e) {
                return "RECUSOU: " + e.getMessage();
            }
        });
        SpaceHorizons.LOGGER.info(TAG + "{}: pedido de âncora: {}", label, result);
        check(label + ": âncora pedida", result.startsWith("ok"), result);
    }

    /** A nave virou blocos (a entidade sumiu e o relatório da conversão saiu). */
    static boolean anchored(Minecraft mc) {
        return ShipDevtest.onServer(mc, server -> rocket(server) == null && ShipConversions.reports().size() > reportsBefore);
    }

    /** Itens nos baús e barris do retrato (as fornalhas ficam fora, como no romaneio). */
    private static int containerItems(RocketPortrait portrait) {
        int total = 0;
        for (CompoundTag tag : portrait.blockEntities().values()) {
            if (tag.getStringOr("id", "").contains("furnace")) {
                continue;
            }
            Map<String, Integer> items = new TreeMap<>();
            ShipManifest.countItems(tag, items, true);
            for (int n : items.values()) {
                total += n;
            }
        }
        return total;
    }

    private static int containerItems(ServerLevel level, AABB box) {
        int total = 0;
        BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
        for (int x = (int) box.minX; x < box.maxX; x++) {
            for (int y = (int) box.minY; y < box.maxY; y++) {
                for (int z = (int) box.minZ; z < box.maxZ; z++) {
                    BlockEntity be = level.getBlockEntity(c.set(x, y, z));
                    if (be instanceof net.minecraft.world.Container container && !(be instanceof FurnaceBlockEntity)) {
                        for (int i = 0; i < container.getContainerSize(); i++) {
                            total += container.getItem(i).getCount();
                        }
                    }
                }
            }
        }
        return total;
    }

    /** A pose da grade da âncora aplicada ao retrato SEM giro (o referencial do convés de antes). */
    private static DeckFrame anchorFrame(ShipAnchors.Anchor a) {
        int turns = a.quarterTurns();
        Vec3 c = ShipAnchors.snappedCenter(a.min(), a.sizeX(), a.sizeY(), a.sizeZ());
        return new DeckFrame(c.x, c.y, c.z, ShipAnchors.snappedAttitude(turns), new Vector3f(sx / 2.0F, sy / 2.0F, sz / 2.0F));
    }

    /** O bloco do mundo de uma célula local do retrato SEM giro, na nave ancorada. */
    static BlockPos worldOf(BlockPos local) {
        int t = anchor.quarterTurns();
        return anchor.min().offset(PortraitGrid.rotatedX(t, local.getX(), local.getZ(), sx, sz), local.getY(), PortraitGrid.rotatedZ(t, local.getX(), local.getZ(), sx, sz));
    }

    /** Confere a ida (servidor): romaneio sem perdas, tempo, blocos e itens no mundo, a entidade fora e o tripulante no mesmo ponto relativo. */
    static void checkAnchored(Minecraft mc, String label, boolean seated) {
        String c = label + ": ";
        List<String> lines = ShipDevtest.onServer(mc, server -> {
            List<String> out = new ArrayList<>();
            ServerPlayer player = player(server);
            ServerLevel level = (ServerLevel) player.level();
            List<ConversionReport> reports = ShipConversions.reports();
            ConversionReport report = reports.get(reports.size() - 1);
            SpaceHorizons.LOGGER.info(TAG + "{} relatório: {}", label, report.line());
            List<ShipAnchors.Anchor> all = ShipAnchors.all();
            anchor = all.isEmpty() ? null : all.get(all.size() - 1);
            out.add(String.valueOf(report.direction().contains("âncora") && report.ok() && report.losses().isEmpty()));
            out.add(report.ok() + ", perdas: " + (report.losses().isEmpty() ? "nenhuma" : String.join("; ", report.losses())) + ", " + report.blocks() + " blocos, "
                    + report.blockEntities() + " block entities, " + report.items() + " itens");
            out.add(String.valueOf(report.wallMs()));
            out.add(String.format(Locale.ROOT, "%.0f ms de parede (%d ticks de job, máx %.1f ms/tick, início %.1f ms), tick do servidor médio %.1f ms, máx %.1f ms",
                    report.wallMs(), report.jobTicks(), report.jobMsMax(), report.startupMs(), report.serverTickMsAvg(), report.serverTickMsMax()));
            if (anchor == null) {
                out.add("sem âncora");
                return out;
            }
            AABB box = anchor.box();
            int blocks = countBlocks(level, box);
            int items = containerItems(level, box);
            out.add(blocks + "/" + built.portrait().blockCount() + " blocos, itens " + items + "/" + itemsBefore + ", âncora " + anchor.id() + " em "
                    + anchor.min().toShortString() + " " + anchor.sizeX() + "x" + anchor.sizeY() + "x" + anchor.sizeZ() + " (" + anchor.quarterTurns() + " quarto(s))");
            out.add(String.valueOf(blocks == built.portrait().blockCount() && items == itemsBefore && items > 0));
            boolean fullBridge = level.getBlockState(anchor.cockpit()).is(ModBlocks.SHIP_BRIDGE.get());
            out.add(String.valueOf(fullBridge));
            // o tripulante: o ponto do mundo esperado é o local de antes pela pose da grade
            if (!seated && localBefore != null) {
                Vector3d w = anchorFrame(anchor).toWorld(localBefore.x, localBefore.y, localBefore.z);
                expectedWorld = new Vec3(w.x, w.y, w.z);
                double err = player.position().distanceTo(expectedWorld);
                out.add(String.valueOf(err));
                out.add("servidor " + player.position() + " esperado " + expectedWorld + " (local antes " + localBefore + "), no convés depois: "
                        + (DeckCrew.member(player.getUUID()) != null));
            } else {
                expectedWorld = null;
                boolean inside = box.inflate(0.5).contains(player.position());
                out.add(inside && !player.isPassenger() ? "0" : "99");
                out.add("sentado antes; depois em pé em " + player.position() + ", passageiro " + player.isPassenger() + ", dentro da caixa " + inside);
            }
            return out;
        });
        check(c + "romaneio (ShipManifest) com 0 perdas na ida", Boolean.parseBoolean(lines.get(0)), lines.get(1));
        double ms = Double.parseDouble(lines.get(2));
        check(c + "conversão em menos de 1 s", ms < LIMIT_MS, lines.get(3));
        if (lines.size() < 9) {
            check(c + "âncora registrada", false, lines.get(lines.size() - 1));
            return;
        }
        check(c + "blocos e itens dos baús no mundo iguais aos do retrato; entidade removida", Boolean.parseBoolean(lines.get(5)), lines.get(4));
        check(c + "a ponte está no bloco registrado da âncora", Boolean.parseBoolean(lines.get(6)), "ponte em " + anchor.cockpit().toShortString());
        double err = Double.parseDouble(lines.get(7));
        check(c + (seated ? "o piloto sentado ficou em pé dentro da nave" : "o jogador em pé ficou a < 0,1 bloco da posição relativa ao convés (servidor)"),
                err < LIMIT_POSITION, "erro " + f(err) + " | " + lines.get(8));
        TABLE.add(String.format(Locale.ROOT, "%-34s | %s | %s | erro de posição (servidor) %s", label, lines.get(1), lines.get(3), seated ? "-" : f(err)));
    }

    /** Confere no cliente, alguns ticks depois: fora do convés, em pé no bloco real e no ponto esperado. */
    static void checkClientAfterAnchor(Minecraft mc, String label) {
        LocalPlayer p = mc.player;
        BlockPos below = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
        boolean onBlock = !mc.level.getBlockState(below).isAir();
        if (expectedWorld != null) {
            double err = p.position().distanceTo(expectedWorld);
            check(label + ": o jogador em pé ficou a < 0,1 bloco da posição relativa ao convés (cliente), fora do convés e em cima de um bloco real",
                    err < LIMIT_POSITION && !DeckClient.isAboard() && onBlock && p.onGround(), "erro " + f(err) + ", cliente " + p.position() + ", no convés "
                            + DeckClient.isAboard() + ", bloco sob os pés " + mc.level.getBlockState(below) + ", no chão " + p.onGround());
            TABLE.add(String.format(Locale.ROOT, "%-34s | erro de posição (cliente) %s", label, f(err)));
        } else {
            check(label + ": o jogador em cima de um bloco real da nave (cliente)", onBlock && !p.isPassenger(), "bloco sob os pés " + mc.level.getBlockState(below));
        }
    }

    // ============================================================================================================ areia, cascalho, água e mobs

    private static final BlockPos SAND_LOCAL = new BlockPos(20, -2, 20);
    private static final BlockPos GRAVEL_LOCAL = new BlockPos(20, -2, 6);
    private static final BlockPos WATER_LOCAL = new BlockPos(20, 1, 13);

    private static BlockPos underCeiling(BlockPos local) {
        return local.getY() < 0 ? new BlockPos(local.getX(), sy + local.getY(), local.getZ()) : local;
    }

    /** Põe areia e cascalho pendurados no teto (ar embaixo) e uma fonte de água no piso. */
    static void placeHazards(Minecraft mc) {
        ShipDevtest.onServer(mc, server -> {
            ServerLevel level = (ServerLevel) player(server).level();
            level.setBlockAndUpdate(worldOf(underCeiling(SAND_LOCAL)), Blocks.SAND.defaultBlockState());
            level.setBlockAndUpdate(worldOf(underCeiling(GRAVEL_LOCAL)), Blocks.GRAVEL.defaultBlockState());
            level.setBlockAndUpdate(worldOf(WATER_LOCAL), Blocks.WATER.defaultBlockState());
            return null;
        });
    }

    /** Depois de 60 ticks: nada caiu nem escorreu; mobs não nascem dentro nem no teto (e nascem, pela regra, fora da nave). */
    static void checkHazards(Minecraft mc) {
        List<String> r = ShipDevtest.onServer(mc, server -> {
            List<String> out = new ArrayList<>();
            ServerLevel level = (ServerLevel) player(server).level();
            BlockPos sand = worldOf(underCeiling(SAND_LOCAL));
            BlockPos gravel = worldOf(underCeiling(GRAVEL_LOCAL));
            BlockPos water = worldOf(WATER_LOCAL);
            int falling = level.getEntitiesOfClass(FallingBlockEntity.class, anchor.box().inflate(2.0)).size();
            boolean sandStays = level.getBlockState(sand).is(Blocks.SAND) && level.getBlockState(sand.below()).isAir();
            boolean gravelStays = level.getBlockState(gravel).is(Blocks.GRAVEL) && level.getBlockState(gravel.below()).isAir();
            int wet = 0;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (!level.getFluidState(water.relative(d)).isEmpty()) {
                    wet++;
                }
            }
            boolean source = level.getFluidState(water).isSource();
            out.add(String.valueOf(sandStays && gravelStays && falling == 0));
            out.add("areia " + level.getBlockState(sand) + " (embaixo " + level.getBlockState(sand.below()) + "), cascalho " + level.getBlockState(gravel)
                    + ", blocos caindo " + falling);
            out.add(String.valueOf(source && wet == 0));
            out.add("fonte " + source + ", vizinhos molhados " + wet);
            // mobs: a checagem de lugar do NeoForge com o resultado padrão "pode"
            BlockPos roof = anchor.min().offset(anchor.sizeX() / 2, anchor.sizeY(), anchor.sizeZ() / 2);
            BlockPos inside = worldOf(new BlockPos(13, 1, 8));
            BlockPos outside = anchor.min().offset(-40, 0, 0);
            boolean roofOk = spawnAllowed(level, roof);
            boolean insideOk = spawnAllowed(level, inside);
            boolean outsideOk = spawnAllowed(level, outside);
            out.add(String.valueOf(!roofOk && !insideOk && outsideOk));
            out.add("zumbi natural no teto " + roofOk + ", dentro " + insideOk + ", fora da nave " + outsideOk);
            // limpa (a montagem de volta recusa terreno e fluido)
            level.setBlock(sand, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(gravel, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(water, Blocks.AIR.defaultBlockState(), 3);
            return out;
        });
        check("areia e cascalho pendurados no teto da nave ancorada não caem", Boolean.parseBoolean(r.get(0)), r.get(1));
        check("a água no piso da nave ancorada não escorre", Boolean.parseBoolean(r.get(2)), r.get(3));
        check("mobs não nascem no teto nem dentro da nave ancorada (e nascem fora)", Boolean.parseBoolean(r.get(4)), r.get(5));
    }

    private static boolean spawnAllowed(ServerLevel level, BlockPos pos) {
        MobSpawnEvent.SpawnPlacementCheck event = new MobSpawnEvent.SpawnPlacementCheck(net.minecraft.world.entity.EntityTypes.ZOMBIE, level, EntitySpawnReason.NATURAL, pos, level.getRandom(),
                true);
        NeoForge.EVENT_BUS.post(event);
        return event.getPlacementCheckResult();
    }

    // ============================================================================================================ o robô: andar, baú e fornalha

    private static Vec3 walkStart;
    private static int walkTicks;

    /** O ponto do mundo de um ponto local (pés) da nave ancorada. */
    static Vec3 worldPoint(double lx, double ly, double lz) {
        Vector3d w = anchorFrame(anchor).toWorld(lx, ly, lz);
        return new Vec3(w.x, w.y, w.z);
    }

    /** Anda com as teclas até {@code goal} (centro), olhando para lá; devolve true ao chegar (ou ao estourar o tempo). */
    static boolean walkTo(Minecraft mc, Vec3 goal, int maxTicks, String label) {
        LocalPlayer p = mc.player;
        if (walkStart == null) {
            walkStart = p.position();
            walkTicks = 0;
        }
        walkTicks++;
        double dx = goal.x - p.getX();
        double dz = goal.z - p.getZ();
        double dist = Math.hypot(dx, dz);
        if (dist < 0.3 || walkTicks > maxTicks) {
            mc.options.keyUp.setDown(false);
            double moved = Math.hypot(p.getX() - walkStart.x, p.getZ() - walkStart.z);
            double floorY = anchor.min().getY() + 1.0;
            check("o robô andou " + label + " na nave ancorada, no piso", dist < 0.6 && Math.abs(p.getY() - floorY) < 0.01, String.format(Locale.ROOT,
                    "andou %.2f blocos em %d ticks, faltam %.2f, pés em y=%.3f (piso %.1f)", moved, walkTicks, dist, p.getY(), floorY));
            walkStart = null;
            return true;
        }
        p.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        p.setXRot(10.0F);
        mc.options.keyUp.setDown(dist > 0.3);
        return false;
    }

    /** Olha e clica com a mão vazia num bloco local da nave (face voltada para o jogador). */
    static void use(Minecraft mc, BlockPos local, Direction face, String label) {
        BlockPos pos = worldOf(local);
        Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        Vec3 d = hit.subtract(mc.player.getEyePosition());
        mc.player.setYRot(ShipDevtest.yawOf(d));
        mc.player.setXRot(ShipDevtest.pitchOf(d));
        var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
        SpaceHorizons.LOGGER.info(TAG + "clique direito em {} ({}): {}", label, pos, result);
    }

    /** O baú abriu no cliente com os mesmos itens do servidor. */
    static void checkChestOpen(Minecraft mc) {
        int server = ShipDevtest.onServer(mc, s -> {
            ServerLevel level = (ServerLevel) player(s).level();
            int n = 0;
            if (level.getBlockEntity(worldOf(CHEST_LOCAL)) instanceof ChestBlockEntity chest) {
                for (int i = 0; i < chest.getContainerSize(); i++) {
                    n += chest.getItem(i).getCount();
                }
            }
            return n;
        });
        boolean open = mc.player.containerMenu != mc.player.inventoryMenu;
        int total = 0;
        if (open) {
            for (int i = 0; i < 27; i++) {
                total += mc.player.containerMenu.getSlot(i).getItem().getCount();
            }
        }
        check("o robô abriu o baú da nave ancorada (tela com os itens do servidor)", open && total == server && total > 0, "aberto " + open + ", cliente " + total
                + ", servidor " + server);
    }

    /** Dá carvão e ferro bruto ao jogador (barra, posições 0 e 1) para ligar a fornalha. */
    static void giveFurnaceItems(Minecraft mc) {
        ShipDevtest.onServer(mc, server -> {
            ServerPlayer player = player(server);
            player.getInventory().setItem(0, new ItemStack(Items.COAL, 8));
            player.getInventory().setItem(1, new ItemStack(Items.RAW_IRON, 8));
            player.getInventory().setSelectedSlot(2);
            player.inventoryMenu.broadcastChanges();
            return null;
        });
    }

    /** Com a tela da fornalha aberta: shift-clique no carvão e no ferro da barra (o vanilla manda o carvão para o combustível e o ferro para a entrada). */
    static void loadFurnace(Minecraft mc) {
        var menu = mc.player.containerMenu;
        boolean furnace = menu instanceof net.minecraft.world.inventory.FurnaceMenu;
        SpaceHorizons.LOGGER.info(TAG + "tela aberta: {}", menu.getClass().getSimpleName());
        check("o robô abriu a fornalha da nave ancorada", furnace, menu.getClass().getSimpleName());
        if (furnace) {
            // fornalha: 0 entrada, 1 combustível, 2 saída, 3..29 inventário, 30..38 barra
            mc.gameMode.handleContainerInput(menu.containerId, 30, 0, ContainerInput.QUICK_MOVE, mc.player);
            mc.gameMode.handleContainerInput(menu.containerId, 31, 0, ContainerInput.QUICK_MOVE, mc.player);
        }
    }

    /** A fornalha acendeu no servidor (estado aceso, queimando e cozinhando). */
    static void checkFurnaceLit(Minecraft mc) {
        String r = ShipDevtest.onServer(mc, server -> {
            ServerLevel level = (ServerLevel) player(server).level();
            BlockPos pos = furnaceWorld(level);
            if (pos == null) {
                return "false|sem fornalha";
            }
            BlockState state = level.getBlockState(pos);
            boolean lit = state.getValue(AbstractFurnaceBlock.LIT);
            String items = "";
            if (level.getBlockEntity(pos) instanceof FurnaceBlockEntity f) {
                items = "entrada " + f.getItem(0) + ", combustível " + f.getItem(1) + ", saída " + f.getItem(2);
            }
            return lit + "|" + pos.toShortString() + " aceso=" + lit + " " + items;
        });
        String[] parts = r.split("\\|", 2);
        check("o robô ligou a fornalha da nave ancorada (acesa no servidor)", Boolean.parseBoolean(parts[0]), parts[1]);
    }

    /** A fornalha do retrato no mundo. */
    private static BlockPos furnaceWorld(ServerLevel level) {
        return worldOf(furnaceLocal());
    }

    static BlockPos furnaceLocal() {
        for (Long2ObjectMap.Entry<CompoundTag> e : built.portrait().blockEntities().long2ObjectEntrySet()) {
            if (e.getValue().getStringOr("id", "").contains("furnace")) {
                return BlockPos.of(e.getLongKey());
            }
        }
        return BlockPos.ZERO;
    }

    // ============================================================================================================ desancorar e voar

    /** Volta o jogador em pé ao ponto de embarque, no piso real (servidor). */
    static void standAtStart(Minecraft mc) {
        ShipDevtest.onServer(mc, server -> {
            Vec3 at = worldPoint(START_LOCAL[0], START_LOCAL[1], START_LOCAL[2]);
            player(server).connection.teleport(at.x, at.y, at.z, 0.0F, 10.0F);
            return null;
        });
    }

    /** Desancora pelo comando de dev (ninguém senta; o jogador em pé entra no convés na mesma posição) e confere. */
    static void unanchor(Minecraft mc) {
        List<String> r = ShipDevtest.onServer(mc, server -> {
            List<String> out = new ArrayList<>();
            ServerPlayer player = player(server);
            ServerLevel level = (ServerLevel) player.level();
            worldBefore = player.position();
            int anchoredItems = containerItems(level, anchor.box());
            int anchoredBlocks = countBlocks(level, anchor.box());
            long t0 = System.nanoTime();
            RocketEntity ship;
            try {
                ship = ShipAnchors.unanchor(level, anchor, null);
            } catch (com.spacehorizons.rocket.RocketException e) {
                out.add("false");
                out.add("RECUSOU: " + e.component().getString());
                return out;
            }
            double ms = (System.nanoTime() - t0) / 1.0e6;
            rocketUuid = ship.getUUID();
            DeckCrew.Member m = DeckCrew.member(player.getUUID());
            Vector3d local = m == null ? null : m.state().pos;
            // independente da entidade: o canto da âncora é o canto da caixa do retrato novo (sem giro: a montagem não gira)
            Vec3 expectedLocal = worldBefore.subtract(anchor.min().getX(), anchor.min().getY(), anchor.min().getZ());
            double err = local == null ? 99.0 : Math.sqrt(local.distanceSquared(expectedLocal.x, expectedLocal.y, expectedLocal.z));
            int shipItems = containerItems(ship.portrait());
            boolean same = ship.portrait().blockCount() == anchoredBlocks && shipItems == anchoredItems && ship.isShip()
                    && countBlocks(level, anchor.box()) == 0;
            out.add(String.valueOf(same && ShipAnchors.all().isEmpty()));
            out.add(String.format(Locale.ROOT, "%d blocos (%d ancorados), itens %d (%d), nave %s, caixa do mundo vazia %s, âncoras %d, montagem em %.1f ms",
                    ship.portrait().blockCount(), anchoredBlocks, shipItems, anchoredItems, ship.isShip(), countBlocks(level, anchor.box()) == 0, ShipAnchors.all().size(), ms));
            out.add(String.valueOf(err));
            out.add("local no convés " + local + ", esperado " + expectedLocal + " (mundo antes " + worldBefore + ")");
            TABLE.add(String.format(Locale.ROOT, "%-34s | %s | erro de posição (servidor) %s", "desancorar (comando)", out.get(1), f(err)));
            return out;
        });
        check("desancorar: a nave volta a entidade com os mesmos blocos e itens, sem âncora e sem blocos no mundo", Boolean.parseBoolean(r.get(0)), r.get(1));
        if (r.size() > 2) {
            double err = Double.parseDouble(r.get(2));
            check("desancorar: o jogador em pé ficou a < 0,1 bloco da posição relativa ao convés (servidor), de volta ao convés", err < LIMIT_POSITION,
                    "erro " + f(err) + " | " + r.get(3));
        }
    }

    /** No cliente, depois de desancorar: de volta ao convés e no mesmo ponto do mundo. */
    static void checkClientAfterUnanchor(Minecraft mc) {
        double err = mc.player.position().distanceTo(worldBefore);
        check("desancorar: o jogador em pé no convés ficou a < 0,1 bloco do ponto de antes (cliente)", DeckClient.isAboard() && err < LIMIT_POSITION,
                "no convés " + DeckClient.isAboard() + ", erro " + f(err) + ", cliente " + mc.player.position() + ", antes " + worldBefore);
        TABLE.add(String.format(Locale.ROOT, "%-34s | erro de posição (cliente) %s", "desancorar (comando)", f(err)));
    }

    /** A nave voa 100 blocos para o leste a 1 b/t (roteiro) com o jogador em pé no convés. */
    static void fly(Minecraft mc) {
        ShipDevtest.onServer(mc, server -> {
            RocketEntity r = rocket(server);
            Vec3 from = r.position();
            script = new Script(from, from.add(100.0, 0.0, 0.0), 1.0, new Quaternionf(r.attitude()));
            r.setDevtestScript(script);
            return null;
        });
    }

    // ============================================================================================================ montar pela ponte

    /** Põe o jogador em pé na frente da ponte (ao norte dela, olhando para o sul). */
    static void standAtBridge(Minecraft mc) {
        ShipDevtest.onServer(mc, server -> {
            BlockPos bridge = anchor.cockpit();
            player(server).connection.teleport(bridge.getX() + 0.5, bridge.getY(), bridge.getZ() - 1.5, 0.0F, 30.0F);
            return null;
        });
    }

    /** Clique com a mão vazia na ponte (o jogador já agachado). */
    static void clickBridge(Minecraft mc) {
        BlockPos bridge = anchor.cockpit();
        Vec3 hit = Vec3.atCenterOf(bridge).add(0.0, 0.0, -0.5);
        Vec3 d = hit.subtract(mc.player.getEyePosition());
        mc.player.setYRot(ShipDevtest.yawOf(d));
        mc.player.setXRot(ShipDevtest.pitchOf(d));
        var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.NORTH, bridge, false));
        SpaceHorizons.LOGGER.info(TAG + "clique agachado na ponte {}: {} (agachado {})", bridge, result, mc.player.isShiftKeyDown());
    }

    /** Montou pela ponte: a nave é entidade de novo, o jogador sentado nela e a âncora fora do registro. */
    static void checkBridgeMounted(Minecraft mc) {
        String r = ShipDevtest.onServer(mc, server -> {
            ServerPlayer player = player(server);
            boolean seated = player.getVehicle() instanceof RocketEntity rocket && rocket.isShip();
            if (seated) {
                rocketUuid = player.getVehicle().getUUID();
            }
            return seated + "|sentado na nave " + seated + ", âncoras " + ShipAnchors.all().size() + ", blocos " + (seated ? ((RocketEntity) player.getVehicle()).portrait()
                    .blockCount() : 0) + ", posição " + player.position();
        });
        String[] parts = r.split("\\|", 2);
        check("montou pela ponte no ar (agachado, mão vazia): nave entidade, jogador sentado, âncora desfeita", Boolean.parseBoolean(parts[0])
                && parts[1].contains("âncoras 0"), parts[1]);
    }

    // ============================================================================================================ nave grande (tempo)

    private static UUID bigUuid;

    /** Gera a nave de ~8.000 blocos (a do {@code ship genrocket 8000}) no ar, a 60 blocos do chão, já na grade, e pede a âncora. */
    static void bigShip(Minecraft mc) {
        String r = ShipDevtest.onServer(mc, server -> {
            ServerPlayer player = player(server);
            ServerLevel level = (ServerLevel) player.level();
            ShipTestRocket.Built big = ShipTestRocket.build(level.registryAccess(), ShipTestRocket.preset(8_000));
            RocketPortrait p = big.portrait();
            int x0 = (int) Math.floor(player.getX()) + 70;
            int z0 = (int) Math.floor(player.getZ()) - p.sizeZ / 2;
            int ground = 0;
            for (int x = x0; x < x0 + p.sizeX; x += 4) {
                for (int z = z0; z < z0 + p.sizeZ; z += 4) {
                    ground = Math.max(ground, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
                }
            }
            Vec3 center = new Vec3(x0 + p.sizeX / 2.0, ground + HOVER_ABOVE_GROUND + p.sizeY / 2.0, z0 + p.sizeZ / 2.0);
            RocketEntity rocket = ShipTestRocket.spawn(level, center, big);
            rocket.setShip(true);
            bigUuid = rocket.getUUID();
            reportsBefore = ShipConversions.reports().size();
            itemsBefore = containerItems(p);
            try {
                ShipAnchors.request(level, rocket);
                return "ok: " + p.blockCount() + " blocos, " + p.blockEntities().size() + " block entities, " + itemsBefore + " itens, centro " + center;
            } catch (ShipConversionException e) {
                return "RECUSOU: " + e.getMessage();
            }
        });
        check("nave grande (~8.000 blocos): âncora pedida", r.startsWith("ok"), r);
    }

    static boolean bigAnchored(Minecraft mc) {
        return ShipDevtest.onServer(mc, server -> {
            boolean gone = true;
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(bigUuid) != null) {
                    gone = false;
                }
            }
            return gone && ShipConversions.reports().size() > reportsBefore;
        });
    }

    static void checkBig(Minecraft mc) {
        String r = ShipDevtest.onServer(mc, server -> {
            List<ConversionReport> reports = ShipConversions.reports();
            ConversionReport report = reports.get(reports.size() - 1);
            SpaceHorizons.LOGGER.info(TAG + "nave grande relatório: {}", report.line());
            String line = String.format(Locale.ROOT, "%d blocos, %d block entities, %d itens; %.0f ms de parede (%d ticks de job, máx %.1f ms/tick, início %.1f ms), "
                    + "tick do servidor médio %.1f ms, máx %.1f ms; perdas: %s", report.blocks(), report.blockEntities(), report.items(), report.wallMs(), report.jobTicks(),
                    report.jobMsMax(), report.startupMs(), report.serverTickMsAvg(), report.serverTickMsMax(), report.losses().isEmpty() ? "nenhuma" : String.join("; ",
                            report.losses()));
            TABLE.add(String.format(Locale.ROOT, "%-34s | %s", "nave grande (~8.000 blocos)", line));
            return (report.ok() && report.losses().isEmpty()) + "|" + (report.wallMs() < LIMIT_MS) + "|" + line;
        });
        String[] parts = r.split("\\|", 3);
        check("nave grande (~8.000 blocos): romaneio com 0 perdas", Boolean.parseBoolean(parts[0]), parts[2]);
        check("nave grande (~8.000 blocos): conversão em menos de 1 s", Boolean.parseBoolean(parts[1]), parts[2]);
    }

    // ============================================================================================================ prints e resumo

    /** Ponto e olhar para o print de fora: a oeste da nave, um pouco acima, olhando para ela. */
    static Vec3 outsideEye() {
        Vec3 c = ShipAnchors.snappedCenter(anchor.min(), anchor.sizeX(), anchor.sizeY(), anchor.sizeZ());
        return c.add(-anchor.sizeX() * 1.6, anchor.sizeY() * 0.45, -anchor.sizeZ() * 0.9);
    }

    static Vec3 anchorCenter() {
        return ShipAnchors.snappedCenter(anchor.min(), anchor.sizeX(), anchor.sizeY(), anchor.sizeZ());
    }

    static void summary() {
        SpaceHorizons.LOGGER.info(TAG + "TABELA ({} linhas):", TABLE.size());
        for (String row : TABLE) {
            SpaceHorizons.LOGGER.info(TAG + "TABELA {}", row);
        }
    }
}
