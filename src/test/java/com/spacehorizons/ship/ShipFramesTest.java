package com.spacehorizons.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** O referencial local da nave na conversão: posição e vista do passageiro entre o mundo (com atitude) e o estaleiro (sem rotação). */
class ShipFramesTest {
    private static final double EPS = 1.0E-4;

    @Test
    void identidadeSoSomaOPivo() {
        Vector3d local = ShipFrames.worldToLocal(new Quaternionf(), 100.0, 200.0, 300.0, new Vector3f(4.5F, 6.0F, 4.5F), 101.0, 199.0, 302.0);
        assertEquals(5.5, local.x, EPS);
        assertEquals(5.0, local.y, EPS);
        assertEquals(6.5, local.z, EPS);
    }

    @Test
    void idaEVoltaComAtitudeQualquer() {
        Quaternionf q = new Quaternionf().rotateY(0.7F).rotateX(-0.4F).rotateZ(1.1F);
        Vector3f pivot = new Vector3f(13.0F, 14.0F, 13.0F);
        Vector3d local = ShipFrames.worldToLocal(q, 5000.0, 20.0, -300.0, pivot, 5003.0, 25.0, -297.5);
        Vector3d back = ShipFrames.localToWorld(q, 5000.0, 20.0, -300.0, pivot, local.x, local.y, local.z);
        assertEquals(5003.0, back.x, EPS);
        assertEquals(25.0, back.y, EPS);
        assertEquals(-297.5, back.z, EPS);
    }

    @Test
    void foguetePeloLadoDeitadoOCockpitFicaEmCimaNaNave() {
        // atitude: o foguete girou 90 graus em torno de Z (deitado); o ponto 5 acima do centro no referencial da nave está 5 ao lado no mundo
        Quaternionf q = new Quaternionf().rotateZ((float) Math.toRadians(90.0));
        Vector3d world = ShipFrames.localToWorld(q, 0.0, 100.0, 0.0, new Vector3f(10.0F, 10.0F, 10.0F), 10.0, 15.0, 10.0);
        Vector3d local = ShipFrames.worldToLocal(q, 0.0, 100.0, 0.0, new Vector3f(10.0F, 10.0F, 10.0F), world.x, world.y, world.z);
        assertEquals(10.0, local.x, EPS);
        assertEquals(15.0, local.y, EPS);
        assertEquals(10.0, local.z, EPS);
        assertEquals(-5.0, world.x, 1.0E-3);
        assertEquals(100.0, world.y, 1.0E-3);
    }

    @Test
    void vistaIdentidadeNaoMuda() {
        float[] v = ShipFrames.viewToLocal(new Quaternionf(), 37.0F, -12.0F);
        assertEquals(37.0, v[0], 1.0E-3);
        assertEquals(-12.0, v[1], 1.0E-3);
    }

    @Test
    void vistaIdaEVolta() {
        Quaternionf q = new Quaternionf().rotateY(1.2F).rotateX(0.3F);
        float[] local = ShipFrames.viewToLocal(q, 80.0F, 20.0F);
        float[] back = ShipFrames.viewToWorld(q, local[0], local[1]);
        assertEquals(80.0, back[0], 1.0E-2);
        assertEquals(20.0, back[1], 1.0E-2);
    }

    @Test
    void giroEmYawGiraAVistaPeloMesmoAngulo() {
        // atitude de 90 graus de yaw (anti-horário visto de cima no JOML): olhar para o mundo a yaw 0 equivale a yaw +90 ou -90 na nave
        Quaternionf q = new Quaternionf().rotateY((float) Math.toRadians(90.0));
        float[] local = ShipFrames.viewToLocal(q, 0.0F, 0.0F);
        assertEquals(90.0, Math.abs(local[0]), 1.0E-2);
        assertEquals(0.0, local[1], 1.0E-3);
    }
}
