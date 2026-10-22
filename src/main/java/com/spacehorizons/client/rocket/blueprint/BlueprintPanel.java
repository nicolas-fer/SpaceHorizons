package com.spacehorizons.client.rocket.blueprint;

import com.spacehorizons.client.rocket.RocketPanel;
import com.spacehorizons.rocket.blueprint.BlueprintCheck;
import com.spacehorizons.rocket.blueprint.RocketBlueprintItem;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A parte da planta no painel de projeto da plataforma ({@code RocketDesignClient}): o nome da planta ativa no conjunto de plataformas, quanto
 * falta ("faltam 12 de 40 blocos", "3 errados") e o destino do projeto da planta (o mesmo cálculo do painel sobre a planta montada).
 */
public final class BlueprintPanel {
    /** Linhas que a parte da planta ocupa (sem planta: nenhuma). */
    public static final int LINES = 3;
    private static final int COLOR_TITLE = 0xFF7FD8FF;
    private static final int COLOR_GOOD = 0xFF66E08A;
    private static final int COLOR_WARN = 0xFFFFC857;
    private static final int COLOR_BAD = 0xFFFF5A4F;
    private static final int COLOR_VALUE = 0xFFFFFFFF;

    private BlueprintPanel() {
    }

    /** O estado da planta ligada à plataforma, ou null (sem planta: o painel não muda). */
    public static BlueprintHologram.@Nullable State stateFor(@Nullable Level level, BlockPos pad) {
        return level == null ? null : BlueprintHologram.forPanel(level, pad);
    }

    /** Desenha as linhas da planta a partir de {@code y} e devolve o y seguinte. */
    public static int render(GuiGraphicsExtractor g, Font font, int x, int y, int width, int lineHeight, BlueprintHologram.State state) {
        BlueprintCheck.Progress p = state.progress();
        g.text(font, Component.translatable("design.spacehorizons.blueprint.title", RocketBlueprintItem.displayName(state.blueprint())), x + 5, y, COLOR_TITLE);
        y += lineHeight;
        if (p != null) {
            if (p.complete()) {
                g.text(font, Component.translatable("design.spacehorizons.blueprint.complete", p.total()), x + 5, y, COLOR_GOOD);
            } else {
                Component left = Component.translatable("design.spacehorizons.blueprint.missing", p.missing(), p.total());
                g.text(font, left, x + 5, y, COLOR_WARN);
                if (p.wrong() > 0) {
                    Component wrong = Component.translatable("design.spacehorizons.blueprint.wrong", p.wrong());
                    g.text(font, wrong, x + width - 5 - font.width(wrong), y, COLOR_BAD);
                }
            }
        }
        y += lineHeight;
        RocketPanel.row(g, font, x, y, width, Component.translatable("design.spacehorizons.blueprint.project"),
                Component.translatable(RocketBlueprintItem.destinationKey(state.blueprint())).getString(),
                state.blueprint().design().assemblable() ? COLOR_VALUE : COLOR_BAD);
        return y + lineHeight;
    }
}
