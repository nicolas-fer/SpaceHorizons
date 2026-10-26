package com.spacehorizons.ship.deck;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.jspecify.annotations.Nullable;

import net.minecraft.world.phys.AABB;

/**
 * A decisão de embarcar no convés (fase 2e, frente 2): dada a posição dos pés no referencial do retrato, o jogador está APOIADO num sólido do
 * retrato (ou a ponto de estar) e, se estiver, em que altura ficam os pés? Lógica pura, testada sem o jogo. A nave não tem colisão com
 * entidades do mundo: quem pula num convés que paira atravessa o piso no mundo, então o embarque aceita uma folga para baixo ({@code reach}: os
 * pés já passaram do topo do piso) e uma para cima ({@code above}: os pés ainda estão logo acima dele), e põe os pés EXATAMENTE no topo.
 */
public final class DeckBoarding {
    /** Folga automática (pisar no convés): quanto os pés podem estar abaixo do topo do piso (blocos). */
    public static final double REACH = 0.6;
    /** Folga automática: quanto os pés podem estar acima do topo do piso (blocos). */
    public static final double ABOVE = 0.25;
    /**
     * Levantar do assento: o ponto do assento põe os pés perto da base do bloco do cockpit, então o topo dele (e o piso em volta) fica bem ACIMA
     * dos pés; o jogador sobe ao topo mais alto em que cabe.
     */
    public static final double SEAT_REACH = 1.6;
    public static final double SEAT_ABOVE = 0.8;
    /** Velocidade máxima da nave (blocos por tick) para o embarque automático de quem pisa no convés: numa nave rápida o convés "varre" o jogador. */
    public static final double MAX_AUTO_SPEED = 2.0;
    /** Penetração tolerada na caixa já assentada no topo (erro de ponto flutuante). */
    private static final double FIT = 0.001;
    private static final double EPS = 1.0E-6;

    private DeckBoarding() {
    }

    /**
     * Onde ficam os pés se o jogador embarcar: o mesmo x e z, com y no topo do piso mais alto sob a caixa dele que o deixa de pé (a caixa inteira
     * livre); {@code null} se nenhum topo está dentro da folga (o jogador não está no convés). {@code reach} é quanto os pés podem estar
     * ABAIXO do topo e {@code above} quanto podem estar acima dele.
     */
    public static @Nullable Vector3d settle(Vector3dc feet, double width, double height, DeckCollision.Colliders colliders, double reach, double above) {
        double half = width / 2.0;
        AABB region = new AABB(feet.x() - half, feet.y() - above, feet.z() - half, feet.x() + half, feet.y() + reach, feet.z() + half);
        List<Double> tops = new ArrayList<>();
        colliders.collect(region, c -> {
            boolean overX = Math.min(feet.x() + half, c.maxX) - Math.max(feet.x() - half, c.minX) > EPS;
            boolean overZ = Math.min(feet.z() + half, c.maxZ) - Math.max(feet.z() - half, c.minZ) > EPS;
            if (overX && overZ && c.maxY >= feet.y() - above - EPS && c.maxY <= feet.y() + reach + EPS) {
                tops.add(c.maxY);
            }
        });
        tops.sort((a, b) -> Double.compare(b, a));
        for (double top : tops) {
            AABB box = new AABB(feet.x() - half, top, feet.z() - half, feet.x() + half, top + height, feet.z() + half);
            if (DeckCollision.penetration(box, colliders) <= FIT) {
                return new Vector3d(feet.x(), top, feet.z());
            }
        }
        return null;
    }

    /**
     * Como {@link #settle}, mas se o ponto não serve tenta em volta dele (anéis de 1 em 1 bloco, em 8 direções, até {@code maxRadius}): quem levanta do
     * assento fica ao lado dele quando em cima não cabe.
     */
    public static @Nullable Vector3d settleNear(Vector3dc feet, double width, double height, DeckCollision.Colliders colliders, double reach, double above,
            int maxRadius) {
        Vector3d at = settle(feet, width, height, colliders, reach, above);
        for (int r = 1; at == null && r <= maxRadius; r++) {
            for (int dx = -1; dx <= 1 && at == null; dx++) {
                for (int dz = -1; dz <= 1 && at == null; dz++) {
                    if (dx != 0 || dz != 0) {
                        at = settle(new Vector3d(feet.x() + dx * r, feet.y(), feet.z() + dz * r), width, height, colliders, reach, above);
                    }
                }
            }
        }
        return at;
    }

    /** O embarque automático vale com a nave devagar o bastante em relação ao mundo. */
    public static boolean slowEnough(double shipSpeedBlocksPerTick) {
        return shipSpeedBlocksPerTick <= MAX_AUTO_SPEED;
    }
}
