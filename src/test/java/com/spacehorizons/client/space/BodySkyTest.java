package com.spacehorizons.client.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.planet.Planets;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceFrame;

class BodySkyTest {
    private static final double EARTH_R = Planets.EARTH.radius();
    private static final double MOON_R = Planets.MOON.radius();
    private static final double D = SolarSystem.EARTH_MOON_DISTANCE;

    @Test
    void moonIsAtZenithFromEarthSpawn() {
        double y = Planets.EARTH.seaLevel() + 1.0;
        BodySky.Apparent a = BodySky.fromDimension(SolarSystem.EARTH, 0, y, 0, SolarSystem.MOON);
        assertEquals(0.0, a.x(), 1e-9);
        assertEquals(1.0, a.y(), 1e-9);
        assertEquals(0.0, a.z(), 1e-9);
        assertEquals(D - EARTH_R - 1.0, a.distance(), 1e-6);
        assertEquals(Math.asin(MOON_R / a.distance()), a.angularRadius(), 1e-12);
    }

    @Test
    void earthSeenFromMoonFace() {
        // face da Lua voltada para a Terra: lon = π (x = C/2), lat 0; a Terra fica em cima, a (D - R_lua) de distância
        double x = Planets.MOON.halfCircumference();
        double y = Planets.MOON.seaLevel();
        BodySky.Apparent a = BodySky.fromDimension(SolarSystem.MOON, x, y, 0, SolarSystem.EARTH);
        assertEquals(1.0, a.y(), 1e-9);
        assertEquals(0.0, a.x(), 1e-9);
        assertEquals(0.0, a.z(), 1e-9);
        assertEquals(D - MOON_R, a.distance(), 1e-6);
        assertEquals(Math.asin(EARTH_R / (D - MOON_R)), a.angularRadius(), 1e-12);
        // raio angular de ~10°: um disco grande
        assertTrue(Math.toDegrees(a.angularRadius()) > 9.0);
    }

    @Test
    void ownPlanetIsBelowAndSubtendsTheHorizon() {
        double alt = 40_000.0;
        BodySky.Apparent a = BodySky.fromDimension(SolarSystem.EARTH, 1234.5, Planets.EARTH.seaLevel() + alt, -2000.0, SolarSystem.EARTH);
        assertEquals(-1.0, a.y(), 1e-9);
        assertEquals(0.0, a.x(), 1e-9);
        assertEquals(0.0, a.z(), 1e-9);
        assertEquals(EARTH_R + alt, a.distance(), 1e-6);
        assertEquals(Math.asin(EARTH_R / (EARTH_R + alt)), a.angularRadius(), 1e-12);
    }

    @Test
    void spaceAndLocalViewsAgree() {
        double[] cam = new double[3];
        double x = 3000;
        double y = Planets.EARTH.seaLevel() + 500;
        double z = 800;
        SpaceFrame.toSpace(SolarSystem.EARTH, x, y, z, cam);
        BodySky.Apparent space = BodySky.fromSpacePoint(SolarSystem.MOON, cam[0], cam[1], cam[2]);
        BodySky.Apparent local = BodySky.fromDimension(SolarSystem.EARTH, x, y, z, SolarSystem.MOON);
        assertEquals(space.distance(), local.distance(), 1e-6);
        assertEquals(space.angularRadius(), local.angularRadius(), 1e-12);
        double[] back = new double[3];
        SpaceFrame.localToSpace(SolarSystem.EARTH, x, z, local.x(), local.y(), local.z(), back);
        assertEquals(space.x(), back[0], 1e-9);
        assertEquals(space.y(), back[1], 1e-9);
        assertEquals(space.z(), back[2], 1e-9);
    }

    @Test
    void scaledSpaceKeepsDirectionAndAngularSize() {
        double dist = 3_000_000.0;
        double s = BodySky.scaleFactor(dist, BodySky.SCALED_SPACE_DISTANCE);
        assertEquals(BodySky.SCALED_SPACE_DISTANCE / dist, s, 1e-15);
        // raio e distância escalados juntos: o raio angular não muda
        assertEquals(Math.asin(EARTH_R / dist), Math.asin(EARTH_R * s / (dist * s)), 1e-15);
        assertEquals(1.0, BodySky.scaleFactor(50_000.0, BodySky.SCALED_SPACE_DISTANCE), 0.0);
        assertEquals(1.0, BodySky.scaleFactor(BodySky.SCALED_SPACE_DISTANCE, BodySky.SCALED_SPACE_DISTANCE), 0.0);
    }

    @Test
    void segmentsGrowWithApparentSizeAndStayInRange() {
        double pixel = 2.0 * Math.tan(Math.toRadians(35.0)) / 1080.0;
        double nearEarth = BodySky.requiredSegments(EARTH_R, 20_000.0, pixel);
        double farEarth = BodySky.requiredSegments(EARTH_R, 400_000.0, pixel);
        assertTrue(nearEarth > farEarth);
        assertEquals(BodySky.MAX_SEGMENTS, BodySky.chooseSegments(0, nearEarth));
        assertEquals(BodySky.MIN_SEGMENTS, BodySky.chooseSegments(0, 1.0));
        assertEquals(128, BodySky.chooseSegments(0, 100.0));
        // histerese: pequenas variações não trocam de nível
        assertEquals(128, BodySky.chooseSegments(128, 80.0));
        assertEquals(128, BodySky.chooseSegments(128, 180.0));
        assertEquals(256, BodySky.chooseSegments(128, 200.0));
        assertEquals(64, BodySky.chooseSegments(128, 60.0));
    }

    @Test
    void daylightFadesBodiesOnlyInsideAtmosphereByDay() {
        assertEquals(1.0, BodySky.daylightFade(0.0, 1.0), 0.0);
        assertEquals(1.0, BodySky.daylightFade(1.0, -0.5), 0.0);
        assertEquals(1.0 - BodySky.MAX_DAYLIGHT_FADE, BodySky.daylightFade(1.0, 1.0), 1e-12);
        assertTrue(BodySky.daylightFade(1.0, 0.1) > BodySky.daylightFade(1.0, 0.5));
    }

    @Test
    void frustumTest() {
        double half = Math.toRadians(45.0);
        assertTrue(BodySky.inFrustum(0, 0, -1, 0, 0, -1, half, 0.01));
        assertFalse(BodySky.inFrustum(0, 0, 1, 0, 0, -1, half, 0.01));
        // disco grande além da borda do frustum ainda conta
        double angle = Math.toRadians(60.0);
        assertTrue(BodySky.inFrustum(Math.sin(angle), 0, -Math.cos(angle), 0, 0, -1, half, Math.toRadians(20.0)));
        assertFalse(BodySky.inFrustum(Math.sin(angle), 0, -Math.cos(angle), 0, 0, -1, half, Math.toRadians(5.0)));
    }
}
