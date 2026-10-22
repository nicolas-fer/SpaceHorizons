package com.spacehorizons.ship.deck;

import org.joml.Vector3dc;

import net.minecraft.world.phys.AABB;

/**
 * A conferência do servidor sobre o estado local que o cliente manda (o "moved wrongly" do vanilla, só que no referencial da nave): o estado
 * tem de ser finito, não pode andar mais que o possível desde o último aceito e a caixa do jogador não pode estar dentro de um bloco. Lógica
 * pura, testada sem o jogo. Como o referencial é o da nave, a velocidade da nave e o atraso da rede não entram nas contas.
 */
public final class DeckMoveCheck {
    /** Blocos por tick local que o tripulante pode andar no máximo (a queda livre do vanilla chega a 3,92). */
    public static final double MAX_SPEED = 4.0;
    /** Folga fixa somada ao limite de distância (blocos). */
    public static final double SLACK = 0.5;
    /** Quanto a caixa pode entrar num bloco (blocos) antes de a posição ser recusada. */
    public static final double MAX_PENETRATION = 0.05;
    /** O tempo (ticks) acima do qual o limite de distância não cresce mais: depois de uma pausa longa o jogador não "ganha" distância sem fim. */
    public static final int MAX_ELAPSED = 40;

    private DeckMoveCheck() {
    }

    public enum Verdict {
        OK, NOT_FINITE, TOO_FAST, IN_SOLID
    }

    /**
     * @param lastGood última posição aceita (pés)
     * @param incoming estado recebido
     * @param elapsedTicks ticks do servidor desde o último aceito (pelo menos 1)
     * @param width largura da caixa do jogador
     * @param height altura da caixa do jogador
     */
    public static Verdict check(Vector3dc lastGood, DeckState incoming, int elapsedTicks, double width, double height, DeckCollision.Colliders colliders) {
        if (!incoming.isFinite()) {
            return Verdict.NOT_FINITE;
        }
        int ticks = Math.max(1, Math.min(MAX_ELAPSED, elapsedTicks));
        double limit = MAX_SPEED * ticks + SLACK;
        if (incoming.pos.distanceSquared(lastGood) > limit * limit) {
            return Verdict.TOO_FAST;
        }
        double half = width / 2.0;
        AABB box = new AABB(incoming.pos.x - half, incoming.pos.y, incoming.pos.z - half, incoming.pos.x + half, incoming.pos.y + height,
                incoming.pos.z + half);
        if (DeckCollision.penetration(box, colliders) > MAX_PENETRATION) {
            return Verdict.IN_SOLID;
        }
        return Verdict.OK;
    }
}
