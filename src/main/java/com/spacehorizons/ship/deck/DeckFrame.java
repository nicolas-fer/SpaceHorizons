package com.spacehorizons.ship.deck;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3f;

import com.spacehorizons.ship.ShipFrames;

/**
 * A pose da nave num instante: o centro da caixa do retrato no mundo, a atitude (quaternion que leva o retrato ao mundo) e o pivô (o ponto do
 * retrato que fica no centro). É a transformação que leva o referencial local do tripulante ao mundo e de volta ({@link ShipFrames}), e que o
 * desenho e a rede usam para colocar o tripulante: o movimento da nave entra aqui, como transformação, nunca como deslocamento a corrigir no
 * jogador. Valor imutável; a atitude é copiada.
 */
public record DeckFrame(double cx, double cy, double cz, Quaternionfc attitude, Vector3f pivot) {
    public DeckFrame {
        attitude = new Quaternionf(attitude);
        pivot = new Vector3f(pivot);
    }

    /** Coordenadas locais do retrato → mundo. */
    public Vector3d toWorld(double x, double y, double z) {
        return ShipFrames.localToWorld(this.attitude, this.cx, this.cy, this.cz, this.pivot, x, y, z);
    }

    /** Mundo → coordenadas locais do retrato. */
    public Vector3d toLocal(double x, double y, double z) {
        return ShipFrames.worldToLocal(this.attitude, this.cx, this.cy, this.cz, this.pivot, x, y, z);
    }

    /** Direção de visão (yaw/pitch locais, graus do Minecraft) → a mesma direção no mundo; devolve {yaw, pitch}. */
    public float[] viewToWorld(float yaw, float pitch) {
        return ShipFrames.viewToWorld(this.attitude, yaw, pitch);
    }

    /** Direção de visão do mundo → local; devolve {yaw, pitch}. */
    public float[] viewToLocal(float yaw, float pitch) {
        return ShipFrames.viewToLocal(this.attitude, yaw, pitch);
    }

    /** A pose entre duas, no instante {@code t} (0 a 1): o centro por interpolação linear e a atitude por slerp, como o desenho da nave interpola. */
    public static DeckFrame lerp(DeckFrame a, DeckFrame b, double t) {
        Quaternionf q = new Quaternionf();
        a.attitude.slerp(b.attitude, (float) t, q);
        return new DeckFrame(a.cx + (b.cx - a.cx) * t, a.cy + (b.cy - a.cy) * t, a.cz + (b.cz - a.cz) * t, q, a.pivot);
    }
}
