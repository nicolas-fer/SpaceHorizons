package com.spacehorizons.planet.map;

import org.jspecify.annotations.Nullable;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetMath;
import com.spacehorizons.planet.PlanetTerrain;
import com.spacehorizons.planet.SurfaceType;

/**
 * Terreno visto de longe de um planeta cujo mundo vem do gerador vanilla: responde pelo {@link PlanetMap} quando ele
 * já existe e, enquanto ele não chega, pelo terreno analítico de reserva (a vista de longe troca sozinha quando o mapa
 * fica pronto; {@link #version()} sobe a cada troca para os consumidores reconstruírem suas malhas).
 *
 * Thread-safe: o mapa é publicado por um campo volátil e nunca muda depois de publicado.
 */
public final class PlanetMapTerrain implements PlanetTerrain {
    private final PlanetDefinition planet;
    private final PlanetTerrain fallback;
    private volatile @Nullable PlanetMap map;
    private volatile int version;

    public PlanetMapTerrain(PlanetDefinition planet, PlanetTerrain fallback) {
        this.planet = planet;
        this.fallback = fallback;
    }

    @Override
    public PlanetDefinition planet() {
        return planet;
    }

    /** true se há um mapa real: só então as cores e a água vêm do mundo (senão valem a paleta e o terreno de reserva). */
    public boolean hasMap() {
        return map != null;
    }

    public @Nullable PlanetMap map() {
        return map;
    }

    /** Muda a cada mapa publicado ou removido. */
    public int version() {
        return version;
    }

    public synchronized void setMap(@Nullable PlanetMap newMap) {
        this.map = newMap;
        this.version++;
    }

    @Override
    public int surfaceHeight(int blockX, int blockZ) {
        PlanetMap m = map;
        return m == null ? fallback.surfaceHeight(blockX, blockZ) : (int) Math.round(m.heightAt(wrap(blockX), blockZ));
    }

    @Override
    public SurfaceType surfaceType(int blockX, int blockZ) {
        PlanetMap m = map;
        return m == null ? fallback.surfaceType(blockX, blockZ) : m.typeAt(wrap(blockX), blockZ);
    }

    @Override
    public int surfaceColor(int blockX, int blockZ) {
        PlanetMap m = map;
        return m == null ? fallback.surfaceColor(blockX, blockZ) : m.colorAt(wrap(blockX), blockZ);
    }

    @Override
    public boolean isWater(int blockX, int blockZ) {
        PlanetMap m = map;
        return m == null ? fallback.isWater(blockX, blockZ) : m.waterAt(wrap(blockX), blockZ);
    }

    private double wrap(int blockX) {
        return PlanetMath.wrapX(planet, blockX);
    }
}
