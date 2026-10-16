package com.spacehorizons.space.flight;

import org.jspecify.annotations.Nullable;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.space.SolarSystem;

/**
 * Zonas de dimensão do foguete: a dimensão do planeta vai do chão até a altitude de saída
 * ({@code 5000 · R / R_Terra}: Terra 5.000, Lua 1.250); acima dela o foguete vive na dimensão de espaço. A volta ao
 * corpo só acontece abaixo de {@link FlightConstants#ZONE_ENTER_FACTOR} (0,8) vezes a altitude de saída, para o foguete
 * não ficar trocando de dimensão na fronteira.
 */
public final class Zones {
    private Zones() {
    }

    /** Altitude (blocos acima do nível do mar) em que o foguete sai da dimensão do planeta. */
    public static double exitAltitude(PlanetDefinition planet) {
        return FlightConstants.EXIT_ALTITUDE_EARTH * planet.radius() / SolarSystem.EARTH.planet().radius();
    }

    /** Altitude abaixo da qual o foguete que está no espaço entra na dimensão do planeta (histerese de 0,8). */
    public static double enterAltitude(PlanetDefinition planet) {
        return FlightConstants.ZONE_ENTER_FACTOR * exitAltitude(planet);
    }

    /**
     * O foguete que está na dimensão de {@code planet} na coordenada {@code y} deve passar ao espaço? É a altitude
     * ({@code y − seaLevel}) acima da altitude de saída.
     */
    public static boolean shouldLeave(PlanetDefinition planet, double y) {
        return y - planet.seaLevel() > exitAltitude(planet);
    }

    /**
     * O foguete que está na dimensão de espaço na posição absoluta {@code absPos} (eixos do espaço, ver
     * {@code FrameBridge.spaceDimToSpace}) deve entrar na dimensão de algum corpo? Devolve o corpo (o de menor altitude
     * relativa à sua zona, se por acaso houver mais de um) ou {@code null}. {@code clock} dá a posição da Lua.
     */
    public static SolarSystem.@Nullable Body shouldEnter(double clock, V3 absPos) {
        SolarSystem.Body best = null;
        double bestRatio = Double.POSITIVE_INFINITY;
        for (SolarSystem.Body body : SolarSystem.BODIES) {
            double[] center = new double[3];
            body.positionAt(clock, center);
            PlanetDefinition planet = body.planet();
            double altitude = absPos.minus(V3.of(center)).length() - planet.radius();
            double ratio = altitude / exitAltitude(planet);
            if (altitude < enterAltitude(planet) && ratio < bestRatio) {
                best = body;
                bestRatio = ratio;
            }
        }
        return best;
    }
}
