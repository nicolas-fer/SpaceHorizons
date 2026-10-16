package com.spacehorizons.client.oxygen;

import java.util.Locale;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.planet.Planets;
import com.spacehorizons.survival.oxygen.OxygenMath;
import com.spacehorizons.survival.oxygen.OxygenSetup;
import com.spacehorizons.survival.oxygen.OxygenState;
import com.spacehorizons.survival.suit.SuitGear;
import com.spacehorizons.survival.suit.TankMath;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Barra de oxigênio do HUD, acima da fome (a mesma faixa das bolhas de ar do vanilla, uma linha acima delas para
 * não sobrepor ao mergulhar). Aparece só quando o jogador está no vácuo ou com a reserva abaixo do máximo, e nunca
 * em criativo/espectador nem com o sistema desligado pela regra de jogo. Lê o Data Attachment sincronizado.
 * Com o traje respirando dos recipientes de oxigênio, o texto mostra a carga total deles (ver {@link #label}).
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public final class OxygenHud {
    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "oxygen");

    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 7;
    private static final int COLOR_BORDER = 0xFF000000;
    private static final int COLOR_BACK = 0xA0101820;
    private static final int COLOR_FILL = 0xFF4FC3F7;
    private static final int COLOR_FILL_LOW = 0xFFE53935;
    private static final int COLOR_FILL_SUPPLIED = 0xFF66E08A;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    /** Abaixo disto (um quarto da reserva) a barra fica vermelha e pisca. */
    private static final int LOW_RESERVE = OxygenMath.MAX_RESERVE / 4;

    private OxygenHud() {
    }

    @SubscribeEvent
    static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL, LAYER_ID, OxygenHud::render);
    }

    /** Decide se a barra aparece, dado o estado já sincronizado. Separado para o autoteste logar a mesma regra. */
    public static boolean isVisible(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isCreative() || player.isSpectator()) {
            return false;
        }
        OxygenState state = player.getData(OxygenSetup.OXYGEN);
        if (!state.enabled()) {
            return false;
        }
        return isVacuum(mc) || !state.isFull();
    }

    private static boolean isVacuum(Minecraft mc) {
        LocalPlayer player = mc.player;
        return Planets.forLevel(mc.level).map(planet -> OxygenMath.isVacuum(planet, player.getEyeY())).orElse(false);
    }

    /**
     * Texto acima da barra. Com o traje respirando dos recipientes de oxigênio mostra a carga total deles ("Tanques
     * 1:45"); com o traje no vácuo e os recipientes vazios, a reserva do corpo e o aviso; senão, como sempre.
     * Público para o autoteste logar exatamente o que o jogador vê.
     */
    public static Component label(Minecraft mc, OxygenState state) {
        LocalPlayer player = mc.player;
        boolean suitBreathes = SuitGear.piecesOf(player).canBreathe();
        if (state.supplied()) {
            int tanks = suitBreathes ? SuitGear.totalOxygen(player) : 0;
            return tanks > 0
                    ? Component.translatable("hud.spacehorizons.oxygen.tanks", TankMath.formatClock(tanks))
                    : Component.translatable("hud.spacehorizons.oxygen.supplied");
        }
        String seconds = String.format(Locale.ROOT, "%.0f", state.secondsLeft());
        return Component.translatable(
                suitBreathes && isVacuum(mc) ? "hud.spacehorizons.oxygen.seconds_no_tanks" : "hud.spacehorizons.oxygen.seconds", seconds);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        // com F1 (GUI oculta) o Minecraft nem chama as camadas
        if (!isVisible(mc)) {
            return;
        }
        OxygenState state = mc.player.getData(OxygenSetup.OXYGEN);
        int right = graphics.guiWidth() / 2 + 91;
        int x = right - BAR_WIDTH;
        // food em guiHeight-39, bolhas de ar em -49: a barra fica em -60
        int y = graphics.guiHeight() - 60;

        boolean low = !state.supplied() && state.reserve() <= LOW_RESERVE;
        // pisca (4 vezes por segundo) quando a reserva está baixa
        boolean blinkOff = low && (mc.player.tickCount / 3) % 2 == 0;
        int fillColor = state.supplied() ? COLOR_FILL_SUPPLIED : (low ? COLOR_FILL_LOW : COLOR_FILL);

        graphics.fill(x - 1, y - 1, right + 1, y + BAR_HEIGHT + 1, COLOR_BORDER);
        graphics.fill(x, y, right, y + BAR_HEIGHT, COLOR_BACK);
        int filled = Math.round(BAR_WIDTH * state.reserve() / (float) OxygenMath.MAX_RESERVE);
        if (filled > 0 && !blinkOff) {
            graphics.fill(x, y, x + filled, y + BAR_HEIGHT, fillColor);
        }

        graphics.text(mc.font, label(mc, state), x, y - 10, COLOR_TEXT);
    }
}
