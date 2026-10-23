package com.spacehorizons.story;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.spacehorizons.SpaceHorizons;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Desenha um {@link SiteLayout} no mundo EM GERAÇÃO, só dentro do {@code chunkBB} dado: serve às duas colocações (a estrutura do vanilla da Terra, chunk a
 * chunk pelo {@code postProcess} da peça, e o desenho fixo da Lua, chunk a chunk pelo gerador). Como cada operação depende só da posição, desenhar em
 * qualquer ordem de chunks dá o mesmo lugar.
 */
public final class StoryStamper {
    private static final Map<String, BlockState> STATES = new ConcurrentHashMap<>();

    private StoryStamper() {
    }

    /** O estado de uma string do desenho; um bloco que não existe no jogo vira ar (e é avisado no log uma vez). */
    public static BlockState state(String text) {
        return STATES.computeIfAbsent(text, s -> {
            try {
                return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, s, false).blockState();
            } catch (CommandSyntaxException | RuntimeException e) {
                SpaceHorizons.LOGGER.error("Trama: estado de bloco inválido no desenho de um lugar: {}", s, e);
                return Blocks.AIR.defaultBlockState();
            }
        });
    }

    /**
     * Desenha {@code layout} com a laje no nível {@code floorY} e o canto de menor x e z em {@code (minX, minZ)}, só nas colunas do {@code chunkBB}.
     *
     * @param random só para as tabelas de saque dos baús (as dos diários são fixas, então a semente não muda o conteúdo)
     */
    public static void stamp(WorldGenLevel level, SiteLayout layout, int minX, int floorY, int minZ, BoundingBox chunkBB, RandomSource random) {
        int x0 = Math.max(minX, chunkBB.minX());
        int x1 = Math.min(minX + layout.width() - 1, chunkBB.maxX());
        int z0 = Math.max(minZ, chunkBB.minZ());
        int z1 = Math.min(minZ + layout.depth() - 1, chunkBB.maxZ());
        if (x0 > x1 || z0 > z1) {
            return;
        }
        BlockState foundation = state(layout.foundation());
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                // fundação sob a laje
                for (int d = 1; d <= SiteLayout.FOUNDATION_DEPTH; d++) {
                    pos.set(x, floorY - d, z);
                    if (pos.getY() <= level.getMinY()) {
                        break;
                    }
                    BlockState current = level.getBlockState(pos);
                    if (!(current.isAir() || current.liquid() || current.canBeReplaced())) {
                        break;
                    }
                    level.setBlock(pos, foundation, 2);
                }
                // limpa o que está acima (árvores, rochas) antes do desenho
                for (int y = 0; y <= layout.clearHeight(); y++) {
                    pos.set(x, floorY + y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, air, 2);
                    }
                }
            }
        }
        for (SiteLayout.Op op : layout.ops()) {
            switch (op) {
                case SiteLayout.Box b -> fill(level, minX, floorY, minZ, x0, x1, z0, z1, op, b.x0(), b.y0(), b.z0(), b.x1(), b.y1(), b.z1(), null, random);
                case SiteLayout.Mix m -> fill(level, minX, floorY, minZ, x0, x1, z0, z1, op, m.x0(), m.y0(), m.z0(), m.x1(), m.y1(), m.z1(), null, random);
                case SiteLayout.Chest c -> fill(level, minX, floorY, minZ, x0, x1, z0, z1, op, c.x(), c.y(), c.z(), c.x(), c.y(), c.z(), c.loot(), random);
                case SiteLayout.Terminal t -> fill(level, minX, floorY, minZ, x0, x1, z0, z1, op, t.x(), t.y(), t.z(), t.x(), t.y(), t.z(), null, random);
            }
        }
    }

    private static void fill(WorldGenLevel level, int minX, int floorY, int minZ, int cx0, int cx1, int cz0, int cz1, SiteLayout.Op op, int lx0, int ly0,
            int lz0, int lx1, int ly1, int lz1, String loot, RandomSource random) {
        int xa = Math.max(Math.min(lx0, lx1), cx0 - minX);
        int xb = Math.min(Math.max(lx0, lx1), cx1 - minX);
        int za = Math.max(Math.min(lz0, lz1), cz0 - minZ);
        int zb = Math.min(Math.max(lz0, lz1), cz1 - minZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lx = xa; lx <= xb; lx++) {
            for (int lz = za; lz <= zb; lz++) {
                for (int ly = Math.min(ly0, ly1); ly <= Math.max(ly0, ly1); ly++) {
                    String text = SiteLayout.stateAt(op, lx, ly, lz);
                    if (text == null) {
                        continue;
                    }
                    BlockState state = state(text);
                    pos.set(minX + lx, floorY + ly, minZ + lz);
                    level.setBlock(pos, state, 2);
                    if (state.getBlock() instanceof IronBarsBlock) {
                        level.getChunk(pos).markPosForPostProcessing(pos);
                    }
                    if (loot != null) {
                        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, loot));
                        RandomizableContainer.setBlockEntityLootTable(level, random, pos, key);
                    }
                }
            }
        }
    }
}
