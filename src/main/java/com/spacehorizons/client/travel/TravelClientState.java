package com.spacehorizons.client.travel;

/**
 * Sinal "o próximo respawn é uma transferência contínua entre corpos". Ligado pelo aviso do servidor (que chega
 * imediatamente antes do pacote de respawn) e consumido por ClientPacketListenerMixin. Expira sozinho: se o teleporte
 * for cancelado no servidor, um respawn qualquer depois (portal, morte) não pode herdar o sinal e pular a tela.
 *
 * Só a thread principal do cliente lê e escreve.
 */
public final class TravelClientState {
    /** Tempo máximo entre o aviso e o respawn. A conexão é ordenada, então na prática são milissegundos. */
    private static final long EXPIRY_NANOS = 3_000_000_000L;

    private static long deadline;
    private static boolean pending;

    private TravelClientState() {
    }

    private static double x;
    private static double y;
    private static double z;
    private static float yRot;
    private static float xRot;

    public static void markPending(double px, double py, double pz, float pyRot, float pxRot) {
        x = px;
        y = py;
        z = pz;
        yRot = pyRot;
        xRot = pxRot;
        pending = true;
        deadline = System.nanoTime() + EXPIRY_NANOS;
    }

    /** true uma única vez por aviso, se ainda não expirou. */
    public static boolean consumePending() {
        boolean valid = pending && System.nanoTime() - deadline < 0;
        pending = false;
        return valid;
    }

    /** Põe o jogador novo já na posição e no olhar de destino (o pacote de posição só chega depois). */
    public static void applyTo(net.minecraft.world.entity.Entity player) {
        player.snapTo(x, y, z, yRot, xRot);
    }

    public static void reset() {
        pending = false;
    }
}
