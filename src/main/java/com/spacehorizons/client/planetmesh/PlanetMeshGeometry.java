package com.spacehorizons.client.planetmesh;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.PlanetMath;
import com.spacehorizons.planet.PlanetTerrain;
import com.spacehorizons.planet.SurfaceColors;
import com.spacehorizons.planet.SurfaceType;
import com.spacehorizons.planet.map.PlanetMapTerrain;

/**
 * Geração (Java puro, sem Minecraft) da malha orbital: uma grade polar na esfera, centrada no nadir da câmera,
 * que cobre do fim do alcance do DH até o horizonte visível (no máximo quase o antípoda do planeta).
 *
 * Cada vértice é uma direção (θ, φ) em torno do nadir, em anéis de raio de arco r = R·θ crescente. A posição é
 * exatamente (R + h)·direção, relativa à câmera (a mesma esfera que chunks e LODs desenham com CurvatureMath).
 * O terreno é amostrado no ponto do mundo dado por {@link PlanetMeshProjection#terrainPoint}: igual ao dos
 * chunks e do DH perto do chão e do nadir, e mais estável (esfera lat/lon fixa) em órbita.
 *
 * A grade é adaptativa: a contagem de segmentos de cada anel dobra (48, 96, 192...) conforme o anel cresce, de
 * modo que as células fiquem com um tamanho de ~1,5 pixel na tela (ver {@link Layout#adaptive}); a costura entre
 * anéis de contagens diferentes usa triângulos extras, sem fendas. A cor de cada vértice é a média de várias
 * amostras do terreno (supersampling), o que suaviza as bordas de bioma.
 */
public final class PlanetMeshGeometry {
    /** Quanto a malha fica abaixo do terreno real, para nunca brigar com o DH na região de sobreposição. */
    public static final double SINK = 3.0;
    /** Fração de π·R do anel externo: perto do antípoda a grade converge para um ponto e vira lixo numérico. */
    public static final double OUTER_FRACTION = 0.98;
    /** O alfa da cor do vértice é a máscara de água (255 = água, 0 = terra, intermediário = costa); o shader usa para o brilho do sol. */
    public static final int WATER_ALPHA = 255;
    /** Células menores que isto (blocos) não precisam de supersampling: já são mais finas que o DH. */
    static final double SUPERSAMPLE_MIN_CELL = 24.0;
    /** O mesmo critério, para a esfera dos corpos vistos de fora (client/space). */
    public static final double SUPERSAMPLE_MIN_CELL_BLOCKS = SUPERSAMPLE_MIN_CELL;
    /** Raio dos pontos extras do supersampling, em frações do tamanho da célula. */
    private static final double SUPERSAMPLE_RADIUS = 0.35;
    private static final double[] SUPERSAMPLE_COS = {Math.cos(Math.PI / 2), Math.cos(Math.PI * 7 / 6), Math.cos(Math.PI * 11 / 6)};
    private static final double[] SUPERSAMPLE_SIN = {Math.sin(Math.PI / 2), Math.sin(Math.PI * 7 / 6), Math.sin(Math.PI * 11 / 6)};

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
     * Topologia da grade: anéis de raio de arco crescente, cada um com seu número de segmentos. Anéis vizinhos
     * têm o mesmo número de segmentos ou o dobro (a costura dos índices só trata esses dois casos).
     * O índice do vértice (anel i, segmento j) é vertexOffset(i) + j.
     */
    public static final class Layout {
        /** Menor número de segmentos de um anel. */
        public static final int MIN_SEGMENTS = 48;
        /** Maior número de segmentos de um anel (48 · 2^7). */
        public static final int MAX_SEGMENTS = MIN_SEGMENTS << 7;
        /** Tamanho alvo da célula na tela, em pixels. */
        public static final double TARGET_PIXELS = 1.5;
        /** Piso do tamanho de célula, em blocos (mais fino que isso o DH já cobre). */
        public static final double MIN_CELL = 16.0;
        /** Ângulo de um pixel (rad) assumido quando o chamador não sabe: FOV 70° em 1080p. */
        public static final double DEFAULT_PIXEL_ANGLE = 2.0 * Math.tan(Math.toRadians(35.0)) / 1080.0;

        private final double[] radii;
        private final int[] segments;
        private final int[] offsets;

        public Layout(double[] radii, int[] segments) {
            if (radii.length < 2 || radii.length != segments.length) {
                throw new IllegalArgumentException("grade pequena demais ou inconsistente: " + radii.length + " / " + segments.length);
            }
            if (!(radii[0] > 0)) {
                throw new IllegalArgumentException("raio interno inválido: " + radii[0]);
            }
            for (int i = 0; i < radii.length; i++) {
                if (segments[i] < 3) {
                    throw new IllegalArgumentException("anel com menos de 3 segmentos: " + i);
                }
                if (i > 0) {
                    if (!(radii[i] > radii[i - 1])) {
                        throw new IllegalArgumentException("raios devem crescer: anel " + i);
                    }
                    if (segments[i] != segments[i - 1] && segments[i] != 2 * segments[i - 1]) {
                        throw new IllegalArgumentException("anéis vizinhos precisam ter n ou 2n segmentos: anel " + i);
                    }
                }
            }
            this.radii = radii.clone();
            this.segments = segments.clone();
            this.offsets = new int[radii.length + 1];
            for (int i = 0; i < radii.length; i++) {
                offsets[i + 1] = offsets[i] + segments[i];
            }
        }

        /**
         * Grade uniforme (mesmo número de segmentos em todos os anéis, raios geométricos): escolhe o número de
         * anéis para que o espaçamento radial acompanhe o tangencial (razão 1 + 2π/segments), limitado por maxRings.
         */
        public static Layout uniform(double rMin, double rMax, int segments, int maxRings) {
            if (!(rMin > 0) || !(rMax > rMin)) {
                throw new IllegalArgumentException("raios inválidos: " + rMin + " .. " + rMax);
            }
            double ratio = 1.0 + 2.0 * Math.PI / segments;
            int rings = (int) Math.ceil(Math.log(rMax / rMin) / Math.log(ratio)) + 1;
            rings = Math.max(2, Math.min(maxRings, rings));
            double[] radii = new double[rings];
            int[] counts = new int[rings];
            for (int i = 0; i < rings; i++) {
                radii[i] = rMin * Math.pow(rMax / rMin, i / (double) (rings - 1));
                counts[i] = segments;
            }
            return new Layout(radii, counts);
        }

        /**
         * Grade adaptativa para uma câmera a {@code altitude} blocos do nível do mar: o tamanho da célula de cada
         * anel é TARGET_PIXELS pixels na tela (distância real da câmera ao anel vezes o ângulo de um pixel),
         * com piso MIN_CELL e teto de 1/4 do raio do anel; o espaçamento radial é igual ao tangencial. Se passar
         * de {@code maxVertices} todas as células crescem na mesma proporção até caber.
         */
        public static Layout adaptive(double planetRadius, double altitude, double rMin, double rMax,
                double pixelAngle, int maxVertices) {
            if (!(rMin > 0) || !(rMax > rMin)) {
                throw new IllegalArgumentException("raios inválidos: " + rMin + " .. " + rMax);
            }
            double angle = pixelAngle > 0 ? pixelAngle : DEFAULT_PIXEL_ANGLE;
            Layout layout = build(planetRadius, altitude, rMin, rMax, angle, 1.0);
            if (layout.vertexCount() <= maxVertices) {
                return layout;
            }
            double lo = 1.0;
            double hi = 256.0;
            Layout best = build(planetRadius, altitude, rMin, rMax, angle, hi);
            for (int i = 0; i < 14; i++) {
                double mid = Math.sqrt(lo * hi);
                Layout candidate = build(planetRadius, altitude, rMin, rMax, angle, mid);
                if (candidate.vertexCount() <= maxVertices) {
                    hi = mid;
                    best = candidate;
                } else {
                    lo = mid;
                }
            }
            return best;
        }

        /** Distância da câmera (altitude acima do ponto sob ela) a um ponto da superfície a r blocos de arco. */
        static double distanceToRing(double planetRadius, double altitude, double r) {
            double cameraRadius = planetRadius + Math.max(0.0, altitude);
            double cos = Math.cos(Math.min(Math.PI, r / planetRadius));
            return Math.sqrt(Math.max(1.0, cameraRadius * cameraRadius + planetRadius * planetRadius
                    - 2.0 * planetRadius * cameraRadius * cos));
        }

        /** Tamanho alvo (blocos) da célula num anel de raio r. */
        static double cellSize(double planetRadius, double altitude, double r, double pixelAngle, double scale) {
            double raw = TARGET_PIXELS * pixelAngle * distanceToRing(planetRadius, altitude, r) * scale;
            return Math.min(Math.max(raw, MIN_CELL), 0.25 * r);
        }

        private static Layout build(double planetRadius, double altitude, double rMin, double rMax,
                double pixelAngle, double scale) {
            double[] radii = new double[64];
            int count = 0;
            double r = rMin;
            while (true) {
                if (count == radii.length) {
                    radii = Arrays.copyOf(radii, count * 2);
                }
                radii[count++] = r;
                if (r >= rMax) {
                    break;
                }
                double cell = cellSize(planetRadius, altitude, r, pixelAngle, scale);
                double next = r + cell;
                // não deixa um último anel colado no penúltimo
                r = next >= rMax - 0.25 * cell ? rMax : next;
            }
            if (count < 2) {
                radii[count++] = rMax;
            }
            int[] counts = new int[count];
            int level = 0;
            for (int i = 0; i < count; i++) {
                double cell = cellSize(planetRadius, altitude, radii[i], pixelAngle, scale);
                double ideal = 2.0 * Math.PI * radii[i] / cell;
                int wanted = (int) Math.round(Math.log(Math.max(1.0, ideal / MIN_SEGMENTS)) / Math.log(2.0));
                // monotônico e no máximo um dobro por anel (a costura só trata n e 2n)
                level = Math.max(level, Math.min(wanted, i == 0 ? wanted : level + 1));
                level = Math.min(level, 7);
                counts[i] = MIN_SEGMENTS << level;
            }
            return new Layout(Arrays.copyOf(radii, count), counts);
        }

        public int rings() {
            return radii.length;
        }

        public int segments(int ring) {
            return segments[ring];
        }

        public double ringRadius(int ring) {
            return radii[ring];
        }

        /** Índice do primeiro vértice do anel. */
        public int vertexOffset(int ring) {
            return offsets[ring];
        }

        public double rMin() {
            return radii[0];
        }

        public double rMax() {
            return radii[radii.length - 1];
        }

        public int vertexCount() {
            return offsets[radii.length];
        }

        public int indexCount() {
            int triangles = 0;
            for (int i = 0; i < radii.length - 1; i++) {
                // mesmo número: 2 triângulos por célula; o dobro: 3 por segmento do anel mais grosso
                triangles += segments[i + 1] == segments[i] ? 2 * segments[i] : 3 * segments[i];
            }
            return triangles * 3;
        }

        /** Mesmos anéis e segmentos: o buffer de índices de uma serve para a outra. */
        public boolean sameTopology(Layout other) {
            return Arrays.equals(segments, other.segments);
        }
    }

    /** A malha cobre até o horizonte da altitude atual vezes esta folga (a câmera sobe entre reconstruções) mais a margem. */
    static final double OUTER_ALTITUDE_HEADROOM = 1.25;
    static final double OUTER_ALTITUDE_MARGIN = 100.0;
    static final double OUTER_HORIZON_SLACK = 1.05;
    /** Altura de montanha usada para decidir quanto além do horizonte do nível do mar a malha ainda pode aparecer. */
    static final double HORIZON_MARGIN_HEIGHT = 300.0;

    /** Raio externo máximo para um planeta: 0,98·π·R. */
    public static double outerRadius(PlanetDefinition planet) {
        return OUTER_FRACTION * Math.PI * planet.radius();
    }

    /** Distância (arco) até o horizonte de uma câmera na altitude dada, mais o horizonte de uma montanha de referência. */
    public static double horizonDistance(double radius, double altitude) {
        double fromCamera = Math.acos(radius / (radius + Math.max(0.0, altitude)));
        double fromPeak = Math.acos(radius / (radius + HORIZON_MARGIN_HEIGHT));
        return radius * (fromCamera + fromPeak);
    }

    /**
     * Raio externo para uma câmera na altitude dada: só o que pode aparecer sobre o horizonte, com folga para a
     * subida até a próxima reconstrução, limitado a {@link #outerRadius(PlanetDefinition)}.
     */
    public static double outerRadius(PlanetDefinition planet, double altitude) {
        double headroom = Math.max(0.0, altitude) * OUTER_ALTITUDE_HEADROOM + OUTER_ALTITUDE_MARGIN;
        double horizon = horizonDistance(planet.radius(), headroom) * OUTER_HORIZON_SLACK;
        return Math.min(outerRadius(planet), horizon);
    }

    /**
     * Preenche posições (3 floats por vértice, relativas à câmera) e cores (0xAARRGGBB, alfa = máscara de água).
     * {@code nearRadius} é até onde chunks/LODs cobrem (alcance do DH): dentro dele a malha usa a mesma carta
     * deles. Se pool não for nulo os anéis são calculados em paralelo nele; o terreno é thread-safe.
     */
    public static void fill(PlanetTerrain terrain, Palette palette, Layout layout,
            double camX, double camY, double camZ, double nearRadius, float[] outPos, int[] outColor, ForkJoinPool pool) {
        if (outPos.length < layout.vertexCount() * 3 || outColor.length < layout.vertexCount()) {
            throw new IllegalArgumentException("buffers pequenos para " + layout.vertexCount() + " vértices");
        }
        Runnable all = () -> IntStream.range(0, layout.rings()).parallel()
                .forEach(i -> fillRing(terrain, palette, layout, i, camX, camY, camZ, nearRadius, outPos, outColor));
        if (pool == null) {
            for (int i = 0; i < layout.rings(); i++) {
                fillRing(terrain, palette, layout, i, camX, camY, camZ, nearRadius, outPos, outColor);
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
            double camX, double camY, double camZ, double nearRadius, float[] outPos, int[] outColor) {
        PlanetDefinition planet = terrain.planet();
        double radius = planet.radius();
        double camAlt = camY - planet.seaLevel();
        int sea = planet.seaLevel();
        double r = layout.ringRadius(ring);
        double theta = r / radius;
        int segments = layout.segments(ring);
        double weight = PlanetMeshProjection.weight(radius, camAlt, r, nearRadius);
        double cell = 2.0 * Math.PI * r / segments;
        boolean supersample = cell >= SUPERSAMPLE_MIN_CELL;
        double sinTheta = Math.max(Math.sin(theta), 1.0e-3);
        double[] dir = new double[3];
        double[] xz = new double[2];
        double[] extraPoint = new double[2];

        double[] extra = new double[SUPERSAMPLE_COS.length * 2];
        double[] height = new double[1];

        for (int j = 0; j < segments; j++) {
            double phi = 2.0 * Math.PI * j / segments;
            PlanetMeshProjection.terrainPoint(planet, camX, camZ, theta, phi, weight, xz);
            int extras = 0;
            if (supersample) {
                extras = SUPERSAMPLE_COS.length;
                for (int s = 0; s < extras; s++) {
                    double offRadial = SUPERSAMPLE_RADIUS * cell * SUPERSAMPLE_COS[s];
                    double offTangent = SUPERSAMPLE_RADIUS * cell * SUPERSAMPLE_SIN[s];
                    double t = Math.max(0.0, Math.min(Math.PI, theta + offRadial / radius));
                    double p = phi + offTangent / (radius * sinTheta);
                    PlanetMeshProjection.terrainPoint(planet, camX, camZ, t, p, weight, extraPoint);
                    extra[s * 2] = extraPoint[0];
                    extra[s * 2 + 1] = extraPoint[1];
                }
            }
            int argb = shadeVertex(terrain, palette, xz[0], xz[1], extra, extras, height);

            PlanetMeshProjection.direction(theta, phi, dir);
            double h = height[0] - SINK - sea;
            double shell = radius + h;
            int v = layout.vertexOffset(ring) + j;
            outPos[v * 3] = (float) (shell * dir[0]);
            outPos[v * 3 + 1] = (float) (shell * dir[1] - (radius + camAlt));
            outPos[v * 3 + 2] = (float) (shell * dir[2]);
            outColor[v] = argb;
        }
    }

    /**
     * Altura e cor de um vértice da esfera no ponto (x, z) do mundo, com {@code extras} pontos de supersampling
     * ({@code extraXZ}: pares x, z). Fonte ÚNICA da altura e da cor de qualquer vértice orbital: a malha do planeta
     * atual (fillRing) e a esfera do mesmo planeta vista de outro corpo (BodyMeshGeometry) chamam esta função, então
     * as cores e o relevo são idênticos e não há salto na troca de dimensão.
     *
     * @param outHeight [0] = Y efetivo da superfície (planetas com oceano são planos no nível do mar)
     * @return 0xAARRGGBB; alfa = máscara de água (sempre 0 em planetas sem oceano)
     */
    public static int shadeVertex(PlanetTerrain terrain, Palette palette, double x, double z,
            double[] extraXZ, int extras, double[] outHeight) {
        PlanetDefinition planet = terrain.planet();
        int sea = planet.seaLevel();
        Column center = column(terrain, (int) PlanetMath.wrapX(planet, Math.round(x)), Math.round(z));
        // oceano é plano no nível do mar
        outHeight[0] = planet.hasOcean() ? Math.max(center.height, sea) : center.height;

        int centerDepth = Math.max(0, sea - center.height);
        int shared = columnColor(palette, center, centerDepth);
        int red = shared >> 16 & 0xFF;
        int green = shared >> 8 & 0xFF;
        int blue = shared & 0xFF;
        int water = center.water ? 1 : 0;
        for (int s = 0; s < extras; s++) {
            Column extra = columnTypeOnly(terrain, (int) PlanetMath.wrapX(planet, Math.round(extraXZ[s * 2])), Math.round(extraXZ[s * 2 + 1]));
            int rgb = columnColor(palette, extra, extra.water ? centerDepth : 0);
            red += rgb >> 16 & 0xFF;
            green += rgb >> 8 & 0xFF;
            blue += rgb & 0xFF;
            water += extra.water ? 1 : 0;
        }
        int samples = 1 + extras;
        int alpha = (int) Math.round(WATER_ALPHA * water / (double) samples);
        return alpha << 24
                | (int) Math.round(red / (double) samples) << 16
                | (int) Math.round(green / (double) samples) << 8
                | (int) Math.round(blue / (double) samples);
    }

    /** Marca de "cor não definida": a coluna usa a paleta por tipo de superfície. */
    private static final int NO_COLOR = -1;

    /** O que a malha precisa saber de uma coluna do terreno. rgb = cor real do mundo (mapa) ou NO_COLOR (paleta). */
    private record Column(int height, SurfaceType type, boolean water, int rgb) {
    }

    /** true se o terreno tem o mapa real do mundo: cor, água e tipo vêm dele em vez da paleta por SurfaceType. */
    private static boolean hasWorldColors(PlanetTerrain terrain) {
        return terrain instanceof PlanetMapTerrain mapTerrain && mapTerrain.hasMap();
    }

    private static Column column(PlanetTerrain terrain, int worldX, long worldZ) {
        PlanetDefinition planet = terrain.planet();
        int maxLat = planet.maxLatitudeBlocks();
        // além do limite de latitude o mundo é a calota polar: repete a última coluna e pinta de gelo
        boolean cap = worldZ > maxLat || worldZ < -maxLat;
        int sampleZ = (int) Math.max(-maxLat, Math.min(maxLat, worldZ));
        int raw = terrain.surfaceHeight(worldX, sampleZ);
        SurfaceType type = capType(terrain, cap, worldX, sampleZ);
        if (hasWorldColors(terrain)) {
            boolean water = !cap && terrain.isWater(worldX, sampleZ);
            return new Column(raw, type, water, cap ? capColor(terrain, worldX, sampleZ, worldZ, maxLat) : terrain.surfaceColor(worldX, sampleZ));
        }
        // água só em planetas com oceano (na Lua uma cratera abaixo do nível de referência continua sendo pó)
        boolean water = planet.hasOcean() && !cap && (type == SurfaceType.OCEAN || raw < planet.seaLevel());
        return new Column(raw, type, water, NO_COLOR);
    }

    /** Amostra extra do supersampling: só o tipo/cor (a altura vem da amostra central), a metade do custo. */
    private static Column columnTypeOnly(PlanetTerrain terrain, int worldX, long worldZ) {
        PlanetDefinition planet = terrain.planet();
        int maxLat = planet.maxLatitudeBlocks();
        boolean cap = worldZ > maxLat || worldZ < -maxLat;
        int sampleZ = (int) Math.max(-maxLat, Math.min(maxLat, worldZ));
        if (hasWorldColors(terrain)) {
            boolean water = !cap && terrain.isWater(worldX, sampleZ);
            int rgb = cap ? capColor(terrain, worldX, sampleZ, worldZ, maxLat) : terrain.surfaceColor(worldX, sampleZ);
            return new Column(planet.seaLevel(), SurfaceType.PLAINS, water, rgb);
        }
        SurfaceType type = capType(terrain, cap, worldX, sampleZ);
        return new Column(planet.seaLevel(), type, planet.hasOcean() && !cap && type == SurfaceType.OCEAN, NO_COLOR);
    }

    /** Cor da calota: começa na cor real da última linha do mapa e vira gelo em degradê (~15% de maxLat além do limite). */
    private static int capColor(PlanetTerrain terrain, int worldX, int sampleZ, long worldZ, int maxLat) {
        int ice = SurfaceColors.of(SurfaceType.POLAR_ICE) & 0xFFFFFF;
        int edge = terrain.surfaceColor(worldX, sampleZ) & 0xFFFFFF;
        double t = Math.min(1.0, (Math.abs((double) worldZ) - maxLat) / (maxLat * 0.15));
        int r = lerp(edge >> 16 & 0xFF, ice >> 16 & 0xFF, t);
        int g = lerp(edge >> 8 & 0xFF, ice >> 8 & 0xFF, t);
        int b = lerp(edge & 0xFF, ice & 0xFF, t);
        return r << 16 | g << 8 | b;
    }

    /** Tipo da coluna: gelo na calota polar da Terra; em planetas sem oceano a calota segue o tipo do próprio terreno. */
    private static SurfaceType capType(PlanetTerrain terrain, boolean cap, int worldX, int sampleZ) {
        if (cap && terrain.planet().hasOcean()) {
            return SurfaceType.POLAR_ICE;
        }
        return terrain.surfaceType(worldX, sampleZ);
    }

    private static int columnColor(Palette palette, Column column, int depth) {
        if (column.water) {
            return waterColor(palette, depth) & 0xFFFFFF;
        }
        return column.rgb != NO_COLOR ? column.rgb & 0xFFFFFF : palette.land()[column.type.ordinal()] & 0xFFFFFF;
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
     * Índices (triângulos) da grade. O segmento seguinte do último é o 0, fechando o anel. Entre anéis com o
     * mesmo número de segmentos são dois triângulos por célula; quando o anel externo tem o dobro, cada segmento
     * do anel interno liga a três vértices do externo (3 triângulos, sem T-junction). A orientação é irrelevante
     * porque o pipeline não faz culling.
     */
    public static void fillIndices(Layout layout, int[] out) {
        if (out.length < layout.indexCount()) {
            throw new IllegalArgumentException("array de índices pequeno");
        }
        int n = 0;
        for (int i = 0; i < layout.rings() - 1; i++) {
            int inner = layout.segments(i);
            int outer = layout.segments(i + 1);
            int innerBase = layout.vertexOffset(i);
            int outerBase = layout.vertexOffset(i + 1);
            for (int j = 0; j < inner; j++) {
                int j1 = (j + 1) % inner;
                if (outer == inner) {
                    out[n++] = innerBase + j;
                    out[n++] = outerBase + j;
                    out[n++] = innerBase + j1;
                    out[n++] = innerBase + j1;
                    out[n++] = outerBase + j;
                    out[n++] = outerBase + j1;
                } else {
                    int f0 = outerBase + 2 * j;
                    int f1 = f0 + 1;
                    int f2 = outerBase + (2 * j + 2) % outer;
                    out[n++] = innerBase + j;
                    out[n++] = f0;
                    out[n++] = f1;
                    out[n++] = innerBase + j;
                    out[n++] = f1;
                    out[n++] = innerBase + j1;
                    out[n++] = innerBase + j1;
                    out[n++] = f1;
                    out[n++] = f2;
                }
            }
        }
    }
}
