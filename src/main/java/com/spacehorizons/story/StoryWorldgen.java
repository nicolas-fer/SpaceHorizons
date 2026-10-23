package com.spacehorizons.story;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;

import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A colocação dos lugares da trama no mundo.
 *
 * <ul>
 *   <li><b>Terra</b> (vanilla com costura): estrutura do vanilla ({@link StoryStructure} + {@link StoryPiece}), sorteada por
 *   {@code data/spacehorizons/worldgen/structure_set/earth_station.json} em terra firme.</li>
 *   <li><b>Lua</b> (gerador paramétrico próprio, sem estruturas do vanilla): posições FIXAS ({@link StorySites}) desenhadas pelo próprio gerador depois da
 *   decoração de cada chunk ({@link #stampMoon}); não dependem do "gerar estruturas" do mundo.</li>
 * </ul>
 */
public final class StoryWorldgen {
    private static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, SpaceHorizons.MODID);
    private static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, SpaceHorizons.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<StoryStructure>> STRUCTURE_TYPE = STRUCTURE_TYPES.register("story_site",
            () -> () -> StoryStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> PIECE = PIECES.register("story_site",
            () -> (StructurePieceType) (context, tag) -> new StoryPiece(tag));

    private StoryWorldgen() {
    }

    static void init(IEventBus modEventBus) {
        STRUCTURE_TYPES.register(modEventBus);
        PIECES.register(modEventBus);
    }

    /**
     * Desenha os lugares da Lua que tocam o chunk (só dentro dele). Chamado pelo gerador da Lua depois da decoração; em qualquer outro planeta não faz
     * nada.
     */
    public static void stampMoon(PlanetDefinition planet, WorldGenLevel level, ChunkAccess chunk) {
        if (!planet.equals(Planets.MOON)) {
            return;
        }
        ChunkPos pos = chunk.getPos();
        BoundingBox chunkBB = new BoundingBox(pos.getMinBlockX(), level.getMinY(), pos.getMinBlockZ(), pos.getMaxBlockX(), level.getMaxY(), pos.getMaxBlockZ());
        for (StorySites.Placement placement : StorySites.moonPlacementsTouching(pos.x(), pos.z())) {
            RandomSource random = RandomSource.create(pos.pack() ^ placement.site().ordinal());
            StoryStamper.stamp(level, SiteLayouts.of(placement.site()), placement.minX(), placement.floorY(), placement.minZ(), chunkBB, random);
        }
    }
}
