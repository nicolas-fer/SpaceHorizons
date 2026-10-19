package com.spacehorizons.client.planetmesh;

import com.spacehorizons.planet.PlanetDefinition;
import com.spacehorizons.planet.Planets;

/**
 * Qual ponto do mundo aparece em cada direção da malha orbital (Java puro, sem Minecraft).
 *
 * <h2>O problema: "continentes nadando"</h2>
 * Chunks e LODs do DH usam a projeção azimutal equidistante centrada na câmera (CurvatureMath): o terreno em
 * (camX + dx, camZ + dz) aparece a θ = |d| / R do ponto sob a câmera, na direção atan2(dz, dx). Isso é
 * exato perto da câmera, mas o centro da projeção anda junto com ela. Quem olha o planeta inteiro de longe vê o
 * mesmo mapa ser "arrastado" no plano em vez de a esfera girar: o terreno desliza em relação à esfera.
 *
 * Erro medido (script numérico sobre a calota visível, R = 104.304 (655.360 / 2π); deslocamento lateral D da câmera,
 * pior ponto, que fica perto do limbo e perpendicular ao movimento): erro = distância geodésica entre onde o ponto
 * aparece com a câmera em c2 e onde apareceria se a esfera apenas girasse D/R desde c1.
 * <pre>
 *   altitude   raio da calota   D = 500        D = 3.000
 *      600        11.160 bl      1,7 bl (0,02%) 10,5 bl (0,09%)
 *    2.000        20.270 bl      5,7 bl (0,03%) 34 bl (0,17%)
 *    5.000        31.670 bl      14 bl (0,04%)  83 bl (0,26%)
 *   15.000        52.870 bl      38 bl (0,07%)  227 bl (0,43%)
 *   40.000        79.580 bl      83 bl (0,10%)  498 bl (0,63%)
 * </pre>
 * (percentuais do raio da calota). Com a Terra 10x
 * maior o deslizamento a uma mesma altitude caiu uma ordem de grandeza (a distorção azimutal cresce com o quadrado do
 * ângulo da calota, e ele é menor), por isso as altitudes abaixo ficam absolutas (3.000 e 12.000) em vez de crescer
 * com o raio da Terra.
 *
 * <h2>Mitigação</h2>
 * O planeta é definido em lat/lon (equirretangular: lon = x / R, lat = z / R; ver PlanetMath.unitSphere), que é
 * uma esfera FIXA: nela mover a câmera só gira a esfera, nada desliza. Então a malha é uma grade polar na esfera
 * (θ, φ em torno do nadir, o que cobre exatamente a calota visível, sem buracos) e a coordenada do terreno
 * amostrada em cada direção é uma mistura de duas cartas:
 * <ul>
 *   <li>azimutal (mesma dos chunks/DH): x = camX + R·θ·cos φ, z = camZ + R·θ·sin φ;</li>
 *   <li>lat/lon fixa: a direção é girada do referencial da câmera para o do mundo e convertida em (lon, lat).</li>
 * </ul>
 * O peso da carta fixa é {@link #weight}: zero abaixo de {@link #ALTITUDE_START} e perto do nadir (até o alcance
 * do DH), então onde há chunks/LODs a malha é idêntica a eles e perto do chão nada muda; sobe suavemente até 1
 * em {@link #ALTITUDE_FULL} e além do alcance do DH + {@link #RAMP_FRACTION}·R.
 *
 * <h2>Trade-off</h2>
 * A carta lat/lon só coincide com a azimutal, em primeira ordem, no equador: em latitude lat0 ela comprime as
 * formas leste-oeste por cos(lat0) em relação ao plano local dos chunks (um bloco de x vale cos(lat) bloco de
 * arco). Fora do alcance do DH, em órbita, o mundo aparece como a esfera lat/lon "de verdade" (polos
 * convergindo, continentes fixos), mas perto do nadir o plano local é preservado; a faixa de transição estica
 * de forma contínua (sem dobras) de uma carta para a outra. Abaixo de {@link #ALTITUDE_START} o deslizamento
 * medido na tabela é pequeno e a prioridade é a coerência com DH e chunks.
 */
public final class PlanetMeshProjection {
    /** Abaixo desta altitude (blocos sobre o nível do mar) a malha é a carta azimutal pura, igual a DH e chunks. */
    public static final double ALTITUDE_START = 3_000.0;
    /** A partir desta altitude a carta fixa (lat/lon) vale por inteiro fora do alcance do DH. */
    public static final double ALTITUDE_FULL = 12_000.0;
    /**
     * Raio de referência das altitudes acima (o planeta de teste). Em outros planetas as altitudes escalam com o
     * raio: o deslizamento relativo depende de altitude/raio, não da altitude absoluta.
     */
    public static final double REFERENCE_RADIUS = Planets.EARTH.radius();
    /** Largura da rampa espacial, em frações do raio do planeta, depois do alcance do DH. */
    public static final double RAMP_FRACTION = 0.30;

    private PlanetMeshProjection() {
    }

    /** {@link #ALTITUDE_START} para um planeta de raio {@code radius}. */
    public static double altitudeStart(double radius) {
        return ALTITUDE_START * (radius / REFERENCE_RADIUS);
    }

    /** {@link #ALTITUDE_FULL} para um planeta de raio {@code radius}. */
    public static double altitudeFull(double radius) {
        return ALTITUDE_FULL * (radius / REFERENCE_RADIUS);
    }

    /**
     * Peso (0..1) da carta lat/lon fixa para um vértice a {@code arc} blocos de arco do nadir, com a câmera a
     * {@code altitude} blocos do nível do mar e o DH/render distance cobrindo até {@code nearRadius} blocos.
     */
    public static double weight(double radius, double altitude, double arc, double nearRadius) {
        double byAltitude = smoothstep(altitudeStart(radius), altitudeFull(radius), altitude);
        if (byAltitude <= 0.0) {
            return 0.0;
        }
        double bySpace = smoothstep(nearRadius, nearRadius + RAMP_FRACTION * radius, arc);
        return byAltitude * bySpace;
    }

    /** Direção unitária no referencial da câmera (x leste, y para cima, z = +Z do mundo) para (θ, φ) em torno do nadir. */
    public static void direction(double theta, double phi, double[] out) {
        double s = Math.sin(theta);
        out[0] = s * Math.cos(phi);
        out[1] = Math.cos(theta);
        out[2] = s * Math.sin(phi);
    }

    /**
     * Ponto do mundo (x, z em blocos, x ainda não normalizado pelo wrap) que a direção (θ, φ) mostra, misturando
     * as duas cartas com o peso {@code w}. Com w = 0 é exatamente a carta azimutal usada por chunks e LODs.
     */
    public static void terrainPoint(PlanetDefinition planet, double camX, double camZ,
            double theta, double phi, double w, double[] outXZ) {
        double radius = planet.radius();
        double arc = radius * theta;
        double ax = camX + arc * Math.cos(phi);
        double az = camZ + arc * Math.sin(phi);
        if (w <= 0.0) {
            outXZ[0] = ax;
            outXZ[1] = az;
            return;
        }

        double lat0 = camZ / radius;
        double sinLat0 = Math.sin(lat0);
        double cosLat0 = Math.cos(lat0);
        double s = Math.sin(theta);
        double dx = s * Math.cos(phi);
        double dy = Math.cos(theta);
        double dz = s * Math.sin(phi);
        // referencial da câmera -> mundo (inversa da rotação que leva o ponto sob a câmera para o topo)
        double ux = dx;
        double uy = dy * sinLat0 + dz * cosLat0;
        double uz = dy * cosLat0 - dz * sinLat0;
        double lat = Math.asin(Math.max(-1.0, Math.min(1.0, uy)));
        double lonRel = Math.atan2(ux, uz);
        double sx = camX + lonRel * radius;
        double sz = lat * radius;
        if (w >= 1.0) {
            outXZ[0] = sx;
            outXZ[1] = sz;
            return;
        }
        // mistura só faz sentido no mesmo "ramo" de longitude: escolhe a cópia de sx (módulo a circunferência) mais perto de ax
        double circumference = planet.circumferenceBlocks();
        sx += circumference * Math.rint((ax - sx) / circumference);
        outXZ[0] = ax + (sx - ax) * w;
        outXZ[1] = az + (sz - az) * w;
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = Math.max(0.0, Math.min(1.0, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }
}
