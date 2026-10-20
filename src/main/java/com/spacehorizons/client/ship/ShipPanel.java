package com.spacehorizons.client.ship;

import java.util.List;
import java.util.Locale;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.client.rocket.RocketKeys;
import com.spacehorizons.client.rocket.RocketPanel;
import com.spacehorizons.client.rocket.RocketPanelModel;
import com.spacehorizons.client.rocket.RocketTelemetryClient;
import com.spacehorizons.rocket.RocketControlPayload;
import com.spacehorizons.rocket.RocketTelemetryPayload;
import com.spacehorizons.space.flight.AttitudeMath;

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

import org.jspecify.annotations.Nullable;

/**
 * O painel do piloto da nave (HUD), enquanto se está sentado no assento: o mesmo desenho do painel do foguete ({@link RocketPanel}, que cede as
 * peças de desenho) com os dados da nave. Coluna da ESQUERDA: altitude, velocidades, apoastro, periastro, inclinação e tempo até o apoastro
 * (a telemetria do servidor, a cada 4 ticks); coluna da DIREITA: barras de acelerador e combustível, razão empuxo/peso, Δv, SAS e corpo. Sem
 * telemetria mostra "Sem telemetria". O acelerador mostrado é o comandado (alavanca local); um traço marca o que o servidor aplicou.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class ShipPanel {
    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "ship_panel");

    private static final int COLOR_TITLE = 0xFF4FC3F7;
    private static final int COLOR_LABEL = 0xFF9FB4C8;
    private static final int COLOR_VALUE = 0xFFFFFFFF;
    private static final int COLOR_GOOD = 0xFF66E08A;
    private static final int COLOR_WARN = 0xFFFFC857;
    private static final int COLOR_BAD = 0xFFFF5A4F;

    private static final int MARGIN = 6;
    private static final int MIN_LEFT_WIDTH = 120;
    private static final int BARS_WIDTH = 58;
    private static final int ROW = 10;
    private static final int BAR_HEIGHT = 60;

    private ShipPanel() {
    }

    @SubscribeEvent
    static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER_ID, ShipPanel::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!ShipPilot.isPiloting()) {
            return;
        }
        RocketTelemetryPayload t = RocketTelemetryClient.current();
        Font font = mc.font;
        long tick = mc.level == null ? 0L : mc.level.getGameTime();
        drawFlightColumn(graphics, font, t);
        drawEngineColumn(graphics, font, t);
        if (t == null) {
            RocketPanel.centeredBanner(graphics, font, Component.translatable("hud.spacehorizons.rocket.no_signal"), COLOR_LABEL, 24);
        } else if (t.burnNow() && RocketPanel.blink(tick)) {
            RocketPanel.centeredBanner(graphics, font, Component.translatable("hud.spacehorizons.rocket.burn_now"), COLOR_GOOD, 24);
        }
    }

    /** Dados de voo (coluna da esquerda). */
    private static void drawFlightColumn(GuiGraphicsExtractor g, Font font, @Nullable RocketTelemetryPayload t) {
        List<RocketPanelModel.Row> rows = RocketPanelModel.flightRows(t);
        int height = 16 + rows.size() * ROW + 4 + ROW + 4;
        int x = MARGIN;
        int y = Math.max(MARGIN, (g.guiHeight() - height) / 2 - 30);
        Component title = Component.translatable("hud.spacehorizons.ship.title_flight", RocketPanel.bodyName(t));
        Component hint = Component.translatable("hud.spacehorizons.ship.hint", RocketKeys.DISASSEMBLE.getTranslatedKeyMessage(), RocketKeys.NEXT_SAS.getTranslatedKeyMessage());
        int width = Math.max(MIN_LEFT_WIDTH, Math.max(font.width(title) + 12, font.width(hint) + 12));
        for (RocketPanelModel.Row row : rows) {
            width = Math.max(width, RocketPanel.needed(font, Component.translatable(row.labelKey()), row.value()));
        }
        RocketPanel.box(g, x, y, width, height);
        g.text(font, title, x + 5, y + 4, COLOR_TITLE);
        int ry = y + 16;
        for (RocketPanelModel.Row row : rows) {
            RocketPanel.row(g, font, x, ry, width, Component.translatable(row.labelKey()), row.value(), RocketPanel.toneColor(row.tone()));
            ry += ROW;
        }
        g.text(font, hint, x + 5, ry + 4, COLOR_LABEL);
    }

    /** Barras de acelerador e combustível, TWR, Δv, SAS e corpo (coluna da direita). */
    private static void drawEngineColumn(GuiGraphicsExtractor g, Font font, @Nullable RocketTelemetryPayload t) {
        float commanded = ShipPilot.throttle();
        float applied = ShipPilot.appliedThrottle();
        boolean notAccepted = ShipPilot.throttleNotAccepted();
        double fuelMb = t != null ? t.fuelMb() : 0.0;
        double capacity = t != null ? t.fuelCapacityMb() : 0.0;
        float fuelFraction = RocketPanelModel.fuelFraction(fuelMb, capacity);
        String sas = Component.translatable("hud.spacehorizons.rocket.sas." + AttitudeMath.SasMode.byId(t == null ? 0 : t.sasMode()).name().toLowerCase(Locale.ROOT)).getString();

        Component[] labels = {Component.translatable("hud.spacehorizons.rocket.throttle"), Component.translatable("hud.spacehorizons.rocket.twr"),
                Component.translatable("hud.spacehorizons.rocket.delta_v"), Component.translatable("hud.spacehorizons.rocket.sas")};
        String[] values = {RocketPanelModel.throttle(commanded, applied, notAccepted), RocketPanelModel.twr(t), RocketPanelModel.deltaV(t), sas};
        int[] colors = {notAccepted ? COLOR_BAD : COLOR_VALUE, t != null && Double.isFinite(t.twr()) && t.twr() < 1.0 ? COLOR_WARN : COLOR_VALUE, COLOR_VALUE, COLOR_VALUE};
        Component fuelLabel = Component.translatable("hud.spacehorizons.rocket.fuel");
        String fuelText = RocketPanelModel.fuel(fuelMb, capacity);
        Component bodyLabel = Component.translatable("hud.spacehorizons.rocket.body");
        String bodyText = RocketPanel.bodyName(t).getString();
        int side = 0;
        for (int i = 0; i < labels.length; i++) {
            side = Math.max(side, RocketPanel.needed(font, labels[i], values[i]));
        }
        int width = Math.max(Math.max(BARS_WIDTH + side, RocketPanel.needed(font, fuelLabel, fuelText)), RocketPanel.needed(font, bodyLabel, bodyText));
        width = Math.max(width, font.width(Component.translatable("hud.spacehorizons.ship.title_engine")) + 12);

        int height = 16 + BAR_HEIGHT + 14 + ROW + ROW + 6;
        int x = g.guiWidth() - MARGIN - width;
        int y = Math.max(MARGIN, (g.guiHeight() - height) / 2 - 30);
        RocketPanel.box(g, x, y, width, height);
        g.text(font, Component.translatable("hud.spacehorizons.ship.title_engine"), x + 5, y + 4, COLOR_TITLE);

        int barTop = y + 16;
        int throttleX = x + 10;
        RocketPanel.verticalBar(g, throttleX, barTop, 14, BAR_HEIGHT, commanded, 0xFF4FC3F7);
        if (Math.abs(applied - commanded) > 0.03F) {
            int markY = barTop + BAR_HEIGHT - Math.round(BAR_HEIGHT * applied);
            g.fill(throttleX - 2, markY, throttleX + 16, markY + 1, COLOR_VALUE);
        }
        int fuelColor = fuelFraction < 0.10F ? COLOR_BAD : (fuelFraction < 0.25F ? COLOR_WARN : COLOR_GOOD);
        RocketPanel.verticalBar(g, x + 34, barTop, 14, BAR_HEIGHT, fuelFraction, fuelColor);
        int ty = barTop;
        for (int i = 0; i < labels.length; i++) {
            RocketPanel.row(g, font, x + BARS_WIDTH - 5, ty, width - BARS_WIDTH + 5, labels[i], values[i], colors[i]);
            ty += 12;
        }
        int below = barTop + BAR_HEIGHT + 2;
        g.centeredText(font, Component.translatable("hud.spacehorizons.rocket.bar_throttle"), throttleX + 7, below, COLOR_LABEL);
        g.centeredText(font, Component.translatable("hud.spacehorizons.rocket.bar_fuel"), x + 41, below, COLOR_LABEL);
        RocketPanel.row(g, font, x, below + 12, width, fuelLabel, fuelText, fuelColor);
        RocketPanel.row(g, font, x, below + 12 + ROW, width, bodyLabel, bodyText, COLOR_VALUE);
    }

    /** Para o autoteste: o último pacote de controle como texto. */
    public static String describe(@Nullable RocketControlPayload p) {
        return RocketPanel.describe(p);
    }
}
