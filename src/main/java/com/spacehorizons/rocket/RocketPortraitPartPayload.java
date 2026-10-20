package com.spacehorizons.rocket;

import java.util.UUID;

import com.spacehorizons.SpaceHorizons;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Uma parte do retrato de um foguete grande demais para o pacote de spawn (servidor para cliente), ver {@link PortraitParts}.
 *
 * @param portraitId o id do retrato ({@link RocketPortrait#id()}), que o pacote de spawn do foguete já trouxe
 * @param index      número da parte (0 a count - 1)
 * @param count      quantas partes o retrato tem
 * @param bytes      os bytes desta parte
 */
public record RocketPortraitPartPayload(UUID portraitId, int index, int count, byte[] bytes) implements CustomPacketPayload {
    public static final Type<RocketPortraitPartPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "rocket_portrait_part"));

    public static final StreamCodec<ByteBuf, RocketPortraitPartPayload> CODEC = StreamCodec.of(
            (buf, p) -> p.write(new FriendlyByteBuf(buf)), buf -> read(new FriendlyByteBuf(buf)));

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.portraitId);
        buf.writeVarInt(this.index);
        buf.writeVarInt(this.count);
        buf.writeByteArray(this.bytes);
    }

    private static RocketPortraitPartPayload read(FriendlyByteBuf buf) {
        return new RocketPortraitPartPayload(buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(PortraitParts.PART_BYTES + 16));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
