package com.spacehorizons.client.rocket;

import java.util.List;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.FlightMode;
import com.spacehorizons.rocket.RocketControlPayload;
import com.spacehorizons.rocket.RocketEntity;
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
 * O painel do piloto (HUD) enquanto se pilota um foguete. Sem cobrir o centro da tela:
 * <ul>
 *   <li>coluna da ESQUERDA: dados de voo (altitude, velocidades, apoastro, periastro, inclinação, tempo até o apoastro) e o
 *       planejador da Lua logo abaixo;</li>
 *   <li>coluna da DIREITA: barras verticais de acelerador e combustível, TWR, Δv, SAS, modo de voo e o corpo dominante;</li>
 *   <li>avisos empilhados no TOPO, ao centro (calor, aguardando terreno, "QUEIME AGORA", paraquedas);</li>
 *   <li>com aquecimento acima de 0,7, um brilho laranja nas bordas da tela.</li>
 * </ul>
 * Os números vêm de {@link RocketTelemetryClient#current()} (servidor, a cada 4 ticks) e dos dados sincronizados do foguete
 * (combustível, SAS, modo, calor, que também valem sem telemetria). O acelerador mostrado é o comandado localmente. Formatação
 * e unidades (b/s) em {@link RocketPanelModel}.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class RocketPanel {
    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "rocket_panel");

    private static final int COLOR_BG = 0xA00C1218;
    private static final int COLOR_BORDER = 0xFF2C3E50;
    private static final int COLOR_TITLE = 0xFF4FC3F7;
    private static final int COLOR_LABEL = 0xFF9FB4C8;
    private static final int COLOR_VALUE = 0xFFFFFFFF;
    private static final int COLOR_GOOD = 0xFF66E08A;
    private static final int COLOR_WARN = 0xFFFFC857;
    private static final int COLOR_BAD = 0xFFFF5A4F;
    private static final int COLOR_BAR_BACK = 0xFF05080B;

    private static final int MARGIN = 6;
    private static final int MIN_LEFT_WIDTH = 120;
    /** Espaço das duas barras verticais (acelerador e combustível) no começo da coluna da direita. */
    private static final int BARS_WIDTH = 58;
    private static final int ROW = 10;
    private static final int BAR_HEIGHT = 60;

    private RocketPanel() {
    }

    @SubscribeEvent
    static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER_ID, RocketPanel::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        RocketEntity rocket = RocketPilot.rocket();
        if (rocket == null) {
            return;
        }
        RocketTelemetryPayload t = RocketTelemetryClient.current();
        Font font = mc.font;
        long tick = mc.level == null ? 0L : mc.level.getGameTime();

        drawHeatGlow(graphics, rocket.heat());
        int leftBottom = drawFlightColumn(graphics, font, rocket, t);
        drawPlanner(graphics, font, t, MARGIN, leftBottom + 4, tick);
        drawEngineColumn(graphics, font, rocket, t);
        drawWarnings(graphics, font, rocket, t, tick);
    }

    // ---------------------------------------------------------------- coluna da esquerda

    /** Dados de voo; devolve o y logo abaixo da caixa. */
    private static int drawFlightColumn(GuiGraphicsExtractor g, Font font, RocketEntity rocket, @Nullable RocketTelemetryPayload t) {
        List<RocketPanelModel.Row> rows = RocketPanelModel.flightRows(t);
        int height = 16 + rows.size() * ROW + 4 + ROW + 4;
        int x = MARGIN;
        int y = Math.max(MARGIN, (g.guiHeight() - height) / 2 - 30);
        int width = leftWidth(font, t);
        box(g, x, y, width, height);

        Component title = Component.translatable("hud.spacehorizons.rocket.title_flight", bodyName(t));
        g.text(font, title, x + 5, y + 4, COLOR_TITLE);
        int ry = y + 16;
        for (RocketPanelModel.Row row : rows) {
            row(g, font, x, ry, width, Component.translatable(row.labelKey()), row.value(), toneColor(row.tone()));
            ry += ROW;
        }
        // dica da tecla de sair, só pousado (a saída do foguete é G)
        if (rocket.flightMode() == FlightMode.LANDED) {
            g.text(font, Component.translatable("hud.spacehorizons.rocket.hint_landed", RocketKeys.DISASSEMBLE.getTranslatedKeyMessage()), x + 5,
                    ry + 4, COLOR_GOOD);
        } else {
            g.text(font, Component.translatable("hud.spacehorizons.rocket.hint_flight", RocketKeys.NEXT_SAS.getTranslatedKeyMessage(),
                    RocketKeys.CAMERA.getTranslatedKeyMessage()), x + 5, ry + 4, COLOR_LABEL);
        }
        return y + height;
    }

    /** O planejador da Lua; não desenha nada se não há o que mostrar. */
    private static void drawPlanner(GuiGraphicsExtractor g, Font font, @Nullable RocketTelemetryPayload t, int x, int y, long tick) {
        List<RocketPanelModel.Row> rows = RocketPanelModel.plannerRows(t);
        if (rows.isEmpty()) {
            return;
        }
        boolean burn = t != null && t.burnNow();
        int height = 16 + rows.size() * ROW + 4;
        int width = leftWidth(font, t);
        box(g, x, y, width, height);
        g.text(font, Component.translatable("hud.spacehorizons.rocket.planner.title"), x + 5, y + 4, burn && blink(tick) ? COLOR_GOOD : COLOR_TITLE);
        int ry = y + 16;
        for (RocketPanelModel.Row row : rows) {
            Component label = Component.translatable(row.labelKey());
            if (row.value().isEmpty()) {
                g.text(font, label, x + 5, ry, toneColor(row.tone()));
            } else {
                row(g, font, x, ry, width, label, row.value(), toneColor(row.tone()));
            }
            ry += ROW;
        }
    }

    /** Largura da coluna da esquerda (dados de voo e planejador): a da linha mais larga, para nada se sobrepor em nenhum idioma. */
    private static int leftWidth(Font font, @Nullable RocketTelemetryPayload t) {
        int width = MIN_LEFT_WIDTH;
        for (RocketPanelModel.Row row : RocketPanelModel.flightRows(t)) {
            width = Math.max(width, needed(font, Component.translatable(row.labelKey()), row.value()));
        }
        for (RocketPanelModel.Row row : RocketPanelModel.plannerRows(t)) {
            width = Math.max(width, needed(font, Component.translatable(row.labelKey()), row.value()));
        }
        width = Math.max(width, font.width(Component.translatable("hud.spacehorizons.rocket.title_flight", bodyName(t))) + 12);
        width = Math.max(width, font.width(Component.translatable("hud.spacehorizons.rocket.hint_landed", RocketKeys.DISASSEMBLE.getTranslatedKeyMessage())) + 12);
        return Math.max(width, font.width(Component.translatable("hud.spacehorizons.rocket.hint_flight", RocketKeys.NEXT_SAS.getTranslatedKeyMessage(),
                RocketKeys.CAMERA.getTranslatedKeyMessage())) + 12);
    }

    /** Largura de uma linha de rótulo e valor: os dois textos, o respiro dos lados e uma folga entre eles. */
    private static int needed(Font font, Component label, String value) {
        return font.width(label) + font.width(value) + 18;
    }

    // ---------------------------------------------------------------- coluna da direita

    private static void drawEngineColumn(GuiGraphicsExtractor g, Font font, RocketEntity rocket, @Nullable RocketTelemetryPayload t) {
        float commanded = RocketPilot.throttle();
        double fuelMb = t != null ? t.fuelMb() : rocket.syncedFuelMb();
        double capacity = t != null ? t.fuelCapacityMb() : rocket.stats().fuelCapacityMb();
        float fuelFraction = RocketPanelModel.fuelFraction(fuelMb, capacity);

        // linhas ao lado das barras (rótulo, valor, cor) e a largura da caixa pela linha mais larga
        Component[] labels = {Component.translatable("hud.spacehorizons.rocket.throttle"), Component.translatable("hud.spacehorizons.rocket.twr"),
                Component.translatable("hud.spacehorizons.rocket.delta_v"), Component.translatable("hud.spacehorizons.rocket.sas"),
                Component.translatable("hud.spacehorizons.rocket.mode")};
        float applied = rocket.throttle();
        boolean notAccepted = RocketPilot.throttleNotAccepted();
        String[] values = {RocketPanelModel.throttle(commanded, applied, notAccepted), RocketPanelModel.twr(t), RocketPanelModel.deltaV(t), sasName(rocket).getString(),
                modeName(rocket).getString()};
        int[] colors = {notAccepted ? COLOR_BAD : COLOR_VALUE, t != null && Double.isFinite(t.twr()) && t.twr() < 1.0 ? COLOR_WARN : COLOR_VALUE, COLOR_VALUE, COLOR_VALUE,
                rocket.flightMode() == FlightMode.LANDED ? COLOR_GOOD : COLOR_VALUE};
        Component fuelLabel = Component.translatable("hud.spacehorizons.rocket.fuel");
        String fuelText = RocketPanelModel.fuel(fuelMb, capacity);
        Component bodyLabel = Component.translatable("hud.spacehorizons.rocket.body");
        String bodyText = bodyName(t).getString();
        int side = 0;
        for (int i = 0; i < labels.length; i++) {
            side = Math.max(side, needed(font, labels[i], values[i]));
        }
        int width = Math.max(Math.max(BARS_WIDTH + side, needed(font, fuelLabel, fuelText)), needed(font, bodyLabel, bodyText));
        width = Math.max(width, font.width(Component.translatable("hud.spacehorizons.rocket.title_engine")) + 12);

        int height = 16 + BAR_HEIGHT + 14 + ROW + ROW + 6;
        int x = g.guiWidth() - MARGIN - width;
        int y = Math.max(MARGIN, (g.guiHeight() - height) / 2 - 30);
        box(g, x, y, width, height);
        g.text(font, Component.translatable("hud.spacehorizons.rocket.title_engine"), x + 5, y + 4, COLOR_TITLE);

        int barTop = y + 16;
        // acelerador: barra vertical; um traço branco marca o que o servidor aplicou, se diferir do comandado
        int throttleX = x + 10;
        verticalBar(g, throttleX, barTop, 14, BAR_HEIGHT, commanded, 0xFF4FC3F7);
        if (Math.abs(applied - commanded) > 0.03F) {
            int markY = barTop + BAR_HEIGHT - Math.round(BAR_HEIGHT * applied);
            g.fill(throttleX - 2, markY, throttleX + 16, markY + 1, COLOR_VALUE);
        }
        // combustível: verde, amarelo abaixo de 25%, vermelho piscando abaixo de 10%
        int fuelColor = fuelFraction < 0.10F ? COLOR_BAD : (fuelFraction < 0.25F ? COLOR_WARN : COLOR_GOOD);
        boolean fuelBlink = fuelFraction < 0.10F && blink(mc().level == null ? 0L : mc().level.getGameTime());
        verticalBar(g, x + 34, barTop, 14, BAR_HEIGHT, fuelBlink ? 0.0F : fuelFraction, fuelColor);

        int ty = barTop;
        for (int i = 0; i < labels.length; i++) {
            row(g, font, x + BARS_WIDTH - 5, ty, width - BARS_WIDTH + 5, labels[i], values[i], colors[i]);
            ty += 12;
        }

        int below = barTop + BAR_HEIGHT + 2;
        g.centeredText(font, Component.translatable("hud.spacehorizons.rocket.bar_throttle"), throttleX + 7, below, COLOR_LABEL);
        g.centeredText(font, Component.translatable("hud.spacehorizons.rocket.bar_fuel"), x + 41, below, COLOR_LABEL);
        row(g, font, x, below + 12, width, fuelLabel, fuelText, fuelColor);
        row(g, font, x, below + 12 + ROW, width, bodyLabel, bodyText, COLOR_VALUE);
    }

    // ---------------------------------------------------------------- avisos e brilho

    private static void drawWarnings(GuiGraphicsExtractor g, Font font, RocketEntity rocket, @Nullable RocketTelemetryPayload t, long tick) {
        List<RocketPanelModel.Warning> warnings = RocketPanelModel.warnings(t, rocket.heat());
        int y = 24;
        if (t == null) {
            y = centeredBanner(g, font, Component.translatable("hud.spacehorizons.rocket.no_signal"), COLOR_LABEL, y);
        }
        for (RocketPanelModel.Warning w : warnings) {
            boolean urgent = w.tone() == RocketPanelModel.Tone.BAD || "hud.spacehorizons.rocket.burn_now".equals(w.key());
            if (urgent && !blink(tick)) {
                // pisca duas vezes por segundo, mas o espaço fica reservado
                y += 14;
                continue;
            }
            y = centeredBanner(g, font, Component.translatable(w.key(), w.args()), toneColor(w.tone()), y);
        }
    }

    /** Uma faixa de aviso centrada no topo; devolve o y da próxima. */
    private static int centeredBanner(GuiGraphicsExtractor g, Font font, Component text, int color, int y) {
        int width = font.width(text) + 12;
        int x = (g.guiWidth() - width) / 2;
        g.fill(x - 1, y - 1, x + width + 1, y + 12, COLOR_BORDER);
        g.fill(x, y, x + width, y + 11, COLOR_BG);
        g.text(font, text, x + 6, y + 2, color);
        return y + 14;
    }

    /** Brilho laranja nas bordas de cima e de baixo quando o calor passa de 0,7 (cresce até 2,0). */
    private static void drawHeatGlow(GuiGraphicsExtractor g, float heat) {
        if (heat <= RocketPanelModel.HEAT_WARNING) {
            return;
        }
        float k = Math.min(1.0F, (heat - RocketPanelModel.HEAT_WARNING) / 1.3F);
        int alpha = (int) (60 + 160 * k);
        int strong = (alpha << 24) | 0xFF6A1A;
        int none = 0x00FF6A1A;
        int band = g.guiHeight() / 5;
        g.fillGradient(0, 0, g.guiWidth(), band, strong, none);
        g.fillGradient(0, g.guiHeight() - band, g.guiWidth(), g.guiHeight(), none, strong);
    }

    // ---------------------------------------------------------------- textos

    /** O nome do corpo dominante; sem telemetria, "—". */
    static Component bodyName(@Nullable RocketTelemetryPayload t) {
        return t == null ? Component.literal(RocketPanelModel.NONE)
                : Component.translatableWithFallback("hud.spacehorizons.rocket.body." + t.body(), t.body());
    }

    static Component sasName(RocketEntity rocket) {
        return Component.translatable("hud.spacehorizons.rocket.sas." + AttitudeMath.SasMode.byId(rocket.sasMode()).name().toLowerCase(java.util.Locale.ROOT));
    }

    static Component modeName(RocketEntity rocket) {
        return Component.translatable("hud.spacehorizons.rocket.mode." + rocket.flightMode().name().toLowerCase(java.util.Locale.ROOT));
    }

    // ---------------------------------------------------------------- desenho

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static boolean blink(long tick) {
        return (tick / 5) % 2 == 0;
    }

    private static int toneColor(RocketPanelModel.Tone tone) {
        return switch (tone) {
            case GOOD -> COLOR_GOOD;
            case WARN -> COLOR_WARN;
            case BAD -> COLOR_BAD;
            case NORMAL -> COLOR_VALUE;
        };
    }

    private static void box(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, COLOR_BORDER);
        g.fill(x, y, x + w, y + h, COLOR_BG);
    }

    /** Rótulo à esquerda (cinza) e valor alinhado à direita da faixa {@code [x, x + w]}. */
    private static void row(GuiGraphicsExtractor g, Font font, int x, int y, int w, Component label, String value, int valueColor) {
        g.text(font, label, x + 5, y, COLOR_LABEL);
        g.text(font, value, x + w - 5 - font.width(value), y, valueColor);
    }

    /** Barra vertical que enche de baixo para cima. */
    private static void verticalBar(GuiGraphicsExtractor g, int x, int y, int w, int h, float fraction, int color) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, COLOR_BORDER);
        g.fill(x, y, x + w, y + h, COLOR_BAR_BACK);
        int filled = Math.round(h * Math.max(0.0F, Math.min(1.0F, fraction)));
        if (filled > 0) {
            g.fill(x, y + h - filled, x + w, y + h, color);
        }
        // marcas a cada 25%
        for (int i = 1; i < 4; i++) {
            int markY = y + h - h * i / 4;
            g.fill(x, markY, x + 3, markY + 1, 0x80FFFFFF);
        }
    }

    /** Para o autoteste: o acelerador comandado como o painel o mostra. */
    public static String throttleText() {
        return RocketPanelModel.percent(RocketPilot.throttle());
    }

    /** Para o autoteste: o último pacote de controle como texto. */
    public static String describe(@Nullable RocketControlPayload p) {
        return p == null ? "nenhum" : String.format(java.util.Locale.ROOT, "pitch=%.0f yaw=%.0f roll=%.0f acelerador=%.2f flags=%d", p.pitch(), p.yaw(),
                p.roll(), p.throttle(), p.flags());
    }
}
