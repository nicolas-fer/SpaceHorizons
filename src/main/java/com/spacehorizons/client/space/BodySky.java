package com.spacehorizons.client.space;

import com.spacehorizons.space.SolarSystem;
import com.spacehorizons.space.SpaceFrame;

/**
 * Matemática pura (sem Minecraft) de como os corpos do SolarSystem aparecem no céu de quem está num deles.
 *
 * <h2>Por que não há salto na troca de dimensão</h2>
 * O planeta atual visto de muito alto (malha orbital, PlanetMeshGeometry) e o MESMO planeta visto de outro corpo
 * (esfera do BodyRenderer, BodyMeshGeometry) são a mesma geometria por construção:
 * <ul>
 *   <li>posição: ambos são (R + h)·direção no mapeamento esférico verdadeiro de {@link SpaceFrame} (lon = x/R,
 *       lat = z/R); a malha orbital escreve isso nos eixos da câmera (x leste, y cima, z norte) e o BodyRenderer leva
 *       o vetor do espaço para esses mesmos eixos com {@link SpaceFrame#spaceToLocal};</li>
 *   <li>altura e cor: os dois chamam {@code PlanetMeshGeometry.shadeVertex} com a mesma paleta
 *       ({@code PlanetMeshPalette}) e o mesmo afundamento ({@code SINK}), então relevo, oceano e tons são idênticos;</li>
 *   <li>sombreamento: o shader do corpo (space/body.fsh) copia a fórmula de planetmesh/planet_mesh.fsh.</li>
 * </ul>
 * Só a resolução da grade muda (a malha orbital é adaptativa em torno do nadir; a esfera tem níveis de 32 a 512
 * segmentos). {@code PlanetBodyCoincidenceTest} confere a igualdade vértice a vértice.
 *
 * <h2>Scaled space</h2>
 * Corpos além de {@link #SCALED_SPACE_DISTANCE} passam do far plane. Desenha-se o corpo a essa distância, na mesma
 * direção, com a escala {@link #scaleFactor} aplicada ao raio e à posição: o raio angular e a direção ficam idênticos.
 */
public final class BodySky {
    /** Distância (blocos) acima da qual o corpo é desenhado a esta distância com escala reduzida. */
    public static final double SCALED_SPACE_DISTANCE = 400_000.0;
    /** Tamanho alvo, em pixels, de um segmento da esfera. */
    public static final double TARGET_PIXELS = 1.5;
    public static final int MIN_SEGMENTS = 32;
    public static final int MAX_SEGMENTS = 512;
    /** Quanto o dia (céu claro) apaga um corpo visto de dentro de uma atmosfera, no máximo. */
    public static final double MAX_DAYLIGHT_FADE = 0.65;

    private BodySky() {
    }

    /**
     * Um corpo visto de um ponto: direção unitária (x, y, z), distância ao CENTRO e raio angular aparente do disco.
     * Os eixos da direção dependem de quem chama (eixos do espaço ou eixos locais da câmera).
     */
    public record Apparent(double x, double y, double z, double distance, double angularRadius) {
    }

    /** Direção, distância e raio angular de {@code target} visto de um ponto do espaço; direção nos eixos do espaço. */
    public static Apparent fromSpacePoint(SolarSystem.Body target, double sx, double sy, double sz) {
        return apparent(target, target.x() - sx, target.y() - sy, target.z() - sz, false, null, 0, 0);
    }

    /**
     * Idem, para uma câmera na posição (x, y, z) da dimensão de {@code observer}; a direção sai nos eixos locais
     * da câmera (x leste, y para cima, z norte), os mesmos da malha orbital e dos shaders. Se o alvo é o próprio
     * observador, a direção aponta para o nadir (0, -1, 0).
     */
    public static Apparent fromDimension(SolarSystem.Body observer, double x, double y, double z, SolarSystem.Body target) {
        double[] cam = new double[3];
        SpaceFrame.toSpace(observer, x, y, z, cam);
        return apparent(target, target.x() - cam[0], target.y() - cam[1], target.z() - cam[2], true, observer, x, z);
    }

    private static Apparent apparent(SolarSystem.Body target, double vx, double vy, double vz, boolean local,
            SolarSystem.Body observer, double camX, double camZ) {
        double[] v = {vx, vy, vz};
        if (local) {
            SpaceFrame.spaceToLocal(observer, camX, camZ, vx, vy, vz, v);
        }
        double distance = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        double radius = target.planet().radius();
        double angular = distance <= radius ? Math.PI / 2.0 : Math.asin(radius / distance);
        if (distance < 1.0e-9) {
            return new Apparent(0.0, -1.0, 0.0, 0.0, angular);
        }
        return new Apparent(v[0] / distance, v[1] / distance, v[2] / distance, distance, angular);
    }

    /** Escala do scaled space: 1 até {@code maxDistance}, depois maxDistance/distância. */
    public static double scaleFactor(double distance, double maxDistance) {
        return distance <= maxDistance ? 1.0 : maxDistance / distance;
    }

    /** Segmentos "ideais" (contínuo) para um corpo de raio {@code radius} a {@code distance}, com {@code pixelAngle} rad por pixel. */
    public static double requiredSegments(double radius, double distance, double pixelAngle) {
        double angle = pixelAngle > 0 ? pixelAngle : 2.0 * Math.tan(Math.toRadians(35.0)) / 1080.0;
        return 2.0 * Math.PI * radius / (TARGET_PIXELS * angle * Math.max(1.0, distance));
    }

    /**
     * Nível (potência de 2 entre MIN e MAX) para o valor contínuo {@code required}. Com histerese: se o nível anterior
     * {@code previous} (0 = nenhum) ainda atende (até 1,5 vez mais fino que o necessário ou 40% mais grosso), fica nele.
     */
    public static int chooseSegments(int previous, double required) {
        if (previous > 0 && required > previous * 0.6 && required <= previous * 1.5) {
            return previous;
        }
        int segments = MIN_SEGMENTS;
        while (segments < MAX_SEGMENTS && segments < required) {
            segments <<= 1;
        }
        return segments;
    }

    /**
     * Fator de opacidade de um corpo visto de dentro de uma atmosfera: o céu claro de dia o apaga (sem passar de
     * {@link #MAX_DAYLIGHT_FADE}); no espaço (skyBrightness 0) vale 1. {@code sunHeight} = componente "para cima" do sol.
     */
    public static double daylightFade(double skyBrightness, double sunHeight) {
        double t = Math.max(0.0, Math.min(1.0, (sunHeight + 0.10) / 0.38));
        double day = t * t * (3.0 - 2.0 * t);
        return 1.0 - MAX_DAYLIGHT_FADE * day * Math.max(0.0, Math.min(1.0, skyBrightness));
    }

    /**
     * true se o disco (direção local {@code dir}, raio angular {@code angularRadius}) pode aparecer num frustum com
     * a direção de visada {@code forward} e meio-ângulo diagonal {@code halfDiagonal} (rad).
     */
    public static boolean inFrustum(double dirX, double dirY, double dirZ, double forwardX, double forwardY, double forwardZ,
            double halfDiagonal, double angularRadius) {
        double cos = dirX * forwardX + dirY * forwardY + dirZ * forwardZ;
        double limit = halfDiagonal + angularRadius;
        return limit >= Math.PI || cos >= Math.cos(limit);
    }
}
