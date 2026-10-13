package com.spacehorizons.planet;

/** Família de terreno de um planeta: escolhe a implementação de PlanetTerrain e os blocos de superfície. */
public enum TerrainStyle {
    /** Continentes, oceanos e clima por latitude. */
    EARTH,
    /** Regolito cinza com crateras e mares escuros; sem água nem atmosfera. */
    MOON
}
