package com.spacehorizons.ship.deck;

import com.spacehorizons.SpaceHorizons;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * O clique direito num bloco do retrato (cliente para servidor): o vanilla só enxerga blocos do mundo, então o clique no retrato da nave vai por
 * aqui, com a nave, a posição LOCAL do bloco, a face e o ponto atingido (local). O servidor confere se o bloco existe e se o jogador está ao
 * alcance antes de agir ({@code PortraitUse}).
 */
public record DeckUsePayload(int rocketId, long pos, int face, float hx, float hy, float hz) implements CustomPacketPayload {
    public static final Type<DeckUsePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "deck_use"));

    public static final StreamCodec<ByteBuf, DeckUsePayload> CODEC = StreamCodec.of((buf, p) -> p.write(new FriendlyByteBuf(buf)),
            buf -> read(new FriendlyByteBuf(buf)));

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.rocketId);
        buf.writeLong(this.pos);
        buf.writeByte(this.face);
        buf.writeFloat(this.hx);
        buf.writeFloat(this.hy);
        buf.writeFloat(this.hz);
    }

    private static DeckUsePayload read(FriendlyByteBuf buf) {
        return new DeckUsePayload(buf.readVarInt(), buf.readLong(), buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
