package com.spacehorizons.client.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.spacehorizons.client.planetmesh.PlanetMeshGeometry;
import com.spacehorizons.client.planetmesh.PlanetMeshProjection;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrain;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.SurfaceType;
import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceFrame;

/**
 * A garantia de "sem salto": a malha orbital do planeta atual (vista de muito alto) e a esfera do mesmo planeta
 * vista de outro corpo ficam no mesmo lugar, com a mesma cor e a mesma altura.
 */
class PlanetBodyCoincidenceTest {
    private static final PlanetDefinition EARTH = Planets.EARTH;

    /** Relevo e tipos que variam (para a comparação de cor e altura valer algo), sem depender do terreno real. */
    private static PlanetTerrain varied() {
        return new PlanetTerrain() {
            @Override
            public PlanetDefinition planet() {
                return EARTH;
            }

            @Override
            public int surfaceHeight(int blockX, int blockZ) {
                return 40 + Math.floorMod(blockX / 997 + blockZ / 1013, 90);
            }

            @Override
            public SurfaceType surfaceType(int blockX, int blockZ) {
                SurfaceType[] types = {SurfaceType.OCEAN, SurfaceType.PLAINS, SurfaceType.MOUNTAIN, SurfaceType.DESERT};
                return types[Math.floorMod(blockX / 1500 + blockZ / 1700, types.length)];
            }
        };
    }

    private static PlanetMeshGeometry.Palette palette() {
        int[] land = new int[SurfaceType.values().length];
        for (int i = 0; i < land.length; i++) {
            land[i] = 0x203040 + i * 0x090807;
        }
        return new PlanetMeshGeometry.Palette(land, 0x3F76E4, 0x1A3A8C);
    }

    @Test
    void orbitalMeshVertexEqualsBodySphereVertexSeenFromTheSameCamera() {
        PlanetTerrain terrain = varied();
        PlanetMeshGeometry.Palette palette = palette();
        // câmera em órbita, fora do centro da carta e em latitude diferente de 0 (exercita a rotação da base local)
        double camX = 1234.5;
        double camZ = 2000.0;
        double alt = 40_000.0;
        double camY = EARTH.seaLevel() + alt;
        double nearRadius = 128.0;

        double radius = EARTH.radius();
        // células de 24+ blocos teriam supersampling; aqui o raio é grande, então a cor central é a parte comparável
        PlanetMeshGeometry.Layout layout = new PlanetMeshGeometry.Layout(new double[] {35_000.0, 45_000.0, 55_000.0, 65_000.0},
                new int[] {48, 48, 96, 96});
        float[] pos = new float[layout.vertexCount() * 3];
        int[] colors = new int[layout.vertexCount()];
        PlanetMeshGeometry.fill(terrain, palette, layout, camX, camY, camZ, nearRadius, pos, colors, null);

        double[] camSpace = new double[3];
        SpaceFrame.toSpace(SolarSystem.EARTH, camX, camY, camZ, camSpace);
        double[] point = new double[3];
        double[] xz = new double[2];
        double[] height = new double[1];
        double[] local = new double[3];
        int checked = 0;
        for (int ring = 0; ring < layout.rings(); ring++) {
            double theta = layout.ringRadius(ring) / radius;
            double weight = PlanetMeshProjection.weight(radius, alt, layout.ringRadius(ring), nearRadius);
            assertEquals(1.0, weight, 0.0, "a carta lat/lon fixa tem de valer por inteiro em órbita");
            for (int j = 0; j < layout.segments(ring); j += 5) {
                double phi = 2.0 * Math.PI * j / layout.segments(ring);
                PlanetMeshProjection.terrainPoint(EARTH, camX, camZ, theta, phi, weight, xz);
                // o lado do corpo: mesmo (x, z) do terreno, altura da função compartilhada, posição pelo SpaceFrame
                int argb = PlanetMeshGeometry.shadeVertex(terrain, palette, xz[0], xz[1], new double[6], 0, height);
                SpaceFrame.toSpace(SolarSystem.EARTH, xz[0], height[0] - PlanetMeshGeometry.SINK, xz[1], point);
                SpaceFrame.spaceToLocal(SolarSystem.EARTH, camX, camZ,
                        point[0] - camSpace[0], point[1] - camSpace[1], point[2] - camSpace[2], local);
                int v = layout.vertexOffset(ring) + j;
                // a malha guarda float: tolerância de alguns ulps a ~100.000 blocos
                assertEquals(pos[v * 3], local[0], 0.05, "x anel " + ring + " seg " + j);
                assertEquals(pos[v * 3 + 1], local[1], 0.05, "y anel " + ring + " seg " + j);
                assertEquals(pos[v * 3 + 2], local[2], 0.05, "z anel " + ring + " seg " + j);
                // mesma cor (células de 24+ blocos têm supersampling na malha: a comparação exata fica para o centro)
                double cell = 2.0 * Math.PI * layout.ringRadius(ring) / layout.segments(ring);
                if (cell < PlanetMeshGeometry.SUPERSAMPLE_MIN_CELL_BLOCKS) {
                    assertEquals(colors[v], argb, "cor anel " + ring + " seg " + j);
                }
                checked++;
            }
        }
        assertTrue(checked > 30);
    }

    @Test
    void ownPlanetHorizonAnglesMatchTheApparentRadius() {
        // vértice no horizonte (arco do horizonte, altura 0): o ângulo dele ao nadir é o raio angular aparente do corpo
        double alt = 15_000.0;
        double radius = EARTH.radius();
        BodySky.Apparent a = BodySky.fromDimension(SolarSystem.EARTH, 0, EARTH.seaLevel() + alt, 0, SolarSystem.EARTH);
        double thetaHorizon = Math.acos(radius / (radius + alt));
        double[] dir = new double[3];
        PlanetMeshProjection.direction(thetaHorizon, 0.7, dir);
        double px = radius * dir[0];
        double py = radius * dir[1] - (radius + alt);
        double pz = radius * dir[2];
        double len = Math.sqrt(px * px + py * py + pz * pz);
        double angleToNadir = Math.acos(-py / len);
        assertEquals(a.angularRadius(), angleToNadir, 1e-9);
    }

    @Test
    void bodySphereVertexMatchesSpaceFrame() {
        PlanetTerrain terrain = varied();
        PlanetMeshGeometry.Palette palette = palette();
        int segments = 64;
        float[] pos = new float[BodyMeshGeometry.vertexCount(segments) * 3];
        int[] colors = new int[BodyMeshGeometry.vertexCount(segments)];
        BodyMeshGeometry.fill(terrain, palette, segments, pos, colors, null);
        double radius = EARTH.radius();
        double[] out = new double[3];
        double[] height = new double[1];
        for (int ring : new int[] {0, 7, 16, 25, 32}) {
            for (int j : new int[] {0, 9, 31, 50}) {
                double lat = BodyMeshGeometry.ringLatitude(segments, ring);
                double lon = BodyMeshGeometry.columnLongitude(segments, j);
                int v = ring * segments + j;
                PlanetMeshGeometry.shadeVertex(terrain, palette, lon * radius, lat * radius, new double[6], 0, height);
                SpaceFrame.toSpace(SolarSystem.EARTH, lon * radius, height[0] - PlanetMeshGeometry.SINK, lat * radius, out);
                assertEquals(out[0], pos[v * 3], 0.02);
                assertEquals(out[1], pos[v * 3 + 1], 0.02);
                assertEquals(out[2], pos[v * 3 + 2], 0.02);
            }
        }
    }
}
