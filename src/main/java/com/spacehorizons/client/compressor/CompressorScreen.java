package com.spacehorizons.client.compressor;

import java.util.List;
import java.util.Locale;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModMenus;
import com.spacehorizons.survival.compressor.CompressorLogic;
import com.spacehorizons.survival.compressor.CompressorMenu;
import com.spacehorizons.survival.oxygen.OxygenAmounts;
import com.spacehorizons.survival.oxygen.OxygenStorage;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Tela do compressor de oxigênio: fundo da fornalha do vanilla com o slot de entrada escondido (o combustível fica
 * onde estava, o tanque ocupa o lugar do resultado), chama da fornalha para a queima e a seta de progresso mostrando
 * quanto o tanque já está cheio. Abaixo da seta, a situação em texto (comprimindo, tanque cheio, sem combustível,
 * sem tanque e, no vácuo, "Sem ar para comprimir").
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public class CompressorScreen extends AbstractContainerScreen<CompressorMenu> {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/furnace.png");
    private static final Identifier LIT_SPRITE = Identifier.withDefaultNamespace("container/furnace/lit_progress");
    private static final Identifier BURN_SPRITE = Identifier.withDefaultNamespace("container/furnace/burn_progress");

    /** Seta de progresso (relativa ao canto da tela): área clicável que o JEI usa para abrir a categoria do compressor. */
    public static final int ARROW_X = 79;
    public static final int ARROW_Y = 34;
    public static final int ARROW_W = 24;
    public static final int ARROW_H = 16;

    /** Texto da situação: abaixo da seta, à direita do combustível. */
    private static final int STATUS_X = 80;
    private static final int STATUS_Y = 59;
    private static final int STATUS_WIDTH = 92;

    /** Moldura do slot de entrada da fornalha (18 x 18), que o compressor não usa. */
    private static final int HIDDEN_SLOT_X = 55;
    private static final int HIDDEN_SLOT_Y = 16;
    private static final int HIDDEN_SLOT_SIZE = 18;
    /** Cinza do painel da tela da fornalha (0xC6C6C6). */
    private static final int COLOR_PANEL = 0xFFC6C6C6;

    public CompressorScreen(CompressorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @SubscribeEvent
    static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.OXYGEN_COMPRESSOR.get(), CompressorScreen::new);
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        graphics.fill(x + HIDDEN_SLOT_X, y + HIDDEN_SLOT_Y, x + HIDDEN_SLOT_X + HIDDEN_SLOT_SIZE, y + HIDDEN_SLOT_Y + HIDDEN_SLOT_SIZE,
                COLOR_PANEL);

        // chama (acima do combustível): o que resta da queima atual
        if (menu.hasFlame()) {
            int height = Mth.ceil(menu.getLitProgress() * 13.0F) + 1;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LIT_SPRITE, 14, 14, 0, 14 - height, x + 56, y + 36 + 14 - height, 14,
                    height);
        }
        // seta: quanto o tanque já está cheio
        int arrow = Mth.ceil(menu.getTankFraction() * 24.0F);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BURN_SPRITE, 24, 16, 0, 0, x + ARROW_X, y + ARROW_Y, arrow, ARROW_H);

        // situação em texto
        CompressorLogic.Status status = menu.getStatus();
        graphics.textWithWordWrap(font,
                Component.translatable("gui.spacehorizons.compressor.status." + status.name().toLowerCase(Locale.ROOT)),
                x + STATUS_X, y + STATUS_Y, STATUS_WIDTH, colorOf(status));
    }

    /** Vermelho para "sem ar" (a causa que o jogador não resolve sem sair dali), laranja para falta de combustível. */
    private static int colorOf(CompressorLogic.Status status) {
        return switch (status) {
            case NO_AIR -> 0xFFB02020;
            case NO_FUEL -> 0xFFA85A10;
            case FULL -> 0xFF1E7B2E;
            case WORKING -> 0xFF1F5FA8;
            case NO_TANK -> 0xFF404040;
        };
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        int ax = leftPos + ARROW_X;
        int ay = topPos + ARROW_Y;
        ItemStack tank = menu.getTank();
        if (OxygenStorage.isContainer(tank) && mouseX >= ax && mouseX < ax + ARROW_W && mouseY >= ay && mouseY < ay + ARROW_H) {
            graphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("gui.spacehorizons.compressor.air"),
                    Component.translatable("gui.spacehorizons.compressor.air_amount",
                            OxygenAmounts.formatTime(OxygenStorage.get(tank)), OxygenAmounts.formatTime(OxygenStorage.capacity(tank)))),
                    mouseX, mouseY);
        }
    }
}
