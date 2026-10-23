package com.spacehorizons.client.ship;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.rocket.RocketKeys;
import com.spacehorizons.client.rocket.RocketPanel;
import com.spacehorizons.client.rocket.RocketPilot;
import com.spacehorizons.ship.AnchorStatus;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * A indicação da âncora no painel do piloto (fase 2f): uma caixa no alto e ao centro, abaixo dos avisos do painel de voo, com o estado vindo do servidor
 * ({@link AnchorClient}): "pode ancorar [tecla]", "falta gerador", "gerador sem carga", "sem espaço livre", "rápido demais", "inclinada demais",
 * "ancorando" e "ancorada". Aparece só para quem pilota uma nave no planeta (e por alguns segundos depois de ancorar).
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class AnchorHud {
    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "anchor_hud");

    private static final int COLOR_TITLE = 0xFF4FC3F7;
    private static final int COLOR_GOOD = 0xFF66E08A;
    private static final int COLOR_WARN = 0xFFFFC857;
    private static final int COLOR_BAD = 0xFFFF5A4F;

    private AnchorHud() {
    }

    @SubscribeEvent
    static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER_ID, AnchorHud::render);
    }

    /** O estado que o painel mostra agora, ou null se não há nada a mostrar. */
    public static AnchorStatus shown() {
        AnchorStatus status = AnchorClient.current();
        if (status == null || status == AnchorStatus.NOT_SHIP) {
            return null;
        }
        return status == AnchorStatus.ANCHORED || RocketPilot.isPiloting() ? status : null;
    }

    /** O texto do estado (o que o painel escreve). */
    public static Component text(AnchorStatus status) {
        if (status == AnchorStatus.READY) {
            return Component.translatable("hud.spacehorizons.anchor.state.ready", RocketKeys.ANCHOR.getTranslatedKeyMessage());
        }
        return Component.translatable("hud.spacehorizons.anchor.state." + status.key());
    }

    private static int color(AnchorStatus status) {
        return switch (status) {
            case READY, ANCHORED -> COLOR_GOOD;
            case BUSY -> COLOR_WARN;
            default -> COLOR_BAD;
        };
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        AnchorStatus status = shown();
        if (status == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Component title = Component.translatable("hud.spacehorizons.anchor.title");
        Component text = text(status);
        int width = Math.max(font.width(title), font.width(text)) + 16;
        int height = 26;
        int x = (g.guiWidth() - width) / 2;
        int y = 44;
        RocketPanel.box(g, x, y, width, height);
        g.centeredText(font, title, g.guiWidth() / 2, y + 4, COLOR_TITLE);
        g.centeredText(font, text, g.guiWidth() / 2, y + 15, color(status));
    }
}
