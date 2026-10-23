package com.spacehorizons.story;

import com.spacehorizons.story.StoryEntry.Site;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * A peça única de um lugar da trama na TERRA: guarda o lugar e o nível da laje e desenha o {@link SiteLayout} chunk a chunk
 * ({@link StoryStamper}) quando o vanilla manda ({@code postProcess}). A caixa da peça é a pegada do desenho (de x e z de {@code minX}/{@code minZ} em
 * diante, da laje até a altura do desenho).
 */
public class StoryPiece extends StructurePiece {
    private final Site site;
    private final int floorY;

    public StoryPiece(Site site, int minX, int floorY, int minZ) {
        super(StoryWorldgen.PIECE.get(), 0, boxOf(site, minX, floorY, minZ));
        this.site = site;
        this.floorY = floorY;
    }

    public StoryPiece(CompoundTag tag) {
        super(StoryWorldgen.PIECE.get(), tag);
        this.site = siteOf(tag.getStringOr("Site", Site.EARTH_STATION.id()));
        this.floorY = tag.getIntOr("FloorY", this.boundingBox.minY());
    }

    static BoundingBox boxOf(Site site, int minX, int floorY, int minZ) {
        SiteLayout layout = SiteLayouts.of(site);
        return new BoundingBox(minX, floorY - 1, minZ, minX + layout.width() - 1, floorY + layout.height(), minZ + layout.depth() - 1);
    }

    static Site siteOf(String id) {
        for (Site s : Site.values()) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        return Site.EARTH_STATION;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("Site", this.site.id());
        tag.putInt("FloorY", this.floorY);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB,
            ChunkPos chunkPos, BlockPos referencePos) {
        StoryStamper.stamp(level, SiteLayouts.of(this.site), this.boundingBox.minX(), this.floorY, this.boundingBox.minZ(), chunkBB, random);
    }
}
