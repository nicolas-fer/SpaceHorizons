package com.spacehorizons.ship.deck;

/**
 * A queda dentro do convés, como o vanilla conta ({@code Entity.checkFallDamage}) mas em coordenadas locais: a distância acumula enquanto o
 * tripulante desce e vale ao tocar o piso. O servidor aplica o dano ({@code causeFallDamage}) com o bloco do RETRATO sob os pés. Lógica pura.
 */
public final class DeckFall {
    private DeckFall() {
    }

    /** O resultado de um passo: a distância acumulada que segue e, se tocou o piso com queda, a distância da queda (senão 0). */
    public record Step(double fall, double landed) {
    }

    /**
     * @param fall distância de queda acumulada antes do passo
     * @param dy variação local da altura dos pés neste passo (negativa ao descer)
     * @param onGround se o estado novo está no chão
     */
    public static Step next(double fall, double dy, boolean onGround) {
        double total = dy < 0.0 ? fall - dy : fall;
        if (onGround) {
            return new Step(0.0, total);
        }
        return new Step(total, 0.0);
    }

    /** O multiplicador de dano do bloco sob os pés: 1 no geral, 0 no slime, 0,2 no feno e no mel, 0,5 na cama. */
    public static float multiplier(String blockPath) {
        return switch (blockPath) {
            case "slime_block" -> 0.0F;
            case "hay_block", "honey_block" -> 0.2F;
            default -> blockPath.endsWith("_bed") ? 0.5F : 1.0F;
        };
    }
}
