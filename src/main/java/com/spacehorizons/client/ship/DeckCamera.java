package com.spacehorizons.client.ship;

import org.joml.Quaternionf;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.rocket.CockpitCamera;
import com.spacehorizons.rocket.RocketEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * A câmera e o desenho do jogador a pé no convés (S5). O olhar do jogador (yaw e pitch do mouse) é LOCAL à nave; a câmera é a atitude interpolada
 * da nave composta com ele ({@link CockpitCamera#deckRotation}), então o horizonte acompanha a inclinação e o giro da nave. A posição do olho
 * vem de {@link DeckClient#eyeWorld} (mixin em {@code CameraMixin}), e o modelo do jogador, em terceira pessoa, é girado pela mesma atitude em
 * torno dos pés: ele fica em pé no convés inclinado, não em pé no mundo.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class DeckCamera {
    private DeckCamera() {
    }

    @SubscribeEvent
    static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        RocketEntity rocket = DeckClient.rocket();
        if (rocket == null || Minecraft.getInstance().player == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Quaternionf attitude = new Quaternionf();
        rocket.orientation((float) event.getPartialTick(), attitude);
        // event.getYaw()/getPitch() chegam como o olhar do jogador, que aqui é o olhar local
        Quaternionf camera = CockpitCamera.deckRotation(attitude, event.getYaw(), event.getPitch());
        float[] angles = mc.options.getCameraType().isMirrored() ? CockpitCamera.toVanillaMirrored(camera) : CockpitCamera.toVanilla(camera);
        event.setYaw(angles[0]);
        event.setPitch(angles[1]);
        event.setRoll(angles[2]);
    }

    /** O modelo do jogador local, girado pela atitude da nave em torno dos pés (a origem da pilha de desenho é a posição dele). */
    @SubscribeEvent
    static void onRenderPlayer(RenderPlayerEvent.Pre<?> event) {
        Minecraft mc = Minecraft.getInstance();
        RocketEntity rocket = DeckClient.rocket();
        if (rocket == null || mc.level == null) {
            return;
        }
        Entity entity = mc.level.getEntity(event.getRenderState().id);
        if (entity == mc.player) {
            Quaternionf attitude = new Quaternionf();
            rocket.orientation(event.getPartialTick(), attitude);
            Vec3 fix = DeckClient.modelCorrection(event.getPartialTick());
            event.getPoseStack().translate(fix.x, fix.y, fix.z);
            event.getPoseStack().rotate(attitude);
        }
    }
}
