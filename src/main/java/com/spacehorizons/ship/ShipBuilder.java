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
