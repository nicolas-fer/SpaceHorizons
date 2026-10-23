package com.spacehorizons.client.space;

import com.spacehorizons.client.travel.AutopilotView;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.SpaceParking;
import com.spacehorizons.space.InertialFrame;
import com.spacehorizons.space.SpaceDimension;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * De onde o céu do espaço é visto quando a câmera está num foguete: no espaço o foguete fica ESTACIONADO num ponto fixo da dimensão
 * ({@code SpaceParking}) e a posição real do voo é um dado sincronizado dele ({@link RocketEntity#virtualSpacePosition}). O céu (a Terra, a
 * Lua, o sol, as estrelas) é desenhado por um observador nessa posição real ({@link SkyObserver#forShip}) com a atitude identidade: o
 * foguete gira, o céu não gira com ele, e a câmera do jogador continua livre. O piloto automático do jogador (comando fly)
 * faz o mesmo: o jogador fica estacionado e a posição real chega por {@link AutopilotView}. Quem não está num foguete nem no piloto automático
 * (comando goto, voo criativo) não tem posição virtual e vê o céu da posição real da câmera, como sempre.
 */
final class ShipView {
    private ShipView() {
    }

    /**
     * A última posição real conhecida do foguete, em I (inercial: não gira com a Terra). Na chegada à dimensão de espaço a entidade é recriada e leva
     * alguns quadros (até mais de um segundo com o servidor ocupado) para receber a posição virtual: sem isto o céu era desenhado a partir das
     * coordenadas do PONTO DE ESTACIONAMENTO (milhões de blocos fora do sistema) e os planetas sumiam e voltavam em rajadas.
     */
    private static double @Nullable [] heldInertial;
    /** Quando ({@code System.nanoTime}) a última posição foi vista; a que passa de {@link #HOLD_NANOS} não vale fora do ponto de estacionamento. */
    private static long heldAt;
    private static final long HOLD_NANOS = 3_000_000_000L;

    /**
     * A posição real do foguete da câmera no espaço do SolarSystem (em E) no relógio {@code clock}, ou null se a câmera não está num foguete
     * estacionado. O dado sincronizado vem em I (ver {@link RocketEntity#setVirtualSpacePosition}) e é levado a E com o relógio do quadro, o mesmo
     * que posiciona os corpos: nada do que o céu mostra depende de a que tick pertence o último pacote. Enquanto a câmera está no ponto de
     * estacionamento e a posição virtual do foguete ainda não chegou, vale a última conhecida, nunca as coordenadas do ponto.
     */
    static double @Nullable [] positionInSpace(double clock) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity camera = minecraft.getCameraEntity();
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (camera != null && camera == minecraft.player && !(camera.getVehicle() instanceof RocketEntity)) {
            // piloto automático do jogador (comando fly), estacionado no espaço: o servidor manda a posição real
            Vec3 flight = AutopilotView.position(partial);
            if (flight != null) {
                double[] out = new double[3];
                SpaceDimension.toSpace(flight.x, flight.y, flight.z, out);
                return out;
            }
        }
        double[] inertial = null;
        if (camera != null && camera.getVehicle() instanceof RocketEntity rocket && rocket.isVirtual()) {
            Vec3 at = rocket.virtualSpacePosition(partial);
            if (at != null) {
                inertial = new double[] {at.x, at.y, at.z};
                heldInertial = inertial;
                heldAt = System.nanoTime();
            }
        }
        if (inertial == null && heldInertial != null && camera != null
                && (camera.getZ() >= SpaceParking.BASE - SpaceParking.SPACING || System.nanoTime() - heldAt < HOLD_NANOS)) {
            inertial = heldInertial;
        }
        if (inertial == null) {
            return null;
        }
        double[] out = new double[3];
        InertialFrame.toEarthFixed(clock, inertial[0], inertial[1], inertial[2], out);
        return out;
    }

    /** Sai do mundo: esquece a última posição conhecida. */
    static void reset() {
        heldInertial = null;
    }
}
