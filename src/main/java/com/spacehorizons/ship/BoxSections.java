package com.spacehorizons.ship;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Percorre a caixa de uma nave por SEÇÕES de chunk (16x16x16), a unidade de trabalho dos jobs de leitura e de limpeza: uma seção só de
 * ar custa quase nada e não é varrida célula a célula. Nada aqui carrega o mundo além do {@code getChunk} do chunk da seção.
 */
final class BoxSections {
    /** Remoção sem atualizar vizinhos (nada solta ou quebra no meio da operação) e sem soltar itens (as mesmas flags da montagem do foguete). */
    static final int REMOVE_FLAGS = Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS
            | Block.UPDATE_IMMEDIATE | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
    /** Custo, em unidades do orçamento, de varrer uma seção com blocos (4096 células lidas a uma fração de um bloco colocado cada). */
    static final int SECTION_COST = 256;

    private BoxSections() {
    }

    /** Uma seção a varrer: coordenadas de chunk e a seção vertical (y >> 4). */
    record Section(int cx, int cz, int sy) {
    }

    /** Todas as seções que a caixa toca, por coluna de chunk e de baixo para cima. */
    static List<Section> of(ShipYard.Box box) {
        List<Section> out = new ArrayList<>();
        for (int cx = box.chunkMinX(); cx <= box.chunkMaxX(); cx++) {
            for (int cz = box.chunkMinZ(); cz <= box.chunkMaxZ(); cz++) {
                for (int sy = box.minY() >> 4; sy <= box.maxY() >> 4; sy++) {
                    out.add(new Section(cx, cz, sy));
                }
            }
        }
        return out;
    }

    /** A seção do mundo (carrega o chunk, de forma síncrona, se preciso). */
    static LevelChunkSection sectionOf(ServerLevel level, Section s) {
        LevelChunk chunk = level.getChunk(s.cx(), s.cz());
        return chunk.getSection(chunk.getSectionIndex(s.sy() << 4));
    }

    /** Limita o intervalo de células da seção à caixa: {minX, maxX, minY, maxY, minZ, maxZ} em coordenadas de mundo. */
    static int[] clip(ShipYard.Box box, Section s) {
        return new int[] {Math.max(box.minX(), s.cx() << 4), Math.min(box.maxX(), (s.cx() << 4) + 15), Math.max(box.minY(), s.sy() << 4),
                Math.min(box.maxY(), (s.sy() << 4) + 15), Math.max(box.minZ(), s.cz() << 4), Math.min(box.maxZ(), (s.cz() << 4) + 15)};
    }

    /** Tira o block entity e o bloco da posição, sem soltar nada nem atualizar vizinhos. */
    static void clearAt(ServerLevel level, BlockPos pos) {
        level.removeBlockEntity(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
    }
}
