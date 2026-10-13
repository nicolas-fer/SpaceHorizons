package com.spacehorizons.planet;

/** Conversões entre coordenadas de bloco e a esfera. Sem estado, thread-safe. */
public final class PlanetMath {
    private PlanetMath() {
    }

    /** Traz X para [-C/2, C/2). Aplicar em todo acesso ao terreno garante periodicidade exata. */
    public static long wrapX(PlanetDefinition planet, long x) {
        long c = planet.circumferenceBlocks();
        long half = c / 2;
        return Math.floorMod(x + half, c) - half;
    }

    public static double longitudeRadians(PlanetDefinition planet, double x) {
        return x / planet.radius();
    }

    public static double latitudeRadians(PlanetDefinition planet, double z) {
        return z / planet.radius();
    }

    /**
     * Ponto na esfera unitária para (x, z). Amostrar ruído 3D aqui dá terreno sem costura no wrap,
     * e a mesma função alimenta chunks, LODs e a malha orbital.
     */
    public static void unitSphere(PlanetDefinition planet, double x, double z, double[] out) {
        double lon = longitudeRadians(planet, x);
        double lat = latitudeRadians(planet, z);
        double cosLat = Math.cos(lat);
        out[0] = cosLat * Math.cos(lon);
        out[1] = Math.sin(lat);
        out[2] = cosLat * Math.sin(lon);
    }
}
