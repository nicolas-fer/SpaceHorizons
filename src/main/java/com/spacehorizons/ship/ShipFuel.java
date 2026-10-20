package com.spacehorizons.ship;

/** Contas do combustível da nave contra os tanques (lógica pura, testável). */
public final class ShipFuel {
    private ShipFuel() {
    }

    /**
     * O combustível do voo depois de somar o que mudou nos tanques por fora do voo (um galão despejado, um tanque quebrado ou posto):
     * {@code tankSumMb} é o que os tanques têm agora e {@code lastDistributedMb} o que o voo repartiu neles da última vez; a diferença é a
     * mudança externa. O resultado fica entre 0 e a capacidade.
     */
    public static double reconcile(double fuelMb, long lastDistributedMb, long tankSumMb, double capacityMb) {
        double external = tankSumMb - lastDistributedMb;
        return Math.max(0.0, Math.min(capacityMb, fuelMb + external));
    }
}
