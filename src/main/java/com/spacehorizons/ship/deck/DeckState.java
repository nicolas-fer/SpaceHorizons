package com.spacehorizons.ship.deck;

import org.joml.Vector3d;

/**
 * O estado do tripulante no referencial local da nave (coordenadas do retrato): a posição dos pés, a velocidade em blocos por tick local, a
 * direção do olhar (yaw/pitch locais, graus do Minecraft) e se está no chão. É a verdade da simulação, no cliente (previsão) e no servidor
 * (aceito depois de conferido); o mundo só vê a posição derivada dele pela pose da nave ({@link DeckFrame}).
 */
public final class DeckState {
    public final Vector3d pos = new Vector3d();
    public final Vector3d vel = new Vector3d();
    public float yaw;
    public float pitch;
    public boolean onGround;

    public DeckState set(DeckState other) {
        this.pos.set(other.pos);
        this.vel.set(other.vel);
        this.yaw = other.yaw;
        this.pitch = other.pitch;
        this.onGround = other.onGround;
        return this;
    }

    public boolean isFinite() {
        return Double.isFinite(this.pos.x) && Double.isFinite(this.pos.y) && Double.isFinite(this.pos.z) && Double.isFinite(this.vel.x)
                && Double.isFinite(this.vel.y) && Double.isFinite(this.vel.z) && Float.isFinite(this.yaw) && Float.isFinite(this.pitch);
    }
}
