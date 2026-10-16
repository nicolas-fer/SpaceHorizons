package com.spacehorizons.client.rocket;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.RocketFuelTankBlock;
import com.spacehorizons.rocket.RocketFuelTankBlockEntity;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jspecify.annotations.Nullable;

/**
 * Mostra a carga de um tanque de foguete quando se olha para ele ("Tanque de combustível: 1000 / 4000 mB"), logo abaixo da
 * mira. O block entity do tanque é sincronizado com o cliente (ver {@link RocketFuelTankBlockEntity#getUpdatePacket}).
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class RocketTankHud {
    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "rocket_tank");
    private static final int COLOR_TEXT = 0xFFFFE0A0;

    private RocketTankHud() {
    }

    @SubscribeEvent
    static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER_ID, RocketTankHud::render);
    }

    /** O texto que a tela mostra para o que o jogador está olhando, ou null se não for um tanque. Público para o autoteste logar. */
    public static @Nullable Component lookedAtText(Minecraft mc) {
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        if (mc.level.getBlockEntity(hit.getBlockPos()) instanceof RocketFuelTankBlockEntity tank) {
            return RocketFuelTankBlock.levelMessage(tank);
        }
        return null;
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Component text = lookedAtText(mc);
        if (text == null) {
            return;
        }
        int width = mc.font.width(text);
        graphics.text(mc.font, text, (graphics.guiWidth() - width) / 2, graphics.guiHeight() / 2 + 12, COLOR_TEXT);
    }
}
