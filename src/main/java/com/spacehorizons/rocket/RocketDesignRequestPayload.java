package com.spacehorizons.rocket;

import com.spacehorizons.SpaceHorizons;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** O cliente pede o relatório do painel de projeto da plataforma de lançamento em {@code pad} (cliente para servidor, ver {@link RocketDesign}). */
public record RocketDesignRequestPayload(BlockPos pad) implements CustomPacketPayload {
    public static final Type<RocketDesignRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SpaceHorizons.MODID, "rocket_design_request"));

    public static final StreamCodec<ByteBuf, RocketDesignRequestPayload> CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC,
            RocketDesignRequestPayload::pad, RocketDesignRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
