package com.spacehorizons.client.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ForkJoinPool;

import org.junit.jupiter.api.Test;

import com.spacehorizons.client.planetmesh.PlanetMeshGeometry;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetTerrain;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.planet.SurfaceType;

class BodyMeshGeometryTest {
    private static PlanetMeshGeometry.Palette palette() {
        int[] land = new int[SurfaceType.values().length];
        for (int i = 0; i < land.length; i++) {
            land[i] = 0x808080;
        }
        return new PlanetMeshGeometry.Palette(land, 0x3F76E4, 0x1A3A8C);
    }

    private static PlanetTerrain flat(PlanetDefinition planet, int height, SurfaceType type) {
        return new PlanetTerrain() {
            @Override
            public PlanetDefinition planet() {
                return planet;
            }

            @Override
            public int surfaceHeight(int blockX, int blockZ) {
                return height;
            }

            @Override
            public SurfaceType surfaceType(int blockX, int blockZ) {
                return type;
            }
        };
    }

    private static double length(float[] pos, int vertex) {
        return Math.sqrt((double) pos[vertex * 3] * pos[vertex * 3] + (double) pos[vertex * 3 + 1] * pos[vertex * 3 + 1]
                + (double) pos[vertex * 3 + 2] * pos[vertex * 3 + 2]);
    }

    @Test
    void countsAndIndicesAreConsistent() {
        for (int segments : new int[] {32, 64, 128}) {
            int vertices = BodyMeshGeometry.vertexCount(segments);
            int[] indices = new int[BodyMeshGeometry.indexCount(segments)];
            BodyMeshGeometry.fillIndices(segments, indices);
            assertEquals((segments / 2 + 1) * segments, vertices);
            assertEquals((segments / 2) * segments * 6, indices.length);
            for (int index : indices) {
                assertTrue(index >= 0 && index < vertices);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> BodyMeshGeometry.vertexCount(7));
    }

    @Test
    void verticesLieOnTheSphereOfTheTerrainSurface() {
        PlanetDefinition planet = Planets.MOON;
        int segments = 64;
        float[] pos = new float[BodyMeshGeometry.vertexCount(segments) * 3];
        int[] colors = new int[BodyMeshGeometry.vertexCount(segments)];
        BodyMeshGeometry.fill(flat(planet, 70, SurfaceType.PLAINS), palette(), segments, pos, colors, ForkJoinPool.commonPool());
        double expected = planet.radius() + (70 - PlanetMeshGeometry.SINK - planet.seaLevel());
        for (int v = 0; v < colors.length; v++) {
            assertEquals(expected, length(pos, v), 0.05);
        }
        // polo norte em +Y, polo sul em -Y
        assertEquals(expected, pos[1], 0.05);
        assertEquals(-expected, pos[(segments / 2) * segments * 3 + 1], 0.05);
    }

    @Test
    void worldsWithoutOceanNeverProduceWater() {
        // buraco abaixo do nível de referência: na Terra seria oceano, na Lua é pó (alfa = máscara de água = 0)
        PlanetDefinition moon = Planets.MOON;
        int segments = 64;
        float[] pos = new float[BodyMeshGeometry.vertexCount(segments) * 3];
        int[] colors = new int[BodyMeshGeometry.vertexCount(segments)];
        BodyMeshGeometry.fill(flat(moon, moon.seaLevel() - 30, SurfaceType.OCEAN), palette(), segments, pos, colors, null);
        for (int color : colors) {
            assertEquals(0, color >>> 24);
        }
        // e a altura não é achatada no nível do mar
        double expected = moon.radius() + (-30 - PlanetMeshGeometry.SINK);
        assertEquals(expected, length(pos, 0), 0.05);
    }

    @Test
    void oceanPlanetsFlattenToSeaLevelAndMarkWater() {
        PlanetDefinition earth = Planets.EARTH;
        int segments = 64;
        PlanetTerrain deep = flat(earth, earth.seaLevel() - 30, SurfaceType.OCEAN);
        float[] pos = new float[BodyMeshGeometry.vertexCount(segments) * 3];
        int[] colors = new int[BodyMeshGeometry.vertexCount(segments)];
        BodyMeshGeometry.fill(deep, palette(), segments, pos, colors, null);
        // anel do equador (segments / 4), fora da calota polar
        int v = (segments / 4) * segments;
        assertEquals(earth.radius() - PlanetMeshGeometry.SINK, length(pos, v), 0.05);
        assertEquals(PlanetMeshGeometry.WATER_ALPHA, colors[v] >>> 24);
    }
}
