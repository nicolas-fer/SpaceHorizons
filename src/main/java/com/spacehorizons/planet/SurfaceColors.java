package com.spacehorizons.planet;

/** Cor média vista de longe por SurfaceType (0xRRGGBB). Java puro: serve de padrão para PlanetTerrain.surfaceColor. */
public final class SurfaceColors {
    private SurfaceColors() {
    }

    public static int of(SurfaceType type) {
        return switch (type) {
            case OCEAN -> 0x3F76E4;
            case BEACH -> 0xDBD3A0;
            case PLAINS -> 0x7BAA4C;
            case FOREST -> 0x4E7A2E;
            case DESERT -> 0xD9C98E;
            case MOUNTAIN -> 0x8A8A8A;
            case SNOW -> 0xF2F6F8;
            case POLAR_ICE -> 0xC8DCF4;
            case REGOLITH -> 0xA8A8A4;
            case MARE -> 0x5A5A5C;
            case HIGHLAND -> 0xBDBDB8;
            case CRATER_FLOOR -> 0x76766F;
            case VOLCANIC -> 0x6E3B2A;
        };
    }
}
