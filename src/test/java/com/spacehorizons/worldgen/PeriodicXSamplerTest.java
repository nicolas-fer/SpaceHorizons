package com.spacehorizons.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.spacehorizons.worldgen.density.PeriodicXSampler;
import com.spacehorizons.worldgen.density.SeamMath;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/** O sampler da folha periódica: o caminho em volume tem de dar o mesmo que o ponto a ponto e que SeamMath. */
class PeriodicXSamplerTest {
    private static final int C = 65_536;
    private static final int W = SeamMath.DEFAULT_HALF_BAND;

    /** Sampler falso: f(x, y, z) suave e NÃO periódica em X. */
    private static final DensitySampler INNER = new DensitySampler() {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer out, DensityVolume volume) {
            DensitySampler.sampleVolumeNaive(context, out, volume, this);
        }

        @Override
        public float sampleValue(SamplerContext context, int x, int y, int z) {
            return f(x, y, z);
        }
    };

    private static float f(int x, int y, int z) {
        return (float) (Math.sin(x * 0.0021) + 0.5 * Math.sin(z * 0.013 + y * 0.07) + x * 1e-5);
    }

    private static DensitySampler sampler() {
        return new PeriodicXSampler(INNER, C, W);
    }

    @Test
    void volumeIgualAoPontoAPonto() {
        DensitySampler sampler = sampler();
        SamplerContext ctx = SamplerContext.EMPTY_UNCACHED;
        // volumes de chunk e de célula (passo 4/8) que cruzam a costura em x = C/2, dentro e fora da faixa
        int[] minXs = { 0, C / 2 - 16, C / 2 - 8, C / 2, C / 2 + 16, -C / 2 - 16, -C / 2, -C / 2 + 4, C / 2 - W - 8, C / 2 + 4 * W, 3 * C };
        for (int minX : minXs) {
            for (int[] step : new int[][] { { 1, 1, 1 }, { 4, 8, 4 } }) {
                DensityVolume volume = new DensityVolume(5, 4, 3, minX, -8, 32, step[0], step[1], step[2]);
                DensityBuffer out = DensityBuffer.createUnpooled(volume.size());
                sampler.sampleVolume(ctx, out, volume);
                for (int z = 0; z < volume.sizeZ(); z++) {
                    for (int x = 0; x < volume.sizeX(); x++) {
                        for (int y = 0; y < volume.sizeY(); y++) {
                            float expected = sampler.sampleValue(ctx, volume.blockX(x), volume.blockY(y), volume.blockZ(z));
                            assertEquals(expected, out.get(volume.indexUnchecked(x, y, z)), 1e-5F,
                                    "minX=" + minX + " passo=" + step[0] + " x=" + x + " y=" + y + " z=" + z);
                        }
                    }
                }
            }
        }
    }

    @Test
    void pontoSegueAReferenciaPuraEPeriodicaEmX() {
        DensitySampler sampler = sampler();
        SamplerContext ctx = SamplerContext.EMPTY_UNCACHED;
        for (int x = -C; x <= C; x += 509) {
            float value = sampler.sampleValue(ctx, x, 10, 20);
            double reference = SeamMath.evaluate(ix -> f(ix, 10, 20), x, C, W);
            assertEquals(reference, value, 1e-5, "x=" + x);
            assertEquals(value, sampler.sampleValue(ctx, x + C, 10, 20), 0.0F, "periodicidade x=" + x);
            assertEquals(value, sampler.sampleValue(ctx, x - C, 10, 20), 0.0F, "periodicidade x=" + x);
        }
    }

    @Test
    void volumeTambemEPeriodicoEmX() {
        DensitySampler sampler = sampler();
        SamplerContext ctx = SamplerContext.EMPTY_UNCACHED;
        DensityVolume near = new DensityVolume(16, 2, 16, C / 2 - 8, 0, 0);
        DensityVolume far = new DensityVolume(16, 2, 16, -C / 2 - 8, 0, 0);
        DensityBuffer a = DensityBuffer.createUnpooled(near.size());
        DensityBuffer b = DensityBuffer.createUnpooled(far.size());
        sampler.sampleVolume(ctx, a, near);
        sampler.sampleVolume(ctx, b, far);
        for (int i = 0; i < near.size(); i++) {
            assertEquals(a.get(i), b.get(i), 0.0F, "índice " + i);
        }
    }
}
