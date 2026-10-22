package com.spacehorizons.rocket.blueprint;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.content.ModDataComponents;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * O menu das plantas pede a troca do projeto do item na mão (cliente para servidor): {@code id} de uma planta embutida ({@link Blueprints})
 * ou vazio para deixar a planta em branco. O servidor confere que o item na mão é uma planta e que a planta escolhida não está trancada
 * (a lunar, até a história, só no criativo).
 */
@EventBusSubscriber(modid = SpaceHorizons.MODID)
public record BlueprintChoicePayload(String id, boolean mainHand) implements CustomPacketPayload {
    public static final Type<BlueprintChoicePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "blueprint_choice"));

    public static final StreamCodec<ByteBuf, BlueprintChoicePayload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(64), BlueprintChoicePayload::id,
            ByteBufCodecs.BOOL, BlueprintChoicePayload::mainHand, BlueprintChoicePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                apply(player, payload);
            }
        });
    }

    /** Aplica a escolha ao item na mão do jogador (o mesmo que o pacote faz; o autoteste também chama). */
    public static boolean apply(ServerPlayer player, BlueprintChoicePayload payload) {
        ItemStack stack = player.getItemInHand(payload.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        if (!(stack.getItem() instanceof RocketBlueprintItem)) {
            return false;
        }
        if (payload.id().isEmpty()) {
            stack.remove(ModDataComponents.ROCKET_BLUEPRINT.get());
            return true;
        }
        Blueprints.Entry entry = Blueprints.byId(payload.id());
        if (entry == null) {
            return false;
        }
        if (entry.locked() && !player.hasInfiniteMaterials()) {
            player.sendOverlayMessage(Component.translatable("message.spacehorizons.blueprint.locked"));
            return false;
        }
        stack.set(ModDataComponents.ROCKET_BLUEPRINT.get(), entry.blueprint());
        return true;
    }
}
