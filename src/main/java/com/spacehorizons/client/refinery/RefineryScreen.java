package com.spacehorizons.client.refinery;

import java.util.List;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModMenus;
import com.spacehorizons.survival.fuel.RefineryLogic;
import com.spacehorizons.survival.fuel.RefineryMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Tela da refinaria: fundo da fornalha do vanilla (os slots de entrada, combustível e saída ficam nos mesmos lugares),
 * chama e seta de progresso com os sprites da fornalha e um tanque à direita desenhado por código (provisório).
 * Passar o mouse sobre o tanque mostra "N / 4000 mB".
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public class RefineryScreen extends AbstractContainerScreen<RefineryMenu> {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/furnace.png");
    private static final Identifier LIT_SPRITE = Identifier.withDefaultNamespace("container/furnace/lit_progress");
    private static final Identifier BURN_SPRITE = Identifier.withDefaultNamespace("container/furnace/burn_progress");

    /** Seta de progresso (relativa ao canto da tela): área clicável que o JEI usa para abrir as receitas de refino. */
    public static final int ARROW_X = 79;
    public static final int ARROW_Y = 34;
    public static final int ARROW_W = 24;
    public static final int ARROW_H = 16;

    /** Retângulo interno do tanque, relativo ao canto da tela. */
    private static final int TANK_X = 146;
    private static final int TANK_Y = 17;
    private static final int TANK_W = 16;
    private static final int TANK_H = 52;

    private static final int COLOR_DARK = 0xFF373737;
    private static final int COLOR_LIGHT = 0xFFFFFFFF;
    private static final int COLOR_BACK = 0xFF8B8B8B;
    private static final int COLOR_FUEL = 0xFFE0A030;
    private static final int COLOR_FUEL_TOP = 0xFFF5CB6A;

    public RefineryScreen(RefineryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @SubscribeEvent
    static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.REFINERY.get(), RefineryScreen::new);
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

        // chama (entre a entrada e o combustível) e seta de progresso
        if (menu.isLit()) {
            int height = Mth.ceil(menu.getLitProgress() * 13.0F) + 1;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LIT_SPRITE, 14, 14, 0, 14 - height, x + 56, y + 36 + 14 - height, 14,
                    height);
        }
        int arrow = Mth.ceil(menu.getProgress() * 24.0F);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BURN_SPRITE, 24, 16, 0, 0, x + ARROW_X, y + ARROW_Y, arrow, ARROW_H);

        // tanque: moldura em relevo invertido (como os slots), fundo cinza e combustível de baixo para cima
        int tx = x + TANK_X;
        int ty = y + TANK_Y;
        graphics.fill(tx - 1, ty - 1, tx + TANK_W + 1, ty + TANK_H + 1, COLOR_DARK);
        graphics.fill(tx, ty, tx + TANK_W + 1, ty + TANK_H + 1, COLOR_LIGHT);
        graphics.fill(tx, ty, tx + TANK_W, ty + TANK_H, COLOR_BACK);
        int filled = Math.round(TANK_H * menu.getTankMb() / (float) RefineryLogic.TANK_CAPACITY);
        if (filled > 0) {
            graphics.fill(tx, ty + TANK_H - filled, tx + TANK_W, ty + TANK_H, COLOR_FUEL);
            graphics.fill(tx, ty + TANK_H - filled, tx + TANK_W, ty + TANK_H - filled + 1, COLOR_FUEL_TOP);
        }
        // marcas a cada 1000 mB (um galão)
        for (int i = 1; i < RefineryLogic.TANK_CAPACITY / 1000; i++) {
            int markY = ty + TANK_H - Math.round(TANK_H * i / (float) (RefineryLogic.TANK_CAPACITY / 1000));
            graphics.fill(tx + TANK_W - 5, markY, tx + TANK_W, markY + 1, COLOR_DARK);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        int tx = leftPos + TANK_X;
        int ty = topPos + TANK_Y;
        if (mouseX >= tx && mouseX < tx + TANK_W && mouseY >= ty && mouseY < ty + TANK_H) {
            graphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("gui.spacehorizons.refinery.tank"),
                    Component.translatable("gui.spacehorizons.refinery.tank_amount", menu.getTankMb(), RefineryLogic.TANK_CAPACITY)),
                    mouseX, mouseY);
        }
    }
}
