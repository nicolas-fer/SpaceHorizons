package com.spacehorizons.worldgen;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.worldgen.density.SeamMath;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Gerador de um planeta com o mundo do Overworld completo: é o NoiseBasedChunkGenerator do vanilla (relevo,
 * biomas, cavernas, aquíferos, minérios, árvores, estruturas), com as noise settings do próprio planeta.
 * A periodicidade em X e o clima por latitude vêm das noise settings (funções de densidade
 * spacehorizons:periodic_x e spacehorizons:latitude_cooling); aqui só ficam o vínculo com o planeta e
 * a supressão de estruturas ao redor da costura.
 *
 * Estruturas: o ponto de início delas é sorteado por região de chunks, sem noção de wrap, então o lado de lá
 * da costura (chunks espelhados, x além de C/2) sortearia estruturas diferentes das do lado real, e uma caixa
 * que cruzasse a costura sairia cortada. Nenhuma estrutura começa a menos de STRUCTURE_SEAM_MARGIN blocos dela.
 */
public final class PlanetNoiseChunkGenerator extends NoiseBasedChunkGenerator {
    /** Distância mínima da costura (em blocos, dos dois lados) para uma estrutura poder começar. */
    public static final int STRUCTURE_SEAM_MARGIN = 640;

    public static final MapCodec<PlanetNoiseChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("planet").forGetter(g -> g.planet.id()),
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(NoiseBasedChunkGenerator::generatorSettings))
            .apply(instance, (id, source, settings) -> new PlanetNoiseChunkGenerator(PlanetChunkGenerator.planetOf(id), source, settings)));

    private final PlanetDefinition planet;

    public PlanetNoiseChunkGenerator(PlanetDefinition planet, BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);
        this.planet = planet;
    }

    public PlanetDefinition planet() {
        return this.planet;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState state, StructureManager structureManager,
            ChunkAccess centerChunk, StructureTemplateManager structureTemplateManager, ResourceKey<Level> level) {
        int chunkCenterX = centerChunk.getPos().getMiddleBlockX();
        if (SeamMath.nearSeam(chunkCenterX, this.planet.circumferenceBlocks(), STRUCTURE_SEAM_MARGIN)) {
            return;
        }
        super.createStructures(registryAccess, state, structureManager, centerChunk, structureTemplateManager, level);
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos, SamplerContext samplerContext) {
        super.addDebugScreenInfo(result, randomState, feetPos, samplerContext);
        int distance = SeamMath.distanceToSeam(feetPos.getX(), this.planet.circumferenceBlocks());
        result.add("SpaceHorizons planeta: " + this.planet.id() + " (costura em X a " + distance + " blocos)");
    }
}
