package com.spacehorizons.client.rocket;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.RocketEntity;
import com.spacehorizons.rocket.RocketPortrait;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Quaternionf;

/**
 * A câmera do piloto, alinhada à atitude do foguete (a conta está em {@link CockpitCamera}, testada em ida e volta).
 *
 * <ul>
 *   <li>{@link ViewportEvent.ComputeCameraAngles}: com o jogador num {@link RocketEntity}, troca yaw/pitch/roll do vanilla
 *       pelos que reproduzem {@code atitude · base · olhar livre}. A atitude é a MESMA interpolada que o renderer usa
 *       ({@link RocketEntity#orientation}), então a câmera e o desenho do foguete nunca divergem. O olhar livre é o yaw e o
 *       pitch do próprio jogador, que o mouse já move (o vanilla não precisa de gancho em {@code onPassengerTurned}).</li>
 *   <li>{@link CalculateDetachedCameraDistanceEvent}: a distância em terceira pessoa é proporcional ao tamanho do retrato.</li>
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
