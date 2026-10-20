package com.spacehorizons.client.space;

import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.space.SpaceDimension;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * De onde o céu do espaço é visto quando a câmera está num foguete: no espaço o foguete fica ESTACIONADO num ponto fixo da dimensão
 * ({@code SpaceParking}) e a posição real do voo é um dado sincronizado dele ({@link RocketEntity#virtualSpacePosition}). O céu (a Terra, a
 * Lua, o sol, as estrelas) é desenhado por um observador nessa posição real ({@link SkyObserver#forShip}) com a atitude identidade: o
 * foguete gira, o céu não gira com ele, e a câmera do jogador continua livre. Quem não está num foguete (comando goto, voo criativo)
 * não tem posição virtual e vê o céu da posição real da câmera, como sempre.
 */
final class ShipView {
    private ShipView() {
    }

    /** A posição real do foguete da câmera no espaço do SolarSystem (em E), interpolada para o quadro, ou null se a câmera não está num foguete estacionado. */
    static double @Nullable [] positionInSpace() {
        Minecraft minecraft = Minecraft.getInstance();
        Entity camera = minecraft.getCameraEntity();
        if (camera == null || !(camera.getVehicle() instanceof RocketEntity rocket) || !rocket.isVirtual()) {
            return null;
        }
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 at = rocket.virtualSpacePosition(partial);
        if (at == null) {
            return null;
        }
        double[] out = new double[3];
        SpaceDimension.toSpace(at.x, at.y, at.z, out);
        return out;
    }
}
