package com.spacehorizons.worldgen.density;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * Função de densidade "spacehorizons:latitude_cooling": só depende de Z e devolve o deslocamento de
 * temperatura por latitude (LatitudeClimate) do planeta. O datapack soma isto à temperatura do Overworld.
 */
public record LatitudeCoolingFunction(Identifier planet) implements DensityFunction {
    public static final MapCodec<LatitudeCoolingFunction> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(Identifier.CODEC.fieldOf("planet").forGetter(LatitudeCoolingFunction::planet))
                    .apply(instance, LatitudeCoolingFunction::new));

    @Override
    public DensitySampler compileSampler(DensityFunction.CompileContext context) {
        PlanetDefinition definition = Planets.byId(this.planet)
                .orElseThrow(() -> new IllegalStateException("planeta desconhecido em latitude_cooling: " + this.planet));
        return new Sampler(definition.maxLatitudeBlocks());
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return this;
    }

    @Override
    public Interval range() {
        return Interval.of(-LatitudeClimate.POLE_COOLING, LatitudeClimate.EQUATOR_WARMTH);
    }

    @Override
    public @DensityFunction.Axes int domainAxes() {
        return DensityFunction.axesFrom(Direction.Axis.Z);
    }

    @Override
    public MapCodec<LatitudeCoolingFunction> codec() {
        return CODEC;
    }

    private record Sampler(double maxLatitudeBlocks) implements DensitySampler {
        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return LatitudeClimate.offset(blockZ, this.maxLatitudeBlocks);
        }

        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            for (int z = 0; z < volume.sizeZ(); z++) {
                float value = LatitudeClimate.offset(volume.blockZ(z), this.maxLatitudeBlocks);
                for (int x = 0; x < volume.sizeX(); x++) {
                    outputBuffer.setRange(volume.indexUnchecked(x, 0, z), volume.sizeY(), value);
                }
            }
        }
    }
}
