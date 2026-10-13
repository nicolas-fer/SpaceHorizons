package com.spacehorizons.planet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import com.spacehorizons.planet.map.PlanetMap;
import com.spacehorizons.planet.map.PlanetMapTerrain;
import com.spacehorizons.planet.terrain.MoonTerrain;
import com.spacehorizons.planet.terrain.SphericalTerrain;

/**
 * Ponto de acesso único ao terreno visto de longe de cada planeta (cacheado, thread-safe).
 *
 * A Lua é analítica (MoonTerrain). A Terra vem do gerador vanilla do mundo: {@link #get} devolve um terreno que
 * responde pelo {@link PlanetMap} pré-calculado quando ele existe (servidor e cliente compartilham a mesma fonte) e
 * pelo terreno analítico simples enquanto ele não chega. O mapa é um estado estático por JVM: quem o publica
 * ({@code PlanetMapService} no servidor, o handler de rede no cliente) o limpa ao sair do mundo.
 */
public final class PlanetTerrains {
    private static final Map<PlanetDefinition, PlanetTerrain> ANALYTIC = new ConcurrentHashMap<>();
    private static final Map<PlanetDefinition, PlanetTerrain> CACHE = new ConcurrentHashMap<>();

    private PlanetTerrains() {
    }

    /** Terreno de longe do planeta: com mapa (Terra) quando disponível, senão o analítico. */
    public static PlanetTerrain get(PlanetDefinition planet) {
        return CACHE.computeIfAbsent(planet, PlanetTerrains::create);
    }

    /**
     * Terreno analítico puro (ruído da esfera), que NÃO consulta o mapa. É o que um gerador de chunks analítico
     * (PlanetChunkGenerator/PlanetBiomeSource) deve usar, para nunca ler um mapa que veio de outro gerador.
     */
    public static PlanetTerrain analytic(PlanetDefinition planet) {
        return ANALYTIC.computeIfAbsent(planet, PlanetTerrains::createAnalytic);
    }

    /** Publica o mapa do planeta (troca o anterior). Só planetas estilo Terra têm mapa. */
    public static void setMap(PlanetMap map) {
        mapTerrain(map.planet()).setMap(map);
    }

    /** Remove o mapa do planeta (volta ao terreno analítico). */
    public static void clearMap(PlanetDefinition planet) {
        if (get(planet) instanceof PlanetMapTerrain terrain && terrain.hasMap()) {
            terrain.setMap(null);
        }
    }

    /** Remove todos os mapas (saída do mundo). */
    public static void clearMaps() {
        for (PlanetTerrain terrain : CACHE.values()) {
            if (terrain instanceof PlanetMapTerrain mapTerrain && mapTerrain.hasMap()) {
                mapTerrain.setMap(null);
            }
        }
    }

    /** O mapa atual do planeta, ou null. */
    public static @Nullable PlanetMap map(PlanetDefinition planet) {
        return get(planet) instanceof PlanetMapTerrain terrain ? terrain.map() : null;
    }

    /**
     * Número que muda sempre que o mapa do planeta é publicado ou removido; 0 para planetas sem mapa. Consumidores
     * que guardam malhas comparam com o valor de quando construíram.
     */
    public static int version(PlanetDefinition planet) {
        return get(planet) instanceof PlanetMapTerrain terrain ? terrain.version() : 0;
    }

    private static PlanetMapTerrain mapTerrain(PlanetDefinition planet) {
        if (get(planet) instanceof PlanetMapTerrain terrain) {
            return terrain;
        }
        throw new IllegalArgumentException("o planeta " + planet.id() + " não usa mapa");
    }

    private static PlanetTerrain create(PlanetDefinition planet) {
        return switch (planet.style()) {
            case EARTH -> new PlanetMapTerrain(planet, analytic(planet));
            case MOON -> analytic(planet);
        };
    }

    private static PlanetTerrain createAnalytic(PlanetDefinition planet) {
        return switch (planet.style()) {
            case EARTH -> new SphericalTerrain(planet);
            case MOON -> new MoonTerrain(planet);
        };
    }
}
