package com.spacehorizons.client.ship;

import java.util.function.DoubleSupplier;

import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.rocket.CockpitCamera;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.ship.deck.PoseBlend;
import com.spacehorizons.ship.deck.SeamJump;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * As trocas sem costura da câmera (fase 2g). A câmera tem fontes diferentes conforme o jogador está a pé no mundo, a pé no convés de uma nave ou
 * sentado num assento (o olho do vanilla, o olho derivado da pose da nave, a janela do cockpit); trocar de uma para outra (embarcar, sair, sentar,
 * levantar, ancorar, desancorar, pousar, decolar) muda a posição e a rotação de um quadro para o outro: um pulo. Aqui a câmera que o jogo
 * acabaria desenhando neste quadro (a "natural") é comparada com a que foi desenhada no quadro anterior e, na troca, a diferença é guardada e
 * DESCE a zero em {@link #BLEND_TICKS} ticks ({@link PoseBlend}): a câmera desliza do olho de antes para o de depois, sem salto.
 *
 * <p>A troca é detectada pela assinatura do jogador (em que nave ele está no convés, em que veículo está sentado). A rotação é misturada no evento de
 * ângulos da câmera (o último a rodar) e a posição no fim de {@code Camera.update} (mixin). Trocar de mundo (dimensão) não mistura: os dois olhos não
 * têm relação.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class CameraSeam {
    /** Duração mínima da mistura (ticks do cliente): 1,5 segundo, curva suave. */
    public static final double BLEND_TICKS = 30.0;
    /** A mistura mais longa (ticks): diferenças grandes (um olhar a 130 graus, 20 blocos) levam mais tempo para não passar de 1 grau ou 0,1 bloco por quadro. */
    private static final double MAX_BLEND_TICKS = 60.0;
    /** Acima desta diferença (blocos) a troca é um teletransporte de verdade (outro lugar), e não se mistura. */
    private static final double MAX_BLEND_DISTANCE = 1_000.0;

    /** Depois de uma troca a câmera natural ainda pode dar um tranco próprio (os dados do assento e o olhar do passageiro chegam alguns ticks depois do pacote de montar): nesta janela (ticks) um tranco também é misturado. */
    private static final double GRACE_TICKS = 40.0;
    /** Um passo de câmera de um quadro para o outro é tranco (e não movimento) se passa disto e de 4 vezes o passo do quadro anterior. */
    private static final double SPIKE_DEGREES = 5.0;
    private static final double SPIKE_BLOCKS = 0.5;

    private static final PoseBlend BLEND = new PoseBlend();
    private static final Quaterniond LAST_NATURAL_ROTATION = new Quaterniond();
    private static final Vector3d LAST_NATURAL_POSITION = new Vector3d();
    private static boolean haveNaturalRotation;
    private static boolean haveNaturalPosition;
    private static double lastRotationStep;
    private static double lastPositionStep;
    private static double graceUntil;
    private static final Vector3d PREVIOUS_POSITION = new Vector3d();
    private static final Quaterniond PREVIOUS_ROTATION = new Quaterniond();
    private static boolean havePosition;
    private static boolean haveRotation;
    private static boolean pendingPosition;
    private static boolean pendingRotation;
    private static @Nullable ClientLevel lastLevel;
    private static int lastDeckRocket = -1;
    private static int lastVehicle = -1;
    private static int blends;

    private CameraSeam() {
    }

    /** Quantas trocas foram misturadas desde o começo (diagnóstico do autoteste). */
    public static int blendCount() {
        return blends;
    }

    private static double clock(Minecraft mc) {
        DoubleSupplier c = RocketEntity.clientClock;
        return c != null ? c.getAsDouble() : (mc.level == null ? 0.0 : mc.level.getGameTime()) + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    private static void reset() {
        BLEND.clear();
        havePosition = false;
        haveRotation = false;
        pendingPosition = false;
        pendingRotation = false;
        haveNaturalRotation = false;
        haveNaturalPosition = false;
        graceUntil = 0.0;
    }

    /** Detecta a troca de fonte da câmera deste quadro (muda a assinatura do jogador) e marca a mistura. */
    private static void detectSwap(Minecraft mc) {
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }
        RocketEntity deck = DeckClient.rocket();
        int deckRocket = deck == null ? -1 : deck.getId();
        Entity vehicle = mc.player == null ? null : mc.player.getVehicle();
        int vehicleId = vehicle == null ? -1 : vehicle.getId();
        if (deckRocket != lastDeckRocket || vehicleId != lastVehicle) {
            int lastDeckRocketOld = lastDeckRocket;
            int lastVehicleOld = lastVehicle;
            lastDeckRocket = deckRocket;
            lastVehicle = vehicleId;
            if (havePosition || haveRotation) {
                pendingPosition = true;
                pendingRotation = true;
                graceUntil = clock(mc) + GRACE_TICKS;
                blends++;
                SpaceHorizons.LOGGER.info("[seam] troca de fonte da câmera: no convés da nave {} -> {}, veículo {} -> {}", lastDeckRocketOld, deckRocket, lastVehicleOld, vehicleId);
            }
        }
    }

    /** A rotação da câmera: roda por último no evento de ângulos e mistura a troca. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }
        detectSwap(mc);
        if (mc.options.getCameraType().isMirrored()) {
            haveRotation = false;
            return;
        }
        double clock = clock(mc);
        Quaternionf q = CockpitCamera.fromVanilla(event.getYaw(), event.getPitch(), event.getRoll());
        Quaterniond natural = new Quaterniond(q.x, q.y, q.z, q.w);
        if (haveNaturalRotation) {
            double step = SeamJump.angleDegrees(LAST_NATURAL_ROTATION, natural);
            if (clock < graceUntil && step > Math.max(SPIKE_DEGREES, 4.0 * lastRotationStep)) {
                pendingRotation = true;
                blends++;
                SpaceHorizons.LOGGER.info("[seam] tranco da câmera de {} graus (passo anterior {}) misturado", step, lastRotationStep);
            }
            lastRotationStep = step;
        }
        LAST_NATURAL_ROTATION.set(natural);
        haveNaturalRotation = true;
        if (pendingRotation && haveRotation) {
            BLEND.beginRotation(clock, Math.min(MAX_BLEND_TICKS, Math.max(BLEND_TICKS, 0.75 * SeamJump.angleDegrees(PREVIOUS_ROTATION, natural))), PREVIOUS_ROTATION,
                    natural);
        }
        pendingRotation = false;
        if (BLEND.rotationActive(clock)) {
            Quaterniond drawn = BLEND.rotation(clock, natural, new Quaterniond());
            float[] angles = CockpitCamera.toVanilla(new Quaternionf((float) drawn.x, (float) drawn.y, (float) drawn.z, (float) drawn.w));
            event.setYaw(angles[0]);
            event.setPitch(angles[1]);
            event.setRoll(angles[2]);
            PREVIOUS_ROTATION.set(drawn);
        } else {
            PREVIOUS_ROTATION.set(natural);
        }
        haveRotation = true;
    }

    /**
     * A posição da câmera: chamado no fim de {@code Camera.update} (mixin) com a posição final do quadro. Devolve a posição com a mistura da troca, ou
     * null se não há o que ajustar (a câmera fica como está).
     */
    public static @Nullable Vec3 adjustPosition(Vec3 natural) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return null;
        }
        double clock = clock(mc);
        Vector3d n = new Vector3d(natural.x, natural.y, natural.z);
        if (haveNaturalPosition) {
            double step = LAST_NATURAL_POSITION.distance(n);
            if (clock < graceUntil && step > Math.max(SPIKE_BLOCKS, 4.0 * lastPositionStep)) {
                pendingPosition = true;
                blends++;
                SpaceHorizons.LOGGER.info("[seam] tranco da câmera de {} blocos (passo anterior {}) misturado", step, lastPositionStep);
            }
            lastPositionStep = step;
        }
        LAST_NATURAL_POSITION.set(n);
        haveNaturalPosition = true;
        if (pendingPosition && havePosition) {
            if (PREVIOUS_POSITION.distance(n) <= MAX_BLEND_DISTANCE) {
                BLEND.beginPosition(clock, Math.min(MAX_BLEND_TICKS, Math.max(BLEND_TICKS, 3.0 * PREVIOUS_POSITION.distance(n))), PREVIOUS_POSITION, n);
            }
        }
        pendingPosition = false;
        havePosition = true;
        if (BLEND.positionActive(clock)) {
            Vector3d drawn = BLEND.position(clock, n, new Vector3d());
            PREVIOUS_POSITION.set(drawn);
            return new Vec3(drawn.x, drawn.y, drawn.z);
        }
        PREVIOUS_POSITION.set(n);
        return null;
    }
}
