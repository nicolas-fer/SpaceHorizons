package com.spacehorizons.rocket.blueprint;

import org.jspecify.annotations.Nullable;

/**
 * A planta contra o mundo (lógica pura): para cada bloco da planta, FALTA (vazio ou substituível no lugar), ERRADO (outro bloco ou outro
 * estado) ou CERTO. O holograma desenha o fantasma onde falta, marca em vermelho o errado e some no certo; o painel mostra "faltam N blocos".
 * É só sugestão: nada aqui trava a montagem.
 *
 * <p>Quem olha o mundo é a {@link Probe} (no jogo, {@code BlueprintWorld} compara BlockStates normalizados; nos testes, strings).
 */
public final class BlueprintCheck {
    public static final byte MISSING = 0;
    public static final byte WRONG = 1;
    public static final byte CORRECT = 2;
    /** O estado da planta não existe neste jogo (bloco de um mod ausente): não conta nem aparece. */
    public static final byte UNKNOWN = 3;

    private BlueprintCheck() {
    }

    /** O que está no mundo no bloco {@code entry} da planta (em dx, dy, dz da âncora): um dos códigos acima. */
    @FunctionalInterface
    public interface Probe {
        byte status(int entry, int dx, int dy, int dz);
    }

    /**
     * O resultado: o código de cada bloco (na ordem da planta) e as contagens. {@code total} não conta os desconhecidos.
     */
    public record Progress(byte[] status, int total, int missing, int wrong, int correct) {
        /** A planta está inteira no mundo, sem bloco errado. */
        public boolean complete() {
            return this.missing == 0 && this.wrong == 0;
        }

        /** Faltam ou estão errados (o que ainda precisa de mão). */
        public int toDo() {
            return this.missing + this.wrong;
        }
    }

    public static Progress check(Blueprint blueprint, Probe probe) {
        byte[] status = new byte[blueprint.size()];
        int missing = 0;
        int wrong = 0;
        int correct = 0;
        for (int i = 0; i < status.length; i++) {
            byte s = probe.status(i, blueprint.dx(i), blueprint.dy(i), blueprint.dz(i));
            status[i] = s;
            switch (s) {
                case MISSING -> missing++;
                case WRONG -> wrong++;
                case CORRECT -> correct++;
                default -> {
                }
            }
        }
        return new Progress(status, missing + wrong + correct, missing, wrong, correct);
    }

    /**
     * A regra de comparação sobre estados já normalizados: {@code actual} null (vazio ou substituível) é FALTA, igual é CERTO, diferente é
     * ERRADO; {@code expected} null (desconhecido) é DESCONHECIDO.
     */
    public static <S> byte compare(@Nullable S expected, @Nullable S actual) {
        if (expected == null) {
            return UNKNOWN;
        }
        if (actual == null) {
            return MISSING;
        }
        return expected.equals(actual) ? CORRECT : WRONG;
    }
}
