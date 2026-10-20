package com.spacehorizons.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Gera a nave de TESTE do spike S2 numa caixa do estaleiro (o comando {@code /spacehorizons ship create}): casco oco de piso, paredes
 * e teto, uma faixa de janelas de vidro em volta (panorama), uma janela no teto e um vidro no piso (para ver a Terra embaixo),
 * lâmpadas (lanternas do mar) em grade no piso e no teto, e uma cabine com porta de madeira, baú com itens, fornalha acesa e uma
 * lâmpada de redstone com alavanca. Tudo vanilla, sem blocos do mod: o estaleiro só guarda blocos reais.
 *
 * <p>As posições de interesse saem de {@link #layout}, que o servidor e o autoteste usam do mesmo jeito (cubo de 40 a 128 de lado).
 */
public final class ShipBuilder {
    /** Itens que o baú recebe: o autoteste confere a contagem total depois de salvar e recarregar. */
    public static final int CHEST_DIAMONDS = 64;
    public static final int CHEST_IRON = 32;
    public static final int CHEST_PEARLS = 16;
    public static final int CHEST_APPLES = 3;
    public static final int CHEST_TOTAL = CHEST_DIAMONDS + CHEST_IRON + CHEST_PEARLS + CHEST_APPLES;
    public static final int MIN_SIZE = 40;

    private static final int HULL_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private ShipBuilder() {
    }

    /** Posições (blocos do estaleiro) dos elementos da nave de teste. */
    public record Layout(ShipYard.Box box, BlockPos spawn, BlockPos doorLower, BlockPos chest, BlockPos furnace, BlockPos lamp,
            BlockPos lever, BlockPos lightProbe, BlockPos floorWindowCenter, BlockPos ceilingWindowCenter, BlockPos cabinMin,
            BlockPos cabinMax) {
        /** Onde o jogador aparece: o centro do salão, em cima do piso, olhando para a cabine (leste). */
        public Vec3 spawnPoint() {
            return new Vec3(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        }
    }

    public static Layout layout(ShipYard.Box box) {
        int cx = box.minX() + box.sizeX() / 2;
        int cz = box.minZ() + box.sizeZ() / 2;
        int floor = box.minY() + 1;                              // primeiro bloco acima do piso
        BlockPos spawn = new BlockPos(cx, floor, cz);
        // cabine: parede oeste em cx+6, parede leste em cx+15, norte em cz-4, sul em cz+5; interior 8x8, pé-direito 5
        BlockPos cabinMin = new BlockPos(cx + 6, box.minY(), cz - 4);
        BlockPos cabinMax = new BlockPos(cx + 15, box.minY() + 6, cz + 5);
        return new Layout(box, spawn,
                new BlockPos(cx + 6, floor, cz),                 // porta (metade de baixo) na parede oeste
                new BlockPos(cx + 14, floor, cz - 2),            // baú, encostado na parede leste
                new BlockPos(cx + 14, floor, cz + 2),            // fornalha acesa, encostada na parede leste
                new BlockPos(cx + 10, floor + 3, cz - 4),        // lâmpada de redstone, embutida na parede norte da cabine
                new BlockPos(cx + 10, floor + 3, cz - 3),        // alavanca presa na face sul da lâmpada
                new BlockPos(cx + 9, floor + 3, cz - 3),         // onde se mede a luz (ao lado da lâmpada, longe da fornalha)
                new BlockPos(cx - 10, box.minY(), cz - 10),      // centro do vidro do piso (9x9)
                new BlockPos(cx, box.minY() + box.sizeY() - 1, cz),   // centro da janela do teto (17x17)
                cabinMin, cabinMax);
    }

    /** Escreve a nave de teste na caixa. Os chunks da caixa precisam estar carregáveis (o getChunk os carrega). */
    public static Layout build(ServerLevel level, ShipYard.Box box) {
        if (box.sizeX() < MIN_SIZE || box.sizeY() < MIN_SIZE || box.sizeZ() < MIN_SIZE) {
            throw new IllegalArgumentException("a nave de teste precisa de pelo menos " + MIN_SIZE + " de lado");
        }
        Layout layout = layout(box);
        // carrega (e gera, vazios) todos os chunks da caixa antes de escrever
        for (int cx = box.chunkMinX(); cx <= box.chunkMaxX(); cx++) {
            for (int cz = box.chunkMinZ(); cz <= box.chunkMaxZ(); cz++) {
                level.getChunk(cx, cz);
            }
        }
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState floorBlock = Blocks.POLISHED_ANDESITE.defaultBlockState();
        BlockState ceilingBlock = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState lantern = Blocks.SEA_LANTERN.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int x0 = box.minX();
        int y0 = box.minY();
        int z0 = box.minZ();
        int sx = box.sizeX();
        int sy = box.sizeY();
        int sz = box.sizeZ();
        int cx = sx / 2;
        int cz = sz / 2;
        int bandTop = Math.min(sy - 8, 40);

        // casco: piso, teto e quatro paredes
        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                set(level, p.set(x0 + x, y0, z0 + z), floorBlock);
                set(level, p.set(x0 + x, y0 + sy - 1, z0 + z), ceilingBlock);
            }
        }
        for (int y = 1; y < sy - 1; y++) {
            for (int x = 0; x < sx; x++) {
                set(level, p.set(x0 + x, y0 + y, z0), wall);
                set(level, p.set(x0 + x, y0 + y, z0 + sz - 1), wall);
            }
            for (int z = 1; z < sz - 1; z++) {
                set(level, p.set(x0, y0 + y, z0 + z), wall);
                set(level, p.set(x0 + sx - 1, y0 + y, z0 + z), wall);
            }
        }
        // faixa de janelas (panorama) nas quatro paredes, de y=8 até bandTop, a 6 blocos dos cantos
        for (int y = 8; y <= bandTop; y++) {
            for (int x = 6; x < sx - 6; x++) {
                set(level, p.set(x0 + x, y0 + y, z0), glass);
                set(level, p.set(x0 + x, y0 + y, z0 + sz - 1), glass);
            }
            for (int z = 6; z < sz - 6; z++) {
                set(level, p.set(x0, y0 + y, z0 + z), glass);
                set(level, p.set(x0 + sx - 1, y0 + y, z0 + z), glass);
            }
        }
        // janela do teto (17x17 no centro) e vidro do piso (9x9 a noroeste do centro)
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                set(level, p.set(x0 + cx + dx, y0 + sy - 1, z0 + cz + dz), glass);
            }
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                set(level, p.set(layout.floorWindowCenter().getX() + dx, y0, layout.floorWindowCenter().getZ() + dz), glass);
            }
        }
        // lâmpadas: lanternas do mar no piso a cada 8 blocos (fora da cabine e do vidro) e no teto a cada 16
        for (int x = 4; x < sx - 3; x += 8) {
            for (int z = 4; z < sz - 3; z += 8) {
                p.set(x0 + x, y0, z0 + z);
                if (!insideXZ(p, layout.cabinMin(), layout.cabinMax(), 1) && !insideXZ(p, layout.floorWindowCenter(), layout.floorWindowCenter(), 5)) {
                    set(level, p, lantern);
                }
            }
        }
        for (int x = 8; x < sx - 7; x += 16) {
            for (int z = 8; z < sz - 7; z += 16) {
                p.set(x0 + x, y0 + sy - 1, z0 + z);
                if (!insideXZ(p, layout.ceilingWindowCenter(), layout.ceilingWindowCenter(), 9)) {
                    set(level, p, lantern);
                }
            }
        }

        buildCabin(level, layout, wall, ceilingBlock);
        return layout;
    }

    // ---------------------------------------------------------------- nave de voo (fase 1c): ponte, assentos, motores, tanques, janelas

    /** Tamanho padrão da nave de voo de teste: 32 de largura (X), 20 de altura (Y) e 56 de comprimento (Z); a proa é o norte. */
    public static final int FLIGHT_SX = 32;
    public static final int FLIGHT_SY = 20;
    public static final int FLIGHT_SZ = 56;
    /** Quantos de cada peça a nave de voo tem (o que o censo precisa achar). */
    public static final int FLIGHT_ENGINES = 4;
    public static final int FLIGHT_TANKS = 8;
    public static final int FLIGHT_SEATS = 3;
    public static final int FLIGHT_AIRLOCKS = 4;
    /** De quantos em quantos blocos há uma lanterna no piso da nave de voo. */
    public static final int FLIGHT_FLOOR_LIGHT_STEP = 6;

    /**
     * Posições e contagem esperada da nave de voo de teste. {@code pilotSeat} é o assento da ponte (olhando para a proa, norte),
     * {@code bridge} o console na frente dele, {@code hall} onde o jogador aparece em pé (sala da ponte), {@code bulkheadDoor} o canto
     * de baixo/oeste da abertura do anteparo (2 de largura e 2 de altura, de eclusas), e {@code expected} o censo que o scanner deve achar.
     */
    public record FlightLayout(ShipYard.Box box, BlockPos bridge, BlockPos pilotSeat, java.util.List<BlockPos> seats, BlockPos hall, BlockPos bulkheadDoor,
            java.util.List<BlockPos> engines, java.util.List<BlockPos> tanks, int tankFuelMb, ShipCensus expected) {
        public Vec3 hallPoint() {
            return new Vec3(hall.getX() + 0.5, hall.getY(), hall.getZ() + 0.5);
        }
    }

    /** O layout da nave de voo na caixa (sem escrever nada); o censo esperado só vale depois de {@link #buildFlightShip}. */
    public static FlightLayout flightLayout(ShipYard.Box box) {
        int x0 = box.minX();
        int y0 = box.minY() + 1;
        int z0 = box.minZ();
        int cx = box.sizeX() / 2;
        BlockPos pilot = new BlockPos(x0 + cx, y0, z0 + 6);
        java.util.List<BlockPos> seats = java.util.List.of(pilot, new BlockPos(x0 + cx - 3, y0, z0 + 6), new BlockPos(x0 + cx + 3, y0, z0 + 6));
        java.util.List<BlockPos> engines = new java.util.ArrayList<>();
        for (int dx : new int[] {-8, -4, 3, 7}) {
            engines.add(new BlockPos(x0 + cx + dx, y0, z0 + box.sizeZ() - 2));
        }
        java.util.List<BlockPos> tanks = new java.util.ArrayList<>();
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 4; i++) {
                tanks.add(new BlockPos(x0 + (side == 0 ? 2 : box.sizeX() - 3), y0, z0 + 24 + i * 2));
            }
        }
        return new FlightLayout(box, new BlockPos(x0 + cx, y0, z0 + 3), pilot, seats, new BlockPos(x0 + cx, y0, z0 + 11),
                new BlockPos(x0 + cx - 1, y0, z0 + 20), engines, tanks, 4000, ShipCensus.EMPTY);
    }

    /**
     * Escreve a nave de voo de teste na caixa: casco de aço oco, janelas de nave nas paredes (a proa e os dois lados, para ver o céu girar),
     * a ponte e três assentos na sala da frente, um anteparo com uma eclusa (quatro células) no meio, e atrás dele oito tanques cheios e
     * quatro motores de impulso virados para a popa; sea lanterns no teto. A caixa precisa ser a de {@link #FLIGHT_SX} x {@link #FLIGHT_SY} x
     * {@link #FLIGHT_SZ} (ou maior em X e Z, centrada na célula como todas). Devolve o layout com o censo esperado.
     */
    public static FlightLayout buildFlightShip(ServerLevel level, ShipYard.Box box) {
        if (box.sizeX() < FLIGHT_SX || box.sizeY() < FLIGHT_SY || box.sizeZ() < FLIGHT_SZ) {
            throw new IllegalArgumentException("a nave de voo precisa de pelo menos " + FLIGHT_SX + "x" + FLIGHT_SY + "x" + FLIGHT_SZ);
        }
        FlightLayout layout = flightLayout(box);
        for (int cx = box.chunkMinX(); cx <= box.chunkMaxX(); cx++) {
            for (int cz = box.chunkMinZ(); cz <= box.chunkMaxZ(); cz++) {
                level.getChunk(cx, cz);
            }
        }
        BlockState hull = com.spacehorizons.content.ModBlocks.ROCKET_HULL.get().defaultBlockState();
        BlockState window = com.spacehorizons.content.ModBlocks.SHIP_WINDOW.get().defaultBlockState();
        BlockState lantern = Blocks.SEA_LANTERN.defaultBlockState();
        int x0 = box.minX();
        int y0 = box.minY();
        int z0 = box.minZ();
        int sx = box.sizeX();
        int sy = box.sizeY();
        int sz = box.sizeZ();
        int[] counts = new int[ShipBlockKind.values().length];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        // casco: piso, teto e quatro paredes
        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                put(level, p.set(x0 + x, y0, z0 + z), hull, counts);
                put(level, p.set(x0 + x, y0 + sy - 1, z0 + z), hull, counts);
            }
        }
        for (int y = 1; y < sy - 1; y++) {
            for (int x = 0; x < sx; x++) {
                put(level, p.set(x0 + x, y0 + y, z0), hull, counts);
                put(level, p.set(x0 + x, y0 + y, z0 + sz - 1), hull, counts);
            }
            for (int z = 1; z < sz - 1; z++) {
                put(level, p.set(x0, y0 + y, z0 + z), hull, counts);
                put(level, p.set(x0 + sx - 1, y0 + y, z0 + z), hull, counts);
            }
        }
        // janelas: a proa (norte) larga e baixa, os dois lados da sala da ponte e do compartimento de carga
        for (int y = 2; y <= 12; y++) {
            for (int x = 4; x < sx - 4; x++) {
                put(level, p.set(x0 + x, y0 + y, z0), window, counts);
            }
            for (int z = 6; z <= 40; z++) {
                put(level, p.set(x0, y0 + y, z0 + z), window, counts);
                put(level, p.set(x0 + sx - 1, y0 + y, z0 + z), window, counts);
            }
        }
        // as janelas foram escritas com o estado padrão (setBlock não calcula as conexões do próprio bloco): liga a textura conectada
        connectWindows(level, box, y0 + 2, y0 + 12);
        // anteparo no meio (z = 20) com a abertura de 2x2 fechada por eclusas
        for (int y = 1; y < sy - 1; y++) {
            for (int x = 1; x < sx - 1; x++) {
                put(level, p.set(x0 + x, y0 + y, z0 + 20), hull, counts);
            }
        }
        BlockState airlock = com.spacehorizons.content.ModBlocks.SHIP_AIRLOCK.get().defaultBlockState();
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                put(level, p.set(layout.bulkheadDoor().getX() + dx, layout.bulkheadDoor().getY() + dy, layout.bulkheadDoor().getZ()), airlock, counts);
            }
        }
        // luzes: sea lanterns no teto, a cada 8 blocos (fora do anteparo)
        for (int x = 4; x < sx - 3; x += 8) {
            for (int z = 4; z < sz - 3; z += 8) {
                if (z != 20 && z != 21 && z != 19) {
                    put(level, p.set(x0 + x, y0 + sy - 1, z0 + z), lantern, counts);
                }
            }
        }
        // luz no piso: o teto fica a 18 blocos e a luz de bloco cai 1 por bloco, então só as lanternas do teto deixam o piso escuro (a nave não tem luz
        // de céu: D8). Uma lanterna do mar embutida no piso a cada 6 blocos (cinco colunas por 9 fileiras, sem o anteparo em z = 20) acende a sala da ponte, o
        // compartimento de carga e a sala dos motores
        for (int x = 4; x < sx - 2; x += FLIGHT_FLOOR_LIGHT_STEP) {
            for (int z = 4; z < sz - 2; z += FLIGHT_FLOOR_LIGHT_STEP) {
                if (Math.abs(z - 20) > 1) {
                    put(level, p.set(x0 + x, y0, z0 + z), lantern, counts);
                }
            }
        }
        // ponte, assentos, motores e tanques
        put(level, layout.bridge(), com.spacehorizons.content.ModBlocks.SHIP_BRIDGE.get().defaultBlockState()
                .setValue(com.spacehorizons.ship.block.ShipBridgeBlock.FACING, Direction.SOUTH), counts);
        BlockState seat = com.spacehorizons.content.ModBlocks.SHIP_SEAT.get().defaultBlockState()
                .setValue(com.spacehorizons.ship.block.ShipSeatBlock.FACING, Direction.NORTH);
        for (BlockPos pos : layout.seats()) {
            put(level, pos, seat, counts);
        }
        BlockState engine = com.spacehorizons.content.ModBlocks.SHIP_IMPULSE_ENGINE.get().defaultBlockState()
                .setValue(com.spacehorizons.ship.block.ShipImpulseEngineBlock.FACING, Direction.SOUTH);
        for (BlockPos pos : layout.engines()) {
            put(level, pos, engine, counts);
        }
        for (BlockPos pos : layout.tanks()) {
            put(level, pos, com.spacehorizons.content.ModBlocks.ROCKET_FUEL_TANK.get().defaultBlockState(), counts);
            if (level.getBlockEntity(pos) instanceof com.spacehorizons.rocket.RocketFuelTankBlockEntity tank) {
                tank.setFuel(layout.tankFuelMb());
            }
        }
        ShipCensus expected = ShipCensus.EMPTY;
        for (ShipBlockKind kind : ShipBlockKind.values()) {
            expected = expected.plus(kind, counts[kind.ordinal()]);
        }
        return new FlightLayout(box, layout.bridge(), layout.pilotSeat(), layout.seats(), layout.hall(), layout.bulkheadDoor(), layout.engines(),
                layout.tanks(), layout.tankFuelMb(), expected);
    }

    /** Calcula a textura conectada de todas as janelas da nave nas paredes (de {@code yMin} a {@code yMax}); o casco em volta não é janela. */
    private static void connectWindows(ServerLevel level, ShipYard.Box box, int yMin, int yMax) {
        net.minecraft.world.level.block.Block window = com.spacehorizons.content.ModBlocks.SHIP_WINDOW.get();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = yMin; y <= yMax; y++) {
            for (int x = box.minX(); x < box.minX() + box.sizeX(); x++) {
                for (int z = box.minZ(); z < box.minZ() + box.sizeZ(); z++) {
                    boolean wall = x == box.minX() || x == box.minX() + box.sizeX() - 1 || z == box.minZ() || z == box.minZ() + box.sizeZ() - 1;
                    if (wall && level.getBlockState(p.set(x, y, z)).is(window)) {
                        com.spacehorizons.content.connected.ConnectedFaces.connectAround(level, p, window,
                                com.spacehorizons.ship.block.ShipWindowBlock.rule());
                    }
                }
            }
        }
    }

    /** Escreve um bloco e o conta pelo papel dele (o censo esperado). */
    private static void put(ServerLevel level, BlockPos pos, BlockState state, int[] counts) {
        counts[ShipKinds.classify(level.getBlockState(pos)).ordinal()]--;       // o que estava aqui deixa de contar
        level.setBlock(pos, state, HULL_FLAGS);
        counts[ShipKinds.classify(state).ordinal()]++;
    }

    private static void buildCabin(ServerLevel level, Layout layout, BlockState wall, BlockState roof) {
        BlockPos min = layout.cabinMin();
        BlockPos max = layout.cabinMax();
        int floorY = min.getY() + 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        // paredes e teto (y de floorY até max.y); o piso é o do casco
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                boolean edge = x == min.getX() || x == max.getX() || z == min.getZ() || z == max.getZ();
                for (int y = floorY; y <= max.getY(); y++) {
                    if (y == max.getY()) {
                        set(level, p.set(x, y, z), roof);
                    } else if (edge) {
                        set(level, p.set(x, y, z), wall);
                    }
                }
            }
        }
        // porta de madeira na parede oeste (abre com a mão)
        BlockPos door = layout.doorLower();
        level.setBlock(door, doorState(DoubleBlockHalf.LOWER), Block.UPDATE_ALL);
        level.setBlock(door.above(), doorState(DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        // baú com itens
        level.setBlock(layout.chest(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST), Block.UPDATE_ALL);
        if (level.getBlockEntity(layout.chest()) instanceof Container chest) {
            chest.setItem(0, new ItemStack(Items.DIAMOND, CHEST_DIAMONDS));
            chest.setItem(1, new ItemStack(Items.IRON_INGOT, CHEST_IRON));
            chest.setItem(2, new ItemStack(Items.ENDER_PEARL, CHEST_PEARLS));
            ItemStack apples = new ItemStack(Items.GOLDEN_APPLE, CHEST_APPLES);
            apples.set(DataComponents.CUSTOM_NAME, Component.literal("Provisão da nave"));
            chest.setItem(13, apples);
            ((BlockEntity) chest).setChanged();
        }
        // fornalha acesa: minério e combustível dentro, para ela continuar queimando
        level.setBlock(layout.furnace(), Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.WEST)
                .setValue(AbstractFurnaceBlock.LIT, true), Block.UPDATE_ALL);
        if (level.getBlockEntity(layout.furnace()) instanceof Container furnace) {
            furnace.setItem(0, new ItemStack(Items.RAW_IRON, 64));
            furnace.setItem(1, new ItemStack(Items.COAL, 32));
            ((BlockEntity) furnace).setChanged();
        }
        // lâmpada de redstone e a alavanca presa na face sul dela (liga e desliga com a mão)
        level.setBlock(layout.lamp(), Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(layout.lever(), Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL)
                .setValue(LeverBlock.FACING, Direction.SOUTH).setValue(BlockStateProperties.POWERED, false), Block.UPDATE_ALL);
    }

    private static BlockState doorState(DoubleBlockHalf half) {
        return Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST).setValue(DoorBlock.HALF, half);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, HULL_FLAGS);
    }

    /** O ponto (só X e Z) está dentro do retângulo [a, b] ampliado em {@code margin}? */
    private static boolean insideXZ(BlockPos p, BlockPos a, BlockPos b, int margin) {
        return p.getX() >= Math.min(a.getX(), b.getX()) - margin && p.getX() <= Math.max(a.getX(), b.getX()) + margin
                && p.getZ() >= Math.min(a.getZ(), b.getZ()) - margin && p.getZ() <= Math.max(a.getZ(), b.getZ()) + margin;
    }
}
