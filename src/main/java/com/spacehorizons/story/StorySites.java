package com.spacehorizons.story;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.spacehorizons.planet.PlanetTerrain;
import com.spacehorizons.planet.PlanetTerrains;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.story.StoryEntry.Site;

/**
 * Onde ficam os lugares da trama na LUA: posições FIXAS (iguais em todo mundo), porque os terminais entregam coordenadas e bússolas para elas. Cada
 * lugar parte de um ponto nominal perto do ponto da Lua voltado para a Terra (onde {@code /spacehorizons goto moon} e a missão pousam, em
 * {@code x = -81919,5}, {@code z = 0,5}, sobre a costura em X) e escolhe, entre os chunks vizinhos dele, o de relevo mais PLANO sob a pegada do desenho
 * (a conta usa só a função analítica de relevo da Lua, a mesma dos chunks, então é pura e dá o mesmo resultado em todo lugar). O nível da laje é a
 * média das alturas sob a pegada. A base fica ao leste do ponto, a ~560 blocos dele (fora do alcance dos chunks espelhados da costura); a sonda, ~590
 * blocos adiante da base.
 *
 * <p>Na Terra a estação NÃO tem posição fixa: sai do sorteio de estruturas do vanilla ({@code data/spacehorizons/worldgen/structure_set/earth_station.json}).
 */
public final class StorySites {
    /** Ponto nominal da base lunar: ao leste do ponto da Lua voltado para a Terra. */
    public static final int MOON_BASE_NOMINAL_X = -81_471;
    public static final int MOON_BASE_NOMINAL_Z = 320;
    /** Ponto nominal dos destroços da sonda: a leste e ao sul da base. */
    public static final int MOON_PROBE_NOMINAL_X = -81_103;
    public static final int MOON_PROBE_NOMINAL_Z = 784;

    /** Quantos chunks, para cada lado do nominal, a busca do trecho mais plano percorre. */
    private static final int SEARCH_CHUNKS = 6;

    /** A posição de um lugar: canto de menor x e z da pegada e o nível da laje (y = 0 do desenho). */
    public record Placement(Site site, int minX, int floorY, int minZ, int width, int depth, int roughness) {
        public int maxX() {
            return this.minX + this.width - 1;
        }

        public int maxZ() {
            return this.minZ + this.depth - 1;
        }

        public int centerX() {
            return this.minX + this.width / 2;
        }

        public int centerZ() {
            return this.minZ + this.depth / 2;
        }

        /** O chunk que contém o centro (o chunk "inicial" do lugar). */
        public int centerChunkX() {
            return Math.floorDiv(this.centerX(), 16);
        }

        public int centerChunkZ() {
            return Math.floorDiv(this.centerZ(), 16);
        }

        /** A pegada toca o chunk {@code (chunkX, chunkZ)}? */
        public boolean intersectsChunk(int chunkX, int chunkZ) {
            int x0 = chunkX * 16;
            int z0 = chunkZ * 16;
            return this.maxX() >= x0 && this.minX <= x0 + 15 && this.maxZ() >= z0 && this.minZ <= z0 + 15;
        }
    }

    private static final Map<Site, Placement> CACHE = new ConcurrentHashMap<>();

    private StorySites() {
    }

    /** Os lugares da Lua, na ordem da trama. */
    public static final List<Site> MOON_SITES = List.of(Site.MOON_BASE, Site.MOON_PROBE);

    /** A posição fixa de um lugar da Lua (calculada uma vez). */
    public static Placement moon(Site site) {
        if (!site.onMoon()) {
            throw new IllegalArgumentException("a estação da Terra não tem posição fixa: " + site);
        }
        return CACHE.computeIfAbsent(site, StorySites::computeMoon);
    }

    /** Os lugares da Lua cuja pegada toca o chunk. */
    public static List<Placement> moonPlacementsTouching(int chunkX, int chunkZ) {
        return MOON_SITES.stream().map(StorySites::moon).filter(p -> p.intersectsChunk(chunkX, chunkZ)).toList();
    }

    private static Placement computeMoon(Site site) {
        PlanetTerrain terrain = PlanetTerrains.analytic(Planets.MOON);
        SiteLayout layout = SiteLayouts.of(site);
        int nominalX = site == Site.MOON_BASE ? MOON_BASE_NOMINAL_X : MOON_PROBE_NOMINAL_X;
        int nominalZ = site == Site.MOON_BASE ? MOON_BASE_NOMINAL_Z : MOON_PROBE_NOMINAL_Z;
        int nominalChunkX = Math.floorDiv(nominalX, 16);
        int nominalChunkZ = Math.floorDiv(nominalZ, 16);
        Placement best = null;
        long bestScore = Long.MAX_VALUE;
        for (int dx = -SEARCH_CHUNKS; dx <= SEARCH_CHUNKS; dx++) {
            for (int dz = -SEARCH_CHUNKS; dz <= SEARCH_CHUNKS; dz++) {
                int centerX = (nominalChunkX + dx) * 16 + 8;
                int centerZ = (nominalChunkZ + dz) * 16 + 8;
                int minX = centerX - layout.width() / 2;
                int minZ = centerZ - layout.depth() / 2;
                int[] stats = heights(terrain, minX, minZ, layout.width(), layout.depth());
                int roughness = stats[1] - stats[0];
                long score = roughness * 1_000_000L + (long) dx * dx + (long) dz * dz;
                if (score < bestScore) {
                    bestScore = score;
                    best = new Placement(site, minX, stats[2], minZ, layout.width(), layout.depth(), roughness);
                }
            }
        }
        return best;
    }

    /** Menor, maior e média (arredondada) das alturas sob a pegada, amostradas de 2 em 2 blocos. */
    private static int[] heights(PlanetTerrain terrain, int minX, int minZ, int width, int depth) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        long sum = 0;
        int n = 0;
        for (int x = minX; x < minX + width; x += 2) {
            for (int z = minZ; z < minZ + depth; z += 2) {
                int h = terrain.surfaceHeight(x, z);
                min = Math.min(min, h);
                max = Math.max(max, h);
                sum += h;
                n++;
            }
        }
        return new int[] {min, max, (int) Math.round(sum / (double) n)};
    }
}
