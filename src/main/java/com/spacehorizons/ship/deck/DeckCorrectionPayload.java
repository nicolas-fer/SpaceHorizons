package com.spacehorizons.ship.deck;

import com.spacehorizons.SpaceHorizons;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Correção do servidor (servidor para cliente), o equivalente local do "moved wrongly": a posição que o cliente mandou não cabe (entra num
 * bloco ou anda rápido demais), então o servidor devolve a última posição boa. O cliente refaz o estado dali, com velocidade zero.
 */
public record DeckCorrectionPayload(int rocketId, DeckMovePayload state) implements CustomPacketPayload {
    public static final Type<DeckCorrectionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "deck_correction"));

    public static final StreamCodec<ByteBuf, DeckCorrectionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DeckCorrectionPayload::rocketId,
            DeckMovePayload.CODEC, DeckCorrectionPayload::state,
            DeckCorrectionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
