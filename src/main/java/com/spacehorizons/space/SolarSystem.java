package com.spacehorizons.space;

import java.util.List;
import java.util.Optional;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;

/**
 * Posições dos corpos no "espaço": um referencial único, em blocos, com a Terra no centro.
 * Por enquanto tudo é estático (sem órbitas nem rotação): fica fácil de testar e de manter
 * o mesmo quadro antes e depois da troca de dimensão.
 *
 * Eixos do espaço (ver SpaceFrame): o ponto (lat 0, lon 0) de cada planeta aponta para +X,
 * leste (+lon, +X do Minecraft) para +Z e +lat (+Z do Minecraft) para +Y.
 */
public final class SolarSystem {
    /** Um corpo: planeta e posição do centro no espaço. */
    public record Body(PlanetDefinition planet, double x, double y, double z) {
    }

    /** Distância centro a centro Terra–Lua (comprimida: ~5,75 raios terrestres em vez de 60). */
    public static final double EARTH_MOON_DISTANCE = 60_000;

    /**
     * A Lua fica no zênite do spawn da Terra (x=0, z=0): subir em linha reta a partir do spawn leva até ela,
     * e a viagem no Overworld é só em Y (sem carregar chunks novos no caminho). A face da Lua voltada para
     * a Terra é a do ponto lon=π (x = ±C/2 na dimensão da Lua).
     */
    public static final Body EARTH = new Body(Planets.EARTH, 0, 0, 0);
    public static final Body MOON = new Body(Planets.MOON, EARTH_MOON_DISTANCE, 0, 0);

    public static final List<Body> BODIES = List.of(EARTH, MOON);

    private SolarSystem() {
    }

    public static Optional<Body> bodyOf(PlanetDefinition planet) {
        return BODIES.stream().filter(b -> b.planet().equals(planet)).findFirst();
    }
}
