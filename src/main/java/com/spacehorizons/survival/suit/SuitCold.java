package com.spacehorizons.survival.suit;

/**
 * Regras puras do frio do vácuo (sem Minecraft), testáveis sem iniciar o jogo. Todas as constantes de balanceamento
 * do frio ficam aqui.
 *
 * O frio reaproveita o congelamento do vanilla (a mesma mecânica da neve em pó): os "ticks de congelamento" da
 * entidade enchem até {@code getTicksRequiredToFreeze()} (140 = 7 s), o jogador vê o contorno de gelo na tela,
 * anda mais devagar, e quando está totalmente congelado leva o dano {@code freeze} a cada 2 s.
 *
 * Detalhe de ordem dentro do tick: o vanilla desconta {@link #VANILLA_THAW_PER_TICK} ticks de congelamento por tick
 * de quem não está na neve em pó, <i>antes</i> do evento de fim de tick em que esta lógica roda. Por isso o ganho
 * somado aqui é {@link #NET_FREEZE_PER_TICK} + {@link #VANILLA_THAW_PER_TICK}: o saldo líquido por tick é
 * {@link #NET_FREEZE_PER_TICK}. Como o vanilla só dá dano se achar o jogador congelado <i>depois</i> desse desconto
 * (e a nossa conta fecha em 140, o que depois do desconto é 138), o dano de congelar é aplicado por nós, e só por nós,
 * com o mesmo tipo de dano e o mesmo intervalo do vanilla; assim também não vale a roupa de couro (que no vanilla
 * imuniza contra congelar) para escapar do frio do vácuo.
 */
public final class SuitCold {
    /** Saldo líquido de ticks de congelamento por tick no vácuo sem o traje completo: 140 ticks (7 s) para congelar, como na neve em pó. */
    public static final int NET_FREEZE_PER_TICK = 1;
    /** O que o vanilla já descontou neste tick de quem não está na neve em pó (ver o comentário da classe). */
    public static final int VANILLA_THAW_PER_TICK = 2;
    /** Intervalo entre danos de congelamento: 40 ticks (2 s), igual ao vanilla. */
    public static final int DAMAGE_INTERVAL_TICKS = 40;
    /** Dano de cada golpe de frio: meio coração, igual ao vanilla. */
    public static final float DAMAGE_AMOUNT = 1.0F;

    private SuitCold() {
    }

    /**
     * O jogador está sofrendo o frio do vácuo? Só com a regra de jogo ligada, fora do criativo/espectador, no vácuo
     * (a mesma regra do oxigênio) e sem as quatro peças do traje.
     */
    public static boolean exposed(boolean enabled, boolean exempt, boolean vacuum, SuitPieces suit) {
        return enabled && !exempt && vacuum && !suit.protectsFromCold();
    }

    /**
     * Novo valor dos ticks de congelamento do jogador exposto, lido no fim do tick (já com o desconto do vanilla).
     * Nunca passa de {@code required} ({@code getTicksRequiredToFreeze()}).
     */
    public static int nextFrozenTicks(int current, int required) {
        long next = (long) Math.max(0, current) + NET_FREEZE_PER_TICK + VANILLA_THAW_PER_TICK;
        return (int) Math.min(next, required);
    }

    /** Dano de frio neste tick: só com o jogador totalmente congelado e a cada {@link #DAMAGE_INTERVAL_TICKS} ticks. */
    public static float damageAt(int frozenTicks, int required, int tickCount) {
        if (frozenTicks < required) {
            return 0.0F;
        }
        return Math.floorMod(tickCount, DAMAGE_INTERVAL_TICKS) == 0 ? DAMAGE_AMOUNT : 0.0F;
    }
}
