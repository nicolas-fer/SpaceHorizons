package com.spacehorizons.worldgen.density;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * Sampler da PeriodicXLeaf: avalia o sampler de entrada em X embrulhado e, na faixa da costura, mistura A e B
 * (ver SeamMath). Fica fora da record para ser testável sem inicializar os registros do Minecraft.
 */
public record PeriodicXSampler(DensitySampler input, int circumference, int halfBand) implements DensitySampler {
    @Override
    public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
        int offset = SeamMath.seamOffset(blockX, this.circumference);
        int shiftA = SeamMath.shiftA(blockX, this.circumference, this.halfBand);
        if (!SeamMath.inBand(offset, this.halfBand)) {
            return this.input.sampleValue(context, blockX + shiftA, blockY, blockZ);
        }

        int shiftB = SeamMath.shiftB(blockX, this.circumference, this.halfBand);
        float a = this.input.sampleValue(context, blockX + shiftA, blockY, blockZ);
        float b = this.input.sampleValue(context, blockX + shiftB, blockY, blockZ);
        return (float) SeamMath.mix(a, b, SeamMath.weightB(offset, this.halfBand));
    }

    @Override
    public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
        int sizeX = volume.sizeX();
        int first = 0;
        while (first < sizeX) {
            // trecho contínuo de colunas X com o mesmo regime (fora da faixa, ou dentro com os mesmos deslocamentos)
            int x0 = volume.blockX(first);
            boolean band = SeamMath.inBand(SeamMath.seamOffset(x0, this.circumference), this.halfBand);
            int shiftA = SeamMath.shiftA(x0, this.circumference, this.halfBand);
            int shiftB = band ? SeamMath.shiftB(x0, this.circumference, this.halfBand) : shiftA;
            int end = first + 1;
            while (end < sizeX) {
                int x = volume.blockX(end);
                boolean sameBand = SeamMath.inBand(SeamMath.seamOffset(x, this.circumference), this.halfBand) == band;
                if (!sameBand
                        || SeamMath.shiftA(x, this.circumference, this.halfBand) != shiftA
                        || band && SeamMath.shiftB(x, this.circumference, this.halfBand) != shiftB) {
                    break;
                }
                end++;
            }
            sampleRun(context, outputBuffer, volume, first, end, band, shiftA, shiftB);
            first = end;
        }
    }

    private void sampleRun(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume, int first, int end,
            boolean band, int shiftA, int shiftB) {
        boolean whole = first == 0 && end == volume.sizeX();
        if (!band && whole) {
            // caso comum: o volume inteiro fora da faixa, avaliado direto no volume deslocado
            this.input.sampleVolume(context, outputBuffer, shiftA == 0 ? volume : shifted(volume, 0, volume.sizeX(), shiftA));
            return;
        }

        int count = end - first;
        DensityVolume runA = shifted(volume, first, count, shiftA);
        try (ScopedDensityBuffer bufferA = context.acquireBuffer(runA)) {
            this.input.sampleVolume(context, bufferA, runA);
            if (!band) {
                copyRun(outputBuffer, volume, runA, bufferA, first, count);
                return;
            }

            DensityVolume runB = shifted(volume, first, count, shiftB);
            try (ScopedDensityBuffer bufferB = context.acquireBuffer(runB)) {
                this.input.sampleVolume(context, bufferB, runB);
                blendRun(outputBuffer, volume, runA, bufferA, bufferB, first, count);
            }
        }
    }

    private static DensityVolume shifted(DensityVolume volume, int first, int count, int shiftX) {
        return new DensityVolume(count, volume.sizeY(), volume.sizeZ(), volume.blockX(first) + shiftX, volume.minBlockY(),
                volume.minBlockZ(), volume.stepBlockX(), volume.stepBlockY(), volume.stepBlockZ());
    }

    private static void copyRun(DensityBuffer out, DensityVolume volume, DensityVolume run, DensityBuffer runBuffer, int first,
            int count) {
        for (int z = 0; z < volume.sizeZ(); z++) {
            for (int ix = 0; ix < count; ix++) {
                for (int y = 0; y < volume.sizeY(); y++) {
                    out.set(volume.indexUnchecked(first + ix, y, z), runBuffer.get(run.indexUnchecked(ix, y, z)));
                }
            }
        }
    }

    private void blendRun(DensityBuffer out, DensityVolume volume, DensityVolume run, DensityBuffer bufferA,
            DensityBuffer bufferB, int first, int count) {
        double[] cos = new double[count];
        double[] sin = new double[count];
        for (int ix = 0; ix < count; ix++) {
            int offset = SeamMath.seamOffset(volume.blockX(first + ix), this.circumference);
            double angle = SeamMath.weightB(offset, this.halfBand) * (Math.PI / 2.0);
            cos[ix] = Math.cos(angle);
            sin[ix] = Math.sin(angle);
        }
        for (int z = 0; z < volume.sizeZ(); z++) {
            for (int ix = 0; ix < count; ix++) {
                for (int y = 0; y < volume.sizeY(); y++) {
                    int runIndex = run.indexUnchecked(ix, y, z);
                    float value = (float) (cos[ix] * bufferA.get(runIndex) + sin[ix] * bufferB.get(runIndex));
                    out.set(volume.indexUnchecked(first + ix, y, z), value);
                }
            }
        }
    }
}
