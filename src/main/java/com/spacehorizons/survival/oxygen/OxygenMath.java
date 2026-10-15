package com.spacehorizons.survival.oxygen;

import java.util.function.IntSupplier;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.atmosphere.AtmosphereModel;

/**
 * Regras puras do oxigênio (sem Minecraft além dos records de planeta), testáveis sem iniciar o jogo.
 * Todas as constantes de balanceamento ficam aqui. Unidade da reserva: "ticks de respiração".
 */
public final class OxygenMath {
    /** Reserva máxima: 600 ticks = 30 s de respiração no vácuo sem nenhuma fonte. */
    public static final int MAX_RESERVE = 600;
    /** Quanto a reserva cai por tick no vácuo (1 = 20 por segundo). Também é o abastecimento que sustenta a respiração. */
    public static final int CONSUMPTION_PER_TICK = 1;
    /** Quanto a reserva sobe por tick quando se respira (fora do vácuo ou com fonte): enche do zero em 3 s. */
    public static final int RECHARGE_PER_TICK = 10;
    /** Intervalo entre danos com a reserva vazia no vácuo (1 s, como o afogamento). */
    public static final int DAMAGE_INTERVAL_TICKS = 20;
    /** Dano de cada golpe (1 coração). */
    public static final float DAMAGE_AMOUNT = 2.0F;

    private OxygenMath() {
    }

    /** Resultado de um tick: o novo estado e o dano a aplicar neste tick (0 = nenhum). */
    public record Result(OxygenState state, float damage) {
    }

    /**
     * Vácuo = planeta sem atmosfera ou acima do topo dela (o mesmo {@link AtmosphereModel#isVacuum} do visual).
     * {@code y} é a altura absoluta do ponto de respiração (os olhos); a altitude é contada desde o nível do mar.
     */
    public static boolean isVacuum(PlanetDefinition planet, double y) {
        return AtmosphereModel.of(planet).isVacuum(y - planet.seaLevel());
    }

    /** Soma das fontes: cada uma vale no mínimo 0 e o total satura em Integer.MAX_VALUE. */
    public static int totalSupply(Iterable<? extends IntSupplier> sources) {
        long total = 0;
        for (IntSupplier source : sources) {
            total += Math.max(0, source.getAsInt());
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    /**
     * Um tick do sistema para um jogador.
     *
     * @param enabled   regra de jogo ligada
     * @param exempt    criativo, espectador ou morto: reserva sempre cheia, sem dano
     * @param vacuum    o jogador está num ambiente sem ar
     * @param supply    abastecimento total das fontes neste tick
     * @param tickCount contador de ticks do jogador (decide em que tick cai o dano periódico)
     */
    public static Result step(OxygenState current, boolean enabled, boolean exempt, boolean vacuum, int supply, int tickCount) {
        if (!enabled) {
            return new Result(new OxygenState(MAX_RESERVE, false, false), 0.0F);
        }
        if (exempt) {
            return new Result(new OxygenState(MAX_RESERVE, true, false), 0.0F);
        }
        // respira quando há ar ou quando as fontes cobrem todo o consumo
        boolean breathing = !vacuum || supply >= CONSUMPTION_PER_TICK;
        int reserve;
        if (breathing) {
            reserve = Math.min(MAX_RESERVE, current.reserve() + RECHARGE_PER_TICK);
        } else {
            // fonte parcial só diminui o consumo
            reserve = Math.max(0, current.reserve() - (CONSUMPTION_PER_TICK - Math.max(0, supply)));
        }
        boolean supplied = vacuum && supply > 0;
        float damage = damageAt(reserve, vacuum && !breathing, tickCount);
        return new Result(new OxygenState(reserve, true, supplied), damage);
    }

    /** Dano periódico: só com a reserva vazia, sem respirar, e a cada {@link #DAMAGE_INTERVAL_TICKS} ticks. */
    public static float damageAt(int reserve, boolean suffocating, int tickCount) {
        if (!suffocating || reserve > 0) {
            return 0.0F;
        }
        return Math.floorMod(tickCount, DAMAGE_INTERVAL_TICKS) == 0 ? DAMAGE_AMOUNT : 0.0F;
    }
}
