package com.spacehorizons.survival.suit;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.IntStream;

import com.spacehorizons.survival.oxygen.OxygenMath;

/**
 * Contas puras de quanto e de qual recipiente de oxigênio o traje consome (sem Minecraft), testáveis sem iniciar o
 * jogo. {@link SuitGear} aplica isto aos recipientes de verdade do inventário.
 */
public final class TankMath {
    private TankMath() {
    }

    /**
     * Quanto tirar de cada recipiente para somar {@code wanted}: <b>o mais vazio (ainda com oxigênio) primeiro</b>,
     * e, em caso de empate, o de menor posição (a ordem de varredura do inventário: barra de atalhos, mochila,
     * mão secundária).
     *
     * Por que o mais vazio: esgota um recipiente por vez em vez de deixar vários pela metade, mantém os cheios
     * intactos como reserva de emergência (o jogador recarrega os vazios no compressor sem mexer nos cheios) e
     * evita que o oxigênio fique "espalhado". A soma disponível não depende da ordem; ela só decide o que o jogador
     * vê esvaziar primeiro.
     *
     * @param amounts carga atual de cada recipiente (na ordem de varredura)
     * @param wanted  quanto o traje quer neste tick
     * @return quanto sai de cada recipiente (mesmo tamanho de {@code amounts}); a soma é no máximo {@code wanted} e
     *         no máximo o total disponível, e nunca passa da carga de cada um
     */
    public static int[] plan(int[] amounts, int wanted) {
        int[] taken = new int[amounts.length];
        int left = Math.max(0, wanted);
        // índices dos recipientes com carga, do mais vazio ao mais cheio, desempatando pela posição
        int[] order = IntStream.range(0, amounts.length)
                .filter(i -> amounts[i] > 0)
                .boxed()
                .sorted((a, b) -> amounts[a] != amounts[b] ? Integer.compare(amounts[a], amounts[b]) : Integer.compare(a, b))
                .mapToInt(Integer::intValue)
                .toArray();
        for (int index : order) {
            if (left <= 0) {
                break;
            }
            int take = Math.min(left, amounts[index]);
            taken[index] = take;
            left -= take;
        }
        return taken;
    }

    /** Soma das cargas (valores negativos contam como zero), saturando em Integer.MAX_VALUE. */
    public static int total(int[] amounts) {
        long sum = Arrays.stream(amounts).mapToLong(amount -> Math.max(0, amount)).sum();
        return (int) Math.min(sum, Integer.MAX_VALUE);
    }

    /**
     * Tempo de respiração que a carga rende, no formato {@code m:ss}. Arredonda para cima, como um cronômetro
     * regressivo: um tanque cheio de 2400 mostra 2:00 e só passa a 1:59 depois de um segundo.
     */
    public static String formatClock(int oxygenTicks) {
        int ticksPerSecond = OxygenMath.CONSUMPTION_PER_TICK * 20;
        long seconds = (Math.max(0L, oxygenTicks) + ticksPerSecond - 1) / ticksPerSecond;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
