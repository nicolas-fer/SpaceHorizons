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
 * Três sistemas de eixos:
 * <ul>
 *   <li>espaço: o referencial da Terra (SolarSystem);</li>
 *   <li>"fixo no corpo": cartesiano com origem no centro e eixos presos ao corpo (lat 0 lon 0 em +X, norte em +Y, leste
 *       em +Z): é o que o mapeamento esférico produz; só depende de (x, y, z) da dimensão, nunca do tempo;</li>
 *   <li>local: os eixos do Minecraft num ponto (east = d/dlon = +X, up = radial = +Y, north = d/dlat = +Z).</li>
 * </ul>
 * Do fixo no corpo ao espaço vai a {@link SolarSystem.Pose} do corpo no instante (rotação e translação); os corpos giram
 * (a Lua gira junto com a órbita), então toda conversão de ou para o espaço leva o relógio {@code clock} (ticks do
 * Overworld, com fração). As versões sem relógio usam a configuração de REFERÊNCIA do corpo (eixos alinhados aos do
 * espaço, a Lua em +X): servem a contas estáticas e testes, nunca ao jogo em andamento.
 *
 * Um vetor do espaço vira coordenadas do Minecraft relativas à câmera por (v·east, v·up, v·north) depois de levado ao
 * fixo no corpo.
 */
public final class SpaceFrame {
    /** Meio passo (ticks) da diferença central da velocidade de um ponto fixo no corpo. */
    private static final double VELOCITY_STEP = 0.5;

    private SpaceFrame() {
    }

    // ------------------------------------------------------------------ fixo no corpo (independente do tempo)

    /** Ponto (x, y, z) da dimensão do corpo → cartesiano fixo no corpo. out = {bx, by, bz}. */
    public static void toBodyFixed(SolarSystem.Body body, double x, double y, double z, double[] out) {
        PlanetDefinition planet = body.planet();
        double r = planet.radius() + (y - planet.seaLevel());
        // seno/cosseno já são periódicos: não precisa de wrapX aqui
        double lon = x / planet.radius();
        double lat = z / planet.radius();
        double cosLat = Math.cos(lat);
        out[0] = r * cosLat * Math.cos(lon);
        out[1] = r * Math.sin(lat);
        out[2] = r * cosLat * Math.sin(lon);
    }

    /** Inverso de toBodyFixed. x fica em [-C/2, C/2]. out = {x, y, z} da dimensão. */
    public static void fromBodyFixed(SolarSystem.Body body, double bx, double by, double bz, double[] out) {
        PlanetDefinition planet = body.planet();
        double r = Math.sqrt(bx * bx + by * by + bz * bz);
        double lat = r < 1.0e-12 ? 0.0 : Math.asin(Math.max(-1.0, Math.min(1.0, by / r)));
        double lon = Math.atan2(bz, bx);
        out[0] = lon * planet.radius();
        out[1] = r - planet.radius() + planet.seaLevel();
        out[2] = lat * planet.radius();
    }

    // ------------------------------------------------------------------ espaço, com a pose do corpo

    /** Posição no espaço de um ponto (x, y, z) da dimensão do corpo na pose dada. out = {sx, sy, sz}. */
    public static void toSpace(SolarSystem.Body body, SolarSystem.Pose pose, double x, double y, double z, double[] out) {
        double[] b = new double[3];
        toBodyFixed(body, x, y, z, b);
        pose.rotate(b[0], b[1], b[2], out);
        out[0] += pose.x();
        out[1] += pose.y();
        out[2] += pose.z();
    }

    /** Posição no espaço, no instante {@code clock}. */
    public static void toSpace(SolarSystem.Body body, double clock, double x, double y, double z, double[] out) {
        toSpace(body, body.poseAt(clock), x, y, z, out);
    }

    /** Idem, na configuração de referência. */
    public static void toSpace(SolarSystem.Body body, double x, double y, double z, double[] out) {
        toSpace(body, body.referencePose(), x, y, z, out);
    }

    /** Inverso de toSpace: ponto no espaço → (x, y, z) na dimensão do corpo. x fica em [-C/2, C/2]. */
    public static void fromSpace(SolarSystem.Body body, SolarSystem.Pose pose, double sx, double sy, double sz, double[] out) {
        double[] b = new double[3];
        pose.unrotate(sx - pose.x(), sy - pose.y(), sz - pose.z(), b);
        fromBodyFixed(body, b[0], b[1], b[2], out);
    }

    public static void fromSpace(SolarSystem.Body body, double clock, double sx, double sy, double sz, double[] out) {
        fromSpace(body, body.poseAt(clock), sx, sy, sz, out);
    }

    public static void fromSpace(SolarSystem.Body body, double sx, double sy, double sz, double[] out) {
        fromSpace(body, body.referencePose(), sx, sy, sz, out);
    }

    /** Ponto do espaço → cartesiano fixo no corpo no instante {@code clock}. out = {bx, by, bz}. */
    public static void spaceToBodyFixed(SolarSystem.Body body, double clock, double sx, double sy, double sz, double[] out) {
        SolarSystem.Pose pose = body.poseAt(clock);
        pose.unrotate(sx - pose.x(), sy - pose.y(), sz - pose.z(), out);
    }

    /** Cartesiano fixo no corpo → ponto do espaço no instante {@code clock}. out = {sx, sy, sz}. */
    public static void bodyFixedToSpace(SolarSystem.Body body, double clock, double bx, double by, double bz, double[] out) {
        SolarSystem.Pose pose = body.poseAt(clock);
        pose.rotate(bx, by, bz, out);
        out[0] += pose.x();
        out[1] += pose.y();
        out[2] += pose.z();
    }

    /** Vetor (direção ou velocidade) do espaço → eixos fixos no corpo. out não pode ser a entrada. */
    public static void vectorToBodyFixed(SolarSystem.Body body, double clock, double vx, double vy, double vz, double[] out) {
        body.poseAt(clock).unrotate(vx, vy, vz, out);
    }

    /** Vetor dos eixos fixos no corpo → eixos do espaço. */
    public static void vectorFromBodyFixed(SolarSystem.Body body, double clock, double bx, double by, double bz, double[] out) {
        body.poseAt(clock).rotate(bx, by, bz, out);
    }

    /**
     * Velocidade (blocos/tick, nos eixos do espaço) do ponto fixo no corpo que neste instante está em (sx, sy, sz): a do
     * centro mais a da rotação sincronizada (ω × r). É a velocidade do "chão" ali; o jogador parado na superfície tem
     * esta velocidade no espaço. Por diferença central do próprio movimento do corpo; zero para a Terra.
     */
    public static void groundVelocity(SolarSystem.Body body, double clock, double sx, double sy, double sz, double[] out) {
        double[] b = new double[3];
        spaceToBodyFixed(body, clock, sx, sy, sz, b);
        double[] before = new double[3];
        double[] after = new double[3];
        bodyFixedToSpace(body, clock - VELOCITY_STEP, b[0], b[1], b[2], before);
        bodyFixedToSpace(body, clock + VELOCITY_STEP, b[0], b[1], b[2], after);
        for (int i = 0; i < 3; i++) {
            out[i] = (after[i] - before[i]) / (2.0 * VELOCITY_STEP);
        }
    }

    /**
     * Velocidade em I (blocos por tick de relógio, eixos inerciais) do ponto fixo no corpo que neste instante está em
     * (ix, iy, iz), um ponto de I: a do centro mais a da rotação ({@code ω × r}). Para a Terra é só a da rotação
     * (~27 blocos/tick no equador, em média) e para a Lua a do centro mais a da rotação sincronizada. Por diferença central
     * do movimento do corpo em I.
     */
    public static void inertialGroundVelocity(SolarSystem.Body body, double clock, double ix, double iy, double iz,
            double[] out) {
        SolarSystem.Pose now = body.inertialPoseAt(clock);
        double[] b = new double[3];
        now.unrotate(ix - now.x(), iy - now.y(), iz - now.z(), b);
        double[] before = new double[3];
        double[] after = new double[3];
        inertialBodyFixedToInertial(body, clock - VELOCITY_STEP, b, before);
        inertialBodyFixedToInertial(body, clock + VELOCITY_STEP, b, after);
        for (int i = 0; i < 3; i++) {
            out[i] = (after[i] - before[i]) / (2.0 * VELOCITY_STEP);
        }
    }

    private static void inertialBodyFixedToInertial(SolarSystem.Body body, double clock, double[] b, double[] out) {
        SolarSystem.Pose pose = body.inertialPoseAt(clock);
        pose.rotate(b[0], b[1], b[2], out);
        out[0] += pose.x();
        out[1] += pose.y();
        out[2] += pose.z();
    }

    // ------------------------------------------------------------------ base local

    /** Base local no ponto (x, z) do corpo: east, up, north (3 doubles cada) em out[0..8], nos eixos fixos no corpo. */
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

    /** Vetor nos eixos fixos no corpo → eixos do Minecraft do ponto (camX, camZ). out = {mcX, mcY, mcZ}. */
    public static void bodyToLocal(SolarSystem.Body body, double camX, double camZ, double bx, double by, double bz,
            double[] out) {
        double[] b = new double[9];
        localBasis(body, camX, camZ, b);
        out[0] = bx * b[0] + by * b[1] + bz * b[2];
        out[1] = bx * b[3] + by * b[4] + bz * b[5];
        out[2] = bx * b[6] + by * b[7] + bz * b[8];
    }

    /** Inverso de bodyToLocal: eixos do Minecraft do ponto → eixos fixos no corpo. */
    public static void localToBody(SolarSystem.Body body, double camX, double camZ, double mx, double my, double mz,
            double[] out) {
        double[] b = new double[9];
        localBasis(body, camX, camZ, b);
        out[0] = mx * b[0] + my * b[3] + mz * b[6];
        out[1] = mx * b[1] + my * b[4] + mz * b[7];
        out[2] = mx * b[2] + my * b[5] + mz * b[8];
    }

    /**
     * Vetor do espaço (ex.: centro de outro corpo menos a posição da câmera) expresso nos eixos do Minecraft da
     * câmera que está em (camX, camZ) do corpo atual, na pose dada. out = {mcX, mcY, mcZ}.
     */
    public static void spaceToLocal(SolarSystem.Body body, SolarSystem.Pose pose, double camX, double camZ, double vx,
            double vy, double vz, double[] out) {
        double[] b = new double[3];
        pose.unrotate(vx, vy, vz, b);
        bodyToLocal(body, camX, camZ, b[0], b[1], b[2], out);
    }

    public static void spaceToLocal(SolarSystem.Body body, double clock, double camX, double camZ, double vx, double vy,
            double vz, double[] out) {
        spaceToLocal(body, body.poseAt(clock), camX, camZ, vx, vy, vz, out);
    }

    public static void spaceToLocal(SolarSystem.Body body, double camX, double camZ, double vx, double vy, double vz,
            double[] out) {
        spaceToLocal(body, body.referencePose(), camX, camZ, vx, vy, vz, out);
    }

    /** Inverso de spaceToLocal: vetor nos eixos do Minecraft da câmera → vetor no espaço. */
    public static void localToSpace(SolarSystem.Body body, SolarSystem.Pose pose, double camX, double camZ, double mx,
            double my, double mz, double[] out) {
        double[] b = new double[3];
        localToBody(body, camX, camZ, mx, my, mz, b);
        pose.rotate(b[0], b[1], b[2], out);
    }

    public static void localToSpace(SolarSystem.Body body, double clock, double camX, double camZ, double mx, double my,
            double mz, double[] out) {
        localToSpace(body, body.poseAt(clock), camX, camZ, mx, my, mz, out);
    }

    public static void localToSpace(SolarSystem.Body body, double camX, double camZ, double mx, double my, double mz,
            double[] out) {
        localToSpace(body, body.referencePose(), camX, camZ, mx, my, mz, out);
    }

    /**
     * Matriz 3x3 (ordem de linhas, 9 doubles) que leva um vetor nos eixos fixos em {@code target} (a esfera do corpo
     * desenhada com o mapeamento esférico) para os eixos do Minecraft da câmera em (camX, camZ) de {@code observer} no
     * instante {@code clock}: base local · R(observer)ᵀ · R(target). Quando observer == target é só a base local.
     */
    public static void meshToLocal(SolarSystem.Body observer, double clock, double camX, double camZ,
            SolarSystem.Body target, double[] out) {
        SolarSystem.Pose observerPose = observer.poseAt(clock);
        SolarSystem.Pose targetPose = target.poseAt(clock);
        double[] column = new double[3];
        double[] inSpace = new double[3];
        double[] local = new double[3];
        for (int j = 0; j < 3; j++) {
            targetPose.rotate(j == 0 ? 1.0 : 0.0, j == 1 ? 1.0 : 0.0, j == 2 ? 1.0 : 0.0, inSpace);
            spaceToLocal(observer, observerPose, camX, camZ, inSpace[0], inSpace[1], inSpace[2], local);
            column[0] = local[0];
            column[1] = local[1];
            column[2] = local[2];
            out[j] = column[0];
            out[3 + j] = column[1];
            out[6 + j] = column[2];
        }
    }
}
