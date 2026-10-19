package com.spacehorizons.space.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.space.InertialFrame;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceFrame;

/**
 * Regressão do movimento da Lua no {@link SolarSystem}, que decide toda a calibração das manobras. Em I (o referencial
 * INERCIAL, o da física do foguete) a Lua é kepleriana: círculo uniforme a ~38 blocos/tick, período ~98.985 ticks, para o
 * leste, com rotação sincronizada. Em E (o referencial preso à Terra, o dos lugares e do piloto automático criativo) ela
 * anda de ~66 a ~149 blocos/tick, porque E gira. Se alguém mexer na órbita ou na curva do sol, estes números mudam e a
 * física de voo (janelas de transferência, custo de captura) precisa ser recalibrada.
 */
class MoonKinematicsTest {
    @Test
    void moonSpeedInTheEarthFixedFrameRangesFromSixtySixToOneFortyNine() {
        double min = Double.MAX_VALUE;
        double max = 0;
        double sum = 0;
        int count = 0;
        double[] v = new double[3];
        for (double c = 0; c < 24_000; c += 10) {
            SolarSystem.MOON.velocityAt(c + 0.5, v);
            double speed = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            min = Math.min(min, speed);
            max = Math.max(max, speed);
            sum += speed;
            count++;
        }
        assertEquals(66.4, min, 0.5);
        assertEquals(149.0, max, 1.0);
        // média = D·(2π/24.000 − n) = 119,4 (a taxa de giro média da Terra menos o movimento médio da Lua)
        assertEquals(SolarSystem.EARTH_MOON_DISTANCE * (2.0 * Math.PI / 24_000.0 - SolarSystem.MOON_MEAN_MOTION), sum / count, 0.5);
    }

    @Test
    void moonSpeedInTheInertialFrameIsConstantAndTheCircularSpeed() {
        double[] v = new double[3];
        BodyPhysics earth = BodyPhysics.of(SolarSystem.EARTH);
        for (double c = 0; c < 200_000; c += 997) {
            SolarSystem.MOON.inertialVelocityAt(c, v);
            assertEquals(38.08, Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]), 0.02);
            assertEquals(earth.circularSpeed(SolarSystem.EARTH_MOON_DISTANCE), Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]), 1e-4);
        }
    }

    @Test
    void equatorialSurfaceSpeedOfTheMoonRotationIsConstantInTheInertialFrame() {
        // rotação sincronizada: o período é o orbital, n·R = 1,66 blocos/tick no equador, sempre (em I)
        double[] abs = new double[3];
        double[] ground = new double[3];
        double[] center = new double[3];
        for (double clock = 0; clock < 120_000; clock += 2_501) {
            SolarSystem.Pose pose = SolarSystem.MOON.inertialPoseAt(clock);
            SpaceFrame.toSpace(SolarSystem.MOON, pose, 0, SolarSystem.MOON.planet().seaLevel(), 0, abs);
            SpaceFrame.inertialGroundVelocity(SolarSystem.MOON, clock, abs[0], abs[1], abs[2], ground);
            SolarSystem.MOON.inertialVelocityAt(clock, center);
            double speed = new V3(ground[0] - center[0], ground[1] - center[1], ground[2] - center[2]).length();
            assertEquals(SolarSystem.MOON_MEAN_MOTION * SolarSystem.MOON.planet().radius(), speed, 1e-3);
        }
    }

    @Test
    void equatorialSurfaceSpeedOfTheMoonRotationInTheEarthFixedFrameRangesFromTwoPointNineToFive() {
        // em E o chão da Lua gira a (φ̇ − n): de ~2,9 a ~5,6 blocos/tick no equador
        double min = Double.MAX_VALUE;
        double max = 0;
        double[] abs = new double[3];
        double[] ground = new double[3];
        double[] center = new double[3];
        for (double clock = 0; clock < 24_000; clock += 25) {
            SpaceFrame.toSpace(SolarSystem.MOON, clock, 0, 63, 0, abs);
            SpaceFrame.groundVelocity(SolarSystem.MOON, clock, abs[0], abs[1], abs[2], ground);
            SolarSystem.MOON.velocityAt(clock, center);
            double speed = new V3(ground[0] - center[0], ground[1] - center[1], ground[2] - center[2]).length();
            min = Math.min(min, speed);
            max = Math.max(max, speed);
        }
        double radius = SolarSystem.MOON.planet().radius();
        assertEquals((0.666 * 2.0 * Math.PI / 24_000.0 - SolarSystem.MOON_MEAN_MOTION) * radius, min, 0.1);
        assertEquals((1.19 * 2.0 * Math.PI / 24_000.0 - SolarSystem.MOON_MEAN_MOTION) * radius, max, 0.15);
    }

    @Test
    void moonOrbitsEastwardInTheInertialFrame() {
        // o polo P = −norte da Terra dá à Lua uma inclinação de ~5° em todo instante (leste, o sentido da rotação da Terra)
        BodyPhysics earth = BodyPhysics.of(SolarSystem.EARTH);
        for (double clock : new double[] {0, 4_321, 12_000.5, 77_777, 191_999}) {
            double[] m = new double[3];
            SolarSystem.MOON.inertialPositionAt(clock, m);
            double[] mv = new double[3];
            SolarSystem.MOON.inertialVelocityAt(clock, mv);
            Orbit moon = Orbit.fromState(earth.mu(), V3.of(m), V3.of(mv), earth.pole(clock));
            assertEquals(5.0, Math.toDegrees(moon.inclination()), 0.1, "clock = " + clock);
            assertTrue(moon.eastward());
            assertEquals(0.0, moon.e(), 1e-6);
            assertEquals(SolarSystem.EARTH_MOON_DISTANCE, moon.a(), 1.0);
            assertEquals(SolarSystem.ORBITAL_PERIOD_TICKS, moon.period(), 0.5);
        }
    }

    @Test
    void theMoonIsKeplerianInTheInertialFrameButNotInTheEarthFixedOne() {
        BodyPhysics earth = BodyPhysics.of(SolarSystem.EARTH);
        double keplerian = Math.sqrt(earth.mu() / Math.pow(SolarSystem.EARTH_MOON_DISTANCE, 3));
        assertEquals(6.348e-5, keplerian, 1e-7);
        assertEquals(keplerian, SolarSystem.MOON_MEAN_MOTION, 1e-12);
        // em E a taxa angular dela é φ̇ − n: nasce a leste e se põe a oeste, de ~2,0e-4 rad/tick em média
        double meanE = 2.0 * Math.PI / 24_000.0 - keplerian;
        assertEquals(1.983e-4, meanE, 1e-6);
        assertTrue(InertialFrame.rate(0.0) > 0);
    }
}
