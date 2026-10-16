package com.spacehorizons.rocket;

/** Contas do combustível do foguete entre os tanques (mB inteiros). Lógica pura, testável. */
public final class RocketFuel {
    private RocketFuel() {
    }

    /** Quanto cabe a mais num tanque que tem {@code current} mB (capacidade {@link RocketSpecs#TANK_CAPACITY_MB}). */
    public static int space(int current) {
        return RocketSpecs.TANK_CAPACITY_MB - clamp(current);
    }

    /** Força a quantidade de um tanque para [0, capacidade]. */
    public static int clamp(int mb) {
        return Math.max(0, Math.min(RocketSpecs.TANK_CAPACITY_MB, mb));
    }

    /**
     * Reparte {@code totalMb} entre tanques de capacidades dadas, proporcional à capacidade (tanques iguais enchem por
     * igual) e sem perder nem criar combustível: a soma do resultado é exatamente o total (limitado à capacidade somada) e
     * nenhum tanque passa da sua capacidade. A sobra de arredondamento vai, de 1 em 1 mB, para os primeiros tanques.
     */
    public static int[] distribute(int totalMb, int[] capacities) {
        int[] out = new int[capacities.length];
        long capacity = 0;
        for (int c : capacities) {
            capacity += c;
        }
        if (capacity <= 0 || totalMb <= 0) {
            return out;
        }
        long total = Math.min(totalMb, capacity);
        long given = 0;
        for (int i = 0; i < capacities.length; i++) {
            out[i] = (int) (total * capacities[i] / capacity);
            given += out[i];
        }
        for (int i = 0; given < total && i < out.length; i++) {
            if (out[i] < capacities[i]) {
                out[i]++;
                given++;
            }
        }
        return out;
    }
}
