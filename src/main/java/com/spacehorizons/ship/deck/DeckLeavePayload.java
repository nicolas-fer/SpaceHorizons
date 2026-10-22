package com.spacehorizons.ship.deck;

import com.spacehorizons.SpaceHorizons;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** O servidor tira o jogador do referencial da nave (servidor para cliente); o pacote de posição do mundo vem em seguida. */
public record DeckLeavePayload() implements CustomPacketPayload {
    public static final Type<DeckLeavePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "deck_leave"));

    public static final StreamCodec<ByteBuf, DeckLeavePayload> CODEC = StreamCodec.unit(new DeckLeavePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
