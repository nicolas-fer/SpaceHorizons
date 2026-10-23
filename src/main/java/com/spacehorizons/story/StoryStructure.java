package com.spacehorizons.story;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spacehorizons.story.StoryEntry.Site;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * A estrutura do vanilla ({@code spacehorizons:story_site}) que põe um lugar da trama na TERRA: o JSON da estrutura escolhe o {@code site}, o
 * {@code structure_set} sorteia onde (espalhamento aleatório) e a tag de biomas diz em que biomas (terra firme). O desenho vem de {@link SiteLayouts}. A
 * estrutura só vale onde o terreno é bastante plano (diferença de até {@link #MAX_ROUGHNESS} blocos sob a pegada) e acima do mar; a laje fica na média das
 * alturas. Perto da costura em X o gerador da Terra já suprime estruturas ({@code PlanetNoiseChunkGenerator.STRUCTURE_SEAM_MARGIN}).
 *
 * <p>Vale só com "gerar estruturas" ligado no mundo (o vanilla pula as estruturas se estiver desligado); a Lua não depende disso (desenho fixo, ver
 * {@link StorySites}).
 */
public class StoryStructure extends Structure {
    public static final MapCodec<StoryStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            settingsCodec(instance),
            Codec.STRING.fieldOf("site").forGetter(s -> s.site.id()))
            .apply(instance, (settings, site) -> new StoryStructure(settings, StoryPiece.siteOf(site))));

    /** Diferença máxima de altura sob a pegada para a estrutura nascer. */
    public static final int MAX_ROUGHNESS = 10;

    private final Site site;

    public StoryStructure(StructureSettings settings, Site site) {
        super(settings);
        this.site = site;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!context.couldValidBiomeExistOnTopOfChunkCenter()) {
            return Optional.empty();
        }
        SiteLayout layout = SiteLayouts.of(this.site);
        ChunkPos chunk = context.chunkPos();
        int centerX = chunk.getMiddleBlockX();
        int centerZ = chunk.getMiddleBlockZ();
        int minX = centerX - layout.width() / 2;
        int minZ = centerZ - layout.depth() / 2;
        ChunkGenerator generator = context.chunkGenerator();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        long sum = 0;
        int n = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                int x = minX + (layout.width() - 1) * i / 2;
                int z = minZ + (layout.depth() - 1) * j / 2;
                int top = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()) - 1;
                min = Math.min(min, top);
                max = Math.max(max, top);
                sum += top;
                n++;
            }
        }
        if (max - min > MAX_ROUGHNESS || min <= generator.getSeaLevel()) {
            return Optional.empty();
        }
        int floorY = (int) Math.round(sum / (double) n);
        return Optional.of(new GenerationStub(new BlockPos(centerX, floorY, centerZ), builder -> builder.addPiece(new StoryPiece(this.site, minX, floorY, minZ))));
    }

    @Override
    public StructureType<?> type() {
        return StoryWorldgen.STRUCTURE_TYPE.get();
    }
}
