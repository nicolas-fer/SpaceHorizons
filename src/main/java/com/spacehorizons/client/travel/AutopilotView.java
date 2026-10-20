package com.spacehorizons.client.travel;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.phys.Vec3;

/**
 * A posição REAL do piloto automático no espaço, recebida do servidor a cada tick enquanto o jogador está estacionado na dimensão de
 * espaço (ver {@code AutopilotSkyPayload}). {@code ShipView} a usa como a posição do observador do céu. Interpolada entre dois pacotes como
 * a posição virtual do foguete. Só a thread principal do cliente lê e escreve.
 */
public final class AutopilotView {
    private static boolean active;
    private static Vec3 previous = Vec3.ZERO;
    private static Vec3 current = Vec3.ZERO;

    private AutopilotView() {
    }

    /** Pacote do servidor: nova posição real (ou o fim dela). */
    public static void update(boolean isActive, double x, double y, double z) {
        Vec3 at = new Vec3(x, y, z);
        previous = isActive && active ? current : at;
        current = at;
        active = isActive;
    }

    /** A posição real interpolada (coordenadas da dimensão de espaço), ou null se o voo não está estacionado no espaço. */
    public static @Nullable Vec3 position(float partialTick) {
        if (!active) {
            return null;
        }
        return previous.lerp(current, Math.max(0.0F, Math.min(1.0F, partialTick)));
    }

    public static void reset() {
        active = false;
    }
}
