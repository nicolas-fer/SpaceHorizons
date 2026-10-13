package com.spacehorizons.dh;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGeneratorReturnType;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBiomeWrapper;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.objects.data.DhApiTerrainDataPoint;
import com.seibel.distanthorizons.api.objects.data.IDhApiFullDataSource;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrain;
import com.spacehorizons.planet.PlanetTerrains;
import com.spacehorizons.planet.SurfacePalette;
import com.spacehorizons.planet.SurfaceType;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Gera os LODs do DH direto da função de terreno do planeta, sem gerar chunks vanilla.
 *
 * Cada coluna do DH (2^detail blocos de lado) vira uma lista de segmentos verticais: pedra, subsolo,
 * topo e, se a superfície está abaixo do mar, água até o nível do mar. Os blocos e biomas vêm de
 * SurfacePalette, o mesmo lugar que os chunks usam, para a cor de longe bater com a de perto.
 *
 * Premissas do DH (verificadas no bytecode da 3.3.4-26.3, mas confirmar em runtime):
 *  - a origem do dado é chunkX*16, chunkZ*16 em blocos (o DH passa a posição do chunk, não do setor);
 *  - cada segmento é [bottomYBlockPos, topYBlockPos), com Y SEM SINAL relativo ao minY do level
 *    (javadoc de DhApiTerrainDataPoint); Y absoluto negativo vira altura gigante;
 *  - a coluna precisa incluir o ar acima da superfície com luz do céu, senão LODs distantes ficam pretos.
 */
final class PlanetLodGenerator implements IDhApiWorldGenerator {
    private static final int FILLER_DEPTH = 3;
    private static final int FULL_SKY_LIGHT = 15;
    private static final int NO_SKY_LIGHT = 0;

    private final IDhApiLevelWrapper levelWrapper;
    private final Level level;
    private final PlanetDefinition planet;
    private final PlanetTerrain terrain;

    // caches por instância (um gerador por level): wrappers do DH são caros de criar
    private final Map<BlockState, IDhApiBlockStateWrapper> blockWrappers = new ConcurrentHashMap<>();
    private final Map<SurfaceType, IDhApiBiomeWrapper> biomeWrappers = new ConcurrentHashMap<>();

    PlanetLodGenerator(IDhApiLevelWrapper levelWrapper, Level level, PlanetDefinition planet) {
        this.levelWrapper = levelWrapper;
        this.level = level;
        this.planet = planet;
        this.terrain = PlanetTerrains.get(planet);
    }

    @Override
    public EDhApiWorldGeneratorReturnType getReturnType() {
        // dados prontos por coluna: DH não precisa de chunks vanilla nem de API chunks
        return EDhApiWorldGeneratorReturnType.API_DATA_SOURCES;
    }

    @Override
    public CompletableFuture<Void> generateLod(int chunkX, int chunkZ, int sectionX, int sectionZ, byte detailLevel,
            IDhApiFullDataSource dataSource, EDhApiDistantGeneratorMode mode, ExecutorService executor,
            Consumer<IDhApiFullDataSource> resultConsumer) {
        // a origem em blocos sai de chunkX/chunkZ; sectionX/sectionZ ficam de fora porque o detail já a define
        return CompletableFuture
                .runAsync(() -> fill(chunkX * 16, chunkZ * 16, detailLevel, dataSource), executor)
                .thenRun(() -> resultConsumer.accept(dataSource));
    }

    private void fill(int originX, int originZ, byte detailLevel, IDhApiFullDataSource dataSource) {
        int width = dataSource.getWidthInDataColumns();
        int columnBlocks = 1 << detailLevel;
        int half = columnBlocks / 2;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < width; j++) {
                // amostra no centro da coluna; PlanetTerrain já é periódico em X
                int blockX = originX + i * columnBlocks + half;
                int blockZ = originZ + j * columnBlocks + half;
                dataSource.setApiDataPointColumn(i, j, EDhApiWorldGenerationStep.SURFACE, columnPoints(blockX, blockZ, detailLevel));
            }
        }
    }

    private List<DhApiTerrainDataPoint> columnPoints(int blockX, int blockZ, byte detailLevel) {
        int surfaceY = terrain.surfaceHeight(blockX, blockZ);
        SurfaceType type = terrain.surfaceType(blockX, blockZ);
        int minY = level.getMinY();
        int maxY = minY + level.getHeight();
        int seaTop = planet.seaLevel() + 1;
        int solidTop = surfaceY + 1;
        boolean water = planet.hasOcean() && surfaceY < planet.seaLevel();
        int columnTop = water ? Math.max(solidTop, seaTop) : solidTop;

        IDhApiBiomeWrapper biome = biomeWrapper(type);
        List<DhApiTerrainDataPoint> points = new ArrayList<>(5);
        addSegment(points, detailLevel, minY, minY, surfaceY - FILLER_DEPTH, NO_SKY_LIGHT,
                Blocks.STONE.defaultBlockState(), biome);
        addSegment(points, detailLevel, minY, surfaceY - FILLER_DEPTH, surfaceY, NO_SKY_LIGHT,
                SurfacePalette.filler(type), biome);
        addSegment(points, detailLevel, minY, surfaceY, solidTop, NO_SKY_LIGHT, SurfacePalette.top(type), biome);
        if (water) {
            addSegment(points, detailLevel, minY, solidTop, seaTop, FULL_SKY_LIGHT,
                    Blocks.WATER.defaultBlockState(), biome);
        }
        addSegment(points, detailLevel, minY, columnTop, maxY, FULL_SKY_LIGHT, Blocks.AIR.defaultBlockState(), biome);
        return points;
    }

    /** Recebe Y absoluto e converte para o Y relativo ao minY que o DH exige, cortando nos limites do mundo. */
    private void addSegment(List<DhApiTerrainDataPoint> points, byte detailLevel, int minY, int bottomY, int topY,
            int skyLight, BlockState state, IDhApiBiomeWrapper biome) {
        int bottom = Math.max(bottomY, minY) - minY;
        int top = Math.min(topY, minY + level.getHeight()) - minY;
        if (top <= bottom) {
            return;
        }
        points.add(DhApiTerrainDataPoint.create(detailLevel, 0, skyLight, bottom, top, blockWrapper(state), biome));
    }

    private IDhApiBlockStateWrapper blockWrapper(BlockState state) {
        return blockWrappers.computeIfAbsent(state,
                s -> DhApi.Delayed.wrapperFactory.getBlockStateWrapper(new Object[] {s}, levelWrapper));
    }

    private IDhApiBiomeWrapper biomeWrapper(SurfaceType type) {
        return biomeWrappers.computeIfAbsent(type, t -> {
            ResourceKey<Biome> key = SurfacePalette.biome(t);
            Holder<Biome> holder = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(key);
            return DhApi.Delayed.wrapperFactory.getBiomeWrapper(new Object[] {holder}, levelWrapper);
        });
    }

    @Override
    public void preGeneratorTaskStart() {
        // nada a preparar: os dados vêm direto da função de terreno
    }

    @Override
    public void close() {
        blockWrappers.clear();
        biomeWrappers.clear();
    }
}
