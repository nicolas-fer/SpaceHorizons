package com.spacehorizons.client.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceDimension;
import com.spacehorizons.space.SunModel;

/**
 * Céu virtual com atitude (spike S1 das naves): a composição {@code R · LOCAL_TO_SPACE}, a coincidência com {@code forSpace} na
 * atitude identidade e, em qualquer atitude, a coerência entre o sol desenhado (altura + giro em torno da vertical do vanilla),
 * a luz das esferas ({@code observer.sun}) e as estrelas (rotação completa): tudo gira junto.
 */
class SkyAttitudeTest {
    private static final double[] CLOCKS = {0, 6_000, 18_000, 70_000.5, 123_456.25};
    /** yaw, pitch, roll em graus: identidade, os casos do autoteste e combinações gerais. */
    private static final double[][] ATTITUDES = {{0, 0, 0}, {90, 0, 0}, {0, 45, 0}, {0, 0, 90}, {90, 45, 90}, {-130, 70, 33}, {10, -85, 170}};

    private static Quaterniond attitude(double[] a) {
        return SkyAttitude.localToSpaceFrame(SkyAttitude.fromEuler(a[0], a[1], a[2]));
    }

    private static double[] pos() {
        double[] s = new double[3];
        SpaceDimension.toSpace(0, 30_000, 0, s);
        return s;
    }

    @Test
    void identityAttitudeIsTheFixedSpaceObserver() {
        for (double clock : CLOCKS) {
            SkyObserver fixed = SkyObserver.forSpace(clock, 0, 30_000, 0);
            SkyObserver ship = SkyObserver.forShip(clock, pos(), new Quaterniond());
            for (int i = 0; i < 9; i++) {
                assertEquals(fixed.localToSpace()[i], ship.localToSpace()[i], 1e-12);
            }
            for (SolarSystem.Body body : SolarSystem.BODIES) {
                BodySky.Apparent a = fixed.apparent(body);
                BodySky.Apparent b = ship.apparent(body);
                assertEquals(a.x(), b.x(), 1e-9);
                assertEquals(a.y(), b.y(), 1e-9);
                assertEquals(a.z(), b.z(), 1e-9);
            }
            double[] stars = new double[9];
            ship.vanillaStarsToLocal(stars);
            for (int i = 0; i < 9; i++) {
                assertEquals(i % 4 == 0 ? 1.0 : 0.0, stars[i], 1e-12, "estrelas = identidade");
            }
        }
    }

    @Test
    void localToSpaceIsAttitudeTimesDimensionAndOrthonormal() {
        double[] l = SpaceDimension.LOCAL_TO_SPACE;
        for (double[] a : ATTITUDES) {
            Quaterniond q = attitude(a);
            double[] m = SkyObserver.shipLocalToSpace(q);
            // M = R·L: aplicada a cada eixo local, é a atitude aplicada ao eixo da dimensão
            for (int j = 0; j < 3; j++) {
                Vector3d axis = new Vector3d(l[j], l[3 + j], l[6 + j]);
                q.transform(axis);
                assertEquals(axis.x, m[j], 1e-12);
                assertEquals(axis.y, m[3 + j], 1e-12);
                assertEquals(axis.z, m[6 + j], 1e-12);
            }
            // ortonormal, det +1
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    double dot = m[i * 3] * m[j * 3] + m[i * 3 + 1] * m[j * 3 + 1] + m[i * 3 + 2] * m[j * 3 + 2];
                    assertEquals(i == j ? 1.0 : 0.0, dot, 1e-12);
                }
            }
            double det = m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6]) + m[2] * (m[3] * m[7] - m[4] * m[6]);
            assertEquals(1.0, det, 1e-12);
        }
    }

    /** Os ângulos do comando são em eixos locais: yaw gira em torno do "cima" local, pitch do "leste" local. */
    @Test
    void yawKeepsTheDimensionUpAndPitchKeepsEast() {
        // LOCAL_TO_SPACE: coluna 0 (leste) = +Z do espaço, coluna 1 (cima) = +X, coluna 2 (norte) = +Y
        double[] yaw = SkyObserver.shipLocalToSpace(attitude(new double[] {90, 0, 0}));
        assertEquals(1.0, yaw[1], 1e-12);
        assertEquals(0.0, yaw[4], 1e-12);
        assertEquals(0.0, yaw[7], 1e-12);
        double[] pitch = SkyObserver.shipLocalToSpace(attitude(new double[] {0, 45, 0}));
        assertEquals(0.0, pitch[0], 1e-12);
        assertEquals(0.0, pitch[3], 1e-12);
        assertEquals(1.0, pitch[6], 1e-12);
    }

    @Test
    void shipSpaceToLocalRotatesTheWholeSkyInverselyToTheAttitude() {
        double clock = 70_000.5;
        for (double[] a : ATTITUDES) {
            Quaterniond q = attitude(a);
            SkyObserver fixed = SkyObserver.forSpace(clock, 0, 30_000, 0);
            SkyObserver ship = SkyObserver.forShip(clock, pos(), q);
            // um vetor no espaço visto da nave = o visto do referencial fixo, girado por R⁻¹ (no espaço) antes de entrar nos eixos locais
            Vector3d v = new Vector3d(0.3, -0.5, 0.8).normalize();
            double[] viaShip = new double[3];
            ship.spaceToLocal(v.x, v.y, v.z, viaShip);
            Vector3d inv = new Quaterniond(q).conjugate().transform(new Vector3d(v));
            double[] viaFixed = new double[3];
            fixed.spaceToLocal(inv.x, inv.y, inv.z, viaFixed);
            for (int i = 0; i < 3; i++) {
                assertEquals(viaFixed[i], viaShip[i], 1e-12);
            }
        }
    }

    /**
     * O sol do vanilla é desenhado por (altura, giro em torno da vertical): reconstruído, é a direção que ilumina as esferas
     * ({@code observer.sun}). As estrelas, pela rotação completa, levam o sol do vanilla do spawn à mesma direção: fixas em
     * relação ao sol em qualquer atitude.
     */
    @Test
    void drawnSunStarsAndSphereLightAgreeForAnyAttitude() {
        for (double clock : CLOCKS) {
            for (double[] a : ATTITUDES) {
                SkyObserver ship = SkyObserver.forShip(clock, pos(), attitude(a));
                double[] sun = new double[3];
                ship.sun(sun);
                assertEquals(1.0, Math.sqrt(sun[0] * sun[0] + sun[1] * sun[1] + sun[2] * sun[2]), 1e-12);

                // disco: sol do vanilla (-sen a, cos a, 0) girado em torno de Y pelo tilt (convenção do JOML)
                double angle = SunModel.vanillaSkyAngle(sun);
                double tilt = SunModel.azimuthTilt(sun);
                org.joml.Vector3f disc = new org.joml.Vector3f((float) -Math.sin(angle), (float) Math.cos(angle), 0.0F);
                new Matrix4f().rotationY((float) tilt).transformDirection(disc);
                assertEquals(sun[0], disc.x, 2e-6, "disco x clock=" + clock + " " + java.util.Arrays.toString(a));
                assertEquals(sun[1], disc.y, 2e-6, "disco y");
                assertEquals(sun[2], disc.z, 2e-6, "disco z");

                // estrelas: o sol do vanilla no referencial do spawn, levado pela rotação das estrelas, é o mesmo sol
                double[] r = new double[9];
                ship.vanillaStarsToLocal(r);
                double sa = SunModel.angle(clock);
                double[] spawnSun = {-Math.sin(sa), Math.cos(sa), 0.0};
                for (int i = 0; i < 3; i++) {
                    double v = r[i * 3] * spawnSun[0] + r[i * 3 + 1] * spawnSun[1] + r[i * 3 + 2] * spawnSun[2];
                    assertEquals(sun[i], v, 1e-9, "estrelas/sol eixo " + i);
                }
            }
        }
    }

    @Test
    void spinAdvancesAboutTheLocalAxisWithoutJumpsAndOffRestoresIdentity() {
        long t0 = 1_000_000_000L;
        SkyAttitude.spin(90.0, 1, t0);
        assertTrue(SkyAttitude.current() != null);
        Quaterniond start = new Quaterniond(SkyAttitude.current());
        SkyAttitude.update(t0 + 1_000_000_000L);                // 1 s depois: 90 graus em torno do cima local
        Quaterniond after = new Quaterniond(SkyAttitude.current());
        double turned = 2.0 * Math.acos(Math.min(1.0, Math.abs(new Quaterniond(start).conjugate().mul(after).w)));
        assertEquals(Math.PI / 2.0, turned, 1e-9);
        // passos pequenos: o giro por quadro é constante (sem tremor)
        double previous = Double.NaN;
        Quaterniond last = new Quaterniond(start);
        for (int i = 1; i <= 50; i++) {
            SkyAttitude.update(t0 + i * 16_666_667L);
            Quaterniond now = new Quaterniond(SkyAttitude.current());
            double step = 2.0 * Math.acos(Math.min(1.0, Math.abs(new Quaterniond(last).conjugate().mul(now).w)));
            if (!Double.isNaN(previous)) {
                assertEquals(previous, step, 1e-7);
            }
            previous = step;
            last = now;
        }
        SkyAttitude.off();
        assertTrue(SkyAttitude.current() == null);
    }
}
