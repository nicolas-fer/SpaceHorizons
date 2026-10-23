package com.spacehorizons.client.moonbase;

import java.util.List;
import java.util.Locale;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModMenus;
import com.spacehorizons.moonbase.energy.EnergyMath;
import com.spacehorizons.moonbase.machine.MachineKind;
import com.spacehorizons.moonbase.machine.MachineStatus;
import com.spacehorizons.moonbase.machine.MoonMachineMenu;
import com.spacehorizons.moonbase.room.RoomPressure;

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
 * Tela das máquinas da base lunar: fundo da fornalha do vanilla com os slots que o tipo não usa escondidos, seta de progresso, barra
 * de energia à esquerda (passar o mouse mostra os UE) e, à direita, a situação e os números do tipo (pressão e oxigênio do gerador,
 * hidrogênio e oxigênio do eletrolisador, combustível da refinaria). Provisória, como as outras telas de máquina do mod.
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID, value = Dist.CLIENT)
public class MoonMachineScreen extends AbstractContainerScreen<MoonMachineMenu> {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/furnace.png");
    private static final Identifier BURN_SPRITE = Identifier.withDefaultNamespace("container/furnace/burn_progress");

    private static final int ARROW_X = 79;
    private static final int ARROW_Y = 34;
    private static final int ARROW_W = 24;
    private static final int ARROW_H = 16;
    /** Barra de energia (interior), relativa ao canto da tela. */
    private static final int BAR_X = 22;
    private static final int BAR_Y = 17;
    private static final int BAR_W = 10;
    private static final int BAR_H = 52;
    private static final int TEXT_X = 80;
    private static final int TEXT_WIDTH = 92;
    private static final int COLOR_PANEL = 0xFFC6C6C6;
    private static final int COLOR_DARK = 0xFF373737;
    private static final int COLOR_LIGHT = 0xFFFFFFFF;
    private static final int COLOR_BACK = 0xFF8B8B8B;
    private static final int COLOR_ENERGY = 0xFFE05A2A;
    private static final int COLOR_ENERGY_TOP = 0xFFF5A06A;

    public MoonMachineScreen(MoonMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @SubscribeEvent
    static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.MOON_MACHINE.get(), MoonMachineScreen::new);
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
        MachineKind kind = menu.kind();
        // molduras dos slots que este tipo não usa: pintadas com o cinza do painel
        if (!kind.slotUsed(0)) {
            graphics.fill(x + 55, y + 16, x + 73, y + 34, COLOR_PANEL);
        }
        if (!kind.slotUsed(1)) {
            graphics.fill(x + 55, y + 52, x + 73, y + 70, COLOR_PANEL);
        }
        if (!kind.slotUsed(2)) {
            graphics.fill(x + 110, y + 30, x + 138, y + 58, COLOR_PANEL);
        }
        // seta: progresso da tarefa (o gerador de oxigênio mostra a pressão da sala)
        float fraction = kind == MachineKind.OXYGEN_GENERATOR ? menu.getPressure() / (float) RoomPressure.MAX_PRESSURE
                : menu.getTotal() > 0 ? menu.getProgress() / (float) menu.getTotal() : 0.0F;
        int arrow = Mth.ceil(Mth.clamp(fraction, 0.0F, 1.0F) * ARROW_W);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BURN_SPRITE, ARROW_W, ARROW_H, 0, 0, x + ARROW_X, y + ARROW_Y, arrow, ARROW_H);

        // barra de energia: moldura em relevo invertido, fundo cinza e energia de baixo para cima
        int bx = x + BAR_X;
        int by = y + BAR_Y;
        graphics.fill(bx - 1, by - 1, bx + BAR_W + 1, by + BAR_H + 1, COLOR_DARK);
        graphics.fill(bx, by, bx + BAR_W + 1, by + BAR_H + 1, COLOR_LIGHT);
        graphics.fill(bx, by, bx + BAR_W, by + BAR_H, COLOR_BACK);
        int filled = Math.round(BAR_H * menu.getEnergy() / (float) EnergyMath.MACHINE_CAPACITY);
        if (filled > 0) {
            graphics.fill(bx, by + BAR_H - filled, bx + BAR_W, by + BAR_H, COLOR_ENERGY);
            graphics.fill(bx, by + BAR_H - filled, bx + BAR_W, by + BAR_H - filled + 1, COLOR_ENERGY_TOP);
        }

        // situação e números do tipo
        MachineStatus status = menu.getStatus();
        graphics.textWithWordWrap(font, Component.translatable("gui.spacehorizons.moonbase.status." + status.name().toLowerCase(Locale.ROOT)),
                x + TEXT_X, y + 59, TEXT_WIDTH, colorOf(status));
        Component info = info(kind);
        if (info != null) {
            graphics.textWithWordWrap(font, info, x + TEXT_X, y + 68, TEXT_WIDTH, 0xFF404040);
        }
    }

    private Component info(MachineKind kind) {
        return switch (kind) {
            case OXYGEN_GENERATOR -> Component.translatable("gui.spacehorizons.moonbase.info.generator", menu.getPressure() / 10, menu.getOxygen());
            case ELECTROLYZER -> Component.translatable("gui.spacehorizons.moonbase.info.electrolyzer", menu.getFluid(), menu.getOxygen());
            case LUNAR_REFINERY -> Component.translatable("gui.spacehorizons.moonbase.info.refinery", menu.getFluid());
            default -> null;
        };
    }

    private static int colorOf(MachineStatus status) {
        return switch (status) {
            case NO_POWER, NO_OXYGEN, LEAK, TOO_BIG, BLOCKED, WRONG_PLANET -> 0xFFB02020;
            case NO_INPUT, OUTPUT_FULL -> 0xFFA85A10;
            case WORKING, PRESSURIZING -> 0xFF1F5FA8;
            case PRESSURIZED -> 0xFF1E7B2E;
            default -> 0xFF404040;
        };
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        int bx = leftPos + BAR_X;
        int by = topPos + BAR_Y;
        if (mouseX >= bx && mouseX < bx + BAR_W && mouseY >= by && mouseY < by + BAR_H) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.spacehorizons.moonbase.energy"),
                    Component.translatable("gui.spacehorizons.moonbase.energy_amount", menu.getEnergy(), EnergyMath.MACHINE_CAPACITY)),
                    mouseX, mouseY);
        }
    }
}
