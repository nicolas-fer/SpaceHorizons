package com.spacehorizons.client.devtest;

import java.lang.reflect.Field;
import java.util.List;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.rocket.CockpitCamera;
import com.spacehorizons.client.rocket.RocketKeys;
import com.spacehorizons.client.rocket.RocketPanel;
import com.spacehorizons.client.rocket.RocketPilot;
import com.spacehorizons.client.rocket.RocketTelemetryClient;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketTelemetryPayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Verificações do cenário {@code cockpit} do autoteste (fase B2: cliente do foguete): teclas simuladas, pacote enviado e o que
 * o servidor recebeu, painel com telemetria falsa, câmera acompanhando a atitude. Escreve no log com o prefixo
 * {@code [AUTOTEST] COCKPIT}. Os dados sincronizados do foguete são mexidos SÓ no cliente (reflexão nos acessores privados)
 * para ver os efeitos sem a física de voo do servidor.
 */
final class CockpitDevtest {
    private static final String TAG = "[AUTOTEST] COCKPIT ";

    private CockpitDevtest() {
    }

    private static RocketEntity rocket(Minecraft mc) {
        List<RocketEntity> rockets = mc.level.getEntitiesOfClass(RocketEntity.class, new AABB(RocketDevtest.BASE).inflate(60.0));
        return rockets.isEmpty() ? null : rockets.get(0);
    }

    /** O jogador está pilotando? Loga o estado do cliente depois de sentar. */
    static void boarded(Minecraft mc, String label) {
        RocketEntity r = rocket(mc);
        SpaceHorizons.LOGGER.info("{}{}: pilotando={} câmera={} yaw/pitch do jogador={}/{} acelerador local={} pacotes enviados={}", TAG, label,
                RocketPilot.isPiloting(), mc.options.getCameraType(), mc.player.getYRot(), mc.player.getXRot(), RocketPilot.throttle(),
                RocketPilot.sentCount());
        if (r == null) {
            SpaceHorizons.LOGGER.warn("{}{}: sem entidade de foguete no cliente", TAG, label);
        }
    }

    /** Telemetria falsa de um voo em órbita baixa com plano da Lua e tudo aceso (para o painel ter números). */
    static void pinOrbitTelemetry() {
        RocketTelemetryClient.pinForTest(new RocketTelemetryPayload("earth", true, 842.0, 0.9, 1.2, 3.9, 12340.0, 220.0, 28.5, 2460.0, 1500.0, 4000.0,
                2.5, 1.4, 0.8F, (byte) 1, (byte) 1, 0.0F, 0.0F, (byte) 0, 73.0F, 114.6F, 1.55F, true, true));
        SpaceHorizons.LOGGER.info("{}telemetria falsa FIXADA: órbita baixa, plano da Lua OK, queime agora, aguardando terreno", TAG);
    }

    /** Telemetria falsa de reentrada: descendo, calor, paraquedas abrindo, sem plano. */
    static void pinReentryTelemetry() {
        RocketTelemetryClient.pinForTest(new RocketTelemetryPayload("earth", false, 2310.0, -2.4, 0.6, 3.1, Double.POSITIVE_INFINITY, -420.0, 28.5,
                Double.NaN, 300.0, 4000.0, 0.4, 0.9, 0.0F, (byte) 3, (byte) 1, 1.3F, 0.5F, (byte) -1, 0.0F, 0.0F, 0.0F, false, false));
        SpaceHorizons.LOGGER.info("{}telemetria falsa FIXADA: reentrada (calor, descida rápida, paraquedas, combustível baixo)", TAG);
    }

    static void unpinTelemetry() {
        RocketTelemetryClient.pinForTest(null);
    }

    /** Mexe num dado sincronizado do foguete SÓ no cliente (o servidor não o reenvia se não mudar). */
    @SuppressWarnings("unchecked")
    static void setClientData(Minecraft mc, String field, Object value) {
        RocketEntity r = rocket(mc);
        if (r == null) {
            SpaceHorizons.LOGGER.warn("{}sem foguete para mexer em {}", TAG, field);
            return;
        }
        try {
            Field f = RocketEntity.class.getDeclaredField(field);
            f.setAccessible(true);
            r.getEntityData().set((EntityDataAccessor<Object>) f.get(null), value);
        } catch (ReflectiveOperationException e) {
            SpaceHorizons.LOGGER.error(TAG + "dado " + field + " não encontrado", e);
        }
    }

    /** Aperta uma tecla do foguete (um clique de borda, como o teclado faria). */
    static void click(KeyMapping key) {
        KeyMapping.click(key.getKey());
    }

    /** Aperta uma tecla pelo código, como o teclado: o clique vai a todas as teclas com esse código (vanilla e foguete). */
    static void clickRawKey(int keysym) {
        KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD.getOrCreate(keysym));
    }

    /** Solta todas as teclas simuladas. */
    static void releaseAll(Minecraft mc) {
        mc.options.keyShift.setDown(false);
        mc.options.keySprint.setDown(false);
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        RocketKeys.ROLL_LEFT.setDown(false);
        RocketKeys.ROLL_RIGHT.setDown(false);
    }

    /** O que o cliente mandou e o que o painel mostra agora. */
    static void sent(Minecraft mc, String label) {
        SpaceHorizons.LOGGER.info("{}{}: último pacote={} | último com flag={} | acelerador do painel={} | sentado={} | Shift apertado={} (isShiftKeyDown={}) | tela aberta={}",
                TAG, label, RocketPanel.describe(RocketPilot.lastSent()), RocketPanel.describe(RocketPilot.lastFlagged()), RocketPanel.throttleText(),
                mc.player.getVehicle() != null, mc.options.keyShift.isDown(), mc.player.isShiftKeyDown(),
                mc.gui.screen() == null ? "nenhuma" : mc.gui.screen().getClass().getSimpleName());
    }

    /** O comando que o SERVIDOR guardou do piloto (chegou pela rede). */
    static void serverReceived(Minecraft mc, String label) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().get(0);
            if (player.getVehicle() instanceof RocketEntity r) {
                SpaceHorizons.LOGGER.info("{}{} (SERVIDOR recebeu): {} | veículo do piloto ainda é o foguete=true", TAG, label,
                        RocketPanel.describe(r.flight().control(r.level().getGameTime())));
            } else {
                SpaceHorizons.LOGGER.warn("{}{} (SERVIDOR): o piloto NÃO está mais no foguete (veículo={})", TAG, label, player.getVehicle());
            }
        });
    }

    /** A câmera acompanha o foguete? Compara a direção da câmera com a esperada pela atitude e pelo olhar livre. */
    static void camera(Minecraft mc, String label) {
        RocketEntity r = rocket(mc);
        if (r == null) {
            return;
        }
        Quaternionf attitude = new Quaternionf();
        r.orientation(1.0F, attitude);
        boolean third = !mc.options.getCameraType().isFirstPerson();
        Quaternionf expected = CockpitCamera.cameraRotation(attitude, third, mc.player.getYRot(), mc.player.getXRot());
        Vector3f expectedForward = expected.transform(new Vector3f(0, 0, -1));
        Vector3f cameraForward = new Vector3f(mc.gameRenderer.mainCamera().forwardVector());
        Vector3f nose = attitude.transform(new Vector3f(0, 1, 0));
        Vector3f cameraUp = mc.gameRenderer.mainCamera().rotation().transform(new Vector3f(0, 1, 0));
        Vector3f expectedUp = expected.transform(new Vector3f(0, 1, 0));
        SpaceHorizons.LOGGER.info("{}{}: {} | nariz={} | câmera olha para={} (esperado {}) alinhamento={} | cima da câmera={} (esperado {}) alinhamento={} | "
                        + "inclinação do foguete={}", TAG, label, mc.options.getCameraType(), fmt(nose), fmt(cameraForward), fmt(expectedForward),
                String.format("%.4f", cameraForward.dot(expectedForward)), fmt(cameraUp), fmt(expectedUp), String.format("%.4f", cameraUp.dot(expectedUp)),
                String.format("%.1f", r.tiltDegrees()));
        if (!third && mc.player.getYRot() == 0.0F && mc.player.getXRot() == 0.0F) {
            SpaceHorizons.LOGGER.info("{}{}: primeira pessoa sem olhar livre: a câmera olha ao longo do nariz? alinhamento={}", TAG, label,
                    String.format("%.4f", cameraForward.dot(nose)));
        }
    }

    private static String fmt(Vector3f v) {
        return String.format("(%.3f, %.3f, %.3f)", v.x, v.y, v.z);
    }

    /** O jogador local aparece em algum lugar? Loga as partículas ativas e o número de entidades de foguete. */
    static void effects(Minecraft mc, String label) {
        RocketEntity r = rocket(mc);
        SpaceHorizons.LOGGER.info("{}{}: acelerador sincronizado={} calor={} modo={} combustível={} | partículas ativas={}", TAG, label,
                r == null ? "?" : r.throttle(), r == null ? "?" : r.heat(), r == null ? "?" : r.flightMode(), r == null ? "?" : r.syncedFuelMb(),
                mc.particleEngine.countParticles());
    }
}
