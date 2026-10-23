package com.spacehorizons.client.devtest;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.NativeImage;
import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.rocket.CockpitCamera;
import com.spacehorizons.client.space.VesselClient;
import com.spacehorizons.client.space.VesselRenderer;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.ship.VesselDev;
import com.spacehorizons.space.SpaceDimension;
import com.spacehorizons.space.vessel.VesselEntry;
import com.spacehorizons.space.vessel.VesselHull;
import com.spacehorizons.space.vessel.VesselPose;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import org.jspecify.annotations.Nullable;

/**
 * Só em dev (autoteste {@code shipsee}, fase 2d das naves): as outras naves e foguetes no céu, conferidos contra o servidor. Para cada visada: o
 * observador e o alvo vêm do servidor integrado (posição em E, atitude, casco), a posição esperada na tela sai da MESMA câmera que desenha o quadro
 * (rotação do {@link Camera} e projeção do renderer) aplicada ao casco do alvo, e a medida vem de pixels: dois quadros iguais, um com os veículos desligados
 * e um com eles, e a caixa e o centro do que mudou. Nada daqui roda fora do autoteste.
 */
final class ShipSeeDevtest {
    private static final String TAG = "[AUTOTEST] SEE ";

    private ShipSeeDevtest() {
    }

    // ---------------------------------------------------------------- captura

    private static volatile int[] pixels;
    private static volatile int width;
    private static volatile int height;
    private static volatile boolean ready;

    /** Pede o quadro atual do alvo principal; {@link #captured} avisa quando chegou. */
    static void capture(Minecraft mc) {
        ready = false;
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), 1, image -> {
            try (NativeImage copy = image) {
                pixels = copy.getPixelsABGR().clone();
                width = copy.getWidth();
                height = copy.getHeight();
            }
            ready = true;
        });
    }

    static boolean captured() {
        return ready;
    }

    record Frame(int w, int h, int[] px) {
    }

    static Frame take() {
        return new Frame(width, height, pixels);
    }

    // ---------------------------------------------------------------- o que se mede

    /** A caixa do que mudou entre dois quadros (pixels, topo esquerdo na origem) e quantos pixels mudaram. */
    record Box(double minX, double minY, double maxX, double maxY, int count, double centerX, double centerY) {
        double width() {
            return this.maxX - this.minX + 1.0;
        }

        double height() {
            return this.maxY - this.minY + 1.0;
        }
    }

    /** O que mudou entre dois quadros dentro de {@code region} (null: o quadro todo); pixels fora dela, como a borda da Terra que andou, não contam. */
    static @Nullable Box diff(Frame base, Frame with, int threshold, @Nullable Box region) {
        int x0 = region == null ? 0 : (int) Math.max(0, Math.floor(region.minX()));
        int x1 = region == null ? with.w() - 1 : (int) Math.min(with.w() - 1, Math.ceil(region.maxX()));
        int y0 = region == null ? 0 : (int) Math.max(0, Math.floor(region.minY()));
        int y1 = region == null ? with.h() - 1 : (int) Math.min(with.h() - 1, Math.ceil(region.maxY()));
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -1;
        double maxY = -1;
        double sumX = 0;
        double sumY = 0;
        int count = 0;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int a = base.px()[y * base.w() + x];
                int b = with.px()[y * with.w() + x];
                int d = Math.abs((a & 0xFF) - (b & 0xFF)) + Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)) + Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF));
                if (d > threshold) {
                    count++;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    sumX += x;
                    sumY += y;
                }
            }
        }
        return count == 0 ? null : new Box(minX, minY, maxX, maxY, count, sumX / count, sumY / count);
    }

    // ---------------------------------------------------------------- o que se espera

    /** Observador e alvo de uma visada, em dados puros (posições em E, atitudes do servidor e o casco do alvo). */
    record Setup(String label, double[] observerPos, double[] observerModel, UUID targetId, double[] targetPos, double[] targetAttitude, VesselHull hull) {
    }

    private static final Map<UUID, VesselHull> HULLS = new HashMap<>();

    private static VesselHull hullOf(Minecraft mc, UUID target) {
        return HULLS.computeIfAbsent(target, id -> ShipDevtest.onServer(mc, server -> VesselDev.hull(server, id)));
    }

    /** Quem olha é a nave {@code shipId} no estaleiro (o céu dela tem a atitude da nave). */
    static Setup fromShip(Minecraft mc, String label, int shipId, UUID target) {
        UUID self = VesselEntry.shipUuid(shipId);
        VesselEntry[] e = ShipDevtest.onServer(mc, server -> new VesselEntry[] {VesselDev.entry(server, self), VesselDev.entry(server, target)});
        return new Setup(label, e[0].pos(), VesselPose.modelToSpace(e[0].attitude()), target, e[1].pos(), e[1].attitude(), hullOf(mc, target));
    }

    /** Quem olha é um foguete estacionado (o céu dele tem a atitude identidade; a câmera é que gira com o foguete). */
    static Setup fromRocket(Minecraft mc, String label, UUID rocket, UUID target) {
        VesselEntry[] e = ShipDevtest.onServer(mc, server -> new VesselEntry[] {VesselDev.entry(server, rocket), VesselDev.entry(server, target)});
        return new Setup(label, e[0].pos(), VesselPose.modelToSpace(VesselPose.identityAttitude()), target, e[1].pos(), e[1].attitude(), hullOf(mc, target));
    }

    /** Quem olha é o jogador livre na dimensão de espaço: a posição é a da câmera. */
    static Setup fromFree(Minecraft mc, String label, UUID target) {
        VesselEntry e = ShipDevtest.onServer(mc, server -> VesselDev.entry(server, target));
        double[] pos = new double[3];
        var eye = mc.gameRenderer.mainCamera().position();
        SpaceDimension.toSpace(eye.x, eye.y, eye.z, pos);
        return new Setup(label, pos, VesselPose.modelToSpace(VesselPose.identityAttitude()), target, e.pos(), e.attitude(), hullOf(mc, target));
    }

    /** O vetor ao pivô do alvo nos eixos locais do observador. */
    static double[] centerVector(Setup s) {
        double[] c = new double[3];
        VesselPose.relative(s.observerPos(), s.observerModel(), s.targetPos(), VesselPose.modelToSpace(s.targetAttitude()), c, new double[9]);
        return c;
    }

    /** Todos os vértices do casco do alvo nos eixos locais do observador. */
    static double[][] hullPoints(Setup s) {
        double[] c = new double[3];
        double[] r = new double[9];
        VesselPose.relative(s.observerPos(), s.observerModel(), s.targetPos(), VesselPose.modelToSpace(s.targetAttitude()), c, r);
        VesselHull h = s.hull();
        double[][] out = new double[h.cellCount() * 8][];
        int n = 0;
        for (int i = 0; i < h.cellCount(); i++) {
            int at = h.cellIndex(i);
            int x = at % h.sizeX;
            int z = (at / h.sizeX) % h.sizeZ;
            int y = at / (h.sizeX * h.sizeZ);
            for (int k = 0; k < 8; k++) {
                double px = (x + (k & 1)) * h.scale - h.pivotX;
                double py = (y + ((k >> 1) & 1)) * h.scale - h.pivotY;
                double pz = (z + ((k >> 2) & 1)) * h.scale - h.pivotZ;
                out[n++] = new double[] {c[0] + r[0] * px + r[1] * py + r[2] * pz, c[1] + r[3] * px + r[4] * py + r[5] * pz, c[2] + r[6] * px + r[7] * py + r[8] * pz};
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- câmera e projeção

    /** A rotação da câmera do quadro (eixos da câmera → eixos locais do observador). */
    static Quaternionf cameraRotation(Minecraft mc) {
        return new Quaternionf(mc.gameRenderer.mainCamera().rotation());
    }

    /** O ponto (eixos locais) na tela, em pixels (topo esquerdo na origem); null atrás da câmera. */
    static double @Nullable [] project(double[] p, Quaternionf cameraRotation, float m00, float m11, int w, int h) {
        Vector3f v = new Quaternionf(cameraRotation).conjugate().transform((float) p[0], (float) p[1], (float) p[2], new Vector3f());
        double forward = -v.z;
        if (forward <= 1.0e-6) {
            return null;
        }
        double ndcX = m00 * v.x / forward;
        double ndcY = m11 * v.y / forward;
        return new double[] {(ndcX + 1.0) * 0.5 * w, (1.0 - ndcY) * 0.5 * h};
    }

    /** A caixa esperada do alvo na tela (todos os vértices do casco projetados), ou null se algum vértice está atrás da câmera. */
    static @Nullable Box expectedBox(Setup s, Quaternionf cameraRotation, float m00, float m11, int w, int h) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (double[] p : hullPoints(s)) {
            double[] px = project(p, cameraRotation, m00, m11, w, h);
            if (px == null) {
                return null;
            }
            minX = Math.min(minX, px[0]);
            maxX = Math.max(maxX, px[0]);
            minY = Math.min(minY, px[1]);
            maxY = Math.max(maxY, px[1]);
        }
        return new Box(minX, minY, maxX, maxY, 0, (minX + maxX) * 0.5, (minY + maxY) * 0.5);
    }

    /** O ângulo (graus) entre o olhar e o raio que passa pelo pixel (px, py). */
    static double angleFromCenter(double px, double py, float m00, float m11, int w, int h) {
        double nx = (px / w * 2.0 - 1.0) / m00;
        double ny = (1.0 - py / h * 2.0) / m11;
        return Math.toDegrees(Math.atan(Math.hypot(nx, ny)));
    }

    // ---------------------------------------------------------------- apontar a câmera

    /**
     * Põe o yaw e o pitch do jogador para a câmera olhar na direção {@code target} (eixos locais do observador), somados de {@code yawOffset} e
     * {@code pitchOffset} (graus). Para o piloto de um foguete, a câmera é {@code atitude · base · olhar livre} (CockpitCamera): resolve o olhar livre que leva
     * o eixo da câmera ao alvo.
     */
    static void aim(Minecraft mc, double[] target, double yawOffset, double pitchOffset) {
        double n = Math.sqrt(target[0] * target[0] + target[1] * target[1] + target[2] * target[2]);
        double tx = target[0] / n;
        double ty = target[1] / n;
        double tz = target[2] / n;
        if (mc.player.getVehicle() instanceof RocketEntity rocket) {
            Quaternionf attitude = new Quaternionf();
            rocket.orientation(0.0F, attitude);
            boolean third = !mc.options.getCameraType().isFirstPerson();
            Quaternionf toCamera = new Quaternionf(attitude).mul(CockpitCamera.baseView(third)).conjugate();
            Vector3f u = toCamera.transform((float) tx, (float) ty, (float) tz, new Vector3f());
            double pitch = -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, u.y))));
            double yaw = Math.toDegrees(Math.atan2(u.x, -u.z));
            mc.player.setYRot((float) (yaw + yawOffset));
            mc.player.setXRot((float) (pitch + pitchOffset));
            return;
        }
        double yaw = Math.toDegrees(Math.atan2(-tx, tz));
        double pitch = -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, ty))));
        mc.player.setYRot((float) (yaw + yawOffset));
        mc.player.setXRot((float) (pitch + pitchOffset));
    }

    // ---------------------------------------------------------------- avaliação

    /** Compara o que mudou na tela com o que se esperava e registra as verificações. */
    static void evaluate(Minecraft mc, String name, Setup s, Frame base, Frame with, boolean expectFullyVisible) {
        float[] proj = VesselRenderer.lastProjection();
        float m00 = proj[0];
        float m11 = proj[1];
        Quaternionf camera = cameraRotation(mc);
        Box expected = expectedBox(s, camera, m00, m11, with.w(), with.h());
        Box region = expected == null ? null : new Box(expected.minX() - 8.0, expected.minY() - 8.0, expected.maxX() + 8.0, expected.maxY() + 8.0, 0, 0, 0);
        Box measured = diff(base, with, 40, region);
        double[] c = centerVector(s);
        double distance = Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]);
        Vector3f forward = new Quaternionf(camera).transform(0.0F, 0.0F, -1.0F, new Vector3f());
        double dot = (c[0] * forward.x + c[1] * forward.y + c[2] * forward.z) / distance;
        double expectedAngle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
        SpaceHorizons.LOGGER.info(TAG + "{} câmera: yaw={} pitch={} desenhados={} visto={}", name, f(mc.player.getYRot()), f(mc.player.getXRot()), VesselRenderer.drawnLastFrame(),
                VesselRenderer.lastSeen().stream().map(v -> String.format(Locale.ROOT, "%s d=%.1f hull=%s", v.id().toString().substring(0, 8), v.distance(), v.hull())).toList());
        String head = String.format(Locale.ROOT, "%s: distância %.1f blocos, a %.2f graus do centro do olhar", name, distance, expectedAngle);
        if (expected == null || measured == null) {
            ShipDevtest.check(name + " aparece no quadro", false, head + String.format(Locale.ROOT, "; esperado=%s medido=%s", expected, measured));
            return;
        }
        double measuredAngle = angleFromCenter(measured.centerX(), measured.centerY(), m00, m11, with.w(), with.h());
        double expectedCenterAngle = angleFromCenter(expected.centerX(), expected.centerY(), m00, m11, with.w(), with.h());
        double dx = measured.centerX() - expected.centerX();
        double dy = measured.centerY() - expected.centerY();
        SpaceHorizons.LOGGER.info(TAG + "{} | esperado: centro ({}, {}) caixa {}x{} px, ângulo ao centro {} graus | medido: centro ({}, {}) caixa {}x{} px, {} pixels, ângulo {} graus | erro "
                + "do centro ({}, {}) px", head, f(expected.centerX()), f(expected.centerY()), f(expected.width()), f(expected.height()), f(expectedCenterAngle), f(measured.centerX()),
                f(measured.centerY()), f(measured.width()), f(measured.height()), measured.count(), f(measuredAngle), f(dx), f(dy));
        ShipDevtest.check(name + " aparece no quadro (pixels que mudaram)", measured.count() >= 30, "pixels=" + measured.count());
        // o centro da caixa medida cai na esperada: a posição relativa, a atitude e a escala estão certas
        double tolerance = 4.0 + 0.03 * Math.max(expected.width(), expected.height());
        if (expectFullyVisible) {
            ShipDevtest.check(name + ": a caixa na tela bate com a esperada (centro a menos de " + f(tolerance) + " px; largura e altura a menos de 10% + 4 px)",
                    Math.abs(dx) <= tolerance && Math.abs(dy) <= tolerance && Math.abs(measured.width() - expected.width()) <= 4.0 + 0.10 * expected.width()
                            && Math.abs(measured.height() - expected.height()) <= 4.0 + 0.10 * expected.height(),
                    String.format(Locale.ROOT, "erro do centro (%.1f, %.1f) px; largura %.1f contra %.1f; altura %.1f contra %.1f", dx, dy, measured.width(), expected.width(),
                            measured.height(), expected.height()));
        } else {
            // parte do veículo pode estar atrás do casco da nave de quem olha: o que se vê tem de ficar DENTRO da caixa esperada
            ShipDevtest.check(name + ": o que se vê fica dentro da caixa esperada (a menos de 4 px) e o ângulo do centro visto bate com o esperado (a menos de 1,5 graus)",
                    measured.minX() >= expected.minX() - 4.0 && measured.maxX() <= expected.maxX() + 4.0 && measured.minY() >= expected.minY() - 4.0
                            && measured.maxY() <= expected.maxY() + 4.0 && Math.abs(measuredAngle - expectedCenterAngle) <= 1.5,
                    String.format(Locale.ROOT, "medido x %.0f..%.0f y %.0f..%.0f; esperado x %.0f..%.0f y %.0f..%.0f", measured.minX(), measured.maxX(), measured.minY(),
                            measured.maxY(), expected.minX(), expected.maxX(), expected.minY(), expected.maxY()));
        }
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    // ---------------------------------------------------------------- leituras do cliente

    static boolean spaceAndRiding(Minecraft mc) {
        return SpaceDimension.is(mc.level) && mc.player.getVehicle() instanceof RocketEntity;
    }

    static String clientSummary() {
        return String.format(Locale.ROOT, "veículos=%d pacotes=%d cascos=%d desenhados=%d", VesselClient.count(), VesselClient.packetCount(), VesselClient.hullsReceived(),
                VesselRenderer.drawnLastFrame());
    }
}
