package com.spacehorizons.space.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceFrame;
import com.spacehorizons.space.flight.AttitudeMath.SasMode;

class AttitudeMathTest {
    /** Relógio em que E e I coincidem (meia-noite do dia 0: φ = 0). */
    private static final double IDENTITY_CLOCK = 18_000.0;

    private static final double[] CLOCKS = {0, 7_000, 40_000.5, 123_456};

    private static Quaterniond randomQuaternion(Random rng) {
        return new Quaterniond(rng.nextGaussian(), rng.nextGaussian(), rng.nextGaussian(), rng.nextGaussian()).normalize();
    }

    private static V3 toV3(double[] a) {
        return V3.of(a);
    }

    private static double norm(double x, double y, double z, double w) {
        return Math.sqrt(x * x + y * y + z * z + w * w);
    }

    // ------------------------------------------------------------------ bases

    @Test
    void localMatrixIsAProperRotation() {
        for (SolarSystem.Body body : SolarSystem.BODIES) {
            for (double clock : CLOCKS) {
                Matrix3d m = AttitudeMath.localToSpaceMatrix(body, clock, 1_234, -567);
                assertEquals(1.0, m.determinant(), 1e-12);
                // ortonormal
                Matrix3d product = new Matrix3d(m).transpose().mul(m);
                assertEquals(1.0, product.m00(), 1e-12);
                assertEquals(1.0, product.m11(), 1e-12);
                assertEquals(1.0, product.m22(), 1e-12);
                assertEquals(0.0, product.m01(), 1e-12);
            }
        }
        assertEquals(1.0, AttitudeMath.spaceDimensionMatrix().determinant(), 1e-15);
    }

    // ------------------------------------------------------------------ inercial <-> local

    @Test
    void inertialToLocalRoundTripsOnEarthAndMoon() {
        Random rng = new Random(5);
        for (SolarSystem.Body body : SolarSystem.BODIES) {
            for (double clock : CLOCKS) {
                double x = rng.nextDouble() * 20_000 - 10_000;
                double z = rng.nextDouble() * 10_000 - 5_000;
                for (int i = 0; i < 20; i++) {
                    Quaterniond q = randomQuaternion(rng);
                    Quaterniond local = AttitudeMath.inertialToLocal(body, clock, x, z, q);
                    Quaterniond back = AttitudeMath.localToInertial(body, clock, x, z, local);
                    // q e −q são a mesma rotação
                    assertEquals(1.0, Math.abs(q.dot(back)), 1e-12);
                }
            }
        }
    }

    @Test
    void floatVersionsRoundTripWithFloatPrecision() {
        Random rng = new Random(6);
        for (int i = 0; i < 50; i++) {
            Quaternionf q = new Quaternionf((float) rng.nextGaussian(), (float) rng.nextGaussian(), (float) rng.nextGaussian(),
                    (float) rng.nextGaussian()).normalize();
            Quaternionf local = AttitudeMath.inertialToLocal(SolarSystem.MOON, 9_000, 800, 300, q);
            Quaternionf back = AttitudeMath.localToInertial(SolarSystem.MOON, 9_000, 800, 300, local);
            assertEquals(1.0, Math.abs(q.dot(back)), 1e-5);
            Quaternionf space = AttitudeMath.inertialToSpaceLocal(q, 7_777.0);
            assertEquals(1.0, Math.abs(q.dot(AttitudeMath.spaceLocalToInertial(space, 7_777.0))), 1e-5);
            assertEquals(1.0, norm(space.x(), space.y(), space.z(), space.w()), 1e-5);
        }
    }

    @Test
    void uprightRocketHasTheLocalUpAsItsNose() {
        // quaternion local identidade = nariz para cima; no espaço o nariz é a vertical local do ponto
        for (SolarSystem.Body body : SolarSystem.BODIES) {
            for (double clock : CLOCKS) {
                double x = 2_345;
                double z = -1_234;
                Quaterniond inertial = AttitudeMath.localToInertial(body, clock, x, z, new Quaterniond());
                V3 nose = AttitudeMath.noseDirection(inertial);
                double[] up = new double[3];
                SpaceFrame.localToSpace(body, body.inertialPoseAt(clock), x, z, 0, 1, 0, up);
                assertEquals(0.0, nose.distanceTo(toV3(up)), 1e-12);
                // e a inclinação em relação à vertical é 0
                assertEquals(0.0, AttitudeMath.tiltDegrees(inertial, toV3(up)), 1e-6);
            }
        }
    }

    @Test
    void localNoseIsTheSpaceNoseSeenFromTheLocalAxes() {
        Random rng = new Random(7);
        for (SolarSystem.Body body : SolarSystem.BODIES) {
            double clock = 55_555;
            double x = 3_000;
            double z = 700;
            for (int i = 0; i < 20; i++) {
                Quaterniond inertial = randomQuaternion(rng);
                Quaterniond local = AttitudeMath.inertialToLocal(body, clock, x, z, inertial);
                V3 inertialNose = AttitudeMath.noseDirection(inertial);
                double[] expected = new double[3];
                SpaceFrame.spaceToLocal(body, body.inertialPoseAt(clock), x, z, inertialNose.x(), inertialNose.y(), inertialNose.z(), expected);
                assertEquals(0.0, AttitudeMath.noseDirection(local).distanceTo(toV3(expected)), 1e-12);
            }
        }
    }

    @Test
    void rotatingTheBodyTurnsTheLocalAttitudeWithTheSameAngle() {
        // o ângulo entre dois quaternions não depende do referencial em que são expressos
        Random rng = new Random(8);
        Quaterniond a = randomQuaternion(rng);
        Quaterniond b = randomQuaternion(rng);
        double inertial = rotationAngle(a.difference(b, new Quaterniond()));
        Quaterniond la = AttitudeMath.inertialToLocal(SolarSystem.EARTH, 100, 0, 0, a);
        Quaterniond lb = AttitudeMath.inertialToLocal(SolarSystem.EARTH, 100, 0, 0, b);
        assertEquals(inertial, rotationAngle(la.difference(lb, new Quaterniond())), 1e-9);
    }

    /** Ângulo (0..π) da rotação, independente do sinal do quaternion. */
    private static double rotationAngle(Quaterniond q) {
        return 2.0 * Math.acos(Math.min(1.0, Math.abs(q.w())));
    }

    // ------------------------------------------------------------------ dimensão de espaço

    @Test
    void spaceDimensionUprightRocketPointsAlongSpacePlusX() {
        // na dimensão de espaço o "cima" local é o +X do espaço (a vertical do spawn da Terra)
        Quaterniond inertial = AttitudeMath.spaceLocalToInertial(new Quaterniond(), IDENTITY_CLOCK);
        V3 nose = AttitudeMath.noseDirection(inertial);
        assertEquals(0.0, nose.distanceTo(new V3(1, 0, 0)), 1e-12);
        // a conversão concorda com o mapeamento de vetores do FrameBridge
        Random rng = new Random(9);
        for (int i = 0; i < 20; i++) {
            Quaterniond local = randomQuaternion(rng);
            V3 localNose = AttitudeMath.noseDirection(local);
            V3 spaceNose = AttitudeMath.noseDirection(AttitudeMath.spaceLocalToInertial(local, IDENTITY_CLOCK));
            assertEquals(0.0, FrameBridge.spaceDimVectorToSpace(localNose.x(), localNose.y(), localNose.z()).distanceTo(spaceNose),
                    1e-12);
            Quaterniond back = AttitudeMath.inertialToSpaceLocal(AttitudeMath.spaceLocalToInertial(local, 4_321.0), 4_321.0);
            assertEquals(1.0, Math.abs(local.dot(back)), 1e-12);
        }
    }

    @Test
    void earthSpawnLocalAxesAreTheSpaceDimensionAxes() {
        // a base local da Terra em (0, 0) é a mesma da dimensão de espaço (esse é o ponto do desenho dos eixos)
        // (em I as duas giram juntas com a Terra: valem em qualquer relógio)
        for (double clock : new double[] {IDENTITY_CLOCK, 0.0, 1_234.5, 9_999.0, 55_555.5}) {
            Matrix3d earth = AttitudeMath.localToSpaceMatrix(SolarSystem.EARTH, clock, 0, 0);
            Matrix3d space = AttitudeMath.spaceDimensionMatrix(clock);
            for (int column = 0; column < 3; column++) {
                for (int row = 0; row < 3; row++) {
                    assertEquals(space.get(column, row), earth.get(column, row), 1e-12);
                }
            }
        }
        // sem relógio é a matriz da dimensão para E, que coincide com I à meia-noite do dia 0
        Matrix3d inE = AttitudeMath.spaceDimensionMatrix();
        Matrix3d inI = AttitudeMath.spaceDimensionMatrix(IDENTITY_CLOCK);
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 3; row++) {
                assertEquals(inE.get(column, row), inI.get(column, row), 1e-12);
            }
        }
    }

    // ------------------------------------------------------------------ SAS

    @Test
    void sasTargetsArePrograde() {
        V3 v = new V3(0, 0, 25);
        assertNull(AttitudeMath.sasTarget(SasMode.OFF, v));
        assertNull(AttitudeMath.sasTarget(SasMode.STABILIZE, v));
        assertEquals(0.0, AttitudeMath.sasTarget(SasMode.PROGRADE, v).distanceTo(new V3(0, 0, 1)), 1e-15);
        assertEquals(0.0, AttitudeMath.sasTarget(SasMode.RETROGRADE, v).distanceTo(new V3(0, 0, -1)), 1e-15);
        // sem velocidade não há alvo
        assertNull(AttitudeMath.sasTarget(SasMode.PROGRADE, V3.ZERO));
    }

    @Test
    void sasModeCycleFollowsTheKey() {
        assertSame(SasMode.STABILIZE, SasMode.OFF.next());
        assertSame(SasMode.PROGRADE, SasMode.STABILIZE.next());
        assertSame(SasMode.RETROGRADE, SasMode.PROGRADE.next());
        assertSame(SasMode.HORIZONTAL, SasMode.RETROGRADE.next());
        assertSame(SasMode.OFF, SasMode.HORIZONTAL.next());
        assertSame(SasMode.PROGRADE, SasMode.byId(SasMode.PROGRADE.ordinal()));
        assertSame(SasMode.OFF, SasMode.byId(99));
        assertSame(SasMode.OFF, SasMode.byId(-1));
    }

    @Test
    void sasNumbersOfTheOldModesDoNotChange() {
        // mundos e pacotes antigos gravam o número: os quatro primeiros não podem mudar de lugar
        assertEquals(0, SasMode.OFF.ordinal());
        assertEquals(1, SasMode.STABILIZE.ordinal());
        assertEquals(2, SasMode.PROGRADE.ordinal());
        assertEquals(3, SasMode.RETROGRADE.ordinal());
        assertSame(SasMode.HORIZONTAL, SasMode.byId(4));
    }

    @Test
    void horizontalTargetInACircularOrbitIsThePrograde() {
        // órbita circular: a velocidade já é perpendicular ao radial, então horizontal = prógrado
        V3 r = new V3(1.0e6, 0, 0);
        V3 v = new V3(0, 0, 25);
        V3 horizontal = AttitudeMath.sasTarget(SasMode.HORIZONTAL, v, r);
        assertNotNull(horizontal);
        assertEquals(0.0, horizontal.distanceTo(AttitudeMath.sasTarget(SasMode.PROGRADE, v)), 1e-12);
    }

    @Test
    void horizontalTargetInAnEccentricOrbitIsPerpendicularToTheRadialAndNotThePrograde() {
        // fora do apo e do peri: o prógrado tem componente radial, o horizontal não
        V3 r = new V3(1.0e6, 0, 0);
        V3 v = new V3(12, 0, 20);
        V3 horizontal = AttitudeMath.sasTarget(SasMode.HORIZONTAL, v, r);
        assertNotNull(horizontal);
        assertEquals(0.0, horizontal.dot(new V3(1, 0, 0)), 1e-12);
        assertEquals(0.0, horizontal.distanceTo(new V3(0, 0, 1)), 1e-12);
        assertTrue(horizontal.distanceTo(AttitudeMath.sasTarget(SasMode.PROGRADE, v)) > 0.1);
        // o "up" não precisa ser unitário
        assertEquals(0.0, horizontal.distanceTo(AttitudeMath.sasTarget(SasMode.HORIZONTAL, v, new V3(5.0e5, 0, 0))), 1e-12);
    }

    @Test
    void horizontalWithoutAVerticalOrWithAVerticalVelocityHasNoTarget() {
        V3 r = new V3(1.0e6, 0, 0);
        // subida vertical: a velocidade é toda radial, não sobra horizontal
        assertNull(AttitudeMath.sasTarget(SasMode.HORIZONTAL, new V3(50, 0, 0), r));
        // sem a vertical não há alvo, mesmo com velocidade
        assertNull(AttitudeMath.sasTarget(SasMode.HORIZONTAL, new V3(0, 0, 25)));
        assertNull(AttitudeMath.sasTarget(SasMode.HORIZONTAL, new V3(0, 0, 25), null));
        assertNull(AttitudeMath.sasTarget(SasMode.HORIZONTAL, new V3(0, 0, 25), V3.ZERO));
    }

    @Test
    void slewTurnsTheNoseTowardTheTargetWithoutOvershooting() {
        Quaterniond q = new Quaterniond(); // nariz em +Y
        V3 target = new V3(1, 1, 0).unit();
        double angle = new V3(0, 1, 0).angleTo(target);
        double step = Math.toRadians(5);
        int steps = 0;
        double previous = angle;
        while (AttitudeMath.noseDirection(q).angleTo(target) > 1e-9 && steps < 100) {
            q = AttitudeMath.slewNoseToward(q, target, step);
            double now = AttitudeMath.noseDirection(q).angleTo(target);
            assertTrue(now < previous, "o ângulo deve diminuir");
            assertTrue(previous - now <= step + 1e-9, "no máximo " + step + " por passo");
            previous = now;
            steps++;
        }
        // 45° em passos de 5°: nove passos
        assertEquals(9, steps);
        assertEquals(1.0, norm(q.x(), q.y(), q.z(), q.w()), 1e-12);
    }

    @Test
    void slewHandlesAnAntiparallelTargetAndDegenerateInputs() {
        Quaterniond q = new Quaterniond();
        Quaterniond turned = AttitudeMath.slewNoseToward(q, new V3(0, -1, 0), Math.toRadians(30));
        assertEquals(Math.toRadians(30), AttitudeMath.noseDirection(turned).angleTo(new V3(0, 1, 0)), 1e-9);
        // alvo nulo e passo nulo não mexem
        assertEquals(1.0, Math.abs(q.dot(AttitudeMath.slewNoseToward(q, V3.ZERO, 1.0))), 1e-12);
        assertEquals(1.0, Math.abs(q.dot(AttitudeMath.slewNoseToward(q, new V3(1, 0, 0), 0.0))), 1e-12);
        // já apontando: fica igual
        assertEquals(1.0, Math.abs(q.dot(AttitudeMath.slewNoseToward(q, new V3(0, 5, 0), 1.0))), 1e-12);
    }

    @Test
    void slewKeepsTheRollAroundTheNose() {
        // gira em torno do eixo perpendicular a nariz e alvo: o eixo X do corpo continua no plano esperado
        Quaterniond q = AttitudeMath.rotateBody(new Quaterniond(), 0, 0, Math.toRadians(40)); // rolagem de 40° em torno do nariz
        Quaterniond turned = AttitudeMath.slewNoseToward(q, new V3(0, 0, 1), Math.toRadians(90));
        assertEquals(0.0, AttitudeMath.noseDirection(turned).distanceTo(new V3(0, 0, 1)), 1e-12);
        // a rolagem em torno do nariz continua a mesma: desfazendo-a, o resultado é o do giro sem rolagem
        Quaterniond reference = AttitudeMath.slewNoseToward(new Quaterniond(), new V3(0, 0, 1), Math.toRadians(90));
        Quaterniond withoutRoll = turned.rotateY(-Math.toRadians(40));
        assertEquals(1.0, Math.abs(reference.dot(withoutRoll)), 1e-12);
    }

    // ------------------------------------------------------------------ comandos de arfagem, guinada e rolagem

    @Test
    void bodyRatesRotateAroundTheBodyAxes() {
        Quaterniond identity = new Quaterniond();
        double angle = Math.toRadians(10);
        // arfagem em torno de +X: o nariz (+Y) vai para +Z
        V3 pitched = AttitudeMath.noseDirection(AttitudeMath.rotateBody(identity, angle, 0, 0));
        assertEquals(0.0, pitched.distanceTo(new V3(0, Math.cos(angle), Math.sin(angle))), 1e-12);
        // guinada em torno de +Z: o nariz vai para −X
        V3 yawed = AttitudeMath.noseDirection(AttitudeMath.rotateBody(identity, 0, angle, 0));
        assertEquals(0.0, yawed.distanceTo(new V3(-Math.sin(angle), Math.cos(angle), 0)), 1e-12);
        // rolagem em torno do nariz: o nariz não se mexe
        V3 rolled = AttitudeMath.noseDirection(AttitudeMath.rotateBody(identity, 0, 0, angle));
        assertEquals(0.0, rolled.distanceTo(new V3(0, 1, 0)), 1e-12);
    }

    @Test
    void bodyRatesComposeInTheBodyFrameAndKeepUnitLength() {
        Quaterniond q = new Quaterniond();
        for (int i = 0; i < 1_000; i++) {
            q = AttitudeMath.rotateBody(q, 0.01, -0.02, 0.005);
        }
        assertEquals(1.0, norm(q.x(), q.y(), q.z(), q.w()), 1e-12);
        // girar em torno de um eixo do corpo e depois no oposto desfaz
        Quaterniond there = AttitudeMath.rotateBody(new Quaterniond(), 0.3, 0, 0);
        Quaterniond back = AttitudeMath.rotateBody(there, -0.3, 0, 0);
        assertEquals(1.0, Math.abs(back.dot(new Quaterniond())), 1e-12);
    }

    @Test
    void tiltIsTheAngleBetweenTheNoseAndUp() {
        V3 up = new V3(1, 0, 0);
        Quaterniond tilted = new Quaterniond().rotateZ(Math.toRadians(20)); // nariz de +Y para −X
        assertNotNull(AttitudeMath.noseDirection(tilted));
        // para cima = +Y: 20° de inclinação
        assertEquals(20.0, AttitudeMath.tiltDegrees(tilted, new V3(0, 1, 0)), 1e-9);
        assertEquals(90.0, AttitudeMath.tiltDegrees(new Quaterniond(), up), 1e-9);
        assertEquals(180.0, AttitudeMath.tiltDegrees(new Quaterniond().rotateX(Math.PI), new V3(0, 1, 0)), 1e-9);
    }
}
