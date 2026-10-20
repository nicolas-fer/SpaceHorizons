package com.spacehorizons.client.rocket;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketPortrait;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A câmera do piloto, alinhada à atitude do foguete (a conta está em {@link CockpitCamera}, testada em ida e volta).
 *
 * <ul>
 *   <li>{@link ViewportEvent.ComputeCameraAngles}: com o jogador num {@link RocketEntity}, troca yaw/pitch/roll do vanilla
 *       pelos que reproduzem {@code atitude · base · olhar livre}. A atitude é a MESMA interpolada que o renderer usa
 *       ({@link RocketEntity#orientation}), então a câmera e o desenho do foguete nunca divergem. O olhar livre é o yaw e o
 *       pitch do próprio jogador, que o mouse já move (o vanilla não precisa de gancho em {@code onPassengerTurned}).</li>
 *   <li>{@link CalculateDetachedCameraDistanceEvent}: a distância em terceira pessoa é proporcional ao tamanho do retrato.</li>
 *   <li>{@link #firstPersonEye} (chamado por {@code CameraMixin}): na primeira pessoa o olho vai para a janela do cockpit, fora do
 *       casco, em vez de ficar dentro do bloco;</li>
 *   <li>{@link RenderHandEvent}: sem mão nem item na primeira pessoa enquanto se pilota;</li>
 *   <li>{@link RenderPlayerEvent.Pre}: não desenha o jogador sentado num foguete. O modelo do jogador é uma entidade à parte
 *       e fica sempre em pé; com o foguete girando ele atravessaria o casco e apareceria deitado fora dele. O piloto é visto
 *       só pelos efeitos do foguete e pela câmera, e por isso também some para os outros jogadores.</li>
 * </ul>
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class RocketCamera {
    private RocketCamera() {
    }

    /** O foguete em que a câmera do jogador local está montada, ou null. */
    private static RocketEntity cameraRocket(Minecraft mc) {
        Entity camera = mc.getCameraEntity();
        return camera instanceof LocalPlayer player && player.getVehicle() instanceof RocketEntity rocket ? rocket : null;
    }

    /**
     * Onde fica o olho da câmera em primeira pessoa do piloto (mundo), ou null se não vale (não pilota, ou terceira pessoa). Usa a
     * mesma posição e atitude interpoladas que o renderer do foguete, então o olho acompanha o desenho sem tremer.
     */
    public static Vec3 firstPersonEye(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        RocketEntity rocket = cameraRocket(mc);
        if (rocket == null || !mc.options.getCameraType().isFirstPerson()) {
            return null;
        }
        RocketPortrait portrait = rocket.portrait();
        if (portrait.isEmpty()) {
            return null;
        }
        Direction front = portrait.get(portrait.cockpit()).getValueOrElse(com.spacehorizons.rocket.RocketCockpitBlock.FACING, Direction.NORTH);
        Vector3f local = new Vector3f(portrait.cockpit().getX() + 0.5F, portrait.cockpit().getY() + 0.5F, portrait.cockpit().getZ() + 0.5F)
                .sub(rocket.pivotLocal()).add(CockpitCamera.windowEye(front.getStepX(), front.getStepZ()));
        Quaternionf attitude = new Quaternionf();
        rocket.orientation(partialTick, attitude);
        attitude.transform(local);
        return rocket.getPosition(partialTick).add(local.x(), local.y(), local.z());
    }

    /** Sem mão nem item do jogador na primeira pessoa: ele pilota, e a mão apareceria flutuando dentro do cockpit. */
    @SubscribeEvent
    static void onRenderHand(RenderHandEvent event) {
        if (cameraRocket(Minecraft.getInstance()) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        RocketEntity rocket = cameraRocket(mc);
        if (rocket == null) {
            return;
        }
        Quaternionf attitude = new Quaternionf();
        rocket.orientation((float) event.getPartialTick(), attitude);
        boolean third = !mc.options.getCameraType().isFirstPerson();
        // event.getYaw()/getPitch() chegam como o olhar do jogador (getViewYRot/getViewXRot): é o olhar livre
        Quaternionf camera = CockpitCamera.cameraRotation(attitude, third, event.getYaw(), event.getPitch());
        float[] angles = mc.options.getCameraType().isMirrored() ? CockpitCamera.toVanillaMirrored(camera) : CockpitCamera.toVanilla(camera);
        event.setYaw(angles[0]);
        event.setPitch(angles[1]);
        event.setRoll(angles[2]);
    }

    @SubscribeEvent
    static void onDetachedDistance(CalculateDetachedCameraDistanceEvent event) {
        RocketEntity rocket = cameraRocket(Minecraft.getInstance());
        if (rocket != null) {
            RocketPortrait p = rocket.portrait();
            event.setDistance(CockpitCamera.thirdPersonDistance(p.sizeX, p.sizeY, p.sizeZ));
        }
    }

    @SubscribeEvent
    static void onRenderPlayer(RenderPlayerEvent.Pre<?> event) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(event.getRenderState().id) instanceof Entity rider && rider.getVehicle() instanceof RocketEntity) {
            event.setCanceled(true);
        }
    }
}
