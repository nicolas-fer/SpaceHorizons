package com.spacehorizons.planet;

/**
 * Fonte única de verdade do relevo visto de longe. Chunks, LODs do DH, a malha orbital e os corpos vistos do espaço
 * consultam esta interface, por isso as implementações devem ser determinísticas, thread-safe e baratas
 * (são chamadas de várias threads, para milhões de colunas).
 *
 * Num planeta gerado pelo gerador vanilla a implementação pode ser aproximada (ex.: um mapa pré-calculado);
 * o que importa é que todo consumidor de longe use a MESMA fonte, para não haver salto entre camadas.
 */
public interface PlanetTerrain {
    PlanetDefinition planet();

    /** Y do bloco mais alto sólido da coluna. Deve ser periódico em X com período = circunferência. */
    int surfaceHeight(int blockX, int blockZ);

    SurfaceType surfaceType(int blockX, int blockZ);

    /**
     * Cor média da coluna vista de longe (0xRRGGBB, já com água/neve/vegetação), usada pela malha orbital e pelos
     * corpos no céu. O padrão deriva do SurfaceType; terrenos com biomas reais devem sobrescrever.
     */
    default int surfaceColor(int blockX, int blockZ) {
        return SurfaceColors.of(surfaceType(blockX, blockZ));
    }

    /** true se a superfície vista de longe é água (reflexo do sol, nível do mar plano na malha). */
    default boolean isWater(int blockX, int blockZ) {
        return surfaceType(blockX, blockZ) == SurfaceType.OCEAN;
    }
}
