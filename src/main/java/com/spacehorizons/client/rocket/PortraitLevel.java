package com.spacehorizons.client.rocket;

import com.spacehorizons.rocket.RocketPortrait;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/**
 * "Mundo" falso só para a tesselação: dá a ModelBlockRenderer os vizinhos (culling de faces e oclusão ambiente)
 * e a luz. Fora da caixa do retrato é ar com céu cheio (15) e sem luz de bloco, então as faces externas
 * ficam acesas e o interior fechado escurece sozinho.
 */
final class PortraitLevel implements BlockAndTintGetter {
    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};

    private final RocketPortrait portrait;
    private final byte[] sky;
    private final byte[] block;

    PortraitLevel(RocketPortrait portrait) {
        this.portrait = portrait;
        int n = portrait.sizeX * portrait.sizeY * portrait.sizeZ;
        this.sky = new byte[n];
        this.block = new byte[n];
        this.propagate();
    }

    private int index(int x, int y, int z) {
        return x + this.portrait.sizeX * (z + this.portrait.sizeZ * y);
    }

    /** Inundação simples (BFS) das duas camadas: o que a engine de luz do vanilla faria, em ~1.000 células. */
    private void propagate() {
        RocketPortrait p = this.portrait;
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
        // céu: entra por toda célula de borda que não é opaca; cai sem perder força e perde 1 por passo de lado
        for (int y = 0; y < p.sizeY; y++) {
            for (int z = 0; z < p.sizeZ; z++) {
                for (int x = 0; x < p.sizeX; x++) {
                    boolean border = x == 0 || z == 0 || y == 0 || x == p.sizeX - 1 || z == p.sizeZ - 1 || y == p.sizeY - 1;
                    if (border && p.get(x, y, z).getLightDampening() < 15) {
                        this.sky[this.index(x, y, z)] = 15;
                        queue.enqueue(this.index(x, y, z));
                    }
                }
            }
        }
        this.flood(this.sky, queue, true);
        // luz de bloco: cada emissor semeia a sua intensidade
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < p.sizeY; y++) {
            for (int z = 0; z < p.sizeZ; z++) {
                for (int x = 0; x < p.sizeX; x++) {
                    int emission = p.get(x, y, z).getLightEmission(this, pos.set(x, y, z));
                    if (emission > 0) {
                        this.block[this.index(x, y, z)] = (byte) emission;
                        queue.enqueue(this.index(x, y, z));
                    }
                }
            }
        }
        this.flood(this.block, queue, false);
    }

    private void flood(byte[] layer, IntArrayFIFOQueue queue, boolean skyLayer) {
        RocketPortrait p = this.portrait;
        while (!queue.isEmpty()) {
            int cell = queue.dequeueInt();
            int x = cell % p.sizeX;
            int z = cell / p.sizeX % p.sizeZ;
            int y = cell / (p.sizeX * p.sizeZ);
            int level = layer[cell];
            for (int[] d : DIRS) {
                int nx = x + d[0];
                int ny = y + d[1];
                int nz = z + d[2];
                if (!p.inside(nx, ny, nz)) {
                    continue;
                }
                int dampening = p.get(nx, ny, nz).getLightDampening();
                boolean freeFall = skyLayer && level == 15 && d[1] == -1 && dampening == 0;
                int next = freeFall ? 15 : level - Math.max(1, dampening);
                int ni = this.index(nx, ny, nz);
                if (next > layer[ni]) {
                    layer[ni] = (byte) next;
                    queue.enqueue(ni);
                }
            }
        }
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return this.portrait.get(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.portrait.get(pos).getFluidState();
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    /** Sobrescreve o default (LevelLightEngine): é por aqui que LightCoordsUtil.BrightnessGetter.DEFAULT lê a luz. */
    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        if (!this.portrait.inside(pos.getX(), pos.getY(), pos.getZ())) {
            return layer == LightLayer.SKY ? 15 : 0;
        }
        int i = this.index(pos.getX(), pos.getY(), pos.getZ());
        return layer == LightLayer.SKY ? this.sky[i] : this.block[i];
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return LevelLightEngine.EMPTY; // não é consultado: getBrightness acima responde por tudo
    }

    @Override
    public CardinalLighting cardinalLighting() {
        return CardinalLighting.DEFAULT;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver color) {
        return -1; // sem bioma: grama e folhas ficam sem tom
    }

    @Override
    public int getHeight() {
        return this.portrait.sizeY;
    }

    @Override
    public int getMinY() {
        return 0;
    }
}
