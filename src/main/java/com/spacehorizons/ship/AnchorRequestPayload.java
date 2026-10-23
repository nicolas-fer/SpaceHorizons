package com.spacehorizons.ship;

import com.spacehorizons.SpaceHorizons;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** O piloto apertou a tecla de ancorar (cliente para servidor). Sem dados: a nave é a que o jogador pilota, e o servidor confere tudo. */
public record AnchorRequestPayload() implements CustomPacketPayload {
    public static final Type<AnchorRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "anchor_request"));

    public static final StreamCodec<ByteBuf, AnchorRequestPayload> CODEC = StreamCodec.unit(new AnchorRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
