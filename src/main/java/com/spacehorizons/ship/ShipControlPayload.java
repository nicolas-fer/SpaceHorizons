package com.spacehorizons.ship;

import com.spacehorizons.SpaceHorizons;
import com.spacehorizons.rocket.RocketControlPayload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Comando de voo do piloto da nave (cliente para servidor): o mesmo comando do foguete ({@link RocketControlPayload}: giros, acelerador e
 * bits de ação) com outro tipo de pacote, para o servidor saber que é de uma nave. O bit {@link RocketControlPayload#FLAG_DISASSEMBLE}
 * vale aqui como "levantar do assento". A nave é a do assento onde o jogador está sentado; o servidor não confia no cliente para isso.
 */
public record ShipControlPayload(RocketControlPayload control) implements CustomPacketPayload {
    public static final Type<ShipControlPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "ship_control"));

    public static final StreamCodec<ByteBuf, ShipControlPayload> CODEC = StreamCodec.composite(RocketControlPayload.CODEC, ShipControlPayload::control,
            ShipControlPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
