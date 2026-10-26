package com.spacehorizons.client.devtest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.joml.Quaternionf;
import org.joml.Vector3d;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.ship.DeckAim;
import com.spacehorizons.client.ship.DeckClient;
import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.content.ModEntities;
import com.spacehorizons.rocket.FlightMode;
import com.spacehorizons.rocket.PortraitChange;
import com.spacehorizons.rocket.PortraitGrid;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketLanding;
import com.spacehorizons.rocket.RocketPortrait;
import com.spacehorizons.ship.ShipTestRocket;
import com.spacehorizons.ship.deck.DeckCrew;
import com.spacehorizons.ship.deck.DeckState;
import com.spacehorizons.ship.deck.DeckStand;
import com.spacehorizons.ship.deck.PortraitContainers;
import com.spacehorizons.ship.deck.PortraitDeltaPayload;
import com.spacehorizons.ship.deck.PortraitUse;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * Autoteste da interação com a nave em movimento (fase 2e, frente 3): {@code -PshOnly=shipdeckuse}. Uma nave pequena (sala de 9x7x9 com porta, baú,
 * fornalha, assento e ponte) sobe a 10 blocos por tick girando e inclinada, e o jogador está no convés dela. O roteiro: mira e contorno na porta;
 * a porta fechada barra o jogador e, aberta pelo clique (pelo retrato, com o delta chegando ao cliente), deixa passar; abre o baú (menu vanilla
 * sobre o proxy), tira e põe itens e confere o NBT do retrato e a conservação dos itens; clica na fornalha e recebe o aviso; senta no assento e na
 * ponte, levanta e volta ao convés em pé; por fim a nave pousa (a conversão em blocos) e o mundo tem a porta aberta e o baú com o conteúdo novo.
 * O clique usa o mesmo caminho do jogador ({@link DeckAim#useNow}, que o evento de uso chama), com o olhar apontado de verdade para o bloco.
 */
final class ShipDeckUseDevtest {
    private static final String TAG = "[AUTOTEST] SHIPDECKUSE ";

    private static final int SX = 9;
    private static final int SY = 7;
    private static final int SZ = 9;
    private static final BlockPos DOOR_LOW = new BlockPos(3, 1, 4);
    private static final BlockPos DOOR_UP = new BlockPos(3, 2, 4);
    private static final BlockPos CHEST = new BlockPos(6, 1, 4);
    private static final BlockPos FURNACE = new BlockPos(4, 1, 2);
    private static final BlockPos SEAT = new BlockPos(4, 1, 6);
    private static final BlockPos COCKPIT = new BlockPos(6, 1, 7);
    private static final double Y_LOW = 600.0;
    private static final double Y_HIGH = 4_400.0;
    private static final double SPEED = 10.0;

    private static UUID rocketUuid;
    private static int rocketId;
    private static RocketPortrait original;

    // ---- estado do roteiro ----
    private static int phase;
    private static int t;
    private static int deltas0;
    private static long rejected0;
    private static long accepted0;
    private static int warnings0;
    private static int uses0;
    private static double maxXBlocked;
    private static CompoundTag chestBefore;
    private static UUID portraitIdBefore;
    private static List<Double> seatErrors = new ArrayList<>();
    private static Map<String, Integer> totalsBefore;
    private static RocketPortrait lastPortrait;
    private static int passed0;

    private ShipDeckUseDevtest() {
    }

    // ============================================================================================================ o retrato de teste

    private static void put(PortraitGrid grid, List<BlockState> palette, Map<BlockState, Integer> index, int x, int y, int z, BlockState state) {
        int idx = index.computeIfAbsent(state, s -> {
            palette.add(s);
            return palette.size() - 1;
        });
        grid.set(x, y, z, idx);
    }

    private static CompoundTag nbtOf(BlockEntity be, HolderLookup.Provider registries) {
        CompoundTag tag = be.saveWithFullMetadata(registries);
        tag.remove("x");
        tag.remove("y");
        tag.remove("z");
        return tag;
    }

    /** A sala: casco de aço, piso de tijolo com lanternas, janelas, a porta de carvalho, o baú (12 diamantes, 20 pedregulhos, maçãs), a fornalha, o assento e a ponte. */
    static RocketPortrait buildPortrait(HolderLookup.Provider registries) {
        PortraitGrid grid = PortraitGrid.empty(SX, SY, SZ);
        List<BlockState> palette = new ArrayList<>();
        palette.add(Blocks.AIR.defaultBlockState());
        Map<BlockState, Integer> index = new HashMap<>();
        index.put(palette.get(0), 0);
        Long2ObjectMap<CompoundTag> entities = new Long2ObjectLinkedOpenHashMap<>();
        BlockState hull = ModBlocks.ROCKET_HULL.get().defaultBlockState();
        for (int y = 0; y < SY; y++) {
            for (int z = 0; z < SZ; z++) {
                for (int x = 0; x < SX; x++) {
                    boolean shell = x == 0 || x == SX - 1 || y == 0 || y == SY - 1 || z == 0 || z == SZ - 1;
                    if (!shell) {
                        continue;
                    }
                    BlockState state = hull;
                    boolean side = (x == 0 || x == SX - 1) != (z == 0 || z == SZ - 1);
                    if (y == 0) {
                        state = (x % 4 == 2 && z % 4 == 2) ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
                    } else if (y == SY - 1 && x % 4 == 2 && z % 4 == 2) {
                        state = Blocks.SEA_LANTERN.defaultBlockState();
                    } else if (side && (y == 3 || y == 4) && ((x == 0 || x == SX - 1) ? z >= 2 && z <= SZ - 3 : x >= 2 && x <= SX - 3)) {
                        state = Blocks.GLASS.defaultBlockState();
                    }
                    put(grid, palette, index, x, y, z, state);
                }
            }
        }
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.OPEN, false);
        put(grid, palette, index, DOOR_LOW.getX(), DOOR_LOW.getY(), DOOR_LOW.getZ(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        put(grid, palette, index, DOOR_UP.getX(), DOOR_UP.getY(), DOOR_UP.getZ(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        BlockState chestState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST);
        put(grid, palette, index, CHEST.getX(), CHEST.getY(), CHEST.getZ(), chestState);
        ChestBlockEntity chest = new ChestBlockEntity(CHEST, chestState);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 12));
        chest.setItem(1, new ItemStack(Items.COBBLESTONE, 20));
        ItemStack apples = new ItemStack(Items.GOLDEN_APPLE, 3);
        apples.set(DataComponents.CUSTOM_NAME, Component.literal("Provisão de bordo"));
        chest.setItem(5, apples);
        entities.put(CHEST.asLong(), nbtOf(chest, registries));
        BlockState furnaceState = Blocks.FURNACE.defaultBlockState().setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.FACING, Direction.SOUTH);
        put(grid, palette, index, FURNACE.getX(), FURNACE.getY(), FURNACE.getZ(), furnaceState);
        FurnaceBlockEntity furnace = new FurnaceBlockEntity(FURNACE, furnaceState);
        furnace.setItem(0, new ItemStack(Items.RAW_IRON, 8));
        entities.put(FURNACE.asLong(), nbtOf(furnace, registries));
        put(grid, palette, index, SEAT.getX(), SEAT.getY(), SEAT.getZ(), ModBlocks.SHIP_SEAT.get().defaultBlockState());
        put(grid, palette, index, COCKPIT.getX(), COCKPIT.getY(), COCKPIT.getZ(), ModBlocks.ROCKET_COCKPIT.get().defaultBlockState());
        return new RocketPortrait(UUID.randomUUID(), grid, palette, entities, COCKPIT);
    }

    /** O movimento roteirizado: sobe e desce a 10 blocos por tick entre os limites, girando 1 grau por tick, inclinada 20 graus. */
    private static final class Script implements java.util.function.Consumer<RocketEntity> {
        private final double x;
        private final double z;
        private double y = Y_LOW + 100.0;
        private double direction = 1.0;
        private int tick;

        Script(double x, double z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public void accept(RocketEntity rocket) {
            if (this.y + this.direction * SPEED > Y_HIGH || this.y + this.direction * SPEED < Y_LOW) {
                this.direction = -this.direction;
            }
            this.y += this.direction * SPEED;
            double tilt = Math.toRadians(20.0 * Math.min(1.0, this.tick / 100.0));
            Quaternionf q = new Quaternionf().rotateX((float) tilt).rotateY((float) Math.toRadians(1.0 * this.tick));
            rocket.setPos(this.x, this.y, this.z);
            rocket.setDeltaMovement(0.0, this.direction * SPEED, 0.0);
            rocket.setAttitude(q);
            this.tick++;
        }
    }

    // ============================================================================================================ começo

    static void begin(Minecraft mc) {
        phase = 0;
        t = 0;
        passed0 = 0;
        Integer[] ids = ShipDevtest.onServer(mc, server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = (ServerLevel) player.level();
            original = buildPortrait(level.registryAccess());
            RocketEntity rocket = ModEntities.ROCKET.get().create(level, EntitySpawnReason.TRIGGERED);
            rocket.setPortrait(original);
            Script script = new Script(Math.floor(player.getX()) + 0.5, Math.floor(player.getZ()) + 0.5);
            rocket.setPos(script.x, script.y, script.z);
            level.addFreshEntity(rocket);
            rocket.setFlightMode(FlightMode.FLIGHT);
            rocket.setDevtestScript(script);
            rocketUuid = rocket.getUUID();
            DeckState start = new DeckState();
            start.pos.set(1.5, 1.0, 4.5);
            start.yaw = -90.0F;
            start.pitch = 5.0F;
            start.onGround = true;
            accepted0 = DeckCrew.ACCEPTED.get();
            rejected0 = DeckCrew.REJECTED.get();
            boolean boarded = DeckCrew.board(player, rocket, start);
            SpaceHorizons.LOGGER.info(TAG + "nave {} ({}x{}x{}, {} blocos) em voo roteirizado a {} b/t; embarcou {}", rocket.getId(), SX, SY, SZ, original.blockCount(), SPEED, boarded);
            // o tamanho do delta de uma porta contra o retrato inteiro de uma nave grande (a de teste de 4.000 blocos)
            RocketPortrait big = ShipTestRocket.build(level.registryAccess(), ShipTestRocket.preset(4_000)).portrait();
            ByteBuf buf = Unpooled.buffer();
            PortraitDeltaPayload.CODEC.encode(buf, new PortraitDeltaPayload(rocket.getId(), UUID.randomUUID(), UUID.randomUUID(),
                    List.of(PortraitChange.state(DOOR_LOW, Blocks.OAK_DOOR.defaultBlockState()), PortraitChange.state(DOOR_UP, Blocks.OAK_DOOR.defaultBlockState()))));
            SpaceHorizons.LOGGER.info(TAG + "TAMANHOS: retrato inteiro da nave de teste de {} blocos = {} bytes; delta de uma porta (2 metades) = {} bytes; retrato desta sala = {} bytes",
                    big.blockCount(), RocketPortrait.encodedSize(big), buf.readableBytes(), RocketPortrait.encodedSize(original));
            return new Integer[] {rocket.getId(), boarded ? 1 : 0};
        });
        rocketId = ids[0];
        ShipDevtest.check("DECKUSE: embarcou no convés da nave de teste", ids[1] == 1, "id " + rocketId);
        mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
    }

    static boolean ready(Minecraft mc) {
        RocketEntity r = DeckClient.rocket();
        return r != null && DeckClient.isAboard() && !r.portrait().isEmpty() && r.getId() == rocketId;
    }

    // ============================================================================================================ utilidades

    private static RocketEntity clientRocket(Minecraft mc) {
        return mc.level != null && mc.level.getEntity(rocketId) instanceof RocketEntity r ? r : null;
    }

    private static RocketEntity serverRocket(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(rocketUuid) instanceof RocketEntity r) {
                return r;
            }
        }
        return null;
    }

    /** Aponta o olhar (local) para o ponto local, a partir do olho do jogador no convés. */
    private static void aimAt(Minecraft mc, double x, double y, double z) {
        DeckState s = DeckClient.state();
        double dx = x - s.pos.x;
        double dy = y - (s.pos.y + mc.player.getEyeHeight());
        double dz = z - s.pos.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        mc.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        mc.player.setXRot((float) -Math.toDegrees(Math.asin(dy / len)));
    }

    private static void aimAtCell(Minecraft mc, BlockPos cell, double fy) {
        aimAt(mc, cell.getX() + 0.5, cell.getY() + fy, cell.getZ() + 0.5);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "autotest_" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                msg -> SpaceHorizons.LOGGER.info(TAG + "print salvo: {}", msg.getString()));
    }

    private static void go(int next) {
        phase = next;
        t = 0;
    }

    private static boolean targetIs(DeckAim.Target target, BlockPos... cells) {
        if (target == null) {
            return false;
        }
        for (BlockPos cell : cells) {
            if (cell.equals(target.pos())) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Integer> itemTotals(ServerPlayer player, RocketEntity rocket) {
        Map<String, Integer> totals = new HashMap<>();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            add(totals, stack);
        }
        add(totals, player.containerMenu.getCarried());
        CompoundTag nbt = rocket.portrait().blockEntities().get(CHEST.asLong());
        BlockEntity be = BlockEntity.loadStatic(CHEST, rocket.portrait().get(CHEST), nbt, player.level().registryAccess());
        if (be instanceof Container c) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                add(totals, c.getItem(i));
            }
        }
        return totals;
    }

    private static void add(Map<String, Integer> totals, ItemStack stack) {
        if (!stack.isEmpty()) {
            totals.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
        }
    }

    private static int chestCount(ServerPlayer player, RocketEntity rocket, net.minecraft.world.item.Item item) {
        CompoundTag nbt = rocket.portrait().blockEntities().get(CHEST.asLong());
        BlockEntity be = BlockEntity.loadStatic(CHEST, rocket.portrait().get(CHEST), nbt, player.level().registryAccess());
        int n = 0;
        if (be instanceof Container c) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                if (c.getItem(i).is(item)) {
                    n += c.getItem(i).getCount();
                }
            }
        }
        return n;
    }

    private static int playerCount(ServerPlayer player, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    // ============================================================================================================ o roteiro (um tick do cliente por chamada; true no fim)

    static boolean tick(Minecraft mc) {
        t++;
        if (mc.player == null || mc.level == null) {
            return false;
        }
        if (mc.gui.screen() != null && !(mc.player.containerMenu instanceof ChestMenu)) {
            mc.gui.setScreen(null);
        }
        switch (phase) {
            case 0 -> { // acomoda: a linha do tempo da pose da nave se enche
                aimAtCell(mc, DOOR_UP, 0.5);
                if (t >= 40) {
                    go(1);
                }
            }
            case 1 -> { // mira e contorno na porta
                aimAtCell(mc, DOOR_UP, 0.5);
                DeckAim.Target tg = DeckAim.compute(mc);
                if (t == 5) {
                    ShipDevtest.check("DECKUSE: a mira acerta a porta no retrato (nave em voo a 10 b/t, girando e inclinada)", targetIs(tg, DOOR_LOW, DOOR_UP),
                            tg == null ? "nada" : tg.pos() + " face " + tg.face() + String.format(Locale.ROOT, " a %.3f blocos", tg.t()));
                    ShipDevtest.check("DECKUSE: o alvo da mira (contorno) está ligado nos quadros", targetIs(DeckAim.target(), DOOR_LOW, DOOR_UP),
                            DeckAim.target() == null ? "nada" : DeckAim.target().pos().toString());
                }
                if (t == 8) {
                    shot(mc, "sdu_1_contorno_porta");
                }
                if (t >= 14) {
                    maxXBlocked = 0.0;
                    go(2);
                }
            }
            case 2 -> { // a porta fechada barra
                mc.options.keyUp.setDown(true);
                maxXBlocked = Math.max(maxXBlocked, DeckClient.state().pos.x);
                if (t >= 40) {
                    mc.options.keyUp.setDown(false);
                    ShipDevtest.check("DECKUSE: a porta fechada barra o jogador (colisão do convés)", maxXBlocked < 2.8,
                            String.format(Locale.ROOT, "x máximo %.3f (a porta começa em 3,0; o jogador tem meia largura 0,3)", maxXBlocked));
                    go(3);
                }
            }
            case 3 -> { // clique na porta
                if (t == 1) {
                    deltas0 = DeckAim.deltaCount();
                    aimAtCell(mc, DOOR_UP, 0.5);
                    portraitIdBefore = clientRocket(mc).portrait().id();
                }
                if (t == 3) {
                    aimAtCell(mc, DOOR_UP, 0.5);
                    boolean sent = DeckAim.useNow(mc);
                    ShipDevtest.check("DECKUSE: o clique na porta foi enviado pelo caminho do uso", sent, "enviado " + sent);
                }
                if (t > 3) {
                    RocketEntity cr = clientRocket(mc);
                    boolean open = cr.portrait().get(DOOR_LOW).getValue(DoorBlock.OPEN) && cr.portrait().get(DOOR_UP).getValue(DoorBlock.OPEN);
                    if (open || t > 60) {
                        boolean[] server = ShipDevtest.onServer(mc, s -> {
                            RocketEntity r = serverRocket(s);
                            return new boolean[] {r.portrait().get(DOOR_LOW).getValue(DoorBlock.OPEN), r.portrait().get(DOOR_UP).getValue(DoorBlock.OPEN)};
                        });
                        UUID serverId = ShipDevtest.onServer(mc, s -> serverRocket(s).portrait().id());
                        ShipDevtest.check("DECKUSE: a porta abriu no retrato do servidor (as duas metades)", server[0] && server[1], server[0] + "/" + server[1]);
                        ShipDevtest.check("DECKUSE: o cliente recebeu o delta e o retrato dele mudou (porta aberta)", open && DeckAim.deltaCount() > deltas0,
                                "deltas recebidos " + (DeckAim.deltaCount() - deltas0) + ", aberta no cliente " + open);
                        ShipDevtest.check("DECKUSE: o retrato do cliente é o mesmo do servidor (id) e o id mudou", cr.portrait().id().equals(serverId) && !serverId.equals(portraitIdBefore),
                                cr.portrait().id() + " / " + serverId);
                        shot(mc, "sdu_2_porta_aberta");
                        go(4);
                    }
                }
            }
            case 4 -> { // passa pela porta aberta
                mc.options.keyUp.setDown(true);
                double x = DeckClient.state().pos.x;
                if (x >= 4.6 || t > 120) {
                    mc.options.keyUp.setDown(false);
                    long rejected = DeckCrew.REJECTED.get() - rejected0;
                    ShipDevtest.check("DECKUSE: com a porta aberta o jogador passa (colisão usa o estado novo) sem recusas do servidor", x >= 4.5 && rejected == 0,
                            String.format(Locale.ROOT, "x final %.3f, recusas %d, correções do cliente %d", x, rejected, DeckClient.correctionCount()));
                    go(5);
                }
            }
            case 5 -> { // mira e abre o baú
                if (t == 1) {
                    ShipDevtest.onServer(mc, s -> {
                        // os itens a por no baú: 7 tábuas no inventário do jogador
                        s.getPlayerList().getPlayers().get(0).getInventory().add(new ItemStack(Items.OAK_PLANKS, 7));
                        return null;
                    });
                }
                aimAtCell(mc, CHEST, 0.45);
                if (t == 6) {
                    DeckAim.Target tg = DeckAim.compute(mc);
                    ShipDevtest.check("DECKUSE: a mira acerta o baú no retrato", targetIs(tg, CHEST), tg == null ? "nada" : tg.pos().toString());
                    shot(mc, "sdu_3_contorno_bau");
                }
                if (t == 9) {
                    boolean sent = DeckAim.useNow(mc);
                    ShipDevtest.check("DECKUSE: o clique no baú foi enviado", sent, "enviado " + sent);
                    uses0 = DeckAim.useCount();
                }
                if (t > 9 && mc.player.containerMenu instanceof ChestMenu menu) {
                    ItemStack s0 = menu.getSlot(0).getItem();
                    ItemStack s1 = menu.getSlot(1).getItem();
                    ShipDevtest.check("DECKUSE: o menu do baú abriu em voo com o conteúdo do retrato (12 diamantes, 20 pedregulhos)",
                            s0.is(Items.DIAMOND) && s0.getCount() == 12 && s1.is(Items.COBBLESTONE) && s1.getCount() == 20,
                            "slot 0 " + s0 + ", slot 1 " + s1 + ", linhas " + menu.getRowCount());
                    int proxies = ShipDevtest.onServer(mc, s -> PortraitContainers.openCount());
                    ShipDevtest.check("DECKUSE: um proxy aberto no servidor", proxies == 1, "proxies " + proxies);
                    deltas0 = DeckAim.deltaCount();
                    totalsBefore = ShipDevtest.onServer(mc, s -> itemTotals(s.getPlayerList().getPlayers().get(0), serverRocket(s)));
                    SpaceHorizons.LOGGER.info(TAG + "totais antes: {}", totalsBefore);
                    go(6);
                } else if (t > 80) {
                    ShipDevtest.check("DECKUSE: o menu do baú abriu em voo", false, "não abriu em 80 ticks");
                    go(9);
                }
            }
            case 6 -> { // espera o menu desenhar, tira o print e mexe nos itens
                if (t == 8) {
                    shot(mc, "sdu_4_menu_bau_em_voo");
                }
                if (t == 14 && mc.player.containerMenu instanceof ChestMenu menu) {
                    // tira os diamantes (shift+clique no slot 0) e põe as tábuas (shift+clique no slot do inventário que as tem)
                    mc.gameMode.handleContainerInput(menu.containerId, 0, 0, ContainerInput.QUICK_MOVE, mc.player);
                    int planks = -1;
                    for (int i = menu.getRowCount() * 9; i < menu.slots.size(); i++) {
                        if (menu.getSlot(i).getItem().is(Items.OAK_PLANKS)) {
                            planks = i;
                            break;
                        }
                    }
                    if (planks >= 0) {
                        mc.gameMode.handleContainerInput(menu.containerId, planks, 0, ContainerInput.QUICK_MOVE, mc.player);
                    }
                    SpaceHorizons.LOGGER.info(TAG + "tirou os diamantes (slot 0) e pôs as tábuas (slot {})", planks);
                }
                if (t >= 40) {
                    int[] counts = ShipDevtest.onServer(mc, s -> {
                        ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                        RocketEntity r = serverRocket(s);
                        return new int[] {chestCount(p, r, Items.DIAMOND), playerCount(p, Items.DIAMOND), chestCount(p, r, Items.OAK_PLANKS), playerCount(p, Items.OAK_PLANKS)};
                    });
                    ShipDevtest.check("DECKUSE: o NBT do baú no retrato do servidor confere (diamantes saíram, tábuas entraram)",
                            counts[0] == 0 && counts[1] == 12 && counts[2] == 7 && counts[3] == 0,
                            "baú: diamantes " + counts[0] + ", tábuas " + counts[2] + "; jogador: diamantes " + counts[1] + ", tábuas " + counts[3]);
                    Map<String, Integer> after = ShipDevtest.onServer(mc, s -> itemTotals(s.getPlayerList().getPlayers().get(0), serverRocket(s)));
                    SpaceHorizons.LOGGER.info(TAG + "totais depois: {}", after);
                    Map<String, Integer> expected = new HashMap<>(totalsBefore);
                    ShipDevtest.check("DECKUSE: nada duplicado nem perdido (totais de baú + jogador iguais, tábuas e diamantes incluídos)", after.equals(expected),
                            "antes " + totalsBefore + " depois " + after);
                    CompoundTag serverNbt = ShipDevtest.onServer(mc, s -> serverRocket(s).portrait().blockEntities().get(CHEST.asLong()).copy());
                    CompoundTag clientNbt = clientRocket(mc).portrait().blockEntities().get(CHEST.asLong());
                    ShipDevtest.check("DECKUSE: o NBT do baú no retrato do cliente é o do servidor (deltas)", serverNbt.equals(clientNbt) && DeckAim.deltaCount() > deltas0,
                            "deltas " + (DeckAim.deltaCount() - deltas0) + ", iguais " + serverNbt.equals(clientNbt));
                    mc.player.closeContainer();
                    go(7);
                }
            }
            case 7 -> { // fechou: o proxy some, o conteúdo fica no retrato
                if (t >= 20) {
                    int proxies = ShipDevtest.onServer(mc, s -> PortraitContainers.openCount());
                    boolean closed = !(mc.player.containerMenu instanceof ChestMenu);
                    int diamondsInChest = ShipDevtest.onServer(mc, s -> chestCount(s.getPlayerList().getPlayers().get(0), serverRocket(s), Items.DIAMOND));
                    int planksInChest = ShipDevtest.onServer(mc, s -> chestCount(s.getPlayerList().getPlayers().get(0), serverRocket(s), Items.OAK_PLANKS));
                    ShipDevtest.check("DECKUSE: ao fechar o menu o proxy some e o conteúdo novo continua no retrato", closed && proxies == 0 && diamondsInChest == 0 && planksInChest == 7,
                            "fechado " + closed + ", proxies " + proxies + ", diamantes no baú " + diamondsInChest + ", tábuas no baú " + planksInChest);
                    go(8);
                }
            }
            case 8 -> { // a fornalha: aviso
                aimAtCell(mc, FURNACE, 0.5);
                if (t == 6) {
                    DeckAim.Target tg = DeckAim.compute(mc);
                    ShipDevtest.check("DECKUSE: a mira acerta a fornalha no retrato", targetIs(tg, FURNACE), tg == null ? "nada" : tg.pos().toString());
                    warnings0 = PortraitUse.MACHINE_WARNINGS.get();
                    DeckAim.useNow(mc);
                }
                if (t == 14) {
                    int warnings = PortraitUse.MACHINE_WARNINGS.get() - warnings0;
                    ShipDevtest.check("DECKUSE: o clique na fornalha em movimento mostra o aviso na barra de ação (e não abre a fornalha)",
                            warnings == 1 && !(mc.player.containerMenu instanceof ChestMenu) && mc.gui.screen() == null, "avisos " + warnings);
                    shot(mc, "sdu_5_aviso_fornalha");
                }
                if (t >= 20) {
                    go(9);
                }
            }
            case 9 -> { // senta no assento
                if (t == 1) {
                    aimAtCell(mc, SEAT, 0.3);
                }
                aimAtCell(mc, SEAT, 0.3);
                if (t == 6) {
                    DeckAim.Target tg = DeckAim.compute(mc);
                    ShipDevtest.check("DECKUSE: a mira acerta o assento no retrato", targetIs(tg, SEAT), tg == null ? "nada" : tg.pos().toString());
                    DeckAim.useNow(mc);
                    seatErrors.clear();
                }
                if (t > 6) {
                    RocketEntity cr = clientRocket(mc);
                    if (mc.player.isPassenger() && mc.player.getVehicle() == cr) {
                        if (t > 30) {
                            // posição do jogador na pose da nave: o assento (centro do bloco, na altura do assento)
                            double[] errs = ShipDevtest.onServer(mc, s -> {
                                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                                RocketEntity r = serverRocket(s);
                                Vector3d local = r.deckFrame().toLocal(p.getX(), p.getY(), p.getZ());
                                double server = local.distance(SEAT.getX() + 0.5, SEAT.getY() + 0.3, SEAT.getZ() + 0.5);
                                return new double[] {server, r.seatOf(p).equals(SEAT) ? 1 : 0, r.getControllingPassenger() == p ? 1 : 0, DeckCrew.isCrew(p) ? 1 : 0};
                            });
                            Vec3 cp = mc.player.position();
                            Vector3d clientLocal = cr.deckFrame().toLocal(cp.x, cp.y, cp.z);
                            double clientErr = clientLocal.distance(SEAT.getX() + 0.5, SEAT.getY() + 0.3, SEAT.getZ() + 0.5);
                            ShipDevtest.check("DECKUSE: sentou no assento do retrato como passageiro da nave (posição do assento na pose, servidor e cliente)",
                                    errs[0] < 0.45 && clientErr < 0.45 && errs[1] == 1 && cr.seatOf(mc.player).equals(SEAT),
                                    String.format(Locale.ROOT, "erro servidor %.3f, cliente %.3f blocos; assento no servidor %s, no cliente %s", errs[0], clientErr, errs[1] == 1, cr.seatOf(mc.player)));
                            ShipDevtest.check("DECKUSE: quem senta no assento comum não pilota e deixou o convés a pé", errs[2] == 0 && errs[3] == 0 && !DeckClient.isAboard(),
                                    "piloto " + (errs[2] == 1) + ", tripulante no servidor " + (errs[3] == 1) + ", a bordo no cliente " + DeckClient.isAboard());
                            shot(mc, "sdu_6_sentado");
                            go(10);
                        }
                    } else if (t > 80) {
                        ShipDevtest.check("DECKUSE: sentou no assento do retrato", false, "não sentou em 80 ticks");
                        go(13);
                    }
                }
            }
            case 10 -> { // um segundo passageiro (um porco) na ponte enquanto o jogador está no assento
                if (t == 1) {
                    int[] r = ShipDevtest.onServer(mc, s -> {
                        ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                        RocketEntity rocket = serverRocket(s);
                        ServerLevel level = (ServerLevel) p.level();
                        Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.TRIGGERED);
                        pig.setPos(rocket.position());
                        level.addFreshEntity(pig);
                        boolean rode = pig.startRiding(rocket);
                        return new int[] {rode ? 1 : 0, rocket.getPassengers().size()};
                    });
                    ShipDevtest.check("DECKUSE: dois passageiros em assentos diferentes ao mesmo tempo (jogador no assento, outro na ponte)", r[0] == 1 && r[1] == 2,
                            "o porco sentou " + (r[0] == 1) + ", passageiros " + r[1]);
                }
                if (t == 15) {
                    double err = ShipDevtest.onServer(mc, s -> {
                        RocketEntity rocket = serverRocket(s);
                        for (net.minecraft.world.entity.Entity e : rocket.getPassengers()) {
                            if (e instanceof Pig pig) {
                                Vector3d local = rocket.deckFrame().toLocal(pig.getX(), pig.getY(), pig.getZ());
                                double d = local.distance(COCKPIT.getX() + 0.5, COCKPIT.getY() + 0.3, COCKPIT.getZ() + 0.5);
                                pig.stopRiding();
                                pig.discard();
                                return d;
                            }
                        }
                        return -1.0;
                    });
                    ShipDevtest.check("DECKUSE: o segundo passageiro fica na posição do assento dele (a ponte), não na do jogador", err >= 0 && err < 0.45,
                            String.format(Locale.ROOT, "erro %.3f blocos", err));
                    go(11);
                }
            }
            case 11 -> { // levanta (shift): volta ao convés em pé
                if (t < 6) {
                    mc.options.keyShift.setDown(true);
                } else {
                    mc.options.keyShift.setDown(false);
                }
                if (t > 6 && DeckClient.isAboard() && !mc.player.isPassenger()) {
                    boolean[] r = ShipDevtest.onServer(mc, s -> {
                        ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                        return new boolean[] {DeckCrew.isCrew(p), !p.isPassenger()};
                    });
                    DeckState st = DeckClient.state();
                    double distSeat = st.pos.distance(SEAT.getX() + 0.5, SEAT.getY(), SEAT.getZ() + 0.5);
                    boolean fits = DeckStand.fits(clientRocket(mc).portrait().deckColliders(), new Vec3(st.pos.x, st.pos.y, st.pos.z), 0.6, 1.8);
                    ShipDevtest.check("DECKUSE: levantar devolve o jogador ao convés em pé ao lado do assento (servidor e cliente)", r[0] && r[1] && fits && distSeat < 2.6,
                            String.format(Locale.ROOT, "tripulante no servidor %s, a %.2f blocos do assento, cabe em pé %s, pos local (%.2f, %.2f, %.2f)", r[0], distSeat, fits, st.pos.x, st.pos.y, st.pos.z));
                    go(12);
                } else if (t > 120) {
                    mc.options.keyShift.setDown(false);
                    ShipDevtest.check("DECKUSE: levantar devolve o jogador ao convés", false, "não voltou ao convés em 120 ticks");
                    go(13);
                }
            }
            case 12 -> { // a ponte: senta como piloto e levanta de novo
                if (t < 20) {
                    break;
                }
                if (t == 20) {
                    aimAtCell(mc, COCKPIT, 0.5);
                }
                aimAtCell(mc, COCKPIT, 0.5);
                if (t == 24) {
                    DeckAim.Target tg = DeckAim.compute(mc);
                    ShipDevtest.check("DECKUSE: a mira acerta a ponte no retrato", targetIs(tg, COCKPIT), tg == null ? "nada" : tg.pos().toString());
                    DeckAim.useNow(mc);
                }
                if (t > 24 && t < 60) {
                    RocketEntity cr = clientRocket(mc);
                    if (mc.player.isPassenger() && mc.player.getVehicle() == cr && t > 35) {
                        boolean pilot = cr.getControllingPassenger() == mc.player;
                        boolean serverPilot = ShipDevtest.onServer(mc, s -> serverRocket(s).getControllingPassenger() == s.getPlayerList().getPlayers().get(0));
                        ShipDevtest.check("DECKUSE: sentar na ponte (cockpit do retrato) faz do jogador o piloto", pilot && serverPilot && cr.seatOf(mc.player).equals(COCKPIT),
                                "piloto no cliente " + pilot + ", no servidor " + serverPilot);
                        t = 100;
                    }
                }
                if (t >= 100 && t < 106) {
                    mc.options.keyShift.setDown(true);
                } else if (t >= 106) {
                    mc.options.keyShift.setDown(false);
                    if (DeckClient.isAboard() && !mc.player.isPassenger()) {
                        ShipDevtest.check("DECKUSE: levantar da ponte também volta ao convés", true, "a bordo");
                        go(13);
                    } else if (t > 220) {
                        ShipDevtest.check("DECKUSE: levantar da ponte também volta ao convés", false, "não voltou");
                        go(13);
                    }
                }
                if (t >= 60 && t < 100) {
                    ShipDevtest.check("DECKUSE: sentar na ponte (cockpit do retrato) faz do jogador o piloto", false, "não sentou em 36 ticks");
                    t = 100;
                }
            }
            case 13 -> { // pouso: a conversão em blocos leva o retrato (porta aberta, baú novo)
                mc.options.keyShift.setDown(false);
                mc.options.keyUp.setDown(false);
                if (t == 5) {
                    String result = ShipDevtest.onServer(mc, server -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        ServerLevel level = (ServerLevel) player.level();
                        RocketEntity rocket = serverRocket(server);
                        lastPortrait = rocket.portrait();
                        rocket.setDevtestScript(null);
                        rocket.setFlightMode(FlightMode.LANDED);
                        rocket.setAttitude(new Quaternionf());
                        rocket.setDeltaMovement(Vec3.ZERO);
                        rocket.setPos(Math.floor(player.getX()) + 0.5, 300.0 + SY / 2.0, Math.floor(player.getZ()) + 0.5);
                        BlockPos origin = RocketLanding.originFor(rocket.position(), SX, SY, SZ);
                        try {
                            RocketLanding.land(level, rocket);
                        } catch (com.spacehorizons.rocket.RocketException e) {
                            return "falhou: " + e.getMessage();
                        }
                        BlockState low = level.getBlockState(origin.offset(DOOR_LOW));
                        BlockState up = level.getBlockState(origin.offset(DOOR_UP));
                        BlockEntity chest = level.getBlockEntity(origin.offset(CHEST));
                        int diamonds = 0;
                        int planks = 0;
                        int cobble = 0;
                        if (chest instanceof Container c) {
                            for (int i = 0; i < c.getContainerSize(); i++) {
                                ItemStack st = c.getItem(i);
                                if (st.is(Items.DIAMOND)) {
                                    diamonds += st.getCount();
                                } else if (st.is(Items.OAK_PLANKS)) {
                                    planks += st.getCount();
                                } else if (st.is(Items.COBBLESTONE)) {
                                    cobble += st.getCount();
                                }
                            }
                        }
                        boolean furnaceOk = level.getBlockEntity(origin.offset(FURNACE)) instanceof FurnaceBlockEntity f && f.getItem(0).is(Items.RAW_IRON) && f.getItem(0).getCount() == 8;
                        boolean doorOpen = low.is(Blocks.OAK_DOOR) && up.is(Blocks.OAK_DOOR) && low.getValue(DoorBlock.OPEN) && up.getValue(DoorBlock.OPEN)
                                && low.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
                        return String.format(Locale.ROOT, "ok porta_aberta=%s diamantes=%d tabuas=%d pedregulhos=%d fornalha=%s origem=%s", doorOpen, diamonds, planks, cobble, furnaceOk, origin);
                    });
                    SpaceHorizons.LOGGER.info(TAG + "pouso: {}", result);
                    ShipDevtest.check("DECKUSE: a nave pousou (conversão em blocos)", result.startsWith("ok"), result);
                    ShipDevtest.check("DECKUSE: nos blocos do mundo a porta está aberta e o baú tem o conteúdo novo (0 diamantes, 7 tábuas, 20 pedregulhos), fornalha intacta",
                            result.contains("porta_aberta=true") && result.contains("diamantes=0") && result.contains("tabuas=7") && result.contains("pedregulhos=20") && result.contains("fornalha=true"),
                            result);
                    return true;
                }
            }
            default -> {
                return true;
            }
        }
        return false;
    }
}
