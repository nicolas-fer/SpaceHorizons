package com.spacehorizons.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

/** Atitude do foguete (yaw, inclinação) e a origem da caixa ao pousar. */
class RocketAttitudeTest {
    private static final double EPS = 1.0E-3;

    @Test
    void yawNoventaLevaONorteParaLesteIgualARotationClockwise90() {
        Vector3f norte = RocketAttitude.fromYaw(90.0).transform(new Vector3f(0.0F, 0.0F, -1.0F));
        assertEquals(1.0, norte.x(), EPS, "norte (0,0,-1) vira leste (+x)");
        assertEquals(0.0, norte.z(), EPS);
        Vector3f leste = RocketAttitude.fromYaw(90.0).transform(new Vector3f(1.0F, 0.0F, 0.0F));
        assertEquals(1.0, leste.z(), EPS, "leste vira sul (+z)");
    }

    @Test
    void yawIdaEVoltaEmVariosAngulos() {
        for (double yaw : new double[] {0.0, 30.0, 90.0, 135.0, 179.0, -45.0, -90.0, -170.0}) {
            assertEquals(yaw, RocketAttitude.yawDegrees(RocketAttitude.fromYaw(yaw)), EPS, "yaw " + yaw);
        }
    }

    @Test
    void emPeNaoTemInclinacaoEDeitadoTemNoventa() {
        // o acos de um float perto de 1 amplifica o erro de arredondamento: tolerância de 0,05 grau
        double tol = 0.05;
        assertEquals(0.0, RocketAttitude.tiltDegrees(new Quaternionf()), tol);
        assertEquals(0.0, RocketAttitude.tiltDegrees(RocketAttitude.fromYaw(123.0)), tol, "girar em yaw não inclina");
        assertEquals(90.0, RocketAttitude.tiltDegrees(new Quaternionf().rotationX((float) Math.toRadians(90.0))), tol);
        assertEquals(180.0, RocketAttitude.tiltDegrees(new Quaternionf().rotationX((float) Math.PI)), tol);
        assertEquals(10.0, RocketAttitude.tiltDegrees(new Quaternionf().rotationZ((float) Math.toRadians(10.0))), tol);
    }

    @Test
    void origemDaCaixaArredondaOCentroParaOBlocoMaisPerto() {
        // caixa 3 x 5 x 1 montada com o canto em (10, 70, -4): o centro fica em (11,5, 72,5, -3,5)
        net.minecraft.world.phys.Vec3 centro = new net.minecraft.world.phys.Vec3(11.5, 72.5, -3.5);
        assertEquals(new BlockPos(10, 70, -4), com.spacehorizons.rocket.RocketLanding.originFor(centro, 3, 5, 1));
        // com um pouco de deriva depois de um nudge, ainda volta ao mesmo bloco
        net.minecraft.world.phys.Vec3 derivado = new net.minecraft.world.phys.Vec3(11.62, 72.41, -3.58);
        assertEquals(new BlockPos(10, 70, -4), com.spacehorizons.rocket.RocketLanding.originFor(derivado, 3, 5, 1));
        // caixa de tamanho par: o centro cai na aresta do bloco
        assertEquals(new BlockPos(5, 0, 5), com.spacehorizons.rocket.RocketLanding.originFor(
                new net.minecraft.world.phys.Vec3(7.0, 2.0, 7.0), 4, 4, 4));
    }
}
