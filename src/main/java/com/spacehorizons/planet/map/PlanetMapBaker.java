package com.spacehorizons.planet.map;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleConsumer;
import java.util.stream.IntStream;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.SurfaceType;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Calcula o {@link PlanetMap} a partir do gerador REAL do mundo: altura do fundo (OCEAN_FLOOR_WG) e da superfície
 * (WORLD_SURFACE_WG, que enxerga a água) por {@code ChunkGenerator.getBaseHeight}, e o bioma do clima do
 * {@code RandomState} pelo biome source. Roda em threads de background (um ForkJoinPool pequeno, de baixa prioridade);
 * nada aqui toca em estado do mundo do servidor: só o gerador, o RandomState e a altura do mundo, que são somente
 * leitura e já são usados em paralelo pelo próprio vanilla.
 */
public final class PlanetMapBaker {
    /** Deslocamento (blocos) das amostras extras de bioma que suavizam a cor média da célula. */
    private static final int COLOR_SPREAD_FRACTION = 4;

    private final PlanetDefinition planet;
    private final ChunkGenerator generator;
    private final RandomState randomState;
    private final LevelHeightAccessor heightAccessor;
    private final ThreadLocal<BiomeResolver> resolvers;

    /**
     * @param minY altura mínima do mundo (para o LevelHeightAccessor)
     * @param worldHeight altura total do mundo
     */
    public PlanetMapBaker(PlanetDefinition planet, ChunkGenerator generator, RandomState randomState, int minY, int worldHeight) {
        this.planet = planet;
        this.generator = generator;
        this.randomState = randomState;
        this.heightAccessor = new LevelHeightAccessor() {
            @Override
            public int getHeight() {
                return worldHeight;
            }

            @Override
            public int getMinY() {
                return minY;
            }
        };
        // o resolvedor do clima guarda estado: um por thread
        this.resolvers = ThreadLocal.withInitial(() -> generator.getBiomeSource().createUncachedResolver(randomState));
    }

    /**
     * Calcula o mapa inteiro. Bloqueia a thread chamadora até terminar (chame de uma thread de background).
     *
     * @param progress recebe a fração concluída (0..1) de tempos em tempos, de threads do pool
     * @param cancel se virar true o cálculo para cedo e devolve null
     */
    public PlanetMap bake(long seed, int width, String fingerprint, DoubleConsumer progress, AtomicBoolean cancel) {
        PlanetMap map = new PlanetMap(planet, seed, width, fingerprint);
        int rows = map.height();
        AtomicInteger done = new AtomicInteger();
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        ForkJoinPool pool = new ForkJoinPool(threads, pool0 -> {
            ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool0);
            thread.setName("SpaceHorizons-PlanetMapBake-" + thread.getPoolIndex());
            thread.setPriority(Thread.MIN_PRIORITY + 1);
            return thread;
        }, null, false);
        try {
            pool.submit(() -> IntStream.range(0, rows).parallel().forEach(row -> {
                if (cancel.get()) {
                    return;
                }
                bakeRow(map, row);
                progress.accept(done.incrementAndGet() / (double) rows);
            })).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("falha ao calcular o mapa do planeta", e.getCause());
        } finally {
            pool.shutdown();
        }
        return cancel.get() ? null : map;
    }

    private void bakeRow(PlanetMap map, int row) {
        BiomeResolver resolver = resolvers.get();
        int z = (int) Math.round(map.nodeZ(row));
        int spread = Math.max(4, (int) (map.cellSize() / COLOR_SPREAD_FRACTION));
        for (int column = 0; column < map.width(); column++) {
            int x = (int) Math.round(map.nodeX(column));
            int floor = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heightAccessor, randomState);
            int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heightAccessor, randomState);
            boolean water = surface > floor;
            int topSolid = floor - 1;

            Holder<Biome> biome = biomeAt(resolver, x, surface, z);
            if (water) {
                map.set(column, row, topSolid, BiomeColors.waterColor(biome), true, SurfaceType.OCEAN);
                continue;
            }
            SurfaceType type = BiomeColors.surfaceType(biome);
            int color = averageLandColor(resolver, biome, x, surface, z, spread);
            map.set(column, row, topSolid, color, false, type);
        }
    }

    /** Média da cor de terra do bioma central e de quatro vizinhos (água é ignorada: a célula é terra). */
    private static int averageLandColor(BiomeResolver resolver, Holder<Biome> center, int x, int y, int z, int spread) {
        int r = 0;
        int g = 0;
        int b = 0;
        int n = 0;
        int[][] offsets = {{0, 0}, {spread, 0}, {-spread, 0}, {0, spread}, {0, -spread}};
        for (int[] offset : offsets) {
            Holder<Biome> sample = offset[0] == 0 && offset[1] == 0 ? center : biomeAt(resolver, x + offset[0], y, z + offset[1]);
            if (BiomeColors.isWaterBiome(sample) && sample != center) {
                continue;
            }
            int rgb = BiomeColors.isWaterBiome(sample) ? BiomeColors.landColor(center) : BiomeColors.landColor(sample);
            r += rgb >> 16 & 0xFF;
            g += rgb >> 8 & 0xFF;
            b += rgb & 0xFF;
            n++;
        }
        return (int) Math.round(r / (double) n) << 16 | (int) Math.round(g / (double) n) << 8 | (int) Math.round(b / (double) n);
    }

    private static Holder<Biome> biomeAt(BiomeResolver resolver, int x, int y, int z) {
        return resolver.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
    }
}
