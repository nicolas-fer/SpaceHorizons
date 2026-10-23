package com.spacehorizons.client.story;

import java.util.List;

import com.spacehorizons.story.StoryEntries;
import com.spacehorizons.story.StoryEntry;
import com.spacehorizons.story.StoryLogic;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * A tela de leitura de um registro da trama: o diário (clique direito no item, texto em papel) ou o terminal (clique direito no bloco, texto verde sobre
 * fundo escuro, com a lista do que o terminal entrega e se já foi levado). O texto vem do lang pelo id do registro
 * ({@code story.spacehorizons.<id>.title|author|body}); a leitura em si (marcar como lido, entregar a recompensa) é do servidor.
 */
public class StoryScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int PAD = 10;

    private final StoryEntry entry;
    private final boolean terminal;

    public StoryScreen(String entryId) {
        super(Component.translatable("screen.spacehorizons.story.diary"));
        StoryEntry found = StoryEntries.byId(entryId);
        this.entry = found;
        this.terminal = found != null && found.isTerminal();
    }

    /** O id do registro mostrado (para o autoteste). */
    public String entryId() {
        return this.entry == null ? "" : this.entry.id();
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).bounds((this.width - 120) / 2, this.height - 28, 120, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        if (this.entry == null) {
            graphics.centeredText(this.font, Component.translatable("screen.spacehorizons.story.unknown"), this.width / 2, this.height / 2, -1);
            return;
        }
        int panelW = Math.min(PANEL_W, this.width - 20);
        int x = (this.width - panelW) / 2;
        int textW = panelW - 2 * PAD;
        List<FormattedCharSequence> body = this.font.split(Component.translatable(this.entry.textKey("body")), textW);
        int rewardLines = this.terminal ? this.entry.rewards().size() + 2 : 0;
        int panelH = PAD + 12 + 12 + 6 + body.size() * 9 + (this.terminal ? 8 + rewardLines * 10 : 0) + PAD;
        int y = Math.max(8, (this.height - 36 - panelH) / 2);
        int paper = this.terminal ? 0xE6081810 : 0xF0E9D8B0;
        int edge = this.terminal ? 0xFF3FD27A : 0xFF6B4F2A;
        int ink = this.terminal ? 0xFF7CFFA8 : 0xFF3A2A12;
        int dim = this.terminal ? 0xFF3FA868 : 0xFF6B5636;
        graphics.fill(x - 1, y - 1, x + panelW + 1, y + panelH + 1, edge);
        graphics.fill(x, y, x + panelW, y + panelH, paper);
        int ty = y + PAD;
        graphics.text(this.font, Component.translatable(this.entry.textKey("title")), x + PAD, ty, ink, false);
        ty += 12;
        graphics.text(this.font, Component.translatable(this.entry.textKey("author")), x + PAD, ty, dim, false);
        ty += 12 + 6;
        for (FormattedCharSequence line : body) {
            graphics.text(this.font, line, x + PAD, ty, ink, false);
            ty += 9;
        }
        if (this.terminal) {
            ty += 8;
            graphics.text(this.font, Component.translatable("screen.spacehorizons.story.rewards"), x + PAD, ty, dim, false);
            ty += 10;
            boolean claimed = this.minecraft.player != null && StoryLogic.progress(this.minecraft.player).hasClaimed(this.entry.id());
            for (StoryEntry.Reward reward : this.entry.rewards()) {
                Component line = Component.translatable(claimed ? "screen.spacehorizons.story.reward_done" : "screen.spacehorizons.story.reward_pending",
                        Component.translatable("story.spacehorizons.reward." + reward.id()));
                graphics.text(this.font, line, x + PAD, ty, ink, false);
                ty += 10;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
