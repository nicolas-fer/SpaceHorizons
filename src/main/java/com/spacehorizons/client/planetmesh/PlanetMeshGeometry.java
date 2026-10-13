package com.spacehorizons.client.planetmesh;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

import com.spacehorizons.planet.CurvatureMath;
import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetMath;
import com.spacehorizons.planet.PlanetTerrain;
import com.spacehorizons.planet.SurfaceType;

/**
 * Geração (Java puro, sem Minecraft) da malha orbital: uma grade polar centrada na câmera, que cobre do
 * fim do alcance do DH até quase o antípoda do planeta.
 *
 * Cada vértice é um ponto (dx, dz) do plano em anéis de raio r crescente. O terreno é amostrado em
 * (camX + dx, camZ + dz), como nos chunks e nos LODs, e a posição final passa por CurvatureMath.curve com
 * a mesma altitude de câmera, então a malha cai exatamente em cima da esfera que os outros dois desenham.
 * A posição sai relativa à câmera de construção (float), pronta para o vertex buffer.
 */
public final class PlanetMeshGeometry {
    /** Quanto a malha fica abaixo do terreno real, para nunca brigar com o DH na região de sobreposição. */
    public static final double SINK = 3.0;
    /** Fração de π·R do anel externo: perto do antípoda a grade converge para um ponto e vira lixo numérico. */
    public static final double OUTER_FRACTION = 0.98;
    /** O alfa da cor do vértice é a máscara de água (255 = água, 0 = terra); o shader usa para o brilho do sol. */
    public static final int WATER_ALPHA = 255;

    private PlanetMeshGeometry() {
    }

    /** Cores por tipo de superfície (0xRRGGBB, indexadas por SurfaceType.ordinal()) e as duas cores da água. */
    public record Palette(int[] land, int waterShallow, int waterDeep) {
        public Palette {
            if (land.length != SurfaceType.values().length) {
                throw new IllegalArgumentException("land precisa ter uma cor por SurfaceType");
            }
        }
    }

    /**
     * Topologia da grade: rings anéis (de rMin a rMax, espaçamento geométrico) com segments vértices cada.
     * O índice do vértice (anel i, segmento j) é i * segments + j.
     */
    public record Layout(int rings, int segments, double rMin, double rMax) {
        public Layout {
            if (rings < 2 || segments < 3) {
                throw new IllegalArgumentException("grade pequena demais: " + rings + "x" + segments);
            }
            if (!(rMin > 0) || !(rMax > rMin)) {
                throw new IllegalArgumentException("raios inválidos: " + rMin + " .. " + rMax);
            }
        }

        /**
         * Escolhe o número de anéis para que o espaçamento radial acompanhe o tangencial
         * (razão entre anéis = 1 + 2π/segments), limitado por maxRings.
         */
        public static Layout create(double rMin, double rMax, int segments, int maxRings) {
            double ratio = 1.0 + 2.0 * Math.PI / segments;
            int rings = (int) Math.ceil(Math.log(rMax / rMin) / Math.log(ratio)) + 1;
            return new Layout(Math.max(2, Math.min(maxRings, rings)), segments, rMin, rMax);
        }

        public int vertexCount() {
            return rings * segments;
        }

        public int indexCount() {
            return (rings - 1) * segments * 6;
        }

        public double ringRadius(int ring) {
            return rMin * Math.pow(rMax / rMin, ring / (double) (rings - 1));
        }
    }

    /** Raio externo para um planeta: 0,98·π·R. */
    public static double outerRadius(PlanetDefinition planet) {
        return OUTER_FRACTION * Math.PI * planet.radius();
    }

    /**
     * Preenche posições (3 floats por vértice, relativas à câmera) e cores (0xAARRGGBB, alfa = máscara de água).
     * Se pool não for nulo os anéis são calculados em paralelo nele; o terreno é thread-safe.
     */
    public static void fill(PlanetTerrain terrain, Palette palette, Layout layout,
            double camX, double camY, double camZ, float[] outPos, int[] outColor, ForkJoinPool pool) {
        if (outPos.length < layout.vertexCount() * 3 || outColor.length < layout.vertexCount()) {
            throw new IllegalArgumentException("buffers pequenos para " + layout.vertexCount() + " vértices");
        }
        double[] cos = new double[layout.segments()];
        double[] sin = new double[layout.segments()];
        for (int j = 0; j < layout.segments(); j++) {
            double phi = 2.0 * Math.PI * j / layout.segments();
            cos[j] = Math.cos(phi);
            sin[j] = Math.sin(phi);
        }

        Runnable all = () -> IntStream.range(0, layout.rings()).parallel()
                .forEach(i -> fillRing(terrain, palette, layout, i, camX, camY, camZ, cos, sin, outPos, outColor));
        if (pool == null) {
            for (int i = 0; i < layout.rings(); i++) {
                fillRing(terrain, palette, layout, i, camX, camY, camZ, cos, sin, outPos, outColor);
            }
            return;
        }
        try {
            pool.submit(all).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("malha orbital interrompida", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("falha ao gerar a malha orbital", e.getCause());
        }
    }

    private static void fillRing(PlanetTerrain terrain, Palette palette, Layout layout, int ring,
            double camX, double camY, double camZ, double[] cos, double[] sin, float[] outPos, int[] outColor) {
        PlanetDefinition planet = terrain.planet();
        double radius = planet.radius();
        double camAlt = camY - planet.seaLevel();
        int maxLat = planet.maxLatitudeBlocks();
        int sea = planet.seaLevel();
        double r = layout.ringRadius(ring);
        double[] curved = new double[3];

        for (int j = 0; j < layout.segments(); j++) {
            double dx = r * cos[j];
            double dz = r * sin[j];
            long worldX = PlanetMath.wrapX(planet, Math.round(camX + dx));
            long worldZ = Math.round(camZ + dz);
            // além do limite de latitude o mundo é a calota polar: repete a última coluna e pinta de gelo
            boolean cap = worldZ > maxLat || worldZ < -maxLat;
            int sampleZ = (int) Math.max(-maxLat, Math.min(maxLat, worldZ));

            int raw = terrain.surfaceHeight((int) worldX, sampleZ);
            SurfaceType type = cap ? SurfaceType.POLAR_ICE : terrain.surfaceType((int) worldX, sampleZ);
            boolean water = !cap && (type == SurfaceType.OCEAN || raw < sea);
            // oceano é plano no nível do mar
            double height = Math.max(raw, sea);

            CurvatureMath.curve(radius, camAlt, dx, height - SINK - camY, dz, curved);
            int v = ring * layout.segments() + j;
            outPos[v * 3] = (float) curved[0];
            outPos[v * 3 + 1] = (float) curved[1];
            outPos[v * 3 + 2] = (float) curved[2];
            outColor[v] = water
                    ? waterColor(palette, sea - raw)
                    : palette.land()[type.ordinal()] & 0xFFFFFF;
        }
    }

    /** Água: azul-claro na costa, escurecendo com a profundidade do fundo (satura em 60 blocos). Alfa = máscara de água. */
    static int waterColor(Palette palette, int depth) {
        double t = Math.max(0.0, Math.min(1.0, depth / 60.0));
        int r = lerp(palette.waterShallow() >> 16 & 0xFF, palette.waterDeep() >> 16 & 0xFF, t);
        int g = lerp(palette.waterShallow() >> 8 & 0xFF, palette.waterDeep() >> 8 & 0xFF, t);
        int b = lerp(palette.waterShallow() & 0xFF, palette.waterDeep() & 0xFF, t);
        return WATER_ALPHA << 24 | r << 16 | g << 8 | b;
    }

    private static int lerp(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }

    /**
     * Índices (triângulos) da grade. O segmento seguinte do último é o 0, fechando o anel.
     * Dois triângulos por célula; a orientação é irrelevante porque o pipeline não faz culling.
     */
    public static void fillIndices(Layout layout, int[] out) {
        if (out.length < layout.indexCount()) {
            throw new IllegalArgumentException("array de índices pequeno");
        }
        int segments = layout.segments();
        int n = 0;
        for (int i = 0; i < layout.rings() - 1; i++) {
            int row = i * segments;
            int next = row + segments;
            for (int j = 0; j < segments; j++) {
                int j1 = (j + 1) % segments;
                out[n++] = row + j;
                out[n++] = next + j;
                out[n++] = row + j1;
                out[n++] = row + j1;
                out[n++] = next + j;
                out[n++] = next + j1;
            }
        }
    }
}
