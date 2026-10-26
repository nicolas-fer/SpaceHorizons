package com.spacehorizons.ship.deck;

import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Onde o jogador fica em pé ao levantar de um assento do retrato: o ponto livre mais perto do assento (coordenadas locais), com chão firme
 * embaixo e espaço para o corpo inteiro. Lógica pura sobre {@link DeckCollision.Colliders}.
 */
public final class DeckStand {
    /** Quanto abaixo dos pés o chão pode estar para valer como apoio (blocos). */
    private static final double GROUND_PROBE = 0.05;
    /** Folga na conferência de espaço livre: encostar numa parede não é bater nela. */
    private static final double EPSILON = 1.0E-6;

    private DeckStand() {
    }

    /**
     * O ponto (pés) em pé mais perto do centro da célula do assento {@code (cellX, cellY, cellZ)}: primeiro as células vizinhas no mesmo nível,
     * depois um nível acima e um abaixo, em anéis de raio 1 e 2; sem nenhum, em cima do assento (um bloco acima da célula).
     */
    public static Vec3 find(DeckCollision.Colliders colliders, int cellX, int cellY, int cellZ, double width, double height) {
        double bestDistance = Double.MAX_VALUE;
        Vec3 best = null;
        int[] levels = {0, 1, -1};
        for (int radius = 1; radius <= 2 && best == null; radius++) {
            for (int level : levels) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                            continue;
                        }
                        Vec3 spot = new Vec3(cellX + dx + 0.5, cellY + level, cellZ + dz + 0.5);
                        double d = Math.abs(dx) + Math.abs(dz) + Math.abs(level) * 1.5;
                        if (d < bestDistance && fits(colliders, spot, width, height)) {
                            bestDistance = d;
                            best = spot;
                        }
                    }
                }
            }
        }
        return best != null ? best : new Vec3(cellX + 0.5, cellY + 1.0, cellZ + 0.5);
    }

    /** Cabe o corpo (largura x altura) com os pés em {@code feet}, livre de sólidos e com chão logo abaixo. */
    public static boolean fits(DeckCollision.Colliders colliders, Vec3 feet, double width, double height) {
        double half = width / 2.0;
        AABB body = new AABB(feet.x - half, feet.y, feet.z - half, feet.x + half, feet.y + height, feet.z + half);
        AABB shrunk = body.deflate(EPSILON);
        AtomicBoolean blocked = new AtomicBoolean();
        colliders.collect(body, box -> {
            if (box.intersects(shrunk)) {
                blocked.set(true);
            }
        });
        if (blocked.get()) {
            return false;
        }
        AABB below = new AABB(body.minX, feet.y - GROUND_PROBE, body.minZ, body.maxX, feet.y, body.maxZ).deflate(EPSILON, 0.0, EPSILON);
        AtomicBoolean ground = new AtomicBoolean();
        colliders.collect(below, box -> {
            if (box.intersects(below)) {
                ground.set(true);
            }
        });
        return ground.get();
    }
}
