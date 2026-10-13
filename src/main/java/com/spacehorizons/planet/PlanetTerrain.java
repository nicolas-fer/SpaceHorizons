package com.spacehorizons.planet;

/**
 * Fonte única de verdade do relevo. Chunks vanilla, LODs do DH e a futura malha orbital
 * consultam esta interface, por isso as implementações devem ser determinísticas, thread-safe
 * e baratas (o DH chama de várias threads, para milhões de colunas).
 */
public interface PlanetTerrain {
    PlanetDefinition planet();

    /** Y do bloco mais alto sólido da coluna. Deve ser periódico em X com período = circunferência. */
    int surfaceHeight(int blockX, int blockZ);

    SurfaceType surfaceType(int blockX, int blockZ);
}
