package com.spacehorizons.survival.suit;

/**
 * Quais peças do traje espacial o jogador está vestindo. Lógica pura (sem Minecraft), testável sem iniciar o jogo;
 * {@link SuitGear} preenche isto a partir do equipamento de verdade.
 *
 * Duas regras diferentes, de propósito:
 * <ul>
 *   <li>respirar do tanque no vácuo exige só <b>capacete e peitoral</b> ({@link #canBreathe()}): é onde ficam a
 *       viseira selada e a mochila de suporte à vida;</li>
 *   <li>ficar a salvo do frio do vácuo exige <b>as quatro peças</b> ({@link #protectsFromCold()}): o isolamento
 *       térmico só vale com o corpo todo coberto. Uma peça só nunca basta.</li>
 * </ul>
 */
public record SuitPieces(boolean helmet, boolean chestplate, boolean leggings, boolean boots) {
    /** Nenhuma peça do traje. */
    public static final SuitPieces NONE = new SuitPieces(false, false, false, false);
    /** O traje completo. */
    public static final SuitPieces FULL = new SuitPieces(true, true, true, true);

    /** Respira do recipiente de oxigênio: capacete e peitoral. */
    public boolean canBreathe() {
        return helmet && chestplate;
    }

    /** Isolado do frio do vácuo: as quatro peças. */
    public boolean protectsFromCold() {
        return helmet && chestplate && leggings && boots;
    }

    /** Quantas das quatro peças estão vestidas. */
    public int count() {
        return (helmet ? 1 : 0) + (chestplate ? 1 : 0) + (leggings ? 1 : 0) + (boots ? 1 : 0);
    }
}
