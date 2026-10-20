package com.spacehorizons.rocket;

import java.util.UUID;

import net.minecraft.world.phys.Vec3;

/**
 * Onde o foguete fica ESTACIONADO na dimensão de espaço (lógica pura). No espaço aberto o voo é inercial e a posição real (que passa de
 * 50 blocos por tick e chega a centenas de milhares de blocos) vive só no estado de voo; a entidade, o piloto e os passageiros ficam
 * parados num ponto fixo da dimensão, num chunk só, que um ticket mantém carregado: nada se move, nenhum chunk novo é gerado e o cliente
 * não precisa de chunks novos para acompanhar. O céu é desenhado a partir da posição real, sincronizada à parte
 * ({@link RocketEntity#virtualSpacePosition}).
 *
 * <p>Um ponto por foguete, escolhido pelo UUID (que atravessa as trocas de dimensão, ao contrário do id da entidade), numa grade de
 * {@link #GRID}×{@link #GRID} células de {@link #SPACING} blocos a partir de {@link #BASE}: longe das posições reais do espaço (o sistema
 * todo cabe em ~1 milhão de blocos) e dos outros foguetes. Dois UUIDs na mesma célula (1 em 65 mil) dividem o ponto: os foguetes ficam
 * sobrepostos na dimensão de espaço, o que não tem efeito além do visual (não há colisão de entidades).
 */
public final class SpaceParking {
    /** Canto da grade (X e Z da dimensão de espaço), longe de qualquer posição real do sistema. */
    public static final double BASE = 8_000_000.0;
    /** Lado de uma célula da grade, em blocos. */
    public static final double SPACING = 1_024.0;
    /** Células por lado da grade. */
    public static final int GRID = 256;
    /** Altura do ponto: no meio da dimensão (16 blocos de altura, min_y 0). */
    public static final double Y = 8.0;

    private SpaceParking() {
    }

    /** O ponto (centro da caixa do foguete) onde o foguete de UUID {@code id} fica estacionado. */
    public static Vec3 pointFor(UUID id) {
        long m = id.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ id.getLeastSignificantBits();
        m ^= m >>> 31;
        m *= 0xBF58476D1CE4E5B9L;
        m ^= m >>> 29;
        int cell = (int) Long.remainderUnsigned(m, (long) GRID * GRID);
        return new Vec3(BASE + (cell % GRID) * SPACING + 8.0, Y, BASE + (cell / GRID) * SPACING + 8.0);
    }
}
