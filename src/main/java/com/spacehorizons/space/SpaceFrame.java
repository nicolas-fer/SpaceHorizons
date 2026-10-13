package com.spacehorizons.space;

import com.spacehorizons.planet.PlanetDefinition;

/**
 * Conversões entre coordenadas de bloco de uma dimensão-planeta e o referencial do espaço (SolarSystem).
 * Java puro, sem estado, double em tudo.
 *
 * Mapeamento ESFÉRICO VERDADEIRO (não o azimutal da curvatura local): lon = x/R, lat = z/R,
 * raio = R + (y - seaLevel). É o mapeamento que a malha orbital já usa em altitude, então outros corpos
 * desenhados com ele batem com o planeta atual visto de longe.
 *
 * Base local num ponto (vetores unitários no espaço): east = d/dlon (+X do Minecraft), north = d/dlat (+Z do
 * Minecraft) e up = radial (+Y do Minecraft). Um vetor no espaço vira coordenadas do Minecraft relativas à
 * câmera por (v·east, v·up, v·north).
 */
public final class SpaceFrame {
    private SpaceFrame() {
    }

    /** Posição no espaço de um ponto (x, y, z) da dimensão do corpo. out = {sx, sy, sz}. */
    public static void toSpace(SolarSystem.Body body, double x, double y, double z, double[] out) {
        PlanetDefinition planet = body.planet();
        double r = planet.radius() + (y - planet.seaLevel());
        // seno/cosseno já são periódicos: não precisa de wrapX aqui
        double lon = x / planet.radius();
        double lat = z / planet.radius();
        double cosLat = Math.cos(lat);
        out[0] = body.x() + r * cosLat * Math.cos(lon);
        out[1] = body.y() + r * Math.sin(lat);
        out[2] = body.z() + r * cosLat * Math.sin(lon);
    }

    /** Inverso de toSpace: ponto no espaço → (x, y, z) na dimensão do corpo. x fica em [-C/2, C/2]. */
    public static void fromSpace(SolarSystem.Body body, double sx, double sy, double sz, double[] out) {
        PlanetDefinition planet = body.planet();
        double dx = sx - body.x();
        double dy = sy - body.y();
        double dz = sz - body.z();
        double r = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double lat = Math.asin(Math.max(-1.0, Math.min(1.0, dy / r)));
        double lon = Math.atan2(dz, dx);
        out[0] = lon * planet.radius();
        out[1] = r - planet.radius() + planet.seaLevel();
        out[2] = lat * planet.radius();
    }

    /** Base local no ponto (x, z) do corpo: east, up, north (3 doubles cada) em out[0..8]. */
    public static void localBasis(SolarSystem.Body body, double x, double z, double[] out) {
        PlanetDefinition planet = body.planet();
        double lon = x / planet.radius();
        double lat = z / planet.radius();
        double sinLat = Math.sin(lat);
        double cosLat = Math.cos(lat);
        double sinLon = Math.sin(lon);
        double cosLon = Math.cos(lon);
        // east
        out[0] = -sinLon;
        out[1] = 0;
        out[2] = cosLon;
        // up
        out[3] = cosLat * cosLon;
        out[4] = sinLat;
        out[5] = cosLat * sinLon;
        // north (+lat)
        out[6] = -sinLat * cosLon;
        out[7] = cosLat;
        out[8] = -sinLat * sinLon;
    }

    /**
     * Vetor do espaço (ex.: centro de outro corpo menos a posição da câmera) expresso nos eixos do Minecraft da
     * câmera que está em (camX, camZ) do corpo atual. out = {mcX, mcY, mcZ}.
     */
    public static void spaceToLocal(SolarSystem.Body body, double camX, double camZ, double vx, double vy, double vz,
            double[] out) {
        double[] b = new double[9];
        localBasis(body, camX, camZ, b);
        out[0] = vx * b[0] + vy * b[1] + vz * b[2];
        out[1] = vx * b[3] + vy * b[4] + vz * b[5];
        out[2] = vx * b[6] + vy * b[7] + vz * b[8];
    }

    /** Inverso de spaceToLocal: vetor nos eixos do Minecraft da câmera → vetor no espaço. */
    public static void localToSpace(SolarSystem.Body body, double camX, double camZ, double mx, double my, double mz,
            double[] out) {
        double[] b = new double[9];
        localBasis(body, camX, camZ, b);
        out[0] = mx * b[0] + my * b[3] + mz * b[6];
        out[1] = mx * b[1] + my * b[4] + mz * b[7];
        out[2] = mx * b[2] + my * b[5] + mz * b[8];
    }
}
