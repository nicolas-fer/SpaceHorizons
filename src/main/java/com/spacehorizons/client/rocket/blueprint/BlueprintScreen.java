package com.spacehorizons.client.rocket.blueprint;

import com.spacehorizons.rocket.blueprint.Blueprint;
import com.spacehorizons.rocket.blueprint.BlueprintChoicePayload;
import com.spacehorizons.rocket.blueprint.Blueprints;
import com.spacehorizons.rocket.blueprint.RocketBlueprintItem;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * O "livro" das plantas (clique direito no ar com o item {@code rocket_blueprint}): escolhe a planta embutida que o item passa a guardar,
 * ou o deixa em branco para salvar um foguete. A lunar fica trancada em sobrevivência até a história entregá-la (ver {@link Blueprints}).
 * A troca é pedida ao servidor ({@link BlueprintChoicePayload}), que confere tudo de novo.
 */
public class BlueprintScreen extends Screen {
    private static final int BUTTON_W = 280;
    private static final int COLOR_TEXT = 0xFFE0E8F0;
    private static final int COLOR_DIM = 0xFF9FB4C8;

    private final InteractionHand hand;

    public BlueprintScreen(InteractionHand hand) {
        super(Component.translatable("screen.spacehorizons.blueprint.title"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        super.init();
        boolean creative = this.minecraft.player != null && this.minecraft.player.hasInfiniteMaterials();
        int x = (this.width - BUTTON_W) / 2;
        int y = 52;
        for (Blueprints.Entry entry : Blueprints.ALL) {
            boolean locked = entry.locked() && !creative;
            Component name = RocketBlueprintItem.displayName(entry.blueprint());
            Component label = locked ? Component.translatable("screen.spacehorizons.blueprint.locked_entry", name)
                    : Component.translatable("screen.spacehorizons.blueprint.entry", name, Component.translatable(RocketBlueprintItem.destinationKey(entry.blueprint())));
            Button button = Button.builder(label, b -> this.choose(entry.id())).bounds(x, y, BUTTON_W, 20)
                    .tooltip(Tooltip.create(Component.translatable(locked ? "screen.spacehorizons.blueprint.locked_hint" : "screen.spacehorizons.blueprint.hint." + entry.id())))
                    .build();
            button.active = !locked;
            this.addRenderableWidget(button);
            y += 24;
        }
        this.addRenderableWidget(Button.builder(Component.translatable("screen.spacehorizons.blueprint.blank"), b -> this.choose(""))
                .bounds(x, y, BUTTON_W, 20).tooltip(Tooltip.create(Component.translatable("screen.spacehorizons.blueprint.blank_hint"))).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).bounds((this.width - 120) / 2, this.height - 30, 120, 20).build());
    }

    /** Pede a planta {@code id} (vazio = em branco) para o item na mão e fecha. */
    public void choose(String id) {
        ClientPacketDistributor.sendToServer(new BlueprintChoicePayload(id, this.hand == InteractionHand.MAIN_HAND));
        this.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(this.font, this.title, this.width / 2, 16, -1);
        Component current = Component.translatable("screen.spacehorizons.blueprint.current_blank");
        if (this.minecraft.player != null) {
            ItemStack stack = this.minecraft.player.getItemInHand(this.hand);
            Blueprint blueprint = RocketBlueprintItem.blueprintOf(stack);
            if (blueprint != null) {
                current = Component.translatable("screen.spacehorizons.blueprint.current", RocketBlueprintItem.displayName(blueprint));
            }
        }
        graphics.centeredText(this.font, current, this.width / 2, 32, COLOR_DIM);
        int y = 52 + 24 * (Blueprints.ALL.size() + 1) + 8;
        for (int i = 1; i <= 3; i++) {
            graphics.centeredText(this.font, Component.translatable("screen.spacehorizons.blueprint.help" + i), this.width / 2, y, COLOR_TEXT);
            y += 12;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
