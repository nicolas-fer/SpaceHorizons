package com.spacehorizons.ship;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.spacehorizons.content.ModBlocks;
import com.spacehorizons.content.ModEntities;
import com.spacehorizons.rocket.PortraitGrid;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketFuelTankBlockEntity;
import com.spacehorizons.rocket.RocketFuelTankItem;
import com.spacehorizons.rocket.RocketPortrait;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Gera por código um foguete GRANDE para o spike S3 (a montagem do jogo recusa mais de 1.024 blocos): um casco oco de aço com piso de
 * tijolo, janelas, lanternas no teto, motores, o cockpit no centro, tanques de combustível com cargas diferentes, uma fornalha, um barril e
 * baús enchidos nas paredes (27 pilhas cada, com nome, durabilidade e o componente {@code rocket_tank_mb} do mod). Devolve o retrato e a
 * contagem esperada de itens, calculada à parte durante a geração (a conferência final não depende do NBT).
 */
public final class ShipTestRocket {
    private ShipTestRocket() {
    }

    /** Forma do foguete de teste. */
    public record Spec(int sx, int sy, int sz, int chests, int tanks) {
    }

    /** O retrato, o combustível dos tanques e os itens esperados por id (baús e barril; a fornalha fica de fora). */
    public record Built(RocketPortrait portrait, int tankFuelMb, int chests, Map<String, Integer> expectedItems) {
        public int expectedTotalItems() {
            int total = 0;
            for (int n : this.expectedItems.values()) {
                total += n;
            }
            return total;
        }
    }

    /** As formas para ~500, ~4.000 e ~8.000 blocos; outro número procura um cubo aproximado. */
    public static Spec preset(int blocks) {
        if (blocks <= 700) {
            return new Spec(9, 12, 9, 12, 4);
        } else if (blocks <= 5000) {
            return new Spec(26, 28, 26, 40, 8);
        } else if (blocks <= 9000) {
            return new Spec(37, 38, 37, 60, 12);
        }
        int s = 5;
        while (s < 60 && shell(s, s + 2, s) + 80 < blocks) {
            s++;
        }
        return new Spec(s, s + 2, s, Math.min(100, blocks / 130), 12);
    }

    private static int shell(int sx, int sy, int sz) {
        return sx * sy * sz - (sx - 2) * (sy - 2) * (sz - 2);
    }

    public static Built build(HolderLookup.Provider registries, Spec spec) {
        int sx = spec.sx();
        int sy = spec.sy();
        int sz = spec.sz();
        PortraitGrid grid = PortraitGrid.empty(sx, sy, sz);
        List<BlockState> palette = new ArrayList<>();
        palette.add(Blocks.AIR.defaultBlockState());
        java.util.Map<BlockState, Integer> index = new java.util.HashMap<>();
        index.put(palette.get(0), 0);
        Long2ObjectMap<CompoundTag> blockEntities = new Long2ObjectLinkedOpenHashMap<>();
        Map<String, Integer> expected = new TreeMap<>();

        BlockState hull = ModBlocks.ROCKET_HULL.get().defaultBlockState();
        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState lantern = Blocks.SEA_LANTERN.defaultBlockState();
        BlockState engine = ModBlocks.ROCKET_ENGINE.get().defaultBlockState();
        int cx = sx / 2;
        int cz = sz / 2;

        // casco oco
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    boolean onShell = x == 0 || x == sx - 1 || y == 0 || y == sy - 1 || z == 0 || z == sz - 1;
                    if (!onShell) {
                        continue;
                    }
                    BlockState state = hull;
                    boolean side = (x == 0 || x == sx - 1) != (z == 0 || z == sz - 1);
                    if (y == 0) {
                        state = Math.abs(x - cx) <= 1 && Math.abs(z - cz) <= 1 && (x != cx || z != cz) ? engine : brick;
                    } else if (y == sy - 1 && x % 6 == 3 && z % 6 == 3) {
                        state = lantern;
                    } else if (side && (y == sy / 2 || y == sy / 2 + 1) && ((x == 0 || x == sx - 1) ? z >= 3 && z <= sz - 4 : x >= 3 && x <= sx - 4)) {
                        state = glass;     // faixa de janelas nas quatro paredes
                    }
                    put(grid, palette, index, x, y, z, state);
                }
            }
        }

        // cockpit no centro
        BlockPos cockpit = new BlockPos(cx, 1, cz);
        put(grid, palette, index, cockpit.getX(), cockpit.getY(), cockpit.getZ(), ModBlocks.ROCKET_COCKPIT.get().defaultBlockState());

        // tanques de combustível, em volta do cockpit (cargas diferentes)
        int fuelSum = 0;
        int[][] tankSpots = {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}, {-4, 0}, {4, 0}, {0, -4}, {0, 4}, {-4, -4}, {4, -4}, {-4, 4}, {4, 4}};
        BlockState tankState = ModBlocks.ROCKET_FUEL_TANK.get().defaultBlockState();
        for (int i = 0; i < spec.tanks() && i < tankSpots.length; i++) {
            BlockPos at = new BlockPos(cx + tankSpots[i][0], 1, cz + tankSpots[i][1]);
            if (at.getX() < 2 || at.getX() > sx - 3 || at.getZ() < 2 || at.getZ() > sz - 3) {
                continue;
            }
            put(grid, palette, index, at.getX(), at.getY(), at.getZ(), tankState);
            RocketFuelTankBlockEntity tank = new RocketFuelTankBlockEntity(at, tankState);
            int mb = 700 + 450 * i;
            tank.setFuel(mb);
            fuelSum += tank.fuelMb();
            blockEntities.put(at.asLong(), ShipBlockKeys.nbtOf(tank, registries));
        }

        // fornalha (sem combustível, para não mudar de estado sozinha) e barril
        BlockPos furnacePos = new BlockPos(cx, 1, 2);
        BlockState furnaceState = Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.SOUTH);
        put(grid, palette, index, furnacePos.getX(), furnacePos.getY(), furnacePos.getZ(), furnaceState);
        FurnaceBlockEntity furnace = new FurnaceBlockEntity(furnacePos, furnaceState);
        furnace.setItem(0, new ItemStack(Items.RAW_IRON, 64));
        furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 12));
        blockEntities.put(furnacePos.asLong(), ShipBlockKeys.nbtOf(furnace, registries));

        BlockPos barrelPos = new BlockPos(cx, 1, sz - 3);
        BlockState barrelState = Blocks.BARREL.defaultBlockState();
        put(grid, palette, index, barrelPos.getX(), barrelPos.getY(), barrelPos.getZ(), barrelState);
        BarrelBlockEntity barrel = new BarrelBlockEntity(barrelPos, barrelState);
        fill(barrel, 100, expected);
        blockEntities.put(barrelPos.asLong(), ShipBlockKeys.nbtOf(barrel, registries));

        // baús nas paredes (a cada duas células do anel interno, para nunca virarem baú duplo)
        List<int[]> ring = new ArrayList<>();
        for (int x = 1; x <= sx - 2; x++) {
            ring.add(new int[] {x, 1, Direction.SOUTH.get2DDataValue()});
        }
        for (int z = 2; z <= sz - 2; z++) {
            ring.add(new int[] {sx - 2, z, Direction.WEST.get2DDataValue()});
        }
        for (int x = sx - 3; x >= 1; x--) {
            ring.add(new int[] {x, sz - 2, Direction.NORTH.get2DDataValue()});
        }
        for (int z = sz - 3; z >= 2; z--) {
            ring.add(new int[] {1, z, Direction.EAST.get2DDataValue()});
        }
        int placed = 0;
        for (int i = 0; i < ring.size() && placed < spec.chests(); i += 2) {
            int[] r = ring.get(i);
            BlockPos at = new BlockPos(r[0], 1, r[1]);
            if (grid.get(at.getX(), at.getY(), at.getZ()) != 0) {
                continue;
            }
            BlockState chestState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.from2DDataValue(r[2]));
            put(grid, palette, index, at.getX(), at.getY(), at.getZ(), chestState);
            ChestBlockEntity chest = new ChestBlockEntity(at, chestState);
            fill(chest, placed, expected);
            blockEntities.put(at.asLong(), ShipBlockKeys.nbtOf(chest, registries));
            placed++;
        }
        RocketPortrait portrait = new RocketPortrait(UUID.randomUUID(), grid, palette, blockEntities, cockpit);
        return new Built(portrait, fuelSum, placed, expected);
    }

    /** Enche um inventário de 27 slots com itens variados (nome, durabilidade, o tanque do mod com componente) e soma o esperado. */
    private static void fill(Container container, int salt, Map<String, Integer> expected) {
        for (int slot = 0; slot < 27; slot++) {
            ItemStack stack = switch (slot % 9) {
                case 0 -> new ItemStack(Items.DIAMOND, 64);
                case 1 -> new ItemStack(Items.IRON_INGOT, 64);
                case 2 -> new ItemStack(Items.GOLD_INGOT, 32);
                case 3 -> new ItemStack(Items.COBBLESTONE, 64);
                case 4 -> {
                    ItemStack apples = new ItemStack(Items.GOLDEN_APPLE, 3);
                    apples.set(DataComponents.CUSTOM_NAME, Component.literal("Provisão " + salt + "/" + slot));
                    yield apples;
                }
                case 5 -> new ItemStack(Items.ENDER_PEARL, 16);
                case 6 -> RocketFuelTankItem.fullStack();
                case 7 -> {
                    ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
                    pick.set(DataComponents.DAMAGE, 3 + (salt + slot) % 200);
                    yield pick;
                }
                default -> new ItemStack(Items.OAK_LOG, 1 + (salt * 3 + slot) % 60);
            };
            container.setItem(slot, stack);
            expected.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
        }
        if (container instanceof BlockEntity be) {
            be.setChanged();
        }
    }

    private static void put(PortraitGrid grid, List<BlockState> palette, java.util.Map<BlockState, Integer> index, int x, int y, int z, BlockState state) {
        int idx = index.computeIfAbsent(state, s -> {
            palette.add(s);
            return palette.size() - 1;
        });
        grid.set(x, y, z, idx);
    }

    /**
     * Cria a entidade pousada com o retrato, o centro em {@code center}; o combustível da entidade é um pouco menor que a soma dos tanques (a
     * conversão o reparte de novo, como no pouso) e termina em .5 (o piso é o que vale).
     */
    public static RocketEntity spawn(ServerLevel level, Vec3 center, Built built) {
        RocketEntity rocket = ModEntities.ROCKET.get().create(level, EntitySpawnReason.TRIGGERED);
        if (rocket == null) {
            throw new IllegalStateException("não foi possível criar a entidade do foguete");
        }
        rocket.setPortrait(built.portrait());
        rocket.setFuelMb(Math.floor(built.tankFuelMb() * 0.8) + 0.5);
        rocket.setPos(center);
        level.addFreshEntity(rocket);
        return rocket;
    }

    /** O combustível esperado nos tanques depois de qualquer número de conversões: o piso do combustível da entidade. */
    public static int expectedFuelMb(Built built) {
        return (int) Math.floor(Math.floor(built.tankFuelMb() * 0.8) + 0.5);
    }
}
