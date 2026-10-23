package com.spacehorizons.space.vessel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import com.spacehorizons.client.space.SkyAttitude;
import com.spacehorizons.client.space.SkyObserver;
import com.spacehorizons.space.SpaceDimension;

/** A transformação relativa de um veículo visto por um observador: posição, atitude composta e extrapolação (fase 2d das naves). */
class VesselPoseTest {
    private static final double EPS = 1.0e-9;

    private static double[] q(Quaterniond q) {
        return new double[] {q.x, q.y, q.z, q.w};
    }

    private static double[] identityRotation() {
        return new double[] {1, 0, 0, 0, 1, 0, 0, 0, 1};
    }

    @Test
    void observadorSemAtitudeVeOOutroNaDirecaoDoEspacoPeloMapeamentoDaDimensao() {
        // a dimensão: espaço = LOCAL_TO_SPACE · local; o outro a 300 blocos no +Z do espaço (= +X local, "leste") e 60 no +X do espaço (= +Y local, cima)
        double[] observer = {1000.0, 2000.0, 3000.0};
        double[] vessel = {1060.0, 2000.0, 3300.0};
        double[] center = new double[3];
        double[] rotation = new double[9];
        VesselPose.relative(observer, SpaceDimension.LOCAL_TO_SPACE, vessel, VesselPose.modelToSpace(VesselPose.identityAttitude()), center, rotation);
        // Mᵀ · (60, 0, 300): LOCAL_TO_SPACE = {0,1,0, 0,0,1, 1,0,0} -> local = (z, x, y) do espaço = (300, 60, 0)
        assertArrayEquals(new double[] {300.0, 60.0, 0.0}, center, EPS);
        assertArrayEquals(identityRotation(), rotation, EPS, "veículo e observador sem atitude: sem rotação relativa");
    }

    @Test
    void doisVeiculosComAMesmaAtitudeSeVemSemRotacaoRelativa() {
        // o observador de verdade (SkyObserver.forShip com a atitude R do céu, a mesma de SkyAttitude) e o modelo do outro (LOCAL_TO_SPACE · R(ql))
        Quaterniond local = new Quaterniond().rotateY(Math.toRadians(40.0)).rotateX(Math.toRadians(-25.0)).rotateZ(Math.toRadians(70.0));
        SkyObserver observer = SkyObserver.forShip(0.0, new double[] {5.0, 6.0, 7.0}, SkyAttitude.localToSpaceFrame(local));
        double[] center = new double[3];
        double[] rotation = new double[9];
        VesselPose.relative(observer.pos(), observer.localToSpace(), new double[] {5.0, 6.0, 307.0}, VesselPose.modelToSpace(q(local)), center, rotation);
        assertArrayEquals(identityRotation(), rotation, 1.0e-12, "a atitude composta de quem olha e a do outro coincidem: sem rotação relativa");
        // e o vetor ao outro (0, 0, 300 no espaço) nos eixos do observador tem o mesmo módulo
        assertEquals(300.0, Math.sqrt(center[0] * center[0] + center[1] * center[1] + center[2] * center[2]), 1.0e-9);
    }

    @Test
    void aNaveDeQuemOlhaGiradaMoveOOutroNoCeuPelaAtitudeInversa() {
        // observador girado de 90 graus em torno do CIMA local: quem estava à frente (norte, -z local) passa a estar a +90 ou -90 graus
        Quaterniond turn = new Quaterniond().rotateY(Math.toRadians(90.0));
        double[] zero = {0.0, 0.0, 0.0};
        double[] other = {0.0, 0.0, 300.0};      // +Z do espaço = +X local (leste) com a atitude identidade
        double[] c0 = new double[3];
        double[] c1 = new double[3];
        double[] r = new double[9];
        VesselPose.relative(zero, SpaceDimension.LOCAL_TO_SPACE, other, VesselPose.modelToSpace(VesselPose.identityAttitude()), c0, r);
        SkyObserver turned = SkyObserver.forShip(0.0, zero, SkyAttitude.localToSpaceFrame(turn));
        VesselPose.relative(turned.pos(), turned.localToSpace(), other, VesselPose.modelToSpace(VesselPose.identityAttitude()), c1, r);
        assertEquals(300.0, c0[0], EPS, "sem atitude o outro está a leste");
        // com a nave girada de +90 em torno de Y, o vetor ao outro nos eixos dela é o girado de -90 em torno de Y
        Quaterniond inverse = new Quaterniond(turn).conjugate();
        org.joml.Vector3d expected = inverse.transform(new org.joml.Vector3d(c0[0], c0[1], c0[2]));
        assertArrayEquals(new double[] {expected.x, expected.y, expected.z}, c1, 1.0e-9);
        assertEquals(Math.toRadians(90.0), Math.acos(Math.min(1.0, (c0[0] * c1[0] + c0[1] * c1[1] + c0[2] * c1[2]) / (300.0 * 300.0))), 1.0e-9,
                "o outro girou 90 graus no céu");
    }

    @Test
    void outroGiradoApareceGiradoNosEixosDoObservador() {
        // o outro com atitude local de 90 graus em torno do cima local e o observador sem atitude: a rotação relativa é essa rotação nos eixos locais
        Quaterniond local = new Quaterniond().rotateY(Math.toRadians(90.0));
        double[] center = new double[3];
        double[] rotation = new double[9];
        VesselPose.relative(new double[3], SpaceDimension.LOCAL_TO_SPACE, new double[] {0, 0, 10}, VesselPose.modelToSpace(q(local)), center, rotation);
        double[] expected = new double[9];
        VesselPose.rotationMatrix(local.x, local.y, local.z, local.w, expected);
        assertArrayEquals(expected, rotation, 1.0e-12);
    }

    @Test
    void extrapolacaoDePosicaoEAtitudeEExataComTaxaConstante() {
        double[] p = VesselPose.extrapolatePosition(new double[] {10, 20, 30}, new double[] {1, -2, 0.5}, 6.0);
        assertArrayEquals(new double[] {16, 8, 33}, p, EPS);
        Quaterniond a = new Quaterniond().rotateY(0.3).rotateX(-0.2);
        Quaterniond b = new Quaterniond(new Quaterniond().rotateAxis(0.05, 0.0, 1.0, 0.0)).mul(a).normalize();
        double[] omega = VesselPose.rotationBetween(q(a), q(b));
        assertEquals(0.05, Math.sqrt(omega[0] * omega[0] + omega[1] * omega[1] + omega[2] * omega[2]), 1.0e-12);
        double[] after = VesselPose.extrapolateAttitude(q(a), omega, 1.0);
        double dot = Math.abs(after[0] * b.x + after[1] * b.y + after[2] * b.z + after[3] * b.w);
        assertEquals(1.0, dot, 1.0e-12, "um passo de giro leva a atitude a b");
        double[] twice = VesselPose.extrapolateAttitude(q(a), omega, 2.0);
        Quaterniond expected = new Quaterniond(new Quaterniond().rotateAxis(0.10, 0.0, 1.0, 0.0)).mul(a).normalize();
        assertEquals(1.0, Math.abs(twice[0] * expected.x + twice[1] * expected.y + twice[2] * expected.z + twice[3] * expected.w), 1.0e-12);
    }

    @Test
    void raioAngularEAnguloSolidoDeUmaEsfera() {
        assertEquals(Math.asin(0.1), VesselPose.angularRadius(30.0, 300.0), 1.0e-12);
        assertEquals(Math.PI / 2.0, VesselPose.angularRadius(30.0, 10.0), 1.0e-12, "dentro da esfera: o céu inteiro");
        assertTrue(VesselPose.angularRadius(15.0, 300.0) < VesselPose.angularRadius(15.0, 150.0));
    }
}
