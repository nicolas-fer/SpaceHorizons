package com.spacehorizons.survival.oxygen;

import net.minecraft.server.level.ServerPlayer;

/**
 * Fonte de oxigênio consultada a cada tick de cada jogador (só no servidor): traje e tanque (etapa 2), sala
 * selada (etapa 4) etc. implementam isto e se registram em {@link OxygenSources#register}.
 */
@FunctionalInterface
public interface OxygenSource {
    /**
     * Quanto este jogador está sendo abastecido neste tick, na mesma unidade de
     * {@link OxygenMath#CONSUMPTION_PER_TICK}: 0 = nada; valor maior ou igual ao consumo = respira normalmente
     * (e a reserva recarrega); entre os dois reduz só o consumo. Só é chamada no vácuo.
     * A fonte é quem gasta o próprio estoque (ex.: baixar a carga do tanque) dentro deste método.
     */
    int supply(ServerPlayer player);
}
